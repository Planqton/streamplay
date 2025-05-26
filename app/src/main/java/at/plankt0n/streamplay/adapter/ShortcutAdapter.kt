package at.plankt0n.streamplay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.ShortcutItem

class ShortcutAdapter(private val items: List<ShortcutItem>) :
    RecyclerView.Adapter<ShortcutAdapter.ShortcutViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.shortcut_item, parent, false)
        return ShortcutViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ShortcutViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.shortcut_icon)
        private val label: TextView = view.findViewById(R.id.shortcut_label)

        fun bind(item: ShortcutItem) {
            icon.setImageResource(item.iconResId)
            label.text = item.label
        }
    }
}
