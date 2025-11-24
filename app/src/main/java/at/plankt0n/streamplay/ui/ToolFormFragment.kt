package at.plankt0n.streamplay.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.ToolsRepository

class ToolFormFragment : Fragment() {

    private lateinit var repository: ToolsRepository
    private var host: ToolFormHost? = null

    interface ToolFormHost {
        fun onToolCreated(id: Long)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as? ToolFormHost
            ?: parentFragment as? ToolFormHost
            ?: throw IllegalStateException("ToolFormFragment needs a ToolFormHost")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ToolsRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_tool_form, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val orderNumberInput: EditText = view.findViewById(R.id.input_order_number)
        val descriptionInput: EditText = view.findViewById(R.id.input_description)
        view.findViewById<Button>(R.id.button_save_tool).setOnClickListener {
            val orderNumber = orderNumberInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()
            if (orderNumber.isEmpty() || description.isEmpty()) {
                Toast.makeText(requireContext(), R.string.validation_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val newId = repository.insertTool(orderNumber, description)
            Toast.makeText(requireContext(), R.string.toast_tool_saved, Toast.LENGTH_SHORT).show()
            host?.onToolCreated(newId)
        }
    }
}
