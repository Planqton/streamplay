// Datei: at.plankt0n.streamplay.ui.PlayerFragment.kt
package at.plankt0n.streamplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.PageViewMediaItemAdapter
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.MediaServiceController
import com.tbuonomo.viewpagerdotsindicator.WormDotsIndicator

class PlayerFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsIndicator: WormDotsIndicator
    private lateinit var adapter: PageViewMediaItemAdapter
    private lateinit var mediaServiceController: MediaServiceController

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

                // PageViewer -> Player synchronisieren
                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        mediaServiceController.seekToIndex(position)
                    }
                })
            },
            onPlaybackChanged = { isPlaying ->
                // Play/Pause-Status in ALLEN Seiten aktualisieren
                adapter.updatePlayState(isPlaying)
            },
            onStreamIndexChanged = { index ->
                viewPager.setCurrentItem(index, true)
            },
            onMetadataChanged = { title ->
                // Optional: Metadaten
            },
            onTimelineChanged = {
                // Optional: Timeline-Änderungen
            }
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
