package at.plankt0n.streamplay.ui

import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.ui.DiscoverFragment
import at.plankt0n.streamplay.MainActivity
import at.plankt0n.streamplay.ui.SettingsPageFragment
import at.plankt0n.streamplay.ui.MetaLogFragment

class MediaItemOptionsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "MediaItemOptionsBottomSheet"
    }

    private var previousOrientation: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet, container, false)
        // Station Setup
        view.findViewById<LinearLayout>(R.id.option_stations).setOnClickListener {
            dismiss()
            openStationsFragment()
        }
        view.findViewById<ImageView>(R.id.icon_stations).setOnClickListener {
            dismiss()
            openStationsFragment()
        }

        // Discover Stations
        view.findViewById<LinearLayout>(R.id.option_discover).setOnClickListener {
            dismiss()
            openDiscoverFragment()
        }

        view.findViewById<LinearLayout>(R.id.option_logs).setOnClickListener {
            dismiss()
            openMetaLogFragment()
        }

        view.findViewById<LinearLayout>(R.id.option_settings).setOnClickListener {
            dismiss()
            openSettingsFragment()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        previousOrientation?.let { activity?.requestedOrientation = it }
        super.onDismiss(dialog)
    }

    private fun openStationsFragment() {
        (activity as? MainActivity)?.showStationsPage()
    }

    private fun openDiscoverFragment() {
        requireActivity().supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, DiscoverFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openMetaLogFragment() {
        requireActivity().supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, MetaLogFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openSettingsFragment() {
        requireActivity().supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, SettingsPageFragment())
            .addToBackStack(null)
            .commit()
    }

}
