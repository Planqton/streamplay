package at.plankt0n.streamplay

import android.app.Application
import at.plankt0n.streamplay.helper.CrashHandler

class StreamPlayApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
    }
}
