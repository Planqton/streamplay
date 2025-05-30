// Datei: at.plankt0n.streamplay.adapter.PageViewMediaItemAdapter.kt
package at.plankt0n.streamplay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.data.ShortcutItem
import at.plankt0n.streamplay.helper.MediaServiceController

class PageViewMediaItemAdapter(
    private val mediaItems: List<StationItem>,
    private val onMenuClicked: (StationItem, Int) -> Unit,
    private val mediaServiceController: MediaServiceController
) : RecyclerView.Adapter<PageViewMediaItemAdapter.MediaItemViewHolder>() {

    // Aktueller Status – ob der Player spielt oder nicht
    private var isPlaying: Boolean = false

    // Wird vom Fragment aufgerufen, wenn sich der Status ändert
    fun updatePlayState(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.pageview_media_item, parent, false)
        return MediaItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaItemViewHolder, position: Int) {
        val item = mediaItems[position]
        holder.bind(item)

        // Immer richtigen Play/Pause-Status anzeigen
        holder.playPauseButton.setImageResource(
            if (isPlaying) R.drawable.ic_button_pause else R.drawable.ic_button_play
        )

        // Buttons steuern direkt den MediaServiceController
        holder.menuButton.setOnClickListener {
            onMenuClicked(item, position)
        }

        holder.playPauseButton.setOnClickListener {
            mediaServiceController.togglePlayPause()
        }

        holder.backButton.setOnClickListener {
            mediaServiceController.skipToPrevious()
        }

        holder.forwardButton.setOnClickListener {
            mediaServiceController.skipToNext()
        }
    }

    override fun getItemCount(): Int = mediaItems.size

    inner class MediaItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverImage: ImageView = itemView.findViewById(R.id.cover_image)
        private val primaryText: TextView = itemView.findViewById(R.id.meta_overlay_Artist)
        private val secondaryText: TextView = itemView.findViewById(R.id.meta_overlay_Title)
        private val shortcutRecyclerView: RecyclerView =
            itemView.findViewById(R.id.shortcut_recycler_view)
        val playPauseButton: ImageButton = itemView.findViewById(R.id.button_play_pause)
        val backButton: ImageButton = itemView.findViewById(R.id.button_back)
        val forwardButton: ImageButton = itemView.findViewById(R.id.button_forward)
        val menuButton: ImageButton = itemView.findViewById(R.id.button_menu)

        fun bind(mediaItem: StationItem) {
            primaryText.text = mediaItem.stationName

            // Dummy-Shortcuts
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
