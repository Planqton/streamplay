package at.plankt0n.streamplay.helper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import at.plankt0n.streamplay.BuildConfig
import at.plankt0n.streamplay.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object UpdateHelper {
    private const val UPDATE_URL = "https://fytfiles.printspace.at/update/updateinfo_streamplay.json"

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split('.')
        val localParts = local.split('.')
        val max = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until max) {
            val r = remoteParts.getOrNull(i)?.toIntOrNull() ?: 0
            val l = localParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    suspend fun checkForUpdates(context: Context) = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder().url(UPDATE_URL).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                val body = response.body?.string() ?: return@withContext
                val json = JSONObject(body)
                val latestVersion = json.getString("version")
                val apkUrl = json.getString("apkUrl")
                if (isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
                    val file = downloadApk(client, apkUrl, context)
                    file?.let { installApk(context, it) }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.toast_latest_version, Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun downloadApk(client: OkHttpClient, url: String, context: Context): File? {
        return try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val input = res.body?.byteStream() ?: return null
                val file = File(context.cacheDir, "update.apk")
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
                file
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
        } else {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
