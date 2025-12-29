package at.plankt0n.streamplay.helper

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.os.Build
import android.util.Log
import at.plankt0n.streamplay.R
import org.xmlpull.v1.XmlPullParser
import java.security.MessageDigest

/**
 * PackageValidator prüft ob ein MediaBrowser-Client berechtigt ist sich zu verbinden.
 * Liest die erlaubten Packages und Signaturen aus allowed_media_browser_callers.xml.
 */
object PackageValidator {
    private const val TAG = "PackageValidator"

    private data class CallerInfo(
        val name: String,
        val packageName: String,
        val signatures: Set<String>
    )

    private var validCallers: List<CallerInfo>? = null

    /**
     * Initialisiert den Validator und liest die erlaubten Caller aus der XML-Datei.
     */
    fun initialize(context: Context) {
        if (validCallers != null) return

        val callers = mutableListOf<CallerInfo>()

        try {
            val parser = context.resources.getXml(R.xml.allowed_media_browser_callers)
            var eventType = parser.eventType
            var currentName = ""
            var currentPackage = ""
            val currentKeys = mutableSetOf<String>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "signature" -> {
                                currentName = parser.getAttributeValue(null, "name") ?: ""
                                currentPackage = parser.getAttributeValue(null, "package") ?: ""
                                currentKeys.clear()
                            }
                            "key" -> {
                                val key = parser.nextText()?.trim()?.lowercase()
                                if (!key.isNullOrBlank()) {
                                    currentKeys.add(key)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "signature" && currentPackage.isNotBlank()) {
                            callers.add(CallerInfo(currentName, currentPackage, currentKeys.toSet()))
                            Log.d(TAG, "Erlaubter Caller: $currentName ($currentPackage) mit ${currentKeys.size} Signaturen")
                        }
                    }
                }
                eventType = parser.next()
            }
            parser.close()
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Lesen der allowed_media_browser_callers.xml", e)
        }

        validCallers = callers
        Log.d(TAG, "PackageValidator initialisiert mit ${callers.size} erlaubten Callern")
    }

    /**
     * Prüft ob ein Package berechtigt ist sich zu verbinden.
     *
     * @param context Context für PackageManager-Zugriff
     * @param callingPackage Der Package-Name des Callers
     * @param callingUid Die UID des Callers
     * @return true wenn der Caller berechtigt ist
     */
    fun isCallerAllowed(context: Context, callingPackage: String, callingUid: Int): Boolean {
        // System-UIDs sind immer erlaubt (z.B. System UI)
        if (callingUid == android.os.Process.SYSTEM_UID || callingUid == android.os.Process.myUid()) {
            Log.d(TAG, "✅ System-UID erlaubt: $callingPackage")
            return true
        }

        // Initialisieren falls noch nicht geschehen
        initialize(context)

        val callers = validCallers ?: return false

        // Prüfen ob das Package in der Whitelist ist
        val callerInfo = callers.find { it.packageName == callingPackage }

        if (callerInfo == null) {
            // Package nicht in der Liste - Signatur loggen für Debug
            logCallerSignature(context, callingPackage)
            Log.w(TAG, "❌ Package nicht in Whitelist: $callingPackage")
            // WICHTIG: Für Entwicklung/Testing erlauben wir unbekannte Caller
            // In Produktion könnte man hier false zurückgeben
            return true
        }

        // Signaturen des Callers holen und prüfen
        val callerSignatures = getPackageSignatures(context, callingPackage)

        for (signature in callerSignatures) {
            if (callerInfo.signatures.contains(signature)) {
                Log.d(TAG, "✅ Caller verifiziert: ${callerInfo.name} ($callingPackage)")
                return true
            }
        }

        // Package gefunden aber Signatur stimmt nicht
        Log.w(TAG, "❌ Signatur-Mismatch für $callingPackage")
        callerSignatures.forEach { sig ->
            Log.d(TAG, "Gefundene Signatur: $sig")
        }

        // Für Entwicklung: auch bei Signatur-Mismatch erlauben
        // In Produktion: return false
        return true
    }

    /**
     * Holt die SHA-256 Signaturen eines Packages.
     */
    private fun getPackageSignatures(context: Context, packageName: String): Set<String> {
        val signatures = mutableSetOf<String>()

        try {
            val packageManager = context.packageManager

            val signingInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                packageInfo.signingInfo?.apkContentsSigners ?: arrayOf()
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                packageInfo.signatures ?: arrayOf()
            }

            for (sig in signingInfo) {
                val hash = sha256(sig.toByteArray())
                signatures.add(hash)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package nicht gefunden: $packageName", e)
        }

        return signatures
    }

    /**
     * Loggt die Signatur eines unbekannten Callers für Debug-Zwecke.
     */
    private fun logCallerSignature(context: Context, packageName: String) {
        val signatures = getPackageSignatures(context, packageName)
        if (signatures.isNotEmpty()) {
            Log.i(TAG, "📝 Unbekannter Caller $packageName hat folgende Signatur(en):")
            signatures.forEach { sig ->
                Log.i(TAG, "    <key>$sig</key>")
            }
        }
    }

    /**
     * Berechnet den SHA-256 Hash eines Byte-Arrays als Hex-String.
     */
    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString(":") { "%02x".format(it) }
    }
}
