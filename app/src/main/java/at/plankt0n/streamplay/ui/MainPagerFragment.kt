package at.plankt0n.streamplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.R

class MainPagerFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private var pagerAdapter: MainPagerAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_main_pager, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewPager = view.findViewById(R.id.main_view_pager)
        viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL

        // Adapter nur einmal erstellen
        if (pagerAdapter == null) {
            pagerAdapter = MainPagerAdapter(this)
        }
        viewPager.adapter = pagerAdapter

        if (PreferencesHelper.getStations(requireContext()).isEmpty()) {
            viewPager.setCurrentItem(1, false)
        }
    }

    fun showPlayer() { viewPager.currentItem = 0 }
    fun showStations() { viewPager.currentItem = 1 }

    private class MainPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> PlayerFragment()
            else -> StationsFragment()
        }
    }
}
