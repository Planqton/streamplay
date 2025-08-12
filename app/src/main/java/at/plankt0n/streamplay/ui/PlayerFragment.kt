package at.plankt0n.streamplay.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.ViewFlipper
import android.widget.SeekBar
import android.widget.FrameLayout
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
import at.plankt0n.streamplay.helper.LiveCoverHelper
import at.plankt0n.streamplay.helper.MediaServiceController
import at.plankt0n.streamplay.helper.StateHelper
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.MetaLogHelper
import at.plankt0n.streamplay.viewmodel.UITrackViewModel
import at.plankt0n.streamplay.viewmodel.UITrackInfo
import at.plankt0n.streamplay.data.MetaLogEntry
import at.plankt0n.streamplay.Keys
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.tbuonomo.viewpagerdotsindicator.WormDotsIndicator
import android.widget.Toast

class PlayerFragment : Fragment() {

    private var initialized = false

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsIndicator: WormDotsIndicator
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
    private lateinit var buttonManualLog: ImageButton
    private lateinit var shortcutRecyclerView: RecyclerView
    private lateinit var shortcutAdapter: ShortcutAdapter
    private lateinit var countdownTextView: TextView
    private lateinit var connectingBanner: TextView
    private lateinit var metaFlipper: ViewFlipper
    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private val bannerHandler = Handler(Looper.getMainLooper())
    private var bannerRunnable: Runnable? = null
    private lateinit var prefs: SharedPreferences
    private var showInfoBanner: Boolean = true
    private var backgroundEffect = LiveCoverHelper.BackgroundEffect.FADE
    private var coverMode = CoverMode.META
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
            if (initialized) reloadPlaylist()
        }
        if (key == "cover_mode") {
            coverMode = try {
                CoverMode.valueOf(shared.getString(key, CoverMode.META.name)!!)
            } catch (e: IllegalArgumentException) {
                CoverMode.META
            }
            if (initialized) reloadPlaylist()
        }
        if (key == Keys.PREF_UPDATE_AVAILABLE) {
            val showBadge = shared.getBoolean(key, false)
            updateBadge.visibility = if (showBadge) View.VISIBLE else View.GONE
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

    var isMuted = false
    private var showingMetaCover = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonMenu = view.findViewById(R.id.button_menu)
        updateBadge = view.findViewById(R.id.update_badge)
        buttonMenu.setOnClickListener { showBottomSheet() }

        if (PreferencesHelper.getStations(requireContext()).isEmpty()) {
            Log.w("PlayerFragment", "\u26a0\ufe0f Keine Stationen gespeichert, Wechsel ins StationsFragment.")
            (activity as? MainActivity)?.showStationsPage()
            return
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

        dotsIndicator = view.findViewById(R.id.dots_indicator)
        stationNameTextView = view.findViewById(R.id.station_overlay_stationname)
        stationIconImageView = view.findViewById(R.id.station_overlay_stationIcon)
        playPauseButton = view.findViewById(R.id.button_play_pause)
        buttonBack = view.findViewById(R.id.button_back)
        buttonForward = view.findViewById(R.id.button_forward)
        buttonSpotify = view.findViewById(R.id.button_spotify)
        buttonMute = view.findViewById(R.id.button_mute_unmute)
        buttonShare = view.findViewById(R.id.button_share)
        buttonManualLog = view.findViewById(R.id.button_manual_log)
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
        coverMode = try {
            CoverMode.valueOf(prefs.getString("cover_mode", CoverMode.META.name)!!)
        } catch (e: IllegalArgumentException) {
            CoverMode.META
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

        mediaServiceController = MediaServiceController(requireContext())
        mediaServiceController.initializeAndConnect(
            onConnected = { controller ->
                val shortcuts = (0 until controller.mediaItemCount).mapNotNull { i ->
                    val mediaItem = controller.getMediaItemAt(i)
                    val extras = mediaItem.mediaMetadata.extras ?: return@mapNotNull null
                    val label = extras.getString("EXTRA_STATION_NAME") ?: return@mapNotNull null
                    val iconUrl = extras.getString("EXTRA_ICON_URL") ?: ""
                    val mediaId = mediaItem.mediaId
                    ShortcutItem(label, iconUrl, mediaId, i)
                }
                shortcutAdapter.setItems(shortcuts)

                if (controller.mediaItemCount == 0) {
                    if (!StateHelper.hasAutoOpenedDiscover) {
                        Log.w(
                            "PlayerFragment",
                            "\u26a0\ufe0f MediaSession ist leer! Öffne DiscoverFragment."
                        )
                        StateHelper.hasAutoOpenedDiscover = true
                        requireActivity().supportFragmentManager
                            .beginTransaction()
                            .setReorderingAllowed(true)
                            .replace(R.id.fragment_container, DiscoverFragment())
                            .addToBackStack(null)
                            .commit()
                    }
                    return@initializeAndConnect
                } else {
                    StateHelper.hasAutoOpenedDiscover = false
                }

                val coverPageAdapter = CoverPageAdapter(mediaServiceController, backgroundEffect)
                viewPager.adapter = coverPageAdapter
                dotsIndicator.setViewPager2(viewPager)

                coverPageAdapter.mediaItems.forEach { item ->
                    Glide.with(requireContext())
                        .load(item.iconURL)
                        .preload()
                }

                val currentIndex = controller.currentMediaItemIndex
                viewPager.setCurrentItem(currentIndex, false)
                updateOverlayUI(currentIndex)
                updatePlayPauseIcon(controller.isPlaying)
                updateManualLogButtonState(spotifyTrackViewModel.trackInfo.value)

                requireContext().startService(
                    Intent(requireContext(), StreamingService::class.java).apply {
                        action = Keys.ACTION_REFRESH_METADATA
                    }
                )
                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        mediaServiceController.seekToIndex(position)
                    }
                })

            },
            onPlaybackChanged = { updatePlayPauseIcon(it) },
            onStreamIndexChanged = { index ->
                viewPager.setCurrentItem(index, true)
                updateOverlayUI(index)
                updateManualLogButtonState(spotifyTrackViewModel.trackInfo.value)
            },
            onMetadataChanged = {},
            onTimelineChanged = {
                Log.d("PlayerFragment", "\uD83D\uDD01 Timeline ge\u00E4ndert! Grund: $it")
                reloadPlaylist()
            },
            onPlaybackStateChanged = { state ->
                when (state) {
                    Player.STATE_BUFFERING -> showConnecting()
                    Player.STATE_READY -> showConnected()
                }
            },
            onPlayerError = { error ->
                showError(error.message)
            }
        )

        spotifyTrackViewModel = ViewModelProvider(requireActivity())[UITrackViewModel::class.java]
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

        buttonManualLog.setOnClickListener {
            saveManualLog()
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
    }

    private fun observeSpotifyTrackInfo() {
        spotifyTrackViewModel.trackInfo.removeObservers(viewLifecycleOwner)
        spotifyTrackViewModel.trackInfo.observe(viewLifecycleOwner) { trackInfo ->
            val titleTextView = view?.findViewById<TextView>(R.id.meta_overlay_Title)
            val artistTextView = view?.findViewById<TextView>(R.id.meta_overlay_Artist)
            val genreTextView = view?.findViewById<TextView>(R.id.meta_overlay_Genre)
            val albumTextView = view?.findViewById<TextView>(R.id.meta_overlay_Album)
            val stationIconView = view?.findViewById<ShapeableImageView>(R.id.meta_cover_image)

            val spotifyUrl = trackInfo?.spotifyUrl
            val spotifyAvailable = !spotifyUrl.isNullOrBlank()
            buttonSpotify.isEnabled = spotifyAvailable
            buttonSpotify.alpha = if (spotifyAvailable) 1.0f else 0.5f
            buttonSpotify.setColorFilter(
                if (spotifyAvailable) requireContext().getColor(R.color.black)
                else requireContext().getColor(R.color.colorAccent)
            )

            if (trackInfo == null) {
                titleTextView?.text = getString(R.string.unknown_title)
                artistTextView?.text = getString(R.string.unknown_artist)
                genreTextView?.text = getString(R.string.unknown_genre)
                albumTextView?.text = getString(R.string.unknown_album)
                metaFlipper.stopFlipping()
                metaFlipper.displayedChild = 0

                Glide.with(requireContext())
                    .load(R.drawable.placeholder_spotify_dark)
                    .placeholder(R.drawable.placeholder_spotify_dark)
                    .error(R.drawable.placeholder_spotify_dark)
                    .into(stationIconView!!)

                return@observe
            }

            val recyclerView = viewPager.getChildAt(0) as? RecyclerView
            val holder = recyclerView?.findViewHolderForAdapterPosition(viewPager.currentItem)
                    as? CoverPageAdapter.CoverViewHolder ?: return@observe

            val defaultIconUrl = mediaServiceController.mediaController
                ?.getMediaItemAt(viewPager.currentItem)
                ?.mediaMetadata?.extras?.getString("EXTRA_ICON_URL") ?: ""

            val metaCoverUrl = trackInfo.bestCoverUrl?.takeIf { it.isNotBlank() }
            val imageUrlToLoad = when (coverMode) {
                CoverMode.META -> metaCoverUrl ?: defaultIconUrl
                CoverMode.STATION -> defaultIconUrl
            }

            LiveCoverHelper.loadCoverWithBackground(
                context = requireContext(),
                imageUrl = imageUrlToLoad,
                imageView = holder.coverImage,
                backgroundTarget = holder.itemView,
                defaultColor = requireContext().getColor(R.color.default_background),
                lastColor = holder.lastColor,
                lastEffect = holder.lastEffect,
                effect = backgroundEffect,
                onNewColor = { holder.lastColor = it },
                onNewEffect = { holder.lastEffect = it }
            )

            if (coverMode == CoverMode.META && metaCoverUrl != null) {
                showingMetaCover = true
                holder.coverImage.setOnClickListener { view ->
                    val targetUrl = if (showingMetaCover) defaultIconUrl else metaCoverUrl
                    view.animate()
                        .rotationY(90f)
                        .setDuration(150)
                        .withEndAction {
                            LiveCoverHelper.loadCoverWithBackground(
                                context = requireContext(),
                                imageUrl = targetUrl,
                                imageView = holder.coverImage,
                                backgroundTarget = holder.itemView,
                                defaultColor = requireContext().getColor(R.color.default_background),
                                lastColor = holder.lastColor,
                                lastEffect = holder.lastEffect,
                                effect = backgroundEffect,
                                onNewColor = { holder.lastColor = it },
                                onNewEffect = { holder.lastEffect = it }
                            )
                            holder.coverImage.rotationY = -90f
                            holder.coverImage.animate().rotationY(0f).setDuration(150).start()
                        }
                        .start()
                    showingMetaCover = !showingMetaCover
                }
            } else {
                holder.coverImage.setOnClickListener(null)
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

            if (coverMode == CoverMode.META && !trackInfo.bestCoverUrl.isNullOrBlank()) {
                Glide.with(requireContext())
                    .load(trackInfo.bestCoverUrl)
                    .placeholder(R.drawable.ic_placeholder_logo)
                    .error(R.drawable.ic_stationcover_placeholder)
                    .into(stationIconView!!)
            } else {
                Glide.with(requireContext())
                    .load(defaultIconUrl)
                    .placeholder(R.drawable.ic_placeholder_logo)
                    .error(R.drawable.ic_stationcover_placeholder)
                    .into(stationIconView!!)
            }

            enableMarquee(titleTextView!!, artistTextView!!, genreTextView!!, albumTextView!!)
            updateManualLogButtonState(trackInfo)
        }
    }

    private fun updateOverlayUI(index: Int) {
        val controller = mediaServiceController.mediaController ?: return
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

        val handler = Handler(Looper.getMainLooper())
        val dismissRunnable = Runnable {
            overlayContainer.removeView(sliderLayout)
            previousContent?.visibility = View.VISIBLE
            volumeSlider = null
        }
        handler.postDelayed(dismissRunnable, Keys.VOLUME_SLIDER_INITIAL_HIDE_DELAY_MS)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                handler.removeCallbacks(dismissRunnable)
                handler.postDelayed(
                    dismissRunnable,
                    Keys.VOLUME_SLIDER_POST_ADJUST_HIDE_DELAY_MS
                )
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
                handler.removeCallbacks(dismissRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(dismissRunnable)
                handler.postDelayed(
                    dismissRunnable,
                    Keys.VOLUME_SLIDER_POST_ADJUST_HIDE_DELAY_MS
                )
            }
        })
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

        val coverPageAdapter = CoverPageAdapter(mediaServiceController, backgroundEffect)
        viewPager.adapter = coverPageAdapter
        dotsIndicator.setViewPager2(viewPager)

        coverPageAdapter.mediaItems.forEach { item ->
            Glide.with(requireContext()).load(item.iconURL).preload()
        }

        val currentIndex = mediaServiceController.getCurrentStreamIndex()
        viewPager.setCurrentItem(currentIndex, false)
        updateOverlayUI(currentIndex)
    }

    override fun onDestroyView() {
        if (initialized) {
            requireContext().unregisterReceiver(autoplayReceiver)
            countdownHandler.removeCallbacksAndMessages(null)
            bannerHandler.removeCallbacksAndMessages(null)
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
            mediaServiceController.disconnect()
        }
        super.onDestroyView()
    }

    private fun showBottomSheet() {
        val bottomSheet = MediaItemOptionsBottomSheet()
        bottomSheet.show(parentFragmentManager, bottomSheet.tag)
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
}
