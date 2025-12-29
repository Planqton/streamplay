package at.plankt0n.streamplay.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.adapter.StationListAdapter
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.PlaylistURLHelper
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.MediaServiceController
import at.plankt0n.streamplay.helper.StateHelper
import at.plankt0n.streamplay.MainActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.activity.OnBackPressedCallback
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import at.plankt0n.streamplay.helper.StationImportHelper
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import at.plankt0n.streamplay.Keys
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class StationsFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var stationList: MutableList<StationItem>
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StationListAdapter
    private lateinit var mediaServiceController: MediaServiceController
    private lateinit var topbarBackButton: ImageButton
    private lateinit var topbarTitle: TextView
    private var backPressedCallback: OnBackPressedCallback? = null
    private lateinit var stationPrefs: SharedPreferences
    private var hasChanges: Boolean = false
    private var isReceiverRegistered = false
    private lateinit var emptyStateContainer: View
    private lateinit var fabAddStation: FloatingActionButton
    private lateinit var listNameLabel: TextView
    private lateinit var buttonEditListName: ImageButton

    private val stationsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Keys.ACTION_STATIONS_UPDATED) {
                refreshStationList()
            }
        }
    }

    private fun refreshStationList() {
        stationList.clear()
        stationList.addAll(PreferencesHelper.getStations(requireContext()))
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (stationList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateContainer.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateContainer.visibility = View.GONE
        }
    }

    private fun openDiscoverFragment() {
        (activity as? MainActivity)?.let { mainActivity ->
            val currentFragment = mainActivity.supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment != null) {
                mainActivity.supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .hide(currentFragment)
                    .add(R.id.fragment_container, DiscoverFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

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
        stationPrefs = requireContext().getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)

        // Register for station update broadcasts (only if not already registered)
        if (!isReceiverRegistered) {
            LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(stationsUpdateReceiver, IntentFilter(Keys.ACTION_STATIONS_UPDATED))
            isReceiverRegistered = true
        }

        recyclerView = view.findViewById(R.id.recyclerViewStations)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Empty State initialisieren
        emptyStateContainer = view.findViewById(R.id.empty_state_container)
        fabAddStation = view.findViewById(R.id.fab_add_station)
        fabAddStation.setOnClickListener {
            openDiscoverFragment()
        }

        // Listenname initialisieren
        listNameLabel = view.findViewById(R.id.list_name_label)
        buttonEditListName = view.findViewById(R.id.button_edit_list_name)
        updateListNameLabel()
        buttonEditListName.setOnClickListener {
            showRenameListDialog()
        }

        val simpleCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.moveItem(from, to)
                return true
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                PreferencesHelper.saveStations(requireContext(), stationList)
                hasChanges = true
                refreshPlaylist()
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                stationList.removeAt(position)
                PreferencesHelper.saveStations(requireContext(), stationList)
                adapter.notifyItemRemoved(position)
                hasChanges = true
                refreshPlaylist()
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    val itemView = viewHolder.itemView
                    val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)
                    val background = ColorDrawable(Color.RED)
                    background.setBounds(
                        itemView.right + dX.toInt(),
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                    background.draw(c)

                    icon?.let {
                        val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                        val iconTop = itemView.top + iconMargin
                        val iconLeft = itemView.right - iconMargin - it.intrinsicWidth
                        val iconRight = itemView.right - iconMargin
                        val iconBottom = iconTop + it.intrinsicHeight
                        it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        it.draw(c)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(simpleCallback)

        mediaServiceController = MediaServiceController(requireContext())

        adapter = StationListAdapter(stationList, { holder ->
            itemTouchHelper.startDrag(holder)
        }, {
            hasChanges = true
            refreshPlaylist()
        }, { index ->
            mediaServiceController.playAtIndex(index)
        }) { station ->
            pinStationToHome(station)
        }
        recyclerView.adapter = adapter
        itemTouchHelper.attachToRecyclerView(recyclerView)

        val parentPager = parentFragment?.view?.findViewById<ViewPager2>(R.id.main_view_pager)
        recyclerView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    parentPager?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    parentPager?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        mediaServiceController.initializeAndConnect(
            onConnected = { controller ->
                adapter.setCurrentPlayingIndex(controller.currentMediaItemIndex)
            },
            onPlaybackChanged = {},
            onStreamIndexChanged = { idx -> adapter.setCurrentPlayingIndex(idx) },
            onMetadataChanged = {},
            onTimelineChanged = {}
        )

        view.findViewById<View>(R.id.buttonAddStation).setOnClickListener {
            showAddDialog()
        }

        view.findViewById<View>(R.id.buttonImportStations).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            startActivityForResult(intent, REQUEST_CODE_IMPORT_JSON)
        }

        topbarBackButton.setOnClickListener {
            if (hasChanges) {
                refreshPlaylist()
            }
            (activity as? MainActivity)?.showPlayerPage()
        }

        // Initialen Empty State setzen
        updateEmptyState()

        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IMPORT_JSON && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri -> importStationsFromUri(uri) }
        }
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_station, null)
        val editName = dialogView.findViewById<EditText>(R.id.editStationName)
        val editUrl = dialogView.findViewById<EditText>(R.id.editStreamUrl)
        val editIcon = dialogView.findViewById<EditText>(R.id.editIconUrl)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Station")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = editName.text.toString().ifBlank { editUrl.text.toString() }
                val url = editUrl.text.toString().trim()
                val icon = editIcon.text.toString().trim()
                if (url.isBlank()) {
                    Toast.makeText(requireContext(), getString(R.string.toast_url_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val finalUrl = if (url.endsWith(".m3u", true) || url.endsWith(".pls", true)) {
                        PlaylistURLHelper.resolvePlaylistUrl(url) ?: url
                    } else url

                    val station = StationItem(
                        uuid = UUID.randomUUID().toString(),
                        stationName = name,
                        streamURL = finalUrl,
                        iconURL = icon
                    )
                    stationList.add(station)
                    PreferencesHelper.saveStations(requireContext(), stationList)
                    adapter.notifyItemInserted(stationList.size - 1)
                    hasChanges = true
                    refreshPlaylist()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun pinStationToHome(station: StationItem) {
        val context = requireContext()
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, getString(R.string.toast_shortcut_not_supported), Toast.LENGTH_SHORT).show()
            return
        }

        val shortcutId = "station_${station.uuid}"
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Keys.ACTION_PLAY_STATION
            putExtra(Keys.EXTRA_STATION_UUID, station.uuid)
            putExtra(Keys.EXTRA_STATION_NAME, station.stationName)
            putExtra(Keys.EXTRA_STATION_STREAM_URL, station.streamURL)
            putExtra(Keys.EXTRA_STATION_ICON_URL, station.iconURL)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    if (station.iconURL.isNotBlank()) {
                        Glide.with(context).asBitmap().load(station.iconURL).submit().get()
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            val icon = if (bitmap != null) {
                IconCompat.createWithBitmap(bitmap)
            } else {
                IconCompat.createWithResource(context, R.drawable.ic_radio)
            }

            val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(station.stationName)
                .setIcon(icon)
                .setIntent(intent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
            Toast.makeText(context, R.string.toast_shortcut_added, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshPlaylist() {
        StateHelper.isPlaylistChangePending = true
        val intent = Intent(requireContext(), StreamingService::class.java).apply {
            action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
        }
        requireContext().startService(intent)
    }


    private fun importStationsFromUri(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
                ?: throw Exception("Datei konnte nicht geöffnet werden")
            val json = inputStream.bufferedReader().use { it.readText() }

            val result = StationImportHelper.importStationsFromJson(
                requireContext(),
                json,
                replaceAll = false
            )

            stationList.clear()
            stationList.addAll(result.newList)
            adapter.notifyDataSetChanged()

            Toast.makeText(
                requireContext(),
                "Import abgeschlossen: ${result.added} neu, ${result.updated} aktualisiert.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Fehler beim Import: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updateListNameLabel() {
        val listName = PreferencesHelper.getSelectedListName(requireContext())
        listNameLabel.text = listName
    }

    private fun showRenameListDialog() {
        val currentName = PreferencesHelper.getSelectedListName(requireContext())

        val padding = (16 * resources.displayMetrics.density).toInt()
        val editText = EditText(requireContext()).apply {
            setText(currentName)
            setSelection(currentName.length)
            hint = getString(R.string.rename_list_hint)
            setSingleLine(true)
            setPadding(padding, padding, padding, padding)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename_list_title)
            .setView(editText)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) {
                    val success = PreferencesHelper.renameList(requireContext(), currentName, newName)
                    if (success) {
                        updateListNameLabel()
                        // Broadcast senden, damit PlayerFragment Dropdown aktualisiert
                        LocalBroadcastManager.getInstance(requireContext())
                            .sendBroadcast(Intent(Keys.ACTION_STATIONS_UPDATED))
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Name existiert bereits oder ist ungültig",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        hasChanges = false
        stationPrefs.registerOnSharedPreferenceChangeListener(this)
        refreshStationList()
        updateListNameLabel()
        parentFragment?.view
            ?.findViewById<ViewPager2>(R.id.main_view_pager)
            ?.isUserInputEnabled = false

        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasChanges) {
                    refreshPlaylist()
                }
                (activity as? MainActivity)?.showPlayerPage()
            }
        }
        requireActivity().onBackPressedDispatcher
            .addCallback(this, backPressedCallback!!)
    }

    override fun onPause() {
        stationPrefs.unregisterOnSharedPreferenceChangeListener(this)
        backPressedCallback?.remove()
        backPressedCallback = null

        parentFragment?.view
            ?.findViewById<ViewPager2>(R.id.main_view_pager)
            ?.isUserInputEnabled = true
        super.onPause()
    }

    override fun onDestroyView() {
        // Ensure listener is unregistered even if onPause was missed
        try {
            stationPrefs.unregisterOnSharedPreferenceChangeListener(this)
        } catch (e: Exception) {
            // Already unregistered
        }
        mediaServiceController.disconnect()
        if (isReceiverRegistered) {
            context?.let { ctx ->
                LocalBroadcastManager.getInstance(ctx)
                    .unregisterReceiver(stationsUpdateReceiver)
            }
            isReceiverRegistered = false
        }
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "stations") {
            refreshStationList()
        }
    }
}
