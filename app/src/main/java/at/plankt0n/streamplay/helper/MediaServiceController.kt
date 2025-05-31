// Datei: at/plankt0n/streamplay/helper/MediaServiceController.kt
package at.plankt0n.streamplay.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import at.plankt0n.streamplay.StreamingService
import at.plankt0n.streamplay.data.StationItem

class MediaServiceController(private val context: Context) {

    private var mediaController: MediaController? = null
    private var listener: Player.Listener? = null
    private val handler = Handler(Looper.getMainLooper())

    fun initializeAndConnect(
        onConnected: (MediaController) -> Unit,
        onPlaybackChanged: (Boolean) -> Unit,
        onStreamIndexChanged: (Int) -> Unit,
        onMetadataChanged: (String) -> Unit,
        onTimelineChanged: (Int) -> Unit
    ) {
        // Service als Foreground starten
        val serviceIntent = Intent(context, StreamingService::class.java)
        context.startForegroundService(serviceIntent)

        // MediaController bauen
        val sessionToken = SessionToken(context, ComponentName(context, StreamingService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()

        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller

                logMediaSessionItems(controller)

                listener = object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        onPlaybackChanged(isPlaying)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        handler.postDelayed({
                            val index = controller.currentMediaItemIndex
                            if (index >= 0) {
                                onStreamIndexChanged(index)
                            }
                        }, 100)
                    }

                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        Log.d("MediaServiceController", "🔁 Timeline geändert! Grund: $reason")
                        onTimelineChanged(reason)
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        mediaMetadata.title?.toString()?.let { onMetadataChanged(it) }
                    }
                }

                controller.addListener(listener!!)
                onConnected(controller)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, context.mainExecutor)
    }

    private fun logMediaSessionItems(controller: MediaController) {
        Log.d("MediaServiceController", "====== MEDIASESSION ITEMS ======")
        for (i in 0 until controller.mediaItemCount) {
            val mediaItem = controller.getMediaItemAt(i)
            val uri = mediaItem.localConfiguration?.uri?.toString() ?: "(keine URI)"
            Log.d("MediaServiceController", "[$i] $uri")
        }
    }

    fun disconnect() {
        listener?.let { mediaController?.removeListener(it) }
        mediaController?.release()
        mediaController = null
        listener = null
    }

    fun isPlaying(): Boolean = mediaController?.isPlaying == true

    fun playAtIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
        }
    }
    fun getCurrentPlaylist(): List<StationItem> {
        val controller = mediaController ?: return emptyList()
        val itemCount = controller.mediaItemCount
        val streams = mutableListOf<StationItem>()

        for (i in 0 until itemCount) {
            val mediaItem = controller.getMediaItemAt(i)
            val metadata: MediaMetadata = mediaItem.mediaMetadata
            val extras = metadata.extras

            val uuid = extras?.getString("EXTRA_UUID") ?: ""

            val url = mediaItem.localConfiguration?.uri?.toString() ?: ""
            val iconUrl = extras?.getString("EXTRA_ICON_URL") ?: ""
            val name = extras?.getString("EXTRA_STATION_NAME") ?: ""
            streams.add(
                StationItem(
                    uuid = uuid,
                    stationName = name,
                    streamURL = url,
                    iconURL = iconUrl
                )
            )
        }

        return streams
    }


    fun seekToIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    fun skipToNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun getCurrentStreamIndex(): Int = mediaController?.currentMediaItemIndex ?: 0
}
