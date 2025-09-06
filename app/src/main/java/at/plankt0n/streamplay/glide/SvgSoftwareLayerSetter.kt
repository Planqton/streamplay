package at.plankt0n.streamplay.glide

import android.graphics.drawable.PictureDrawable
import android.view.View
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.target.Target

/**
 * Ensures that SVGs are rendered using software layer to avoid issues with hardware acceleration.
 */
class SvgSoftwareLayerSetter : RequestListener<PictureDrawable> {
    override fun onLoadFailed(
        e: GlideException?,
        model: Any?,
        target: Target<PictureDrawable>,
        isFirstResource: Boolean
    ): Boolean {
        if (target is ImageViewTarget<*>) {
            val view = target.view
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        return false
    }

    override fun onResourceReady(
        resource: PictureDrawable,
        model: Any?,
        target: Target<PictureDrawable>,
        dataSource: DataSource,
        isFirstResource: Boolean
    ): Boolean {
        if (target is ImageViewTarget<*>) {
            val view = target.view
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        return false
    }
}

