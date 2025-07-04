package at.plankt0n.streamplay.helper

import android.media.audiofx.Equalizer

object EqualizerHelper {
    private var equalizer: Equalizer? = null

    fun init(audioSessionId: Int) {
        release()
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (_: Exception) {
            equalizer = null
        }
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }

    fun getPresets(): List<String> {
        val eq = equalizer ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until eq.numberOfPresets) {
            list.add(eq.getPresetName(i.toShort()))
        }
        return list
    }

    fun applyPreset(index: Int) {
        val eq = equalizer ?: return
        if (index in 0 until eq.numberOfPresets) {
            eq.usePreset(index.toShort())
        }
    }
}
