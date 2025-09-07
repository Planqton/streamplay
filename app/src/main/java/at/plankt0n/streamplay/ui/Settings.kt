package at.plankt0n.streamplay.ui

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import at.plankt0n.streamplay.AudioFocusMode
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.ScreenOrientationMode
import at.plankt0n.streamplay.data.CoverAnimationStyle
import at.plankt0n.streamplay.data.CoverMode
import at.plankt0n.streamplay.helper.LiveCoverHelper
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.StationImportHelper
import at.plankt0n.streamplay.helper.CouchDbHelper
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Possible categories a preference can belong to. */
enum class SettingsCategory { PLAYER, PLAYBACK, UI, METAINFO, SPOTIFY_META, PERSONAL_SYNC, ABOUT }

private const val EXTRA_CATEGORY = "category"

/** Convenience property to assign a category to a [Preference]. */
var Preference.category: SettingsCategory?
    get() = extras.getString(EXTRA_CATEGORY)?.let { SettingsCategory.valueOf(it) }
    set(value) {
        extras.putString(EXTRA_CATEGORY, value?.name)
    }

fun PreferenceFragmentCompat.updateSpotifyToggle(
    api: String? = findPreference<EditTextPreference>(Keys.PREF_SPOTIFY_CLIENT_ID)?.text,
    secret: String? = findPreference<EditTextPreference>(Keys.PREF_SPOTIFY_CLIENT_SECRET)?.text
) {
    val useSpotifyMetaPref =
        findPreference<SwitchPreferenceCompat>(Keys.PREF_USE_SPOTIFY_META)
    val hasKeys = !api.isNullOrBlank() && !secret.isNullOrBlank()
    useSpotifyMetaPref?.isEnabled = hasKeys
    if (!hasKeys) {
        useSpotifyMetaPref?.isChecked = false
    }
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

    val exportSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let {
                lifecycleScope.launch {
                    val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
                    val json = Gson().toJson(prefs.all)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { output ->
                            output.write(json.toByteArray())
                        }
                    }
                    Toast.makeText(context, R.string.settings_export_success, Toast.LENGTH_SHORT).show()
                }
            }
        }

    val importSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                lifecycleScope.launch {
                    try {
                        val json = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
                        }
                        json?.let { data ->
                            val obj = JsonParser.parseString(data).asJsonObject
                            val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
                            val editor = prefs.edit().clear()
                            for ((key, value) in obj.entrySet()) {
                                when {
                                    value.isJsonPrimitive -> {
                                        val prim = value.asJsonPrimitive
                                        when {
                                            prim.isBoolean -> editor.putBoolean(key, prim.asBoolean)
                                            prim.isNumber -> {
                                                val num = prim.asNumber
                                                if (num.toString().contains('.')) {
                                                    editor.putFloat(key, num.toFloat())
                                                } else {
                                                    editor.putLong(key, num.toLong())
                                                }
                                            }
                                            prim.isString -> editor.putString(key, prim.asString)
                                        }
                                    }
                                    value.isJsonArray -> {
                                        val type = object : TypeToken<Set<String>>() {}.type
                                        val set: Set<String> = Gson().fromJson(value, type)
                                        editor.putStringSet(key, set)
                                    }
                                }
                            }
                            editor.apply()
                            Toast.makeText(context, R.string.settings_import_success, Toast.LENGTH_SHORT).show()
                            val restartIntent = context.packageManager
                                .getLaunchIntentForPackage(context.packageName)?.apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                            restartIntent?.let {
                                context.startActivity(it)
                                Runtime.getRuntime().exit(0)
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_import_failed, e.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    val categoryMap = SettingsCategory.values().associateWith { cat ->
        PreferenceCategory(context).apply {
            title = when (cat) {
                SettingsCategory.PLAYER -> getString(R.string.settings_category_player)
                SettingsCategory.PLAYBACK -> getString(R.string.settings_category_playback)
                SettingsCategory.UI -> getString(R.string.settings_category_ui)
                SettingsCategory.METAINFO -> getString(R.string.settings_category_metainfo)
                SettingsCategory.SPOTIFY_META -> getString(R.string.settings_category_spotify_meta)
                SettingsCategory.PERSONAL_SYNC -> getString(R.string.settings_category_personal_sync)
                SettingsCategory.ABOUT -> getString(R.string.settings_category_about)
            }
            icon = when (cat) {
                SettingsCategory.PLAYER -> context.getDrawable(R.drawable.ic_button_play)
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

    val audioFocusPref = ListPreference(context).apply {
        key = Keys.PREF_AUDIO_FOCUS_MODE
        title = getString(R.string.settings_audiofocus)
        entries = arrayOf(
            getString(R.string.settings_audiofocus_stop),
            getString(R.string.settings_audiofocus_hold),
            getString(R.string.settings_audiofocus_lower)
        )
        entryValues = arrayOf(
            AudioFocusMode.STOP.name,
            AudioFocusMode.HOLD.name,
            AudioFocusMode.LOWER.name
        )
        setDefaultValue(AudioFocusMode.STOP.name)
        category = SettingsCategory.PLAYER
        icon = context.getDrawable(R.drawable.ic_button_play)
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
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

    val orientationPref = ListPreference(context).apply {
        key = Keys.PREF_SCREEN_ORIENTATION
        title = getString(R.string.settings_rotation)
        entries = arrayOf(
            getString(R.string.orientation_auto),
            getString(R.string.orientation_landscape),
            getString(R.string.orientation_portrait)
        )
        entryValues = arrayOf(
            ScreenOrientationMode.AUTO.name,
            ScreenOrientationMode.LANDSCAPE.name,
            ScreenOrientationMode.PORTRAIT.name
        )
        setDefaultValue(ScreenOrientationMode.AUTO.name)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
    }

    val bannerSwitch = SwitchPreferenceCompat(context).apply {
        key = "show_exoplayer_banner"
        title = getString(R.string.settings_exoplayer_infobanner)
        setDefaultValue(true)
        category = SettingsCategory.PLAYER
        icon = context.getDrawable(R.drawable.ic_autoplay)
    }

    val resumeLiveSwitch = SwitchPreferenceCompat(context).apply {
        key = Keys.PREF_RESUME_LIVE_AFTER_PAUSE
        title = getString(R.string.settings_resume_live_after_pause)
        setDefaultValue(true)
        category = SettingsCategory.PLAYER
        icon = context.getDrawable(R.drawable.ic_button_play)
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
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
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
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
    }

    val coverAnimationStylePref = ListPreference(context).apply {
        key = Keys.PREF_COVER_ANIMATION_STYLE
        title = getString(R.string.settings_cover_animation)
        entries = arrayOf(
            getString(R.string.cover_animation_none),
            getString(R.string.cover_animation_flip),
            getString(R.string.cover_animation_fade)
        )
        entryValues = arrayOf(
            CoverAnimationStyle.NONE.name,
            CoverAnimationStyle.FLIP.name,
            CoverAnimationStyle.FADE.name
        )
        setDefaultValue(CoverAnimationStyle.FLIP.name)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
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

    val defaultPersonalUrl = Keys.DEFAULT_PERSONAL_SYNC_URL
    val startPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    if (!startPrefs.contains(Keys.PREF_PERSONAL_SYNC_URL)) {
        startPrefs.edit().putString(Keys.PREF_PERSONAL_SYNC_URL, defaultPersonalUrl).apply()
    }

    val personalUrlPref = EditTextPreference(context).apply {
        key = Keys.PREF_PERSONAL_SYNC_URL
        title = getString(R.string.settings_personal_sync_url)
        setDefaultValue(defaultPersonalUrl)
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

    val autoSyncPref = SwitchPreferenceCompat(context).apply {
        key = Keys.PREF_AUTOSYNC_JSON_STARTUP
        title = getString(R.string.settings_autosync_json_startup)
        setDefaultValue(false)
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

    val couchEndpointPref = EditTextPreference(context).apply {
        key = Keys.PREF_COUCHDB_ENDPOINT
        title = getString(R.string.settings_couchdb_endpoint)
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

    val couchUserPref = EditTextPreference(context).apply {
        key = Keys.PREF_COUCHDB_USERNAME
        title = getString(R.string.settings_couchdb_username)
        category = SettingsCategory.PERSONAL_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val couchPasswordPref = EditTextPreference(context).apply {
        key = Keys.PREF_COUCHDB_PASSWORD
        title = getString(R.string.settings_couchdb_password)
        category = SettingsCategory.PERSONAL_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val couchShowLogsPref = SwitchPreferenceCompat(context).apply {
        key = Keys.PREF_COUCHDB_SHOW_LOGS
        title = getString(R.string.settings_couchdb_show_logs)
        setDefaultValue(true)
        category = SettingsCategory.PERSONAL_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val couchAutoSyncPref = SwitchPreferenceCompat(context).apply {
        key = Keys.PREF_AUTOSYNC_COUCHDB_STARTUP
        title = getString(R.string.settings_autosync_couchdb_startup)
        setDefaultValue(false)
        category = SettingsCategory.PERSONAL_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val couchPushPref = Preference(context).apply {
        key = "couchdb_push"
        title = getString(R.string.settings_couchdb_push)
        category = SettingsCategory.PERSONAL_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        setOnPreferenceClickListener {
            val endpoint = couchEndpointPref.text ?: ""
            if (endpoint.isNotBlank()) {
                val user = couchUserPref.text ?: ""
                val pass = couchPasswordPref.text ?: ""
                this@initSettingsScreen.lifecycleScope.launch {
                    try {
                        CouchDbHelper.pushStations(context, endpoint, user, pass)
                        if (couchShowLogsPref.isChecked) {
                            Toast.makeText(context, R.string.couchdb_push_success, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        if (couchShowLogsPref.isChecked) {
                            Toast.makeText(
                                context,
                                getString(R.string.couchdb_push_failed, e.message ?: ""),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else {
                if (couchShowLogsPref.isChecked) {
                    Toast.makeText(context, R.string.couchdb_endpoint_required, Toast.LENGTH_LONG).show()
                }
            }
            true
        }
    }

    val couchReadPref = Preference(context).apply {
        key = "couchdb_read"
        title = getString(R.string.settings_couchdb_read)
        category = SettingsCategory.PERSONAL_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        setOnPreferenceClickListener {
            val endpoint = couchEndpointPref.text ?: ""
            if (endpoint.isNotBlank()) {
                val user = couchUserPref.text ?: ""
                val pass = couchPasswordPref.text ?: ""
                this@initSettingsScreen.lifecycleScope.launch {
                    try {
                        CouchDbHelper.readStations(context, endpoint, user, pass)
                        if (couchShowLogsPref.isChecked) {
                            Toast.makeText(context, R.string.couchdb_read_success, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        if (couchShowLogsPref.isChecked) {
                            Toast.makeText(
                                context,
                                getString(R.string.couchdb_read_failed, e.message ?: ""),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else {
                if (couchShowLogsPref.isChecked) {
                    Toast.makeText(context, R.string.couchdb_endpoint_required, Toast.LENGTH_LONG).show()
                }
            }
            true
        }
    }

    fun updateSyncPreferenceStates() {
        val couchEnabled = couchAutoSyncPref.isChecked
        val jsonEnabled = autoSyncPref.isChecked
        personalUrlPref.isEnabled = !couchEnabled
        personalSyncPref.isEnabled = !couchEnabled
        personalExportPref.isEnabled = !couchEnabled
        autoSyncPref.isEnabled = !couchEnabled
        couchAutoSyncPref.isEnabled = !jsonEnabled
    }

    couchAutoSyncPref.setOnPreferenceChangeListener { _, newValue ->
        val enabled = newValue as Boolean
        if (enabled) {
            autoSyncPref.isChecked = false
            val endpoint = couchEndpointPref.text ?: ""
            if (endpoint.isNotBlank()) {
                val user = couchUserPref.text ?: ""
                val pass = couchPasswordPref.text ?: ""
                lifecycleScope.launch {
                    try {
                        CouchDbHelper.syncStations(context, endpoint, user, pass)
                        if (couchShowLogsPref.isChecked) {
                            Toast.makeText(context, R.string.couchdb_sync_success, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        if (couchShowLogsPref.isChecked) {
                            Toast.makeText(
                                context,
                                getString(R.string.couchdb_sync_failed, e.message ?: ""),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        couchAutoSyncPref.isChecked = false
                    }
                }
            } else {
                if (couchShowLogsPref.isChecked) {
                    Toast.makeText(context, R.string.couchdb_endpoint_required, Toast.LENGTH_LONG).show()
                }
                return@setOnPreferenceChangeListener false
            }
        }
        updateSyncPreferenceStates()
        true
    }

    autoSyncPref.setOnPreferenceChangeListener { _, newValue ->
        val enabled = newValue as Boolean
        if (enabled) {
            couchAutoSyncPref.isChecked = false
        }
        updateSyncPreferenceStates()
        true
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

    val settingsTransferPref = Preference(context).apply {
        key = "import_export_settings"
        title = getString(R.string.settings_import_export)
        category = SettingsCategory.ABOUT
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        setOnPreferenceClickListener {
            AlertDialog.Builder(context)
                .setTitle(getString(R.string.settings_import_export))
                .setItems(
                    arrayOf(
                        getString(R.string.settings_export_settings),
                        getString(R.string.settings_import_settings)
                    )
                ) { _, which ->
                    when (which) {
                        0 -> exportSettingsLauncher.launch("settings.json")
                        1 -> importSettingsLauncher.launch(arrayOf("application/json"))
                    }
                }
                .show()
            true
        }
    }

    val preferences = listOf(
        audioFocusPref,
        autoplaySwitch,
        minimizeSwitch,
        delayPreference,
        orientationPref,
        bannerSwitch,
        resumeLiveSwitch,
        backgroundEffectPref,
        coverModePref,
        coverAnimationStylePref,
        spotifyApiKeyPref,
        spotifySecretKeyPref,
        useSpotifyMetaPref,
        personalUrlPref,
        autoSyncPref,
        personalSyncPref,
        personalExportPref,
        couchEndpointPref,
        couchUserPref,
        couchPasswordPref,
        couchShowLogsPref,
        couchAutoSyncPref,
        couchPushPref,
        couchReadPref,
        versionPref,
        updatePref,
        addTestPref,
        settingsTransferPref
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

    updateSpotifyToggle()
    updateSyncPreferenceStates()
}
