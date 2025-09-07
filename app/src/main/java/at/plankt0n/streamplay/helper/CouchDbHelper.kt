package at.plankt0n.streamplay.helper

import android.content.Context
import at.plankt0n.streamplay.Keys
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
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
    @Volatile
    var isApplyingPrefs = false
        private set
    private val client = OkHttpClient()

    private fun buildUrls(endpoint: String): Pair<HttpUrl, HttpUrl> {
        val url = endpoint.trimEnd('/').toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid URL")
        if (url.pathSegments.size < 2) {
            throw IllegalArgumentException("Endpoint must include database and document")
        }
        val dbUrl = url.newBuilder().removePathSegment(url.pathSegments.size - 1).build()
        return url to dbUrl
    }

    private suspend fun ensureDatabase(dbUrl: HttpUrl, auth: String?, createIfMissing: Boolean) {
        val headBuilder = Request.Builder().url(dbUrl)
        auth?.let { headBuilder.header("Authorization", it) }
        headBuilder.header("Accept", "application/json")
        val headRequest = headBuilder.head().build()
        val headResponse = withContext(Dispatchers.IO) { client.newCall(headRequest).execute() }
        if (headResponse.code == 404) {
            if (createIfMissing) {
                val createBuilder = Request.Builder().url(dbUrl)
                auth?.let { createBuilder.header("Authorization", it) }
                createBuilder.header("Accept", "application/json")
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

    suspend fun syncPrefs(
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
        builder.header("Accept", "application/json")
        val getRequest = builder.build()
        val response = withContext(Dispatchers.IO) { client.newCall(getRequest).execute() }
        when (response.code) {
            200 -> {
                val body = response.body?.string() ?: "{}"
                val obj = JSONObject(body)
                val stations = obj.optJSONArray("stations")?.toString() ?: "[]"
                val prefsObj = obj.optJSONObject("prefs")?.toString()
                isApplyingPrefs = true
                try {
                    prefsObj?.let { applyPrefs(context, it) }
                    StationImportHelper.importStationsFromJson(context, stations, true)
                } finally {
                    isApplyingPrefs = false
                }
            }
            404 -> {
                val list = PreferencesHelper.getStations(context)
                val prefsMap = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE).all
                val json = Gson().toJson(mapOf("stations" to list, "prefs" to prefsMap))
                val putBuilder = Request.Builder().url(url)
                auth?.let { putBuilder.header("Authorization", it) }
                putBuilder.header("Accept", "application/json")
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

    suspend fun pushPrefs(
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

        val getBuilder = Request.Builder().url(url)
        auth?.let { getBuilder.header("Authorization", it) }
        getBuilder.header("Accept", "application/json")
        val getResponse = withContext(Dispatchers.IO) { client.newCall(getBuilder.build()).execute() }
        val rev = when (getResponse.code) {
            200 -> JSONObject(getResponse.body?.string() ?: "{}").optString("_rev")
            404 -> null
            else -> throw Exception("HTTP ${getResponse.code}")
        }

        val list = PreferencesHelper.getStations(context)
        val prefsMap = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE).all
        val data = mutableMapOf<String, Any>("stations" to list, "prefs" to prefsMap)
        rev?.takeIf { it.isNotBlank() }?.let { data["_rev"] = it }
        val json = Gson().toJson(data)
        val putBuilder = Request.Builder().url(url)
        auth?.let { putBuilder.header("Authorization", it) }
        putBuilder.header("Accept", "application/json")
        val putRequest = putBuilder
            .put(json.toRequestBody("application/json".toMediaType()))
            .build()
        val response = withContext(Dispatchers.IO) { client.newCall(putRequest).execute() }
        if (response.code !in 200..299) {
            throw Exception("HTTP ${response.code}")
        }
    }

    suspend fun readPrefs(
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
        builder.header("Accept", "application/json")
        val getRequest = builder.build()
        val response = withContext(Dispatchers.IO) { client.newCall(getRequest).execute() }
        when (response.code) {
            200 -> {
                val body = response.body?.string() ?: "{}"
                val obj = JSONObject(body)
                val stations = obj.optJSONArray("stations")?.toString() ?: "[]"
                val prefsObj = obj.optJSONObject("prefs")?.toString()
                isApplyingPrefs = true
                try {
                    prefsObj?.let { applyPrefs(context, it) }
                    StationImportHelper.importStationsFromJson(context, stations, true)
                } finally {
                    isApplyingPrefs = false
                }
            }
            404 -> {
                throw Exception("Document not found")
            }
            else -> {
                throw Exception("HTTP ${response.code}")
            }
        }
    }

    suspend fun ensurePrefsDocument(
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

        val headBuilder = Request.Builder().url(url)
        auth?.let { headBuilder.header("Authorization", it) }
        headBuilder.header("Accept", "application/json")
        val headRequest = headBuilder.head().build()
        val headResponse = withContext(Dispatchers.IO) { client.newCall(headRequest).execute() }
        if (headResponse.code == 404) {
            val list = PreferencesHelper.getStations(context)
            val prefsMap = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE).all
            val json = Gson().toJson(mapOf("stations" to list, "prefs" to prefsMap))
            val putBuilder = Request.Builder().url(url)
            auth?.let { putBuilder.header("Authorization", it) }
            putBuilder.header("Accept", "application/json")
            val putRequest = putBuilder
                .put(json.toRequestBody("application/json".toMediaType()))
                .build()
            val putResponse = withContext(Dispatchers.IO) { client.newCall(putRequest).execute() }
            if (putResponse.code !in 200..299) {
                throw Exception("HTTP ${putResponse.code}")
            }
        } else if (headResponse.code !in 200..299) {
            throw Exception("HTTP ${headResponse.code}")
        }
    }

    private fun applyPrefs(context: Context, json: String) {
        val obj = JsonParser.parseString(json).asJsonObject
        val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().clear()
        for ((key, value) in obj.entrySet()) {
            when {
                value.isJsonPrimitive -> {
                    val prim = value.asJsonPrimitive
                    when {
                        prim.isBoolean -> editor.putBoolean(key, prim.asBoolean)
                        prim.isNumber -> {
                            val num = prim.asNumber
                            if (num.toString().contains('.')) {
                                editor.putFloat(key, num.toFloat())
                            } else {
                                editor.putLong(key, num.toLong())
                            }
                        }
                        prim.isString -> editor.putString(key, prim.asString)
                    }
                }
                value.isJsonArray -> {
                    val type = object : TypeToken<Set<String>>() {}.type
                    val set: Set<String> = Gson().fromJson(value, type)
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.apply()
    }
}
