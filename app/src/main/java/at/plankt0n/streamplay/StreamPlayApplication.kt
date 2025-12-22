package at.plankt0n.streamplay

import android.app.Application
import android.content.Context
import android.util.Log
import at.plankt0n.streamplay.helper.CrashHandler

class StreamPlayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        migratePreferences()
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
