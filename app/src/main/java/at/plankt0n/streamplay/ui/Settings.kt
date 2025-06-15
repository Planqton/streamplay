package at.plankt0n.streamplay.ui

import androidx.preference.*
import at.plankt0n.streamplay.R

/** Possible categories a preference can belong to. */
enum class SettingsCategory { PLAYBACK, UI, METAINFO }

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
            }
            icon = when (cat) {
                SettingsCategory.PLAYBACK -> context.getDrawable(R.drawable.ic_button_play)
                SettingsCategory.UI -> context.getDrawable(R.drawable.ic_sheet_settings)
                SettingsCategory.METAINFO -> context.getDrawable(R.drawable.ic_sheet_discover)
            }
        }
    }

    val autoplaySwitch = SwitchPreferenceCompat(context).apply {
        key = "autoplay_enabled"
        title = getString(R.string.settings_autoplay)
        setDefaultValue(false)
        category = SettingsCategory.PLAYBACK
        icon = context.getDrawable(R.drawable.ic_button_play)
    }

    val delayPreference = SeekBarPreference(context).apply {
        key = "autoplay_delay"
        title = getString(R.string.settings_delay)
        min = 0
        max = 30
        showSeekBarValue = true
        category = SettingsCategory.PLAYBACK
        icon = context.getDrawable(R.drawable.ic_button_forward)
    }

    val minimizeSwitch = SwitchPreferenceCompat(context).apply {
        key = "minimize_after_autoplay"
        title = getString(R.string.settings_minimize)
        setDefaultValue(false)
        category = SettingsCategory.UI
        icon = context.getDrawable(R.drawable.ic_drag_handle)
    }

    val preferences = listOf(autoplaySwitch, delayPreference, minimizeSwitch)

    SettingsCategory.values().forEach { cat ->
        val catPref = categoryMap[cat]!!
        screen.addPreference(catPref)
        preferences.filter { it.category == cat }.forEach { catPref.addPreference(it) }
    }

    preferenceScreen = screen
}
