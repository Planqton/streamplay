package at.plankt0n.streamplay.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import at.plankt0n.streamplay.R

class SettingsFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchAutoplay: Switch
    private lateinit var seekDelay: SeekBar
    private lateinit var textDelay: TextView
    private lateinit var switchMinimize: Switch

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences("StreamPlayPrefs", Context.MODE_PRIVATE)

        switchAutoplay = view.findViewById(R.id.switch_autoplay)
        seekDelay = view.findViewById(R.id.seekbar_delay)
        textDelay = view.findViewById(R.id.text_delay_value)
        switchMinimize = view.findViewById(R.id.switch_minimize)

        view.findViewById<TextView>(R.id.topbar_title).text = getString(R.string.settings_title)
        view.findViewById<ImageButton>(R.id.arrow_back).setOnClickListener { parentFragmentManager.popBackStack() }

        switchAutoplay.isChecked = prefs.getBoolean("autoplay_enabled", false)
        seekDelay.progress = prefs.getInt("autoplay_delay", 0)
        updateDelayText(seekDelay.progress)
        switchMinimize.isChecked = prefs.getBoolean("minimize_after_autoplay", false)

        switchAutoplay.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("autoplay_enabled", isChecked).apply()
        }

        seekDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateDelayText(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("autoplay_delay", seekDelay.progress).apply()
            }
        })

        switchMinimize.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("minimize_after_autoplay", isChecked).apply()
        }
    }

    private fun updateDelayText(value: Int) {
        textDelay.text = getString(R.string.settings_delay_seconds, value)
    }
}
