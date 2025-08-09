package at.plankt0n.streamplay.helper

import android.content.Context
import android.content.Intent
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.data.StationItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

object StationImportHelper {
    data class ImportStation(
        val name: String,
        val url: String,
        val iconUrl: String
    )

    data class ImportResult(
        val added: Int,
        val updated: Int,
        val newList: MutableList<StationItem>
    )

    fun importStationsFromJson(
        context: Context,
        json: String,
        replaceAll: Boolean
    ): ImportResult {
        val type = object : TypeToken<List<ImportStation>>() {}.type
        val importedList: List<ImportStation> = Gson().fromJson(json, type)

        val stationList = if (replaceAll) mutableListOf() else PreferencesHelper.getStations(context)
        var updated = 0
        var added = 0

        for (imported in importedList) {
            val index = stationList.indexOfFirst { it.stationName.equals(imported.name, ignoreCase = true) }
            if (index >= 0) {
                val old = stationList[index]
                stationList[index] = StationItem(
                    uuid = old.uuid,
                    stationName = imported.name,
                    streamURL = imported.url,
                    iconURL = imported.iconUrl
                )
                updated++
            } else {
                stationList.add(
                    StationItem(
                        uuid = UUID.randomUUID().toString(),
                        stationName = imported.name,
                        streamURL = imported.url,
                        iconURL = imported.iconUrl
                    )
                )
                added++
            }
        }

        PreferencesHelper.saveStations(context, stationList)
        StateHelper.isPlaylistChangePending = true
        val intent = Intent(context, StreamingService::class.java).apply {
            action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
        }
        context.startService(intent)

        return ImportResult(added, updated, stationList)
    }
}

