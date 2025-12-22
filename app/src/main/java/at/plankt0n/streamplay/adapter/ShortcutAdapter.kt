package at.plankt0n.streamplay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.ShortcutItem
import com.bumptech.glide.Glide

class ShortcutAdapter(
    private val onClick: (ShortcutItem) -> Unit
) : RecyclerView.Adapter<ShortcutAdapter.ViewHolder>() {

    private val items = mutableListOf<ShortcutItem>()

    var selectedIndex: Int = -1
        set(value) {
            val oldIndex = field
            field = value
            if (oldIndex >= 0 && oldIndex < items.size) notifyItemChanged(oldIndex)
            if (value >= 0 && value < items.size) notifyItemChanged(value)
        }

    fun setItems(newItems: List<ShortcutItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconImageView: ImageView = view.findViewById(R.id.shortcut_icon)
        val labelTextView: TextView = view.findViewById(R.id.shortcut_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.shortcut_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.labelTextView.text = item.label
        val source = item.iconUrl.takeIf { it.isNotBlank() }
        Glide.with(holder.itemView)
            .load(source)
            .placeholder(R.drawable.ic_placeholder_logo)
            .error(R.drawable.ic_radio)
            .fallback(R.drawable.ic_radio)
            .into(holder.iconImageView)

        holder.itemView.setOnClickListener { onClick(item) }

        // Ausgewähltes Item vergrößern
        val isSelected = position == selectedIndex
        val scale = if (isSelected) 1.2f else 1.0f
        holder.itemView.scaleX = scale
        holder.itemView.scaleY = scale
    }
}
