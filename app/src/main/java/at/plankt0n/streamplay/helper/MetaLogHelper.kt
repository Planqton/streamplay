package at.plankt0n.streamplay.helper

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import at.plankt0n.streamplay.data.MetaLogEntry
import at.plankt0n.streamplay.Keys

object MetaLogHelper {
    private const val PREFS_NAME = Keys.KEY_META_LOGS_PREFS
    private const val KEY_LOGS = Keys.KEY_META_LOGS

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun addLog(context: Context, entry: MetaLogEntry) {
        val list = getLogs(context)
        list.add(0, entry) // newest first
        val json = Gson().toJson(list)
        prefs(context).edit().putString(KEY_LOGS, json).apply()
    }

    fun addManualLog(context: Context, entry: MetaLogEntry) {
        val manualEntry = entry.copy(manual = true)
        val list = getLogs(context)
        if (list.isNotEmpty()) {
            val first = list[0]
            if (first.station == manualEntry.station &&
                first.title == manualEntry.title &&
                first.artist == manualEntry.artist &&
                first.url == manualEntry.url &&
                !first.manual
            ) {
                list[0] = manualEntry
            } else {
                list.add(0, manualEntry)
            }
        } else {
            list.add(manualEntry)
        }
        val json = Gson().toJson(list)
        prefs(context).edit().putString(KEY_LOGS, json).apply()
    }

    fun getLogs(context: Context): MutableList<MetaLogEntry> {
        val json = prefs(context).getString(KEY_LOGS, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<MetaLogEntry>>() {}.type
            Gson().fromJson(json, type)
        } else mutableListOf()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_LOGS).apply()
    }
}
