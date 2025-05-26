package at.plankt0n.streamplay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.MediaItem
import at.plankt0n.streamplay.data.ShortcutItem

class MediaItemAdapter(private val mediaItems: List<MediaItem>) :
    RecyclerView.Adapter<MediaItemAdapter.MediaItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.media_item_view, parent, false)
        return MediaItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaItemViewHolder, position: Int) {
        val item = mediaItems[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = mediaItems.size

    inner class MediaItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverImage: ImageView = itemView.findViewById(R.id.cover_image)
        private val primaryText: TextView = itemView.findViewById(R.id.overlay_text_primary)
        private val secondaryText: TextView = itemView.findViewById(R.id.overlay_text_secondary)
        private val shortcutRecyclerView: RecyclerView =
            itemView.findViewById(R.id.shortcut_recycler_view)

        fun bind(mediaItem: MediaItem) {
            coverImage.setImageResource(mediaItem.logoResId)
            primaryText.text = mediaItem.stationName
            secondaryText.text = mediaItem.currentTitle

            // Dummy Shortcuts für jede MediaItem-Seite
            val dummyShortcuts = listOf(
                ShortcutItem(R.drawable.ic_placeholder_logo, "Shortcut A"),
                ShortcutItem(R.drawable.ic_placeholder_logo, "Shortcut B"),
                ShortcutItem(R.drawable.ic_placeholder_logo, "Shortcut C"),
                ShortcutItem(R.drawable.ic_placeholder_logo, "Shortcut D"),
                ShortcutItem(R.drawable.ic_placeholder_logo, "Shortcut E")
            )
            val shortcutAdapter = ShortcutAdapter(dummyShortcuts)

            shortcutRecyclerView.layoutManager =
                LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
            shortcutRecyclerView.adapter = shortcutAdapter
        }
    }
}
