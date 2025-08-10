package at.plankt0n.streamplay.ui

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.helper.GitHubUpdateChecker
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

    private var scanTarget: EditTextPreference? = null

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val target = scanTarget
            if (result.resultCode == android.app.Activity.RESULT_OK && target != null) {
                val text = result.data?.getStringExtra("scanned_text")?.trim()
                if (!text.isNullOrBlank()) {
                    target.text = text
                    updateSpotifyToggle()
                } else {
                    Toast.makeText(requireContext(), R.string.scan_no_text, Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                scanLauncher.launch(Intent(requireContext(), LiveScanActivity::class.java))
            } else {
                Toast.makeText(requireContext(), R.string.scan_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    fun startScan(target: EditTextPreference) {
        scanTarget = target
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            scanLauncher.launch(Intent(requireContext(), LiveScanActivity::class.java))
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = Keys.PREFS_NAME
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(prefListener)
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

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is EditTextPreference &&
            (preference.key == Keys.PREF_SPOTIFY_CLIENT_ID ||
                    preference.key == Keys.PREF_SPOTIFY_CLIENT_SECRET ||
                    preference.key == "personal_sync_url")
        ) {
            val dialogFragment = ScanEditTextPreferenceDialogFragment.newInstance(preference.key)
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    class ScanEditTextPreferenceDialogFragment : EditTextPreferenceDialogFragmentCompat() {
        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)
            builder.setNeutralButton(R.string.scan, null)
        }

        override fun onStart() {
            super.onStart()
            val parent = targetFragment as? SettingsFragment
            (dialog as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL)
                .setOnClickListener {
                    val pref = preference as? EditTextPreference
                    if (pref != null) {
                        parent?.startScan(pref)
                    }
                }
        }

        companion object {
            fun newInstance(key: String): ScanEditTextPreferenceDialogFragment {
                val fragment = ScanEditTextPreferenceDialogFragment()
                val bundle = Bundle(1)
                bundle.putString(ARG_KEY, key)
                fragment.arguments = bundle
                return fragment
            }
        }
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

}
