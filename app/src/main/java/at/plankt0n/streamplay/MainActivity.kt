package at.plankt0n.streamplay

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import at.plankt0n.streamplay.ui.MainPagerFragment

class MainActivity : AppCompatActivity() {

    private var mainPagerFragment: MainPagerFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, MainPagerFragment(), "mainPager")
                .commit()

        } else {
            mainPagerFragment =
                supportFragmentManager.findFragmentById(R.id.fragment_container) as? MainPagerFragment
        }
    }

    fun showPlayerPage() {
        mainPagerFragment?.showPlayer()
    }

    fun showStationsPage() {
        mainPagerFragment?.showStations()
    }
}
