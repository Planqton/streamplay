
package at.plankt0n.streamplay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.StationItem
import com.bumptech.glide.Glide

class SearchResultAdapter(
    private val searchResults: List<StationItem>,
    private val onClick: (StationItem) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageLogo: ImageView = view.findViewById(R.id.imageLogo)
        val textStationName: TextView = view.findViewById(R.id.textStationName)
        val textStreamUrl: TextView = view.findViewById(R.id.textStreamUrl)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        val holder = ViewHolder(view)

        // Click-Listener einmal setzen statt bei jedem Bind (Memory Leak Fix)
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < searchResults.size) {
                onClick(searchResults[pos])
            }
        }

        return holder
    }

    override fun getItemCount(): Int = searchResults.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Bounds-Check hinzugefügt
        if (position !in searchResults.indices) return

        val result = searchResults[position]
        holder.textStationName.text = result.stationName
        holder.textStreamUrl.text = result.streamURL

        Glide.with(holder.imageLogo.context)
            .load(result.iconURL)
            .placeholder(R.drawable.ic_stationcover_placeholder)
            .into(holder.imageLogo)
    }
}
    