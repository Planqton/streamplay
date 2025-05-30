package at.plankt0n.streamplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import at.plankt0n.streamplay.R
import android.widget.LinearLayout

class MediaItemOptionsBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.pageviw_media_item_bottom_sheet, container, false)

        // Klick-Listener für Optionen
        view.findViewById<LinearLayout>(R.id.StationSetup).setOnClickListener {
            dismiss()
            openStationsFragment()
        }
        view.findViewById<LinearLayout>(R.id.option_share).setOnClickListener {
            // Aktion
            dismiss()
        }
        view.findViewById<LinearLayout>(R.id.option_delete).setOnClickListener {
            // Aktion
            dismiss()
        }

        return view
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
            // Vollständig geöffnet starten
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED

            // Höhe auf den gesamten Bildschirm setzen
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    private fun openStationsFragment() {
        // Fragment starten
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, StationsFragment())
            .addToBackStack(null)
            .commit()
    }
}
