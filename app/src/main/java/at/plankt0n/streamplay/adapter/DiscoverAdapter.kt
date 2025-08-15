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
import at.plankt0n.streamplay.svg.SvgSoftwareLayerSetter

class DiscoverAdapter(
    private val items: List<StationItem>,
    private val onClick: (StationItem) -> Unit
) : RecyclerView.Adapter<DiscoverAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.tile_image)
        val text: TextView = view.findViewById(R.id.tile_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station_tile, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.text.text = item.stationName
        Glide.with(holder.image.context)
            .load(item.iconURL)
            .placeholder(R.drawable.ic_placeholder_logo)
            .error(R.drawable.ic_stationcover_placeholder)
            .listener(SvgSoftwareLayerSetter())
            .into(holder.image)
        holder.itemView.setOnClickListener { onClick(item) }
    }
}
