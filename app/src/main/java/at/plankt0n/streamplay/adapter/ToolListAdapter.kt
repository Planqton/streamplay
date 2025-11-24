package at.plankt0n.streamplay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.Tool

class ToolListAdapter(
    private var items: List<Tool>,
    private val onClick: (Tool) -> Unit
) : RecyclerView.Adapter<ToolListAdapter.ToolViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tool, parent, false)
        return ToolViewHolder(view)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Tool>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ToolViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val orderNumber: TextView = itemView.findViewById(R.id.text_order_number)
        private val description: TextView = itemView.findViewById(R.id.text_description)

        fun bind(item: Tool) {
            orderNumber.text = item.orderNumber
            description.text = item.description
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
