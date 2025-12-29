package at.plankt0n.streamplay.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import at.plankt0n.streamplay.StreamingService
import android.os.Looper
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.ViewFlipper
import android.widget.SeekBar
import android.widget.FrameLayout
import android.graphics.Color
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.MainActivity
import at.plankt0n.streamplay.adapter.CoverPageAdapter
import at.plankt0n.streamplay.adapter.ShortcutAdapter
import at.plankt0n.streamplay.data.ShortcutItem
import at.plankt0n.streamplay.data.CoverMode
import at.plankt0n.streamplay.data.CoverAnimationStyle
import at.plankt0n.streamplay.helper.LiveCoverHelper
import at.plankt0n.streamplay.helper.MediaServiceController
import at.plankt0n.streamplay.helper.StateHelper
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.MetaLogHelper
import at.plankt0n.streamplay.helper.StreamRecordHelper
import at.plankt0n.streamplay.view.VisualizerView
import at.plankt0n.streamplay.viewmodel.UITrackViewModel
import at.plankt0n.streamplay.viewmodel.UITrackInfo
import at.plankt0n.streamplay.data.MetaLogEntry
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.ScreenOrientationMode
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.core.graphics.ColorUtils
import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.graphics.drawable.Drawable
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.content.res.Configuration
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline

@OptIn(UnstableApi::class)
class PlayerFragment : Fragment() {

    private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    private var initialized = false

    private lateinit var viewPager: ViewPager2
    private lateinit var mediaServiceController: MediaServiceController
    private lateinit var spotifyTrackViewModel: UITrackViewModel

    private lateinit var stationNameTextView: TextView
    private lateinit var stationIconImageView: ImageView
    private lateinit var playPauseButton: ImageButton
    private lateinit var buttonBack: ImageButton
    private lateinit var buttonForward: ImageButton
    private lateinit var buttonMenu: ImageButton
    private lateinit var updateBadge: TextView
    private lateinit var buttonSpotify: ImageButton
    private lateinit var buttonMute: ImageButton
    private var volumeSlider: SeekBar? = null
    private lateinit var buttonShare: ImageButton
    private lateinit var buttonRecord: ImageButton
    private lateinit var buttonManualLog: ImageButton
    private lateinit var buttonLyrics: ImageButton
    private var buttonRotateLock: ImageButton? = null
    private lateinit var shortcutRecyclerView: RecyclerView
    private lateinit var shortcutAdapter: ShortcutAdapter
    private var coverPageAdapter: CoverPageAdapter? = null
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
    private var volumeHandler: Handler? = null
    private var volumeDismissRunnable: Runnable? = null
    private var recordHandler: Handler? = null
    private var recordStartRunnable: Runnable? = null
    private lateinit var countdownTextView: TextView
    private lateinit var connectingBanner: TextView
    private lateinit var listDropdown: Spinner
    private var listDropdownAdapter: ArrayAdapter<String>? = null
    private var isListDropdownInitializing = false
    private lateinit var metaFlipper: ViewFlipper
    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private val bannerHandler = Handler(Looper.getMainLooper())
    private var bannerRunnable: Runnable? = null
    private lateinit var prefs: SharedPreferences
    private var showInfoBanner: Boolean = true
    private var backgroundEffect = LiveCoverHelper.BackgroundEffect.FADE
    private var coverMode = CoverMode.META
    private var coverAnimationStyle = CoverAnimationStyle.FLIP
    private var lastOverlayForeground: Int? = null  // Für Farb-Reset nach Recording-Stop
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
        if (key == "show_exoplayer_banner") {
            showInfoBanner = shared.getBoolean(key, true)
            if (!showInfoBanner) hideConnecting()
        }
        if (key == "background_effect") {
            backgroundEffect = try {
                LiveCoverHelper.BackgroundEffect.valueOf(
                    shared.getString(key, LiveCoverHelper.BackgroundEffect.FADE.name)!!
                )
            } catch (e: IllegalArgumentException) {
                LiveCoverHelper.BackgroundEffect.FADE
            }
            if (initialized) {
                reloadPlaylist()
            }
        }
        if (key == Keys.PREF_VISUALIZER_STYLE) {
            StateHelper.visualizerStyle = try {
                VisualizerView.Style.valueOf(
                    shared.getString(key, VisualizerView.Style.BARS.name)!!
                )
            } catch (e: IllegalArgumentException) {
                VisualizerView.Style.BARS
            }
            if (initialized && backgroundEffect == LiveCoverHelper.BackgroundEffect.VISUALIZER) {
                reloadPlaylist()
            }
        }
        if (key == "cover_mode") {
            coverMode = try {
                CoverMode.valueOf(shared.getString(key, CoverMode.META.name)!!)
            } catch (e: IllegalArgumentException) {
                CoverMode.META
            }
            if (initialized) {
                reloadPlaylist()
                refreshCurrentCover()
            }
        }
        if (key == Keys.PREF_COVER_ANIMATION_STYLE) {
            coverAnimationStyle = try {
                CoverAnimationStyle.valueOf(
                    shared.getString(key, CoverAnimationStyle.FLIP.name)!!
                )
            } catch (e: IllegalArgumentException) {
                CoverAnimationStyle.FLIP
            }
        }
        if (key == Keys.PREF_UPDATE_AVAILABLE) {
            val showBadge = shared.getBoolean(key, false)
            updateBadge.visibility = if (showBadge) View.VISIBLE else View.GONE
        }
        if (key == Keys.PREF_RECORDING_ENABLED) {
            if (initialized) {
                updateRecordButtonState()
            }
        }
    }

    private val autoplayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Keys.ACTION_SHOW_COUNTDOWN -> {
                    val duration = intent.getIntExtra(Keys.EXTRA_COUNTDOWN_DURATION, 0)
                    showCountdown(duration)
                }
                Keys.ACTION_HIDE_COUNTDOWN -> hideCountdown()
            }
        }
    }

    private val stationsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Keys.ACTION_STATIONS_UPDATED) {
                Log.d("PlayerFragment", "Received STATIONS_UPDATED broadcast, refreshing dropdown")
                refreshListDropdown()
            }
        }
    }

    @Volatile
    var isMuted = false
    private var showingMetaCover = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Orientierung merken und Layout anpassen
        currentOrientation = resources.configuration.orientation
        inflateUiOverlay()
        applyOrientationLayout()

        buttonMenu = view.findViewById(R.id.button_menu)
        updateBadge = view.findViewById(R.id.update_badge)
        buttonMenu.setOnClickListener { showBottomSheet() }

        // Listen-Dropdown ZUERST initialisieren (vor dem Empty-Check)
        listDropdown = view.findViewById(R.id.station_overlay_dropdown)
        setupListDropdown()

        // Prüfe ob IRGENDEINE Liste Stationen hat
        val allLists = PreferencesHelper.getStationLists(requireContext())
        val hasAnyStations = allLists.values.any { it.isNotEmpty() }

        if (!hasAnyStations) {
            Log.w("PlayerFragment", "⚠️ Keine Stationen in keiner Liste, Wechsel ins StationsFragment.")
            (activity as? MainActivity)?.showStationsPage()
            return
        }

        // Wenn aktuelle Liste leer ist, aber andere Listen Stationen haben,
        // wechsle automatisch zur ersten nicht-leeren Liste
        if (PreferencesHelper.getStations(requireContext()).isEmpty()) {
            val firstNonEmptyList = allLists.entries.firstOrNull { it.value.isNotEmpty() }
            if (firstNonEmptyList != null) {
                Log.d("PlayerFragment", "Aktuelle Liste leer, wechsle zu: ${firstNonEmptyList.key}")
                PreferencesHelper.setSelectedListName(requireContext(), firstNonEmptyList.key)
                refreshListDropdown()
            }
        }

        viewPager = view.findViewById(R.id.view_pager)
        viewPager.offscreenPageLimit = 2

        shortcutRecyclerView = view.findViewById(R.id.shortcut_recycler_view)
        shortcutAdapter = ShortcutAdapter { item ->
            mediaServiceController.playAtIndex(item.index)
        }
        shortcutRecyclerView.adapter = shortcutAdapter
        shortcutRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        stationNameTextView = view.findViewById(R.id.station_overlay_stationname)
        stationIconImageView = view.findViewById(R.id.station_overlay_stationIcon)
        playPauseButton = view.findViewById(R.id.button_play_pause)
        buttonBack = view.findViewById(R.id.button_back)
        buttonForward = view.findViewById(R.id.button_forward)
        buttonSpotify = view.findViewById(R.id.button_spotify)
        buttonMute = view.findViewById(R.id.button_mute_unmute)
        buttonShare = view.findViewById(R.id.button_share)
        buttonRecord = view.findViewById(R.id.button_record)
        buttonManualLog = view.findViewById(R.id.button_manual_log)
        buttonLyrics = view.findViewById(R.id.button_lyrics)
        buttonRotateLock = view.findViewById(R.id.button_rotate_lock)
        countdownTextView = view.findViewById(R.id.autoplay_countdown)
        connectingBanner = view.findViewById(R.id.connecting_banner)
        metaFlipper = view.findViewById(R.id.meta_flipper)
        metaFlipper.setOnClickListener {
            if (metaFlipper.childCount > 1) {
                if (metaFlipper.displayedChild == metaFlipper.childCount - 1) {
                    metaFlipper.displayedChild = 0
                } else {
                    metaFlipper.showNext()
                }
            }
        }

        prefs = requireContext().getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        showInfoBanner = prefs.getBoolean("show_exoplayer_banner", true)
        backgroundEffect = try {
            LiveCoverHelper.BackgroundEffect.valueOf(
                prefs.getString("background_effect", LiveCoverHelper.BackgroundEffect.FADE.name)!!
            )
        } catch (e: IllegalArgumentException) {
            LiveCoverHelper.BackgroundEffect.FADE
        }
        StateHelper.visualizerStyle = try {
            VisualizerView.Style.valueOf(
                prefs.getString(Keys.PREF_VISUALIZER_STYLE, VisualizerView.Style.BARS.name)!!
            )
        } catch (e: IllegalArgumentException) {
            VisualizerView.Style.BARS
        }
        coverMode = try {
            CoverMode.valueOf(prefs.getString("cover_mode", CoverMode.META.name)!!)
        } catch (e: IllegalArgumentException) {
            CoverMode.META
        }
        coverAnimationStyle = try {
            CoverAnimationStyle.valueOf(
                prefs.getString(
                    Keys.PREF_COVER_ANIMATION_STYLE,
                    CoverAnimationStyle.FLIP.name
                )!!
            )
        } catch (e: IllegalArgumentException) {
            CoverAnimationStyle.FLIP
        }
        updateBadge.visibility = if (prefs.getBoolean(Keys.PREF_UPDATE_AVAILABLE, false)) View.VISIBLE else View.GONE
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        // Grundlegende Button-Listener setzen, auch wenn die Playlist leer ist
        playPauseButton.setOnClickListener { mediaServiceController.togglePlayPause() }
        buttonBack.setOnClickListener { mediaServiceController.skipToPrevious() }
        buttonForward.setOnClickListener { mediaServiceController.skipToNext() }
        buttonMenu.setOnClickListener { showBottomSheet() }

        val filter = IntentFilter().apply {
            addAction(Keys.ACTION_SHOW_COUNTDOWN)
            addAction(Keys.ACTION_HIDE_COUNTDOWN)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else 0
        requireContext().registerReceiver(autoplayReceiver, filter, flags)

        // Register for station list updates (from API sync)
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(stationsUpdateReceiver, IntentFilter(Keys.ACTION_STATIONS_UPDATED))

        // ViewModel VOR MediaServiceController initialisieren (für onConnected Callback)
        spotifyTrackViewModel = ViewModelProvider(requireActivity())[UITrackViewModel::class.java]

        mediaServiceController = MediaServiceController(requireContext())
        mediaServiceController.initializeAndConnect(
            onConnected = { controller ->
                if (!isAdded) return@initializeAndConnect
                val shortcuts = (0 until controller.mediaItemCount).mapNotNull { i ->
                    val mediaItem = controller.getMediaItemAt(i)
                    val extras = mediaItem.mediaMetadata.extras ?: return@mapNotNull null
                    val label = extras.getString("EXTRA_STATION_NAME") ?: return@mapNotNull null
                    val iconUrl = extras.getString("EXTRA_ICON_URL") ?: ""
                    val mediaId = mediaItem.mediaId
                    ShortcutItem(label, iconUrl, mediaId, i)
                }
                shortcutAdapter.setItems(shortcuts)
                val currentIndex = controller.currentMediaItemIndex
                shortcutAdapter.selectedIndex = if (currentIndex >= 0) currentIndex else 0

                if (controller.mediaItemCount == 0) {
                    if (!StateHelper.hasAutoOpenedDiscover) {
                        Log.w(
                            "PlayerFragment",
                            "\u26a0\ufe0f MediaSession ist leer! Öffne DiscoverFragment."
                        )
                        StateHelper.hasAutoOpenedDiscover = true
                        val currentFragment = requireActivity().supportFragmentManager.findFragmentById(R.id.fragment_container)
                        requireActivity().supportFragmentManager
                            .beginTransaction()
                            .setReorderingAllowed(true)
                            .hide(currentFragment!!)
                            .add(R.id.fragment_container, DiscoverFragment())
                            .addToBackStack(null)
                            .commit()
                    }
                    return@initializeAndConnect
                } else {
                    StateHelper.hasAutoOpenedDiscover = false
                }

                coverPageAdapter = CoverPageAdapter(mediaServiceController, backgroundEffect)
                coverPageAdapter?.onColorChanged = { position, color ->
                    if (position == viewPager.currentItem) {
                        updateOverlayColors(color)
                    }
                }

                // Meta-Cover setzen BEVOR Adapter zugewiesen wird
                // currentIndex is already declared above
                val currentTrackInfo = spotifyTrackViewModel.trackInfo.value
                val metaCoverUrl = currentTrackInfo?.bestCoverUrl?.takeIf { it.isNotBlank() }
                if (coverMode == CoverMode.META && metaCoverUrl != null) {
                    coverPageAdapter?.setCoverUrlForPosition(currentIndex, metaCoverUrl)
                }

                viewPager.adapter = coverPageAdapter

                coverPageAdapter?.mediaItems?.forEach { item ->
                    Glide.with(requireContext())
                        .load(item.iconURL)
                        .preload()
                }

                viewPager.setCurrentItem(currentIndex, false)
                updateOverlayUI(currentIndex)
                updatePlayPauseIcon(controller.isPlaying)
                updateManualLogButtonState(currentTrackInfo)

                requireContext().startService(
                    Intent(requireContext(), StreamingService::class.java).apply {
                        action = Keys.ACTION_REFRESH_METADATA
                    }
                )

                // Remove old callback if exists to prevent memory leaks
                pageChangeCallback?.let { viewPager.unregisterOnPageChangeCallback(it) }

                pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        mediaServiceController.seekToIndex(position)

                        // Shortcut hervorheben und dorthin scrollen
                        shortcutAdapter.selectedIndex = position
                        shortcutRecyclerView.smoothScrollToPosition(position)
                    }
                }
                viewPager.registerOnPageChangeCallback(pageChangeCallback!!)

            },
            onPlaybackChanged = { isPlaying ->
                if (!isAdded) return@initializeAndConnect
                updatePlayPauseIcon(isPlaying)
            },
            onStreamIndexChanged = { index ->
                if (!isAdded) return@initializeAndConnect
                viewPager.setCurrentItem(index, true)
                updateOverlayUI(index)
                updateManualLogButtonState(spotifyTrackViewModel.trackInfo.value)
            },
            onMetadataChanged = {},
            onTimelineChanged = {
                if (!isAdded) return@initializeAndConnect
                Log.d("PlayerFragment", "\uD83D\uDD01 Timeline geändert! Grund: $it")
                reloadPlaylist()
            },
            onPlaybackStateChanged = { state ->
                if (!isAdded) return@initializeAndConnect
                when (state) {
                    Player.STATE_BUFFERING -> showConnecting()
                    Player.STATE_READY -> showConnected()
                }
            },
            onPlayerError = { error ->
                if (!isAdded) return@initializeAndConnect
                showError(error.message)
            }
        )

        observeSpotifyTrackInfo()

        buttonSpotify.setOnClickListener {
            val trackInfo = spotifyTrackViewModel.trackInfo.value
            val spotifyUrl = trackInfo?.spotifyUrl ?: return@setOnClickListener

            AlertDialog.Builder(requireContext())
                .setTitle("Spotify-Link \u00f6ffnen?")
                .setMessage("M\u00f6chtest du diesen Song in Spotify \u00f6ffnen?")
                .setPositiveButton("Ja") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUrl))
                    startActivity(intent)
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        buttonShare.setOnClickListener {
            val trackInfo = spotifyTrackViewModel.trackInfo.value
            val spotifyUrl = trackInfo?.spotifyUrl
            val spotifyTitle = trackInfo?.trackName
            val spotifyArtist = trackInfo?.artistName

            if (spotifyUrl.isNullOrBlank() || spotifyTitle.isNullOrBlank() || spotifyArtist.isNullOrBlank()) return@setOnClickListener

            val textToShare = getString(R.string.share_text, spotifyTitle, spotifyArtist, spotifyUrl)
            val chooserTitle = getString(R.string.share_chooser_title)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, textToShare)
            }

            startActivity(Intent.createChooser(intent, chooserTitle))
        }

        setupRecordButton()
        updateRecordButtonState()
        setupRotateLockButton()

        buttonManualLog.setOnClickListener {
            saveManualLog()
        }

        buttonLyrics.setOnClickListener {
            openLyricsSheet()
        }

        buttonMute.setOnClickListener {
            val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (isMuted) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                buttonMute.setImageResource(R.drawable.ic_button_unmuted)
            } else {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                buttonMute.setImageResource(R.drawable.ic_button_muted)
            }
            isMuted = !isMuted
        }
        buttonMute.setOnLongClickListener { showVolumePopup(it); true }
        updateManualLogButtonState(spotifyTrackViewModel.trackInfo.value)
        initialized = true
    }

    override fun onStart() {
        super.onStart()
        if (!initialized) return
        observeSpotifyTrackInfo()
    }

    override fun onResume() {
        super.onResume()
        if (!initialized) return
        if (StateHelper.isPlaylistChangePending) {
            reloadPlaylist()
            StateHelper.isPlaylistChangePending = false
        }
        // Cover aktualisieren wenn man aus Settings zurückkommt
        refreshCurrentCover()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!initialized) return

        // Nur wenn Orientierung sich wirklich geändert hat
        if (newConfig.orientation != currentOrientation) {
            currentOrientation = newConfig.orientation

            // UI-Overlay neu laden für neue Orientierung
            inflateUiOverlay()
            applyOrientationLayout()

            // UI-Bindings neu setzen
            rebindUiOverlay()
        }
    }

    /**
     * Lädt das UI-Overlay in den Container.
     * Android wählt automatisch das richtige Layout basierend auf der aktuellen Orientierung.
     */
    private fun inflateUiOverlay() {
        val container = view?.findViewById<FrameLayout>(R.id.ui_overlay_container) ?: return
        container.removeAllViews()

        val overlay = LayoutInflater.from(context).inflate(
            R.layout.fragment_player_ui_overlay,
            container,
            false
        )
        container.addView(overlay)
    }

    /**
     * Passt das Layout für die aktuelle Orientierung an.
     * Im Landscape: ViewPager links (50%), Overlay rechts
     * Im Portrait: ViewPager Vollbild, Overlay überlagert
     */
    private fun applyOrientationLayout() {
        val guideline = view?.findViewById<Guideline>(R.id.guideline_viewpager_end) ?: return
        val container = view?.findViewById<FrameLayout>(R.id.ui_overlay_container) ?: return
        val constraintLayout = view as? ConstraintLayout ?: return

        val guidelineParams = guideline.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val containerParams = container.layoutParams as? ConstraintLayout.LayoutParams ?: return

        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Landscape: ViewPager links (50%), Overlay rechts
            guidelineParams.guidePercent = 0.5f
            containerParams.startToStart = ConstraintLayout.LayoutParams.UNSET
            containerParams.startToEnd = R.id.guideline_viewpager_end
        } else {
            // Portrait: ViewPager Vollbild, Overlay überlagert
            guidelineParams.guidePercent = 1.0f
            containerParams.startToEnd = ConstraintLayout.LayoutParams.UNSET
            containerParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        }

        guideline.layoutParams = guidelineParams
        container.layoutParams = containerParams
    }

    /**
     * Bindet alle UI-Elemente nach einem Overlay-Reload neu.
     */
    private fun rebindUiOverlay() {
        val v = view ?: return

        // Alle Views neu finden
        buttonMenu = v.findViewById(R.id.button_menu)
        updateBadge = v.findViewById(R.id.update_badge)
        listDropdown = v.findViewById(R.id.station_overlay_dropdown)
        stationNameTextView = v.findViewById(R.id.station_overlay_stationname)
        stationIconImageView = v.findViewById(R.id.station_overlay_stationIcon)
        playPauseButton = v.findViewById(R.id.button_play_pause)
        buttonBack = v.findViewById(R.id.button_back)
        buttonForward = v.findViewById(R.id.button_forward)
        buttonSpotify = v.findViewById(R.id.button_spotify)
        buttonMute = v.findViewById(R.id.button_mute_unmute)
        buttonShare = v.findViewById(R.id.button_share)
        buttonRecord = v.findViewById(R.id.button_record)
        buttonManualLog = v.findViewById(R.id.button_manual_log)
        buttonLyrics = v.findViewById(R.id.button_lyrics)
        buttonRotateLock = v.findViewById(R.id.button_rotate_lock)
        countdownTextView = v.findViewById(R.id.autoplay_countdown)
        connectingBanner = v.findViewById(R.id.connecting_banner)
        metaFlipper = v.findViewById(R.id.meta_flipper)
        shortcutRecyclerView = v.findViewById(R.id.shortcut_recycler_view)

        // Event-Listener neu setzen
        buttonMenu.setOnClickListener { showBottomSheet() }
        setupListDropdown()

        // Player-Buttons
        playPauseButton.setOnClickListener { mediaServiceController.togglePlayPause() }
        buttonBack.setOnClickListener { mediaServiceController.skipToPrevious() }
        buttonForward.setOnClickListener { mediaServiceController.skipToNext() }

        // Spotify-Button
        buttonSpotify.setOnClickListener {
            val trackInfo = spotifyTrackViewModel.trackInfo.value
            val spotifyUrl = trackInfo?.spotifyUrl ?: return@setOnClickListener

            AlertDialog.Builder(requireContext())
                .setTitle("Spotify-Link \u00f6ffnen?")
                .setMessage("M\u00f6chtest du diesen Song in Spotify \u00f6ffnen?")
                .setPositiveButton("Ja") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUrl))
                    startActivity(intent)
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        // Share-Button
        buttonShare.setOnClickListener {
            val trackInfo = spotifyTrackViewModel.trackInfo.value
            val spotifyUrl = trackInfo?.spotifyUrl
            val spotifyTitle = trackInfo?.trackName
            val spotifyArtist = trackInfo?.artistName

            if (spotifyUrl.isNullOrBlank() || spotifyTitle.isNullOrBlank() || spotifyArtist.isNullOrBlank()) return@setOnClickListener

            val textToShare = getString(R.string.share_text, spotifyTitle, spotifyArtist, spotifyUrl)
            val chooserTitle = getString(R.string.share_chooser_title)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, textToShare)
            }

            startActivity(Intent.createChooser(intent, chooserTitle))
        }

        // Record-Button neu einrichten
        setupRecordButton()
        updateRecordButtonState()

        // Rotate Lock Button
        setupRotateLockButton()

        // ManualLog-Button
        buttonManualLog.setOnClickListener { saveManualLog() }

        // Lyrics-Button
        buttonLyrics.setOnClickListener { openLyricsSheet() }

        // Mute-Button
        buttonMute.setOnClickListener {
            val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (isMuted) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                buttonMute.setImageResource(R.drawable.ic_button_unmuted)
            } else {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                buttonMute.setImageResource(R.drawable.ic_button_muted)
            }
            isMuted = !isMuted
        }
        buttonMute.setOnLongClickListener { showVolumePopup(it); true }

        // MetaFlipper click listener
        metaFlipper.setOnClickListener {
            if (metaFlipper.childCount > 1) {
                if (metaFlipper.displayedChild == metaFlipper.childCount - 1) {
                    metaFlipper.displayedChild = 0
                } else {
                    metaFlipper.showNext()
                }
            }
        }

        // Shortcut RecyclerView neu einrichten
        shortcutRecyclerView.adapter = shortcutAdapter
        shortcutRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        // Update Badge Status
        updateBadge.visibility = if (prefs.getBoolean(Keys.PREF_UPDATE_AVAILABLE, false)) View.VISIBLE else View.GONE

        // Aktuellen Playback-Status anzeigen
        val controller = mediaServiceController.mediaController
        if (controller != null) {
            updatePlayPauseIcon(controller.isPlaying)
            val currentIndex = controller.currentMediaItemIndex
            if (currentIndex >= 0 && currentIndex < controller.mediaItemCount) {
                updateOverlayUI(currentIndex)
                shortcutAdapter.selectedIndex = currentIndex
            }
        }

        // Track-Info Observer erneut aktivieren (für neue Views)
        observeSpotifyTrackInfo()
    }

    private fun observeSpotifyTrackInfo() {
        spotifyTrackViewModel.trackInfo.removeObservers(viewLifecycleOwner)
        spotifyTrackViewModel.trackInfo.observe(viewLifecycleOwner) { trackInfo ->
            // Safety check - don't update UI if fragment is not attached
            if (!isAdded || view == null) return@observe
            val context = context ?: return@observe

            val titleTextView = view?.findViewById<TextView>(R.id.meta_overlay_Title)
            val artistTextView = view?.findViewById<TextView>(R.id.meta_overlay_Artist)
            val genreTextView = view?.findViewById<TextView>(R.id.meta_overlay_Genre)
            val albumTextView = view?.findViewById<TextView>(R.id.meta_overlay_Album)
            val stationIconView = view?.findViewById<ShapeableImageView>(R.id.meta_cover_image)

            val spotifyUrl = trackInfo?.spotifyUrl
            val spotifyAvailable = !spotifyUrl.isNullOrBlank()
            Log.d("PlayerFragment", "🎵 Observer: ${trackInfo?.trackName}, spotifyUrl=${spotifyUrl?.take(30) ?: "NULL"}, enabled=$spotifyAvailable")
            buttonSpotify.isEnabled = spotifyAvailable
            buttonSpotify.alpha = if (spotifyAvailable) 1.0f else 0.5f
            buttonSpotify.setColorFilter(
                if (spotifyAvailable) context.getColor(R.color.black)
                else context.getColor(R.color.colorAccent)
            )

            if (trackInfo == null) {
                titleTextView?.text = getString(R.string.unknown_title)
                artistTextView?.text = getString(R.string.unknown_artist)
                genreTextView?.text = getString(R.string.unknown_genre)
                albumTextView?.text = getString(R.string.unknown_album)
                metaFlipper.stopFlipping()
                metaFlipper.displayedChild = 0

                stationIconView?.let { iconView ->
                    Glide.with(context)
                        .load(R.drawable.placeholder_spotify_dark)
                        .placeholder(R.drawable.placeholder_spotify_dark)
                        .error(R.drawable.placeholder_spotify_dark)
                        .into(iconView)
                }

                return@observe
            }

            val currentPosition = viewPager.currentItem
            if (currentPosition < 0) return@observe

            val defaultIconUrl = mediaServiceController.mediaController
                ?.getMediaItemAt(currentPosition)
                ?.mediaMetadata?.extras?.getString("EXTRA_ICON_URL") ?: ""

            // Bestimme die Cover-URL für ViewPager
            val metaCover = trackInfo.bestCoverUrl?.takeIf { it.isNotBlank() }
            val coverUrlToUse = if (coverMode == CoverMode.META && metaCover != null) {
                metaCover
            } else {
                defaultIconUrl.takeIf { it.isNotBlank() }
            }

            // Cover über Adapter aktualisieren (zuverlässiger als manuelles ViewHolder-Suchen)
            coverPageAdapter?.updateCoverUrl(currentPosition, coverUrlToUse ?: "")

            // Click-Listener für Flip zwischen Metadata und Station-Cover
            val isViewPagerVisible = viewPager.visibility == android.view.View.VISIBLE

            if (isViewPagerVisible) {
                // Standard-Modus: ViewPager ist sichtbar, Flip auf ViewPager-Cover
                viewPager.post {
                    if (!isAdded || view == null) return@post
                    val postContext = context ?: return@post
                    val recyclerView = viewPager.getChildAt(0) as? RecyclerView
                    val holder = recyclerView?.findViewHolderForAdapterPosition(currentPosition)
                            as? CoverPageAdapter.CoverViewHolder
                    if (holder != null && coverMode == CoverMode.META && metaCover != null) {
                        // Lade Meta-Cover direkt falls noch nicht geladen
                        LiveCoverHelper.loadCoverWithBackground(
                            context = postContext,
                            imageUrl = metaCover,
                            imageView = holder.coverImage,
                            backgroundTarget = holder.itemView,
                            defaultColor = postContext.getColor(R.color.default_background),
                            lastColor = holder.lastColor,
                            lastEffect = holder.lastEffect,
                            effect = backgroundEffect,
                            onNewColor = {
                                holder.lastColor = it
                                updateOverlayColors(it)
                            },
                            onNewEffect = { holder.lastEffect = it }
                        )
                        showingMetaCover = true

                        holder.coverImage.setOnClickListener { view ->
                            val clickContext = context ?: return@setOnClickListener
                            val targetUrl = if (showingMetaCover) {
                                defaultIconUrl.takeIf { it.isNotBlank() }
                            } else {
                                trackInfo.bestCoverUrl?.takeIf { it.isNotBlank() }
                                    ?: defaultIconUrl.takeIf { it.isNotBlank() }
                            }

                            val loadNewCoverWithBackground = {
                                if (targetUrl.isNullOrBlank()) {
                                    holder.coverImage.setImageResource(R.drawable.ic_placeholder_logo)
                                    holder.itemView.setBackgroundColor(clickContext.getColor(R.color.default_background))
                                } else {
                                    LiveCoverHelper.loadCoverWithBackground(
                                        context = clickContext,
                                        imageUrl = targetUrl,
                                        imageView = holder.coverImage,
                                        backgroundTarget = holder.itemView,
                                        defaultColor = clickContext.getColor(R.color.default_background),
                                        lastColor = holder.lastColor,
                                        lastEffect = holder.lastEffect,
                                        effect = backgroundEffect,
                                        onNewColor = {
                                            holder.lastColor = it
                                            updateOverlayColors(it)
                                        },
                                        onNewEffect = { holder.lastEffect = it }
                                    )
                                }
                            }

                            val animStyle = if (coverAnimationStyle == CoverAnimationStyle.NONE) {
                                CoverAnimationStyle.FLIP
                            } else {
                                coverAnimationStyle
                            }

                            when (animStyle) {
                                CoverAnimationStyle.FLIP -> {
                                    view.animate()
                                        .rotationY(90f)
                                        .setDuration(150)
                                        .withEndAction {
                                            loadNewCoverWithBackground()
                                            holder.coverImage.rotationY = -90f
                                            holder.coverImage.animate().rotationY(0f).setDuration(150).start()
                                        }
                                        .start()
                                }
                                CoverAnimationStyle.FADE -> {
                                    view.animate()
                                        .alpha(0f)
                                        .setDuration(150)
                                        .withEndAction {
                                            loadNewCoverWithBackground()
                                            holder.coverImage.alpha = 0f
                                            holder.coverImage.animate().alpha(1f).setDuration(150).start()
                                        }
                                        .start()
                                }
                                CoverAnimationStyle.NONE -> loadNewCoverWithBackground()
                            }

                            showingMetaCover = !showingMetaCover
                        }
                    } else {
                        holder?.coverImage?.setOnClickListener(null)
                    }
                }
            } else {
                // Kleines Gerät im Querformat: ViewPager versteckt, meta_cover_image ist flipable
                if (stationIconView != null && coverMode == CoverMode.META && !trackInfo.bestCoverUrl.isNullOrBlank()) {
                    // Lade Meta-Cover ins große meta_cover_image
                    Glide.with(context)
                        .load(trackInfo.bestCoverUrl)
                        .placeholder(R.drawable.ic_placeholder_logo)
                        .error(R.drawable.ic_placeholder_logo)
                        .into(stationIconView)
                    showingMetaCover = true

                    stationIconView.setOnClickListener { imgView ->
                        val clickContext = context ?: return@setOnClickListener
                        val targetUrl = if (showingMetaCover) {
                            defaultIconUrl.takeIf { it.isNotBlank() }
                        } else {
                            trackInfo.bestCoverUrl?.takeIf { it.isNotBlank() }
                                ?: defaultIconUrl.takeIf { it.isNotBlank() }
                        }

                        val loadNewCover = {
                            if (targetUrl.isNullOrBlank()) {
                                (imgView as? ShapeableImageView)?.setImageResource(R.drawable.ic_placeholder_logo)
                            } else {
                                Glide.with(clickContext)
                                    .load(targetUrl)
                                    .placeholder(R.drawable.ic_placeholder_logo)
                                    .error(R.drawable.ic_placeholder_logo)
                                    .into(imgView as ShapeableImageView)
                            }
                        }

                        val animStyle = if (coverAnimationStyle == CoverAnimationStyle.NONE) {
                            CoverAnimationStyle.FLIP
                        } else {
                            coverAnimationStyle
                        }

                        when (animStyle) {
                            CoverAnimationStyle.FLIP -> {
                                imgView.animate()
                                    .rotationY(90f)
                                    .setDuration(150)
                                    .withEndAction {
                                        loadNewCover()
                                        imgView.rotationY = -90f
                                        imgView.animate().rotationY(0f).setDuration(150).start()
                                    }
                                    .start()
                            }
                            CoverAnimationStyle.FADE -> {
                                imgView.animate()
                                    .alpha(0f)
                                    .setDuration(150)
                                    .withEndAction {
                                        loadNewCover()
                                        imgView.alpha = 0f
                                        imgView.animate().alpha(1f).setDuration(150).start()
                                    }
                                    .start()
                            }
                            CoverAnimationStyle.NONE -> loadNewCover()
                        }

                        showingMetaCover = !showingMetaCover
                    }
                } else {
                    stationIconView?.setOnClickListener(null)
                }
            }

            titleTextView?.text = trackInfo.trackName.takeIf { it.isNotBlank() } ?: getString(R.string.unknown_title)
            artistTextView?.text = trackInfo.artistName.takeIf { it.isNotBlank() } ?: getString(R.string.unknown_artist)
            genreTextView?.text = if (trackInfo.genre.isNotBlank()) getString(R.string.genre_prefix, trackInfo.genre) else getString(R.string.unknown_genre)

            if (trackInfo.albumName.isNotBlank()) {
                albumTextView?.text = getString(R.string.album_prefix, trackInfo.albumName)
                if (metaFlipper.displayedChild != 0) metaFlipper.displayedChild = 0
                metaFlipper.startFlipping()
            } else {
                metaFlipper.stopFlipping()
                metaFlipper.displayedChild = 0
                albumTextView?.text = getString(R.string.unknown_album)
            }

            // Bestimme die URL für meta_cover_image
            val metaCoverUrl = if (coverMode == CoverMode.META && !trackInfo.bestCoverUrl.isNullOrBlank()) {
                trackInfo.bestCoverUrl
            } else {
                defaultIconUrl.takeIf { it.isNotBlank() }
            }

            // Lade Cover oder setze Placeholder direkt wenn keine URL vorhanden
            if (stationIconView != null) {
                if (metaCoverUrl.isNullOrBlank()) {
                    stationIconView.setImageResource(R.drawable.ic_placeholder_logo)
                } else {
                    Glide.with(context)
                        .load(metaCoverUrl)
                        .placeholder(R.drawable.ic_placeholder_logo)
                        .error(R.drawable.ic_stationcover_placeholder)
                        .into(stationIconView)
                }
            }

            listOfNotNull(titleTextView, artistTextView, genreTextView, albumTextView)
                .let { if (it.isNotEmpty()) enableMarquee(*it.toTypedArray()) }
            updateManualLogButtonState(trackInfo)
        }
    }

    private fun updateOverlayColors(color: Int) {
        val luminance = ColorUtils.calculateLuminance(color)
        val foreground = if (luminance > 0.5) Color.BLACK else Color.WHITE
        lastOverlayForeground = foreground  // Speichern für Recording-Stop
        val shadowColor = if (luminance > 0.5) Color.WHITE else Color.BLACK
        val shadowRadius = 4f
        val shadowDx = 1f
        val shadowDy = 1f

        // Hilfsfunktion für Text + Schatten
        fun TextView.applyStyle() {
            setTextColor(foreground)
            setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor)
        }

        // Hilfsfunktion für Buttons + Schatten (via Elevation)
        fun ImageButton.applyStyle() {
            setColorFilter(foreground)
            elevation = shadowRadius
        }

        stationNameTextView.applyStyle()
        view?.findViewById<TextView>(R.id.meta_overlay_Title)?.applyStyle()
        view?.findViewById<TextView>(R.id.meta_overlay_Artist)?.applyStyle()
        view?.findViewById<TextView>(R.id.meta_overlay_Album)?.applyStyle()
        view?.findViewById<TextView>(R.id.meta_overlay_Genre)?.applyStyle()

        // Dropdown-Text anpassen
        (listDropdown.selectedView as? TextView)?.applyStyle()

        buttonBack.applyStyle()
        playPauseButton.applyStyle()
        buttonForward.applyStyle()
        buttonMute.applyStyle()
        buttonShare.applyStyle()
        // Record-Button nur stylen wenn nicht aufgenommen wird (sonst bleibt er rot)
        if (!StreamRecordHelper.isRecording()) {
            buttonRecord.applyStyle()
        }
        buttonMenu.applyStyle()
        buttonSpotify.applyStyle()
        buttonLyrics.applyStyle()
        buttonManualLog.applyStyle()
    }

    private fun updateOverlayUI(index: Int) {
        val controller = mediaServiceController.mediaController ?: return
        if (index < 0 || index >= controller.mediaItemCount) return
        val mediaItem = controller.getMediaItemAt(index)
        val extras = mediaItem.mediaMetadata.extras ?: return

        val stationName = extras.getString("EXTRA_STATION_NAME") ?: ""
        val iconUrl = extras.getString("EXTRA_ICON_URL") ?: ""

        stationNameTextView.text = stationName
        Glide.with(stationIconImageView)
            .load(iconUrl)
            .placeholder(R.drawable.placeholder_spotify_dark)
            .error(R.drawable.placeholder_spotify_dark)
            .into(stationIconImageView)
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        val iconRes = if (isPlaying) R.drawable.ic_button_pause else R.drawable.ic_button_play
        playPauseButton.setImageResource(iconRes)
    }

    private fun showVolumePopup(@Suppress("UNUSED_PARAMETER") anchor: View) {
        if (volumeSlider != null) return
        val rootView = view ?: return
        val overlayContainer = rootView.findViewById<FrameLayout>(R.id.station_overlay_container)
        val previousContent = overlayContainer.getChildAt(0)
        previousContent?.visibility = View.INVISIBLE

        val sliderLayout = LayoutInflater.from(requireContext()).inflate(
            R.layout.volume_slider_popup, overlayContainer, false
        )
        val seekBar = sliderLayout.findViewById<SeekBar>(R.id.volume_seekbar)
        val volumeLabel = sliderLayout.findViewById<TextView>(R.id.volume_percentage)
        volumeSlider = seekBar

        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        seekBar.max = maxVolume
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        seekBar.progress = currentVolume
        volumeLabel.text = "${currentVolume * 100 / maxVolume}%"

        overlayContainer.addView(sliderLayout)

        // Cancel any previous volume handler callbacks
        volumeHandler?.removeCallbacksAndMessages(null)
        volumeHandler = Handler(Looper.getMainLooper())

        volumeDismissRunnable = Runnable {
            if (isAdded) {
                overlayContainer.removeView(sliderLayout)
                previousContent?.visibility = View.VISIBLE
                volumeSlider = null
            }
        }
        volumeHandler?.postDelayed(volumeDismissRunnable!!, Keys.VOLUME_SLIDER_INITIAL_HIDE_DELAY_MS)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                volumeDismissRunnable?.let { volumeHandler?.removeCallbacks(it) }
                volumeDismissRunnable?.let {
                    volumeHandler?.postDelayed(it, Keys.VOLUME_SLIDER_POST_ADJUST_HIDE_DELAY_MS)
                }
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                volumeLabel.text = "${progress * 100 / maxVolume}%"
                if (progress == 0) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                    isMuted = true
                    buttonMute.setImageResource(R.drawable.ic_button_muted)
                } else {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                    isMuted = false
                    buttonMute.setImageResource(R.drawable.ic_button_unmuted)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                volumeDismissRunnable?.let { volumeHandler?.removeCallbacks(it) }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                volumeDismissRunnable?.let { volumeHandler?.removeCallbacks(it) }
                volumeDismissRunnable?.let {
                    volumeHandler?.postDelayed(it, Keys.VOLUME_SLIDER_POST_ADJUST_HIDE_DELAY_MS)
                }
            }
        })
    }

    /**
     * Initialisiert die Listen-Dropdown mit allen verfügbaren Stationslisten
     */
    private fun setupListDropdown() {
        val listNames = PreferencesHelper.getListNames(requireContext()).toMutableList()
        val selectedIndex = PreferencesHelper.getSelectedListIndex(requireContext())
        android.util.Log.d("PlayerFragment", "setupListDropdown: listNames=$listNames, selectedIndex=$selectedIndex")

        listDropdownAdapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item_light,
            listNames
        )
        listDropdownAdapter?.setDropDownViewResource(R.layout.spinner_dropdown_item_light)
        listDropdown.adapter = listDropdownAdapter

        // Aktuelle Auswahl setzen ohne Listener zu triggern
        isListDropdownInitializing = true
        if (selectedIndex in listNames.indices) {
            listDropdown.setSelection(selectedIndex)
        } else if (listNames.isNotEmpty()) {
            listDropdown.setSelection(0)
        }
        isListDropdownInitializing = false

        listDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Farbe des Dropdown-Texts anpassen
                lastOverlayForeground?.let { color ->
                    (view as? TextView)?.setTextColor(color)
                }

                if (isListDropdownInitializing) return

                val currentIndex = PreferencesHelper.getSelectedListIndex(requireContext())

                if (position != currentIndex) {
                    switchToList(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Wechselt zur angegebenen Stationsliste (per Index) und aktualisiert die Playlist
     */
    private fun switchToList(index: Int) {
        // Zuerst leere Listen aufräumen
        if (PreferencesHelper.cleanupEmptyLists(requireContext())) {
            refreshListDropdown()
        }

        PreferencesHelper.setSelectedListIndex(requireContext(), index)

        // Prüfe ob neue Liste leer ist
        val stations = PreferencesHelper.getStations(requireContext())
        if (stations.isEmpty()) {
            Log.w("PlayerFragment", "Gewählte Liste (Index $index) ist leer. Wechsel ins StationsFragment.")
            (activity as? MainActivity)?.showStationsPage()
            return
        }

        // Playlist im Service aktualisieren
        StateHelper.isPlaylistChangePending = true
        requireContext().startService(
            Intent(requireContext(), StreamingService::class.java).apply {
                action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
            }
        )

        // UI aktualisieren
        reloadPlaylist()
    }

    /**
     * Aktualisiert die Listen-Dropdown (z.B. nach Änderungen an den Listen)
     */
    private fun refreshListDropdown() {
        val listNames = PreferencesHelper.getListNames(requireContext()).toMutableList()
        val selectedIndex = PreferencesHelper.getSelectedListIndex(requireContext())
        Log.d("PlayerFragment", "refreshListDropdown: listNames=$listNames, selectedIndex=$selectedIndex")

        isListDropdownInitializing = true
        listDropdownAdapter?.clear()
        listDropdownAdapter?.addAll(listNames)
        listDropdownAdapter?.notifyDataSetChanged()

        Log.d("PlayerFragment", "refreshListDropdown: adapterCount=${listDropdownAdapter?.count}")
        if (selectedIndex in listNames.indices) {
            listDropdown.setSelection(selectedIndex)
        } else if (listNames.isNotEmpty()) {
            // Fallback zu Index 0
            listDropdown.setSelection(0)
            PreferencesHelper.setSelectedListIndex(requireContext(), 0)
        }
        isListDropdownInitializing = false
    }

    private fun reloadPlaylist() {
        val controller = mediaServiceController.mediaController ?: return

        if (controller.mediaItemCount == 0) {
            Log.w("PlayerFragment", "\u26a0\ufe0f Playlist leer nach Reload. Wechsel ins StationsFragment.")
            (activity as? MainActivity)?.showStationsPage()
            return
        }

        val shortcuts = (0 until controller.mediaItemCount).mapNotNull { i ->
            val mediaItem = controller.getMediaItemAt(i)
            val extras = mediaItem.mediaMetadata.extras ?: return@mapNotNull null
            val label = extras.getString("EXTRA_STATION_NAME") ?: return@mapNotNull null
            val iconUrl = extras.getString("EXTRA_ICON_URL") ?: ""
            val mediaId = mediaItem.mediaId
            ShortcutItem(label, iconUrl, mediaId, i)
        }
        shortcutAdapter.setItems(shortcuts)
        val currentIndex = controller.currentMediaItemIndex
        shortcutAdapter.selectedIndex = if (currentIndex >= 0) currentIndex else 0

        // Adapter wiederverwenden statt neu erstellen
        coverPageAdapter?.let { adapter ->
            adapter.backgroundEffect = backgroundEffect
            adapter.updateMediaItems()
            adapter.mediaItems.forEach { item ->
                Glide.with(requireContext()).load(item.iconURL).preload()
            }
        } ?: run {
            // Fallback: neuen Adapter erstellen falls noch keiner existiert
            coverPageAdapter = CoverPageAdapter(mediaServiceController, backgroundEffect)
            coverPageAdapter?.onColorChanged = { position, color ->
                if (position == viewPager.currentItem) {
                    updateOverlayColors(color)
                }
            }
            viewPager.adapter = coverPageAdapter
            coverPageAdapter?.mediaItems?.forEach { item ->
                Glide.with(requireContext()).load(item.iconURL).preload()
            }
        }

        val streamIndex = mediaServiceController.getCurrentStreamIndex()
        viewPager.setCurrentItem(streamIndex, false)
        updateOverlayUI(streamIndex)
    }

    private fun refreshCurrentCover() {
        val controller = mediaServiceController.mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= controller.mediaItemCount) return

        val mediaItem = controller.getMediaItemAt(currentIndex)
        val defaultIconUrl = mediaItem.mediaMetadata.extras?.getString("EXTRA_ICON_URL") ?: ""

        // Hole aktuelle Track-Info aus ViewModel
        val trackInfo = spotifyTrackViewModel.trackInfo.value

        // Bestimme Cover-URL basierend auf coverMode UND showingMetaCover Status
        val metaCoverUrl = trackInfo?.bestCoverUrl?.takeIf { it.isNotBlank() }
        val coverUrlToUse = if (coverMode == CoverMode.META && showingMetaCover && metaCoverUrl != null) {
            metaCoverUrl
        } else {
            defaultIconUrl
        }

        // Update Adapter mit neuer Cover-URL
        coverPageAdapter?.updateCoverUrl(currentIndex, coverUrlToUse)
    }

    override fun onDestroyView() {
        if (initialized) {
            try {
                context?.unregisterReceiver(autoplayReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver was not registered, ignore
            }
            try {
                context?.let {
                    LocalBroadcastManager.getInstance(it).unregisterReceiver(stationsUpdateReceiver)
                }
            } catch (e: IllegalArgumentException) {
                // Receiver was not registered, ignore
            }
            countdownHandler.removeCallbacksAndMessages(null)
            bannerHandler.removeCallbacksAndMessages(null)
            volumeHandler?.removeCallbacksAndMessages(null)
            volumeHandler = null
            volumeDismissRunnable = null
            recordHandler?.removeCallbacksAndMessages(null)
            recordHandler = null
            recordStartRunnable = null
            // Clear runnable references to prevent memory leaks
            countdownRunnable = null
            bannerRunnable = null
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)

            // Unregister ViewPager callback to prevent memory leak
            pageChangeCallback?.let {
                if (::viewPager.isInitialized) {
                    viewPager.unregisterOnPageChangeCallback(it)
                }
            }
            pageChangeCallback = null

            // Nur trennen wenn Fragment wirklich entfernt wird (nicht bei Orientierungswechsel)
            val act = activity
            val shouldDisconnect = isRemoving ||
                (act != null && act.isFinishing && !act.isChangingConfigurations)
            if (shouldDisconnect) {
                mediaServiceController.disconnect()
            }
        }
        super.onDestroyView()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && initialized) {
            // Fragment wird wieder sichtbar - prüfe ob UI-Rebuild nötig
            if (StateHelper.isPlaylistChangePending) {
                reloadPlaylist()
                StateHelper.isPlaylistChangePending = false
            }
        }
    }

    private fun showBottomSheet() {
        val tag = MediaItemOptionsBottomSheet.TAG
        if (parentFragmentManager.findFragmentByTag(tag) != null) return
        MediaItemOptionsBottomSheet().show(parentFragmentManager, tag)
    }

    fun enableMarquee(vararg views: TextView) {
        views.forEach { it.isSelected = true }
    }

    private fun saveManualLog() {
        val controller = mediaServiceController.mediaController ?: return
        val extras = controller.currentMediaItem?.mediaMetadata?.extras ?: return
        val station = extras.getString("EXTRA_STATION_NAME") ?: ""
        val trackInfo = spotifyTrackViewModel.trackInfo.value

        val entry = MetaLogEntry(
            timestamp = System.currentTimeMillis(),
            station = station,
            title = trackInfo?.trackName ?: "",
            artist = trackInfo?.artistName ?: "",
            url = trackInfo?.spotifyUrl?.takeIf { it.isNotBlank() },
            coverUrl = trackInfo?.bestCoverUrl,
            manual = true
        )

        MetaLogHelper.addLog(requireContext(), entry)
        Toast.makeText(requireContext(), getString(R.string.manual_log_saved), Toast.LENGTH_SHORT).show()
        updateManualLogButtonState(trackInfo)
    }

    private fun updateManualLogButtonState(trackInfo: UITrackInfo?) {
        val logs = MetaLogHelper.getLogs(requireContext())
        val last = logs.firstOrNull()
        val extras = mediaServiceController.mediaController
            ?.currentMediaItem?.mediaMetadata?.extras
        val station = extras?.getString("EXTRA_STATION_NAME") ?: ""
        val same = last != null &&
            last.station == station &&
            last.title == (trackInfo?.trackName ?: "") &&
            last.artist == (trackInfo?.artistName ?: "") &&
            last.url == (trackInfo?.spotifyUrl?.takeIf { it.isNotBlank() }) &&
            last.manual

        buttonManualLog.isEnabled = !same
        buttonManualLog.alpha = if (same) 0.5f else 1.0f
    }

    private fun openLyricsSheet() {
        val trackInfo = spotifyTrackViewModel.trackInfo.value
        val artist = trackInfo?.artistName ?: ""
        val title = trackInfo?.trackName ?: ""

        if (artist.isBlank() && title.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.lyrics_no_track), Toast.LENGTH_SHORT).show()
            return
        }

        val lyricsSheet = LyricsBottomSheet.newInstance(artist, title)
        lyricsSheet.show(parentFragmentManager, LyricsBottomSheet.TAG)
    }

    private fun showCountdown(duration: Int) {
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        var remaining = duration
        countdownTextView.text = getString(R.string.minimizing_in, remaining)
        countdownTextView.visibility = View.VISIBLE

        countdownRunnable = object : Runnable {
            override fun run() {
                remaining--
                if (remaining <= 0) {
                    hideCountdown()
                    return
                }
                countdownTextView.text = getString(R.string.minimizing_in, remaining)
                countdownHandler.postDelayed(this, 1000)
            }
        }
        countdownHandler.postDelayed(countdownRunnable!!, 1000)
    }

    private fun hideCountdown() {
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        countdownTextView.visibility = View.GONE
    }

    private fun showConnecting() {
        if (!showInfoBanner) return
        bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
        if (::connectingBanner.isInitialized) {
            connectingBanner.text = getString(R.string.connecting)
            connectingBanner.setBackgroundResource(R.drawable.rounded_blue_transparent_bg)
            connectingBanner.visibility = View.VISIBLE
        }
    }

    private fun showConnected() {
        if (!showInfoBanner) return
        bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
        if (::connectingBanner.isInitialized) {
            connectingBanner.text = getString(R.string.connected)
            connectingBanner.setBackgroundResource(R.drawable.rounded_green_transparent_bg)
            connectingBanner.visibility = View.VISIBLE
            bannerRunnable = Runnable { hideConnecting() }
            bannerHandler.postDelayed(bannerRunnable!!, Keys.CONNECTED_BANNER_DURATION_MS)
        }
    }

    private fun showError(message: String?) {
        if (!showInfoBanner) return
        bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
        if (::connectingBanner.isInitialized) {
            connectingBanner.text = getString(R.string.playback_error, message ?: "unknown")
            connectingBanner.setBackgroundResource(R.drawable.rounded_red_transparent_bg)
            connectingBanner.visibility = View.VISIBLE
        }
    }

    private fun hideConnecting() {
        bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
        if (::connectingBanner.isInitialized) {
            connectingBanner.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecordButton() {
        buttonRecord.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (StreamRecordHelper.isRecording()) {
                        // Already recording - stop immediately on tap
                        stopRecording()
                    } else {
                        // Not recording - start countdown for long press
                        recordHandler?.removeCallbacksAndMessages(null)
                        recordHandler = Handler(Looper.getMainLooper())
                        recordStartRunnable = Runnable {
                            startRecording()
                        }
                        recordHandler?.postDelayed(recordStartRunnable!!, Keys.RECORD_START_HOLD_DURATION_MS)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Cancel pending recording start if finger lifted
                    recordStartRunnable?.let { recordHandler?.removeCallbacks(it) }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRotateLockButton() {
        val button = buttonRotateLock ?: return

        // Show button only if setting is enabled
        val showRotateLock = prefs.getBoolean(Keys.PREF_SHOW_ROTATE_LOCK, false)
        button.visibility = if (showRotateLock) View.VISIBLE else View.GONE

        // Update icon based on current lock state
        updateRotateLockIcon()

        button.setOnClickListener {
            val currentMode = ScreenOrientationMode.fromName(
                prefs.getString(Keys.PREF_SCREEN_ORIENTATION, ScreenOrientationMode.AUTO.name)
            )

            if (currentMode == ScreenOrientationMode.AUTO) {
                // Currently unlocked -> lock to current orientation
                val currentOrientation = resources.configuration.orientation
                val newMode = if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    ScreenOrientationMode.LANDSCAPE
                } else {
                    ScreenOrientationMode.PORTRAIT
                }

                // Save to preferences
                prefs.edit()
                    .putString(Keys.PREF_SCREEN_ORIENTATION, newMode.name)
                    .apply()

                // Apply the orientation lock
                activity?.requestedOrientation = when (newMode) {
                    ScreenOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    ScreenOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }

                Toast.makeText(
                    requireContext(),
                    if (newMode == ScreenOrientationMode.LANDSCAPE)
                        getString(R.string.rotation_locked_landscape)
                    else
                        getString(R.string.rotation_locked_portrait),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // Currently locked -> unlock (set to AUTO)
                prefs.edit()
                    .putString(Keys.PREF_SCREEN_ORIENTATION, ScreenOrientationMode.AUTO.name)
                    .apply()

                // Remove orientation lock
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

                Toast.makeText(
                    requireContext(),
                    getString(R.string.rotation_unlocked),
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Update icon after state change
            updateRotateLockIcon()
        }
    }

    private fun updateRotateLockIcon() {
        val button = buttonRotateLock ?: return
        val currentMode = ScreenOrientationMode.fromName(
            prefs.getString(Keys.PREF_SCREEN_ORIENTATION, ScreenOrientationMode.AUTO.name)
        )

        if (currentMode == ScreenOrientationMode.AUTO) {
            // Unlocked - show unlock icon with lower alpha
            button.setImageResource(R.drawable.ic_rotate_unlock)
            button.alpha = 0.35f
        } else {
            // Locked - show lock icon with higher alpha
            button.setImageResource(R.drawable.ic_rotate_lock)
            button.alpha = 0.7f
        }
    }

    private fun startRecording() {
        val controller = mediaServiceController.mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= controller.mediaItemCount) {
            Toast.makeText(requireContext(), getString(R.string.recording_no_stream), Toast.LENGTH_SHORT).show()
            return
        }

        val mediaItem = controller.getMediaItemAt(currentIndex)
        val streamUrl = mediaItem.localConfiguration?.uri?.toString()
        val stationName = mediaItem.mediaMetadata.extras?.getString("EXTRA_STATION_NAME") ?: "Unknown"

        if (streamUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.recording_no_stream), Toast.LENGTH_SHORT).show()
            return
        }

        val success = StreamRecordHelper.startRecording(requireContext(), streamUrl, stationName)
        updateRecordButtonState()
        if (success) {
            Toast.makeText(requireContext(), getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), getString(R.string.recording_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        val fileName = StreamRecordHelper.stopRecording(requireContext())
        updateRecordButtonState()
        if (fileName != null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.recording_stopped, fileName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun toggleRecording() {
        if (StreamRecordHelper.isRecording()) {
            // Stop recording
            val fileName = StreamRecordHelper.stopRecording(requireContext())
            updateRecordButtonState()
            if (fileName != null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.recording_stopped, fileName),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            // Start recording
            val controller = mediaServiceController.mediaController ?: return
            val currentIndex = controller.currentMediaItemIndex
            if (currentIndex < 0 || currentIndex >= controller.mediaItemCount) {
                Toast.makeText(requireContext(), getString(R.string.recording_no_stream), Toast.LENGTH_SHORT).show()
                return
            }

            val mediaItem = controller.getMediaItemAt(currentIndex)
            val streamUrl = mediaItem.localConfiguration?.uri?.toString()
            val stationName = mediaItem.mediaMetadata.extras?.getString("EXTRA_STATION_NAME") ?: "Unknown"

            if (streamUrl.isNullOrBlank()) {
                Toast.makeText(requireContext(), getString(R.string.recording_no_stream), Toast.LENGTH_SHORT).show()
                return
            }

            val success = StreamRecordHelper.startRecording(requireContext(), streamUrl, stationName)
            updateRecordButtonState()
            if (success) {
                Toast.makeText(requireContext(), getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), getString(R.string.recording_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateRecordButtonState() {
        // Visibility basierend auf Recording-Einstellung
        val recordingEnabled = prefs.getBoolean(Keys.PREF_RECORDING_ENABLED, true)
        buttonRecord.visibility = if (recordingEnabled) View.VISIBLE else View.GONE

        if (StreamRecordHelper.isRecording()) {
            buttonRecord.setImageResource(R.drawable.ic_button_record_stop)
            // Rot während der Aufnahme
            buttonRecord.setColorFilter(requireContext().getColor(R.color.category_recording))
        } else {
            buttonRecord.setImageResource(R.drawable.ic_button_record)
            // Farbe zurücksetzen (falls von Recording kommend)
            lastOverlayForeground?.let { buttonRecord.setColorFilter(it) }
        }
    }

}
