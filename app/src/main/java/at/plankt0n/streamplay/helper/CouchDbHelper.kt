package at.plankt0n.streamplay.helper

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object CouchDbHelper {
    private val client = OkHttpClient()

    suspend fun syncStations(
        context: Context,
        endpoint: String,
        username: String,
        password: String
    ) {
        val auth = if (username.isNotBlank() || password.isNotBlank()) {
            Credentials.basic(username, password)
        } else {
            null
        }
        val builder = Request.Builder().url(endpoint)
        auth?.let { builder.header("Authorization", it) }
        val getRequest = builder.build()
        val response = withContext(Dispatchers.IO) { client.newCall(getRequest).execute() }
        when (response.code) {
            200 -> {
                val body = response.body?.string() ?: "{}"
                val obj = JSONObject(body)
                val stations = obj.optJSONArray("stations")?.toString() ?: "[]"
                StationImportHelper.importStationsFromJson(context, stations, true)
            }
            404 -> {
                val list = PreferencesHelper.getStations(context)
                val json = Gson().toJson(mapOf("stations" to list))
                val putBuilder = Request.Builder().url(endpoint)
                auth?.let { putBuilder.header("Authorization", it) }
                val putRequest = putBuilder
                    .put(json.toRequestBody("application/json".toMediaType()))
                    .build()
                withContext(Dispatchers.IO) { client.newCall(putRequest).execute() }
            }
            else -> {
                throw Exception("HTTP ${'$'}{response.code}")
            }
        }
    }
}
