package at.plankt0n.streamplay.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.CoverPageAdapter
import at.plankt0n.streamplay.helper.MediaServiceController
import at.plankt0n.streamplay.viewmodel.SpotifyMetaViewModel
import com.bumptech.glide.Glide
import com.tbuonomo.viewpagerdotsindicator.WormDotsIndicator

class PlayerFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsIndicator: WormDotsIndicator
    private lateinit var mediaServiceController: MediaServiceController
    private lateinit var spotifyMetaViewModel: SpotifyMetaViewModel

    private lateinit var stationNameTextView: TextView
    private lateinit var stationIconImageView: ImageView

    private lateinit var playPauseButton: ImageButton
    private lateinit var buttonBack: ImageButton
    private lateinit var buttonForward: ImageButton
    private lateinit var buttonMenu: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonMenu = view.findViewById(R.id.button_menu)
        viewPager = view.findViewById(R.id.view_pager)
        viewPager.offscreenPageLimit = 2 // 2 Seiten links + 2 Seiten rechts vorab laden

        dotsIndicator = view.findViewById(R.id.dots_indicator)
        stationNameTextView = view.findViewById(R.id.station_overlay_stationname)
        stationIconImageView = view.findViewById(R.id.station_overlay_stationIcon)
        playPauseButton = view.findViewById(R.id.button_play_pause)
        buttonBack = view.findViewById(R.id.button_back)
        buttonForward = view.findViewById(R.id.button_forward)

        spotifyMetaViewModel = ViewModelProvider(requireActivity())[SpotifyMetaViewModel::class.java]

        mediaServiceController = MediaServiceController(requireContext())
        mediaServiceController.initializeAndConnect(
            onConnected = { controller ->
                if (controller.mediaItemCount == 0) {
                    Log.w("PlayerFragment", "⚠️ MediaSession ist leer! Wechsel ins StationsFragment.")
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

                // Preload Cover-Bilder für flüssiges Scrollen!
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
                buttonMenu.setOnClickListener {
                 showBottomSheet()
                }
            },
            onPlaybackChanged = { updatePlayPauseIcon(it) },
            onStreamIndexChanged = { index ->
                viewPager.setCurrentItem(index, true)
                updateOverlayUI(index)
            },
            onMetadataChanged = { /* optional */ },
            onTimelineChanged = {
                Log.d("PlayerFragment", "🔁 Timeline geändert! Grund: $it")
                reloadPlaylist()
            }
        )
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
            .placeholder(R.drawable.ic_placeholder_logo)
            .error(R.drawable.ic_stationcover_placeholder)
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

        // Preload Cover-Bilder erneut!
        coverPageAdapter.mediaItems.forEach { item ->
            Glide.with(requireContext())
                .load(item.iconURL)
                .preload()
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
}
