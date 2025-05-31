package at.plankt0n.streamplay.adapter

import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.helper.MediaServiceController
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.google.android.material.imageview.ShapeableImageView

class CoverPageAdapter(
    private val mediaServiceController: MediaServiceController
) : RecyclerView.Adapter<CoverPageAdapter.CoverViewHolder>() {

    private val mediaItems = mediaServiceController.getCurrentPlaylist()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoverViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_player_coverpagview, parent, false)
        return CoverViewHolder(view)
    }

    override fun onBindViewHolder(holder: CoverViewHolder, position: Int) {
        val item = mediaItems[position]

        // Lade das Bild als Bitmap, um die Farbe extrahieren zu können
        Glide.with(holder.itemView)
            .asBitmap()
            .load(item.iconURL)
            .placeholder(R.drawable.ic_placeholder_logo)
            .error(R.drawable.ic_stationcover_placeholder)
            .into(object : BitmapImageViewTarget(holder.coverImage) {
                override fun setResource(resource: Bitmap?) {
                    super.setResource(resource)
                    resource?.let { bitmap ->
                        // Farbpalette extrahieren
                        Palette.from(bitmap).generate { palette ->
                            palette?.let {
                                val dominantColor = it.getDominantColor(
                                    holder.itemView.context.getColor(R.color.default_background)
                                )
                                // Farbe leicht aufhellen
                                val hsv = FloatArray(3)
                                Color.colorToHSV(dominantColor, hsv)
                                hsv[2] = (hsv[2] + 0.2f).coerceAtMost(1.0f) // Aufhellen
                                val brightColor = Color.HSVToColor(hsv)
                                holder.itemView.setBackgroundColor(brightColor)
                            }
                        }
                    }
                }
            })
    }

    override fun getItemCount(): Int = mediaItems.size

    inner class CoverViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val coverImage: ShapeableImageView = itemView.findViewById(R.id.cover_image)
    }
}
