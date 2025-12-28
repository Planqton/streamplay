package at.plankt0n.streamplay.data

/**
 * Equalizer Presets with predefined gain values.
 * Values are in decibels (dB), typical range: -15 to +15 dB
 * 5-Band EQ: Band 0 = 60Hz, Band 1 = 230Hz, Band 2 = 910Hz, Band 3 = 3.6kHz, Band 4 = 14kHz
 */
enum class EqualizerPreset(
    val displayName: String,
    val gains: IntArray
) {
    FLAT("Flat", intArrayOf(0, 0, 0, 0, 0)),
    ROCK("Rock", intArrayOf(8, 4, -2, 4, 8)),
    POP("Pop", intArrayOf(-2, 4, 8, 4, -2)),
    JAZZ("Jazz", intArrayOf(6, 2, -2, 2, 6)),
    CLASSICAL("Classical", intArrayOf(0, 0, 0, -2, 5)),
    BASS_BOOST("Bass Boost", intArrayOf(12, 8, 2, 0, 0)),
    TREBLE_BOOST("Treble Boost", intArrayOf(0, 0, 2, 8, 12)),
    VOCAL("Vocal", intArrayOf(-4, 0, 6, 4, 0)),
    CUSTOM("Custom", intArrayOf(0, 0, 0, 0, 0));

    companion object {
        fun fromName(name: String): EqualizerPreset {
            return entries.find { it.name == name } ?: FLAT
        }

        fun fromDisplayName(displayName: String): EqualizerPreset {
            return entries.find { it.displayName == displayName } ?: FLAT
        }
    }
}
