package at.plankt0n.streamplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.MediaItemAdapter
import at.plankt0n.streamplay.data.MediaItem

class PlayerFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: MediaItemAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_player, container, false)

        viewPager = rootView.findViewById(R.id.view_pager)

        // Dummy-MediaItems
        val dummyMediaItems = listOf(
            MediaItem("Dummy 1", "Titel 1", R.drawable.ic_placeholder_logo),
            MediaItem("Dummy 2", "Titel 2", R.drawable.ic_placeholder_logo),
            MediaItem("Dummy 3", "Titel 3", R.drawable.ic_placeholder_logo),
            MediaItem("Dummy 4", "Titel 4", R.drawable.ic_placeholder_logo),
            MediaItem("Dummy 5", "Titel 5", R.drawable.ic_placeholder_logo)
        )

        adapter = MediaItemAdapter(dummyMediaItems)
        viewPager.adapter = adapter

        return rootView
    }
}
