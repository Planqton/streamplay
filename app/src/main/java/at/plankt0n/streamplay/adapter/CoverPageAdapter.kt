// Datei: at/plankt0n/streamplay/adapter/CoverPageAdapter.kt
package at.plankt0n.streamplay.adapter

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.MediaServiceController
import at.plankt0n.streamplay.helper.LiveCoverHelper
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.google.android.material.imageview.ShapeableImageView

class CoverPageAdapter(
    private val mediaServiceController: MediaServiceController,
    var backgroundEffect: LiveCoverHelper.BackgroundEffect
) : RecyclerView.Adapter<CoverPageAdapter.CoverViewHolder>() {

    val mediaItems: List<StationItem> = mediaServiceController.getCurrentPlaylist()

    inner class CoverViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val coverImage: ShapeableImageView = itemView.findViewById(R.id.cover_image)
        var lastColor: Int? = null
        var lastEffect: LiveCoverHelper.BackgroundEffect? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoverViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_player_coverpagview, parent, false)
        return CoverViewHolder(view)
    }

    override fun onBindViewHolder(holder: CoverViewHolder, position: Int) {
        val item = mediaItems[position]

        Glide.with(holder.itemView)
            .asBitmap()
            .load(item.iconURL)
            .placeholder(R.drawable.ic_placeholder_logo)
            .error(R.drawable.ic_stationcover_placeholder)
            .into(object : BitmapImageViewTarget(holder.coverImage) {
                override fun setResource(resource: Bitmap?) {
                    super.setResource(resource)
                    resource?.let { bitmap ->
                        Palette.from(bitmap).generate { palette ->
                            palette?.let {
                                val dominantColor = it.getDominantColor(
                                    holder.itemView.context.getColor(R.color.default_background)
                                )

                                // Farbton sanfter machen (leichter entsättigen, Gelb-Töne neutralisieren)
                                val hsv = FloatArray(3)
                                Color.colorToHSV(dominantColor, hsv)
                                hsv[1] = (hsv[1] * 0.7f).coerceAtMost(1.0f) // Sättigung reduzieren
                                hsv[2] = (hsv[2] + 0.1f).coerceAtMost(1.0f) // Helligkeit leicht erhöhen
                                val smoothColor = Color.HSVToColor(hsv)

                                // Nur animieren, wenn Farbe oder Effekt sich ändert
                                if (holder.lastColor != smoothColor || holder.lastEffect != backgroundEffect) {
                                    val fromColor = holder.lastColor
                                        ?: holder.itemView.context.getColor(R.color.default_background)
                                    val animator = ValueAnimator.ofArgb(fromColor, smoothColor)
                                    animator.duration = 400
                                    animator.addUpdateListener { a ->
                                        val color = a.animatedValue as Int
                                        val gradient = LiveCoverHelper.createGradient(color, backgroundEffect)
                                        holder.itemView.background = gradient
                                    }
                                    animator.start()
                                    holder.lastColor = smoothColor
                                    holder.lastEffect = backgroundEffect
                                }
                            }
                        }
                    }
                }
            })
    }

    override fun getItemCount(): Int = mediaItems.size
}
