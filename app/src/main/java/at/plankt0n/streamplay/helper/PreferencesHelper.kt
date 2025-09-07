package at.plankt0n.streamplay.helper

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.data.StationItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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

    fun saveStations(
        context: Context,
        stationList: List<StationItem>,
        syncRemote: Boolean = true
    ) {
        val json = Gson().toJson(stationList)
        getPrefs(context).edit().putString(KEY_STATIONS, json).apply()

        if (syncRemote) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (prefs.getBoolean(Keys.PREF_USE_JSONBIN, false)) {
                val url = prefs.getString(Keys.PREF_JSONBIN_URL, "") ?: ""
                val key = prefs.getString(Keys.PREF_JSONBIN_MASTER_KEY, "") ?: ""
                if (url.isNotBlank() && key.isNotBlank()) {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            StationImportHelper.exportStationsToJsonBin(url, key, stationList)
                        } catch (_: Exception) {
                        }
                    }
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
