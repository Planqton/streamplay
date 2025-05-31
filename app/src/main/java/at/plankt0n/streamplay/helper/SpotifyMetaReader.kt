// Datei: at/plankt0n/webstream/helper/SpotifyMetaReader.kt
package at.plankt0n.streamplay.helper

import android.content.Context
import android.util.Base64
import android.util.Log
import at.plankt0n.streamplay.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import at.plankt0n.streamplay.data.ExtendedMetaInfo

object SpotifyMetaReader {

    private var accessToken: String? = null
    private var tokenExpirationTime: Long = 0 // Millisekunden

    private suspend fun getAccessToken(context: Context): String = withContext(Dispatchers.IO) {
        val clientId = context.getString(R.string.spotify_client_id)
        val clientSecret = context.getString(R.string.spotify_client_secret)
        val credentials = "$clientId:$clientSecret"
        val encodedCredentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        // Wenn Token noch gültig ist, gleich zurückgeben
        if (System.currentTimeMillis() < tokenExpirationTime && accessToken != null) {
            return@withContext accessToken!!
        }

        val requestBody = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .build()

        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Authorization", "Basic $encodedCredentials")
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Spotify Auth failed: ${response.code}")

            val json = JSONObject(response.body?.string() ?: "")
            accessToken = json.getString("access_token")
            val expiresIn = json.getLong("expires_in") // Sekunden
            tokenExpirationTime = System.currentTimeMillis() + expiresIn * 1000
            Log.d("SpotifyMetaReader", "🔑 Neues Access Token geholt, gültig bis $tokenExpirationTime")
            accessToken!!
        }
    }

    suspend fun getExtendedMetaInfo(context: Context, artist: String, title: String): ExtendedMetaInfo? =
        withContext(Dispatchers.IO) {
            val token = getAccessToken(context)
            val client = OkHttpClient()

            // 1️⃣ Suche nach Track (Search)
            val query = "track:$title artist:$artist"
            val searchUrl = HttpUrl.Builder()
                .scheme("https")
                .host("api.spotify.com")
                .addPathSegments("v1/search")
                .addQueryParameter("q", query)
                .addQueryParameter("type", "track")
                .addQueryParameter("limit", "1")
                .build()

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $token")
                .build()

            val searchResponse = client.newCall(searchRequest).execute()
            if (!searchResponse.isSuccessful) {
                Log.e("SpotifyMetaReader", "❌ Spotify Search API-Fehler: ${searchResponse.code}")
                return@withContext null
            }

            val searchJson = JSONObject(searchResponse.body?.string() ?: "")
            val trackItem = searchJson.getJSONObject("tracks").getJSONArray("items").optJSONObject(0)
                ?: return@withContext null

            val trackId = trackItem.getString("id")
            val trackUri = trackItem.getJSONObject("external_urls").getString("spotify")
            val trackName = trackItem.getString("name")
            val trackPopularity = trackItem.getInt("popularity")
            val trackDurationMs = trackItem.getLong("duration_ms")
            val album = trackItem.getJSONObject("album")
            val albumId = album.getString("id")
            val albumName = album.getString("name")
            val albumReleaseDate = album.getString("release_date")

            // Bestes Cover-Bild zum Track (höchste Auflösung)
            val images = album.getJSONArray("images")
            val bestCoverUrl = if (images.length() > 0) images.getJSONObject(0).getString("url") else null

            // 2️⃣ Album-Infos holen (Album-Endpoint)
            val albumUrl = "https://api.spotify.com/v1/albums/$albumId"
            val albumRequest = Request.Builder()
                .url(albumUrl)
                .header("Authorization", "Bearer $token")
                .build()

            val albumResponse = client.newCall(albumRequest).execute()
            if (!albumResponse.isSuccessful) {
                Log.w("SpotifyMetaReader", "⚠️ Spotify Album API-Fehler: ${albumResponse.code}")
                return@withContext ExtendedMetaInfo(
                    trackName, artist, albumName, albumReleaseDate,
                    trackUri, bestCoverUrl, trackDurationMs, trackPopularity
                )
            }

            val albumJson = JSONObject(albumResponse.body?.string() ?: "")
            val albumImages = albumJson.getJSONArray("images")
            val bestAlbumCoverUrl = if (albumImages.length() > 0) albumImages.getJSONObject(0).getString("url") else null

            // Ausgabe
            return@withContext ExtendedMetaInfo(
                trackName, artist, albumName, albumReleaseDate,
                trackUri, bestAlbumCoverUrl ?: bestCoverUrl, trackDurationMs, trackPopularity
            )
        }

}
