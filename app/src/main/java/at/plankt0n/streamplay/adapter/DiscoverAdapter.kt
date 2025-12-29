package at.plankt0n.streamplay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.search.RadioBrowserResult
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class DiscoverAdapter(
    private val items: MutableList<RadioBrowserResult>,
    private val onClick: (RadioBrowserResult) -> Unit
) : RecyclerView.Adapter<DiscoverAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.tile_image)
        val name: TextView = view.findViewById(R.id.tile_name)
        val genre: TextView = view.findViewById(R.id.tile_genre)
        val bitrate: TextView = view.findViewById(R.id.tile_bitrate)
        val votes: TextView = view.findViewById(R.id.tile_votes)
        val voteIcon: ImageView = view.findViewById(R.id.tile_vote_icon)
        val country: TextView = view.findViewById(R.id.tile_country)
        val divider: View = view.findViewById(R.id.tile_divider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station_tile, parent, false)
        val holder = ViewHolder(view)

        // Click-Listener einmal setzen statt bei jedem Bind (Memory Leak Fix)
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < items.size) {
                onClick(items[pos])
            }
        }

        return holder
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // Station name
        holder.name.text = item.name

        // Station image
        Glide.with(holder.image.context)
            .load(item.favicon)
            .placeholder(R.drawable.ic_placeholder_logo)
            .error(R.drawable.ic_stationcover_placeholder)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(holder.image)

        // Genre tag
        val firstTag = item.getFirstTag()
        if (firstTag.isNotEmpty()) {
            holder.genre.text = firstTag
            holder.genre.visibility = View.VISIBLE
        } else {
            holder.genre.visibility = View.GONE
        }

        // Bitrate badge
        val bitrateText = item.formatBitrate()
        if (bitrateText.isNotEmpty()) {
            holder.bitrate.text = bitrateText
            holder.bitrate.visibility = View.VISIBLE
        } else {
            holder.bitrate.visibility = View.GONE
        }

        // Votes
        if (item.votes > 0) {
            holder.votes.text = item.formatVotes()
            holder.votes.visibility = View.VISIBLE
            holder.voteIcon.visibility = View.VISIBLE
        } else {
            holder.votes.visibility = View.GONE
            holder.voteIcon.visibility = View.GONE
        }

        // Country
        if (item.countrycode.isNotEmpty()) {
            holder.country.text = item.countrycode
            holder.country.visibility = View.VISIBLE
            // Only show divider if we have both votes and country
            holder.divider.visibility = if (item.votes > 0) View.VISIBLE else View.GONE
        } else {
            holder.country.visibility = View.GONE
            holder.divider.visibility = View.GONE
        }

    }

    fun updateItems(newItems: List<RadioBrowserResult>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
