package at.plankt0n.streamplay

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import at.plankt0n.streamplay.helper.CrashHandler
import at.plankt0n.streamplay.helper.StreamplayApiHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StreamPlayApplication : Application() {

    // Application-weiter CoroutineScope - überlebt Activity-Lifecycle
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        migratePreferences()
        applyStoredLanguage()
        apiSyncOnStartup()
    }

    private fun apiSyncOnStartup() {
        val prefs = getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        val syncEnabled = prefs.getBoolean(Keys.PREF_API_SYNC_ENABLED, false)
        val username = prefs.getString(Keys.PREF_API_USERNAME, "") ?: ""
        val hasPassword = !prefs.getString(Keys.PREF_API_PASSWORD, "").isNullOrEmpty()
        Log.d("API_SYNC", "Application: apiSyncOnStartup: enabled=$syncEnabled, user=$username, hasPass=$hasPassword")

        if (syncEnabled) {
            Log.d("API_SYNC", "Application: Starting API sync on startup")
            applicationScope.launch {
                try {
                    val result = StreamplayApiHelper.readFromProfile(this@StreamPlayApplication)
                    when (result) {
                        is StreamplayApiHelper.ApiResult.Success -> {
                            Log.d("API_SYNC", "Application: Sync completed: ${result.data.stations.size} stations")
                        }
                        is StreamplayApiHelper.ApiResult.Error -> {
                            Log.e("API_SYNC", "Application: Sync failed: ${result.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("API_SYNC", "Application: Sync exception: ${e.message}", e)
                }
            }
        } else {
            Log.d("API_SYNC", "Application: API sync disabled")
        }
    }

    private fun applyStoredLanguage() {
        val prefs = getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        val languageCode = prefs.getString(Keys.PREF_APP_LANGUAGE, "system") ?: "system"
        if (languageCode != "system") {
            val localeList = LocaleListCompat.forLanguageTags(languageCode)
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }

    private fun migratePreferences() {
        val prefs = getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)

        // Migriere autoplay_delay von Float zu Int
        try {
            val floatValue = prefs.getFloat("autoplay_delay", -1f)
            if (floatValue >= 0f) {
                prefs.edit()
                    .remove("autoplay_delay")
                    .putInt("autoplay_delay", floatValue.toInt())
                    .commit()  // Synchron, damit Migration vor Settings-Laden fertig ist
                Log.d("StreamPlayApplication", "Migriert autoplay_delay von Float zu Int: ${floatValue.toInt()}")
            }
        } catch (e: ClassCastException) {
            // Schon ein Int - keine Migration nötig
        }
    }
}
