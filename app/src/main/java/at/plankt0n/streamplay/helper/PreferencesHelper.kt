package at.plankt0n.streamplay.helper

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import at.plankt0n.streamplay.data.StationItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    fun saveStations(context: Context, stationList: List<StationItem>, syncToApi: Boolean = true) {
        val json = Gson().toJson(stationList)
        getPrefs(context).edit().putString(KEY_STATIONS, json).apply()
        if (syncToApi) {
            CoroutineScope(Dispatchers.IO).launch {
                StreamplayApiHelper.pushIfSyncEnabled(context)
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
