package at.plankt0n.streamplay.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class MetaLogEntry(
    val timestamp: Long,
    val station: String,
    val title: String,
    val artist: String,
    val url: String? = null,
    val coverUrl: String? = null,
    val manual: Boolean = false
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formattedDate(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getDateKey(): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis.toString()
    }

    fun isToday(): Boolean {
        val todayCal = Calendar.getInstance()
        todayCal.set(Calendar.HOUR_OF_DAY, 0)
        todayCal.set(Calendar.MINUTE, 0)
        todayCal.set(Calendar.SECOND, 0)
        todayCal.set(Calendar.MILLISECOND, 0)

        val entryCal = Calendar.getInstance()
        entryCal.timeInMillis = timestamp

        return entryCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
               entryCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    }

    fun isYesterday(): Boolean {
        val yesterdayCal = Calendar.getInstance()
        yesterdayCal.add(Calendar.DAY_OF_YEAR, -1)

        val entryCal = Calendar.getInstance()
        entryCal.timeInMillis = timestamp

        return entryCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
               entryCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)
    }
}
