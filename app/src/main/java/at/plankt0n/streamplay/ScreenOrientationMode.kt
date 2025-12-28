package at.plankt0n.streamplay

enum class ScreenOrientationMode {
    AUTO,
    LANDSCAPE,
    PORTRAIT;

    companion object {
        fun fromName(name: String?): ScreenOrientationMode =
            values().firstOrNull { it.name == name } ?: AUTO
    }
}
