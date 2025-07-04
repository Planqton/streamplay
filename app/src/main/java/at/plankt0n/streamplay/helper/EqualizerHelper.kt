package at.plankt0n.streamplay.helper

import android.media.audiofx.Equalizer

object EqualizerHelper {
    private var equalizer: Equalizer? = null

    fun init(audioSessionId: Int = 0) {
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

    private fun ensureEqualizer(): Equalizer? {
        if (equalizer == null) {
            try {
                equalizer = Equalizer(0, 0).apply { enabled = true }
            } catch (_: Exception) {
                equalizer = null
            }
        }
        return equalizer
    }

    fun getPresets(): List<String> {
        val eq = ensureEqualizer() ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until eq.numberOfPresets) {
            list.add(eq.getPresetName(i.toShort()))
        }
        return list
    }

    fun applyPreset(index: Int) {
        val eq = ensureEqualizer() ?: return
        if (index in 0 until eq.numberOfPresets) {
            eq.usePreset(index.toShort())
        }
    }
}
