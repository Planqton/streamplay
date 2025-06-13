
package at.plankt0n.streamplay.search

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object RadioBrowserHelper {
    private const val BASE_URL = "https://de1.api.radio-browser.info/json/stations/byname/"
    private const val TOP_URL = "https://de1.api.radio-browser.info/json/stations/topvote/"

    suspend fun searchStations(query: String): List<RadioBrowserResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val apiUrl = "$BASE_URL$encoded"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val json = inputStream.bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<RadioBrowserResult>>() {}.type
                Gson().fromJson(json, type)
            } else {
                Log.e("RadioBrowserHelper", "HTTP error: ${connection.responseCode}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("RadioBrowserHelper", "Error: ${e.localizedMessage}")
            emptyList()
        }
    }

    suspend fun getTopStations(limit: Int): List<RadioBrowserResult> = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "$TOP_URL$limit"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val json = inputStream.bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<RadioBrowserResult>>() {}.type
                Gson().fromJson(json, type)
            } else {
                Log.e("RadioBrowserHelper", "HTTP error: ${connection.responseCode}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("RadioBrowserHelper", "Error: ${e.localizedMessage}")
            emptyList()
        }
    }
}
    