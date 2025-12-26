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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PreferencesHelper {

    // Persistenter Scope für Fire-and-Forget Operationen
    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getStations(context: Context): MutableList<StationItem> {
        val json = getPrefs(context).getString(Keys.KEY_STATIONS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<MutableList<StationItem>>() {}.type
                Gson().fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                // JSON ist korrupt - leere Liste zurückgeben und korrupte Daten löschen
                android.util.Log.e("PreferencesHelper", "Korrupte Station-Daten gefunden, lösche: ${e.message}")
                getPrefs(context).edit().remove(Keys.KEY_STATIONS).apply()
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }

    fun saveStations(context: Context, stationList: List<StationItem>, syncToApi: Boolean = true) {
        val json = Gson().toJson(stationList)
        getPrefs(context).edit().putString(Keys.KEY_STATIONS, json).apply()
        if (syncToApi) {
            helperScope.launch {
                StreamplayApiHelper.pushIfSyncEnabled(context)
            }
        }
    }

    fun clearStations(context: Context) {
        getPrefs(context).edit().remove(Keys.KEY_STATIONS).apply()
    }

    fun getLastPlayedStreamIndex(context: Context): Int {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(Keys.PREF_LAST_PLAYED_STREAM_INDEX, 0)
    }

    fun setLastPlayedStreamIndex(context: Context, index: Int) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putInt(Keys.PREF_LAST_PLAYED_STREAM_INDEX, index)
            .apply()
    }
}
