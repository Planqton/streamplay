package at.plankt0n.streamplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.text.Editable
import android.text.TextWatcher
import android.app.AlertDialog
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.DiscoverAdapter
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.helper.PlaylistURLHelper
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.StateHelper
import at.plankt0n.streamplay.search.RadioBrowserHelper
import at.plankt0n.streamplay.search.RadioBrowserResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class DiscoverFragment : Fragment() {

    private val stations = mutableListOf<RadioBrowserResult>()
    private lateinit var adapter: DiscoverAdapter
    private lateinit var searchField: EditText
    private lateinit var filterButton: ImageButton
    private lateinit var filterSummary: TextView
    private lateinit var backButton: ImageButton
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressLoading: ProgressBar
    private lateinit var textEmpty: TextView
    private lateinit var genreChipsContainer: LinearLayout
    private lateinit var tabTop: TextView
    private lateinit var tabLocal: TextView
    private lateinit var tabTrending: TextView

    private var selectedCountry: String? = null
    private var selectedTag: String? = null
    private var selectedLanguage: String? = null
    private var selectedCodec: String? = null
    private var selectedGenreChip: String? = null

    private var currentTab = Tab.TOP
    private var searchJob: Job? = null

    enum class Tab { TOP, LOCAL, TRENDING }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_discover, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        searchField = view.findViewById(R.id.editSearchRadio)
        filterButton = view.findViewById(R.id.buttonFilter)
        filterSummary = view.findViewById(R.id.textFilters)
        backButton = view.findViewById(R.id.arrow_back)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        progressLoading = view.findViewById(R.id.progressLoading)
        textEmpty = view.findViewById(R.id.textEmpty)
        genreChipsContainer = view.findViewById(R.id.genreChipsContainer)
        tabTop = view.findViewById(R.id.tabTop)
        tabLocal = view.findViewById(R.id.tabLocal)
        tabTrending = view.findViewById(R.id.tabTrending)

        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Setup adapter
        adapter = DiscoverAdapter(stations) { result ->
            showAddDialog(result)
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerViewDiscover)
        recycler.adapter = adapter
        recycler.layoutManager = GridLayoutManager(requireContext(), 3)

        // Setup SwipeRefresh
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.category_playback)
        )
        swipeRefresh.setOnRefreshListener {
            loadCurrentTab()
        }

        // Setup tabs
        setupTabs()

        // Setup genre chips
        setupGenreChips()

        // Setup filter button
        filterButton.setOnClickListener {
            lifecycleScope.launch {
                showLoading(true)
                val countries = RadioBrowserHelper.getCountries().map { it.name }
                val tags = RadioBrowserHelper.getTags().map { it.name }
                val languages = RadioBrowserHelper.getLanguages().map { it.name }
                val codecs = RadioBrowserHelper.getCodecs().map { it.name }
                if (!isAdded) return@launch
                showLoading(false)
                showFilterDialog(countries, tags, languages, codecs)
            }
        }

        // Setup search with debounce
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce
                    val query = s?.toString() ?: ""
                    if (query.length >= 2) {
                        performSearch(query)
                    } else if (query.isEmpty()) {
                        clearGenreSelection()
                        loadCurrentTab()
                    }
                }
            }
        })

        // Initial load
        loadCurrentTab()
    }

    private fun setupTabs() {
        tabTop.setOnClickListener { selectTab(Tab.TOP) }
        tabLocal.setOnClickListener { selectTab(Tab.LOCAL) }
        tabTrending.setOnClickListener { selectTab(Tab.TRENDING) }
    }

    private fun selectTab(tab: Tab) {
        currentTab = tab
        clearGenreSelection()
        searchField.text.clear()

        // Update tab styling
        listOf(tabTop, tabLocal, tabTrending).forEach { tv ->
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
            tv.setTypeface(null, Typeface.NORMAL)
        }

        val selectedTab = when (tab) {
            Tab.TOP -> tabTop
            Tab.LOCAL -> tabLocal
            Tab.TRENDING -> tabTrending
        }
        selectedTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary))
        selectedTab.setTypeface(null, Typeface.BOLD)

        loadCurrentTab()
    }

    private fun setupGenreChips() {
        genreChipsContainer.removeAllViews()

        RadioBrowserHelper.POPULAR_GENRES.forEach { genre ->
            val chip = TextView(requireContext()).apply {
                text = genre.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                setTextColor(ContextCompat.getColor(context, R.color.textSecondary))
                textSize = 13f
                setPadding(32, 16, 32, 16)
                setBackgroundResource(R.drawable.bg_chip)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = 8
                layoutParams = params

                setOnClickListener {
                    if (selectedGenreChip == genre) {
                        clearGenreSelection()
                        loadCurrentTab()
                    } else {
                        selectGenreChip(genre)
                    }
                }
            }
            genreChipsContainer.addView(chip)
        }
    }

    private fun selectGenreChip(genre: String) {
        selectedGenreChip = genre
        searchField.text.clear()

        // Update chip styling
        for (i in 0 until genreChipsContainer.childCount) {
            val chip = genreChipsContainer.getChildAt(i) as TextView
            val chipGenre = RadioBrowserHelper.POPULAR_GENRES[i]

            if (chipGenre == genre) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected)
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary))
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip)
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
            }
        }

        loadStationsByGenre(genre)
    }

    private fun clearGenreSelection() {
        selectedGenreChip = null

        for (i in 0 until genreChipsContainer.childCount) {
            val chip = genreChipsContainer.getChildAt(i) as TextView
            chip.setBackgroundResource(R.drawable.bg_chip)
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
        }
    }

    private fun loadCurrentTab() {
        when (currentTab) {
            Tab.TOP -> loadTopStations()
            Tab.LOCAL -> loadLocalStations()
            Tab.TRENDING -> loadTrendingStations()
        }
    }

    private fun loadTopStations() {
        lifecycleScope.launch {
            showLoading(true)
            val results = RadioBrowserHelper.getTopStations(50)
            if (!isAdded) return@launch
            updateStations(results)
            showLoading(false)
        }
    }

    private fun loadLocalStations() {
        lifecycleScope.launch {
            showLoading(true)
            // Get device locale country code
            val countryCode = Locale.getDefault().country
            val results = RadioBrowserHelper.getStationsByCountryCode(countryCode, 50)
            if (!isAdded) return@launch
            updateStations(results)
            showLoading(false)
        }
    }

    private fun loadTrendingStations() {
        lifecycleScope.launch {
            showLoading(true)
            val results = RadioBrowserHelper.getTopClickStations(50)
            if (!isAdded) return@launch
            updateStations(results)
            showLoading(false)
        }
    }

    private fun loadStationsByGenre(genre: String) {
        lifecycleScope.launch {
            showLoading(true)
            val results = RadioBrowserHelper.getStationsByTag(genre, 50)
            if (!isAdded) return@launch
            updateStations(results)
            showLoading(false)
        }
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
                loadCurrentTab()
            }
            .show()
    }

    private fun updateFilterSummary() {
        val parts = mutableListOf<String>()
        selectedCountry?.let { parts += getString(R.string.filter_country) + ": " + it }
        selectedTag?.let { parts += getString(R.string.filter_genre) + ": " + it }
        selectedLanguage?.let { parts += getString(R.string.filter_language) + ": " + it }
        selectedCodec?.let { parts += getString(R.string.filter_codec) + ": " + it }

        if (parts.isEmpty()) {
            filterSummary.visibility = View.GONE
        } else {
            filterSummary.text = parts.joinToString(" • ")
            filterSummary.visibility = View.VISIBLE
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank() && selectedCountry == null && selectedTag == null && selectedLanguage == null && selectedCodec == null) {
            loadCurrentTab()
            return
        }

        lifecycleScope.launch {
            showLoading(true)
            val results = RadioBrowserHelper.searchStations(query, selectedCountry, selectedTag, selectedLanguage, selectedCodec)
            if (!isAdded) return@launch
            updateStations(results)
            showLoading(false)
        }
    }

    private fun updateStations(results: List<RadioBrowserResult>) {
        stations.clear()
        stations.addAll(results)
        adapter.notifyDataSetChanged()

        textEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        swipeRefresh.isRefreshing = false
    }

    private fun showLoading(loading: Boolean) {
        if (loading && !swipeRefresh.isRefreshing) {
            progressLoading.visibility = View.VISIBLE
        } else {
            progressLoading.visibility = View.GONE
        }

        if (!loading) {
            swipeRefresh.isRefreshing = false
        }
    }

    private fun showAddDialog(result: RadioBrowserResult) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_add_title))
            .setMessage(getString(R.string.confirm_add_message, result.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch { addStation(result) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        searchJob = null
        super.onDestroyView()
    }

    private suspend fun addStation(item: RadioBrowserResult) {
        val context = context ?: return
        val list = PreferencesHelper.getStations(context).toMutableList()

        // Resolve playlist URLs if needed
        val finalUrl = if (item.url.endsWith(".m3u", true) || item.url.endsWith(".pls", true)) {
            PlaylistURLHelper.resolvePlaylistUrl(item.url) ?: item.url
        } else {
            item.url
        }

        val station = item.toStationItem().copy(
            uuid = UUID.randomUUID().toString(),
            streamURL = finalUrl
        )
        list.add(station)
        PreferencesHelper.saveStations(context, list)

        // Trigger playlist reload in service
        StateHelper.isPlaylistChangePending = true
        val intent = android.content.Intent(context, StreamingService::class.java)
        intent.action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
        context.startService(intent)
    }
}
