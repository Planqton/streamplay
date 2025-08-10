package at.plankt0n.streamplay.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.data.CoverMode
import at.plankt0n.streamplay.helper.LiveCoverHelper
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.StationImportHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Possible categories a preference can belong to. */
enum class SettingsCategory { PLAYBACK, UI, METAINFO, SPOTIFY_META, PERSONAL_SYNC, ABOUT }

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

    val exportJsonLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let {
                lifecycleScope.launch {
                    val json = Gson().toJson(PreferencesHelper.getStations(context))
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { output ->
                            output.write(json.toByteArray())
                        }
                    }
                }
            }
        }

    val categoryMap = SettingsCategory.values().associateWith { cat ->
        PreferenceCategory(context).apply {
            title = when (cat) {
                SettingsCategory.PLAYBACK -> getString(R.string.settings_category_playback)
                SettingsCategory.UI -> getString(R.string.settings_category_ui)
                SettingsCategory.METAINFO -> getString(R.string.settings_category_metainfo)
                SettingsCategory.SPOTIFY_META -> getString(R.string.settings_category_spotify_meta)
                SettingsCategory.PERSONAL_SYNC -> getString(R.string.settings_category_personal_sync)
                SettingsCategory.ABOUT -> getString(R.string.settings_category_about)
            }
            icon = when (cat) {
                SettingsCategory.PLAYBACK -> context.getDrawable(R.drawable.ic_button_play)
                SettingsCategory.UI -> context.getDrawable(R.drawable.ic_sheet_settings)
                SettingsCategory.METAINFO -> context.getDrawable(R.drawable.ic_sheet_discover)
                SettingsCategory.SPOTIFY_META -> context.getDrawable(R.drawable.ic_sheet_settings)
                SettingsCategory.PERSONAL_SYNC -> context.getDrawable(R.drawable.ic_sheet_settings)
                SettingsCategory.ABOUT -> context.getDrawable(R.mipmap.ic_launcher)
            }
        }
    }

    val autoplaySwitch = SwitchPreferenceCompat(context).apply {
        key = "autoplay_enabled"
        title = getString(R.string.settings_autoplay)
        setDefaultValue(false)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_autoplay)
    }

    val minimizeSwitch = SwitchPreferenceCompat(context).apply {
        key = "minimize_after_autoplay"
        title = getString(R.string.settings_minimize)
        setDefaultValue(false)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_pip)
    }

    val delayPreference = SeekBarPreference(context).apply {
        key = "autoplay_delay"
        title = getString(R.string.settings_delay)
        min = 0
        max = 30
        showSeekBarValue = true
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_timer)
    }

    val bannerSwitch = SwitchPreferenceCompat(context).apply {
        key = "show_exoplayer_banner"
        title = getString(R.string.settings_exoplayer_infobanner)
        setDefaultValue(true)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_autoplay)
    }

    val backgroundEffectPref = ListPreference(context).apply {
        key = "background_effect"
        title = getString(R.string.settings_background_effect)
        entries = arrayOf(
            getString(R.string.bg_effect_fade),
            getString(R.string.bg_effect_aqua),
            getString(R.string.bg_effect_radial),
            getString(R.string.bg_effect_sunset),
            getString(R.string.bg_effect_forest),
            getString(R.string.bg_effect_diagonal),
            getString(R.string.bg_effect_spotlight),
            getString(R.string.bg_effect_blur)
        )
        entryValues = arrayOf(
            LiveCoverHelper.BackgroundEffect.FADE.name,
            LiveCoverHelper.BackgroundEffect.AQUA.name,
            LiveCoverHelper.BackgroundEffect.RADIAL.name,
            LiveCoverHelper.BackgroundEffect.SUNSET.name,
            LiveCoverHelper.BackgroundEffect.FOREST.name,
            LiveCoverHelper.BackgroundEffect.DIAGONAL.name,
            LiveCoverHelper.BackgroundEffect.SPOTLIGHT.name,
            LiveCoverHelper.BackgroundEffect.BLUR.name
        )
        setDefaultValue(LiveCoverHelper.BackgroundEffect.FADE.name)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val coverModePref = ListPreference(context).apply {
        key = "cover_mode"
        title = getString(R.string.settings_cover_mode)
        entries = arrayOf(
            getString(R.string.cover_mode_station),
            getString(R.string.cover_mode_meta)
        )
        entryValues = arrayOf(
            CoverMode.STATION.name,
            CoverMode.META.name
        )
        setDefaultValue(CoverMode.META.name)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val spotifyApiKeyPref = EditTextPreference(context).apply {
        key = Keys.PREF_SPOTIFY_CLIENT_ID
        title = getString(R.string.settings_spotify_api_key)
        setDefaultValue("")
        summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val value = pref.text
            if (value.isNullOrBlank()) {
                pref.context.getString(R.string.settings_personal_sync_url_empty)
            } else {
                value
            }
        }
        category = SettingsCategory.SPOTIFY_META
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val spotifySecretKeyPref = EditTextPreference(context).apply {
        key = Keys.PREF_SPOTIFY_CLIENT_SECRET
        title = getString(R.string.settings_spotify_secret_key)
        setDefaultValue("")
        summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val value = pref.text
            if (value.isNullOrBlank()) {
                pref.context.getString(R.string.settings_personal_sync_url_empty)
            } else {
                value
            }
        }
        category = SettingsCategory.SPOTIFY_META
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val useSpotifyMetaPref = SwitchPreferenceCompat(context).apply {
        key = Keys.PREF_USE_SPOTIFY_META
        title = getString(R.string.settings_use_spotify_meta)
        setDefaultValue(false)
        isEnabled = false
        category = SettingsCategory.SPOTIFY_META
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    fun updateSpotifyToggle(api: String? = spotifyApiKeyPref.text, secret: String? = spotifySecretKeyPref.text) {
        val hasKeys = !api.isNullOrBlank() && !secret.isNullOrBlank()
        useSpotifyMetaPref.isEnabled = hasKeys
        if (!hasKeys) {
            useSpotifyMetaPref.isChecked = false
        }
    }

    spotifyApiKeyPref.setOnPreferenceChangeListener { _, newValue ->
        val newText = newValue as String
        updateSpotifyToggle(newText, spotifySecretKeyPref.text)
        true
    }

    spotifySecretKeyPref.setOnPreferenceChangeListener { _, newValue ->
        val newText = newValue as String
        updateSpotifyToggle(spotifyApiKeyPref.text, newText)
        true
    }

    updateSpotifyToggle()

    val personalUrlPref = EditTextPreference(context).apply {
        key = "personal_sync_url"
        title = getString(R.string.settings_personal_sync_url)
        setDefaultValue("")
        summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val value = pref.text
            if (value.isNullOrBlank()) {
                pref.context.getString(R.string.settings_personal_sync_url_empty)
            } else {
                value
            }
        }
        category = SettingsCategory.PERSONAL_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val personalSyncPref = Preference(context).apply {
        key = "personal_sync_now"
        title = getString(R.string.settings_sync_personal_json)
        category = SettingsCategory.PERSONAL_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        setOnPreferenceClickListener {
            val url = personalUrlPref.text ?: ""
            if (url.isBlank()) {
                Toast.makeText(context, "URL erforderlich", Toast.LENGTH_SHORT).show()
            } else {
                this@initSettingsScreen.lifecycleScope.launch {
                    try {
                        val result = StationImportHelper.importStationsFromUrl(context, url, true)
                        Toast.makeText(
                            context,
                            "Sync abgeschlossen: ${result.added} neu, ${result.updated} aktualisiert.",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Fehler beim Sync: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            true
        }
    }

    val personalExportPref = Preference(context).apply {
        key = "personal_export_json"
        title = getString(R.string.settings_export_personal_json)
        category = SettingsCategory.PERSONAL_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        setOnPreferenceClickListener {
            exportJsonLauncher.launch("stations.json")
            true
        }
    }

    val versionPref = Preference(context).apply {
        key = "app_version"
        title = getString(R.string.settings_app_version)
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        summary = pkgInfo.versionName
        category = SettingsCategory.ABOUT
        icon = context.getDrawable(R.mipmap.ic_launcher)
    }

    val updatePref = Preference(context).apply {
        key = "check_updates"
        title = getString(R.string.settings_check_updates)
        val updateAvailable = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(Keys.PREF_UPDATE_AVAILABLE, false)
        summary = if (updateAvailable) {
            val text = context.getString(R.string.update_available_title)
            val color = context.getColor(R.color.update_available_orange)
            SpannableString(text).apply {
                setSpan(
                    ForegroundColorSpan(color),
                    0,
                    text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else null
        category = SettingsCategory.ABOUT
        icon = context.getDrawable(R.drawable.ic_autoplay)
    }

    val addTestPref = Preference(context).apply {
        key = "add_test_stations"
        title = getString(R.string.settings_add_test_stations)
        category = SettingsCategory.ABOUT
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        setOnPreferenceClickListener {
            this@initSettingsScreen.lifecycleScope.launch {
                try {
                    val result = StationImportHelper.importStationsFromUrl(
                        context,
                        "https://raw.githubusercontent.com/Planqton/streamplay/main/teststations.json",
                        false
                    )
                    Toast.makeText(
                        context,
                        "Sync abgeschlossen: ${result.added} neu, ${result.updated} aktualisiert.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Fehler beim Sync: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            true
        }
    }

    val preferences = listOf(
        autoplaySwitch,
        minimizeSwitch,
        delayPreference,
        bannerSwitch,
        backgroundEffectPref,
        coverModePref,
        spotifyApiKeyPref,
        spotifySecretKeyPref,
        useSpotifyMetaPref,
        personalUrlPref,
        personalSyncPref,
        personalExportPref,
        versionPref,
        updatePref,
        addTestPref
    )

    SettingsCategory.values().forEach { cat ->
        val catPref = categoryMap[cat]!!
        val catPrefs = preferences.filter { it.category == cat }
        if (catPrefs.isNotEmpty()) {
            screen.addPreference(catPref)
            catPrefs.forEach { catPref.addPreference(it) }
        }
    }

    preferenceScreen = screen
}
