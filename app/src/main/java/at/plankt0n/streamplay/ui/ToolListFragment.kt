package at.plankt0n.streamplay.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.adapter.ToolListAdapter
import at.plankt0n.streamplay.data.ToolsRepository

class ToolListFragment : Fragment() {

    private lateinit var adapter: ToolListAdapter
    private lateinit var repository: ToolsRepository
    private var host: ToolListHost? = null

    interface ToolListHost {
        fun onToolSelected(id: Long)
        fun onAddToolRequested()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as? ToolListHost
            ?: parentFragment as? ToolListHost
            ?: throw IllegalStateException("ToolListFragment needs a ToolListHost")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ToolsRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tool_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_tools)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ToolListAdapter(emptyList()) { tool ->
            host?.onToolSelected(tool.id)
        }
        recyclerView.adapter = adapter

        view.findViewById<Button>(R.id.button_open_add).setOnClickListener {
            host?.onAddToolRequested()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun refreshData() {
        adapter.updateData(repository.getAllTools())
    }
}
