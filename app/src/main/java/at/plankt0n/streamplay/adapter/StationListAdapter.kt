package at.plankt0n.streamplay.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import at.plankt0n.streamplay.helper.loadUrl
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.PreferencesHelper

class StationListAdapter(
    private val stationList: MutableList<StationItem>,
    private val startDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onDataChanged: () -> Unit,
    private val onPlayClick: (Int) -> Unit,
    private val onPinToHome: (StationItem) -> Unit
) : RecyclerView.Adapter<StationListAdapter.ViewHolder>() {

    private var editingPosition: Int = -1
    private var currentPlayingIndex: Int = -1

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Normale Ansicht
        val textName: TextView = itemView.findViewById(R.id.textStationName)
        val textUrl: TextView = itemView.findViewById(R.id.textStreamUrl)
        val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
        val playButton: ImageButton = itemView.findViewById(R.id.buttonPlayStation)

        // Editieransicht
        val editLayout: LinearLayout = itemView.findViewById(R.id.editLayout)
        val editName: EditText = itemView.findViewById(R.id.editTextStationName)
        val editUrl: EditText = itemView.findViewById(R.id.editTextStationUrl)
        val editIcon: EditText = itemView.findViewById(R.id.editTextStationIcon)
        val buttonSave: Button = itemView.findViewById(R.id.buttonSaveChangesItem)
        val buttonCancel: Button = itemView.findViewById(R.id.buttonCancelChangesItem)

        val normalLayout: ViewGroup = itemView.findViewById(R.id.stationItemContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station_editable, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = stationList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
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

        holder.playButton.visibility = if (isEditing) View.GONE else View.VISIBLE
        holder.playButton.setOnClickListener { onPlayClick(position) }
        holder.playButton.setOnLongClickListener {
            onPinToHome(station)
            true
        }

        holder.playButton.loadUrl(
            station.iconURL,
            placeholder = R.drawable.ic_stationcover_placeholder,
            error = R.drawable.ic_stationcover_placeholder
        )

        val context = holder.itemView.context
        if (position == currentPlayingIndex) {
            holder.itemView.setBackgroundColor(context.getColor(R.color.highlight))
        } else {
            holder.itemView.setBackgroundColor(context.getColor(android.R.color.transparent))
        }

        // Langes Drücken: In den Bearbeitungsmodus wechseln
        holder.itemView.setOnLongClickListener {
            editingPosition = if (isEditing) -1 else position
            notifyDataSetChanged()
            true
        }

        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                startDrag(holder)
            }
            false
        }

        // Speichern
        holder.buttonSave.setOnClickListener {
            val updatedStation = station.copy(
                stationName = holder.editName.text.toString(),
                streamURL = holder.editUrl.text.toString(),
                iconURL = holder.editIcon.text.toString()
            )
            stationList[position] = updatedStation
            PreferencesHelper.saveStations(holder.itemView.context, stationList)
            editingPosition = -1
            notifyDataSetChanged()
            onDataChanged()
        }

        // Abbrechen
        holder.buttonCancel.setOnClickListener {
            editingPosition = -1
            notifyDataSetChanged()
        }
    }

    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        java.util.Collections.swap(stationList, from, to)
        notifyItemMoved(from, to)
        onDataChanged()
    }

    fun setCurrentPlayingIndex(index: Int) {
        currentPlayingIndex = index
        notifyDataSetChanged()
    }
}
