package at.plankt0n.streamplay.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.adapter.StationListAdapter
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.PlaylistURLHelper
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.StateHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.*

class StationsFragment : Fragment() {

    private lateinit var stationList: MutableList<StationItem>
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StationListAdapter
    private lateinit var topbarBackButton: ImageButton
    private lateinit var topbarTitle: TextView

    companion object {
        private const val REQUEST_CODE_IMPORT_JSON = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_stations, container, false)

        topbarBackButton = view.findViewById(R.id.arrow_back)
        topbarTitle = view.findViewById(R.id.topbar_title)

        stationList = PreferencesHelper.getStations(requireContext()).toMutableList()

        recyclerView = view.findViewById(R.id.recyclerViewStations)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = StationListAdapter(stationList) {
            refreshPlaylist()
        }
        recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.moveItem(from, to)
                return true
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                PreferencesHelper.saveStations(requireContext(), stationList)
                refreshPlaylist()
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                stationList.removeAt(position)
                PreferencesHelper.saveStations(requireContext(), stationList)
                adapter.notifyItemRemoved(position)
                refreshPlaylist()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        view.findViewById<View>(R.id.buttonAddStation).setOnClickListener {
            showAddDialog()
        }

        view.findViewById<View>(R.id.buttonImportStations).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            startActivityForResult(intent, REQUEST_CODE_IMPORT_JSON)
        }

        topbarBackButton.setOnClickListener {
            refreshPlaylist()
            parentFragmentManager.popBackStack()
        }

        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IMPORT_JSON && resultCode == Activity.RESULT_OK && data?.data != null) {
            importStationsFromUri(data.data!!)
        }
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_station, null)
        val editName = dialogView.findViewById<EditText>(R.id.editStationName)
        val editUrl = dialogView.findViewById<EditText>(R.id.editStreamUrl)
        val editIcon = dialogView.findViewById<EditText>(R.id.editIconUrl)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Station")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = editName.text.toString().ifBlank { editUrl.text.toString() }
                val url = editUrl.text.toString().trim()
                val icon = editIcon.text.toString().trim()
                if (url.isBlank()) {
                    Toast.makeText(requireContext(), "URL erforderlich", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val finalUrl = if (url.endsWith(".m3u", true) || url.endsWith(".pls", true)) {
                        PlaylistURLHelper.resolvePlaylistUrl(url) ?: url
                    } else url

                    val station = StationItem(
                        uuid = UUID.randomUUID().toString(),
                        stationName = name,
                        streamURL = finalUrl,
                        iconURL = icon
                    )
                    stationList.add(station)
                    PreferencesHelper.saveStations(requireContext(), stationList)
                    adapter.notifyItemInserted(stationList.size - 1)
                    refreshPlaylist()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun refreshPlaylist() {
        StateHelper.isPlaylistChangePending = true
        val intent = Intent(requireContext(), StreamingService::class.java).apply {
            action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
        }
        requireContext().startService(intent)
    }


    private fun importStationsFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
                ?: throw Exception("Datei konnte nicht geöffnet werden")
            val json = inputStream.bufferedReader().use { it.readText() }

            val type = object : TypeToken<List<ImportStation>>() {}.type
            val importedList: List<ImportStation> = Gson().fromJson(json, type)

            var updated = 0
            var added = 0

            for (imported in importedList) {
                val index = stationList.indexOfFirst { it.stationName.equals(imported.name, ignoreCase = true) }
                if (index >= 0) {
                    val old = stationList[index]
                    stationList[index] = StationItem(
                        uuid = old.uuid,
                        stationName = imported.name,
                        streamURL = imported.url,
                        iconURL = imported.iconUrl
                    )
                    updated++
                } else {
                    stationList.add(
                        StationItem(
                            uuid = UUID.randomUUID().toString(),
                            stationName = imported.name,
                            streamURL = imported.url,
                            iconURL = imported.iconUrl
                        )
                    )
                    added++
                }
            }

            PreferencesHelper.saveStations(requireContext(), stationList)
            adapter.notifyDataSetChanged()

            Toast.makeText(requireContext(), "Import abgeschlossen: $added neu, $updated aktualisiert.", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Fehler beim Import: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    data class ImportStation(
        val name: String,
        val url: String,
        val iconUrl: String
    )
}
