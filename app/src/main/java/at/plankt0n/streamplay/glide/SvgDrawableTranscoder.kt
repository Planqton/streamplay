package at.plankt0n.streamplay.glide

import android.graphics.drawable.PictureDrawable
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.SimpleResource
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder
import com.caverock.androidsvg.SVG

/**
 * Converts an [SVG] object into a [PictureDrawable] so it can be displayed in an [ImageView].
 */
class SvgDrawableTranscoder : ResourceTranscoder<SVG, PictureDrawable> {

    override fun transcode(
        toTranscode: Resource<SVG>,
        options: Options
    ): Resource<PictureDrawable>? {
        val svg = toTranscode.get()
        val picture = svg.renderToPicture()
        val drawable = PictureDrawable(picture)
        return SimpleResource(drawable)
    }
}

