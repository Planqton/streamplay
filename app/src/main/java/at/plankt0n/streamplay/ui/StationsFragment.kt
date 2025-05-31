package at.plankt0n.streamplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.PreferencesHelper
import java.util.*

class StationsFragment : Fragment() {

    private lateinit var stationList: MutableList<StationItem>
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StationListAdapter
    private lateinit var topbarBackButton: ImageButton
    private lateinit var topbarTitle: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_stations, container, false)

        topbarBackButton = view.findViewById(R.id.arrow_back)
        topbarTitle = view.findViewById(R.id.topbar_title)

        // Direkt statisch PreferencesHelper verwenden
        stationList = PreferencesHelper.getStations(requireContext()).toMutableList()

        recyclerView = view.findViewById(R.id.recyclerViewStations)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = StationListAdapter(stationList)
        recyclerView.adapter = adapter

        // Swipe-to-Delete
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                stationList.removeAt(position)
                PreferencesHelper.saveStations(requireContext(), stationList)
                adapter.notifyItemRemoved(position)
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Hinzufügen
        view.findViewById<View>(R.id.buttonAddStation).setOnClickListener {
            showAddDialog()
        }

        // Zurück-Button
        topbarBackButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return view
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_station, null)
        val editName = dialogView.findViewById<EditText>(R.id.editStationName)
        val editUrl = dialogView.findViewById<EditText>(R.id.editStreamUrl)
        val editIcon = dialogView.findViewById<EditText>(R.id.editIconUrl)

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Station")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val station = StationItem(
                    uuid = UUID.randomUUID().toString(),
                    stationName = editName.text.toString(),
                    streamURL = editUrl.text.toString(),
                    iconURL = editIcon.text.toString(),
                )
                stationList.add(station)
                PreferencesHelper.saveStations(requireContext(), stationList)
                adapter.notifyItemInserted(stationList.size - 1)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
