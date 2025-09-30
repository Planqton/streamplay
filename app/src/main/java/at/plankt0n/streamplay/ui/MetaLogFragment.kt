package at.plankt0n.streamplay.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.MetaLogAdapter
import at.plankt0n.streamplay.data.MetaLogEntry
import at.plankt0n.streamplay.helper.MetaLogHelper
import at.plankt0n.streamplay.helper.MetaLogPrintAdapter

class MetaLogFragment : Fragment() {
    private lateinit var adapter: MetaLogAdapter
    private lateinit var searchField: EditText
    private lateinit var manualFilter: CheckBox
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
        manualFilter = view.findViewById(R.id.checkManualFilter)
        manualFilter.setOnCheckedChangeListener { _, isChecked ->
            showManualOnly = isChecked
            filterLogs(searchField.text.toString())
        }
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterLogs(s?.toString() ?: "")
            }
        })

        view.findViewById<Button>(R.id.buttonClearLogs).setOnClickListener {
            MetaLogHelper.clear(requireContext())
            allLogs.clear()
            filterLogs("")
        }

        view.findViewById<Button>(R.id.buttonPrintLogs).setOnClickListener {
            printLogs()
        }
    }

    override fun onResume() {
        super.onResume()
        allLogs = MetaLogHelper.getLogs(requireContext())
        filterLogs(searchField.text.toString())
    }

    private fun filterLogs(query: String) {
        val lower = query.lowercase()
        val filtered = allLogs.filter {
            val matchesQuery = if (query.isBlank()) true else (
                it.station.contains(lower, true) ||
                it.title.contains(lower, true) ||
                it.artist.contains(lower, true) ||
                it.formattedTime().contains(lower, true)
            )
            val manualOk = !showManualOnly || it.manual
            matchesQuery && manualOk
        }
        adapter.setItems(filtered)
    }

    private fun printLogs() {
        val currentLogs = adapter.getItems()
        if (currentLogs.isEmpty()) {
            Toast.makeText(requireContext(), R.string.print_logs_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val printManager = requireContext().getSystemService(PrintManager::class.java)
        if (printManager == null) {
            Toast.makeText(requireContext(), R.string.print_logs_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val printAdapter = MetaLogPrintAdapter(requireContext(), currentLogs)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        val jobName = getString(R.string.print_logs_job_name)
        printManager.print(jobName, printAdapter, attributes)
    }
}
