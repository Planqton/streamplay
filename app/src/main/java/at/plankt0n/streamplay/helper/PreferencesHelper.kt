package at.plankt0n.streamplay.helper

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.data.StationItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

object PreferencesHelper {

    private const val PREFS_NAME = "MyPrefs"
    private const val KEY_STATIONS = "stations"
    private const val PREF_LAST_PLAYED_STREAM_INDEX = "last_played_stream_index"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getStations(context: Context): MutableList<StationItem> {
        val json = getPrefs(context).getString(KEY_STATIONS, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<StationItem>>() {}.type
            Gson().fromJson(json, type)
        } else {
            mutableListOf()
        }
    }

    fun saveStations(context: Context, stationList: List<StationItem>) {
        val json = Gson().toJson(stationList)
        getPrefs(context).edit().putString(KEY_STATIONS, json).apply()

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val url = prefs.getString(Keys.PREF_JSON_BIN_URL, null)
        val key = prefs.getString(Keys.PREF_JSON_BIN_KEY, null)
        val enabled = prefs.getBoolean(Keys.PREF_AUTOSYNC_JSONBIN_STARTUP, false)

        if (enabled && !url.isNullOrBlank() && !key.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "PUT"
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("X-Master-Key", key)
                        doOutput = true
                        outputStream.use { it.write(json.toByteArray()) }
                        inputStream.close()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun clearStations(context: Context) {
        getPrefs(context).edit().remove(KEY_STATIONS).apply()
    }

    fun getLastPlayedStreamIndex(context: Context): Int {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(PREF_LAST_PLAYED_STREAM_INDEX, 0)
    }

    fun setLastPlayedStreamIndex(context: Context, index: Int) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putInt(PREF_LAST_PLAYED_STREAM_INDEX, index)
            .apply()
    }
}
