package at.plankt0n.streamplay.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MetaLogEntry(
    val timestamp: Long,
    val station: String,
    val title: String,
    val artist: String,
    val url: String? = null,
    val manual: Boolean = false
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
