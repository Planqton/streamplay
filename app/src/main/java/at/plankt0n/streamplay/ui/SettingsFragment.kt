package at.plankt0n.streamplay.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.helper.GitHubUpdateChecker
import android.content.SharedPreferences
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
        if (key == Keys.PREF_UPDATE_AVAILABLE) {
            val pref = findPreference<Preference>("check_updates")
            val show = shared.getBoolean(key, false)
            pref?.summary = if (show) getString(R.string.update_available_title) else null
        }
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = Keys.PREFS_NAME
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)
        initSettingsScreen()

        findPreference<Preference>("check_updates")?.setOnPreferenceClickListener {
            lifecycleScope.launch {
                GitHubUpdateChecker(requireContext()).checkForUpdate()
            }
            true
        }

        findPreference<Preference>("app_version")?.let { pref ->
            var tapCount = 0
            pref.setOnPreferenceClickListener {
                tapCount++
                if (tapCount >= Keys.UPDATE_FORCE_TAP_COUNT) {
                    tapCount = 0
                    lifecycleScope.launch {
                        GitHubUpdateChecker(requireContext()).forceUpdate()
                    }
                }
                true
            }
        }
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

}
