package at.plankt0n.streamplay.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import android.content.Context
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.R
import android.media.audiofx.Equalizer

class EqualizerFragment : Fragment() {

    private var equalizer: Equalizer? = null
    private lateinit var presetSpinner: Spinner

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_equalizer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ImageButton>(R.id.arrow_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        view.findViewById<android.widget.TextView>(R.id.topbar_title).text = getString(R.string.equalizer_title)
        presetSpinner = view.findViewById(R.id.spinnerPresets)

        val prefs = requireContext().getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        val sessionId = prefs.getInt(Keys.KEY_EQUALIZER_SESSION, android.media.AudioManager.ERROR)

        if (sessionId == android.media.AudioManager.ERROR || sessionId == 0) {
            Toast.makeText(requireContext(), R.string.equalizer_not_available, Toast.LENGTH_SHORT).show()
            return
        }

        equalizer = try {
            Equalizer(0, sessionId).apply { enabled = false }
        } catch (e: RuntimeException) {
            Toast.makeText(requireContext(), R.string.equalizer_not_available, Toast.LENGTH_SHORT).show()
            null
        }
        setupPresets()
    }

    private fun setupPresets() {
        val eq = equalizer ?: return
        val names = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        presetSpinner.adapter = adapter

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val saved = prefs.getInt(Keys.KEY_EQUALIZER_PRESET, 0)
        val idx = saved.coerceIn(0, names.size - 1)
        presetSpinner.setSelection(idx)

        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                sendPresetToService(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun sendPresetToService(preset: Int) {
        val intent = Intent(requireContext(), at.plankt0n.streamplay.StreamingService::class.java).apply {
            action = Keys.ACTION_SET_EQUALIZER_PRESET
            putExtra(Keys.EXTRA_EQUALIZER_PRESET, preset)
        }
        requireContext().startService(intent)
    }

    override fun onDestroyView() {
        equalizer?.release()
        super.onDestroyView()
    }
}
