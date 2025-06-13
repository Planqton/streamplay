package at.plankt0n.streamplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.DiscoverAdapter
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.PlaylistURLHelper
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.search.RadioBrowserHelper
import kotlinx.coroutines.launch
import java.util.UUID

class DiscoverFragment : Fragment() {

    private val stations = mutableListOf<StationItem>()
    private lateinit var adapter: DiscoverAdapter
    private lateinit var searchField: EditText
    private lateinit var searchButton: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_discover, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchField = view.findViewById(R.id.editSearchRadio)
        searchButton = view.findViewById(R.id.buttonSearchRadio)

        adapter = DiscoverAdapter(stations) { station ->
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.confirm_add_title))
                .setMessage(getString(R.string.confirm_add_message, station.stationName))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch { addStation(station) }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerViewDiscover)
        recycler.adapter = adapter
        recycler.layoutManager = GridLayoutManager(requireContext(), 3)

        searchButton.setOnClickListener { performSearch(searchField.text.toString()) }

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.length >= 2) {
                    performSearch(query)
                } else if (query.isEmpty()) {
                    performLoadTop()
                }
            }
        })

        performLoadTop()
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return
        lifecycleScope.launch {
            val results = RadioBrowserHelper.searchStations(query)
            stations.clear()
            stations.addAll(results.map { it.toStationItem() })
            adapter.notifyDataSetChanged()
        }
    }

    private fun performLoadTop() {
        lifecycleScope.launch {
            val results = RadioBrowserHelper.getTopStations(50)
            stations.clear()
            stations.addAll(results.map { it.toStationItem() })
            adapter.notifyDataSetChanged()
        }
    }

    private suspend fun addStation(item: StationItem) {
        val list = PreferencesHelper.getStations(requireContext()).toMutableList()
        val finalUrl = if (item.streamURL.endsWith(".m3u", true) || item.streamURL.endsWith(".pls", true)) {
            PlaylistURLHelper.resolvePlaylistUrl(item.streamURL) ?: item.streamURL
        } else {
            item.streamURL
        }
        val station = item.copy(uuid = UUID.randomUUID().toString(), streamURL = finalUrl)
        list.add(station)
        PreferencesHelper.saveStations(requireContext(), list)
    }
}
