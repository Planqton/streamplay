package at.plankt0n.streamplay.svg

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.PictureDrawable
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.SimpleResource
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder
import com.caverock.androidsvg.SVG

class SvgBitmapTranscoder : ResourceTranscoder<SVG, Bitmap> {
    override fun transcode(toTranscode: Resource<SVG>, options: Options): Resource<Bitmap>? {
        val svg = toTranscode.get()
        val picture = svg.renderToPicture()
        val width = svg.documentWidth.toInt().takeIf { it > 0 } ?: picture.width
        val height = svg.documentHeight.toInt().takeIf { it > 0 } ?: picture.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPicture(picture)
        return SimpleResource(bitmap)
    }
}
