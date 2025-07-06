package at.plankt0n.streamplay.ui

import android.content.Context
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

    private var downTime = 0L

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val held = System.currentTimeMillis() - downTime
                    if (held >= Keys.UPDATE_FORCE_HOLD_MS) {
                        onLongPressListener?.invoke()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }
}
