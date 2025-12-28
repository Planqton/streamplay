package at.plankt0n.streamplay.ui

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import androidx.core.content.ContextCompat
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.EqualizerPreset
import at.plankt0n.streamplay.helper.EqualizerHelper

class EqualizerBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "EqualizerBottomSheet"
    }

    private val sliders = mutableListOf<SeekBar>()
    private val levelLabels = mutableListOf<TextView>()
    private var isUpdatingFromPreset = false
    private var isInitializing = true
    private var minDb = -15
    private var maxDb = 15

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_equalizer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Prüfen ob Equalizer verfügbar
        if (!EqualizerHelper.isInitialized()) {
            Log.w(TAG, "Equalizer not initialized - stream must be playing")
            Toast.makeText(requireContext(), R.string.eq_not_available, Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        // Backup für Cancel-Funktion
        EqualizerHelper.createBackup()

        setupViews(view)
    }

    private fun setupViews(view: View) {
        isInitializing = true

        val eqSwitch = view.findViewById<SwitchCompat>(R.id.eq_switch)
        val presetSpinner = view.findViewById<Spinner>(R.id.eq_preset_spinner)
        val slidersContainer = view.findViewById<LinearLayout>(R.id.eq_sliders_container)
        val btnCancel = view.findViewById<MaterialButton>(R.id.eq_btn_cancel)
        val btnApply = view.findViewById<MaterialButton>(R.id.eq_btn_apply)
        val labelMax = view.findViewById<TextView>(R.id.eq_label_max)
        val labelMin = view.findViewById<TextView>(R.id.eq_label_min)

        // Level-Range
        val (min, max) = EqualizerHelper.getLevelRange()
        minDb = min
        maxDb = max
        labelMax.text = "+${maxDb}dB"
        labelMin.text = "${minDb}dB"

        Log.d(TAG, "Level range: $minDb..$maxDb dB")

        // Enable Switch
        eqSwitch.isChecked = EqualizerHelper.isEnabled
        eqSwitch.setOnCheckedChangeListener { _, isChecked ->
            EqualizerHelper.setEnabled(isChecked)
            Log.d(TAG, "Equalizer enabled: $isChecked")
        }

        // Preset Spinner
        val presets = EqualizerPreset.entries.map { it.displayName }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, presets)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        presetSpinner.adapter = adapter

        // Sliders erstellen
        createSliders(slidersContainer, presetSpinner)

        // Aktuelles Preset auswählen (nach Slider-Erstellung!)
        val currentPresetIndex = EqualizerPreset.entries.indexOf(EqualizerHelper.currentPreset)
        presetSpinner.setSelection(currentPresetIndex)

        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (isInitializing) return

                val preset = EqualizerPreset.entries[position]
                Log.d(TAG, "Preset selected: ${preset.name}")

                if (preset != EqualizerPreset.CUSTOM) {
                    isUpdatingFromPreset = true
                    EqualizerHelper.applyPreset(preset)
                    updateSlidersFromEqualizer()
                    isUpdatingFromPreset = false
                } else {
                    EqualizerHelper.setPreset(EqualizerPreset.CUSTOM)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Nach Setup Flag zurücksetzen
        view.post {
            isInitializing = false
        }

        // Buttons
        btnCancel.setOnClickListener {
            EqualizerHelper.restoreBackup()
            dismiss()
        }

        btnApply.setOnClickListener {
            EqualizerHelper.saveSettings(requireContext())
            Toast.makeText(requireContext(), R.string.eq_saved, Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun createSliders(container: LinearLayout, presetSpinner: Spinner) {
        val numBands = EqualizerHelper.getNumberOfBands()
        val frequencies = EqualizerHelper.getBandFrequencies()
        val range = maxDb - minDb

        Log.d(TAG, "Creating $numBands sliders, range: $range")

        container.removeAllViews()
        sliders.clear()
        levelLabels.clear()

        val density = requireContext().resources.displayMetrics.density
        val sliderHeight = (140 * density).toInt()  // Höhe des Sliders
        val sliderWidth = (36 * density).toInt()    // Breite des Slider-Bereichs

        for (band in 0 until numBands) {
            val bandLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }

            // Level Label
            val levelLabel = TextView(requireContext()).apply {
                val currentLevel = EqualizerHelper.getBandLevel(band)
                text = formatLevel(currentLevel)
                textSize = 10f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
                gravity = android.view.Gravity.CENTER
            }
            levelLabels.add(levelLabel)

            // Container für den rotierten SeekBar
            val seekBarContainer = android.widget.FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            // Normaler SeekBar mit Rotation
            val seekBar = SeekBar(requireContext()).apply {
                max = range
                val currentLevel = EqualizerHelper.getBandLevel(band)
                progress = currentLevel - minDb

                // Slider-Größe: Breite wird zur Höhe nach Rotation
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    sliderHeight,
                    sliderWidth
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }

                // Rotation um 270° für vertikale Darstellung (oben = max)
                rotation = 270f

                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val levelDb = progress + minDb
                        levelLabel.text = formatLevel(levelDb)

                        if (fromUser && !isUpdatingFromPreset) {
                            EqualizerHelper.setBandLevel(band, levelDb)
                            // Zu Custom wechseln
                            if (EqualizerHelper.currentPreset != EqualizerPreset.CUSTOM) {
                                EqualizerHelper.setPreset(EqualizerPreset.CUSTOM)
                                val customIndex = EqualizerPreset.entries.indexOf(EqualizerPreset.CUSTOM)
                                isUpdatingFromPreset = true
                                presetSpinner.setSelection(customIndex)
                                isUpdatingFromPreset = false
                            }
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        // Verhindere dass das BottomSheet das Touch-Event abfängt
                        seekBar?.parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        seekBar?.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                })
            }

            seekBarContainer.addView(seekBar)
            sliders.add(seekBar)

            // Frequenz Label
            val freqLabel = TextView(requireContext()).apply {
                text = if (band < frequencies.size) frequencies[band] else ""
                textSize = 9f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary))
                gravity = android.view.Gravity.CENTER
            }

            bandLayout.addView(levelLabel)
            bandLayout.addView(seekBarContainer)
            bandLayout.addView(freqLabel)
            container.addView(bandLayout)
        }
    }

    private fun updateSlidersFromEqualizer() {
        for (band in sliders.indices) {
            val levelDb = EqualizerHelper.getBandLevel(band)
            sliders[band].progress = levelDb - minDb
            levelLabels[band].text = formatLevel(levelDb)
        }
    }

    private fun formatLevel(levelDb: Int): String {
        return if (levelDb >= 0) "+$levelDb" else "$levelDb"
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isDraggable = false
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        // Remove SeekBar listeners to prevent memory leaks
        sliders.forEach { seekBar ->
            seekBar.setOnSeekBarChangeListener(null)
        }
        sliders.clear()
        levelLabels.clear()
        super.onDismiss(dialog)
    }
}
