package at.plankt0n.streamplay.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.cardview.widget.CardView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.ui.DiscoverFragment
import at.plankt0n.streamplay.MainActivity
import at.plankt0n.streamplay.ui.SettingsPageFragment
import at.plankt0n.streamplay.ui.MetaLogFragment

class MediaItemOptionsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "MediaItemOptionsBottomSheet"
        private const val DEV_MENU_HOLD_DURATION_MS = 5000L
    }

    private var previousOrientation: Int? = null
    private val devMenuHandler = Handler(Looper.getMainLooper())
    private var devMenuRunnable: Runnable? = null
    private var devMenuHoldStartTime: Long = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet, container, false)

        // Station Setup
        view.findViewById<CardView>(R.id.option_stations).setOnClickListener {
            dismiss()
            openStationsFragment()
        }

        // Discover Stations
        view.findViewById<CardView>(R.id.option_discover).setOnClickListener {
            dismiss()
            openDiscoverFragment()
        }

        // Logs
        view.findViewById<CardView>(R.id.option_logs).setOnClickListener {
            dismiss()
            openMetaLogFragment()
        }

        // Equalizer
        view.findViewById<CardView>(R.id.option_equalizer).setOnClickListener {
            dismiss()
            openEqualizerBottomSheet()
        }

        // Settings - with long press for dev menu toggle
        val settingsCard = view.findViewById<CardView>(R.id.option_settings)
        settingsCard.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    devMenuHoldStartTime = System.currentTimeMillis()
                    devMenuRunnable = Runnable {
                        // Toggle dev menu after 5 seconds
                        toggleDevMenu()
                    }
                    devMenuHandler.postDelayed(devMenuRunnable!!, DEV_MENU_HOLD_DURATION_MS)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    devMenuRunnable?.let { devMenuHandler.removeCallbacks(it) }
                    val holdDuration = System.currentTimeMillis() - devMenuHoldStartTime
                    if (holdDuration < DEV_MENU_HOLD_DURATION_MS) {
                        // Normal click - open settings
                        dismiss()
                        openSettingsFragment()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    devMenuRunnable?.let { devMenuHandler.removeCallbacks(it) }
                    true
                }
                else -> false
            }
        }

        return view
    }

    private fun toggleDevMenu() {
        val prefs = requireContext().getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        val currentState = prefs.getBoolean(Keys.PREF_DEV_MENU_ENABLED, false)
        val newState = !currentState
        prefs.edit().putBoolean(Keys.PREF_DEV_MENU_ENABLED, newState).apply()

        val message = if (newState) {
            getString(R.string.dev_menu_enabled)
        } else {
            getString(R.string.dev_menu_disabled)
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
            behavior.isFitToContents = false
            behavior.halfExpandedRatio = 0.75f
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED
            behavior.skipCollapsed = true
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        // Remove any pending handler callbacks to prevent memory leaks
        devMenuHandler.removeCallbacksAndMessages(null)
        devMenuRunnable = null
        previousOrientation?.let { activity?.requestedOrientation = it }
        super.onDismiss(dialog)
    }

    private fun openStationsFragment() {
        (activity as? MainActivity)?.showStationsPage()
    }

    private fun openDiscoverFragment() {
        val currentFragment = requireActivity().supportFragmentManager.findFragmentById(R.id.fragment_container) ?: return
        requireActivity().supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .hide(currentFragment)
            .add(R.id.fragment_container, DiscoverFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openMetaLogFragment() {
        val currentFragment = requireActivity().supportFragmentManager.findFragmentById(R.id.fragment_container) ?: return
        requireActivity().supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .hide(currentFragment)
            .add(R.id.fragment_container, MetaLogFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openSettingsFragment() {
        val currentFragment = requireActivity().supportFragmentManager.findFragmentById(R.id.fragment_container) ?: return
        requireActivity().supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .hide(currentFragment)
            .add(R.id.fragment_container, SettingsPageFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openEqualizerBottomSheet() {
        val equalizerSheet = EqualizerBottomSheet()
        equalizerSheet.show(parentFragmentManager, EqualizerBottomSheet.TAG)
    }

}
