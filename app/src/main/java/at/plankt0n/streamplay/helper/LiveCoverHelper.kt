package at.plankt0n.streamplay.helper

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import at.plankt0n.streamplay.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
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
        BLUR,
        VISUALIZER
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
        onNewEffect: (BackgroundEffect) -> Unit,
        onVisualizerColors: ((Int, Int) -> Unit)? = null
    ) {
        Glide.with(context)
            .load(imageUrl)
            .placeholder(R.drawable.ic_placeholder_logo)
            .error(R.drawable.ic_stationcover_placeholder)
            .into(imageView)

        Glide.with(context)
            .asBitmap()
            .load(imageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                    if (effect == BackgroundEffect.VISUALIZER) {
                        // Skip if activity is destroyed
                        if (context is Activity && (context.isDestroyed || context.isFinishing)) {
                            return
                        }

                        // Für Visualizer: Farben aus Cover extrahieren
                        Palette.from(bitmap).generate { palette ->
                            if (context is Activity && (context.isDestroyed || context.isFinishing)) {
                                return@generate
                            }

                            // Primärfarbe: Vibrant oder Dominant
                            val vibrantColor = palette?.getVibrantColor(0)
                            val dominantColor = palette?.getDominantColor(defaultColor) ?: defaultColor
                            val primaryColor = if (vibrantColor != null && vibrantColor != 0) vibrantColor else dominantColor

                            // Sekundärfarbe: Light Vibrant, Muted, oder komplementär
                            val lightVibrant = palette?.getLightVibrantColor(0)
                            val mutedColor = palette?.getMutedColor(0)
                            val secondaryColor = when {
                                lightVibrant != null && lightVibrant != 0 -> lightVibrant
                                mutedColor != null && mutedColor != 0 -> mutedColor
                                else -> adjustBrightness(primaryColor, 1.3f)
                            }

                            // Hintergrund dunkel halten
                            backgroundTarget.background = createGradient(Color.BLACK, effect)
                            onNewColor(primaryColor)
                            onNewEffect(effect)

                            // Farben an Visualizer weitergeben
                            onVisualizerColors?.invoke(primaryColor, secondaryColor)
                        }
                    } else if (effect == BackgroundEffect.BLUR) {
                        Palette.from(bitmap).generate { palette ->
                            // Skip if activity is destroyed (e.g. during orientation change)
                            if (context is Activity && (context.isDestroyed || context.isFinishing)) {
                                return@generate
                            }

                            val dominantColor = palette?.getDominantColor(defaultColor) ?: defaultColor

                            val hsv = FloatArray(3)
                            Color.colorToHSV(dominantColor, hsv)
                            hsv[1] = (hsv[1] * 0.7f).coerceAtMost(1.0f)
                            hsv[2] = (hsv[2] + 0.1f).coerceAtMost(1.0f)
                            val smoothColor = Color.HSVToColor(hsv)

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
                                        transition: Transition<in Bitmap>?,
                                    ) {
                                        backgroundTarget.background =
                                            BitmapDrawable(context.resources, resource)
                                        onNewColor(smoothColor)
                                        onNewEffect(effect)
                                    }

                                    override fun onLoadCleared(placeholder: Drawable?) {}
                                })
                        }
                    } else {
                        Palette.from(bitmap).generate { palette ->
                            // Skip if activity is destroyed (e.g. during orientation change)
                            if (context is Activity && (context.isDestroyed || context.isFinishing)) {
                                return@generate
                            }

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
                                }
                                onNewColor(smoothColor)
                                onNewEffect(effect)
                            }
                        }
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
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
            BackgroundEffect.VISUALIZER -> GradientDrawable().apply {
                // Transparenter Hintergrund für Visualizer (VisualizerView wird darüber gelegt)
                setColor(Color.parseColor("#1A000000"))
            }
        }.apply { cornerRadius = 0f }
    }

    private fun adjustBrightness(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }
}
