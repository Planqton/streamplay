package at.plankt0n.streamplay.helper

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val dir = File(context.getExternalFilesDir(null), "crashlogs")
            if (!dir.exists()) dir.mkdirs()

            val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "crash_" + date + ".txt")

            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            file.writeText(sw.toString())

            Log.e("CrashHandler", "Crash-Log gespeichert: ${file.absolutePath}")
        } catch (ex: Exception) {
            Log.e("CrashHandler", "Fehler beim Schreiben des Crash-Logs", ex)
        } finally {
            // Forward to default handler or terminate process if no handler exists
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(t, e)
            } else {
                // No default handler - terminate the process manually
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            }
        }
    }
}
