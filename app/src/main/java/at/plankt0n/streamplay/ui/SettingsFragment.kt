package at.plankt0n.streamplay.ui

import android.os.Bundle
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
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://fytfiles.printspace.at/update/updateinfo_streamplay.json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext
                    val json = JSONObject(response.body?.string() ?: "")
                    val remoteVersion = json.getString("version")
                    val apkUrl = json.getString("apkUrl")
                    val pkgInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
                    val localVersion = pkgInfo.versionName ?: ""
                    if (isNewerVersion(remoteVersion, localVersion)) {
                        downloadAndInstall(apkUrl)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), getString(R.string.update_latest), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (_: Exception) {
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
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val file = File(requireContext().getExternalFilesDir(null), "update.apk")
                client.newCall(request).execute().use { resp ->
                    resp.body?.byteStream()?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                val uri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".provider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                withContext(Dispatchers.Main) {
                    startActivity(intent)
                }
            } catch (_: Exception) {
            }
        }
    }
}
