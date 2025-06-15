package at.plankt0n.streamplay.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import at.plankt0n.streamplay.Keys

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = Keys.PREFS_NAME
        initSettingsScreen()
    }
}
