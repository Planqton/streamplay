package at.plankt0n.streamplay

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import at.plankt0n.streamplay.helper.StreamplayApiHelper
import at.plankt0n.streamplay.helper.LayoutLogger
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.ScreenOrientationMode
import at.plankt0n.streamplay.ui.MainPagerFragment
import at.plankt0n.streamplay.ui.DiscoverFragment
import kotlinx.coroutines.launch

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

        // Log initial layout
        logCurrentLayout()

        // API-Sync läuft jetzt in StreamPlayApplication (überlebt Activity-Lifecycle)

        if (BuildConfig.ENABLE_SELF_UPDATE) {
            lifecycleScope.launch {
                GitHubUpdateChecker(this@MainActivity).silentCheckForUpdate()
            }
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

        // BackStack-Listener um MainPagerFragment wieder anzuzeigen
        supportFragmentManager.addOnBackStackChangedListener {
            val mainFragment = supportFragmentManager.findFragmentByTag("mainPager")
            if (mainFragment != null && mainFragment.isHidden) {
                supportFragmentManager.beginTransaction()
                    .show(mainFragment)
                    .commit()
            }
        }

        handleShortcutIntent(intent)
        maybeShowOnboarding()

    }

    override fun onDestroy() {
        shortcutController?.disconnect()
        shortcutController = null
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    private fun apiSyncIfEnabled() {
        val syncEnabled = prefs.getBoolean(Keys.PREF_API_SYNC_ENABLED, false)
        val username = prefs.getString(Keys.PREF_API_USERNAME, "") ?: ""
        val hasPassword = !prefs.getString(Keys.PREF_API_PASSWORD, "").isNullOrEmpty()
        Log.d("API_SYNC", "apiSyncIfEnabled: enabled=$syncEnabled, user=$username, hasPass=$hasPassword")

        if (syncEnabled) {
            Log.d("API_SYNC", "Starting API sync on startup")
            lifecycleScope.launch {
                try {
                    val result = StreamplayApiHelper.readFromProfile(this@MainActivity)
                    when (result) {
                        is StreamplayApiHelper.ApiResult.Success -> {
                            Log.d("API_SYNC", "Sync completed: ${result.data.stations.size} stations")
                        }
                        is StreamplayApiHelper.ApiResult.Error -> {
                            Log.e("API_SYNC", "Sync failed: ${result.message}")
                            Toast.makeText(
                                this@MainActivity,
                                "API Sync fehlgeschlagen: ${result.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("API_SYNC", "Sync exception: ${e.message}", e)
                    Toast.makeText(
                        this@MainActivity,
                        "API Sync Fehler: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            Log.d("API_SYNC", "API sync disabled")
        }
    }

    override fun onResume() {
        super.onResume()
        maybePlayPendingShortcutStation()
        applyOrientationPreference()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)  // Wichtig: Intent speichern damit getIntent() den neuen Intent zurückgibt
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
                // Controller nach erfolgreicher Wiedergabe freigeben
                shortcutController?.disconnect()
                shortcutController = null
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
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .hide(currentFragment!!)
            .add(R.id.fragment_container, DiscoverFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun maybeShowOnboarding() {
        if (prefs.getBoolean(Keys.PREF_ONBOARDING_DONE, false)) return

        AlertDialog.Builder(this)
            .setTitle("Onboarding")
            .setMessage("Do you want help with setup?")
            .setPositiveButton(android.R.string.yes) { _, _ ->
                askApiEndpoint()
            }
            .setNegativeButton(android.R.string.no) { _, _ ->
                finishOnboarding()
            }
            .setCancelable(false)
            .show()
    }

    private fun askApiEndpoint() {
        val currentEndpoint = prefs.getString(Keys.PREF_API_ENDPOINT, "") ?: ""
        askForInput("API Endpoint", false, currentEndpoint.ifBlank { Keys.DEFAULT_API_ENDPOINT }) { endpoint ->
            val finalEndpoint = endpoint.ifBlank { Keys.DEFAULT_API_ENDPOINT }
            prefs.edit().putString(Keys.PREF_API_ENDPOINT, finalEndpoint).apply()
            askApiUsername()
        }
    }

    private fun askApiUsername() {
        val currentUsername = prefs.getString(Keys.PREF_API_USERNAME, "") ?: ""
        askForInput("Benutzername", false, currentUsername) { username ->
            prefs.edit().putString(Keys.PREF_API_USERNAME, username).apply()
            askApiPassword()
        }
    }

    private fun askApiPassword() {
        askForInput("Passwort", true) { password ->
            prefs.edit()
                .putString(Keys.PREF_API_PASSWORD, password)
                .putBoolean(Keys.PREF_API_SYNC_ENABLED, true)
                .apply()
            finishOnboarding()
        }
    }

    private fun askForInput(title: String, isPassword: Boolean = false, defaultValue: String = "", callback: (String) -> Unit) {
        val input = EditText(this)
        if (isPassword) {
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            input.inputType = InputType.TYPE_CLASS_TEXT
        }
        input.setText(defaultValue)
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
        // Nach dem Onboarding: Stationen von der API laden (nicht pushen!)
        if (prefs.getBoolean(Keys.PREF_API_SYNC_ENABLED, false)) {
            lifecycleScope.launch {
                val result = StreamplayApiHelper.readFromProfile(this@MainActivity)
                if (result is StreamplayApiHelper.ApiResult.Error) {
                    Toast.makeText(
                        this@MainActivity,
                        "API Sync fehlgeschlagen: ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
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
        // Exclude certain keys from triggering sync
        val excludedKeys = setOf(
            Keys.PREF_API_ENDPOINT,
            Keys.PREF_API_USERNAME,
            Keys.PREF_API_PASSWORD,
            Keys.PREF_API_TOKEN,
            Keys.PREF_API_SYNC_ENABLED,
            Keys.PREF_SCREEN_ORIENTATION,
            Keys.PREF_ONBOARDING_DONE
        )
        if (key != null && key !in excludedKeys) {
            lifecycleScope.launch {
                StreamplayApiHelper.pushIfSyncEnabled(this@MainActivity)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        logCurrentLayout()
    }

    private var lastLoggedConfig: String? = null

    private fun logCurrentLayout() {
        val config = resources.configuration
        val orientationStr = when (config.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "LANDSCAPE"
            Configuration.ORIENTATION_PORTRAIT -> "PORTRAIT"
            else -> "UNDEFINED"
        }

        val sw = config.smallestScreenWidthDp
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val layoutFolder = when {
            isLandscape && sw >= 800 -> "layout-sw800dp-land"
            isLandscape && sw >= 600 -> "layout-sw600dp-land"
            isLandscape && sw >= 384 -> "layout-sw384dp-land"
            isLandscape -> "layout-land"
            sw >= 800 -> "layout-sw800dp"
            sw >= 600 -> "layout-sw600dp"
            sw >= 384 -> "layout-sw384dp"
            else -> "layout"
        }

        val currentConfig = "$orientationStr|$sw|${config.screenWidthDp}x${config.screenHeightDp}"

        // Only log when config actually changed
        if (currentConfig != lastLoggedConfig) {
            lastLoggedConfig = currentConfig
            Log.i("LayoutLogger", "")
            Log.i("LayoutLogger", "╔══════════════════════════════════════════╗")
            Log.i("LayoutLogger", "║         📐 LAYOUT WECHSEL                ║")
            Log.i("LayoutLogger", "╠══════════════════════════════════════════╣")
            Log.i("LayoutLogger", "║  Folder:      $layoutFolder")
            Log.i("LayoutLogger", "║  Orientation: $orientationStr")
            Log.i("LayoutLogger", "║  SW:          ${sw}dp")
            Log.i("LayoutLogger", "║  Screen:      ${config.screenWidthDp}dp x ${config.screenHeightDp}dp")
            Log.i("LayoutLogger", "╚══════════════════════════════════════════╝")
            Log.i("LayoutLogger", "")
        }
    }
}
