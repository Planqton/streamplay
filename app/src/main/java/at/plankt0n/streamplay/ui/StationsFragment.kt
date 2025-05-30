package at.plankt0n.streamplay.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.PreferencesHelper
import java.util.UUID

class StationsFragment : Fragment() {

    private lateinit var stationList: MutableList<StationItem>
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StationListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stations, container, false)

        // Direkt statisch PreferencesHelper verwenden
        stationList = PreferencesHelper.getStations(requireContext())

        // Zurück-Button
        view.findViewById<View>(R.id.buttonBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        recyclerView = view.findViewById(R.id.recyclerViewStations)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = StationListAdapter(stationList,
            onEdit = { station -> showEditDialog(station) },
            onDelete = { station ->
                stationList.remove(station)
                PreferencesHelper.saveStations(requireContext(), stationList)
                adapter.notifyDataSetChanged()
            }
        )
        recyclerView.adapter = adapter

        view.findViewById<View>(R.id.buttonAddStation).setOnClickListener {
            showAddDialog()
        }

        return view
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_station, null)
        val editName = dialogView.findViewById<EditText>(R.id.editStationName)
        val editUrl = dialogView.findViewById<EditText>(R.id.editStreamUrl)
        val editIcon = dialogView.findViewById<EditText>(R.id.editIconUrl)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Station")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val station = StationItem(
                    uuid = UUID.randomUUID().toString(),
                    stationName = editName.text.toString(),
                    streamURL = editUrl.text.toString(),
                    iconURL = editIcon.text.toString()
                )
                stationList.add(station)
                PreferencesHelper.saveStations(requireContext(), stationList)
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(station: StationItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_station, null)
        val editName = dialogView.findViewById<EditText>(R.id.editStationName)
        val editUrl = dialogView.findViewById<EditText>(R.id.editStreamUrl)
        val editIcon = dialogView.findViewById<EditText>(R.id.editIconUrl)

        editName.setText(station.stationName)
        editUrl.setText(station.streamURL)
        editIcon.setText(station.iconURL)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Station")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val index = stationList.indexOfFirst { it.uuid == station.uuid }
                if (index != -1) {
                    stationList[index] = station.copy(
                        stationName = editName.text.toString(),
                        streamURL = editUrl.text.toString(),
                        iconURL = editIcon.text.toString()
                    )
                    PreferencesHelper.saveStations(requireContext(), stationList)
                    adapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
