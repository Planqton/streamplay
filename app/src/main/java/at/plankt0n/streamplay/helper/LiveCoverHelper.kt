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

    enum class BackgroundEffect { FADE, AQUA, RADIAL, SUNSET, FOREST }

    fun loadCoverWithBackground(
        context: Context,
        imageUrl: String,
        imageView: ImageView,
        backgroundTarget: View,
        defaultColor: Int,
        lastColor: Int?,
        lastEffect: BackgroundEffect?,
        effect: BackgroundEffect = BackgroundEffect.FADE,
        onNewColor: (Int) -> Unit,
        onNewEffect: (BackgroundEffect) -> Unit
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

                                // Nur animieren, wenn sich Farbe oder Effekt ändert
                                if (lastColor != smoothColor || lastEffect != effect) {
                                    val animator = ValueAnimator.ofArgb(
                                        lastColor ?: defaultColor,
                                        smoothColor
                                    ).apply {
                                        duration = 400
                                        addUpdateListener { anim ->
                                            val color = anim.animatedValue as Int
                                            val gradient = createGradient(color, effect)
                                            backgroundTarget.background = gradient
                                        }
                                    }
                                    animator.start()
                                    onNewColor(smoothColor)
                                    onNewEffect(effect)
                                }
                            }
                        }
                    }
                }
            })
    }

    fun createGradient(color: Int, effect: BackgroundEffect): GradientDrawable {
        return when (effect) {
            BackgroundEffect.FADE -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(color, Color.TRANSPARENT)
            )
            BackgroundEffect.AQUA -> GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                intArrayOf(color, Color.TRANSPARENT)
            )
            BackgroundEffect.RADIAL -> GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(color, Color.TRANSPARENT)
                gradientRadius = 800f
            }
            BackgroundEffect.SUNSET -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#ff7e5f"), Color.parseColor("#feb47b"))
            )
            BackgroundEffect.FOREST -> GradientDrawable(
                GradientDrawable.Orientation.TR_BL,
                intArrayOf(Color.parseColor("#a8e063"), Color.parseColor("#56ab2f"))
            )
        }.apply { cornerRadius = 0f }
    }
}
