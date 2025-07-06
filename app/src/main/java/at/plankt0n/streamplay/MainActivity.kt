package at.plankt0n.streamplay

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import at.plankt0n.streamplay.ui.MainPagerFragment
import at.plankt0n.streamplay.helper.GitHubUpdateChecker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var mainPagerFragment: MainPagerFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            GitHubUpdateChecker(this@MainActivity).silentCheckForUpdate()
        }

        if (savedInstanceState == null) {
            mainPagerFragment = MainPagerFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, mainPagerFragment!!, "mainPager")
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
