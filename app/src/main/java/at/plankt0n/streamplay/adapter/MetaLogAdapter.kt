package at.plankt0n.streamplay.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.MetaLogEntry

class MetaLogAdapter(
    private val onUrlClick: (String) -> Unit
) : RecyclerView.Adapter<MetaLogAdapter.ViewHolder>() {

    private val items = mutableListOf<MetaLogEntry>()

    fun setItems(newItems: List<MetaLogEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItems(): List<MetaLogEntry> = items.toList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val line1: TextView = view.findViewById(R.id.log_line1)
        val line2: TextView = view.findViewById(R.id.log_line2)
        val button: ImageButton = view.findViewById(R.id.log_open_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meta_log, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.line1.text = "${item.formattedTime()} - ${item.station}"
        holder.line2.text = "${item.title} - ${item.artist}"
        if (item.manual) {
            holder.itemView.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.highlight)
            )
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }
        if (!item.url.isNullOrBlank()) {
            holder.button.visibility = View.VISIBLE
            holder.button.setOnClickListener { onUrlClick(item.url!!) }
        } else {
            holder.button.visibility = View.GONE
        }
    }
}
