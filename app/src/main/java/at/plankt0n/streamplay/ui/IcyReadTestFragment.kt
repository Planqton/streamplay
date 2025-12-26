package at.plankt0n.streamplay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.helper.IcyStreamReader

class IcyReadTestFragment : Fragment() {

    private var icyStreamReader: IcyStreamReader? = null
    private lateinit var editTextUrl: EditText
    private lateinit var buttonStart: Button
    private lateinit var buttonStop: Button
    private lateinit var textViewMeta: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_icy_read_test, container, false)

        editTextUrl = view.findViewById(R.id.editTextUrl)
        buttonStart = view.findViewById(R.id.buttonStart)
        buttonStop = view.findViewById(R.id.buttonStop)
        textViewMeta = view.findViewById(R.id.textViewMeta)

        buttonStart.setOnClickListener {
            startReading()
        }

        buttonStop.setOnClickListener {
            stopReading()
        }

        return view
    }

    private fun startReading() {
        val url = editTextUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.toast_please_enter_url), Toast.LENGTH_SHORT).show()
            return
        }

        icyStreamReader?.stop()
        icyStreamReader = IcyStreamReader(requireContext(), url) { artist, title ->
            activity?.runOnUiThread {
                textViewMeta.text = "Artist: ${artist ?: "?"}\nTitle: ${title ?: "?"}"
            }
        }
        icyStreamReader?.start()
        Toast.makeText(requireContext(), getString(R.string.toast_reading_metadata), Toast.LENGTH_SHORT).show()
    }

    private fun stopReading() {
        icyStreamReader?.stop()
        Toast.makeText(requireContext(), getString(R.string.toast_stopped), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        icyStreamReader?.stop()
    }
}
