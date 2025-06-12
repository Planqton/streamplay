package at.plankt0n.streamplay.ui

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.SearchResultAdapter
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.search.RadioBrowserHelper
import kotlinx.coroutines.launch

class RadioBrowserFragment : Fragment() {

    private val results = mutableListOf<StationItem>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SearchResultAdapter
    private lateinit var searchInput: EditText
    private lateinit var topbarBackButton: ImageButton
    private lateinit var topbarTitle: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_radio_browser, container, false)

        topbarBackButton = view.findViewById(R.id.arrow_back)
        topbarTitle = view.findViewById(R.id.topbar_title)
        topbarTitle.text = getString(R.string.fragment_radio_browser_title)

        searchInput = view.findViewById(R.id.edit_search)
        recyclerView = view.findViewById(R.id.recyclerViewRadioResults)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = SearchResultAdapter(results) { selected ->
            val current = PreferencesHelper.getStations(requireContext()).toMutableList()
            current.add(selected)
            PreferencesHelper.saveStations(requireContext(), current)
        }
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            val top = RadioBrowserHelper.getTopStations(20)
            results.clear()
            results.addAll(top.map { it.toStationItem() })
            adapter.notifyDataSetChanged()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: return
                if (query.length >= 3) {
                    lifecycleScope.launch {
                        val resultsApi = RadioBrowserHelper.searchStations(query)
                        results.clear()
                        results.addAll(resultsApi.map { it.toStationItem() })
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        })

        topbarBackButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return view
    }
}
