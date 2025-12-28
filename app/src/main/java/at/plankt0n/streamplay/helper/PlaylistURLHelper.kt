package at.plankt0n.streamplay.helper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object PlaylistURLHelper {

    suspend fun resolvePlaylistUrl(playlistUrl: String): String? = withContext(Dispatchers.IO) {
        val TAG = "PlaylistURLHelper"
        val playlistLines = mutableListOf<String>()

        var urlConnection: HttpURLConnection? = null
        try {
            urlConnection = URL(playlistUrl).openConnection() as HttpURLConnection
            urlConnection.connectTimeout = 5000
            urlConnection.readTimeout = 5000

            urlConnection.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        playlistLines.add(trimmed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Laden: ${e.localizedMessage}")
            return@withContext null
        } finally {
            urlConnection?.disconnect()
        }

        Log.d(TAG, "📄 Gefundene Zeilen: ${playlistLines.size}")

        // Nur Zeilen mit http-Links rausfiltern
        val streamUrls = playlistLines.filter { it.startsWith("http") }

        if (streamUrls.isEmpty()) {
            Log.w(TAG, "⚠️ Keine Streams gefunden in der Playlist.")
            return@withContext null
        }

        // Streams sortieren (höchste geschätzte Qualität zuerst)
        val sortedStreams = streamUrls.sortedByDescending { url ->
            when {
                url.endsWith(".flac", true) -> 4
                url.endsWith(".aac", true) -> 3
                url.endsWith(".ogg", true) -> 2
                url.endsWith(".mp3", true) -> 1
                else -> 0
            }
        }

        Log.d(TAG, "🎶 Sortierte Streams: $sortedStreams")

        // Nacheinander prüfen, welcher erreichbar ist
        for (streamUrl in sortedStreams) {
            Log.d(TAG, "🔍 Prüfe Stream: $streamUrl")
            var testConnection: HttpURLConnection? = null
            try {
                testConnection = URL(streamUrl).openConnection() as HttpURLConnection
                testConnection.requestMethod = "HEAD"
                testConnection.connectTimeout = 3000
                testConnection.readTimeout = 3000
                testConnection.connect()

                val responseCode = testConnection.responseCode
                Log.d(TAG, "🔗 Antwortcode: $responseCode für $streamUrl")

                if (responseCode in 200..399) {
                    Log.i(TAG, "✅ Funktionierender Stream gefunden: $streamUrl")
                    testConnection.disconnect()
                    return@withContext streamUrl
                } else {
                    Log.w(TAG, "❌ Nicht erreichbar: $streamUrl")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Fehler bei $streamUrl: ${e.localizedMessage}")
            } finally {
                testConnection?.disconnect()
            }
        }

        Log.e(TAG, "❌ Kein funktionierender Stream gefunden.")
        return@withContext null
    }

}
