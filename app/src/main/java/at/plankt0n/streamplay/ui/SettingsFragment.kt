package at.plankt0n.streamplay.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import at.plankt0n.streamplay.ui.LongPressPreference
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.helper.GitHubUpdateChecker
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = Keys.PREFS_NAME
        initSettingsScreen()

        findPreference<LongPressPreference>("check_updates")?.apply {
            setOnPreferenceClickListener {
                lifecycleScope.launch {
                    GitHubUpdateChecker(requireContext()).checkForUpdate()
                }
                true
            }
            onLongPressListener = {
                lifecycleScope.launch {
                    GitHubUpdateChecker(requireContext()).forceUpdate()
                }
            }
        }
    }

}
