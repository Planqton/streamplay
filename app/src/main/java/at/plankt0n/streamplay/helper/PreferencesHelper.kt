package at.plankt0n.streamplay.helper

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceManager
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.data.StationItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        maybePushCouchDb(context)
    }

    fun clearStations(context: Context) {
        getPrefs(context).edit().remove(KEY_STATIONS).apply()
        maybePushCouchDb(context)
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

    fun maybePushCouchDb(context: Context) {
        if (CouchDbHelper.isApplyingPrefs) return
        val settings = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        val auto = settings.getBoolean(Keys.PREF_AUTOSYNC_COUCHDB_STARTUP, false)
        val endpoint = settings.getString(Keys.PREF_COUCHDB_ENDPOINT, "") ?: ""
        if (!auto || endpoint.isBlank()) return

        val user = settings.getString(Keys.PREF_COUCHDB_USERNAME, "") ?: ""
        val pass = settings.getString(Keys.PREF_COUCHDB_PASSWORD, "") ?: ""
        val showLogs = settings.getBoolean(Keys.PREF_COUCHDB_SHOW_LOGS, true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                CouchDbHelper.pushPrefs(context, endpoint, user, pass)
                if (showLogs) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.couchdb_push_success, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (showLogs) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.couchdb_push_failed, e.message ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}
