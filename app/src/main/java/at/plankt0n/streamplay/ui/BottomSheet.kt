package at.plankt0n.streamplay.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import at.plankt0n.streamplay.R

class MediaItemOptionsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchAutostart: Switch

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet, container, false)

        sharedPreferences = requireContext().getSharedPreferences("StreamPlayPrefs", Context.MODE_PRIVATE)
        switchAutostart = view.findViewById(R.id.switch_autostart)

        // Autostart-Wert setzen
        switchAutostart.isChecked = sharedPreferences.getBoolean("autostart_enabled", false)

        // Listener für Autoplay
        switchAutostart.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("autoplay_enabled", isChecked).apply()
        }

        // Station Setup
        view.findViewById<LinearLayout>(R.id.option_stations).setOnClickListener {
            dismiss()
            openStationsFragment()
        }

        // Einstellungen (Settings)
        view.findViewById<LinearLayout>(R.id.option_settings).setOnClickListener {
            dismiss()
            openSettingsFragment()
        }


        return view
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    private fun openStationsFragment() {
        parentFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, StationsFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openSettingsFragment() {

    }
}
