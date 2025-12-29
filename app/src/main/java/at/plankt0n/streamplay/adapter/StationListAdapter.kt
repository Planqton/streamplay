package at.plankt0n.streamplay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.PopupMenu
import com.bumptech.glide.Glide
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.PreferencesHelper
import com.google.android.material.textfield.TextInputEditText

class StationListAdapter(
    private val stationList: MutableList<StationItem>,
    private val startDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onDataChanged: () -> Unit,
    private val onPlayClick: (Int) -> Unit,
    private val onPinToHome: (StationItem) -> Unit,
    private val onDeleteStation: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<StationListAdapter.ViewHolder>() {

    private var editingPosition: Int = -1
    private var currentPlayingIndex: Int = -1

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Normale Ansicht
        val textName: TextView = itemView.findViewById(R.id.textStationName)
        val textUrl: TextView = itemView.findViewById(R.id.textStreamUrl)
        val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
        val playButton: ImageButton = itemView.findViewById(R.id.buttonPlayStation)
        val menuButton: ImageButton = itemView.findViewById(R.id.buttonStationMenu)

        // Editieransicht
        val editLayout: LinearLayout = itemView.findViewById(R.id.editLayout)
        val editName: TextInputEditText = itemView.findViewById(R.id.editTextStationName)
        val editUrl: TextInputEditText = itemView.findViewById(R.id.editTextStationUrl)
        val editIcon: TextInputEditText = itemView.findViewById(R.id.editTextStationIcon)
        val buttonSave: View = itemView.findViewById(R.id.buttonSaveChangesItem)
        val buttonCancel: View = itemView.findViewById(R.id.buttonCancelChangesItem)

        val normalLayout: ViewGroup = itemView.findViewById(R.id.stationItemContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station_editable, parent, false)
        val holder = ViewHolder(view)

        // Play Button - Klick zum Abspielen
        holder.playButton.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onPlayClick(pos)
        }

        // Drag Handle
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                startDrag(holder)
            }
            false
        }

        // Menü Button - PopupMenu anzeigen
        holder.menuButton.setOnClickListener { view ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < stationList.size) {
                showPopupMenu(view, pos)
            }
        }

        // Auf Item tippen öffnet auch das Menü (intuitiver)
        holder.normalLayout.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < stationList.size) {
                showPopupMenu(holder.menuButton, pos)
            }
        }

        // Save Button
        holder.buttonSave.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && pos < stationList.size) {
                val updatedStation = stationList[pos].copy(
                    stationName = holder.editName.text.toString().trim().ifEmpty { stationList[pos].stationName },
                    streamURL = holder.editUrl.text.toString().trim().ifEmpty { stationList[pos].streamURL },
                    iconURL = holder.editIcon.text.toString().trim()
                )
                stationList[pos] = updatedStation
                PreferencesHelper.saveStations(holder.itemView.context, stationList)
                editingPosition = -1
                notifyItemChanged(pos)
                onDataChanged()
            }
        }

        // Cancel Button
        holder.buttonCancel.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                editingPosition = -1
                notifyItemChanged(pos)
            }
        }

        return holder
    }

    private fun showPopupMenu(anchorView: View, position: Int) {
        val context = anchorView.context
        val station = stationList[position]

        val popup = PopupMenu(context, anchorView)
        popup.menuInflater.inflate(R.menu.menu_station_item, popup.menu)

        // Löschen-Option ausblenden wenn nur noch eine Station
        if (stationList.size <= 1) {
            popup.menu.findItem(R.id.menu_delete)?.isVisible = false
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_edit -> {
                    // Edit-Modus aktivieren
                    val oldEditingPos = editingPosition
                    editingPosition = position
                    if (oldEditingPos >= 0) notifyItemChanged(oldEditingPos)
                    notifyItemChanged(position)
                    true
                }
                R.id.menu_delete -> {
                    // Lösch-Bestätigung anzeigen
                    showDeleteConfirmation(context, station, position)
                    true
                }
                R.id.menu_pin_home -> {
                    onPinToHome(station)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun showDeleteConfirmation(context: android.content.Context, station: StationItem, position: Int) {
        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.confirm_delete_station)
            .setMessage(context.getString(R.string.confirm_delete_station_message, station.stationName))
            .setPositiveButton(R.string.delete_stream) { _, _ ->
                // Callback an Fragment oder direkt löschen
                onDeleteStation?.invoke(position)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun getItemCount(): Int = stationList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Bounds check to prevent IndexOutOfBoundsException
        if (position < 0 || position >= stationList.size) return
        val station = stationList[position]

        // Normale Ansicht
        holder.textName.text = station.stationName
        holder.textUrl.text = station.streamURL

        // Editieransicht füllen
        holder.editName.setText(station.stationName)
        holder.editUrl.setText(station.streamURL)
        holder.editIcon.setText(station.iconURL)

        // Sichtbarkeiten
        val isEditing = (position == editingPosition)
        holder.normalLayout.visibility = if (isEditing) View.GONE else View.VISIBLE
        holder.editLayout.visibility = if (isEditing) View.VISIBLE else View.GONE

        // Station Icon laden
        Glide.with(holder.playButton.context)
            .load(station.iconURL)
            .placeholder(R.drawable.ic_stationcover_placeholder)
            .error(R.drawable.ic_stationcover_placeholder)
            .fallback(R.drawable.ic_stationcover_placeholder)
            .into(holder.playButton)

        // Aktuell spielender Sender hervorheben
        val context = holder.itemView.context
        if (position == currentPlayingIndex) {
            holder.itemView.setBackgroundColor(context.getColor(R.color.highlight))
        } else {
            holder.itemView.setBackgroundColor(context.getColor(android.R.color.transparent))
        }
    }

    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        // Bounds check to prevent IndexOutOfBoundsException
        if (from < 0 || from >= stationList.size || to < 0 || to >= stationList.size) return
        java.util.Collections.swap(stationList, from, to)
        notifyItemMoved(from, to)
        onDataChanged()
    }

    fun setCurrentPlayingIndex(index: Int) {
        val oldIndex = currentPlayingIndex
        currentPlayingIndex = index
        // Use granular updates for better performance
        if (oldIndex >= 0 && oldIndex < stationList.size) {
            notifyItemChanged(oldIndex)
        }
        if (index >= 0 && index < stationList.size) {
            notifyItemChanged(index)
        }
    }

    fun closeEditMode() {
        if (editingPosition >= 0) {
            val pos = editingPosition
            editingPosition = -1
            notifyItemChanged(pos)
        }
    }
}
