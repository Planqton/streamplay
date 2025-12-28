package at.plankt0n.streamplay.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import at.plankt0n.streamplay.AudioFocusMode
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.NetworkType
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.ScreenOrientationMode
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.data.CoverAnimationStyle
import at.plankt0n.streamplay.data.CoverMode
import at.plankt0n.streamplay.helper.LiveCoverHelper
import at.plankt0n.streamplay.view.VisualizerView
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.StationImportHelper
import at.plankt0n.streamplay.helper.StreamRecordHelper
import at.plankt0n.streamplay.helper.StreamplayApiHelper
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Possible categories a preference can belong to. */
enum class SettingsCategory { PLAYER, PLAYBACK, UI, METAINFO, RECORDING, SPOTIFY_META, API_SYNC, ANDROID_AUTO, ABOUT, DEV }

/** Get the accent color for this category. */
fun SettingsCategory.getAccentColor(context: Context): Int = when (this) {
    SettingsCategory.PLAYER -> context.getColor(R.color.category_player)
    SettingsCategory.PLAYBACK -> context.getColor(R.color.category_playback)
    SettingsCategory.UI -> context.getColor(R.color.category_ui)
    SettingsCategory.METAINFO -> context.getColor(R.color.category_metainfo)
    SettingsCategory.RECORDING -> context.getColor(R.color.category_recording)
    SettingsCategory.SPOTIFY_META -> context.getColor(R.color.category_spotify)
    SettingsCategory.API_SYNC -> context.getColor(R.color.category_api)
    SettingsCategory.ANDROID_AUTO -> context.getColor(R.color.category_android_auto)
    SettingsCategory.ABOUT -> context.getColor(R.color.category_about)
    SettingsCategory.DEV -> context.getColor(R.color.category_dev)
}

/** Get the icon resource for this category. */
fun SettingsCategory.getIconResource(): Int = when (this) {
    SettingsCategory.PLAYER -> R.drawable.ic_category_player
    SettingsCategory.PLAYBACK -> R.drawable.ic_category_playback
    SettingsCategory.UI -> R.drawable.ic_category_ui
    SettingsCategory.METAINFO -> R.drawable.ic_category_metainfo
    SettingsCategory.RECORDING -> R.drawable.ic_category_recording
    SettingsCategory.SPOTIFY_META -> R.drawable.ic_category_spotify
    SettingsCategory.API_SYNC -> R.drawable.ic_category_api
    SettingsCategory.ANDROID_AUTO -> R.drawable.ic_category_android_auto
    SettingsCategory.ABOUT -> R.drawable.ic_category_about
    SettingsCategory.DEV -> R.drawable.ic_sheet_settings
}

/** Preference that requires long press (hold) to activate, but also supports normal tap */
@SuppressLint("ClickableViewAccessibility")
class LongPressPreference(context: Context) : Preference(context) {
    var holdDurationSeconds = 5
    var holdTextFormat = ""  // Will be set from string resource
    var defaultSummary = ""
    var onLongPressComplete: (() -> Unit)? = null
    var onNormalClick: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var counter = 0
    private var runnable: Runnable? = null
    private var longPressTriggered = false

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    counter = holdDurationSeconds
                    longPressTriggered = false
                    summary = String.format(holdTextFormat, counter)
                    runnable = object : Runnable {
                        override fun run() {
                            counter--
                            if (counter > 0) {
                                summary = String.format(holdTextFormat, counter)
                                handler.postDelayed(this, 1000)
                            } else {
                                summary = defaultSummary
                                longPressTriggered = true
                                onLongPressComplete?.invoke()
                            }
                        }
                    }
                    handler.postDelayed(runnable!!, 1000)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    runnable?.let { handler.removeCallbacks(it) }
                    summary = defaultSummary
                    // Wenn Long-Press nicht ausgelöst wurde, normalen Klick triggern
                    if (!longPressTriggered) {
                        onNormalClick?.invoke()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    runnable?.let { handler.removeCallbacks(it) }
                    summary = defaultSummary
                    true
                }
                else -> false
            }
        }
    }
}

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

fun applyLanguage(context: Context, languageCode: String) {
    val locale = when (languageCode) {
        "de" -> java.util.Locale.GERMAN
        "en" -> java.util.Locale.ENGLISH
        else -> java.util.Locale.getDefault()
    }
    val config = context.resources.configuration
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)

    // Also update AppCompatDelegate for consistent behavior
    val localeList = if (languageCode == "system") {
        androidx.core.os.LocaleListCompat.getEmptyLocaleList()
    } else {
        androidx.core.os.LocaleListCompat.forLanguageTags(languageCode)
    }
    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
}

fun PreferenceFragmentCompat.initSettingsScreen() {
    val context = preferenceManager.context
    val screen = preferenceManager.createPreferenceScreen(context)

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
                                                val doubleVal = num.toDouble()
                                                // Store as Int if it's a whole number, otherwise Float
                                                if (doubleVal == doubleVal.toLong().toDouble()) {
                                                    editor.putInt(key, doubleVal.toInt())
                                                } else {
                                                    editor.putFloat(key, num.toFloat())
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

    // Check if there's an API sync error
    val hasApiSyncError = StreamplayApiHelper.hasSyncError(context)

    // Check if dev menu is enabled
    val devMenuEnabled = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(Keys.PREF_DEV_MENU_ENABLED, false)

    val categoryMap = SettingsCategory.values().associateWith { cat ->
        PreferenceCategory(context).apply {
            title = when (cat) {
                SettingsCategory.PLAYER -> getString(R.string.settings_category_player)
                SettingsCategory.PLAYBACK -> getString(R.string.settings_category_playback)
                SettingsCategory.UI -> getString(R.string.settings_category_ui)
                SettingsCategory.METAINFO -> getString(R.string.settings_category_metainfo)
                SettingsCategory.RECORDING -> getString(R.string.settings_category_recording)
                SettingsCategory.SPOTIFY_META -> getString(R.string.settings_category_spotify_meta)
                SettingsCategory.API_SYNC -> if (hasApiSyncError) {
                    getString(R.string.settings_category_api_sync) + " ⚠"
                } else {
                    getString(R.string.settings_category_api_sync)
                }
                SettingsCategory.ANDROID_AUTO -> getString(R.string.settings_category_android_auto)
                SettingsCategory.ABOUT -> getString(R.string.settings_category_about)
                SettingsCategory.DEV -> getString(R.string.settings_category_dev)
            }
            // Kategorie-Icon mit Akzentfarbe (rot bei API-Fehler)
            val accentColor = if (cat == SettingsCategory.API_SYNC && hasApiSyncError) {
                context.getColor(R.color.category_api_error)
            } else {
                cat.getAccentColor(context)
            }
            icon = context.getDrawable(cat.getIconResource())?.mutate()?.apply {
                setTint(accentColor)
            }
            isIconSpaceReserved = true
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

    val playerPrefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
    val duckVolumeSlider = SeekBarPreference(context).apply {
        key = Keys.PREF_DUCK_VOLUME
        title = getString(R.string.settings_duck_volume)
        min = 5
        max = 50
        setDefaultValue(20)
        showSeekBarValue = true
        category = SettingsCategory.PLAYER
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        isEnabled = playerPrefs.getString(Keys.PREF_AUDIO_FOCUS_MODE, AudioFocusMode.STOP.name) == AudioFocusMode.LOWER.name
    }

    audioFocusPref.setOnPreferenceChangeListener { _, newValue ->
        duckVolumeSlider.isEnabled = newValue == AudioFocusMode.LOWER.name
        true
    }

    val networkTypePref = ListPreference(context).apply {
        key = Keys.PREF_NETWORK_TYPE
        title = getString(R.string.settings_network_type)
        entries = arrayOf(
            getString(R.string.network_type_all),
            getString(R.string.network_type_wifi_only),
            getString(R.string.network_type_mobile_only)
        )
        entryValues = arrayOf(
            NetworkType.ALL.name,
            NetworkType.WIFI_ONLY.name,
            NetworkType.MOBILE_ONLY.name
        )
        setDefaultValue(NetworkType.ALL.name)
        category = SettingsCategory.PLAYER
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
    }

    val autoAutoplaySwitch = SwitchPreferenceCompat(context).apply {
        key = Keys.PREF_AUTO_AUTOPLAY
        title = getString(R.string.settings_auto_autoplay)
        summary = getString(R.string.settings_auto_autoplay_summary)
        setDefaultValue(false)
        category = SettingsCategory.ANDROID_AUTO
        icon = context.getDrawable(R.drawable.ic_autoplay)
    }

    val autoStopSwitch = SwitchPreferenceCompat(context).apply {
        key = Keys.PREF_AUTO_STOP_ON_EXIT
        title = getString(R.string.settings_auto_stop)
        summary = getString(R.string.settings_auto_stop_summary)
        setDefaultValue(false)
        category = SettingsCategory.ANDROID_AUTO
        icon = context.getDrawable(R.drawable.ic_autoplay)
    }

    val minimizeSwitch = SwitchPreferenceCompat(context).apply {
        key = "minimize_after_autoplay"
        title = getString(R.string.settings_minimize)
        setDefaultValue(false)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_pip)
    }

    // Migrate autoplay_delay from Float to Int if needed (defensive check)
    val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
    try {
        val floatValue = prefs.getFloat("autoplay_delay", -1f)
        if (floatValue >= 0f) {
            prefs.edit().remove("autoplay_delay").putInt("autoplay_delay", floatValue.toInt()).commit()
        }
    } catch (_: ClassCastException) {
        // Already an Int - no migration needed
    }

    val delayPreference = SeekBarPreference(context).apply {
        key = "autoplay_delay"
        title = getString(R.string.settings_delay)
        min = 0
        max = 30
        setDefaultValue(0)
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

    val languagePref = ListPreference(context).apply {
        key = Keys.PREF_APP_LANGUAGE
        title = getString(R.string.settings_language)
        entries = arrayOf(
            getString(R.string.language_system),
            getString(R.string.language_german),
            getString(R.string.language_english)
        )
        entryValues = arrayOf("system", "de", "en")
        setDefaultValue("system")
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        setOnPreferenceChangeListener { _, newValue ->
            val languageCode = newValue as String
            applyLanguage(context, languageCode)
            // Restart activity to apply language
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            intent?.let { context.startActivity(it) }
            true
        }
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
            getString(R.string.bg_effect_blur),
            getString(R.string.bg_effect_visualizer)
        )
        entryValues = arrayOf(
            LiveCoverHelper.BackgroundEffect.FADE.name,
            LiveCoverHelper.BackgroundEffect.AQUA.name,
            LiveCoverHelper.BackgroundEffect.RADIAL.name,
            LiveCoverHelper.BackgroundEffect.SUNSET.name,
            LiveCoverHelper.BackgroundEffect.FOREST.name,
            LiveCoverHelper.BackgroundEffect.DIAGONAL.name,
            LiveCoverHelper.BackgroundEffect.SPOTLIGHT.name,
            LiveCoverHelper.BackgroundEffect.BLUR.name,
            LiveCoverHelper.BackgroundEffect.VISUALIZER.name
        )
        setDefaultValue(LiveCoverHelper.BackgroundEffect.FADE.name)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
    }

    val visualizerStylePref = ListPreference(context).apply {
        key = Keys.PREF_VISUALIZER_STYLE
        title = getString(R.string.settings_visualizer_style)
        entries = arrayOf(
            getString(R.string.visualizer_style_bars),
            getString(R.string.visualizer_style_wave),
            getString(R.string.visualizer_style_circle),
            getString(R.string.visualizer_style_line),
            getString(R.string.visualizer_style_spectrum),
            getString(R.string.visualizer_style_rings),
            getString(R.string.visualizer_style_blob),
            getString(R.string.visualizer_style_mirror),
            getString(R.string.visualizer_style_dna),
            getString(R.string.visualizer_style_fountain),
            getString(R.string.visualizer_style_equalizer),
            getString(R.string.visualizer_style_radar),
            getString(R.string.visualizer_style_pulse),
            getString(R.string.visualizer_style_plasma),
            getString(R.string.visualizer_style_orbits),
            getString(R.string.visualizer_style_blur_motion)
        )
        entryValues = arrayOf(
            VisualizerView.Style.BARS.name,
            VisualizerView.Style.WAVE.name,
            VisualizerView.Style.CIRCLE.name,
            VisualizerView.Style.LINE.name,
            VisualizerView.Style.SPECTRUM.name,
            VisualizerView.Style.RINGS.name,
            VisualizerView.Style.BLOB.name,
            VisualizerView.Style.MIRROR.name,
            VisualizerView.Style.DNA.name,
            VisualizerView.Style.FOUNTAIN.name,
            VisualizerView.Style.EQUALIZER.name,
            VisualizerView.Style.RADAR.name,
            VisualizerView.Style.PULSE.name,
            VisualizerView.Style.PLASMA.name,
            VisualizerView.Style.ORBITS.name,
            VisualizerView.Style.BLUR_MOTION.name
        )
        setDefaultValue(VisualizerView.Style.BARS.name)
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

    val showStationInMediaInfoSwitch = SwitchPreferenceCompat(context).apply {
        key = Keys.PREF_SHOW_STATION_IN_MEDIAINFO
        title = getString(R.string.settings_show_station_in_mediainfo)
        summary = getString(R.string.settings_show_station_in_mediainfo_summary)
        setDefaultValue(false)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val showRotateLockSwitch = SwitchPreferenceCompat(context).apply {
        key = Keys.PREF_SHOW_ROTATE_LOCK
        title = getString(R.string.settings_show_rotate_lock)
        summary = getString(R.string.settings_show_rotate_lock_summary)
        setDefaultValue(false)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_rotate_lock)
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

    // Recording Settings
    val recordingEnabledPref = SwitchPreferenceCompat(context).apply {
        key = Keys.PREF_RECORDING_ENABLED
        title = getString(R.string.settings_recording_enabled)
        summary = getString(R.string.settings_recording_enabled_summary)
        setDefaultValue(true)
        category = SettingsCategory.RECORDING
        icon = context.getDrawable(R.drawable.ic_button_record)

        // Während Aufnahme nicht deaktivierbar
        if (StreamRecordHelper.isRecording()) {
            isEnabled = false
            summary = getString(R.string.settings_recording_disabled_while_recording)
        }
    }

    recordingEnabledPref.setOnPreferenceChangeListener { _, newValue ->
        if (StreamRecordHelper.isRecording()) {
            Toast.makeText(context, getString(R.string.settings_recording_disabled_while_recording), Toast.LENGTH_SHORT).show()
            false
        } else {
            true
        }
    }

    val recordingPathPref = Preference(context).apply {
        key = Keys.PREF_RECORDING_PATH
        title = getString(R.string.settings_recording_path)
        summary = getString(R.string.settings_recording_path_summary)
        category = SettingsCategory.RECORDING
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        isSelectable = false
    }

    // StreamPlay API Settings
    val apiSyncEnabledPref = SwitchPreferenceCompat(context).apply {
        key = Keys.PREF_API_SYNC_ENABLED
        title = getString(R.string.settings_api_sync_enabled)
        summary = getString(R.string.settings_api_sync_enabled_summary)
        setDefaultValue(false)
        category = SettingsCategory.API_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val defaultApiEndpoint = Keys.DEFAULT_API_ENDPOINT
    val apiPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    if (!apiPrefs.contains(Keys.PREF_API_ENDPOINT)) {
        apiPrefs.edit().putString(Keys.PREF_API_ENDPOINT, defaultApiEndpoint).apply()
    }

    val apiEndpointPref = EditTextPreference(context).apply {
        key = Keys.PREF_API_ENDPOINT
        title = getString(R.string.settings_api_endpoint)
        setDefaultValue(defaultApiEndpoint)
        summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val value = pref.text
            if (value.isNullOrBlank()) defaultApiEndpoint else value
        }
        category = SettingsCategory.API_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        setOnPreferenceChangeListener { _, newValue ->
            val newEndpoint = newValue as String
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(Keys.PREF_API_ENDPOINT, newEndpoint.ifBlank { defaultApiEndpoint })
                .apply()
            true
        }
    }

    val apiUsernamePref = EditTextPreference(context).apply {
        key = Keys.PREF_API_USERNAME
        title = getString(R.string.settings_api_username)
        setDefaultValue("")
        summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val value = pref.text
            if (value.isNullOrBlank()) {
                pref.context.getString(R.string.settings_personal_sync_url_empty)
            } else {
                value
            }
        }
        category = SettingsCategory.API_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val apiPasswordPref = EditTextPreference(context).apply {
        key = Keys.PREF_API_PASSWORD
        title = getString(R.string.settings_api_password)
        setDefaultValue("")
        summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val value = pref.text
            if (value.isNullOrBlank()) {
                pref.context.getString(R.string.settings_personal_sync_url_empty)
            } else {
                pref.context.getString(R.string.settings_api_password_hidden)
            }
        }
        category = SettingsCategory.API_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
    }

    val apiTestLoginPref = Preference(context).apply {
        key = "api_test_login"
        title = getString(R.string.settings_api_test_login)
        category = SettingsCategory.API_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        setOnPreferenceClickListener {
            summary = getString(R.string.settings_api_testing)
            this@initSettingsScreen.lifecycleScope.launch {
                val result = StreamplayApiHelper.testLogin(
                    context,
                    apiUsernamePref.text,
                    apiPasswordPref.text
                )
                summary = when (result) {
                    is StreamplayApiHelper.ApiResult.Success ->
                        getString(R.string.settings_api_success, result.data)
                    is StreamplayApiHelper.ApiResult.Error ->
                        getString(R.string.settings_api_error, result.message)
                }
            }
            true
        }
    }

    val apiPushProfilePref = Preference(context).apply {
        key = "api_push_profile"
        title = getString(R.string.settings_api_push_profile)
        category = SettingsCategory.API_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        setOnPreferenceClickListener {
            AlertDialog.Builder(context)
                .setTitle(getString(R.string.settings_api_push_confirm_title))
                .setMessage(getString(R.string.settings_api_push_confirm_message))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    summary = getString(R.string.settings_api_pushing)
                    this@initSettingsScreen.lifecycleScope.launch {
                        val result = StreamplayApiHelper.pushToProfile(
                            context,
                            apiUsernamePref.text,
                            apiPasswordPref.text
                        )
                        summary = when (result) {
                            is StreamplayApiHelper.ApiResult.Success ->
                                getString(R.string.settings_api_success, result.data)
                            is StreamplayApiHelper.ApiResult.Error ->
                                getString(R.string.settings_api_error, result.message)
                        }
                    }
                }
                .show()
            true
        }
    }

    val apiReadProfilePref = Preference(context).apply {
        key = "api_read_profile"
        title = getString(R.string.settings_api_read_profile)
        category = SettingsCategory.API_SYNC
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        // Show last error message if there was an error
        if (hasApiSyncError) {
            val errorMsg = StreamplayApiHelper.getSyncErrorMessage(context)
            summary = getString(R.string.settings_api_error, errorMsg ?: "Unknown error")
        }
        setOnPreferenceClickListener {
            summary = getString(R.string.settings_api_reading)
            this@initSettingsScreen.lifecycleScope.launch {
                val result = StreamplayApiHelper.readFromProfile(
                    context,
                    apiUsernamePref.text,
                    apiPasswordPref.text
                )
                when (result) {
                    is StreamplayApiHelper.ApiResult.Success -> {
                        summary = getString(R.string.settings_api_read_success, result.data.stations.size)
                        Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
                        // Update category color - need to refresh the fragment
                        categoryMap[SettingsCategory.API_SYNC]?.let { catPref ->
                            catPref.title = getString(R.string.settings_category_api_sync)
                            catPref.icon = context.getDrawable(SettingsCategory.API_SYNC.getIconResource())?.mutate()?.apply {
                                setTint(SettingsCategory.API_SYNC.getAccentColor(context))
                            }
                        }
                    }
                    is StreamplayApiHelper.ApiResult.Error -> {
                        summary = getString(R.string.settings_api_error, result.message)
                        Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                        // Update category to show error state
                        categoryMap[SettingsCategory.API_SYNC]?.let { catPref ->
                            catPref.title = getString(R.string.settings_category_api_sync) + " ⚠"
                            catPref.icon = context.getDrawable(SettingsCategory.API_SYNC.getIconResource())?.mutate()?.apply {
                                setTint(context.getColor(R.color.category_api_error))
                            }
                        }
                    }
                }
            }
            true
        }
    }

    val versionPref = LongPressPreference(context).apply {
        key = "app_version"
        title = getString(R.string.settings_app_version)
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val gitHash = at.plankt0n.streamplay.BuildConfig.GIT_HASH
        val buildTime = at.plankt0n.streamplay.BuildConfig.BUILD_TIME
        defaultSummary = "${pkgInfo.versionName} - $gitHash ($buildTime)"
        summary = defaultSummary
        category = SettingsCategory.ABOUT
        // App-Icon NICHT tinten - wird später ausgeschlossen
        icon = context.getDrawable(R.mipmap.ic_launcher)
        holdDurationSeconds = 5
        holdTextFormat = getString(R.string.hold_open_github)
        onLongPressComplete = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Planqton/streamplay"))
            context.startActivity(intent)
        }
    }

    val updatePref = LongPressPreference(context).apply {
        key = "check_updates"
        title = getString(R.string.settings_check_updates)
        val updateAvailable = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(Keys.PREF_UPDATE_AVAILABLE, false)
        val baseSummary = if (updateAvailable) {
            context.getString(R.string.update_available_title)
        } else ""
        defaultSummary = baseSummary
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
        holdDurationSeconds = 5
        holdTextFormat = getString(R.string.hold_force_update)
        // onNormalClick und onLongPressComplete werden in SettingsFragment gesetzt
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
                        getString(R.string.sync_complete, result.added, result.updated),
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        getString(R.string.sync_error, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            true
        }
    }

    val crashLogsPref = Preference(context).apply {
        key = "crash_logs"
        title = getString(R.string.settings_crash_logs)
        summary = getString(R.string.settings_crash_logs_summary)
        category = SettingsCategory.DEV
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        setOnPreferenceClickListener {
            val currentFragment = (context as? android.app.Activity)
                ?.let { activity ->
                    (activity as? androidx.fragment.app.FragmentActivity)
                        ?.supportFragmentManager?.findFragmentById(R.id.fragment_container)
                }
            currentFragment?.let { fragment ->
                (context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
                    ?.beginTransaction()
                    ?.setReorderingAllowed(true)
                    ?.hide(fragment)
                    ?.add(R.id.fragment_container, CrashLogFragment())
                    ?.addToBackStack(null)
                    ?.commit()
            }
            true
        }
    }

    val crashAppPref = Preference(context).apply {
        key = "crash_app"
        title = getString(R.string.settings_crash_app)
        summary = getString(R.string.settings_crash_app_summary)
        category = SettingsCategory.DEV
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        setOnPreferenceClickListener {
            val crashTypes = arrayOf(
                getString(R.string.crash_type_null_pointer),
                getString(R.string.crash_type_out_of_memory),
                getString(R.string.crash_type_index_out_of_bounds),
                getString(R.string.crash_type_illegal_state),
                getString(R.string.crash_type_stack_overflow),
                getString(R.string.crash_type_runtime)
            )
            AlertDialog.Builder(context)
                .setTitle(getString(R.string.settings_crash_app))
                .setItems(crashTypes) { _, which ->
                    when (which) {
                        0 -> throw NullPointerException("Test crash: NullPointerException")
                        1 -> throw OutOfMemoryError("Test crash: OutOfMemoryError")
                        2 -> throw ArrayIndexOutOfBoundsException("Test crash: ArrayIndexOutOfBoundsException")
                        3 -> throw IllegalStateException("Test crash: IllegalStateException")
                        4 -> {
                            fun recursive(): Int = recursive() + 1
                            recursive()
                        }
                        5 -> throw RuntimeException("Test crash: RuntimeException")
                    }
                }
                .show()
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

    val factoryResetPref = LongPressPreference(context).apply {
        key = "factory_reset"
        title = getString(R.string.settings_factory_reset)
        summary = getString(R.string.settings_factory_reset_summary)
        category = SettingsCategory.ABOUT
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        holdDurationSeconds = 5
        holdTextFormat = getString(R.string.settings_factory_reset_hold)
        defaultSummary = getString(R.string.settings_factory_reset_summary)
        onLongPressComplete = {
            AlertDialog.Builder(context)
                .setTitle(getString(R.string.settings_factory_reset_confirm_title))
                .setMessage(getString(R.string.settings_factory_reset_confirm_message))
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    // Löscht ALLE App-Daten und startet die App neu (wie Neuinstallation)
                    (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                        .clearApplicationUserData()
                }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }
    }

    val exitAppPref = Preference(context).apply {
        key = "exit_app"
        title = getString(R.string.settings_exit_app)
        summary = getString(R.string.settings_exit_app_summary)
        category = SettingsCategory.ABOUT
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
        setOnPreferenceClickListener {
            // Service stoppen
            context.stopService(Intent(context, StreamingService::class.java))
            // Activity beenden
            (context as? android.app.Activity)?.finishAffinity()
            // Prozess beenden
            Runtime.getRuntime().exit(0)
            true
        }
    }

    val preferences = listOf(
        audioFocusPref,
        duckVolumeSlider,
        networkTypePref,
        autoAutoplaySwitch,
        autoStopSwitch,
        autoplaySwitch,
        minimizeSwitch,
        delayPreference,
        orientationPref,
        languagePref,
        bannerSwitch,
        resumeLiveSwitch,
        backgroundEffectPref,
        visualizerStylePref,
        coverModePref,
        coverAnimationStylePref,
        showStationInMediaInfoSwitch,
        showRotateLockSwitch,
        spotifyApiKeyPref,
        spotifySecretKeyPref,
        useSpotifyMetaPref,
        recordingEnabledPref,
        recordingPathPref,
        apiSyncEnabledPref,
        apiEndpointPref,
        apiUsernamePref,
        apiPasswordPref,
        apiTestLoginPref,
        apiPushProfilePref,
        apiReadProfilePref,
        versionPref,
        updatePref,
        addTestPref,
        crashLogsPref,
        crashAppPref,
        settingsTransferPref,
        factoryResetPref,
        exitAppPref
    )

    // Icons der Preferences mit Kategorie-Farbe tinting (außer App-Icon)
    preferences.forEach { pref ->
        if (pref.key != "app_version") {
            pref.category?.let { cat ->
                pref.icon = pref.icon?.mutate()?.apply {
                    setTint(cat.getAccentColor(context))
                }
            }
        }
    }

    SettingsCategory.values().forEach { cat ->
        // Skip DEV category if dev menu is not enabled
        if (cat == SettingsCategory.DEV && !devMenuEnabled) return@forEach

        val catPref = categoryMap[cat] ?: return@forEach
        val catPrefs = preferences.filter { it.category == cat }
        if (catPrefs.isNotEmpty()) {
            screen.addPreference(catPref)
            catPrefs.forEach { catPref.addPreference(it) }
        }
    }

    preferenceScreen = screen

    updateSpotifyToggle()
}
