package at.plankt0n.streamplay.helper

import android.util.Base64
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

object SpotifyTokenHelper {

    private const val CLIENT_ID = "cc55a94b922c496a84c4a725242a313b"
    private const val CLIENT_SECRET = "DEIN_CLIENT_SECRET"
    private var accessToken: String? = null
    private var lastFetchTime: Long = 0

    fun getAccessToken(callback: (String?) -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (accessToken != null && currentTime - lastFetchTime < 3600_000) {
            callback(accessToken)
            return
        }

        val client = OkHttpClient()
        val credentials = "$CLIENT_ID:$CLIENT_SECRET"
        val basicAuth = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        val body = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .build()

        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .addHeader("Authorization", basicAuth)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    accessToken = json.optString("access_token", null)
                    lastFetchTime = System.currentTimeMillis()
                    callback(accessToken)
                } else {
                    callback(null)
                }
            }
        })
    }
}
