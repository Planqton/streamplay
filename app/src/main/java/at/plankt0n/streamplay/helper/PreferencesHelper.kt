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

    // Dev Items for Android Auto "Für mich" section
    fun getDevForYouItems(context: Context): MutableList<StationItem> {
        val json = getPrefs(context).getString(Keys.KEY_DEV_FOR_YOU_ITEMS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<MutableList<StationItem>>() {}.type
                Gson().fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }

    fun saveDevForYouItems(context: Context, items: List<StationItem>) {
        val json = Gson().toJson(items)
        getPrefs(context).edit().putString(Keys.KEY_DEV_FOR_YOU_ITEMS, json).apply()
    }

    // Dev Items for Android Auto "Was möchtest du hören?" section
    fun getDevWhatToListenItems(context: Context): MutableList<StationItem> {
        val json = getPrefs(context).getString(Keys.KEY_DEV_WHAT_TO_LISTEN_ITEMS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<MutableList<StationItem>>() {}.type
                Gson().fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }

    fun saveDevWhatToListenItems(context: Context, items: List<StationItem>) {
        val json = Gson().toJson(items)
        getPrefs(context).edit().putString(Keys.KEY_DEV_WHAT_TO_LISTEN_ITEMS, json).apply()
    }

    fun clearDevItems(context: Context) {
        getPrefs(context).edit()
            .remove(Keys.KEY_DEV_FOR_YOU_ITEMS)
            .remove(Keys.KEY_DEV_WHAT_TO_LISTEN_ITEMS)
            .apply()
    }

    // Generate random dev items
    fun generateRandomDevItems(): List<StationItem> {
        val genres = listOf("Rock", "Pop", "Jazz", "Classical", "Electronic", "Hip-Hop", "Country", "Blues", "Metal", "Indie", "R&B", "Reggae", "Folk", "Punk", "Soul")
        val moods = listOf("Chill", "Energetic", "Relaxing", "Party", "Focus", "Workout", "Sleep", "Morning", "Evening", "Road Trip")
        val adjectives = listOf("Best", "Top", "Hot", "New", "Fresh", "Classic", "Ultimate", "Pure", "True", "Real")

        return (1..15).map { _ ->
            val genre = genres.random()
            val mood = moods.random()
            val adjective = adjectives.random()
            val uuid = java.util.UUID.randomUUID().toString()

            StationItem(
                uuid = uuid,
                stationName = "$adjective $genre $mood",
                streamURL = "https://example.com/stream/$uuid",
                iconURL = "https://picsum.photos/seed/$uuid/300/300"
            )
        }
    }
}
