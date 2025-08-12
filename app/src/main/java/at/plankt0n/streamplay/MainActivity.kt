package at.plankt0n.streamplay

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.GitHubUpdateChecker
import at.plankt0n.streamplay.helper.MediaServiceController
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.ui.MainPagerFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var mainPagerFragment: MainPagerFragment? = null
    private var shortcutController: MediaServiceController? = null
    private var pendingShortcutStation: StationItem? = null

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

        supportFragmentManager.executePendingTransactions()
        handleShortcutIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        maybePlayPendingShortcutStation()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        if (intent?.action != Keys.ACTION_PLAY_STATION) return

        val uuid = intent.getStringExtra(Keys.EXTRA_STATION_UUID) ?: return
        val name = intent.getStringExtra(Keys.EXTRA_STATION_NAME) ?: return
        val streamUrl = intent.getStringExtra(Keys.EXTRA_STATION_STREAM_URL) ?: return
        val iconUrl = intent.getStringExtra(Keys.EXTRA_STATION_ICON_URL) ?: ""
        val station = StationItem(uuid, name, streamUrl, iconUrl)
        pendingShortcutStation = station
        maybePlayPendingShortcutStation()
    }

    private fun maybePlayPendingShortcutStation() {
        val station = pendingShortcutStation ?: return
        if (mainPagerFragment?.view != null) {
            playStationFromShortcut(station)
            pendingShortcutStation = null
        } else {
            Handler(Looper.getMainLooper()).postDelayed(
                { maybePlayPendingShortcutStation() },
                50
            )
        }
    }

    private fun playStationFromShortcut(station: StationItem) {
        val list = PreferencesHelper.getStations(this).toMutableList()
        var index = list.indexOfFirst { it.uuid == station.uuid }
        if (index == -1) {
            list.add(station)
            PreferencesHelper.saveStations(this, list)
            index = list.size - 1
        }

        PreferencesHelper.setLastPlayedStreamIndex(this, index)

        val refreshIntent = Intent(this, StreamingService::class.java).apply {
            action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
        }
        startService(refreshIntent)

        shortcutController = MediaServiceController(this)
        shortcutController?.initializeAndConnect(
            onConnected = { controller ->
                Handler(Looper.getMainLooper()).postDelayed({
                    val idx = shortcutController?.findIndexByMediaId(station.streamURL) ?: index
                    shortcutController?.playAtIndex(idx)
                }, 300)
            },
            onPlaybackChanged = {},
            onStreamIndexChanged = {},
            onMetadataChanged = {},
            onTimelineChanged = {}
        )
        showPlayerPage()
    }

    fun showPlayerPage() {
        mainPagerFragment?.showPlayer()
    }

    fun showStationsPage() {
        mainPagerFragment?.showStations()
    }
}
