package at.plankt0n.streamplay.svg

import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.widget.ImageView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.target.Target

class SvgSoftwareLayerSetter : RequestListener<Drawable> {
    override fun onLoadFailed(
        e: GlideException?,
        model: Any?,
        target: Target<Drawable>?,
        isFirstResource: Boolean
    ): Boolean {
        val view = (target as? ImageViewTarget<*>)?.view
        view?.setLayerType(ImageView.LAYER_TYPE_NONE, null)
        return false
    }

    override fun onResourceReady(
        resource: Drawable,
        model: Any?,
        target: Target<Drawable>?,
        dataSource: DataSource?,
        isFirstResource: Boolean
    ): Boolean {
        val view = (target as? ImageViewTarget<*>)?.view
        if (resource is PictureDrawable) {
            view?.setLayerType(ImageView.LAYER_TYPE_SOFTWARE, null)
        } else {
            view?.setLayerType(ImageView.LAYER_TYPE_NONE, null)
        }
        return false
    }
}
