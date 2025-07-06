package at.plankt0n.streamplay.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import at.plankt0n.streamplay.Keys

class LongPressPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    var onLongPressListener: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isPressing = false

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isPressing = true
                    handler.postDelayed({
                        if (isPressing) {
                            onLongPressListener?.invoke()
                        }
                    }, Keys.UPDATE_FORCE_HOLD_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPressing = false
                    handler.removeCallbacksAndMessages(null)
                }
            }
            false
        }
    }
}
