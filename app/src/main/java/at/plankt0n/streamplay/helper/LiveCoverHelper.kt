package at.plankt0n.streamplay.helper

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import at.plankt0n.streamplay.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.BitmapImageViewTarget
import androidx.palette.graphics.Palette

object LiveCoverHelper {

    fun loadCoverWithBackgroundFade(
        context: Context,
        imageUrl: String,
        imageView: ImageView,
        backgroundTarget: View,
        defaultColor: Int,
        lastColor: Int?,
        onNewColor: (Int) -> Unit
    ) {
        Glide.with(context)
            .asBitmap()
            .load(imageUrl)
            .placeholder(R.drawable.ic_placeholder_logo)
            .error(R.drawable.ic_stationcover_placeholder)
            .into(object : BitmapImageViewTarget(imageView) {
                override fun setResource(resource: Bitmap?) {
                    super.setResource(resource)
                    resource?.let { bitmap ->
                        Palette.from(bitmap).generate { palette ->
                            palette?.let {
                                val dominantColor = it.getDominantColor(defaultColor)

                                val hsv = FloatArray(3)
                                Color.colorToHSV(dominantColor, hsv)
                                hsv[1] = (hsv[1] * 0.7f).coerceAtMost(1.0f)
                                hsv[2] = (hsv[2] + 0.1f).coerceAtMost(1.0f)
                                val smoothColor = Color.HSVToColor(hsv)

                                // Nur animieren, wenn sich Farbe wirklich ändert
                                if (lastColor != smoothColor) {
                                    val animator = ValueAnimator.ofArgb(
                                        lastColor ?: defaultColor,
                                        smoothColor
                                    ).apply {
                                        duration = 400
                                        addUpdateListener { anim ->
                                            val color = anim.animatedValue as Int
                                            val gradient = GradientDrawable(
                                                GradientDrawable.Orientation.TOP_BOTTOM,
                                                intArrayOf(color, Color.TRANSPARENT)
                                            )
                                            gradient.cornerRadius = 0f
                                            backgroundTarget.background = gradient
                                        }
                                    }
                                    animator.start()
                                    onNewColor(smoothColor)
                                }
                            }
                        }
                    }
                }
            })
    }
}
