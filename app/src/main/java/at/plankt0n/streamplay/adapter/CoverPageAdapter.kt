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

    // Speichert die aktuell angezeigte Cover-URL pro Position (Metadata oder Station)
    private val currentCoverUrls = mutableMapOf<Int, String>()

    fun updateMediaItems() {
        mediaItems = mediaServiceController.getCurrentPlaylist()
        currentCoverUrls.clear()
        notifyDataSetChanged()
    }

    fun setCoverUrlForPosition(position: Int, url: String) {
        currentCoverUrls[position] = url
    }

    fun getCoverUrlForPosition(position: Int): String? {
        return currentCoverUrls[position]
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
        // Verwende gespeicherte Cover-URL falls vorhanden, sonst Station-Icon
        val coverUrl = currentCoverUrls[position]?.takeIf { it.isNotBlank() }
            ?: item.iconURL.takeIf { it.isNotBlank() }

        // Falls keine URL vorhanden, zeige Placeholder direkt
        if (coverUrl == null) {
            holder.coverImage.setImageResource(R.drawable.ic_placeholder_logo)
            holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.default_background))
            return
        }

        LiveCoverHelper.loadCoverWithBackground(
            context = holder.itemView.context,
            imageUrl = coverUrl,
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
