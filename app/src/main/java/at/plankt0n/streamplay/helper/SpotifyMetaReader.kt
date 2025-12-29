// Datei: at/plankt0n/webstream/helper/SpotifyMetaReader.kt
package at.plankt0n.streamplay.helper

import android.content.Context
import android.util.Base64
import android.util.Log
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import at.plankt0n.streamplay.data.ExtendedMetaInfo

object SpotifyMetaReader {

    // Singleton OkHttpClient mit Timeouts - wird wiederverwendet
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val tokenLock = Any()
    @Volatile
    private var accessToken: String? = null
    @Volatile
    private var tokenExpirationTime: Long = 0 // Millisekunden

    private suspend fun getAccessToken(context: Context): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        val clientId = prefs.getString(Keys.PREF_SPOTIFY_CLIENT_ID, "") ?: ""
        val clientSecret = prefs.getString(Keys.PREF_SPOTIFY_CLIENT_SECRET, "") ?: ""
        if (clientId.isBlank() || clientSecret.isBlank()) throw IOException("Spotify credentials missing")
        val credentials = "$clientId:$clientSecret"
        val encodedCredentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        // Thread-safe Token-Check
        synchronized(tokenLock) {
            val currentToken = accessToken
            if (System.currentTimeMillis() < tokenExpirationTime && currentToken != null) {
                return@withContext currentToken
            }
        }

        val requestBody = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .build()

        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Authorization", "Basic $encodedCredentials")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Spotify Auth failed: ${response.code}")

            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) throw IOException("Empty response body from Spotify Auth")
            val json = JSONObject(responseBody)
            val newToken = json.getString("access_token")
            val expiresIn = json.getLong("expires_in") // Sekunden
            synchronized(tokenLock) {
                accessToken = newToken
                tokenExpirationTime = System.currentTimeMillis() + expiresIn * 1000
            }
            Log.d("SpotifyMetaReader", "🔑 Neues Access Token geholt, gültig bis $tokenExpirationTime")
            newToken  // Return local variable to avoid race condition with volatile field
        }
    }

    suspend fun getExtendedMetaInfo(context: Context, artist: String, title: String): ExtendedMetaInfo? =
        withContext(Dispatchers.IO) {
            val token = getAccessToken(context)
            // Verwendet den Singleton OkHttpClient

            fun logAttempt(step: String, queryArtist: String?, queryTitle: String?) {
                Log.d("SpotifyMetaReader", "🔎 Versuch [$step] mit Artist='$queryArtist', Title='$queryTitle'")
            }

            fun searchTrack(queryArtist: String?, queryTitle: String?, step: String): JSONObject? {
                val query = buildString {
                    if (!queryTitle.isNullOrBlank()) append("track:$queryTitle ")
                    if (!queryArtist.isNullOrBlank()) append("artist:$queryArtist")
                }.trim()

                val searchUrl = HttpUrl.Builder()
                    .scheme("https")
                    .host("api.spotify.com")
                    .addPathSegments("v1/search")
                    .addQueryParameter("q", query)
                    .addQueryParameter("type", "track")
                    .addQueryParameter("limit", "1")
                    .build()

                logAttempt(step, queryArtist, queryTitle)

                val searchRequest = Request.Builder()
                    .url(searchUrl)
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(searchRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("SpotifyMetaReader", "❌ API-Fehler ($step): ${response.code}")
                        return null
                    }
                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrBlank()) {
                        Log.e("SpotifyMetaReader", "❌ Empty response ($step)")
                        return null
                    }
                    val json = JSONObject(responseBody)
                    return json.getJSONObject("tracks").getJSONArray("items").optJSONObject(0)
                }
            }

            // Stufe 1: Originaldaten
            var trackItem = searchTrack(artist, title, "original")

            // Stufe 2: Bereinigt
            if (trackItem == null) {
                val cleanArtist = artist.replace(Regex("(feat\\..*|&.*|vs\\..*)", RegexOption.IGNORE_CASE), "").trim()
                val cleanTitle = title.replace(Regex("\\(.*?\\)|\\[.*?\\]", RegexOption.IGNORE_CASE), "").trim()
                trackItem = searchTrack(cleanArtist, cleanTitle, "bereinigt")
            }

            // Stufe 3: Nur Titel
            if (trackItem == null) {
                trackItem = searchTrack(null, title, "nur titel")
            }

            // Stufe 4: Vertauscht (Titel und Artist vertauscht)
            if (trackItem == null) {
                trackItem = searchTrack(title, artist, "vertauscht")
            }

            // Stufe 5: Fuzzy
            if (trackItem == null) {
                val fuzzyTitle = title.lowercase()
                    .replace(Regex("[^a-z0-9 ]", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s+"), " ").trim()
                val fuzzyArtist = artist.lowercase()
                    .replace(Regex("[^a-z0-9 ]", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s+"), " ").trim()
                trackItem = searchTrack(fuzzyArtist, fuzzyTitle, "fuzzy")
            }

            // Kein Treffer
            if (trackItem == null) return@withContext null

            // Erfolgreich: Track-Infos extrahieren
            val trackId = trackItem.getString("id")
            val trackUri = trackItem.getJSONObject("external_urls").getString("spotify")
            Log.d("SpotifyMetaReader", "🔗 spotifyUrl extracted: $trackUri")
            val trackName = trackItem.getString("name")
            val trackPopularity = trackItem.getInt("popularity")
            val trackDurationMs = trackItem.getLong("duration_ms")
            val previewUrl = trackItem.optString("preview_url", null)
            val album = trackItem.getJSONObject("album")
            val albumId = album.getString("id")
            val albumName = album.getString("name")
            val albumReleaseDate = album.getString("release_date")
            val images = album.getJSONArray("images")
            val bestCoverUrl = if (images.length() > 0) images.getJSONObject(0).getString("url") else null

            val artistArray = trackItem.getJSONArray("artists")
            val firstArtist = artistArray.optJSONObject(0)
            val artistId = firstArtist?.getString("id")

            var genre = ""
            if (!artistId.isNullOrEmpty()) {
                val artistUrl = "https://api.spotify.com/v1/artists/$artistId"
                val artistRequest = Request.Builder()
                    .url(artistUrl)
                    .header("Authorization", "Bearer $token")
                    .build()

                client.newCall(artistRequest).execute().use { artistResponse ->
                    if (artistResponse.isSuccessful) {
                        val artistBody = artistResponse.body?.string()
                        if (!artistBody.isNullOrBlank()) {
                            val artistJson = JSONObject(artistBody)
                            val genres = artistJson.optJSONArray("genres")
                            if (genres != null && genres.length() > 0) {
                                genre = genres.getString(0)
                            }
                        }
                    } else {
                        Log.w("SpotifyMetaReader", "⚠️ Spotify Artist API-Fehler: ${artistResponse.code}")
                    }
                }
            }

            Log.d("SpotifyMetaReader", "ℹ️ Preview: $previewUrl Genre: $genre")

            // Album-Infos holen
            val albumUrl = "https://api.spotify.com/v1/albums/$albumId"
            val albumRequest = Request.Builder()
                .url(albumUrl)
                .header("Authorization", "Bearer $token")
                .build()

            val bestAlbumCoverUrl = client.newCall(albumRequest).execute().use { albumResponse ->
                if (albumResponse.isSuccessful) {
                    val albumBody = albumResponse.body?.string()
                    if (!albumBody.isNullOrBlank()) {
                        val albumJson = JSONObject(albumBody)
                        val albumImages = albumJson.getJSONArray("images")
                        if (albumImages.length() > 0) albumImages.getJSONObject(0).getString("url") else null
                    } else null
                } else {
                    Log.w("SpotifyMetaReader", "⚠️ Spotify Album API-Fehler: ${albumResponse.code}")
                    null
                }
            }

            return@withContext ExtendedMetaInfo(
                trackName = trackName,
                artistName = artist,
                albumName = albumName,
                albumReleaseDate = albumReleaseDate,
                spotifyUrl = trackUri,
                bestCoverUrl = bestAlbumCoverUrl ?: bestCoverUrl,
                durationMs = trackDurationMs,
                popularity = trackPopularity,
                previewUrl = previewUrl,
                genre = genre
            )
        }

}
