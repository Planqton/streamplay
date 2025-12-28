package at.plankt0n.streamplay.helper

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.FileProvider
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.Keys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

class GitHubUpdateChecker(private val context: Context) {

    private val client = OkHttpClient()
    private val apiUrl = "https://api.github.com/repos/Planqton/streamplay/releases/latest"

    suspend fun checkForUpdate(force: Boolean = false) {
        val progress = withContext(Dispatchers.Main) {
            val bar = ProgressBar(context)
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.update_checking))
                .setView(bar)
                .setCancelable(false)
                .show()
        }

        try {
            val request = Request.Builder().url(apiUrl).build()
            val (bodyStr, responseCode) = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    response.body?.string() to response.code
                }
            }
            if (bodyStr.isNullOrEmpty()) {
                throw Exception("HTTP $responseCode")
            }
            val json = JSONObject(bodyStr)
            val remoteVersion = json.optString("tag_name", "").removePrefix("v")
            if (remoteVersion.isBlank()) throw Exception("No tag_name")
            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val obj = assets.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = obj.optString("browser_download_url", null)
                        break
                    }
                }
            }
            val localVersion = try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                info.versionName ?: "0"
            } catch (_: Exception) {
                "0"
            }
            val newer = isNewerVersion(remoteVersion, localVersion)
            context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(Keys.PREF_UPDATE_AVAILABLE, newer).apply()
            withContext(Dispatchers.Main) { progress.dismiss() }
            if ((force || newer) && !apkUrl.isNullOrEmpty()) {
                if (force) {
                    downloadAndInstall(apkUrl!!)
                } else {
                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(context)
                            .setTitle(context.getString(R.string.update_available_title))
                            .setMessage(context.getString(R.string.update_available_message, remoteVersion))
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                CoroutineScope(Dispatchers.Main).launch { downloadAndInstall(apkUrl!!) }
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
            } else if (!force) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.update_latest), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                progress.dismiss()
                Toast.makeText(context, context.getString(R.string.update_check_fail), Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun forceUpdate() {
        checkForUpdate(true)
    }

    suspend fun silentCheckForUpdate(): Boolean {
        return try {
            val request = Request.Builder().url(apiUrl).build()
            val bodyStr = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            }
            if (bodyStr.isNullOrEmpty()) return false
            val json = JSONObject(bodyStr)
            val remoteVersion = json.optString("tag_name", "").removePrefix("v")
            if (remoteVersion.isBlank()) return false
            val localVersion = try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                info.versionName ?: "0"
            } catch (_: Exception) {
                "0"
            }
            val newer = isNewerVersion(remoteVersion, localVersion)
            context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(Keys.PREF_UPDATE_AVAILABLE, newer).apply()
            newer
        } catch (_: Exception) {
            false
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val r = remote.split('.')
        val l = local.split('.')
        val max = maxOf(r.size, l.size)
        for (i in 0 until max) {
            val rv = r.getOrNull(i)?.toIntOrNull() ?: 0
            val lv = l.getOrNull(i)?.toIntOrNull() ?: 0
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    private suspend fun downloadAndInstall(url: String) {
        val (dialog, bar) = withContext(Dispatchers.Main) {
            val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
            val dlg = AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.update_downloading))
                .setView(progress)
                .setCancelable(false)
                .create()
            dlg.show()
            dlg to progress
        }

        try {
            val request = Request.Builder().url(url).build()
            val file = File(context.getExternalFilesDir(null), "update.apk")
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    val body = response.body ?: throw Exception("no body")
                    val total = body.contentLength()
                    var read = 0L
                    body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytes = input.read(buffer)
                            while (bytes >= 0) {
                                if (bytes > 0) {
                                    output.write(buffer, 0, bytes)
                                    read += bytes
                                    if (total > 0) {
                                        val progressValue = (read * 100 / total).toInt()
                                        withContext(Dispatchers.Main) { bar.progress = progressValue }
                                    }
                                }
                                bytes = input.read(buffer)
                            }
                        }
                    }
                }
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            withContext(Dispatchers.Main) {
                dialog.dismiss()
                context.startActivity(intent)
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                dialog.dismiss()
                Toast.makeText(context, context.getString(R.string.update_download_fail), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

