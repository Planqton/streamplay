package at.plankt0n.streamplay.helper

import android.content.Context
import android.content.Intent
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.data.StationItem
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object StationImportHelper {
    data class ImportStation(
        val name: String? = null,
        val stationName: String? = null,
        val url: String? = null,
        val streamURL: String? = null,
        val iconUrl: String? = null,
        val iconURL: String? = null
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
            val name = imported.name ?: imported.stationName ?: continue
            val url = imported.url ?: imported.streamURL ?: continue
            val icon = imported.iconUrl ?: imported.iconURL ?: ""

            val index = stationList.indexOfFirst { it.stationName.equals(name, ignoreCase = true) }
            if (index >= 0) {
                val old = stationList[index]
                stationList[index] = StationItem(
                    uuid = old.uuid,
                    stationName = name,
                    streamURL = url,
                    iconURL = icon
                )
                updated++
            } else {
                stationList.add(
                    StationItem(
                        uuid = UUID.randomUUID().toString(),
                        stationName = name,
                        streamURL = url,
                        iconURL = icon
                    )
                )
                added++
            }
        }

        PreferencesHelper.saveStations(context, stationList, syncRemote = false)
        StateHelper.isPlaylistChangePending = true
        val intent = Intent(context, StreamingService::class.java).apply {
            action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
        }
        context.startService(intent)

        return ImportResult(added, updated, stationList)
    }

    suspend fun importStationsFromUrl(
        context: Context,
        url: String,
        replaceAll: Boolean
    ): ImportResult {
        val normalizedUrl = if (url.contains("github.com") && url.contains("/blob/")) {
            url.replace("github.com/", "raw.githubusercontent.com/")
                .replace("/blob/", "/")
        } else {
            url
        }

        val json = withContext(Dispatchers.IO) {
            URL(normalizedUrl).openStream().bufferedReader().use { it.readText() }
        }
        return importStationsFromJson(context, json, replaceAll)
    }

    suspend fun importStationsFromJsonBin(
        context: Context,
        url: String,
        masterKey: String,
        replaceAll: Boolean
    ): ImportResult {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .addHeader("X-Master-Key", masterKey)
            .build()
        val json = withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val body = resp.body?.string() ?: "{}"
                val obj = JsonParser.parseString(body).asJsonObject
                obj["record"].toString()
            }
        }
        return importStationsFromJson(context, json, replaceAll)
    }

    suspend fun exportStationsToJsonBin(
        url: String,
        masterKey: String,
        stationList: List<StationItem>
    ) {
        val client = OkHttpClient()
        val body = Gson().toJson(stationList).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .put(body)
            .addHeader("X-Master-Key", masterKey)
            .build()
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().close()
        }
    }
}

