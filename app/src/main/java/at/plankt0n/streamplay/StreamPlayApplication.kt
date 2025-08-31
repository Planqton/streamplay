package at.plankt0n.streamplay

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import at.plankt0n.streamplay.helper.CrashHandler
import at.plankt0n.streamplay.helper.StationImportHelper
import kotlinx.coroutines.runBlocking

class StreamPlayApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(Keys.PREF_PERSONAL_SYNC_STARTUP, false)) {
            val url = prefs.getString(Keys.PREF_PERSONAL_SYNC_URL, Keys.DEFAULT_PERSONAL_SYNC_URL) ?: ""
            Log.i("JSONAUTOSYNC", "Syncing personal JSON at startup")
            var refreshNeeded = false
            runBlocking {
                try {
                    val result = StationImportHelper.importStationsFromUrl(
                        this@StreamPlayApplication,
                        url,
                        true,
                        refreshPlaylist = false,
                    )
                    Log.i(
                        "JSONAUTOSYNC",
                        "Startup sync succeeded: ${result.added} added, ${result.updated} updated"
                    )
                    refreshNeeded = true
                } catch (e: Exception) {
                    Log.e("JSONAUTOSYNC", "Startup sync failed: ${e.message}")
                }
            }
            if (refreshNeeded) {
                val intent = Intent(this, StreamingService::class.java).apply {
                    action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
                }
                try {
                    startService(intent)
                } catch (e: Exception) {
                    Log.e("JSONAUTOSYNC", "Failed to refresh playlist: ${e.message}")
                }
            }
        }

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
    }
}
