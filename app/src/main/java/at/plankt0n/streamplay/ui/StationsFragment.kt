package at.plankt0n.streamplay.ui

import android.app.Activity
import android.content.Intent
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
import at.plankt0n.streamplay.helper.StationImportHelper
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import at.plankt0n.streamplay.Keys
import com.bumptech.glide.Glide
import at.plankt0n.streamplay.helper.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class StationsFragment : Fragment() {

    private lateinit var stationList: MutableList<StationItem>
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StationListAdapter
    private lateinit var mediaServiceController: MediaServiceController
    private lateinit var topbarBackButton: ImageButton
    private lateinit var topbarTitle: TextView
    private var backPressedCallback: OnBackPressedCallback? = null

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

        recyclerView = view.findViewById(R.id.recyclerViewStations)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
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
                refreshPlaylist()
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                stationList.removeAt(position)
                PreferencesHelper.saveStations(requireContext(), stationList)
                adapter.notifyItemRemoved(position)
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
            refreshPlaylist()
            (activity as? MainActivity)?.showPlayerPage()
        }

        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IMPORT_JSON && resultCode == Activity.RESULT_OK && data?.data != null) {
            importStationsFromUri(data.data!!)
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
                    Toast.makeText(requireContext(), "URL erforderlich", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Pinning shortcuts not supported", Toast.LENGTH_SHORT).show()
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
                        if (station.iconURL.lowercase().endsWith(".svg")) {
                            val drawable = Glide.with(context)
                                .`as`(android.graphics.drawable.PictureDrawable::class.java)
                                .load(station.iconURL)
                                .submit()
                                .get()
                            drawable.toBitmap()
                        } else {
                            Glide.with(context).asBitmap().load(station.iconURL).submit().get()
                        }
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

    override fun onResume() {
        super.onResume()
        parentFragment?.view
            ?.findViewById<ViewPager2>(R.id.main_view_pager)
            ?.isUserInputEnabled = false

        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                refreshPlaylist()
                (activity as? MainActivity)?.showPlayerPage()
            }
        }
        requireActivity().onBackPressedDispatcher
            .addCallback(this, backPressedCallback!!)
    }

    override fun onPause() {
        backPressedCallback?.remove()
        backPressedCallback = null

        parentFragment?.view
            ?.findViewById<ViewPager2>(R.id.main_view_pager)
            ?.isUserInputEnabled = true
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaServiceController.disconnect()
    }
}
