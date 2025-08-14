
package at.plankt0n.streamplay.search

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RadioBrowserHelper {
    private const val SEARCH_URL = "https://de1.api.radio-browser.info/json/stations/search?"
    private const val COUNTRIES_URL = "https://de1.api.radio-browser.info/json/countries"
    private const val TAGS_URL = "https://de1.api.radio-browser.info/json/tags"
    private const val TOP_URL = "https://de1.api.radio-browser.info/json/stations/topvote/"

    suspend fun searchStations(query: String, country: String? = null, tag: String? = null): List<RadioBrowserResult> = withContext(Dispatchers.IO) {
        try {
            val params = mutableListOf<String>()
            if (query.isNotBlank()) params += "name=" + URLEncoder.encode(query, "UTF-8")
            if (!country.isNullOrBlank()) params += "country=" + URLEncoder.encode(country, "UTF-8")
            if (!tag.isNullOrBlank()) params += "tag=" + URLEncoder.encode(tag, "UTF-8")
            if (params.isEmpty()) return@withContext emptyList()
            val apiUrl = "$SEARCH_URL" + params.joinToString("&")
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

    suspend fun getCountries(): List<RadioBrowserCountry> = withContext(Dispatchers.IO) {
        try {
            val url = URL(COUNTRIES_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val json = inputStream.bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<RadioBrowserCountry>>() {}.type
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

    suspend fun getTags(): List<RadioBrowserTag> = withContext(Dispatchers.IO) {
        try {
            val url = URL(TAGS_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val json = inputStream.bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<RadioBrowserTag>>() {}.type
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
    