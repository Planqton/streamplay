package at.plankt0n.streamplay.helper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Helper class for fetching lyrics from LRCLIB API
 * Supports both synced (LRC format) and plain lyrics
 */
object LyricsHelper {

    private const val TAG = "LyricsHelper"
    private const val BASE_URL = "https://lrclib.net/api/get"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Simple in-memory cache
    private val lyricsCache = mutableMapOf<String, LyricsResult>()
    private const val MAX_CACHE_SIZE = 50

    data class LyricsResult(
        val syncedLyrics: String?,      // LRC format with timestamps
        val plainLyrics: String?,        // Plain text without timestamps
        val trackName: String?,
        val artistName: String?,
        val albumName: String?,
        val duration: Int?,
        val instrumental: Boolean = false,
        val error: String? = null
    ) {
        val hasSyncedLyrics: Boolean get() = !syncedLyrics.isNullOrBlank()
        val hasPlainLyrics: Boolean get() = !plainLyrics.isNullOrBlank()
        val hasAnyLyrics: Boolean get() = hasSyncedLyrics || hasPlainLyrics
    }

    data class LrcLine(
        val timeMs: Long,
        val text: String
    )

    /**
     * Fetch lyrics for a track
     * @param artist Artist name
     * @param title Track title
     * @return LyricsResult with synced and/or plain lyrics
     */
    suspend fun fetchLyrics(artist: String, title: String): LyricsResult = withContext(Dispatchers.IO) {
        val cacheKey = "${artist.lowercase().trim()}|${title.lowercase().trim()}"

        // Check cache first
        lyricsCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Lyrics found in cache for: $artist - $title")
            return@withContext cached
        }

        try {
            val encodedArtist = URLEncoder.encode(artist.trim(), "UTF-8")
            val encodedTitle = URLEncoder.encode(title.trim(), "UTF-8")
            val url = "$BASE_URL?artist_name=$encodedArtist&track_name=$encodedTitle"

            Log.d(TAG, "Fetching lyrics from: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "StreamPlay/1.0")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                Log.w(TAG, "No lyrics found for: $artist - $title (${response.code})")
                return@withContext LyricsResult(
                    syncedLyrics = null,
                    plainLyrics = null,
                    trackName = title,
                    artistName = artist,
                    albumName = null,
                    duration = null,
                    error = if (response.code == 404) "Keine Lyrics gefunden" else "Fehler: ${response.code}"
                )
            }

            val json = JSONObject(responseBody)

            val result = LyricsResult(
                syncedLyrics = json.optString("syncedLyrics").takeIf { it.isNotBlank() },
                plainLyrics = json.optString("plainLyrics").takeIf { it.isNotBlank() },
                trackName = json.optString("trackName").takeIf { it.isNotBlank() },
                artistName = json.optString("artistName").takeIf { it.isNotBlank() },
                albumName = json.optString("albumName").takeIf { it.isNotBlank() },
                duration = json.optInt("duration").takeIf { it > 0 },
                instrumental = json.optBoolean("instrumental", false)
            )

            // Add to cache
            if (lyricsCache.size >= MAX_CACHE_SIZE) {
                // Remove oldest entry
                lyricsCache.remove(lyricsCache.keys.firstOrNull())
            }
            lyricsCache[cacheKey] = result

            Log.d(TAG, "Lyrics fetched: synced=${result.hasSyncedLyrics}, plain=${result.hasPlainLyrics}, instrumental=${result.instrumental}")
            result

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics: ${e.message}")
            LyricsResult(
                syncedLyrics = null,
                plainLyrics = null,
                trackName = title,
                artistName = artist,
                albumName = null,
                duration = null,
                error = "Fehler: ${e.message}"
            )
        }
    }

    /**
     * Parse LRC format lyrics into timed lines
     * Format: [mm:ss.xx] Text
     */
    fun parseLrcLyrics(lrcText: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val pattern = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\]\s*(.*)""")

        for (line in lrcText.lines()) {
            val match = pattern.find(line) ?: continue
            val (minutes, seconds, millis, text) = match.destructured

            val millisValue = if (millis.length == 2) millis.toInt() * 10 else millis.toInt()
            val timeMs = minutes.toLong() * 60000 + seconds.toLong() * 1000 + millisValue

            if (text.isNotBlank()) {
                lines.add(LrcLine(timeMs, text.trim()))
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    /**
     * Find the current lyric line based on playback position
     * @param lines Parsed LRC lines
     * @param positionMs Current playback position in milliseconds
     * @return Index of current line, or -1 if before first line
     */
    fun getCurrentLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1

        for (i in lines.indices.reversed()) {
            if (positionMs >= lines[i].timeMs) {
                return i
            }
        }
        return -1
    }

    /**
     * Clear the lyrics cache
     */
    fun clearCache() {
        lyricsCache.clear()
        Log.d(TAG, "Lyrics cache cleared")
    }
}
