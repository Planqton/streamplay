package at.plankt0n.streamplay

enum class NetworkType {
    ALL,
    WIFI_ONLY,
    MOBILE_ONLY;

    companion object {
        fun fromName(name: String?): NetworkType =
            values().firstOrNull { it.name == name } ?: ALL
    }
}
