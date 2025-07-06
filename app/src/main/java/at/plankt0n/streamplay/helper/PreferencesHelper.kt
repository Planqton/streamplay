package at.plankt0n.streamplay.helper

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.Keys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PreferencesHelper {

    private const val PREFS_NAME = Keys.PREFS_NAME
    private const val KEY_STATIONS = Keys.KEY_STATIONS
    private const val PREF_LAST_PLAYED_STREAM_INDEX = Keys.PREF_LAST_PLAYED_STREAM_INDEX

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
