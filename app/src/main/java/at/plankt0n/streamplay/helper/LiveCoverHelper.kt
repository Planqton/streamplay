package at.plankt0n.streamplay.helper

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PictureDrawable
import android.view.View
import android.widget.ImageView
import at.plankt0n.streamplay.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import androidx.palette.graphics.Palette
import com.bumptech.glide.load.resource.gif.GifDrawable
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
            .load(imageUrl)
            .placeholder(R.drawable.ic_placeholder_logo)
            .error(R.drawable.ic_stationcover_placeholder)
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    imageView.setImageDrawable(resource)

                    val bitmap: Bitmap? = when (resource) {
                        is BitmapDrawable -> resource.bitmap
                        is GifDrawable -> {
                            resource.start()
                            resource.firstFrame
                        }
                        is PictureDrawable -> {
                            imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                            drawableToBitmap(resource)
                        }
                        else -> null
                    }

                    bitmap?.let { bmp ->
                        if (effect == BackgroundEffect.BLUR) {
                            Palette.from(bmp).generate { palette ->
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
                                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                            backgroundTarget.background = BitmapDrawable(context.resources, resource)
                                            onNewColor(smoothColor)
                                            onNewEffect(effect)
                                        }

                                        override fun onLoadCleared(placeholder: Drawable?) {}
                                    })
                            }
                        } else {
                            Palette.from(bmp).generate { palette ->
                                palette?.let {
                                    val dominantColor = it.getDominantColor(defaultColor)

                                    val hsv = FloatArray(3)
                                    Color.colorToHSV(dominantColor, hsv)
                                    hsv[1] = (hsv[1] * 0.7f).coerceAtMost(1.0f)
                                    hsv[2] = (hsv[2] + 0.1f).coerceAtMost(1.0f)
                                    val smoothColor = Color.HSVToColor(hsv)

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
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    imageView.setImageDrawable(placeholder)
                }
            })
    }

    private fun drawableToBitmap(drawable: PictureDrawable): Bitmap {
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPicture(drawable.picture)
        return bitmap
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
