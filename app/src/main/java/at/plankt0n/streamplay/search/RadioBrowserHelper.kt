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
    private const val TAG = "RadioBrowserHelper"

    // Use the official DNS-based load balancing endpoint
    // This automatically routes to available servers
    private const val API_HOST = "all.api.radio-browser.info"

    private fun getBaseUrl(): String = "https://$API_HOST/json"

    // Popular genres for quick chips
    val POPULAR_GENRES = listOf(
        "pop", "rock", "jazz", "classical", "electronic", "hip hop",
        "news", "talk", "country", "80s", "90s", "indie", "metal", "ambient"
    )

    // Generic fetch function with retry
    // DNS-based load balancing (all.api.radio-browser.info) automatically routes to available servers
    private suspend inline fun <reified T> fetchWithRetry(
        endpoint: String,
        maxRetries: Int = 3
    ): T? = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                val apiUrl = "${getBaseUrl()}$endpoint"
                Log.d(TAG, "Fetching: $apiUrl (attempt ${attempt + 1})")

                val url = URL(apiUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000  // 10 seconds
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "StreamPlay/1.0 (Android)")
                connection.setRequestProperty("Accept", "application/json")

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    val type = object : TypeToken<T>() {}.type
                    return@withContext Gson().fromJson<T>(json, type)
                } else {
                    Log.e(TAG, "HTTP error: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error on attempt ${attempt + 1}: ${e.localizedMessage}")
                lastException = e
            } finally {
                connection?.disconnect()
            }
        }

        Log.e(TAG, "All retry attempts failed", lastException)
        null
    }

    suspend fun searchStations(
        query: String,
        country: String? = null,
        tag: String? = null,
        language: String? = null,
        codec: String? = null
    ): List<RadioBrowserResult> = withContext(Dispatchers.IO) {
        val params = mutableListOf<String>()
        if (query.isNotBlank()) params += "name=" + URLEncoder.encode(query, "UTF-8")
        if (!country.isNullOrBlank()) params += "country=" + URLEncoder.encode(country, "UTF-8")
        if (!tag.isNullOrBlank()) params += "tag=" + URLEncoder.encode(tag, "UTF-8")
        if (!language.isNullOrBlank()) params += "language=" + URLEncoder.encode(language, "UTF-8")
        if (!codec.isNullOrBlank()) params += "codec=" + URLEncoder.encode(codec, "UTF-8")

        if (params.isEmpty()) return@withContext emptyList()

        val endpoint = "/stations/search?" + params.joinToString("&")
        fetchWithRetry<List<RadioBrowserResult>>(endpoint) ?: emptyList()
    }

    suspend fun getTopStations(limit: Int): List<RadioBrowserResult> {
        val endpoint = "/stations/topvote/$limit"
        return fetchWithRetry<List<RadioBrowserResult>>(endpoint) ?: emptyList()
    }

    suspend fun getTopClickStations(limit: Int): List<RadioBrowserResult> {
        val endpoint = "/stations/topclick/$limit"
        return fetchWithRetry<List<RadioBrowserResult>>(endpoint) ?: emptyList()
    }

    suspend fun getStationsByCountryCode(countryCode: String, limit: Int = 50): List<RadioBrowserResult> {
        val encoded = URLEncoder.encode(countryCode, "UTF-8")
        val endpoint = "/stations/bycountrycodeexact/$encoded?limit=$limit&order=clickcount&reverse=true"
        return fetchWithRetry<List<RadioBrowserResult>>(endpoint) ?: emptyList()
    }

    suspend fun getStationsByTag(tag: String, limit: Int = 50): List<RadioBrowserResult> {
        val encoded = URLEncoder.encode(tag, "UTF-8")
        val endpoint = "/stations/bytag/$encoded?limit=$limit&order=clickcount&reverse=true"
        return fetchWithRetry<List<RadioBrowserResult>>(endpoint) ?: emptyList()
    }

    suspend fun getCountries(): List<RadioBrowserCountry> {
        return fetchWithRetry<List<RadioBrowserCountry>>("/countries") ?: emptyList()
    }

    suspend fun getTags(): List<RadioBrowserTag> {
        return fetchWithRetry<List<RadioBrowserTag>>("/tags") ?: emptyList()
    }

    suspend fun getLanguages(): List<RadioBrowserLanguage> {
        return fetchWithRetry<List<RadioBrowserLanguage>>("/languages") ?: emptyList()
    }

    suspend fun getCodecs(): List<RadioBrowserCodec> {
        return fetchWithRetry<List<RadioBrowserCodec>>("/codecs") ?: emptyList()
    }
}
