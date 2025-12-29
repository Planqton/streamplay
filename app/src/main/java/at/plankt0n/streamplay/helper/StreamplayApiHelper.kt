package at.plankt0n.streamplay.helper

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.data.StationItem
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
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
        val lists: Map<String, List<StationItem>>?,
        val selectedListIndex: Int,
        val settings: Map<String, Any?>
    )

    /**
     * Validation result containing either success or detailed error information
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /**
     * Validates a single station object
     */
    private fun validateStation(stationJson: JsonObject, index: Int): ValidationResult {
        val requiredFields = listOf("uuid", "stationName", "streamURL", "iconURL")

        for (field in requiredFields) {
            if (!stationJson.has(field)) {
                return ValidationResult.Invalid("Station #${index + 1}: Feld '$field' fehlt")
            }
            val value = stationJson.get(field)
            if (value.isJsonNull) {
                return ValidationResult.Invalid("Station #${index + 1}: Feld '$field' ist null")
            }
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
                return ValidationResult.Invalid("Station #${index + 1}: Feld '$field' muss ein String sein")
            }
            val stringValue = value.asString
            if (stringValue.isBlank()) {
                return ValidationResult.Invalid("Station #${index + 1}: Feld '$field' ist leer")
            }
        }

        // Validate URL format for streamURL
        val streamUrl = stationJson.get("streamURL").asString
        if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
            return ValidationResult.Invalid("Station #${index + 1}: streamURL muss mit http:// oder https:// beginnen")
        }

        return ValidationResult.Valid
    }

    /**
     * Validates the stations array
     */
    private fun validateStations(stationsJson: JsonArray?): ValidationResult {
        if (stationsJson == null) {
            return ValidationResult.Valid // Empty stations is valid
        }

        for (i in 0 until stationsJson.size()) {
            val element = stationsJson.get(i)
            if (!element.isJsonObject) {
                return ValidationResult.Invalid("Station #${i + 1}: Ungültiges Format (kein Objekt)")
            }
            val stationResult = validateStation(element.asJsonObject, i)
            if (stationResult is ValidationResult.Invalid) {
                return stationResult
            }
        }

        return ValidationResult.Valid
    }

    /**
     * Validates settings for corrupt values
     */
    private fun validateSettings(settingsJson: JsonObject?): ValidationResult {
        if (settingsJson == null) {
            return ValidationResult.Valid // Empty settings is valid
        }

        for (key in settingsJson.keySet()) {
            val value = settingsJson.get(key)

            // Check for truncated/corrupt JSON strings
            if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                val stringValue = value.asString
                // Detect truncated JSON arrays/objects like "[{" or "{"
                if ((stringValue.startsWith("[") || stringValue.startsWith("{")) &&
                    !isValidJsonString(stringValue)) {
                    return ValidationResult.Invalid("Setting '$key': Korrupter JSON-String erkannt ('$stringValue')")
                }
            }
        }

        return ValidationResult.Valid
    }

    /**
     * Checks if a string that looks like JSON is actually valid JSON
     */
    private fun isValidJsonString(str: String): Boolean {
        if (!str.startsWith("[") && !str.startsWith("{")) {
            return true // Not JSON-like, so it's fine
        }
        return try {
            Gson().fromJson(str, Any::class.java)
            true
        } catch (e: JsonSyntaxException) {
            false
        }
    }

    /**
     * Validates the complete API response data
     * Supports both legacy (stations array) and new (lists object) formats
     */
    private fun validateApiData(data: JsonObject): ValidationResult {
        // Check for new lists format first
        val listsJson = try {
            data.getAsJsonObject("lists")
        } catch (e: Exception) {
            null
        }

        if (listsJson != null && listsJson.keySet().isNotEmpty()) {
            // New format: validate lists
            Log.d("API SYNC", "validateApiData: Found lists format with ${listsJson.keySet().size} lists")
            for (listName in listsJson.keySet()) {
                val listStations = try {
                    listsJson.getAsJsonArray(listName)
                } catch (e: Exception) {
                    return ValidationResult.Invalid("Liste '$listName' hat ein ungültiges Format")
                }
                val result = validateStations(listStations)
                if (result is ValidationResult.Invalid) {
                    return ValidationResult.Invalid("Liste '$listName': ${result.reason}")
                }
            }
        } else {
            // Legacy format: validate stations array
            Log.d("API SYNC", "validateApiData: Using legacy stations format")
            val stationsJson = try {
                data.getAsJsonArray("stations")
            } catch (e: Exception) {
                return ValidationResult.Invalid("'stations' hat ein ungültiges Format: ${e.message}")
            }

            val stationsResult = validateStations(stationsJson)
            if (stationsResult is ValidationResult.Invalid) {
                return stationsResult
            }
        }

        // Validate settings
        val settingsJson = try {
            data.getAsJsonObject("settings")
        } catch (e: Exception) {
            return ValidationResult.Invalid("'settings' hat ein ungültiges Format: ${e.message}")
        }

        val settingsResult = validateSettings(settingsJson)
        if (settingsResult is ValidationResult.Invalid) {
            return settingsResult
        }

        return ValidationResult.Valid
    }

    private fun getPrefs(context: Context) = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Saves the sync error state to preferences
     */
    private fun setSyncErrorState(context: Context, hasError: Boolean, errorMessage: String? = null) {
        getPrefs(context).edit()
            .putBoolean(Keys.PREF_API_SYNC_ERROR, hasError)
            .putString(Keys.PREF_API_SYNC_ERROR_MESSAGE, errorMessage)
            .apply()
    }

    /**
     * Clears the sync error state (called on successful sync)
     */
    fun clearSyncError(context: Context) {
        setSyncErrorState(context, false, null)
    }

    /**
     * Checks if there's a sync error
     */
    fun hasSyncError(context: Context): Boolean {
        return getPrefs(context).getBoolean(Keys.PREF_API_SYNC_ERROR, false)
    }

    /**
     * Gets the last sync error message
     */
    fun getSyncErrorMessage(context: Context): String? {
        return getPrefs(context).getString(Keys.PREF_API_SYNC_ERROR_MESSAGE, null)
    }

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
            .commit()  // Use commit() for critical auth token to ensure it's written before next request
    }

    suspend fun login(context: Context, usernameOverride: String? = null, passwordOverride: String? = null): ApiResult<LoginResponse> = withContext(Dispatchers.IO) {
        val endpoint = getApiEndpoint(context)
        val (username, password) = getCredentials(context, usernameOverride, passwordOverride)

        if (username.isBlank() || password.isBlank()) {
            return@withContext ApiResult.Error("Username and password required")
        }

        val url = URL("$endpoint/api/user/login")
        val connection = url.openConnection() as HttpURLConnection
        try {
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
        } finally {
            connection.disconnect()
        }
    }

    suspend fun testLogin(context: Context, usernameOverride: String? = null, passwordOverride: String? = null): ApiResult<String> = withContext(Dispatchers.IO) {
        when (val result = login(context, usernameOverride, passwordOverride)) {
            is ApiResult.Success -> ApiResult.Success("Login successful: ${result.data.username}")
            is ApiResult.Error -> result
        }
    }

    suspend fun readFromProfile(context: Context, usernameOverride: String? = null, passwordOverride: String? = null): ApiResult<SyncData> = withContext(Dispatchers.IO) {
        // Helper to return error and set error state
        fun errorResult(message: String): ApiResult.Error {
            setSyncErrorState(context, true, message)
            return ApiResult.Error(message)
        }

        // First ensure we have a valid token
        val loginResult = login(context, usernameOverride, passwordOverride)
        if (loginResult is ApiResult.Error) {
            setSyncErrorState(context, true, loginResult.message)
            return@withContext loginResult
        }

        val token = getStoredToken(context)
            ?: return@withContext errorResult("No token available")

        val endpoint = getApiEndpoint(context)
        val url = URL("$endpoint/api/user/data")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            Log.d("API SYNC", "Response Code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("API SYNC", "Raw Response (first 500 chars): ${response.take(500)}")

                // Parse JSON response
                val jsonResponse = try {
                    Gson().fromJson(response, JsonDataResponse::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.e("API SYNC", "JSON-Parsing fehlgeschlagen", e)
                    return@withContext errorResult("JSON-Parsing fehlgeschlagen: ${e.message}")
                }

                Log.d("API SYNC", "jsonResponse: $jsonResponse")
                Log.d("API SYNC", "jsonResponse.data: ${jsonResponse?.data}")

                if (jsonResponse?.data == null) {
                    Log.e("API SYNC", "API-Antwort enthält keine Daten - Response war: $response")
                    return@withContext errorResult("API-Antwort enthält keine Daten")
                }

                val data = jsonResponse.data
                Log.d("API SYNC", "Data keys: ${data.keySet()}")

                // Validate data BEFORE applying
                val validationResult = validateApiData(data)
                if (validationResult is ValidationResult.Invalid) {
                    Log.e("API SYNC", "Validierung fehlgeschlagen: ${validationResult.reason}")
                    return@withContext errorResult("Sync abgebrochen - ${validationResult.reason}")
                }
                Log.d("API SYNC", "Validierung erfolgreich")

                // Check for new multi-list format first
                val listsJson = try {
                    data.getAsJsonObject("lists")
                } catch (e: Exception) {
                    null
                }
                Log.d("API SYNC", "listsJson: ${listsJson != null}, keys: ${listsJson?.keySet()}")

                // Parse lists (new format) or stations (legacy)
                val listsType = object : TypeToken<Map<String, List<StationItem>>>() {}.type
                val stationsType = object : TypeToken<List<StationItem>>() {}.type

                val parsedLists: Map<String, List<StationItem>>?
                val stations: List<StationItem>

                if (listsJson != null && listsJson.keySet().isNotEmpty()) {
                    // New format: parse lists
                    Log.d("API SYNC", "Using new multi-list format")
                    parsedLists = try {
                        Gson().fromJson<Map<String, List<StationItem>>>(listsJson, listsType)
                    } catch (e: Exception) {
                        Log.e("API SYNC", "Lists-Parsing fehlgeschlagen", e)
                        return@withContext errorResult("Lists-Parsing fehlgeschlagen: ${e::class.simpleName}: ${e.message}")
                    }
                    Log.d("API SYNC", "Parsed ${parsedLists?.size ?: 0} lists")

                    // Get stations from selected list index or first list
                    val settingsJson = data.getAsJsonObject("settings")
                    val selectedListIndex = try {
                        settingsJson?.get(Keys.KEY_SELECTED_LIST)?.asInt ?: 0
                    } catch (e: Exception) {
                        0 // Fallback to first list
                    }
                    val listNames = parsedLists?.keys?.toList() ?: emptyList()
                    val selectedListName = listNames.getOrNull(selectedListIndex) ?: listNames.firstOrNull()
                    stations = if (selectedListName != null) {
                        parsedLists?.get(selectedListName) ?: emptyList()
                    } else {
                        parsedLists?.values?.firstOrNull() ?: emptyList()
                    }
                    Log.d("API SYNC", "Selected list index: $selectedListIndex, name: $selectedListName, stations: ${stations.size}")
                } else {
                    // Legacy format: parse stations array
                    Log.d("API SYNC", "Using legacy stations format")
                    parsedLists = null
                    val stationsJson = data.getAsJsonArray("stations")
                    Log.d("API SYNC", "stationsJson size: ${stationsJson?.size()}")

                    stations = if (stationsJson != null) {
                        try {
                            Log.d("API SYNC", "Parsing ${stationsJson.size()} stations...")
                            val parsed = Gson().fromJson<List<StationItem>>(stationsJson, stationsType)
                            Log.d("API SYNC", "Parsed stations: ${parsed?.size ?: "null"}")
                            parsed ?: emptyList()
                        } catch (e: Exception) {
                            Log.e("API SYNC", "Stations-Parsing fehlgeschlagen", e)
                            return@withContext errorResult("Stations-Parsing fehlgeschlagen: ${e::class.simpleName}: ${e.message}")
                        }
                    } else {
                        Log.d("API SYNC", "stationsJson is null, returning empty list")
                        emptyList()
                    }
                }
                Log.d("API SYNC", "Final stations count: ${stations.size}")

                // Parse settings
                val settingsJson = data.getAsJsonObject("settings")
                Log.d("API SYNC", "settingsJson keys: ${settingsJson?.keySet()?.size ?: 0}")
                Log.d("API SYNC", "settingsJson content: $settingsJson")

                val settings: Map<String, Any?> = if (settingsJson != null) {
                    try {
                        // Parse each setting individually to avoid type issues
                        val result = mutableMapOf<String, Any?>()
                        for (key in settingsJson.keySet()) {
                            val element = settingsJson.get(key)
                            val value: Any? = when {
                                element.isJsonNull -> null
                                element.isJsonPrimitive -> {
                                    val primitive = element.asJsonPrimitive
                                    when {
                                        primitive.isBoolean -> primitive.asBoolean
                                        primitive.isNumber -> primitive.asNumber
                                        primitive.isString -> primitive.asString
                                        else -> primitive.asString
                                    }
                                }
                                else -> {
                                    // For complex types (arrays, objects), store as string
                                    Log.w("API SYNC", "Complex type for key $key, storing as string: $element")
                                    element.toString()
                                }
                            }
                            result[key] = value
                            Log.d("API SYNC", "Parsed setting: $key = $value (${value?.let { it::class.simpleName } ?: "null"})")
                        }
                        result
                    } catch (e: Exception) {
                        Log.e("API SYNC", "Settings-Parsing fehlgeschlagen", e)
                        Log.e("API SYNC", "Exception type: ${e::class.java.name}")
                        Log.e("API SYNC", "Exception message: ${e.message}")
                        Log.e("API SYNC", "Exception cause: ${e.cause}")
                        return@withContext errorResult("Settings-Parsing fehlgeschlagen: ${e::class.simpleName}: ${e.message}")
                    }
                } else {
                    emptyMap()
                }
                Log.d("API SYNC", "Final settings count: ${settings.size}")

                // All validation passed - now apply the data
                Log.d("API SYNC", "Validierung erfolgreich - wende ${stations.size} Stations an")

                // Clear error state on success
                clearSyncError(context)

                // Apply lists or stations (don't sync back to API to avoid loop)
                if (parsedLists != null && parsedLists.isNotEmpty()) {
                    // New format: save all lists
                    Log.d("API SYNC", "Saving ${parsedLists.size} lists to preferences")
                    PreferencesHelper.saveStationLists(context, parsedLists, syncToApi = false)

                    // Apply selected list index from settings
                    val selectedIndex = try {
                        (settings[Keys.KEY_SELECTED_LIST] as? Number)?.toInt() ?: 0
                    } catch (e: Exception) {
                        0
                    }
                    val maxIndex = parsedLists.size - 1
                    val safeIndex = selectedIndex.coerceIn(0, maxIndex.coerceAtLeast(0))
                    PreferencesHelper.setSelectedListIndex(context, safeIndex)
                    Log.d("API SYNC", "Set selected list index to: $safeIndex")
                } else {
                    // Legacy format: save stations only
                    PreferencesHelper.saveStations(context, stations, syncToApi = false)
                }
                StateHelper.isPlaylistChangePending = true
                val playlistIntent = Intent(context, StreamingService::class.java).apply {
                    action = "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST"
                }
                context.startService(playlistIntent)

                // Notify UI about station updates
                val updateIntent = Intent(Keys.ACTION_STATIONS_UPDATED)
                LocalBroadcastManager.getInstance(context).sendBroadcast(updateIntent)

                // Apply settings (excluding API credentials)
                if (settings.isNotEmpty()) {
                    val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    val excludedKeys = setOf(
                        Keys.PREF_API_ENDPOINT,
                        Keys.PREF_API_USERNAME,
                        Keys.PREF_API_PASSWORD,
                        Keys.PREF_API_TOKEN,
                        Keys.PREF_API_SYNC_ERROR,
                        Keys.PREF_API_SYNC_ERROR_MESSAGE,
                        Keys.KEY_STATIONS,        // Handled separately
                        Keys.KEY_STATION_LISTS,   // Handled separately
                        "lists",                  // Prevent saving as string in settings
                        "stations"                // Prevent saving as string in settings
                    )
                    for ((key, value) in settings) {
                        if (key in excludedKeys) continue
                        if (value == null) {
                            Log.d("API SYNC", "Skipping null value for key: $key")
                            continue
                        }
                        try {
                            when (value) {
                                is Boolean -> {
                                    Log.d("API SYNC", "Setting Boolean: $key = $value")
                                    editor.putBoolean(key, value)
                                }
                                is Number -> {
                                    val doubleValue = value.toDouble()
                                    // Check if value is a whole number (no fractional part)
                                    if (doubleValue == doubleValue.toLong().toDouble()) {
                                        Log.d("API SYNC", "Setting Int: $key = ${value.toInt()}")
                                        editor.putInt(key, value.toInt())
                                    } else {
                                        Log.d("API SYNC", "Setting Float: $key = ${value.toFloat()}")
                                        editor.putFloat(key, value.toFloat())
                                    }
                                }
                                is String -> {
                                    Log.d("API SYNC", "Setting String: $key = $value")
                                    editor.putString(key, value)
                                }
                                else -> {
                                    // Skip complex types (Maps, Lists, etc.)
                                    Log.w("API SYNC", "Skipping unsupported type for key: $key, type: ${value::class.java.name}, value: $value")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("API SYNC", "Error applying setting $key: ${e.message}", e)
                        }
                    }
                    editor.apply()

                    // Notify about equalizer settings update if any EQ settings were received
                    val hasEqSettings = settings.keys.any {
                        it == Keys.PREF_EQ_ENABLED ||
                        it == Keys.PREF_EQ_PRESET ||
                        it.startsWith(Keys.PREF_EQ_BAND_PREFIX)
                    }
                    if (hasEqSettings) {
                        Log.d("API SYNC", "EQ settings received - sending broadcast")
                        context.sendBroadcast(
                            Intent(Keys.ACTION_EQUALIZER_SETTINGS_UPDATED).setPackage(context.packageName)
                        )
                    }
                }

                val selectedListIndex = try {
                    (settings[Keys.KEY_SELECTED_LIST] as? Number)?.toInt() ?: 0
                } catch (e: Exception) {
                    0
                }
                ApiResult.Success(SyncData(stations, parsedLists, selectedListIndex, settings))
            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                errorResult("Authentication failed - please check credentials")
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                errorResult("Failed to read profile: $errorBody")
            }
        } catch (e: Exception) {
            errorResult("Connection error: ${e.message}")
        } finally {
            connection.disconnect()
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

        val endpoint = getApiEndpoint(context)
        val url = URL("$endpoint/api/user/data")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            // Get stations (current list) and all lists
            val stations = PreferencesHelper.getStations(context)
            val allLists = PreferencesHelper.getStationLists(context)
            val selectedListIndex = PreferencesHelper.getSelectedListIndex(context)

            // Get settings (excluding API credentials and stations - stations are pushed separately)
            val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
            val allSettings = prefs.all.toMutableMap()
            val excludedKeys = setOf(
                Keys.PREF_API_ENDPOINT,
                Keys.PREF_API_USERNAME,
                Keys.PREF_API_PASSWORD,
                Keys.PREF_API_TOKEN,
                Keys.KEY_STATIONS,        // Stations are pushed separately as array
                Keys.KEY_STATION_LISTS,   // Lists are pushed separately as object
                "lists",                  // Prevent lists being synced as string in settings
                Keys.KEY_SELECTED_LIST,   // Selected list is pushed in settings
                Keys.PREF_API_SYNC_ERROR,
                Keys.PREF_API_SYNC_ERROR_MESSAGE,
                Keys.KEY_DEV_FOR_YOU_ITEMS,      // Dev-only items, not synced
                Keys.KEY_DEV_WHAT_TO_LISTEN_ITEMS,  // Dev-only items, not synced
                Keys.PREF_UPDATE_AVAILABLE,      // Local-only flag, not synced
                "alarms"                         // Legacy field, no longer used
            )
            excludedKeys.forEach { allSettings.remove(it) }

            // Add selected_list index to settings for sync
            allSettings[Keys.KEY_SELECTED_LIST] = selectedListIndex

            // Build data object with both stations (legacy) and lists (new)
            val dataObject = JsonObject().apply {
                add("stations", Gson().toJsonTree(stations))  // Legacy: aktuelle Liste
                add("lists", Gson().toJsonTree(allLists))     // Neu: alle Listen
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
        } finally {
            connection.disconnect()
        }
    }
}
