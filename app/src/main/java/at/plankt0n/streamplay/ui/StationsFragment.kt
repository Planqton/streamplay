package at.plankt0n.streamplay.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
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
import at.plankt0n.streamplay.search.RadioBrowserHelper
import kotlinx.coroutines.launch
import java.util.*
import at.plankt0n.streamplay.helper.StateHelper

class StationsFragment : Fragment() {

    private lateinit var stationList: MutableList<StationItem>
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StationListAdapter
    private lateinit var topbarBackButton: ImageButton
    private lateinit var topbarTitle: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_stations, container, false)

        topbarBackButton = view.findViewById(R.id.arrow_back)
        topbarTitle = view.findViewById(R.id.topbar_title)

        // Lade gespeicherte Stationen
        stationList = PreferencesHelper.getStations(requireContext()).toMutableList()

        recyclerView = view.findViewById(R.id.recyclerViewStations)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = StationListAdapter(stationList)
        recyclerView.adapter = adapter

        // Swipe-to-Delete
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                stationList.removeAt(position)
                PreferencesHelper.saveStations(requireContext(), stationList)
                adapter.notifyItemRemoved(position)
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Hinzufügen
        view.findViewById<View>(R.id.buttonAddStation).setOnClickListener {
            showSearchDialog()
        }

        // Zurück-Button
        topbarBackButton.setOnClickListener {
            // 1️⃣ Playlist im StreamingService aktualisieren
            StateHelper.isPlaylistChangePending = true
            val intent = Intent(requireContext(), StreamingService::class.java)
            intent.action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
            requireContext().startService(intent)

            // 2️⃣ Zurück navigieren
            parentFragmentManager.popBackStack()
        }

        return view
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
                    // Direkte URL – Playlist prüfen und auflösen
                    lifecycleScope.launch {
                        resolveAndAddStation(query)
                        dialog.dismiss()
                    }
                } else if (query.length >= 3) {
                    // Live-Suche mit der API
                    lifecycleScope.launch {
                        val results = RadioBrowserHelper.searchStations(query)
                        val stationItems = results.map { it.toStationItem() }
                        recyclerView.adapter = SearchResultAdapter(stationItems) { selected ->
                            // Prüfen, ob der Stream eine Playlist ist, auflösen
                            lifecycleScope.launch {
                                val finalUrl = if (selected.streamURL.endsWith(".m3u", true) || selected.streamURL.endsWith(".pls", true)) {
                                    PlaylistURLHelper.resolvePlaylistUrl(selected.streamURL) ?: selected.streamURL
                                } else {
                                    selected.streamURL
                                }

                                // Finalen Direktstream setzen
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
        // Prüfen, ob es eine Playlist ist
        val finalUrl = if (url.endsWith(".m3u", true) || url.endsWith(".pls", true)) {
            PlaylistURLHelper.resolvePlaylistUrl(url) ?: url // Fallback: Original-URL, falls nichts gefunden
        } else {
            url
        }

        val station = StationItem(
            uuid = UUID.randomUUID().toString(),
            stationName = finalUrl, // vorerst URL als Name
            streamURL = finalUrl,
            iconURL = ""
        )
        stationList.add(station)
        PreferencesHelper.saveStations(requireContext(), stationList)
        adapter.notifyItemInserted(stationList.size - 1)
    }
}
