package at.plankt0n.streamplay.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.preference.*
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.helper.LiveCoverHelper

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
            getString(R.string.bg_effect_radial)
        )
        entryValues = arrayOf(
            LiveCoverHelper.BackgroundEffect.FADE.name,
            LiveCoverHelper.BackgroundEffect.AQUA.name,
            LiveCoverHelper.BackgroundEffect.RADIAL.name
        )
        setDefaultValue(LiveCoverHelper.BackgroundEffect.FADE.name)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_sheet_settings)
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

    val preferences = listOf(
        autoplaySwitch,
        delayPreference,
        minimizeSwitch,
        bannerSwitch,
        backgroundEffectPref,
        versionPref,
        updatePref
    )

    SettingsCategory.values().forEach { cat ->
        val catPref = categoryMap[cat]!!
        screen.addPreference(catPref)
        preferences.filter { it.category == cat }.forEach { catPref.addPreference(it) }
    }

    preferenceScreen = screen
}
