package at.plankt0n.streamplay.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.Tool
import at.plankt0n.streamplay.data.ToolsRepository

class ToolDetailFragment : Fragment() {

    private var toolId: Long = -1
    private lateinit var repository: ToolsRepository
    private var host: ToolDetailHost? = null

    interface ToolDetailHost {
        fun onToolUpdated(tool: Tool)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as? ToolDetailHost
            ?: parentFragment as? ToolDetailHost
            ?: throw IllegalStateException("ToolDetailFragment needs a ToolDetailHost")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ToolsRepository(requireContext())
        toolId = arguments?.getLong(ARG_TOOL_ID) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_tool_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title: TextView = view.findViewById(R.id.text_detail_title)
        val orderInput: EditText = view.findViewById(R.id.input_detail_order_number)
        val descriptionInput: EditText = view.findViewById(R.id.input_detail_description)
        val tool = repository.getTool(toolId)
        if (tool != null) {
            title.text = getString(R.string.tools_detail_title, tool.orderNumber)
            orderInput.setText(tool.orderNumber)
            descriptionInput.setText(tool.description)
        }
        view.findViewById<Button>(R.id.button_update_tool).setOnClickListener {
            val orderNumber = orderInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()
            if (orderNumber.isEmpty() || description.isEmpty()) {
                Toast.makeText(requireContext(), R.string.validation_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val updatedRows = repository.updateTool(toolId, orderNumber, description)
            if (updatedRows > 0) {
                val updatedTool = repository.getTool(toolId)
                if (updatedTool != null) {
                    host?.onToolUpdated(updatedTool)
                    title.text = getString(R.string.tools_detail_title, updatedTool.orderNumber)
                }
                Toast.makeText(requireContext(), R.string.toast_tool_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val ARG_TOOL_ID = "tool_id"

        fun newInstance(toolId: Long): ToolDetailFragment {
            val fragment = ToolDetailFragment()
            fragment.arguments = Bundle().apply { putLong(ARG_TOOL_ID, toolId) }
            return fragment
        }
    }
}
