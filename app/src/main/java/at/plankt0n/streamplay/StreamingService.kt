package at.plankt0n.streamplay

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.NetworkType
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.LibraryResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.content.ContentResolver
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.EqualizerHelper
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.SpotifyMetaReader
import at.plankt0n.streamplay.helper.MetaLogHelper
import at.plankt0n.streamplay.helper.StateHelper
import at.plankt0n.streamplay.data.MetaLogEntry
import at.plankt0n.streamplay.viewmodel.UITrackRepository
import at.plankt0n.streamplay.viewmodel.UITrackInfo
import at.plankt0n.streamplay.audio.VisualizerAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
@androidx.media3.common.util.UnstableApi
class StreamingService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private var visualizerProcessor: VisualizerAudioProcessor? = null
    private var playerListener: Player.Listener? = null
    private var visualizerListener: VisualizerAudioProcessor.Listener? = null

    // Autoplay Handler für Cleanup in onDestroy
    private var autoplayHandler: Handler? = null
    private var autoplayRunnable: Runnable? = null

    // Service-eigener CoroutineScope für Lifecycle-gebundene Coroutines
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var streams: List<StationItem> = emptyList()
    private var mediaItems: List<MediaItem> = emptyList()
    private var currentIndex = 0

    @Volatile
    var lastIcyMetadata: Metadata? = null
    @Volatile
    private var lastshowedMetadata: MediaMetadata? = null
    @Volatile
    private var lastArtworkUri: String? = null

    // Original metadata values (without station name prefix)
    private var lastOriginalTitle: String = ""
    private var lastOriginalArtist: String = ""
    private var lastOriginalArtworkUri: String = ""
    private var currentStationUuid: String? = null

    private lateinit var connectivityManager: ConnectivityManager
    @Volatile
    private var resumeOnNetwork = false
    private var boundNetwork: Network? = null
    private var boundNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Handler(Looper.getMainLooper()).post {
                updateNetworkBinding()
            }
        }

        override fun onLost(network: Network) {
            Handler(Looper.getMainLooper()).post {
                updateNetworkBinding()
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            Handler(Looper.getMainLooper()).post {
                updateNetworkBinding()
            }
        }
    }

    // Callback der das gebundene Netzwerk überwacht
    private fun createBoundNetworkCallback(): ConnectivityManager.NetworkCallback {
        return object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                Handler(Looper.getMainLooper()).post {
                    if (network == boundNetwork) {
                        Log.d("StreamingService", "🌐 Gebundenes Netzwerk verloren!")
                        // Netzwerk ist weg - Player stoppen
                        connectivityManager.bindProcessToNetwork(null)
                        boundNetwork = null
                        unregisterBoundNetworkCallback()
                        if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
                            resumeOnNetwork = true
                            player.stop()
                        }
                    }
                }
            }
        }
    }

    private fun unregisterBoundNetworkCallback() {
        boundNetworkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Callback war nicht registriert
            }
        }
        boundNetworkCallback = null
    }

    private fun updateNetworkBinding() {
        // Check if player is initialized before using it
        if (!::player.isInitialized) return

        val networkType = NetworkType.fromName(prefs.getString(Keys.PREF_NETWORK_TYPE, NetworkType.ALL.name))
        val wasPlaying = player.isPlaying || player.playbackState == Player.STATE_BUFFERING

        if (networkType == NetworkType.ALL) {
            // Keine Bindung nötig - alle Netzwerke erlaubt
            if (boundNetwork != null) {
                // Wechsel von spezifischem Netzwerk zu "Alle" - neu starten
                val needsRestart = wasPlaying
                if (needsRestart) player.stop()
                unregisterBoundNetworkCallback()
                connectivityManager.bindProcessToNetwork(null)
                boundNetwork = null
                if (needsRestart && hasAnyNetwork()) {
                    player.prepare()
                    player.play()
                }
            }
            // Prüfen ob wir fortsetzen können
            if (resumeOnNetwork && !player.isPlaying && hasAnyNetwork()) {
                resumeOnNetwork = false
                player.prepare()
                player.play()
            }
            return
        }

        // Suche nach dem gewünschten Netzwerk
        val desiredNetwork = findNetworkByType(networkType)

        if (desiredNetwork != null) {
            // Gewünschtes Netzwerk gefunden - binden
            if (boundNetwork != desiredNetwork) {
                // Netzwerk wechselt - Player muss neu starten
                val needsRestart = wasPlaying
                if (needsRestart) player.stop()

                // Alten Callback entfernen
                unregisterBoundNetworkCallback()

                // Neues Netzwerk binden
                connectivityManager.bindProcessToNetwork(desiredNetwork)
                boundNetwork = desiredNetwork
                Log.d("StreamingService", "🌐 Gebunden an Netzwerk: ${networkType.name}")

                // Callback für dieses spezifische Netzwerk registrieren
                boundNetworkCallback = createBoundNetworkCallback()
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .apply {
                        when (networkType) {
                            NetworkType.WIFI_ONLY -> addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            NetworkType.MOBILE_ONLY -> addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                            else -> {}
                        }
                    }
                    .build()
                connectivityManager.registerNetworkCallback(request, boundNetworkCallback!!)

                if (needsRestart) {
                    player.prepare()
                    player.play()
                }
            }
            // Fortsetzen wenn nötig (z.B. nach Netzwerk-Wiederherstellung)
            if (resumeOnNetwork && !player.isPlaying) {
                resumeOnNetwork = false
                player.prepare()
                player.play()
            }
        } else {
            // Gewünschtes Netzwerk nicht verfügbar - wie offline behandeln
            unregisterBoundNetworkCallback()
            if (boundNetwork != null) {
                connectivityManager.bindProcessToNetwork(null)
                boundNetwork = null
            }
            if (wasPlaying || player.playbackState == Player.STATE_READY) {
                resumeOnNetwork = true
                player.stop()
                Log.d("StreamingService", "🌐 Netzwerk ${networkType.name} nicht verfügbar - gestoppt")
            }
        }
    }

    private fun findNetworkByType(networkType: NetworkType): Network? {
        val allNetworks = connectivityManager.allNetworks
        for (network in allNetworks) {
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: continue
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) continue

            val isMatch = when (networkType) {
                NetworkType.WIFI_ONLY -> capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                NetworkType.MOBILE_ONLY -> capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                NetworkType.ALL -> true
            }
            if (isMatch) return network
        }
        return null
    }

    private fun hasAnyNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isAllowedNetwork(): Boolean {
        val networkType = NetworkType.fromName(prefs.getString(Keys.PREF_NETWORK_TYPE, NetworkType.ALL.name))
        if (networkType == NetworkType.ALL) return hasAnyNetwork()
        return findNetworkByType(networkType) != null
    }

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusMode = AudioFocusMode.STOP
    private var wasPlayingBeforeFocusLoss = false
    private lateinit var prefs: SharedPreferences
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
        if (key == Keys.PREF_AUDIO_FOCUS_MODE) {
            val value = shared.getString(Keys.PREF_AUDIO_FOCUS_MODE, AudioFocusMode.STOP.name)
            audioFocusMode = AudioFocusMode.fromName(value)
            if (audioFocusMode.name != value) {
                shared.edit().putString(Keys.PREF_AUDIO_FOCUS_MODE, audioFocusMode.name).apply()
            }
        } else if (key == Keys.PREF_NETWORK_TYPE) {
            // Bei Änderung der Netzwerkeinstellung Bindung aktualisieren
            updateNetworkBinding()
        }
    }

    // Empfänger für Equalizer-Settings-Updates von der API
    private val equalizerUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Keys.ACTION_EQUALIZER_SETTINGS_UPDATED) {
                Log.d("StreamingService", "🎛️ Equalizer settings updated from API - reloading")
                EqualizerHelper.reloadSettings(context)
            }
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (audioFocusMode == AudioFocusMode.HOLD) {
            if (focusChange <= 0) {
                requestAudioFocus()
            }
            return@OnAudioFocusChangeListener
        }

        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Dauerhafter Verlust - immer pausieren, kein automatisches Resume
                wasPlayingBeforeFocusLoss = false
                player.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporärer Verlust - pausieren, später fortsetzen
                wasPlayingBeforeFocusLoss = player.isPlaying
                player.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Kann leiser machen
                if (audioFocusMode == AudioFocusMode.LOWER) {
                    val duckVolume = prefs.getInt(Keys.PREF_DUCK_VOLUME, 20) / 100f
                    player.volume = duckVolume
                } else {
                    wasPlayingBeforeFocusLoss = player.isPlaying
                    player.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus zurück - Volume wiederherstellen
                player.volume = 1f
                // Bei transientem Verlust wieder starten
                if (wasPlayingBeforeFocusLoss) {
                    player.play()
                    wasPlayingBeforeFocusLoss = false
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    companion object {
        const val CHANNEL_ID = "stream_service_channel"

        // Media Browser hierarchy IDs for Android Auto
        const val MEDIA_ROOT_ID = "root"
        const val MEDIA_MY_STATIONS_ID = "my_stations"
        const val MEDIA_FOR_YOU_ID = "for_you"
        const val MEDIA_WHAT_TO_LISTEN_ID = "what_to_listen"
    }

    // ===== Android Auto: MediaLibrarySession.Callback =====
    private inner class MediaLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId(MEDIA_ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("StreamPlay")
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return when (parentId) {
                MEDIA_ROOT_ID -> {
                    // Root: Zeige alle verfügbaren Kategorien
                    val children = mutableListOf<MediaItem>()

                    // Immer "Meine Sender" anzeigen
                    children.add(createBrowsableMediaItem(MEDIA_MY_STATIONS_ID, getString(R.string.auto_my_stations)))

                    // "Für mich" nur anzeigen wenn Dev-Items vorhanden
                    val forYouItems = PreferencesHelper.getDevForYouItems(this@StreamingService)
                    if (forYouItems.isNotEmpty()) {
                        children.add(createBrowsableMediaItem(MEDIA_FOR_YOU_ID, getString(R.string.auto_for_you)))
                    }

                    // "Was möchtest du hören?" nur anzeigen wenn Dev-Items vorhanden
                    val whatToListenItems = PreferencesHelper.getDevWhatToListenItems(this@StreamingService)
                    if (whatToListenItems.isNotEmpty()) {
                        children.add(createBrowsableMediaItem(MEDIA_WHAT_TO_LISTEN_ID, getString(R.string.auto_what_to_listen)))
                    }

                    Futures.immediateFuture(LibraryResult.ofItemList(children, params))
                }
                MEDIA_MY_STATIONS_ID -> {
                    // Meine Sender: Alle Sender anzeigen
                    val stations = PreferencesHelper.getStations(this@StreamingService)
                    val mediaItems = stations.map { station ->
                        createPlayableMediaItem(station)
                    }
                    Futures.immediateFuture(LibraryResult.ofItemList(mediaItems, params))
                }
                MEDIA_FOR_YOU_ID -> {
                    // Für mich: Dev Items anzeigen
                    val forYouItems = PreferencesHelper.getDevForYouItems(this@StreamingService)
                    val mediaItems = forYouItems.map { station ->
                        createPlayableMediaItem(station)
                    }
                    Futures.immediateFuture(LibraryResult.ofItemList(mediaItems, params))
                }
                MEDIA_WHAT_TO_LISTEN_ID -> {
                    // Was möchtest du hören?: Dev Items anzeigen
                    val whatToListenItems = PreferencesHelper.getDevWhatToListenItems(this@StreamingService)
                    val mediaItems = whatToListenItems.map { station ->
                        createPlayableMediaItem(station)
                    }
                    Futures.immediateFuture(LibraryResult.ofItemList(mediaItems, params))
                }
                else -> Futures.immediateFuture(
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                )
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Suche in allen Quellen nach dem Item
            val stations = PreferencesHelper.getStations(this@StreamingService)
            val forYouItems = PreferencesHelper.getDevForYouItems(this@StreamingService)
            val whatToListenItems = PreferencesHelper.getDevWhatToListenItems(this@StreamingService)

            val allItems = stations + forYouItems + whatToListenItems
            val station = allItems.find { it.uuid == mediaId }

            return if (station != null) {
                Futures.immediateFuture(LibraryResult.ofItem(createPlayableMediaItem(station), null))
            } else {
                Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // Resolve media items for playback
            val resolvedItems = mediaItems.map { item ->
                resolveMediaItem(item)
            }.toMutableList()
            return Futures.immediateFuture(resolvedItems)
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Prüfen ob Android Auto verbunden ist
            if (isAndroidAutoController(controller)) {
                Log.d("StreamingService", "🚗 Android Auto verbunden: ${controller.packageName}")
                val autoplay = prefs.getBoolean(Keys.PREF_AUTO_AUTOPLAY, false)
                if (autoplay && !player.isPlaying) {
                    Log.d("StreamingService", "🚗 Auto-Autoplay aktiviert - starte Wiedergabe")
                    player.prepare()
                    player.play()
                }
            }
            return super.onConnect(session, controller)
        }
    }

    private fun isAndroidAutoController(controller: MediaSession.ControllerInfo): Boolean {
        val pkg = controller.packageName
        return pkg.contains("com.google.android.projection") ||
               pkg.contains("com.google.android.gms.car") ||
               pkg == "com.google.android.carassistant"
    }

    // ===== Android Auto: Helper Methods =====
    private fun createBrowsableMediaItem(id: String, title: String): MediaItem {
        val iconUri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(packageName)
            .path(R.drawable.ic_radio.toString())
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtworkUri(iconUri)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .build()
            )
            .build()
    }

    private fun createPlayableMediaItem(station: StationItem): MediaItem {
        val artworkUri = if (station.iconURL.isNotBlank()) {
            Uri.parse(station.iconURL)
        } else null

        val extras = Bundle().apply {
            putString("EXTRA_ICON_URL", station.iconURL)
            putString("EXTRA_UUID", station.uuid)
            putString("EXTRA_STATION_NAME", station.stationName)
        }

        return MediaItem.Builder()
            .setMediaId(station.uuid)
            .setUri(station.streamURL)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.stationName)
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun resolveMediaItem(item: MediaItem): MediaItem {
        // If item already has a URI, return as is
        if (item.localConfiguration?.uri != null) {
            return item
        }

        // Look up the station by mediaId
        val mediaId = item.mediaId
        val stations = PreferencesHelper.getStations(this)
        val station = stations.find { it.uuid == mediaId }

        return if (station != null) {
            createPlayableMediaItem(station)
        } else {
            item // Return original if not found
        }
    }

    private var hasSeenForeground = false
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (hasSeenForeground) {
                refreshMediaItemMetadata()
            } else {
                hasSeenForeground = true
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            if (hasSeenForeground) {
                refreshMediaItemMetadata()
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST" -> refreshPlaylist()
            Keys.ACTION_REFRESH_METADATA -> refreshMediaItemMetadata()
            Keys.ACTION_AUTO_PLAY -> {
                Log.d("StreamingService", "🚗 ACTION_AUTO_PLAY empfangen")
                if (!player.isPlaying) {
                    player.prepare()
                    player.play()
                }
            }
            Keys.ACTION_AUTO_STOP -> {
                Log.d("StreamingService", "🚗 ACTION_AUTO_STOP empfangen")
                if (player.isPlaying) {
                    player.pause()
                }
            }
            Keys.ACTION_NOTIFY_STATIONS_CHANGED -> {
                Log.d("StreamingService", "📻 Sender-Liste geändert - benachrichtige Android Auto")
                notifyStationsChanged()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // Benachrichtigt Android Auto über Änderungen der Sender-Liste
    private fun notifyStationsChanged() {
        if (::mediaLibrarySession.isInitialized) {
            // Root notifyen damit neue Kategorien erscheinen/verschwinden
            mediaLibrarySession.notifyChildrenChanged(MEDIA_ROOT_ID, 0, null)
            // Alle Kategorien notifyen
            mediaLibrarySession.notifyChildrenChanged(MEDIA_MY_STATIONS_ID, 0, null)
            mediaLibrarySession.notifyChildrenChanged(MEDIA_FOR_YOU_ID, 0, null)
            mediaLibrarySession.notifyChildrenChanged(MEDIA_WHAT_TO_LISTEN_ID, 0, null)
        }
    }


    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        // Equalizer-Update-Receiver registrieren
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                equalizerUpdateReceiver,
                IntentFilter(Keys.ACTION_EQUALIZER_SETTINGS_UPDATED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                equalizerUpdateReceiver,
                IntentFilter(Keys.ACTION_EQUALIZER_SETTINGS_UPDATED)
            )
        }

        val storedMode = prefs.getString(Keys.PREF_AUDIO_FOCUS_MODE, AudioFocusMode.STOP.name)
        audioFocusMode = AudioFocusMode.fromName(storedMode)
        if (audioFocusMode.name != storedMode) {
            prefs.edit().putString(Keys.PREF_AUDIO_FOCUS_MODE, audioFocusMode.name).apply()
        }
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback)

        // ⚠️ ZUERST player initialisieren!
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(DefaultDataSource.Factory(this, httpDataSourceFactory))

        // Audio Session ID für Visualizer generieren und sofort speichern
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val generatedAudioSessionId = audioManager.generateAudioSessionId()
        StateHelper.audioSessionId = generatedAudioSessionId
        Log.d("StreamingService", "🎵 Generated audioSessionId: $generatedAudioSessionId")

        // VisualizerAudioProcessor erstellen und konfigurieren
        visualizerListener = object : VisualizerAudioProcessor.Listener {
            override fun onFftDataAvailable(magnitudes: FloatArray) {
                StateHelper.notifyVisualizerData(magnitudes)
            }
        }
        visualizerProcessor = VisualizerAudioProcessor().apply {
            setListener(visualizerListener)
        }

        // Custom RenderersFactory mit VisualizerAudioProcessor
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): DefaultAudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(visualizerProcessor!!))
                    .build()
            }
        }

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false // Audio Focus wird manuell verwaltet
            )
            .build().apply {
                setAudioSessionId(generatedAudioSessionId)
                repeatMode = Player.REPEAT_MODE_OFF

                // Player.Listener als Member-Variable für sauberes Cleanup
                playerListener = object : Player.Listener {
                    //Listener für ICY META aus EXOPLAYER (nicht MediaMetadata)
                    override fun onMetadata(metadata: Metadata) {
                            fetchMetadata(metadata)
                    }

                    //Listener für wechsel der Streams
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentIndex = currentMediaItemIndex

                        Log.d("StreamingService", "💾 Index gespeichert: $currentIndex")
                        currentStationUuid = mediaItem?.mediaMetadata?.extras?.getString("EXTRA_UUID")
                        val fallbackartworkUri = mediaItem?.mediaMetadata?.extras?.getString("EXTRA_ICON_URL") ?: ""
                        UITrackRepository.clearTrackInfo()
                        lastIcyMetadata = null
                        lastshowedMetadata = null
                        lastArtworkUri = null

                        updateMediaItemMetadata("", "", fallbackartworkUri)


                        PreferencesHelper.setLastPlayedStreamIndex(
                            this@StreamingService,
                            currentIndex
                        )

                    }
                    //Listener für errors
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("StreamingService", "❌ ExoPlayer-Fehler: ${error.errorCodeName} - ${error.message}")
                        Log.e("StreamingService", "Cause: ${error.cause?.message ?: "unbekannt"}")
                        error.cause?.printStackTrace()

                        updateMediaItemMetadata(
                            "Error",
                            error.cause?.message ?: "unbekannt",
                            lastArtworkUri ?: "",
                        )

                        // IO-Error-Codes liegen im Bereich 2000-2999
                        resumeOnNetwork = player.playWhenReady &&
                            (error.errorCode >= 2000 && error.errorCode < 3000)
                    }

                    override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                    //noch keine verwendung
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            requestAudioFocus()
                            // AudioSessionId für Visualizer speichern
                            StateHelper.audioSessionId = player.audioSessionId
                            Log.d("StreamingService", "🎵 audioSessionId saved: ${player.audioSessionId}")
                            // Equalizer initialisieren wenn noch nicht geschehen
                            if (!EqualizerHelper.isInitialized()) {
                                if (EqualizerHelper.init(player.audioSessionId, this@StreamingService)) {
                                    Log.d("StreamingService", "🎛️ Equalizer bei Playback-Start initialisiert")
                                }
                            }
                        } else {
                            abandonAudioFocus()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        // Wenn Player versucht zu buffern aber Netzwerk nicht erlaubt -> stoppen
                        if (playbackState == Player.STATE_BUFFERING && !isAllowedNetwork()) {
                            resumeOnNetwork = true
                            player.stop()
                        }
                    }

                }
                addListener(playerListener!!)
            }

        // Equalizer initialisieren
        if (EqualizerHelper.init(player.audioSessionId, this)) {
            Log.d("StreamingService", "🎛️ Equalizer initialisiert")
        } else {
            Log.w("StreamingService", "⚠️ Equalizer konnte nicht initialisiert werden")
        }

        // Migriere autoplay_delay von Float zu Int falls nötig
        migrateAutoplayDelayPreference()

        // ⚠️ ZUERST Notification Channel erstellen (vor startForeground!)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.streaming_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.streaming_service_channel_description)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Dann Notification erstellen und Service in Vordergrund starten
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.streaming_service_notification_title))
            .setContentText(getString(R.string.streaming_service_notification_text))
            .setSmallIcon(R.drawable.ic_radio)
            .build()
        startForeground(1, notification)

        // Netzwerkbindung initialisieren bevor Playlist geladen wird
        updateNetworkBinding()

        setupPlaylist()

        val sessionIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, MediaLibrarySessionCallback())
            .setSessionActivity(sessionIntent)
            .build()
    }

    private fun setupPlaylist() {
        streams = PreferencesHelper.getStations(this)
        if (streams.isEmpty()) {
            stopSelf()
            return
        }

        currentIndex = PreferencesHelper.getLastPlayedStreamIndex(this)
        if (currentIndex !in streams.indices) {
            Log.w("StreamingService", "⚠️ Letzter Index $currentIndex existiert nicht mehr. Fallback auf 0.")
            currentIndex = 0
        }

        mediaItems = streams.map {
            val extras = Bundle().apply {
                putString("EXTRA_ICON_URL", it.iconURL)
                putString("EXTRA_UUID", it.uuid)
                putString("EXTRA_STATION_NAME", it.stationName)
            }

            val metadata = MediaMetadata.Builder()
                .setExtras(extras)
                .build()

            MediaItem.Builder()
                .setUri(it.streamURL)
                .setMediaId(it.streamURL)
                .setMediaMetadata(metadata)
                .build()
        }

        player.setMediaItems(mediaItems, currentIndex, 0L)
        currentStationUuid = mediaItems[currentIndex].mediaMetadata.extras?.getString("EXTRA_UUID")

        // Nur vorbereiten wenn Netzwerk erlaubt ist
        if (isAllowedNetwork()) {
            player.prepare()
            maybeAutoplay()
        } else {
            resumeOnNetwork = true
        }
    }

    private fun refreshPlaylist() {
        val wasPlaying = player.isPlaying
        if (wasPlaying) player.pause()

        streams = PreferencesHelper.getStations(this)
        if (streams.isEmpty()) {
            stopSelf()
            return
        }

        currentIndex = PreferencesHelper.getLastPlayedStreamIndex(this)
        if (currentIndex !in streams.indices) {
            Log.w("StreamingService", "⚠️ Letzter Index $currentIndex existiert nicht mehr. Fallback auf 0.")
            currentIndex = 0
        }

        mediaItems = streams.map {
            val extras = Bundle().apply {
                putString("EXTRA_ICON_URL", it.iconURL)
                putString("EXTRA_UUID", it.uuid)
                putString("EXTRA_STATION_NAME", it.stationName)
            }

            val metadata = MediaMetadata.Builder()
                .setExtras(extras)
                .build()

            MediaItem.Builder()
                .setUri(it.streamURL)
                .setMediaId(it.streamURL)
                .setMediaMetadata(metadata)
                .build()
        }

        player.setMediaItems(mediaItems, currentIndex, 0L)
        currentStationUuid = mediaItems[currentIndex].mediaMetadata.extras?.getString("EXTRA_UUID")

        // Nur vorbereiten wenn Netzwerk erlaubt ist
        if (isAllowedNetwork()) {
            player.prepare()
            maybeAutoplay(wasPlaying)
            if (wasPlaying) player.play()
        } else {
            resumeOnNetwork = wasPlaying || resumeOnNetwork
        }
    }

    private fun maybeAutoplay(ignoreIfWasPlaying: Boolean = false) {
        val prefs = getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        val autoplay = prefs.getBoolean("autoplay_enabled", false)
        if (!autoplay) return
        if (ignoreIfWasPlaying) return

        // Netzwerkprüfung - nicht starten wenn Netzwerk nicht erlaubt
        if (!isAllowedNetwork()) {
            resumeOnNetwork = true
            return
        }

        val delay = try {
            prefs.getInt("autoplay_delay", 0)
        } catch (e: ClassCastException) {
            // Fallback für alte Float-Werte
            prefs.getFloat("autoplay_delay", 0f).toInt()
        }
        val minimize = prefs.getBoolean("minimize_after_autoplay", false)

        player.play()

        if (minimize) {
            // Cancel any pending autoplay runnables
            autoplayHandler?.removeCallbacksAndMessages(null)

            autoplayRunnable = Runnable {
                sendBroadcast(Intent(Keys.ACTION_HIDE_COUNTDOWN).setPackage(packageName))
                minimizeApp()
            }

            if (delay > 0) {
                val intent = Intent(Keys.ACTION_SHOW_COUNTDOWN).apply {
                    putExtra(Keys.EXTRA_COUNTDOWN_DURATION, delay)
                    setPackage(packageName)
                }
                sendBroadcast(intent)
                autoplayHandler = Handler(Looper.getMainLooper())
                autoplayHandler?.postDelayed(autoplayRunnable!!, delay * 1000L)
            } else {
                autoplayRunnable?.run()
            }
        }
    }

    private fun minimizeApp() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return if (::mediaLibrarySession.isInitialized) mediaLibrarySession else null
    }

    override fun onDestroy() {
        serviceJob.cancel() // Alle Coroutines beenden

        // Autoplay Handler cleanup
        autoplayHandler?.removeCallbacksAndMessages(null)
        autoplayHandler = null
        autoplayRunnable = null

        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        } catch (e: Exception) {
            Log.w("StreamingService", "Failed to remove lifecycle observer", e)
        }

        try {
            connectivityManager.unregisterNetworkCallback(defaultNetworkCallback)
        } catch (e: Exception) {
            Log.w("StreamingService", "defaultNetworkCallback not registered", e)
        }
        unregisterBoundNetworkCallback()
        connectivityManager.bindProcessToNetwork(null) // Netzwerkbindung aufheben
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        try {
            unregisterReceiver(equalizerUpdateReceiver)
        } catch (e: Exception) {
            Log.w("StreamingService", "Equalizer receiver already unregistered")
        }
        abandonAudioFocus()
        EqualizerHelper.release()

        // VisualizerProcessor Listener cleanup
        visualizerProcessor?.setListener(null)
        visualizerListener = null
        visualizerProcessor?.reset()
        visualizerProcessor = null

        if (::mediaLibrarySession.isInitialized) {
            mediaLibrarySession.release()
        }

        // Player.Listener cleanup vor release()
        playerListener?.let { player.removeListener(it) }
        playerListener = null
        player.release()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Nur stoppen wenn nicht gerade abgespielt wird
        // Bei Orientierungswechsel läuft die Musik weiter
        if (!player.isPlaying && player.playbackState != Player.STATE_BUFFERING) {
            stopSelf()
        }
    }

    fun fetchMetadata(metadata: Metadata) {

        val stationUuidAtFetchStart = currentStationUuid
        val fallbackartworkUri = player.currentMediaItem?.mediaMetadata?.extras?.getString("EXTRA_ICON_URL")
        var title = ""
        var artist = ""

        //Ausarbeiten der Relevanten Infos aus der ICY META
        for (i in 0 until metadata.length()) {
            val entry = metadata[i]
            val mediaMetadata = MediaMetadata.Builder().apply {
                entry.populateMediaMetadata(this)
            }.build()

            title = decodeHtmlEntities(mediaMetadata.title?.toString().orEmpty())
            artist = decodeHtmlEntities(mediaMetadata.artist?.toString().orEmpty())


            Log.d("RadioMeta", "🎵 Title: $title | 👤 Artist: $artist")
        }

        if (metadata == lastIcyMetadata) {
            Log.d("StreamingService", "ℹ️ Keine Änderung in der Metadata. beende Fetching")
            return

        }
        lastIcyMetadata = metadata //Damit ab jetzt mit dem wert Verglichen wird!

        //Fürs Splitten! Wenn artist leer ist wird der title mit den Trennzeichen gesplittet
        //Dies hat den Hintergrund dass die Meisten ICY META die exoplayer weiterleitet den Artist unt Titel nicht getrennt ausgeben sondern so Titel - Artist (was natürlich getrennt werden muss)
        //Dies ist eigentlich nur ein fallback so oder so haben wir am ende artist und title getrennt ->
        val rawTitle = title
        val rawArtist = artist

        if (artist.isNullOrEmpty()) {
            val delimiters = listOf(" - ", " / ")
            for (delimiter in delimiters) {
                if (rawTitle.contains(delimiter)) {
                    val parts = rawTitle.split(delimiter, limit = 2)
                    if (parts.size == 2) {
                        artist = parts[0].trim()
                        title = parts[1].trim()
                        Log.d("StreamingService", "🎵 Splitter verwendet: Title: $title | Artist: $artist")
                        break
                    }
                }
            }
        }

        if (artist.isNotEmpty() && title.isNotEmpty()) {
            val prefs = getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
            val useSpotify = prefs.getBoolean(Keys.PREF_USE_SPOTIFY_META, false)
            val hasKeys = !prefs.getString(Keys.PREF_SPOTIFY_CLIENT_ID, "").isNullOrBlank() &&
                    !prefs.getString(Keys.PREF_SPOTIFY_CLIENT_SECRET, "").isNullOrBlank()

            if (useSpotify && hasKeys) {
                serviceScope.launch(Dispatchers.IO) {
                    val extendedInfo =
                        SpotifyMetaReader.getExtendedMetaInfo(this@StreamingService, artist, title)
                    withContext(Dispatchers.Main) {
                        if (stationUuidAtFetchStart != currentStationUuid) {
                            Log.d("StreamingService", "ℹ️ Station changed during metadata fetch, ignoring results")
                            updateMediaItemMetadata("", "", fallbackartworkUri ?: "")
                            return@withContext
                        }
                        if (extendedInfo != null) {
                            Log.d(
                                "SpotifyMetaReader",
                                "✅ Spotify-Infos gefunden: ${extendedInfo.trackName}, spotifyUrl=${extendedInfo.spotifyUrl}"
                            )

                            UITrackRepository.updateTrackInfo(
                                UITrackInfo(
                                    trackName = extendedInfo.trackName,
                                    artistName = extendedInfo.artistName,
                                    bestCoverUrl = extendedInfo.bestCoverUrl,
                                    albumName = extendedInfo.albumName,
                                    durationMs = extendedInfo.durationMs,
                                    albumReleaseDate = extendedInfo.albumReleaseDate,
                                    popularity = extendedInfo.popularity,
                                    spotifyUrl = extendedInfo.spotifyUrl,
                                    previewUrl = extendedInfo.previewUrl,
                                    genre = extendedInfo.genre
                                )
                            )

                            updateMediaItemMetadata(
                                title = extendedInfo.trackName,
                                artist = extendedInfo.artistName,
                                artworkUri = extendedInfo.bestCoverUrl ?: ""
                            )

                            MetaLogHelper.addLog(
                                this@StreamingService,
                                MetaLogEntry(
                                    timestamp = System.currentTimeMillis(),
                                    station = player.currentMediaItem?.mediaMetadata?.extras?.getString("EXTRA_STATION_NAME") ?: "",
                                    title = extendedInfo.trackName,
                                    artist = extendedInfo.artistName,
                                    url = extendedInfo.spotifyUrl.takeIf { it.isNotBlank() },
                                    coverUrl = extendedInfo.bestCoverUrl
                                )
                            )
                        } else {
                            Log.w(
                                "SpotifyMetaReader",
                                "❌ Keine Spotify-Daten gefunden für: $artist - $title"
                            )
                            updateMediaItemMetadata(title, artist, fallbackartworkUri ?: "")
                            UITrackRepository.updateTrackInfo(
                                UITrackInfo(
                                    trackName = title,
                                    artistName = artist,
                                    bestCoverUrl = fallbackartworkUri,
                                    previewUrl = null,
                                    genre = ""
                                )
                            )
                            MetaLogHelper.addLog(
                                this@StreamingService,
                                MetaLogEntry(
                                    timestamp = System.currentTimeMillis(),
                                    station = player.currentMediaItem?.mediaMetadata?.extras?.getString("EXTRA_STATION_NAME") ?: "",
                                    title = title,
                                    artist = artist,
                                    url = null,
                                    coverUrl = fallbackartworkUri
                                )
                            )
                        }
                    }
                }
            } else {
                serviceScope.launch(Dispatchers.Main) {
                    if (stationUuidAtFetchStart != currentStationUuid) {
                        updateMediaItemMetadata("", "", fallbackartworkUri ?: "")
                        return@launch
                    }
                    updateMediaItemMetadata(title, artist, fallbackartworkUri ?: "")
                }
                UITrackRepository.updateTrackInfo(
                    UITrackInfo(
                        trackName = title,
                        artistName = artist,
                        bestCoverUrl = fallbackartworkUri,
                        previewUrl = null,
                        genre = ""
                    )
                )
                MetaLogHelper.addLog(
                    this@StreamingService,
                    MetaLogEntry(
                        timestamp = System.currentTimeMillis(),
                        station = player.currentMediaItem?.mediaMetadata?.extras?.getString("EXTRA_STATION_NAME") ?: "",
                        title = title,
                        artist = artist,
                        url = null,
                        coverUrl = fallbackartworkUri
                    )
                )
            }
        } else {
            Log.d(
                "StreamingService",
                "⚠️ Artist oder Title fehlen – kein Spotify-Request. Fallback auf alte meta"
            )
            // Sicherstellen, dass auch dieser Aufruf im Main-Thread läuft!
            serviceScope.launch(Dispatchers.Main) {
                if (stationUuidAtFetchStart != currentStationUuid) {
                    updateMediaItemMetadata("", "", fallbackartworkUri ?: "")
                    return@launch
                }
                updateMediaItemMetadata(title, artist, fallbackartworkUri ?: "")
            }
            UITrackRepository.updateTrackInfo(
                UITrackInfo(
                    trackName = title,
                    artistName = artist,
                    bestCoverUrl = fallbackartworkUri
                )
            )
            MetaLogHelper.addLog(
                this@StreamingService,
                MetaLogEntry(
                    timestamp = System.currentTimeMillis(),
                    station = player.currentMediaItem?.mediaMetadata?.extras?.getString("EXTRA_STATION_NAME") ?: "",
                    title = title,
                    artist = artist,
                    url = null,
                    coverUrl = fallbackartworkUri
                )
            )
        }

    }

    fun updateMediaItemMetadata(title: String, artist: String, artworkUri: String) {
        // Save original values for refresh
        lastOriginalTitle = title
        lastOriginalArtist = artist
        lastOriginalArtworkUri = artworkUri

        val isInForeground = isAppInForeground()
        lastArtworkUri = artworkUri
        val showStationInMediaInfo = prefs.getBoolean(Keys.PREF_SHOW_STATION_IN_MEDIAINFO, false)
        val stationName = player.currentMediaItem?.mediaMetadata?.extras?.getString("EXTRA_STATION_NAME") ?: ""

        val updateartist: String
        var updatetitle: String

        if (isInForeground) {
            updateartist = artist
            updatetitle = if (showStationInMediaInfo && stationName.isNotBlank() && title.isNotBlank()) {
                "$stationName: $title"
            } else {
                title
            }
            Log.d("StreamingService", "App im Vordergrund: bleibt so")
        } else {
            updatetitle = if (showStationInMediaInfo && stationName.isNotBlank()) {
                if (artist.isNotBlank() && title.isNotBlank()) {
                    "$stationName: $artist - $title"
                } else if (title.isNotBlank()) {
                    "$stationName: $title"
                } else {
                    stationName
                }
            } else {
                "$artist - $title"
            }
            if (artist.isBlank() && title.isBlank() && !showStationInMediaInfo) {
                updatetitle = getString(R.string.no_metadata_available)
            }
            updateartist = if (showStationInMediaInfo && artist.isNotBlank()) {
                artist
            } else {
                stationName.ifBlank { "Sendername nicht Gesetzt" }
            }
            Log.d("StreamingService", "App ist im Hinterrgund: updateartist=$updateartist, updatetitle=$updatetitle")
        }


        player.currentMediaItem?.let { mediaItem ->
            val updatedMetadata = mediaItem.mediaMetadata
                .buildUpon()
                .setTitle(updatetitle)
                .setArtist(updateartist)
                .setArtworkUri(Uri.parse(artworkUri))
                .build()

            val updatedMediaItem = mediaItem.buildUpon()
                .setMediaMetadata(updatedMetadata)
                .build()

            player.replaceMediaItem(player.currentMediaItemIndex, updatedMediaItem)
            Log.d("StreamingService", "🔄 Nur Metadaten aktualisiert: $title - $artist")
            lastshowedMetadata = updatedMetadata // 🟢 Hier speichern!

        }
    }


    fun refreshMediaItemMetadata() {
        // Use original values instead of lastshowedMetadata to avoid accumulating station names
        val title = lastOriginalTitle
        val artist = lastOriginalArtist
        val artworkUri = lastOriginalArtworkUri

        val isInForeground = isAppInForeground()
        val showStationInMediaInfo = prefs.getBoolean(Keys.PREF_SHOW_STATION_IN_MEDIAINFO, false)
        val stationName = player.currentMediaItem?.mediaMetadata?.extras?.getString("EXTRA_STATION_NAME") ?: ""

        val updateartist: String
        var updatetitle: String

        if (isInForeground) {
            updateartist = artist
            updatetitle = if (showStationInMediaInfo && stationName.isNotBlank() && title.isNotBlank()) {
                "$stationName: $title"
            } else {
                title
            }
            Log.d("StreamingService", "refreshMediaItemMetadata - App im Vordergrund")
        } else {
            updatetitle = if (showStationInMediaInfo && stationName.isNotBlank()) {
                if (artist.isNotBlank() && title.isNotBlank()) {
                    "$stationName: $artist - $title"
                } else if (title.isNotBlank()) {
                    "$stationName: $title"
                } else {
                    stationName
                }
            } else {
                "$artist - $title"
            }
            if (artist.isBlank() && title.isBlank() && !showStationInMediaInfo) {
                updatetitle = getString(R.string.no_metadata_available)
            }
            updateartist = if (showStationInMediaInfo && artist.isNotBlank()) {
                artist
            } else {
                stationName.ifBlank { "Sendername nicht Gesetzt" }
            }
            Log.d("StreamingService", "refreshMediaItemMetadata - App im Hintergrund: updateartist=$updateartist, updatetitle=$updatetitle")
        }

        player.currentMediaItem?.let { mediaItem ->
            val updatedMetadata = mediaItem.mediaMetadata
                .buildUpon()
                .setTitle(updatetitle)
                .setArtist(updateartist)
                .setArtworkUri(Uri.parse(artworkUri))
                .build()

            val updatedMediaItem = mediaItem.buildUpon()
                .setMediaMetadata(updatedMetadata)
                .build()

            player.replaceMediaItem(player.currentMediaItemIndex, updatedMediaItem)
            Log.d("StreamingService", "🔄 Metadaten refreshed: $title - $artist")
            lastshowedMetadata = updatedMetadata
            UITrackRepository.updateTrackInfo(
                UITrackInfo(
                    trackName = title,
                    artistName = artist,
                    bestCoverUrl = artworkUri,
                    previewUrl = null,
                    genre = ""
                )
            )
        }
    } //zur anpassung wenn PlayerFragment Minimiert ist damit der sende rin der Medaisesion angzeigt wird


    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }


    fun isPlaylistDifferent(current: List<StationItem>, new: List<StationItem>): Boolean {
        if (current.size != new.size) return true
        for (i in current.indices) {
            val oldItem = current[i]
            val newItem = new[i]
            if (oldItem.uuid != newItem.uuid ||
                oldItem.stationName != newItem.stationName ||
                oldItem.streamURL != newItem.streamURL ||
                oldItem.iconURL != newItem.iconURL) {
                return true
            }
        }
        return false
    }


    fun decodeHtmlEntities(text: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            Html.fromHtml(text).toString()
        }
    }

    private fun migrateAutoplayDelayPreference() {
        val prefs = getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        try {
            // Versuche als Float zu lesen - wenn erfolgreich, migriere zu Int
            val floatValue = prefs.getFloat("autoplay_delay", -1f)
            if (floatValue >= 0f) {
                prefs.edit()
                    .remove("autoplay_delay")
                    .putInt("autoplay_delay", floatValue.toInt())
                    .apply()
                Log.d("StreamingService", "Migriert autoplay_delay von Float zu Int: ${floatValue.toInt()}")
            }
        } catch (e: ClassCastException) {
            // Schon ein Int - keine Migration nötig
        }
    }

}
