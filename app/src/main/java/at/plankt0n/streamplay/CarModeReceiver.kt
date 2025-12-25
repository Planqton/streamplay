package at.plankt0n.streamplay

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class CarModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)

        when (intent.action) {
            UiModeManager.ACTION_ENTER_CAR_MODE -> {
                Log.d("CarModeReceiver", "🚗 Car Mode aktiviert")

                val autoplay = prefs.getBoolean(Keys.PREF_AUTO_AUTOPLAY, false)
                val startActivity = prefs.getBoolean(Keys.PREF_AUTO_START_ACTIVITY, false)

                if (autoplay) {
                    Log.d("CarModeReceiver", "🚗 Auto-Autoplay aktiviert - starte Service")

                    val serviceIntent = Intent(context, StreamingService::class.java).apply {
                        action = Keys.ACTION_AUTO_PLAY
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } else {
                    Log.d("CarModeReceiver", "🚗 Auto-Autoplay deaktiviert - keine Aktion")
                }

                if (startActivity) {
                    Log.d("CarModeReceiver", "🚗 Activity starten aktiviert - starte MainActivity")

                    val activityIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(activityIntent)
                }
            }

            UiModeManager.ACTION_EXIT_CAR_MODE -> {
                Log.d("CarModeReceiver", "🚗 Car Mode beendet")

                val stopOnExit = prefs.getBoolean(Keys.PREF_AUTO_STOP_ON_EXIT, false)

                if (stopOnExit) {
                    Log.d("CarModeReceiver", "🚗 Auto-Stop aktiviert - stoppe Wiedergabe")

                    val serviceIntent = Intent(context, StreamingService::class.java).apply {
                        action = Keys.ACTION_AUTO_STOP
                    }
                    context.startService(serviceIntent)
                } else {
                    Log.d("CarModeReceiver", "🚗 Auto-Stop deaktiviert - Wiedergabe läuft weiter")
                }
            }
        }
    }
}
