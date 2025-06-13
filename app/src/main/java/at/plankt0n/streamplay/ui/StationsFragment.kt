package at.plankt0n.streamplay.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import at.plankt0n.streamplay.adapter.SearchResultAdapter
import at.plankt0n.streamplay.adapter.StationListAdapter
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.PlaylistURLHelper
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.StateHelper
import at.plankt0n.streamplay.search.RadioBrowserHelper
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
        adapter = StationListAdapter(stationList)
        recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                stationList.removeAt(position)
                PreferencesHelper.saveStations(requireContext(), stationList)
                adapter.notifyItemRemoved(position)
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        view.findViewById<View>(R.id.buttonAddStation).setOnClickListener {
            showSearchDialog()
        }

        view.findViewById<View>(R.id.buttonImportStations).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            startActivityForResult(intent, REQUEST_CODE_IMPORT_JSON)
        }

        topbarBackButton.setOnClickListener {
            StateHelper.isPlaylistChangePending = true
            val intent = Intent(requireContext(), StreamingService::class.java)
            intent.action = StreamingService.ACTION_REFRESH_PLAYLIST
            requireContext().startService(intent)
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

    private fun showSearchDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_search_station, null)
        val editSearch = dialogView.findViewById<EditText>(R.id.editSearchQuery)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerViewSearchResults)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Station suchen oder URL eingeben")
            .setView(dialogView)
            .setNegativeButton("Abbrechen", null)
            .create()

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.startsWith("http://") || query.startsWith("https://")) {
                    lifecycleScope.launch {
                        resolveAndAddStation(query)
                        dialog.dismiss()
                    }
                } else if (query.length >= 3) {
                    lifecycleScope.launch {
                        val results = RadioBrowserHelper.searchStations(query)
                        val stationItems = results.map { it.toStationItem() }
                        recyclerView.adapter = SearchResultAdapter(stationItems) { selected ->
                            lifecycleScope.launch {
                                val finalUrl = if (selected.streamURL.endsWith(".m3u", true) || selected.streamURL.endsWith(".pls", true)) {
                                    PlaylistURLHelper.resolvePlaylistUrl(selected.streamURL) ?: selected.streamURL
                                } else {
                                    selected.streamURL
                                }

                                val station = selected.copy(streamURL = finalUrl)
                                stationList.add(station)
                                PreferencesHelper.saveStations(requireContext(), stationList)
                                adapter.notifyItemInserted(stationList.size - 1)
                                dialog.dismiss()
                            }
                        }
                    }
                }
            }
        })

        dialog.show()
    }

    private suspend fun resolveAndAddStation(url: String) {
        val finalUrl = if (url.endsWith(".m3u", true) || url.endsWith(".pls", true)) {
            PlaylistURLHelper.resolvePlaylistUrl(url) ?: url
        } else {
            url
        }

        val station = StationItem(
            uuid = UUID.randomUUID().toString(),
            stationName = finalUrl,
            streamURL = finalUrl,
            iconURL = ""
        )
        stationList.add(station)
        PreferencesHelper.saveStations(requireContext(), stationList)
        adapter.notifyItemInserted(stationList.size - 1)
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
