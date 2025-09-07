package at.plankt0n.streamplay.helper

import android.content.Context
import at.plankt0n.streamplay.Keys
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        var fullEndpoint = endpoint.trimEnd('/')
        var url = fullEndpoint.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL")
        if (url.pathSegments.size < 2) {
            fullEndpoint = fullEndpoint.trimEnd('/') + "/${Keys.DEFAULT_COUCHDB_DATABASE}/${Keys.DEFAULT_COUCHDB_DOCUMENT}"
            url = fullEndpoint.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL")
        }

        val dbUrl = url.newBuilder().removePathSegment(url.pathSegments.size - 1).build()

        // ensure database exists
        val dbHeadBuilder = Request.Builder().url(dbUrl)
        auth?.let { dbHeadBuilder.header("Authorization", it) }
        val dbHeadRequest = dbHeadBuilder.head().build()
        val dbHeadResponse = withContext(Dispatchers.IO) { client.newCall(dbHeadRequest).execute() }
        if (dbHeadResponse.code == 404) {
            val createDbBuilder = Request.Builder().url(dbUrl)
            auth?.let { createDbBuilder.header("Authorization", it) }
            val createDbRequest = createDbBuilder
                .put(ByteArray(0).toRequestBody(null))
                .build()
            withContext(Dispatchers.IO) { client.newCall(createDbRequest).execute() }
        } else if (dbHeadResponse.code !in 200..299) {
            throw Exception("HTTP ${'$'}{dbHeadResponse.code}")
        }

        // fetch or create stations document
        val builder = Request.Builder().url(url)
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
                val putBuilder = Request.Builder().url(url)
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
