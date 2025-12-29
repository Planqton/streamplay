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
     * Entfernt alle leeren Stationslisten (außer es ist die letzte)
     * Die letzte Liste wird NICHT gelöscht, auch wenn sie leer ist!
     * @return true wenn Listen entfernt wurden
     */
    fun cleanupEmptyLists(context: Context): Boolean {
        val lists = getStationLists(context)

        // Keine Listen vorhanden - default erstellen mit Default-Station
        if (lists.isEmpty()) {
            val defaultStation = createDefaultStation()
            val defaultLists = mutableMapOf<String, MutableList<StationItem>>()
            defaultLists[Keys.DEFAULT_LIST_NAME] = mutableListOf(defaultStation)
            val json = Gson().toJson(defaultLists)
            getPrefs(context).edit().putString(Keys.KEY_STATION_LISTS, json).apply()
            android.util.Log.d("PreferencesHelper", "cleanupEmptyLists: Keine Listen vorhanden, '${Keys.DEFAULT_LIST_NAME}' mit Default-Station erstellt")
            return true
        }

        // Nur eine Liste - nicht löschen (auch wenn leer)
        if (lists.size == 1) {
            return false
        }

        // Mehrere Listen - leere entfernen, aber mindestens eine behalten
        val nonEmptyLists = lists.filterValues { it.isNotEmpty() }.toMutableMap()

        // Wenn alle Listen leer sind, die erste behalten und Default-Station hinzufügen
        if (nonEmptyLists.isEmpty()) {
            val firstListName = lists.keys.first()
            nonEmptyLists[firstListName] = mutableListOf(createDefaultStation())
            android.util.Log.d("PreferencesHelper", "cleanupEmptyLists: Alle Listen leer, behalte '$firstListName' mit Default-Station")
        }

        // Prüfen ob etwas entfernt wurde
        if (nonEmptyLists.size == lists.size) {
            return false
        }

        android.util.Log.d("PreferencesHelper", "cleanupEmptyLists: entfernt ${lists.size - nonEmptyLists.size} leere Listen")

        // Speichern ohne API-Sync (wird separat gemacht)
        val json = Gson().toJson(nonEmptyLists)
        getPrefs(context).edit().putString(Keys.KEY_STATION_LISTS, json).apply()

        // Index anpassen falls nötig
        val currentIndex = getSelectedListIndex(context)
        if (currentIndex >= nonEmptyLists.size) {
            setSelectedListIndex(context, 0)
        }

        return true
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
     * Löscht eine Liste
     * @return true wenn erfolgreich, false wenn Liste nicht gefunden oder letzte Liste
     */
    fun deleteList(context: Context, listName: String): Boolean {
        val lists = getStationLists(context)

        // Mindestens eine Liste muss bleiben
        if (lists.size <= 1) {
            return false
        }

        // Prüfe ob Liste existiert
        if (!lists.containsKey(listName)) {
            return false
        }

        // Liste entfernen
        lists.remove(listName)
        saveStationLists(context, lists)

        // Wenn gelöschte Liste die aktuelle war, zur ersten wechseln
        val currentIndex = getSelectedListIndex(context)
        val listNames = lists.keys.toList()
        if (currentIndex >= listNames.size) {
            setSelectedListIndex(context, 0)
        }

        android.util.Log.d("PreferencesHelper", "Liste gelöscht: '$listName'")
        return true
    }

    /**
     * Erstellt eine neue leere Liste
     * @return Name der erstellten Liste oder null wenn Name bereits existiert
     */
    fun createNewList(context: Context, listName: String): String? {
        val trimmedName = listName.trim()
        if (trimmedName.isEmpty()) return null

        val lists = getStationLists(context)

        // Prüfe ob Name bereits existiert
        if (lists.containsKey(trimmedName)) {
            return null
        }

        // Neue leere Liste erstellen
        lists[trimmedName] = mutableListOf()
        saveStationLists(context, lists)

        // Zur neuen Liste wechseln
        val newIndex = lists.keys.toList().indexOf(trimmedName)
        setSelectedListIndex(context, newIndex)

        android.util.Log.d("PreferencesHelper", "Neue Liste erstellt: '$trimmedName'")
        return trimmedName
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

        // Neue Struktur erstellen - falls keine Stationen vorhanden, Default-Station hinzufügen
        val stationsForList = if (oldStations.isEmpty()) {
            mutableListOf(createDefaultStation())
        } else {
            oldStations
        }

        val newLists = mutableMapOf<String, MutableList<StationItem>>()
        newLists[Keys.DEFAULT_LIST_NAME] = stationsForList

        // Speichern (ohne API-Sync, da keine echte Änderung)
        saveStationLists(context, newLists, syncToApi = false)
        setSelectedListName(context, Keys.DEFAULT_LIST_NAME)

        android.util.Log.i("PreferencesHelper", "Migration zu Multi-Listen abgeschlossen: ${stationsForList.size} Stationen")
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

    /**
     * Erstellt die Default-Station für neue/leere Listen
     */
    private fun createDefaultStation(): StationItem {
        return StationItem(
            uuid = java.util.UUID.randomUUID().toString(),
            stationName = "Onlineradio SOFT ROCK",
            streamURL = "https://stream.0nlineradio.com/soft-rock?ref=radiobrowser",
            iconURL = "https://i.ibb.co/SJFG3bt/soft-rock.jpg"
        )
    }
}
