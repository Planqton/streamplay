package at.plankt0n.streamplay.helper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import at.plankt0n.streamplay.glide.SvgSoftwareLayerSetter

/**
 * Loads images into an [ImageView] and transparently handles SVG sources.
 * Optionally returns the loaded [Bitmap] via [onBitmapReady].
 */
fun ImageView.loadUrl(
    url: String,
    placeholder: Int? = null,
    error: Int? = null,
    onBitmapReady: ((Bitmap?) -> Unit)? = null
) {
    if (url.lowercase().endsWith(".svg")) {
        val builder = Glide.with(this)
            .`as`(PictureDrawable::class.java)
            .listener(SvgSoftwareLayerSetter())
            .load(url)

        placeholder?.let { builder.placeholder(it) }
        error?.let { builder.error(it) }

        builder.into(object : CustomTarget<PictureDrawable>() {
            override fun onResourceReady(
                resource: PictureDrawable,
                transition: Transition<in PictureDrawable>?
            ) {
                this@loadUrl.setImageDrawable(resource)
                onBitmapReady?.invoke(resource.toBitmap())
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                this@loadUrl.setImageDrawable(placeholder)
            }
        })
    } else {
        val builder = Glide.with(this)
            .asBitmap()
            .load(url)

        placeholder?.let { builder.placeholder(it) }
        error?.let { builder.error(it) }

        builder.into(object : BitmapImageViewTarget(this) {
            override fun setResource(resource: Bitmap?) {
                super.setResource(resource)
                onBitmapReady?.invoke(resource)
            }
        })
    }
}

/** Converts a [PictureDrawable] to a [Bitmap]. */
fun PictureDrawable.toBitmap(): Bitmap {
    val picture = this.picture
    val bitmap = Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawPicture(picture)
    return bitmap
}

