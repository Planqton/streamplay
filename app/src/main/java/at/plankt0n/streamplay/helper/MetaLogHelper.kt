package at.plankt0n.streamplay.helper

import android.content.Context
import android.util.Log
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

        if (list.isNotEmpty()) {
            val newest = list[0]
            val sameMeta = newest.station == entry.station &&
                newest.title == entry.title &&
                newest.artist == entry.artist &&
                newest.url == entry.url

            if (sameMeta && entry.manual && !newest.manual) {
                list[0] = entry
                val json = Gson().toJson(list)
                prefs(context).edit().putString(KEY_LOGS, json).apply()
                Log.d("MetaLogHelper", "Saved log entry: ${Gson().toJson(entry)}")
                return
            }
        }

        list.add(0, entry) // newest first
        val json = Gson().toJson(list)
        prefs(context).edit().putString(KEY_LOGS, json).apply()
        Log.d("MetaLogHelper", "Saved log entry: ${Gson().toJson(entry)}")
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
