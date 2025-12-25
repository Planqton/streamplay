package at.plankt0n.streamplay.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.helper.GitHubUpdateChecker
import android.content.SharedPreferences
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
        if (key == Keys.PREF_UPDATE_AVAILABLE) {
            val pref = findPreference<Preference>("check_updates")
            val show = shared.getBoolean(key, false)
            pref?.summary = if (show) {
                val text = getString(R.string.update_available_title)
                val color = requireContext().getColor(R.color.update_available_orange)
                SpannableString(text).apply {
                    setSpan(
                        ForegroundColorSpan(color),
                        0,
                        text.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else null
        }
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = Keys.PREFS_NAME
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(prefListener)
        initSettingsScreen()

        // check_updates: Normal Click handled by LongPressPreference's onNormalClick
        // Long hold (5s) triggers forceUpdate via onLongPressComplete
        (findPreference<Preference>("check_updates") as? LongPressPreference)?.apply {
            onNormalClick = {
                lifecycleScope.launch {
                    GitHubUpdateChecker(requireContext()).checkForUpdate()
                }
            }
            onLongPressComplete = {
                lifecycleScope.launch {
                    GitHubUpdateChecker(requireContext()).forceUpdate()
                }
            }
        }
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updateSpotifyToggle()
    }

}
