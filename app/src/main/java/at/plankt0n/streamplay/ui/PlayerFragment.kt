package at.plankt0n.streamplay.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.CoverPageAdapter
import at.plankt0n.streamplay.adapter.ShortcutAdapter
import at.plankt0n.streamplay.data.ShortcutItem
import at.plankt0n.streamplay.helper.LiveCoverHelper
import at.plankt0n.streamplay.helper.MediaServiceController
import at.plankt0n.streamplay.viewmodel.UITrackViewModel
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.tbuonomo.viewpagerdotsindicator.WormDotsIndicator

class PlayerFragment : Fragment() {

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
    private lateinit var buttonSpotify: ImageButton
    private lateinit var buttonMute: ImageButton
    private lateinit var buttonShare: ImageButton
    private lateinit var shortcutRecyclerView: RecyclerView
    private lateinit var shortcutAdapter: ShortcutAdapter

    var isMuted = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonMenu = view.findViewById(R.id.button_menu)
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
                    Log.w("PlayerFragment", "\u26a0\ufe0f MediaSession ist leer! Wechsel ins StationsFragment.")
                    parentFragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .hide(this@PlayerFragment)
                        .add(R.id.fragment_container, StationsFragment())
                        .addToBackStack(null)
                        .commit()
                    return@initializeAndConnect
                }

                val coverPageAdapter = CoverPageAdapter(mediaServiceController)
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

                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        mediaServiceController.seekToIndex(position)
                    }
                })

                playPauseButton.setOnClickListener { mediaServiceController.togglePlayPause() }
                buttonBack.setOnClickListener { mediaServiceController.skipToPrevious() }
                buttonForward.setOnClickListener { mediaServiceController.skipToNext() }
                buttonMenu.setOnClickListener { showBottomSheet() }
            },
            onPlaybackChanged = { updatePlayPauseIcon(it) },
            onStreamIndexChanged = { index ->
                viewPager.setCurrentItem(index, true)
                updateOverlayUI(index)
            },
            onMetadataChanged = {},
            onTimelineChanged = {
                Log.d("PlayerFragment", "\ud83d\udd01 Timeline ge\u00e4ndert! Grund: $it")
                reloadPlaylist()
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
    }

    override fun onStart() {
        super.onStart()
        observeSpotifyTrackInfo()
    }

    private fun observeSpotifyTrackInfo() {
        spotifyTrackViewModel.trackInfo.removeObservers(viewLifecycleOwner)
        spotifyTrackViewModel.trackInfo.observe(viewLifecycleOwner) { trackInfo ->
            val flipper = view?.findViewById<ViewFlipper>(R.id.meta_flipper)
            val titleTextView = view?.findViewById<TextView>(R.id.meta_overlay_Title)
            val artistTextView = view?.findViewById<TextView>(R.id.meta_overlay_Artist)
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
                albumTextView?.text = getString(R.string.unknown_album)
                flipper?.stopFlipping()
                flipper?.setDisplayedChild(0)

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

            val imageUrlToLoad = trackInfo.bestCoverUrl?.takeIf { it.isNotBlank() } ?: defaultIconUrl

            LiveCoverHelper.loadCoverWithBackgroundFade(
                context = requireContext(),
                imageUrl = imageUrlToLoad,
                imageView = holder.coverImage,
                backgroundTarget = holder.itemView,
                defaultColor = requireContext().getColor(R.color.default_background),
                lastColor = holder.lastColor,
                onNewColor = { holder.lastColor = it }
            )

            titleTextView?.text = trackInfo.trackName.takeIf { it.isNotBlank() } ?: getString(R.string.unknown_title)
            artistTextView?.text = trackInfo.artistName.takeIf { it.isNotBlank() } ?: getString(R.string.unknown_artist)

            if (trackInfo.albumName.isNotBlank()) {
                albumTextView?.text = getString(R.string.album_prefix, trackInfo.albumName)
                if (flipper?.displayedChild != 0) flipper?.setDisplayedChild(0)
                flipper?.startFlipping()
            } else {
                flipper?.stopFlipping()
                flipper?.setDisplayedChild(0)
                albumTextView?.text = getString(R.string.unknown_album)
            }

            if (!trackInfo.bestCoverUrl.isNullOrBlank()) {
                Glide.with(requireContext())
                    .load(trackInfo.bestCoverUrl)
                    .placeholder(R.drawable.ic_placeholder_logo)
                    .error(R.drawable.ic_stationcover_placeholder)
                    .into(stationIconView!!)
            } else {
                Glide.with(requireContext()).clear(stationIconView!!)
                stationIconView!!.setImageDrawable(null)
            }

            enableMarquee(titleTextView!!, artistTextView!!, albumTextView!!)
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

    private fun reloadPlaylist() {
        val coverPageAdapter = CoverPageAdapter(mediaServiceController)
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
        super.onDestroyView()
        mediaServiceController.disconnect()
    }

    private fun showBottomSheet() {
        val bottomSheet = MediaItemOptionsBottomSheet()
        bottomSheet.show(parentFragmentManager, bottomSheet.tag)
    }

    fun enableMarquee(vararg views: TextView) {
        views.forEach { it.isSelected = true }
    }
}
