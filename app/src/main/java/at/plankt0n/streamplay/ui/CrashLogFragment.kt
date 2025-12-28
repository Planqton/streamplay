package at.plankt0n.streamplay.ui

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashLogFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var fabClear: FloatingActionButton
    private lateinit var fabExport: FloatingActionButton
    private lateinit var adapter: CrashLogAdapter
    private var crashLogs = mutableListOf<File>()

    // Selection mode
    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<File>()

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitSelectionMode()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_crash_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        // Setup topbar
        val backButton = view.findViewById<ImageButton>(R.id.arrow_back)
        backButton.setOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        val topbarTitle = view.findViewById<TextView>(R.id.topbar_title)
        topbarTitle.text = getString(R.string.crash_log_title)

        // Setup RecyclerView
        recyclerView = view.findViewById(R.id.recyclerCrashLog)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = CrashLogAdapter(
            onItemClick = { file ->
                if (isSelectionMode) {
                    toggleSelection(file)
                } else {
                    showCrashLogContent(file)
                }
            },
            onItemLongClick = { file ->
                if (!isSelectionMode) {
                    enterSelectionMode()
                }
                toggleSelection(file)
            },
            onShareClick = { file -> shareCrashLog(file) },
            isSelected = { file -> selectedItems.contains(file) },
            isSelectionMode = { isSelectionMode }
        )
        recyclerView.adapter = adapter

        // Setup empty state
        emptyState = view.findViewById(R.id.emptyState)

        // Setup FAB for clear
        fabClear = view.findViewById(R.id.fabClearLogs)
        fabClear.setOnClickListener {
            showClearConfirmDialog()
        }

        // Setup FAB for export
        fabExport = view.findViewById(R.id.fabExport)
        fabExport.setOnClickListener {
            exportSelectedLogs()
        }

        loadCrashLogs()
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        backPressedCallback.isEnabled = true
        updateSelectionUI()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        backPressedCallback.isEnabled = false
        updateSelectionUI()
    }

    private fun toggleSelection(file: File) {
        if (selectedItems.contains(file)) {
            selectedItems.remove(file)
            if (selectedItems.isEmpty()) {
                exitSelectionMode()
                return
            }
        } else {
            selectedItems.add(file)
        }
        updateSelectionUI()
    }

    private fun updateSelectionUI() {
        adapter.notifyDataSetChanged()
        fabExport.visibility = if (isSelectionMode && selectedItems.isNotEmpty()) View.VISIBLE else View.GONE
        fabClear.visibility = if (isSelectionMode) View.GONE else if (crashLogs.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadCrashLogs() {
        val dir = File(requireContext().getExternalFilesDir(null), "crashlogs")
        crashLogs = if (dir.exists()) {
            dir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() }
                ?.toMutableList() ?: mutableListOf()
        } else {
            mutableListOf()
        }

        adapter.setItems(crashLogs)
        updateEmptyState(crashLogs.isEmpty())
    }

    private fun showCrashLogContent(file: File) {
        val content = try {
            file.readText()
        } catch (e: Exception) {
            getString(R.string.crash_log_read_error)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(file.name)
            .setMessage(content)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.crash_log_share) { _, _ ->
                shareCrashLog(file)
            }
            .show()
    }

    private fun shareCrashLog(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "StreamPlay Crash Log: ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.crash_log_share)))
        } catch (e: Exception) {
            view?.let { Snackbar.make(it, R.string.crash_log_share_error, Snackbar.LENGTH_SHORT).show() }
        }
    }

    private fun exportSelectedLogs() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportDir = File(downloadsDir, "StreamPlay")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            var exportedCount = 0
            for (file in selectedItems) {
                val destFile = File(exportDir, file.name)
                file.copyTo(destFile, overwrite = true)
                exportedCount++
            }

            view?.let { v ->
                Snackbar.make(
                    v,
                    getString(R.string.crash_log_exported, exportedCount),
                    Snackbar.LENGTH_LONG
                ).show()
            }

            exitSelectionMode()
        } catch (e: Exception) {
            view?.let { Snackbar.make(it, R.string.crash_log_export_error, Snackbar.LENGTH_SHORT).show() }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            fabClear.visibility = View.GONE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            fabClear.visibility = if (isSelectionMode) View.GONE else View.VISIBLE
        }
    }

    private fun showClearConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.crash_log_confirm_clear_title)
            .setMessage(R.string.crash_log_confirm_clear_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear_logs) { _, _ ->
                clearLogs()
            }
            .show()
    }

    private fun clearLogs() {
        val dir = File(requireContext().getExternalFilesDir(null), "crashlogs")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
        crashLogs.clear()
        adapter.setItems(crashLogs)
        updateEmptyState(true)
        view?.let { Snackbar.make(it, R.string.crash_log_cleared, Snackbar.LENGTH_SHORT).show() }
    }

    // Inner Adapter class
    private class CrashLogAdapter(
        private val onItemClick: (File) -> Unit,
        private val onItemLongClick: (File) -> Unit,
        private val onShareClick: (File) -> Unit,
        private val isSelected: (File) -> Boolean,
        private val isSelectionMode: () -> Boolean
    ) : RecyclerView.Adapter<CrashLogAdapter.ViewHolder>() {

        private var items = listOf<File>()

        fun setItems(newItems: List<File>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_crash_log, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleText: TextView = itemView.findViewById(R.id.crashLogTitle)
            private val dateText: TextView = itemView.findViewById(R.id.crashLogDate)
            private val shareButton: ImageButton = itemView.findViewById(R.id.btnShare)
            private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)

            fun bind(file: File) {
                titleText.text = file.name.removeSuffix(".txt")

                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                dateText.text = dateFormat.format(Date(file.lastModified()))

                // Selection mode UI
                val inSelectionMode = isSelectionMode()
                checkBox.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
                checkBox.isChecked = isSelected(file)
                shareButton.visibility = if (inSelectionMode) View.GONE else View.VISIBLE

                itemView.setOnClickListener { onItemClick(file) }
                itemView.setOnLongClickListener {
                    onItemLongClick(file)
                    true
                }
                checkBox.setOnClickListener { onItemClick(file) }
                shareButton.setOnClickListener { onShareClick(file) }
            }
        }
    }
}
