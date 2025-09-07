package at.plankt0n.streamplay.helper

import android.content.Context
import at.plankt0n.streamplay.Keys
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object CouchDbHelper {
    private val client = OkHttpClient()

    private fun buildUrls(endpoint: String): Pair<HttpUrl, HttpUrl> {
        var fullEndpoint = endpoint.trimEnd('/')
        var url = fullEndpoint.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL")
        if (url.pathSegments.size < 2) {
            fullEndpoint =
                fullEndpoint.trimEnd('/') + "/${Keys.DEFAULT_COUCHDB_DATABASE}/${Keys.DEFAULT_COUCHDB_DOCUMENT}"
            url = fullEndpoint.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL")
        }
        val dbUrl = url.newBuilder().removePathSegment(url.pathSegments.size - 1).build()
        return url to dbUrl
    }

    private suspend fun ensureDatabase(dbUrl: HttpUrl, auth: String?, createIfMissing: Boolean) {
        val headBuilder = Request.Builder().url(dbUrl)
        auth?.let { headBuilder.header("Authorization", it) }
        val headRequest = headBuilder.head().build()
        val headResponse = withContext(Dispatchers.IO) { client.newCall(headRequest).execute() }
        if (headResponse.code == 404) {
            if (createIfMissing) {
                val createBuilder = Request.Builder().url(dbUrl)
                auth?.let { createBuilder.header("Authorization", it) }
                val createRequest = createBuilder
                    .put(ByteArray(0).toRequestBody(null))
                    .build()
                val createResponse = withContext(Dispatchers.IO) { client.newCall(createRequest).execute() }
                if (createResponse.code !in 200..299) {
                    throw Exception("HTTP ${createResponse.code}")
                }
            } else {
                throw Exception("Database not found")
            }
        } else if (headResponse.code !in 200..299) {
            throw Exception("HTTP ${headResponse.code}")
        }
    }

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
        val (url, dbUrl) = buildUrls(endpoint)
        ensureDatabase(dbUrl, auth, true)

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
                throw Exception("HTTP ${response.code}")
            }
        }
    }

    suspend fun pushStations(
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
        val (url, dbUrl) = buildUrls(endpoint)
        ensureDatabase(dbUrl, auth, true)

        val list = PreferencesHelper.getStations(context)
        val json = Gson().toJson(mapOf("stations" to list))
        val putBuilder = Request.Builder().url(url)
        auth?.let { putBuilder.header("Authorization", it) }
        val putRequest = putBuilder
            .put(json.toRequestBody("application/json".toMediaType()))
            .build()
        val response = withContext(Dispatchers.IO) { client.newCall(putRequest).execute() }
        if (response.code !in 200..299) {
            throw Exception("HTTP ${response.code}")
        }
    }

    suspend fun readStations(
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
        val (url, dbUrl) = buildUrls(endpoint)
        ensureDatabase(dbUrl, auth, false)

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
                throw Exception("Document not found")
            }
            else -> {
                throw Exception("HTTP ${response.code}")
            }
        }
    }
}
