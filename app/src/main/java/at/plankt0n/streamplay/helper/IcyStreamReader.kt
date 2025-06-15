package at.plankt0n.streamplay.helper

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.net.URL
import java.nio.charset.Charset
import kotlin.concurrent.thread

class IcyStreamReader(
    private val context: Context,
    private val streamUrl: String,
    private val onMetadataReceived: (artist: String?, title: String?) -> Unit
) {
    private var running = false

    fun start() {
        Log.d("ICY", "🔄 Starte IcyStreamReader für URL: $streamUrl")
        thread {
            try {
                val conn = URL(streamUrl).openConnection()
                conn.setRequestProperty("Icy-MetaData", "1")
                conn.setRequestProperty("Connection", "close")
                conn.setRequestProperty("Accept", null)
                conn.connect()

                val metaInt = conn.getHeaderFieldInt("icy-metaint", -1)
                if (metaInt == -1) {
                    Log.w("ICY", "❌ Kein icy-metaint Header vorhanden – keine Metadaten verfügbar.")
                    return@thread
                }
                Log.d("ICY", "✅ icy-metaint: $metaInt")

                val input = BufferedInputStream(conn.getInputStream())

                val audioBuffer = ByteArray(metaInt)
                running = true

                while (running) {
                    // 1️⃣ Genau metaInt Bytes "überspringen" (Audio-Daten)
                    var bytesSkipped = 0
                    while (bytesSkipped < metaInt) {
                        val toRead = metaInt - bytesSkipped
                        val read = input.read(audioBuffer, 0, toRead)
                        if (read == -1) {
                            running = false
                            break
                        }
                        bytesSkipped += read
                    }
                    if (!running) break

                    // 2️⃣ 1 Byte für Metadaten-Länge lesen
                    val metaLenByte = input.read()
                    if (metaLenByte == -1) {
                        running = false
                        break
                    }
                    val metaLen = metaLenByte * 16
                    if (metaLen > 0) {
                        // 3️⃣ Metadaten selbst lesen
                        val metaData = ByteArray(metaLen)
                        var read = 0
                        while (read < metaLen) {
                            val r = input.read(metaData, read, metaLen - read)
                            if (r == -1) {
                                running = false
                                break
                            }
                            read += r
                        }
                        val metaString = String(metaData, Charset.forName("UTF-8")).trim { it <= ' ' || it == '\u0000' }
                        if (metaString.isNotEmpty()) {
                 //           Log.d("ICY", "🎶 Metadaten-String: $metaString")
                            val icyTitle = "StreamTitle='(.*?)';".toRegex().find(metaString)?.groupValues?.get(1)
                            icyTitle?.let {
                                val (artist, title) = splitArtistAndTitle(it)
                                Log.d("ICY", "🎶 Artist: '$artist' | Title: '$title'")
                                onMetadataReceived(artist, title)
                            }
                        }
                    }
                }
                input.close()
                Log.d("ICY", "❌ IcyStreamReader gestoppt oder Stream zu Ende.")
            } catch (e: Exception) {
                Log.e("ICY", "❌ Fehler: ${e.localizedMessage}", e)
            }
        }
    }

    fun stop() {
        running = false
    }

    private fun splitArtistAndTitle(icyTitle: String): Pair<String, String> {
        val delimiters = listOf(" - ", " / ", ": ")
        for (delimiter in delimiters) {
            if (icyTitle.contains(delimiter)) {
                val parts = icyTitle.split(delimiter, limit = 2)
                if (parts.size == 2) {
                    val artist = parts[0].trim()
                    val title = parts[1].trim()
                    return artist to title
                }
            }
        }
        // Fallback: alles in "title", artist leer
        return "" to icyTitle.trim()
    }
}
