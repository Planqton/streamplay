package at.plankt0n.streamplay.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.MetaLogAdapter
import at.plankt0n.streamplay.data.MetaLogEntry
import at.plankt0n.streamplay.helper.MetaLogHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MetaLogFragment : Fragment() {
    private lateinit var adapter: MetaLogAdapter
    private lateinit var searchField: TextInputEditText
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var chipAll: Chip
    private lateinit var chipManual: Chip
    private lateinit var chipTimeAll: Chip
    private lateinit var chipTimeLastHour: Chip
    private lateinit var chipTimeToday: Chip
    private lateinit var chipTimeYesterday: Chip
    private lateinit var chipTimeWeek: Chip
    private lateinit var chipTimeMonth: Chip
    private lateinit var chipTimeCustom: Chip
    private lateinit var customTimeRangeContainer: LinearLayout
    private lateinit var btnTimeFrom: MaterialButton
    private lateinit var btnTimeTo: MaterialButton
    private lateinit var fabClear: FloatingActionButton

    private var showManualOnly = false
    private var timeFilter = TimeFilter.ALL
    private var allLogs = mutableListOf<MetaLogEntry>()

    // Custom time range (hour and minute for today)
    private var customFromHour: Int = 0
    private var customFromMinute: Int = 0
    private var customToHour: Int = 23
    private var customToMinute: Int = 59

    enum class TimeFilter {
        ALL, LAST_HOUR, TODAY, YESTERDAY, WEEK, MONTH, CUSTOM
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_meta_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup topbar
        val backButton = view.findViewById<ImageButton>(R.id.arrow_back)
        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val topbarTitle = view.findViewById<TextView>(R.id.topbar_title)
        topbarTitle.text = getString(R.string.bottom_sheet_logs_text)

        // Setup adapter
        adapter = MetaLogAdapter { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        // Setup RecyclerView
        recyclerView = view.findViewById(R.id.recyclerMetaLog)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Setup SwipeRefresh
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeResources(R.color.category_player)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.cardBackground)
        swipeRefresh.setOnRefreshListener {
            loadLogs()
            swipeRefresh.isRefreshing = false
        }

        // Setup empty state
        emptyState = view.findViewById(R.id.emptyState)

        // Setup search field
        searchField = view.findViewById(R.id.editSearchLogs)
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterLogs(s?.toString() ?: "")
            }
        })

        // Setup type filter chips
        chipAll = view.findViewById(R.id.chipAll)
        chipManual = view.findViewById(R.id.chipManual)

        chipAll.setOnClickListener {
            showManualOnly = false
            chipAll.isChecked = true
            chipManual.isChecked = false
            filterLogs(searchField.text?.toString() ?: "")
        }

        chipManual.setOnClickListener {
            showManualOnly = true
            chipManual.isChecked = true
            chipAll.isChecked = false
            filterLogs(searchField.text?.toString() ?: "")
        }

        // Setup time filter chips
        chipTimeAll = view.findViewById(R.id.chipTimeAll)
        chipTimeLastHour = view.findViewById(R.id.chipTimeLastHour)
        chipTimeToday = view.findViewById(R.id.chipTimeToday)
        chipTimeYesterday = view.findViewById(R.id.chipTimeYesterday)
        chipTimeWeek = view.findViewById(R.id.chipTimeWeek)
        chipTimeMonth = view.findViewById(R.id.chipTimeMonth)
        chipTimeCustom = view.findViewById(R.id.chipTimeCustom)
        customTimeRangeContainer = view.findViewById(R.id.customTimeRangeContainer)
        btnTimeFrom = view.findViewById(R.id.btnTimeFrom)
        btnTimeTo = view.findViewById(R.id.btnTimeTo)

        val timeChips = listOf(chipTimeAll, chipTimeLastHour, chipTimeToday, chipTimeYesterday, chipTimeWeek, chipTimeMonth, chipTimeCustom)

        fun selectTimeChip(selected: Chip, filter: TimeFilter) {
            timeFilter = filter
            timeChips.forEach { it.isChecked = it == selected }
            customTimeRangeContainer.visibility = if (filter == TimeFilter.CUSTOM) View.VISIBLE else View.GONE
            filterLogs(searchField.text?.toString() ?: "")
        }

        chipTimeAll.setOnClickListener { selectTimeChip(chipTimeAll, TimeFilter.ALL) }
        chipTimeLastHour.setOnClickListener { selectTimeChip(chipTimeLastHour, TimeFilter.LAST_HOUR) }
        chipTimeToday.setOnClickListener { selectTimeChip(chipTimeToday, TimeFilter.TODAY) }
        chipTimeYesterday.setOnClickListener { selectTimeChip(chipTimeYesterday, TimeFilter.YESTERDAY) }
        chipTimeWeek.setOnClickListener { selectTimeChip(chipTimeWeek, TimeFilter.WEEK) }
        chipTimeMonth.setOnClickListener { selectTimeChip(chipTimeMonth, TimeFilter.MONTH) }
        chipTimeCustom.setOnClickListener { selectTimeChip(chipTimeCustom, TimeFilter.CUSTOM) }

        // Setup custom time range buttons
        btnTimeFrom.setOnClickListener { showTimePicker(true) }
        btnTimeTo.setOnClickListener { showTimePicker(false) }

        // Initialize button texts
        updateTimeButtonTexts()

        // Setup FAB for clear
        fabClear = view.findViewById(R.id.fabClearLogs)
        fabClear.setOnClickListener {
            showClearConfirmDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        loadLogs()
    }

    private fun showTimePicker(isFromTime: Boolean) {
        val currentHour = if (isFromTime) customFromHour else customToHour
        val currentMinute = if (isFromTime) customFromMinute else customToMinute

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(currentHour)
            .setMinute(currentMinute)
            .setTitleText(if (isFromTime) getString(R.string.meta_log_time_from) else getString(R.string.meta_log_time_to))
            .build()

        picker.addOnPositiveButtonClickListener {
            if (isFromTime) {
                customFromHour = picker.hour
                customFromMinute = picker.minute
            } else {
                customToHour = picker.hour
                customToMinute = picker.minute
            }
            updateTimeButtonTexts()
            filterLogs(searchField.text?.toString() ?: "")
        }

        picker.show(parentFragmentManager, "time_picker")
    }

    private fun updateTimeButtonTexts() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val fromCal = Calendar.getInstance()
        fromCal.set(Calendar.HOUR_OF_DAY, customFromHour)
        fromCal.set(Calendar.MINUTE, customFromMinute)
        btnTimeFrom.text = timeFormat.format(fromCal.time)

        val toCal = Calendar.getInstance()
        toCal.set(Calendar.HOUR_OF_DAY, customToHour)
        toCal.set(Calendar.MINUTE, customToMinute)
        btnTimeTo.text = timeFormat.format(toCal.time)
    }

    private fun loadLogs() {
        allLogs = MetaLogHelper.getLogs(requireContext())
        filterLogs(searchField.text?.toString() ?: "")
    }

    private fun filterLogs(query: String) {
        val lower = query.lowercase()
        val now = System.currentTimeMillis()

        val filtered = allLogs.filter { entry ->
            // Text search filter
            val matchesQuery = if (query.isBlank()) true else (
                entry.station.contains(lower, true) ||
                entry.title.contains(lower, true) ||
                entry.artist.contains(lower, true)
            )

            // Manual filter
            val manualOk = !showManualOnly || entry.manual

            // Time filter
            val timeOk = when (timeFilter) {
                TimeFilter.ALL -> true
                TimeFilter.LAST_HOUR -> entry.timestamp >= now - (60 * 60 * 1000)
                TimeFilter.TODAY -> entry.isToday()
                TimeFilter.YESTERDAY -> entry.isYesterday()
                TimeFilter.WEEK -> entry.timestamp >= getStartOfWeek()
                TimeFilter.MONTH -> entry.timestamp >= getStartOfMonth()
                TimeFilter.CUSTOM -> isInCustomTimeRange(entry.timestamp)
            }

            matchesQuery && manualOk && timeOk
        }

        adapter.setItems(filtered)
        updateEmptyState(filtered.isEmpty())
    }

    private fun isInCustomTimeRange(timestamp: Long): Boolean {
        val entryCal = Calendar.getInstance()
        entryCal.timeInMillis = timestamp

        val entryHour = entryCal.get(Calendar.HOUR_OF_DAY)
        val entryMinute = entryCal.get(Calendar.MINUTE)
        val entryTimeInMinutes = entryHour * 60 + entryMinute

        val fromTimeInMinutes = customFromHour * 60 + customFromMinute
        val toTimeInMinutes = customToHour * 60 + customToMinute

        return if (fromTimeInMinutes <= toTimeInMinutes) {
            // Normal range (e.g., 08:00 - 18:00)
            entryTimeInMinutes in fromTimeInMinutes..toTimeInMinutes
        } else {
            // Overnight range (e.g., 22:00 - 06:00)
            entryTimeInMinutes >= fromTimeInMinutes || entryTimeInMinutes <= toTimeInMinutes
        }
    }

    private fun getStartOfWeek(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            fabClear.visibility = View.GONE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            fabClear.visibility = View.VISIBLE
        }
    }

    private fun showClearConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.meta_log_confirm_clear_title)
            .setMessage(R.string.meta_log_confirm_clear_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear_logs) { _, _ ->
                clearLogs()
            }
            .show()
    }

    private fun clearLogs() {
        context?.let { ctx ->
            MetaLogHelper.clear(ctx)
            allLogs.clear()
            filterLogs("")
            view?.let { v -> Snackbar.make(v, R.string.meta_log_cleared, Snackbar.LENGTH_SHORT).show() }
        }
    }
}
