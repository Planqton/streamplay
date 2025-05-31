package at.plankt0n.streamplay.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.PreferencesHelper
import com.google.android.material.textview.MaterialTextView
import java.util.UUID

class StationsFragment : Fragment() {

    private lateinit var stationList: MutableList<StationItem>
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StationListAdapter
    private lateinit var topbarBackButton: ImageButton
    private lateinit var topbarTitleView: MaterialTextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_stations, container, false)

        // Topbar
        topbarBackButton = view.findViewById(R.id.arrow_back)
        topbarTitleView = view.findViewById(R.id.topbar_title)
        topbarTitleView.text = getString(R.string.fragment_stations_title)

        // Stationen laden
        stationList = PreferencesHelper.getStations(requireContext())

        // RecyclerView vorbereiten
        recyclerView = view.findViewById(R.id.recyclerViewStations)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = StationListAdapter(
            stationList,
            onEdit = { station -> showEditDialog(station) },
            onDelete = { station ->
                stationList.remove(station)
                PreferencesHelper.saveStations(requireContext(), stationList)
                adapter.notifyDataSetChanged()
            }
        )
        recyclerView.adapter = adapter

        // Station hinzufügen
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
