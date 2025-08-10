package at.plankt0n.streamplay.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import androidx.preference.EditTextPreference
import at.plankt0n.streamplay.R

class ScanEditTextPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.editTextPreferenceStyle
) : EditTextPreference(context, attrs, defStyleAttr) {

    var onScanRequest: (() -> Unit)? = null

    init {
        dialogLayoutResource = R.layout.preference_scan_edittext
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        view.findViewById<Button>(R.id.scan_button)?.setOnClickListener {
            onScanRequest?.invoke()
        }
    }
}
