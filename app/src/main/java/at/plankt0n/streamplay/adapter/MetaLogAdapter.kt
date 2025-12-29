package at.plankt0n.streamplay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.MetaLogEntry
import com.bumptech.glide.Glide

class MetaLogAdapter(
    private val onUrlClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_LOG_ITEM = 1
    }

    private val displayItems = mutableListOf<DisplayItem>()

    sealed class DisplayItem {
        data class DateHeader(val dateKey: String, val displayText: String) : DisplayItem()
        data class LogItem(val entry: MetaLogEntry) : DisplayItem()
    }

    fun setItems(newItems: List<MetaLogEntry>) {
        displayItems.clear()

        if (newItems.isEmpty()) {
            notifyDataSetChanged()
            return
        }

        var lastDateKey = ""
        for (entry in newItems) {
            val dateKey = entry.getDateKey()
            if (dateKey != lastDateKey) {
                val displayText = when {
                    entry.isToday() -> ""  // Will use string resource in ViewHolder
                    entry.isYesterday() -> ""  // Will use string resource in ViewHolder
                    else -> entry.formattedDate()
                }
                displayItems.add(DisplayItem.DateHeader(dateKey, displayText))
                lastDateKey = dateKey
            }
            displayItems.add(DisplayItem.LogItem(entry))
        }

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is DisplayItem.DateHeader -> TYPE_DATE_HEADER
            is DisplayItem.LogItem -> TYPE_LOG_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_meta_log_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_meta_log, parent, false)
                val holder = LogItemViewHolder(view)
                // Click-Listener einmal setzen statt bei jedem Bind (Memory Leak Fix)
                holder.spotifyButton.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION && pos < displayItems.size) {
                        val item = displayItems[pos]
                        if (item is DisplayItem.LogItem && !item.entry.url.isNullOrBlank()) {
                            onUrlClick(item.entry.url)
                        }
                    }
                }
                holder
            }
        }
    }

    override fun getItemCount(): Int = displayItems.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is DisplayItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item)
            is DisplayItem.LogItem -> (holder as LogItemViewHolder).bind(item.entry)
        }
    }

    inner class DateHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateText: TextView = view.findViewById(R.id.dateHeaderText)

        fun bind(item: DisplayItem.DateHeader) {
            // Check if adapter position is valid
            val currentPos = adapterPosition
            if (currentPos == RecyclerView.NO_POSITION) {
                dateText.text = item.displayText
                return
            }

            // Get the entry from the next item to determine if today/yesterday
            val nextPosition = currentPos + 1
            if (nextPosition < displayItems.size) {
                val nextItem = displayItems[nextPosition]
                if (nextItem is DisplayItem.LogItem) {
                    dateText.text = when {
                        nextItem.entry.isToday() -> itemView.context.getString(R.string.meta_log_date_today)
                        nextItem.entry.isYesterday() -> itemView.context.getString(R.string.meta_log_date_yesterday)
                        else -> item.displayText
                    }
                    return
                }
            }
            dateText.text = item.displayText
        }
    }

    inner class LogItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val coverImage: ImageView = view.findViewById(R.id.logCoverImage)
        private val titleText: TextView = view.findViewById(R.id.logTitle)
        private val artistText: TextView = view.findViewById(R.id.logArtist)
        private val stationText: TextView = view.findViewById(R.id.logStation)
        private val timeText: TextView = view.findViewById(R.id.logTime)
        private val savedIcon: ImageView = view.findViewById(R.id.logSavedIcon)
        val spotifyButton: ImageButton = view.findViewById(R.id.logSpotifyButton)

        fun bind(entry: MetaLogEntry) {
            titleText.text = entry.title.ifBlank { itemView.context.getString(R.string.unknown_title) }
            artistText.text = entry.artist.ifBlank { itemView.context.getString(R.string.unknown_artist) }
            stationText.text = entry.station
            timeText.text = entry.formattedTime()

            // Show saved indicator for manual entries
            savedIcon.visibility = if (entry.manual) View.VISIBLE else View.GONE

            // Load cover image
            if (!entry.coverUrl.isNullOrBlank()) {
                Glide.with(itemView.context)
                    .load(entry.coverUrl)
                    .placeholder(R.drawable.ic_placeholder_logo)
                    .error(R.drawable.ic_placeholder_logo)
                    .centerCrop()
                    .into(coverImage)
            } else {
                coverImage.setImageResource(R.drawable.ic_placeholder_logo)
            }

            // Spotify button visibility (Listener ist schon in onCreateViewHolder gesetzt)
            spotifyButton.visibility = if (!entry.url.isNullOrBlank()) View.VISIBLE else View.GONE
        }
    }
}
