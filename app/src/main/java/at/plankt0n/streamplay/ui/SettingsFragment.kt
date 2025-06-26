package at.plankt0n.streamplay.ui

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.core.content.FileProvider
import android.content.Intent
import java.io.File

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = Keys.PREFS_NAME
        initSettingsScreen()

        findPreference<Preference>("check_updates")?.setOnPreferenceClickListener {
            lifecycleScope.launch { checkForUpdates() }
            true
        }
    }

    private suspend fun checkForUpdates() {
        val progress = withContext(Dispatchers.Main) {
            val bar = ProgressBar(requireContext())
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.update_checking))
                .setView(bar)
                .setCancelable(false)
                .show()
        }

        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://fytfiles.printspace.at/update/updateinfo_streamplay.json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("http ${'$'}{response.code}")
                    val json = JSONObject(response.body?.string() ?: "")
                    val remoteVersion = json.getString("version")
                    val apkUrl = json.getString("apkUrl")
                    val pkgInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
                    val localVersion = pkgInfo.versionName ?: ""
                    withContext(Dispatchers.Main) { progress.dismiss() }
                    if (isNewerVersion(remoteVersion, localVersion)) {
                        withContext(Dispatchers.Main) {
                            android.app.AlertDialog.Builder(requireContext())
                                .setTitle(getString(R.string.update_available_title))
                                .setMessage(getString(R.string.update_available_message, remoteVersion))
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    lifecycleScope.launch { downloadAndInstall(apkUrl) }
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), getString(R.string.update_latest), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    progress.dismiss()
                    Toast.makeText(requireContext(), getString(R.string.update_check_fail), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(r.size, l.size)
        for (i in 0 until maxLen) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    private suspend fun downloadAndInstall(url: String) {
        val (dialog, bar) = withContext(Dispatchers.Main) {
            val progress = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
            val dlg = android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.update_downloading))
                .setView(progress)
                .setCancelable(false)
                .create()
            dlg.show()
            dlg to progress
        }

        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val file = File(requireContext().getExternalFilesDir(null), "update.apk")
                client.newCall(request).execute().use { resp ->
                    val body = resp.body ?: throw Exception("no body")
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
                                        val progress = (read * 100 / total).toInt()
                                        withContext(Dispatchers.Main) { bar.progress = progress }
                                    }
                                }
                                bytes = input.read(buffer)
                            }
                        }
                    }
                }

                val uri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".provider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    startActivity(intent)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), getString(R.string.update_download_fail), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
