package at.plankt0n.streamplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.text.Editable
import android.text.TextWatcher
import android.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.DiscoverAdapter
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.helper.PlaylistURLHelper
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.StateHelper
import at.plankt0n.streamplay.search.RadioBrowserHelper
import kotlinx.coroutines.launch
import java.util.UUID

class DiscoverFragment : Fragment() {

    private val stations = mutableListOf<StationItem>()
    private lateinit var adapter: DiscoverAdapter
    private lateinit var searchField: EditText
    private lateinit var filterButton: ImageButton
    private lateinit var searchButton: ImageButton
    private lateinit var filterSummary: TextView
    private lateinit var backButton: ImageButton
    private var selectedCountry: String? = null
    private var selectedTag: String? = null
    private var selectedLanguage: String? = null
    private var selectedCodec: String? = null

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
        filterButton = view.findViewById(R.id.buttonFilter)
        searchButton = view.findViewById(R.id.buttonSearchRadio)
        filterSummary = view.findViewById(R.id.textFilters)
        backButton = view.findViewById(R.id.arrow_back)

        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        adapter = DiscoverAdapter(stations) { station ->
            AlertDialog.Builder(requireContext())
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

        filterButton.setOnClickListener {
            lifecycleScope.launch {
                val countries = RadioBrowserHelper.getCountries().map { it.name }
                val tags = RadioBrowserHelper.getTags().map { it.name }
                val languages = RadioBrowserHelper.getLanguages().map { it.name }
                val codecs = RadioBrowserHelper.getCodecs().map { it.name }
                showFilterDialog(countries, tags, languages, codecs)
            }
        }

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.length >= 2 || selectedCountry != null || selectedTag != null || selectedLanguage != null || selectedCodec != null) {
                    performSearch(query)
                } else if (query.isEmpty() && selectedCountry == null && selectedTag == null && selectedLanguage == null && selectedCodec == null) {
                    performLoadTop()
                }
            }
        })

        updateFilterSummary()
        performLoadTop()
    }

    private fun showFilterDialog(countries: List<String>, tags: List<String>, languages: List<String>, codecs: List<String>) {
        val view = layoutInflater.inflate(R.layout.dialog_filters, null)
        val countrySpinner = view.findViewById<Spinner>(R.id.spinnerCountry)
        val tagSpinner = view.findViewById<Spinner>(R.id.spinnerTag)
        val languageSpinner = view.findViewById<Spinner>(R.id.spinnerLanguage)
        val codecSpinner = view.findViewById<Spinner>(R.id.spinnerCodec)

        val countryOptions = listOf(getString(R.string.clear_filters)) + countries
        val tagOptions = listOf(getString(R.string.clear_filters)) + tags
        val languageOptions = listOf(getString(R.string.clear_filters)) + languages
        val codecOptions = listOf(getString(R.string.clear_filters)) + codecs

        countrySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, countryOptions)
        tagSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, tagOptions)
        languageSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, languageOptions)
        codecSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, codecOptions)

        countrySpinner.setSelection(selectedCountry?.let { countries.indexOf(it) + 1 } ?: 0)
        tagSpinner.setSelection(selectedTag?.let { tags.indexOf(it) + 1 } ?: 0)
        languageSpinner.setSelection(selectedLanguage?.let { languages.indexOf(it) + 1 } ?: 0)
        codecSpinner.setSelection(selectedCodec?.let { codecs.indexOf(it) + 1 } ?: 0)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.filter_dialog_title))
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                selectedCountry = countrySpinner.selectedItemPosition.takeIf { it > 0 }?.let { countryOptions[it] }
                selectedTag = tagSpinner.selectedItemPosition.takeIf { it > 0 }?.let { tagOptions[it] }
                selectedLanguage = languageSpinner.selectedItemPosition.takeIf { it > 0 }?.let { languageOptions[it] }
                selectedCodec = codecSpinner.selectedItemPosition.takeIf { it > 0 }?.let { codecOptions[it] }
                updateFilterSummary()
                performSearch(searchField.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.clear_filters) { _, _ ->
                selectedCountry = null
                selectedTag = null
                selectedLanguage = null
                selectedCodec = null
                updateFilterSummary()
                performSearch(searchField.text.toString())
            }
            .show()
    }

    private fun updateFilterSummary() {
        val parts = mutableListOf<String>()
        selectedCountry?.let { parts += getString(R.string.filter_country) + ": " + it }
        selectedTag?.let { parts += getString(R.string.filter_genre) + ": " + it }
        selectedLanguage?.let { parts += getString(R.string.filter_language) + ": " + it }
        selectedCodec?.let { parts += getString(R.string.filter_codec) + ": " + it }
        filterSummary.text = if (parts.isEmpty()) {
            getString(R.string.filter_summary_none)
        } else {
            parts.joinToString(", ")
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank() && selectedCountry == null && selectedTag == null && selectedLanguage == null && selectedCodec == null) return
        lifecycleScope.launch {
            val results = RadioBrowserHelper.searchStations(query, selectedCountry, selectedTag, selectedLanguage, selectedCodec)
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

        // trigger playlist reload in service and update UI
        StateHelper.isPlaylistChangePending = true
        val intent = android.content.Intent(requireContext(), StreamingService::class.java)
        intent.action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
        requireContext().startService(intent)
    }
}
