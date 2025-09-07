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
import android.widget.Toast
import android.widget.EditText
import android.text.InputType
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
import at.plankt0n.streamplay.helper.CouchDbHelper
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
            prefs.getBoolean(Keys.PREF_AUTOSYNC_COUCHDB_STARTUP, false) -> {
                val endpoint = prefs.getString(Keys.PREF_COUCHDB_ENDPOINT, "") ?: ""
                val showLogs = prefs.getBoolean(Keys.PREF_COUCHDB_SHOW_LOGS, true)
                if (endpoint.isNotBlank()) {
                    val user = prefs.getString(Keys.PREF_COUCHDB_USERNAME, "") ?: ""
                    val pass = prefs.getString(Keys.PREF_COUCHDB_PASSWORD, "") ?: ""
                    Log.d("COUCHDB AUTO SYNC>", "Starting auto sync")
                    runBlocking {
                        try {
                            CouchDbHelper.syncPrefs(this@MainActivity, endpoint, user, pass)
                            if (showLogs) {
                                Toast.makeText(
                                    this@MainActivity,
                                    R.string.couchdb_sync_success,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            Log.d("COUCHDB AUTO SYNC>", "Auto sync completed")
                        } catch (e: Exception) {
                            if (showLogs) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.couchdb_sync_failed, e.message ?: ""),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            Log.e("COUCHDB AUTO SYNC>", "Auto sync failed: ${e.message}")
                        }
                    }
                } else {
                    if (showLogs) {
                        Toast.makeText(this, R.string.couchdb_endpoint_required, Toast.LENGTH_LONG).show()
                    }
                    Log.d("COUCHDB AUTO SYNC>", "No endpoint configured")
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
        if (prefs.getBoolean(Keys.PREF_ONBOARDING_DONE, false)) return

        AlertDialog.Builder(this)
            .setTitle("Onboarding")
            .setMessage("Do you want help with setup?")
            .setPositiveButton(android.R.string.yes) { _, _ ->
                askCouchDbDocument()
            }
            .setNegativeButton(android.R.string.no) { _, _ ->
                finishOnboarding()
            }
            .setCancelable(false)
            .show()
    }

    private fun askCouchDbDocument() {
        AlertDialog.Builder(this)
            .setTitle("CouchDB")
            .setMessage("Is there an existing document on CouchDB?")
            .setPositiveButton(android.R.string.yes) { _, _ ->
                askCouchDbConfig()
            }
            .setNegativeButton(android.R.string.no) { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("CouchDB")
                    .setMessage("Set up a CouchDB (Docker or otherwise). Define an endpoint and configure it here.")
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        askCouchDbConfig()
                    }
                    .setCancelable(false)
                    .show()
            }
            .setCancelable(false)
            .show()
    }

    private fun askCouchDbConfig() {
        askForInput("CouchDB endpoint") { endpoint ->
            askForInput("CouchDB user") { user ->
                askForInput("CouchDB password", true) { pass ->
                    prefs.edit()
                        .putString(Keys.PREF_COUCHDB_ENDPOINT, endpoint)
                        .putString(Keys.PREF_COUCHDB_USERNAME, user)
                        .putString(Keys.PREF_COUCHDB_PASSWORD, pass)
                        .apply()
                    askSpotifyIfNeeded()
                }
            }
        }
    }

    private fun askSpotifyIfNeeded() {
        val api = prefs.getString(Keys.PREF_SPOTIFY_CLIENT_ID, "") ?: ""
        val secret = prefs.getString(Keys.PREF_SPOTIFY_CLIENT_SECRET, "") ?: ""
        if (api.isNotBlank() && secret.isNotBlank()) {
            finishOnboarding()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Spotify")
                .setMessage("Configure Spotify?")
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    askForInput("Spotify API key") { apiKey ->
                        askForInput("Spotify secret key", true) { secretKey ->
                            prefs.edit()
                                .putString(Keys.PREF_SPOTIFY_CLIENT_ID, apiKey)
                                .putString(Keys.PREF_SPOTIFY_CLIENT_SECRET, secretKey)
                                .apply()
                            finishOnboarding()
                        }
                    }
                }
                .setNegativeButton(android.R.string.no) { _, _ ->
                    finishOnboarding()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun askForInput(title: String, isPassword: Boolean = false, callback: (String) -> Unit) {
        val input = EditText(this)
        if (isPassword) {
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            input.inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                callback(input.text.toString())
            }
            .setCancelable(false)
            .show()
    }

    private fun finishOnboarding() {
        prefs.edit().putBoolean(Keys.PREF_ONBOARDING_DONE, true).apply()
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
            return
        }
        if (!CouchDbHelper.isApplyingPrefs) {
            PreferencesHelper.maybePushCouchDb(this)
        }
    }
}
