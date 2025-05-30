package at.plankt0n.streamplay.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.StationItem

class StationListAdapter(
    private val stationList: List<StationItem>,
    private val onEdit: (StationItem) -> Unit,
    private val onDelete: (StationItem) -> Unit
) : RecyclerView.Adapter<StationListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textName: TextView = itemView.findViewById(R.id.textStationName)
        val textUrl: TextView = itemView.findViewById(R.id.textStreamUrl)
        val buttonEdit: Button = itemView.findViewById(R.id.buttonEdit)
        val buttonDelete: Button = itemView.findViewById(R.id.buttonDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = stationList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val station = stationList[position]
        holder.textName.text = station.stationName
        holder.textUrl.text = station.streamURL

        holder.buttonEdit.setOnClickListener {
            onEdit(station)
        }

        holder.buttonDelete.setOnClickListener {
            onDelete(station)
        }
    }
}
