package at.plankt0n.streamplay

import android.app.Application
import android.util.Log
import androidx.preference.PreferenceManager
import at.plankt0n.streamplay.helper.CrashHandler
import at.plankt0n.streamplay.helper.StationImportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class StreamPlayApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(Keys.PREF_PERSONAL_SYNC_STARTUP, false)) {
            val url = prefs.getString(Keys.PREF_PERSONAL_SYNC_URL, Keys.DEFAULT_PERSONAL_SYNC_URL) ?: ""
            Log.i("StreamPlay", "Syncing personal JSON at startup")
            runBlocking(Dispatchers.IO) {
                try {
                    val result = StationImportHelper.importStationsFromUrl(this@StreamPlayApplication, url, true)
                    Log.i(
                        "StreamPlay",
                        "Startup sync succeeded: ${result.added} added, ${result.updated} updated"
                    )
                } catch (e: Exception) {
                    Log.e("StreamPlay", "Startup sync failed: ${e.message}")
                }
            }
        }

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
    }
}
