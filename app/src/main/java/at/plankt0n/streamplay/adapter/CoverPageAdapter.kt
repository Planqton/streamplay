// Datei: at/plankt0n/streamplay/adapter/CoverPageAdapter.kt
package at.plankt0n.streamplay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.MediaServiceController
import at.plankt0n.streamplay.helper.LiveCoverHelper
import com.google.android.material.imageview.ShapeableImageView

class CoverPageAdapter(
    private val mediaServiceController: MediaServiceController,
    var backgroundEffect: LiveCoverHelper.BackgroundEffect
) : RecyclerView.Adapter<CoverPageAdapter.CoverViewHolder>() {

    var mediaItems: List<StationItem> = mediaServiceController.getCurrentPlaylist()
        private set

    fun updateMediaItems() {
        mediaItems = mediaServiceController.getCurrentPlaylist()
        notifyDataSetChanged()
    }

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

        LiveCoverHelper.loadCoverWithBackground(
            context = holder.itemView.context,
            imageUrl = item.iconURL,
            imageView = holder.coverImage,
            backgroundTarget = holder.itemView,
            defaultColor = holder.itemView.context.getColor(R.color.default_background),
            lastColor = holder.lastColor,
            lastEffect = holder.lastEffect,
            effect = backgroundEffect,
            onNewColor = { holder.lastColor = it },
            onNewEffect = { holder.lastEffect = it }
        )
    }

    override fun getItemCount(): Int = mediaItems.size
}
