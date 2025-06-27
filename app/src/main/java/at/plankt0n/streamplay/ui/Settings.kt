package at.plankt0n.streamplay.ui

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import at.plankt0n.streamplay.BuildConfig
import at.plankt0n.streamplay.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Possible categories a preference can belong to. */
enum class SettingsCategory { PLAYBACK, UI, METAINFO, ABOUT }

private const val EXTRA_CATEGORY = "category"

/** Convenience property to assign a category to a [Preference]. */
var Preference.category: SettingsCategory?
    get() = extras.getString(EXTRA_CATEGORY)?.let { SettingsCategory.valueOf(it) }
    set(value) {
        extras.putString(EXTRA_CATEGORY, value?.name)
    }

fun PreferenceFragmentCompat.initSettingsScreen() {
    val context = preferenceManager.context
    val screen = preferenceManager.createPreferenceScreen(context)

    val categoryMap = SettingsCategory.values().associateWith { cat ->
        PreferenceCategory(context).apply {
            title = when (cat) {
                SettingsCategory.PLAYBACK -> getString(R.string.settings_category_playback)
                SettingsCategory.UI -> getString(R.string.settings_category_ui)
                SettingsCategory.METAINFO -> getString(R.string.settings_category_metainfo)
                SettingsCategory.ABOUT -> getString(R.string.settings_category_about)
            }
            icon = when (cat) {
                SettingsCategory.PLAYBACK -> context.getDrawable(R.drawable.ic_button_play)
                SettingsCategory.UI -> context.getDrawable(R.drawable.ic_sheet_settings)
                SettingsCategory.METAINFO -> context.getDrawable(R.drawable.ic_sheet_discover)
                SettingsCategory.ABOUT -> context.getDrawable(R.mipmap.ic_launcher)
            }
        }
    }

    val autoplaySwitch = SwitchPreferenceCompat(context).apply {
        key = "autoplay_enabled"
        title = getString(R.string.settings_autoplay)
        setDefaultValue(false)
        category = SettingsCategory.PLAYBACK
        icon = context.getDrawable(R.drawable.ic_autoplay)
    }

    val delayPreference = SeekBarPreference(context).apply {
        key = "autoplay_delay"
        title = getString(R.string.settings_delay)
        min = 0
        max = 30
        showSeekBarValue = true
        category = SettingsCategory.PLAYBACK
        icon = context.getDrawable(R.drawable.ic_timer)
    }

    val minimizeSwitch = SwitchPreferenceCompat(context).apply {
        key = "minimize_after_autoplay"
        title = getString(R.string.settings_minimize)
        setDefaultValue(false)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_pip)
    }

    val versionPref = Preference(context).apply {
        key = "app_version"
        title = getString(R.string.settings_current_version, BuildConfig.VERSION_NAME)
        icon = context.getDrawable(R.mipmap.ic_launcher)
        isSelectable = false
        category = SettingsCategory.ABOUT
    }

    val updatePref = Preference(context).apply {
        key = "check_update"
        title = getString(R.string.settings_check_update)
        icon = context.getDrawable(R.drawable.ic_autoplay)
        category = SettingsCategory.ABOUT
        setOnPreferenceClickListener {
            checkForUpdates()
            true
        }
    }

    val preferences = listOf(autoplaySwitch, delayPreference, minimizeSwitch, versionPref, updatePref)

    SettingsCategory.values().forEach { cat ->
        val catPref = categoryMap[cat]!!
        screen.addPreference(catPref)
        preferences.filter { it.category == cat }.forEach { catPref.addPreference(it) }
    }

    preferenceScreen = screen
}

private fun isNewerVersion(remote: String, local: String): Boolean {
    val r = remote.split(".")
    val l = local.split(".")
    val max = maxOf(r.size, l.size)
    for (i in 0 until max) {
        val rv = r.getOrNull(i)?.toIntOrNull() ?: 0
        val lv = l.getOrNull(i)?.toIntOrNull() ?: 0
        if (rv > lv) return true
        if (rv < lv) return false
    }
    return false
}

fun PreferenceFragmentCompat.checkForUpdates() {
    Toast.makeText(requireContext(), getString(R.string.checking_for_updates), Toast.LENGTH_SHORT).show()
    viewLifecycleOwner.lifecycleScope.launch {
        val result = withContext(Dispatchers.IO) {
            try {
                val url = URL("https://fytfiles.printspace.at/update/updateinfo_streamplay.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    Pair(json.getString("version"), json.getString("apkUrl"))
                } else null
            } catch (e: Exception) {
                null
            }
        }

        result?.let { (version, apkUrl) ->
            if (isNewerVersion(version, BuildConfig.VERSION_NAME)) {
                AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.update_available_question, version))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        downloadAndInstall(apkUrl)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                Toast.makeText(requireContext(), getString(R.string.no_update_available), Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(requireContext(), getString(R.string.no_update_available), Toast.LENGTH_SHORT).show()
    }
}

private fun PreferenceFragmentCompat.downloadAndInstall(url: String) {
    val context = requireContext()
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        setTitle("StreamPlay Update")
        setDescription(getString(R.string.settings_check_update))
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "streamplay_update.apk")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    }
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val id = dm.enqueue(request)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == id) {
                ctx.unregisterReceiver(this)
                val apkUri = dm.getUriForDownloadedFile(downloadId)
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(install)
            }
        }
    }
    context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
}
