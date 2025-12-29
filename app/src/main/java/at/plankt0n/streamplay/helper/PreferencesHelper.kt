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

    /**
     * Lädt Stationen der aktuell ausgewählten Liste
     * Unterstützt sowohl alte (einzelne Liste) als auch neue (Multi-Listen) Struktur
     */
    fun getStations(context: Context): MutableList<StationItem> {
        // Prüfe zuerst Multi-Listen-Struktur
        if (hasMultiListStructure(context)) {
            val lists = getStationLists(context)
            val selectedList = getSelectedListName(context)
            return lists[selectedList]?.toMutableList() ?: mutableListOf()
        }

        // Fallback: Alte einzelne Stationsliste (für Rückwärtskompatibilität)
        val json = getPrefs(context).getString(Keys.KEY_STATIONS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<MutableList<StationItem>>() {}.type
                Gson().fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                android.util.Log.e("PreferencesHelper", "Korrupte Station-Daten gefunden, lösche: ${e.message}")
                getPrefs(context).edit().remove(Keys.KEY_STATIONS).apply()
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }

    /**
     * Speichert Stationen in die aktuell ausgewählte Liste
     * Unterstützt sowohl alte (einzelne Liste) als auch neue (Multi-Listen) Struktur
     */
    fun saveStations(context: Context, stationList: List<StationItem>, syncToApi: Boolean = true) {
        // Prüfe ob Multi-Listen-Struktur existiert
        if (hasMultiListStructure(context)) {
            val lists = getStationLists(context)
            val selectedList = getSelectedListName(context)
            lists[selectedList] = stationList.toMutableList()
            saveStationLists(context, lists, syncToApi)
            return
        }

        // Fallback: Alte einzelne Stationsliste
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

    // ==================== MULTI-LISTEN SUPPORT ====================

    /**
     * Lädt alle Stationslisten aus SharedPreferences
     * Struktur: Map<String, List<StationItem>> z.B. {"default": [...], "GTA Stations": [...]}
     */
    fun getStationLists(context: Context): MutableMap<String, MutableList<StationItem>> {
        val json = getPrefs(context).getString(Keys.KEY_STATION_LISTS, null)
        android.util.Log.d("PreferencesHelper", "getStationLists: json=${json?.take(100)}...")
        return if (json != null) {
            try {
                val type = object : TypeToken<MutableMap<String, MutableList<StationItem>>>() {}.type
                val result: MutableMap<String, MutableList<StationItem>> = Gson().fromJson(json, type) ?: mutableMapOf()
                android.util.Log.d("PreferencesHelper", "getStationLists: parsed ${result.size} lists: ${result.keys}")
                result
            } catch (e: Exception) {
                android.util.Log.e("PreferencesHelper", "Korrupte Listen-Daten: ${e.message}")
                mutableMapOf()
            }
        } else {
            android.util.Log.d("PreferencesHelper", "getStationLists: no json found, returning empty")
            mutableMapOf()
        }
    }

    /**
     * Speichert alle Stationslisten
     */
    fun saveStationLists(context: Context, lists: Map<String, List<StationItem>>, syncToApi: Boolean = true) {
        android.util.Log.d("PreferencesHelper", "saveStationLists: saving ${lists.size} lists: ${lists.keys}")
        val json = Gson().toJson(lists)
        android.util.Log.d("PreferencesHelper", "saveStationLists: json=${json.take(100)}...")
        getPrefs(context).edit().putString(Keys.KEY_STATION_LISTS, json).apply()
        if (syncToApi) {
            helperScope.launch {
                StreamplayApiHelper.pushIfSyncEnabled(context)
            }
        }
    }

    /**
     * Gibt den Index der aktuell ausgewählten Liste zurück
     */
    fun getSelectedListIndex(context: Context): Int {
        return try {
            getPrefs(context).getInt(Keys.KEY_SELECTED_LIST, 0)
        } catch (e: ClassCastException) {
            // Migration von altem String-Format zu Int
            val prefs = getPrefs(context)
            prefs.edit()
                .remove(Keys.KEY_SELECTED_LIST)
                .putInt(Keys.KEY_SELECTED_LIST, 0)
                .commit()
            android.util.Log.d("PreferencesHelper", "Migriert selected_list von String zu Int: 0")
            0
        }
    }

    /**
     * Setzt den Index der aktuell ausgewählten Liste
     */
    fun setSelectedListIndex(context: Context, index: Int) {
        getPrefs(context).edit().putInt(Keys.KEY_SELECTED_LIST, index).apply()
    }

    /**
     * Gibt den Namen der aktuell ausgewählten Liste zurück
     */
    fun getSelectedListName(context: Context): String {
        val lists = getListNames(context)
        val index = getSelectedListIndex(context)
        return if (index in lists.indices) {
            lists[index]
        } else {
            lists.firstOrNull() ?: Keys.DEFAULT_LIST_NAME
        }
    }

    /**
     * Setzt die ausgewählte Liste per Name (konvertiert zu Index)
     */
    fun setSelectedListName(context: Context, listName: String) {
        val lists = getListNames(context)
        val index = lists.indexOf(listName)
        if (index >= 0) {
            setSelectedListIndex(context, index)
        }
    }

    /**
     * Gibt alle verfügbaren Listennamen zurück
     */
    fun getListNames(context: Context): List<String> {
        val lists = getStationLists(context)
        val result = if (lists.isEmpty()) {
            listOf(Keys.DEFAULT_LIST_NAME)
        } else {
            lists.keys.toList()
        }
        android.util.Log.d("PreferencesHelper", "getListNames: returning $result")
        return result
    }

    /**
     * Benennt eine Liste um
     * @return true wenn erfolgreich, false wenn Name bereits existiert oder Liste nicht gefunden
     */
    fun renameList(context: Context, oldName: String, newName: String): Boolean {
        val trimmedNewName = newName.trim()
        if (trimmedNewName.isEmpty()) return false

        val lists = getStationLists(context)

        // Prüfe ob neuer Name bereits existiert
        if (lists.containsKey(trimmedNewName) && trimmedNewName != oldName) {
            return false
        }

        // Prüfe ob alte Liste existiert
        if (!lists.containsKey(oldName)) {
            return false
        }

        // Stationen der alten Liste holen
        val stations = lists.remove(oldName) ?: return false

        // Mit neuem Namen speichern
        lists[trimmedNewName] = stations
        saveStationLists(context, lists)

        android.util.Log.d("PreferencesHelper", "Liste umbenannt: '$oldName' -> '$trimmedNewName'")
        return true
    }

    /**
     * Prüft ob Multi-Listen-Struktur bereits existiert
     */
    fun hasMultiListStructure(context: Context): Boolean {
        return getPrefs(context).contains(Keys.KEY_STATION_LISTS)
    }

    /**
     * Migriert alte einzelne Stationsliste zur neuen Multi-Listen-Struktur
     * Gibt true zurück wenn Migration durchgeführt wurde, false wenn bereits migriert
     */
    fun migrateToMultiList(context: Context): Boolean {
        // Bereits migriert?
        if (hasMultiListStructure(context)) {
            return false
        }

        // Alte Stationen laden (direkt aus KEY_STATIONS)
        val json = getPrefs(context).getString(Keys.KEY_STATIONS, null)
        val oldStations: MutableList<StationItem> = if (json != null) {
            try {
                val type = object : TypeToken<MutableList<StationItem>>() {}.type
                Gson().fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }

        // Neue Struktur erstellen
        val newLists = mutableMapOf<String, MutableList<StationItem>>()
        newLists[Keys.DEFAULT_LIST_NAME] = oldStations

        // Speichern (ohne API-Sync, da keine echte Änderung)
        saveStationLists(context, newLists, syncToApi = false)
        setSelectedListName(context, Keys.DEFAULT_LIST_NAME)

        android.util.Log.i("PreferencesHelper", "Migration zu Multi-Listen abgeschlossen: ${oldStations.size} Stationen")
        return true
    }

    // ==================== ENDE MULTI-LISTEN SUPPORT ====================

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
