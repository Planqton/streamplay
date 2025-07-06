package at.plankt0n.streamplay.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ToggleButton
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.MetaLogAdapter
import at.plankt0n.streamplay.data.MetaLogEntry
import at.plankt0n.streamplay.helper.MetaLogHelper

class MetaLogFragment : Fragment() {
    private lateinit var adapter: MetaLogAdapter
    private lateinit var searchField: EditText
    private lateinit var manualToggle: ToggleButton
    private var showManualOnly = false
    private var allLogs = mutableListOf<MetaLogEntry>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_meta_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backButton = view.findViewById<ImageButton>(R.id.arrow_back)
        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        adapter = MetaLogAdapter { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerMetaLog)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        searchField = view.findViewById(R.id.editSearchLogs)
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterLogs(s?.toString() ?: "")
            }
        })

        manualToggle = view.findViewById(R.id.buttonToggleManual)
        manualToggle.setOnClickListener {
            showManualOnly = manualToggle.isChecked
            filterLogs(searchField.text.toString())
        }

        view.findViewById<Button>(R.id.buttonClearLogs).setOnClickListener {
            MetaLogHelper.clear(requireContext())
            allLogs.clear()
            filterLogs("")
        }
    }

    override fun onResume() {
        super.onResume()
        allLogs = MetaLogHelper.getLogs(requireContext())
        filterLogs(searchField.text.toString())
    }

    private fun filterLogs(query: String) {
        var baseList = allLogs
        if (showManualOnly) {
            baseList = baseList.filter { it.manual }.toMutableList()
        }
        if (query.isBlank()) {
            adapter.setItems(baseList)
            return
        }
        val lower = query.lowercase()
        val filtered = baseList.filter {
            it.station.contains(lower, true) ||
            it.title.contains(lower, true) ||
            it.artist.contains(lower, true) ||
            it.formattedTime().contains(lower, true)
        }
        adapter.setItems(filtered)
    }
}
