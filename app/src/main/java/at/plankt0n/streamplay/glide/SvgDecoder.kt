package at.plankt0n.streamplay.glide

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.SimpleResource
import com.caverock.androidsvg.SVG
import java.io.InputStream

/**
 * Decodes an [InputStream] into an [SVG] object using the AndroidSVG library.
 */
class SvgDecoder : ResourceDecoder<InputStream, SVG> {

    override fun handles(source: InputStream, options: Options): Boolean = true

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<SVG>? {
        return try {
            val svg = SVG.getFromInputStream(source)
            SimpleResource(svg)
        } catch (_: Exception) {
            null
        }
    }
}

