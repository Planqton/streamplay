package at.plankt0n.streamplay.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.helper.GitHubUpdateChecker
import at.plankt0n.streamplay.R
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = Keys.PREFS_NAME
        initSettingsScreen()

        findPreference<Preference>("check_updates")?.setOnPreferenceClickListener {
            lifecycleScope.launch {
                GitHubUpdateChecker(requireContext()).checkForUpdate()
            }
            true
        }

        findPreference<Preference>("open_equalizer")?.setOnPreferenceClickListener {
            val parentSheet = parentFragment as? MediaItemOptionsBottomSheet
            if (parentSheet != null) {
                parentSheet.openEqualizerFragment()
            } else {
                requireActivity().supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.fragment_container, EqualizerFragment())
                    .addToBackStack(null)
                    .commit()
            }
            true
        }
    }

}
