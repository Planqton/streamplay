package at.plankt0n.streamplay.helper

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.data.StationItem
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object StreamplayApiHelper {

    data class LoginResponse(
        val token: String,
        val username: String,
        val is_admin: Boolean
    )

    data class JsonDataResponse(
        val data: JsonObject
    )

    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error(val message: String) : ApiResult<Nothing>()
    }

    data class SyncData(
        val stations: List<StationItem>,
        val settings: Map<String, Any?>
    )

    private fun getPrefs(context: Context) = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)

    private fun getApiEndpoint(context: Context): String {
        return getPrefs(context).getString(Keys.PREF_API_ENDPOINT, Keys.DEFAULT_API_ENDPOINT)
            ?: Keys.DEFAULT_API_ENDPOINT
    }

    private fun getCredentials(context: Context, usernameOverride: String? = null, passwordOverride: String? = null): Pair<String, String> {
        val prefs = getPrefs(context)
        val username = usernameOverride ?: prefs.getString(Keys.PREF_API_USERNAME, "") ?: ""
        val password = passwordOverride ?: prefs.getString(Keys.PREF_API_PASSWORD, "") ?: ""
        return Pair(username, password)
    }

    private fun getStoredToken(context: Context): String? {
        return getPrefs(context).getString(Keys.PREF_API_TOKEN, null)
    }

    private fun saveToken(context: Context, token: String) {
        getPrefs(context)
            .edit()
            .putString(Keys.PREF_API_TOKEN, token)
            .apply()
    }

    suspend fun login(context: Context, usernameOverride: String? = null, passwordOverride: String? = null): ApiResult<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val endpoint = getApiEndpoint(context)
            val (username, password) = getCredentials(context, usernameOverride, passwordOverride)

            if (username.isBlank() || password.isBlank()) {
                return@withContext ApiResult.Error("Username and password required")
            }

            val url = URL("$endpoint/api/user/login")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val body = JsonObject().apply {
                addProperty("username", username)
                addProperty("password", password)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val loginResponse = Gson().fromJson(response, LoginResponse::class.java)
                saveToken(context, loginResponse.token)
                ApiResult.Success(loginResponse)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                ApiResult.Error("Login failed: $errorBody")
            }
        } catch (e: Exception) {
            ApiResult.Error("Connection error: ${e.message}")
        }
    }

    suspend fun testLogin(context: Context, usernameOverride: String? = null, passwordOverride: String? = null): ApiResult<String> = withContext(Dispatchers.IO) {
        when (val result = login(context, usernameOverride, passwordOverride)) {
            is ApiResult.Success -> ApiResult.Success("Login successful: ${result.data.username}")
            is ApiResult.Error -> result
        }
    }

    suspend fun readFromProfile(context: Context, usernameOverride: String? = null, passwordOverride: String? = null): ApiResult<SyncData> = withContext(Dispatchers.IO) {
        // First ensure we have a valid token
        val loginResult = login(context, usernameOverride, passwordOverride)
        if (loginResult is ApiResult.Error) {
            return@withContext loginResult
        }

        val token = getStoredToken(context)
            ?: return@withContext ApiResult.Error("No token available")

        try {
            val endpoint = getApiEndpoint(context)
            val url = URL("$endpoint/api/user/data")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = Gson().fromJson(response, JsonDataResponse::class.java)
                val data = jsonResponse.data

                // Parse stations
                val stationsJson = data.getAsJsonArray("stations")
                val stationsType = object : TypeToken<List<StationItem>>() {}.type
                val stations: List<StationItem> = if (stationsJson != null) {
                    Gson().fromJson(stationsJson, stationsType)
                } else {
                    emptyList()
                }

                // Parse settings
                val settingsJson = data.getAsJsonObject("settings")
                val settingsType = object : TypeToken<Map<String, Any?>>() {}.type
                val settings: Map<String, Any?> = if (settingsJson != null) {
                    Gson().fromJson(settingsJson, settingsType)
                } else {
                    emptyMap()
                }

                // Apply stations (don't sync back to API to avoid loop)
                Log.d("API SYNC>", "Applying ${stations.size} stations from API")
                PreferencesHelper.saveStations(context, stations, syncToApi = false)
                StateHelper.isPlaylistChangePending = true
                val playlistIntent = Intent(context, StreamingService::class.java).apply {
                    action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
                }
                context.startService(playlistIntent)

                // Notify UI about station updates
                val updateIntent = Intent(Keys.ACTION_STATIONS_UPDATED)
                LocalBroadcastManager.getInstance(context).sendBroadcast(updateIntent)

                // Apply settings (excluding API credentials and screen orientation)
                if (settings.isNotEmpty()) {
                    val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    val excludedKeys = setOf(
                        Keys.PREF_API_ENDPOINT,
                        Keys.PREF_API_USERNAME,
                        Keys.PREF_API_PASSWORD,
                        Keys.PREF_API_TOKEN,
                        Keys.PREF_SCREEN_ORIENTATION
                    )
                    for ((key, value) in settings) {
                        if (key in excludedKeys) continue
                        when (value) {
                            is Boolean -> editor.putBoolean(key, value)
                            is Number -> {
                                if (value.toString().contains('.')) {
                                    editor.putFloat(key, value.toFloat())
                                } else {
                                    editor.putInt(key, value.toInt())
                                }
                            }
                            is String -> editor.putString(key, value)
                        }
                    }
                    editor.apply()
                }

                ApiResult.Success(SyncData(stations, settings))
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                ApiResult.Error("Authentication failed - please check credentials")
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                ApiResult.Error("Failed to read profile: $errorBody")
            }
        } catch (e: Exception) {
            ApiResult.Error("Connection error: ${e.message}")
        }
    }

    suspend fun pushIfSyncEnabled(context: Context) {
        if (!getPrefs(context).getBoolean(Keys.PREF_API_SYNC_ENABLED, false)) {
            return
        }
        pushToProfile(context)
    }

    suspend fun pushToProfile(context: Context, usernameOverride: String? = null, passwordOverride: String? = null): ApiResult<String> = withContext(Dispatchers.IO) {
        // First ensure we have a valid token
        val loginResult = login(context, usernameOverride, passwordOverride)
        if (loginResult is ApiResult.Error) {
            return@withContext loginResult
        }

        val token = getStoredToken(context)
            ?: return@withContext ApiResult.Error("No token available")

        try {
            val endpoint = getApiEndpoint(context)
            val url = URL("$endpoint/api/user/data")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            // Get stations
            val stations = PreferencesHelper.getStations(context)

            // Get settings (excluding API credentials and screen orientation)
            val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
            val allSettings = prefs.all.toMutableMap()
            val excludedKeys = setOf(
                Keys.PREF_API_ENDPOINT,
                Keys.PREF_API_USERNAME,
                Keys.PREF_API_PASSWORD,
                Keys.PREF_API_TOKEN,
                Keys.PREF_SCREEN_ORIENTATION
            )
            excludedKeys.forEach { allSettings.remove(it) }

            // Build data object
            val dataObject = JsonObject().apply {
                add("stations", Gson().toJsonTree(stations))
                add("settings", Gson().toJsonTree(allSettings))
            }

            val body = JsonObject().apply {
                add("data", dataObject)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                ApiResult.Success("Profile updated successfully (${stations.size} stations)")
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                ApiResult.Error("Authentication failed - please check credentials")
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                ApiResult.Error("Failed to push profile: $errorBody")
            }
        } catch (e: Exception) {
            ApiResult.Error("Connection error: ${e.message}")
        }
    }
}
