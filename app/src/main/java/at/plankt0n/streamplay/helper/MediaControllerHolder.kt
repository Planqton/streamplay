package at.plankt0n.streamplay.helper

import androidx.media3.session.MediaController

/**
 * Singleton to hold MediaController across configuration changes.
 * Prevents disconnect/reconnect when screen rotates.
 */
object MediaControllerHolder {
    var mediaController: MediaController? = null
        private set

    var currentCallbacks: Callbacks? = null

    fun setController(controller: MediaController) {
        mediaController = controller
    }

    fun isConnected(): Boolean {
        return mediaController?.isConnected == true
    }

    fun release() {
        mediaController?.release()
        mediaController = null
        currentCallbacks = null
    }

    data class Callbacks(
        val onPlaybackChanged: (Boolean) -> Unit,
        val onStreamIndexChanged: (Int) -> Unit,
        val onMetadataChanged: (String) -> Unit,
        val onTimelineChanged: (Int) -> Unit,
        val onPlaybackStateChanged: (Int) -> Unit,
        val onPlayerError: (androidx.media3.common.PlaybackException) -> Unit
    )
}
