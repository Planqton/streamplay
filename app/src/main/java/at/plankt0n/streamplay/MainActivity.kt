package at.plankt0n.streamplay

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.GitHubUpdateChecker
import at.plankt0n.streamplay.helper.MediaServiceController
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.StateHelper
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.ScreenOrientationMode
import at.plankt0n.streamplay.ui.MainPagerFragment
import at.plankt0n.streamplay.ui.DiscoverFragment
import at.plankt0n.streamplay.helper.StationImportHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var mainPagerFragment: MainPagerFragment? = null
    private var shortcutController: MediaServiceController? = null
    private var pendingShortcutStation: StationItem? = null
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        applyOrientationPreference()

        setContentView(R.layout.activity_main)

        autoSyncIfEnabled()

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
        maybeShowOnboarding()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    private fun autoSyncIfEnabled() {
        val prefs = getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        when {
            prefs.getBoolean(Keys.PREF_AUTOSYNC_JSONBIN_STARTUP, false) -> {
                val url = prefs.getString(Keys.PREF_JSON_BIN_URL, "") ?: ""
                val key = prefs.getString(Keys.PREF_JSON_BIN_KEY, "") ?: ""
                if (url.isNotBlank() && key.isNotBlank()) {
                    Log.d("JSON AUTO SYNC>", "Starting JSONBin auto sync")
                    runBlocking {
                        try {
                            StationImportHelper.importStationsFromUrl(
                                this@MainActivity,
                                url,
                                true,
                                mapOf("X-Master-Key" to key)
                            )
                            Log.d("JSON AUTO SYNC>", "JSONBin auto sync completed")
                        } catch (e: Exception) {
                            Log.e("JSON AUTO SYNC>", "JSONBin auto sync failed: ${e.message}")
                        }
                    }
                } else {
                    Log.d("JSON AUTO SYNC>", "No JSONBin configuration")
                }
            }
            prefs.getBoolean(Keys.PREF_AUTOSYNC_JSON_STARTUP, false) -> {
                val url = prefs.getString(Keys.PREF_PERSONAL_SYNC_URL, "") ?: ""
                if (url.isNotBlank()) {
                    Log.d("JSON AUTO SYNC>", "Starting auto sync")
                    runBlocking {
                        try {
                            StationImportHelper.importStationsFromUrl(this@MainActivity, url, true)
                            Log.d("JSON AUTO SYNC>", "Auto sync completed")
                        } catch (e: Exception) {
                            Log.e("JSON AUTO SYNC>", "Auto sync failed: ${e.message}")
                        }
                    }
                } else {
                    Log.d("JSON AUTO SYNC>", "No personal URL configured")
                }
            }
            else -> {
                Log.d("JSON AUTO SYNC>", "Auto sync disabled")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        maybePlayPendingShortcutStation()
        applyOrientationPreference()
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

        StateHelper.isPlaylistChangePending = true

        val refreshIntent = Intent(this, StreamingService::class.java).apply {
            action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
        }
        startService(refreshIntent)

        shortcutController = MediaServiceController(this)
        shortcutController?.initializeAndConnect(
            onConnected = {
                val idx = shortcutController?.findIndexByMediaId(station.streamURL)?.takeIf { it >= 0 } ?: index
                shortcutController?.playAtIndex(idx)
            },
            onPlaybackChanged = {},
            onStreamIndexChanged = {},
            onMetadataChanged = {},
            onTimelineChanged = {
                val idx = shortcutController?.findIndexByMediaId(station.streamURL)?.takeIf { it >= 0 } ?: index
                shortcutController?.playAtIndex(idx)
                StateHelper.isPlaylistChangePending = false
            },
        )
        showPlayerPage()
    }

    fun showPlayerPage() {
        mainPagerFragment?.showPlayer()
    }

    fun showStationsPage() {
        mainPagerFragment?.showStations()
    }

    private fun openDiscoverPage() {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, DiscoverFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun maybeShowOnboarding() {
        if (!prefs.getBoolean(Keys.PREF_ONBOARDING_DONE, false)) {
            AlertDialog.Builder(this)
                .setTitle("Setup Assistance")
                .setMessage("Would you like help with setup? You can improve metadata using the Spotify API (images and info).")
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    AlertDialog.Builder(this)
                        .setTitle("Add Stations")
                        .setMessage("Would you like to add stations now?")
                        .setPositiveButton(android.R.string.yes) { _, _ ->
                            openDiscoverPage()
                            prefs.edit().putBoolean(Keys.PREF_ONBOARDING_DONE, true).apply()
                        }
                        .setNegativeButton(android.R.string.no) { _, _ ->
                            prefs.edit().putBoolean(Keys.PREF_ONBOARDING_DONE, true).apply()
                        }
                        .setCancelable(false)
                        .show()
                }
                .setNegativeButton(android.R.string.no) { _, _ ->
                    prefs.edit().putBoolean(Keys.PREF_ONBOARDING_DONE, true).apply()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun applyOrientationPreference() {
        val mode = ScreenOrientationMode.fromName(
            prefs.getString(Keys.PREF_SCREEN_ORIENTATION, ScreenOrientationMode.AUTO.name)
        )
        requestedOrientation = when (mode) {
            ScreenOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ScreenOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ScreenOrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Keys.PREF_SCREEN_ORIENTATION) {
            applyOrientationPreference()
        }
    }
}
