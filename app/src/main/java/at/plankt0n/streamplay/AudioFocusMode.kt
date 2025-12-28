package at.plankt0n.streamplay

enum class AudioFocusMode {
    STOP,
    HOLD,
    LOWER;

    companion object {
        fun fromName(name: String?): AudioFocusMode =
            values().firstOrNull { it.name == name } ?: STOP
    }
}
