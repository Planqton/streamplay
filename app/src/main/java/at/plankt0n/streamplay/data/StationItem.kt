package at.plankt0n.streamplay.data

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

/*
 * Station Item class
 */
@Keep
@Parcelize
data class StationItem(
    val uuid: String = "",
    val stationName: String = "",
    val streamURL: String = "",
    val iconURL: String = ""
) : Parcelable