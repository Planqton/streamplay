package at.plankt0n.streamplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.PageViewMediaItemAdapter
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.MediaServiceController
import at.plankt0n.streamplay.viewmodel.SpotifyMetaViewModel
import com.bumptech.glide.Glide
import com.tbuonomo.viewpagerdotsindicator.WormDotsIndicator

class PlayerFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsIndicator: WormDotsIndicator
    private lateinit var adapter: PageViewMediaItemAdapter
    private lateinit var mediaServiceController: MediaServiceController
    private lateinit var spotifyMetaViewModel: SpotifyMetaViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.view_pager)
        dotsIndicator = view.findViewById(R.id.dots_indicator)

        // ViewModel holen
        spotifyMetaViewModel = ViewModelProvider(requireActivity())[SpotifyMetaViewModel::class.java]

        // Spotify-Infos live an die sichtbare Seite senden
        spotifyMetaViewModel.spotifyMetaInfo.observe(viewLifecycleOwner) { extendedInfo ->
            val position = viewPager.currentItem
            val viewHolder = viewPager.findViewWithTag<View>("view_$position")

            if (viewHolder != null) {
                viewHolder.findViewById<TextView>(R.id.meta_overlay_Artist)?.text = extendedInfo.artistName
                viewHolder.findViewById<TextView>(R.id.meta_overlay_Title)?.text = extendedInfo.trackName

                val metaCoverImage = viewHolder.findViewById<ImageView>(R.id.meta_cover_image)
                val coverImage = viewHolder.findViewById<ImageView>(R.id.cover_image)
                if (metaCoverImage != null && coverImage != null) {
                    Glide.with(this)
                        .load(extendedInfo.bestCoverUrl)
                        .placeholder(R.drawable.ic_placeholder_logo)
                        .into(metaCoverImage)

                    Glide.with(this)
                        .load(extendedInfo.bestCoverUrl)
                        .placeholder(R.drawable.ic_placeholder_logo)
                        .into(coverImage)
                }
            }
        }

        mediaServiceController = MediaServiceController(requireContext())
        mediaServiceController.initializeAndConnect(
            onConnected = { controller ->
                val sessionPlaylist: List<StationItem> = mediaServiceController.getCurrentPlaylist()
                if (sessionPlaylist.isEmpty()) {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, StationsFragment())
                        .commit()
                    return@initializeAndConnect
                }

                adapter = PageViewMediaItemAdapter(sessionPlaylist, { _, _ ->
                    showBottomSheet()
                }, mediaServiceController)
                viewPager.adapter = adapter
                dotsIndicator.setViewPager2(viewPager)

                val currentIndex = controller.currentMediaItemIndex
                viewPager.setCurrentItem(currentIndex, false)
            },
            onPlaybackChanged = { isPlaying ->
                adapter.updatePlayState(isPlaying)
            },
            onStreamIndexChanged = { index ->
                viewPager.setCurrentItem(index, true)
            },
            onMetadataChanged = { title -> },
            onTimelineChanged = { }
        )
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
