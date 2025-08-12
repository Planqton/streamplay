package at.plankt0n.streamplay

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.IcyStreamReader
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.SpotifyMetaReader
import at.plankt0n.streamplay.helper.MetaLogHelper
import at.plankt0n.streamplay.data.MetaLogEntry
import at.plankt0n.streamplay.viewmodel.UITrackRepository
import at.plankt0n.streamplay.viewmodel.UITrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
@androidx.media3.common.util.UnstableApi
class StreamingService : MediaSessionService() {

    private var icyStreamReader: IcyStreamReader? = null //alt
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    private var streams: List<StationItem> = emptyList()
    private var mediaItems: List<MediaItem> = emptyList()
    private var currentIndex = 0

    var lastIcyMetadata: Metadata? = null
    private var lastshowedMetadata: MediaMetadata? = null
    private var lastArtworkUri: String? = null
    private var currentStationUuid: String? = null

    private lateinit var connectivityManager: ConnectivityManager
    private var resumeOnNetwork = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Handler(Looper.getMainLooper()).post {
                if (resumeOnNetwork && !player.isPlaying) {
                    resumeOnNetwork = false
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusMode = AudioFocusMode.STOP
    private lateinit var prefs: SharedPreferences
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
        if (key == Keys.PREF_AUDIO_FOCUS_MODE) {
            audioFocusMode = AudioFocusMode.valueOf(
                shared.getString(Keys.PREF_AUDIO_FOCUS_MODE, AudioFocusMode.STOP.name)!!
            )
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
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (audioFocusMode == AudioFocusMode.LOWER) {
                    player.volume = 0.2f
                } else {
                    player.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                player.volume = 1f
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
        }
        return super.onStartCommand(intent, flags, startId)
    }


    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        audioFocusMode = AudioFocusMode.valueOf(
            prefs.getString(Keys.PREF_AUDIO_FOCUS_MODE, AudioFocusMode.STOP.name)!!
        )
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        // ⚠️ ZUERST player initialisieren!
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(DefaultDataSource.Factory(this, httpDataSourceFactory))

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)

            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
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

                        resumeOnNetwork = player.playWhenReady &&
                            (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                             error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
                    }

                    override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                    //noch keine verwendung
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            requestAudioFocus()
                        } else {
                            abandonAudioFocus()
                        }
                    }

                })


            }

        // ⚠️ Dann Notification etc.
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.streaming_service_notification_title))
            .setContentText(getString(R.string.streaming_service_notification_text))
            .setSmallIcon(R.drawable.ic_radio)
            .build()
        startForeground(1, notification)

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

        setupPlaylist()

        val sessionIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
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
                .setMediaMetadata(metadata)
                .build()
        }

        player.setMediaItems(mediaItems, currentIndex, 0L)
        currentStationUuid = mediaItems[currentIndex].mediaMetadata.extras?.getString("EXTRA_UUID")
        player.prepare()
        maybeAutoplay()
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
                .setMediaMetadata(metadata)
                .build()
        }

        player.setMediaItems(mediaItems, currentIndex, 0L)
        currentStationUuid = mediaItems[currentIndex].mediaMetadata.extras?.getString("EXTRA_UUID")
        player.prepare()
        maybeAutoplay(wasPlaying)

        if (wasPlaying) player.play()
    }

    private fun maybeAutoplay(ignoreIfWasPlaying: Boolean = false) {
        val prefs = getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        val autoplay = prefs.getBoolean("autoplay_enabled", false)
        if (!autoplay) return
        if (ignoreIfWasPlaying) return

        val delay = prefs.getInt("autoplay_delay", 0)
        val minimize = prefs.getBoolean("minimize_after_autoplay", false)

        player.play()

        if (minimize) {
            val action = Runnable {
                sendBroadcast(Intent(Keys.ACTION_HIDE_COUNTDOWN).setPackage(packageName))
                minimizeApp()
            }

            if (delay > 0) {
                val intent = Intent(Keys.ACTION_SHOW_COUNTDOWN).apply {
                    putExtra(Keys.EXTRA_COUNTDOWN_DURATION, delay)
                    setPackage(packageName)
                }
                sendBroadcast(intent)
                Handler(Looper.getMainLooper()).postDelayed(action, delay * 1000L)
            } else {
                action.run()
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        connectivityManager.unregisterNetworkCallback(networkCallback)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        abandonAudioFocus()
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
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
                GlobalScope.launch(Dispatchers.IO) {
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
                                "✅ Spotify-Infos gefunden: ${extendedInfo.trackName}"
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
                                    url = extendedInfo.spotifyUrl.takeIf { it.isNotBlank() }
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
                                    url = null
                                )
                            )
                        }
                    }
                }
            } else {
                GlobalScope.launch(Dispatchers.Main) {
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
                        url = null
                    )
                )
            }
        } else {
            Log.d(
                "StreamingService",
                "⚠️ Artist oder Title fehlen – kein Spotify-Request. Fallback auf alte meta"
            )
            // Sicherstellen, dass auch dieser Aufruf im Main-Thread läuft!
            GlobalScope.launch(Dispatchers.Main) {
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
                    url = null
                )
            )
        }

    }

    fun updateMediaItemMetadata(title: String, artist: String, artworkUri: String) {

        val isInForeground = isAppInForeground()
        lastArtworkUri = artworkUri

        val updateartist: String
        var updatetitle: String

        if (isInForeground) {
            updateartist = artist
            updatetitle = title
            Log.d("StreamingService", "App im Vordergrund: bleibt so")
        } else {
            updatetitle = "$artist - $title"
            if (artist.isBlank() && title.isBlank()) {
                updatetitle = getString(R.string.no_metadata_available)
            }
            updateartist = player.currentMediaItem?.mediaMetadata?.extras?.getString("EXTRA_STATION_NAME") ?: "Sendername nicht Gesetzt"
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
        val refreshMetaData = lastshowedMetadata
        val isInForeground = isAppInForeground()

        val updateartist: String
        var updatetitle: String
        val artist = refreshMetaData?.artist?.toString().orEmpty()
        val title = refreshMetaData?.title?.toString().orEmpty()
        val artworkUri = refreshMetaData?.artworkUri?.toString().orEmpty()

        if (isInForeground) {
            updateartist = artist
            updatetitle = title
            Log.d("StreamingService", "App im Vordergrund: bleibt so")
        } else {
            updatetitle = "$artist - $title"
            if (artist.isBlank() && title.isBlank()) {
                updatetitle = getString(R.string.no_metadata_available)
            }
            updateartist = player.currentMediaItem?.mediaMetadata?.extras?.getString("EXTRA_STATION_NAME") ?: "Sendername nicht Gesetzt"
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


}
