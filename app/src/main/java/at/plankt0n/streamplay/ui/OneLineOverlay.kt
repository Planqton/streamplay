package at.plankt0n.streamplay.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.BlurMaskFilter
import android.util.AttributeSet
import android.view.View

class OneLineOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val backgroundPaint = Paint().apply {
        color = 0x80000000.toInt()
        maskFilter = BlurMaskFilter(50f, BlurMaskFilter.Blur.NORMAL)
    }
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val lineHeight = height / 24f
        val top = height / 2f - lineHeight / 2f
        canvas.drawRect(0f, top, width.toFloat(), top + lineHeight, clearPaint)
        canvas.restoreToCount(save)
    }
}
