package at.plankt0n.streamplay

import at.plankt0n.streamplay.viewmodel.SpotifyMetaViewModel
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.PreferencesHelper
import at.plankt0n.streamplay.helper.SpotifyMetaReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class StreamingService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    private var streams: List<StationItem> = emptyList()
    private var mediaItems: List<MediaItem> = emptyList()
    private var currentIndex = 0
    private var lastMetadata: MediaMetadata? = null
    private var currentMetadata: MediaMetadata? = null

    companion object {
        const val CHANNEL_ID = "stream_service_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "at.plankt0n.streamplay.ACTION_REFRESH_PLAYLIST") {
            refreshPlaylist()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @androidx.media3.common.util.UnstableApi
    override fun onCreate() {
        super.onCreate()

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
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentIndex = currentMediaItemIndex
                        PreferencesHelper.setLastPlayedStreamIndex(this@StreamingService, currentIndex)
                        Log.d("StreamingService", "💾 Index gespeichert: $currentIndex")
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("StreamingService", "❌ ExoPlayer-Fehler: ${error.errorCodeName} - ${error.message}")
                        Log.e("StreamingService", "Cause: ${error.cause?.message ?: "unbekannt"}")
                        error.cause?.printStackTrace()
                    }

                    override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                        currentMetadata = metadata
                        writeCurrentMetaData(metadata)
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
        player.prepare()
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
        player.prepare()

        if (wasPlaying) player.play()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    fun writeCurrentMetaData(metadata: MediaMetadata)
    {
        val rawTitle = metadata.title?.toString()?.trim().orEmpty()
        val rawArtist = metadata.artist?.toString()?.trim().orEmpty()
        val fallbackArtwork = metadata.extras?.getString("EXTRA_ICON_URL") //fallback falls wir später über die Spotify API kein ICON bekommen oder Gar kein Spotify Track gefunden wird

        //Fürs Splitten! Wenn artist leer ist wird der title mit den Trennzeichen gesplittet
        //Dies hat den Hintergrund dass die Meisten ICY META die exoplayer weiterleitet den Artist unt Titel nicht getrennt ausgeben sondern so Titel - Artist (was natürlich getrennt werden muss)
        //Dies ist eigentlich nur ein fallback so oder so haben wir am ende artist und title getrennt ->
        var artist = rawArtist
        var title = rawTitle
        if (artist.isEmpty()) {
            val delimiters = listOf(" - ", " / ")
            for (delimiter in delimiters) {
                if (rawTitle.contains(delimiter)) {
                    val parts = rawTitle.split(delimiter, limit = 2)
                    if (parts.size == 2) {
                        artist = parts[0].trim()
                        title = parts[1].trim()
                        break
                    }
                }
            }
        }


        if (lastMetadata == metadata) {
            Log.d("StreamingService", "ℹ️ Keine Änderung in der Metadata.")
            return
        }
        lastMetadata = metadata
        Log.d("StreamingService", "🎶 MediaMetadataChanged - Artist: '$artist' | Title: '$title'")

        if (artist.isNotEmpty() && title.isNotEmpty()) {
            GlobalScope.launch(Dispatchers.IO) {
                val extendedInfo = SpotifyMetaReader.getExtendedMetaInfo(this@StreamingService, artist, title)
                if (extendedInfo != null) {
                    Log.d("SpotifyMetaReader", "✅ Spotify-Infos gefunden: ${extendedInfo.trackName}")
                    withContext(Dispatchers.Main) {
                        updateMediaItemMetadata(
                            title = extendedInfo.trackName,
                            artist = extendedInfo.artistName,
                            artworkUri = extendedInfo.bestCoverUrl ?: ""
                        )
                    }
                } else {
                    Log.w("SpotifyMetaReader", "❌ Keine Spotify-Daten gefunden für: $artist - $title")

                }
            }
        } else {
            Log.d("StreamingService", "⚠️ Artist oder Title fehlen – kein Spotify-Request.")
        }
    }


    fun updateMediaItemMetadata(title: String, artist: String, artworkUri: String) {
        player.currentMediaItem?.let { mediaItem ->
            val currentMediaItemMetadata = mediaItem.mediaMetadata
            val extras = currentMediaItemMetadata.extras ?: Bundle()
            val updatedMetadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(Uri.parse(artworkUri))
                .setExtras(extras)
                .build()

            val updatedMediaItem = MediaItem.Builder()
                .setUri(mediaItem.localConfiguration?.uri ?: mediaItem.requestMetadata.mediaUri)
                .setMediaMetadata(updatedMetadata)
                .build()

            player.replaceMediaItem(player.currentMediaItemIndex, updatedMediaItem)
            Log.d("StreamingService", "🔄 Nur Metadaten aktualisiert: $title - $artist")
        }
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


}
