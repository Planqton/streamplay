package at.plankt0n.streamplay.helper

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.data.EqualizerPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Verwaltet den Android Equalizer für Audio-Effekte.
 *
 * Wichtig: Der Equalizer muss mit einer gültigen audioSessionId initialisiert werden,
 * die vom ExoPlayer kommt, NACHDEM die Wiedergabe gestartet wurde.
 */
object EqualizerHelper {
    private const val TAG = "EqualizerHelper"

    private val lock = Any()
    @Volatile
    private var equalizer: Equalizer? = null
    @Volatile
    private var currentAudioSessionId: Int = 0

    // Equalizer-Eigenschaften (werden bei init gesetzt)
    private var numberOfBands: Short = 0
    private var minBandLevel: Short = 0  // in milliBel (mB)
    private var maxBandLevel: Short = 0  // in milliBel (mB)
    private var bandFrequencies: IntArray = intArrayOf()

    // Aktueller Zustand
    var isEnabled: Boolean = false
        private set
    var currentPreset: EqualizerPreset = EqualizerPreset.FLAT
        private set
    private var currentBandLevels: ShortArray = shortArrayOf()  // in milliBel

    // Backup für Cancel-Funktion
    private var backupEnabled: Boolean = false
    private var backupPreset: EqualizerPreset = EqualizerPreset.FLAT
    private var backupBandLevels: ShortArray = shortArrayOf()

    /**
     * Initialisiert den Equalizer mit der Audio-Session des Players.
     * Muss aufgerufen werden, wenn der Player eine gültige audioSessionId hat.
     */
    fun init(audioSessionId: Int, context: Context): Boolean = synchronized(lock) {
        Log.d(TAG, "init() called with audioSessionId=$audioSessionId")

        if (audioSessionId == 0) {
            Log.w(TAG, "audioSessionId is 0 - cannot initialize equalizer")
            return@synchronized false
        }

        // Wenn bereits mit dieser Session initialisiert, nichts tun
        if (equalizer != null && currentAudioSessionId == audioSessionId) {
            Log.d(TAG, "Equalizer already initialized with this session")
            return@synchronized true
        }

        return@synchronized try {
            // Alten Equalizer freigeben
            releaseInternal()

            // Neuen Equalizer erstellen
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            currentAudioSessionId = audioSessionId

            // Eigenschaften auslesen
            numberOfBands = eq.numberOfBands
            val levelRange = eq.bandLevelRange
            minBandLevel = levelRange[0]
            maxBandLevel = levelRange[1]

            Log.d(TAG, "Equalizer created: $numberOfBands bands, level range: $minBandLevel..$maxBandLevel mB")

            // Frequenzen auslesen
            bandFrequencies = IntArray(numberOfBands.toInt()) { band ->
                eq.getCenterFreq(band.toShort())
            }
            Log.d(TAG, "Band frequencies: ${bandFrequencies.map { it / 1000 }}Hz")

            // Arrays initialisieren
            currentBandLevels = ShortArray(numberOfBands.toInt()) { 0 }
            backupBandLevels = ShortArray(numberOfBands.toInt()) { 0 }

            // Gespeicherte Einstellungen laden
            loadSettings(context)

            // Einstellungen anwenden
            applyCurrentSettingsInternal()

            Log.d(TAG, "Equalizer initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize equalizer: ${e.message}", e)
            equalizer = null
            currentAudioSessionId = 0
            false
        }
    }

    /**
     * Gibt den Equalizer frei. Sollte aufgerufen werden, wenn der Player zerstört wird.
     */
    fun release() = synchronized(lock) {
        releaseInternal()
    }

    /**
     * Internal release without locking - called from init() which already holds the lock.
     */
    private fun releaseInternal() {
        try {
            equalizer?.release()
            Log.d(TAG, "Equalizer released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing equalizer: ${e.message}")
        }
        equalizer = null
        currentAudioSessionId = 0
    }

    /**
     * Prüft ob der Equalizer initialisiert ist.
     */
    fun isInitialized(): Boolean = equalizer != null && currentAudioSessionId != 0

    /**
     * Gibt die Anzahl der Frequenzbänder zurück.
     */
    fun getNumberOfBands(): Int = numberOfBands.toInt()

    /**
     * Gibt die Frequenzen der Bänder als formatierte Strings zurück.
     */
    fun getBandFrequencies(): List<String> {
        return bandFrequencies.map { freqHz ->
            val freqKHz = freqHz / 1000
            when {
                freqKHz >= 1000 -> "${freqKHz / 1000}kHz"
                freqKHz > 0 -> "${freqKHz}Hz"
                else -> "${freqHz}Hz"
            }
        }
    }

    /**
     * Gibt den Level-Bereich in dB zurück (min, max).
     */
    fun getLevelRange(): Pair<Int, Int> {
        val minDb = minBandLevel / 100
        val maxDb = maxBandLevel / 100
        return Pair(minDb, maxDb)
    }

    /**
     * Gibt den aktuellen Level eines Bands in dB zurück.
     */
    fun getBandLevel(band: Int): Int {
        if (band !in 0 until numberOfBands) return 0
        return currentBandLevels[band] / 100
    }

    /**
     * Setzt den Level eines Bands in dB.
     */
    fun setBandLevel(band: Int, levelDb: Int) = synchronized(lock) {
        setBandLevelInternal(band, levelDb)
    }

    /**
     * Internal version without locking - called from synchronized methods.
     */
    private fun setBandLevelInternal(band: Int, levelDb: Int) {
        if (band !in 0 until numberOfBands) return

        val levelMb = (levelDb * 100).toShort()
        val clampedLevel = levelMb.coerceIn(minBandLevel, maxBandLevel)
        currentBandLevels[band] = clampedLevel

        try {
            equalizer?.setBandLevel(band.toShort(), clampedLevel)
            Log.d(TAG, "Set band $band to ${clampedLevel}mB (${levelDb}dB)")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting band level: ${e.message}")
        }
    }

    /**
     * Aktiviert oder deaktiviert den Equalizer.
     */
    fun setEnabled(enabled: Boolean) = synchronized(lock) {
        isEnabled = enabled
        try {
            equalizer?.enabled = enabled
            Log.d(TAG, "Equalizer ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting enabled state: ${e.message}")
        }
    }

    /**
     * Setzt das aktuelle Preset (ohne die Werte anzuwenden).
     */
    fun setPreset(preset: EqualizerPreset) = synchronized(lock) {
        currentPreset = preset
        Log.d(TAG, "Preset set to: ${preset.name}")
    }

    /**
     * Wendet ein Preset an und setzt alle Band-Levels entsprechend.
     */
    fun applyPreset(preset: EqualizerPreset) = synchronized(lock) {
        currentPreset = preset
        Log.d(TAG, "Applying preset: ${preset.displayName}")

        if (preset == EqualizerPreset.CUSTOM) {
            // Bei Custom nichts ändern, aktuelle Werte behalten
            return@synchronized
        }

        val gains = preset.gains
        for (band in 0 until numberOfBands) {
            val gainDb = if (band < gains.size) gains[band] else 0
            setBandLevelInternal(band, gainDb)
        }
    }

    /**
     * Erstellt ein Backup der aktuellen Einstellungen (für Cancel-Funktion).
     */
    fun createBackup() = synchronized(lock) {
        backupEnabled = isEnabled
        backupPreset = currentPreset
        backupBandLevels = currentBandLevels.copyOf()
        Log.d(TAG, "Backup created")
    }

    /**
     * Stellt das Backup wieder her (Cancel-Funktion).
     */
    fun restoreBackup() = synchronized(lock) {
        isEnabled = backupEnabled
        currentPreset = backupPreset
        currentBandLevels = backupBandLevels.copyOf()
        applyCurrentSettingsInternal()
        Log.d(TAG, "Backup restored")
    }

    /**
     * Speichert die aktuellen Einstellungen in SharedPreferences.
     * @param context Context für SharedPreferences-Zugriff
     * @param syncToApi Ob die Einstellungen auch zur API synchronisiert werden sollen (default: true)
     */
    fun saveSettings(context: Context, syncToApi: Boolean = true) {
        // Read state under lock
        val (enabled, preset, bands, levels) = synchronized(lock) {
            SaveState(isEnabled, currentPreset, numberOfBands.toInt(), currentBandLevels.copyOf())
        }

        val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putBoolean(Keys.PREF_EQ_ENABLED, enabled)
        editor.putString(Keys.PREF_EQ_PRESET, preset.name)

        // Band-Levels in dB speichern
        for (band in 0 until bands) {
            val levelDb = levels[band] / 100
            editor.putInt("${Keys.PREF_EQ_BAND_PREFIX}$band", levelDb)
        }

        editor.apply()
        Log.d(TAG, "Settings saved: enabled=$enabled, preset=${preset.name}")

        // Zur API synchronisieren, falls aktiviert
        if (syncToApi) {
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) {
                StreamplayApiHelper.pushIfSyncEnabled(context)
            }
        }
    }

    private data class SaveState(
        val enabled: Boolean,
        val preset: EqualizerPreset,
        val bands: Int,
        val levels: ShortArray
    )

    /**
     * Lädt die Einstellungen aus SharedPreferences.
     */
    private fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)

        isEnabled = prefs.getBoolean(Keys.PREF_EQ_ENABLED, false)

        val presetName = prefs.getString(Keys.PREF_EQ_PRESET, EqualizerPreset.FLAT.name)
            ?: EqualizerPreset.FLAT.name
        currentPreset = EqualizerPreset.fromName(presetName)

        // Band-Levels laden
        for (band in 0 until numberOfBands) {
            val levelDb = prefs.getInt("${Keys.PREF_EQ_BAND_PREFIX}$band", 0)
            currentBandLevels[band] = (levelDb * 100).toShort()
        }

        Log.d(TAG, "Settings loaded: enabled=$isEnabled, preset=${currentPreset.name}")
    }

    /**
     * Lädt die Einstellungen neu aus SharedPreferences und wendet sie an.
     * Wird verwendet, wenn Einstellungen extern geändert wurden (z.B. API Sync).
     */
    fun reloadSettings(context: Context) = synchronized(lock) {
        if (!isInitialized()) {
            Log.d(TAG, "Equalizer not initialized - skipping reload")
            return@synchronized
        }
        Log.d(TAG, "Reloading settings from SharedPreferences")
        loadSettings(context)
        applyCurrentSettingsInternal()
    }

    /**
     * Wendet die aktuellen Einstellungen auf den Equalizer an.
     * Internal version without locking - called from synchronized methods.
     */
    private fun applyCurrentSettingsInternal() {
        val eq = equalizer ?: return

        try {
            // Enabled-Status setzen
            eq.enabled = isEnabled

            // Alle Band-Levels setzen
            for (band in 0 until numberOfBands) {
                eq.setBandLevel(band.toShort(), currentBandLevels[band])
            }

            Log.d(TAG, "Applied settings to equalizer")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying settings: ${e.message}")
        }
    }
}
