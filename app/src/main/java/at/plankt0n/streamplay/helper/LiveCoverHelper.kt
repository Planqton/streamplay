package at.plankt0n.streamplay.helper

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import at.plankt0n.streamplay.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import androidx.palette.graphics.Palette
import jp.wasabeef.glide.transformations.BlurTransformation

object LiveCoverHelper {

    enum class BackgroundEffect {
        FADE,
        AQUA,
        RADIAL,
        SUNSET,
        FOREST,
        DIAGONAL,
        SPOTLIGHT,
        BLUR
    }

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
                        if (effect == BackgroundEffect.BLUR) {
                            Glide.with(context)
                                .asBitmap()
                                .load(imageUrl)
                                .apply(
                                    RequestOptions()
                                        .centerCrop()
                                        .transform(BlurTransformation(25, 3))
                                )
                                .into(object : CustomTarget<Bitmap>() {
                                    override fun onResourceReady(
                                        resource: Bitmap,
                                        transition: Transition<in Bitmap>?
                                    ) {
                                        backgroundTarget.background =
                                            BitmapDrawable(context.resources, resource)
                                        onNewColor(defaultColor)
                                        onNewEffect(effect)
                                    }

                                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                                })
                        } else {
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
                }
            })
    }

    fun createGradient(color: Int, effect: BackgroundEffect): GradientDrawable {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val lighter = Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] * 0.7f).coerceAtMost(1f), (hsv[2] * 1.1f).coerceAtMost(1f)))
        val darker = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], (hsv[2] * 0.7f).coerceAtMost(1f)))

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
                intArrayOf(lighter, color)
            )
            BackgroundEffect.FOREST -> GradientDrawable(
                GradientDrawable.Orientation.TR_BL,
                intArrayOf(darker, color)
            )
            BackgroundEffect.DIAGONAL -> GradientDrawable(
                GradientDrawable.Orientation.BR_TL,
                intArrayOf(color, Color.TRANSPARENT)
            )
            BackgroundEffect.SPOTLIGHT -> GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(lighter, color)
                gradientRadius = 600f
            }
            BackgroundEffect.BLUR -> GradientDrawable().apply { setColor(color) }
        }.apply { cornerRadius = 0f }
    }
}
