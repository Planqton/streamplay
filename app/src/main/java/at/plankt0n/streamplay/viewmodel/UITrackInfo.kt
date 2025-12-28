package at.plankt0n.streamplay.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.concurrent.atomic.AtomicReference

data class UITrackInfo(
    val trackName: String = "",
    val artistName: String = "",
    val bestCoverUrl: String? = null,
    val albumName: String = "",
    val durationMs: Long = 0L,
    val albumReleaseDate: String = "",
    val popularity: Int = 0,
    val spotifyUrl: String = "",
    val previewUrl: String? = null,
    val genre: String = ""
)

/**
 * Repository object that stores the currently fetched Spotify track information.
 * This object can be accessed from anywhere in the app, including the
 * [StreamingService]. The [UITrackViewModel] simply exposes the data held here.
 */
object UITrackRepository {
    private val _trackInfo = MutableLiveData<UITrackInfo?>()
    val trackInfo: LiveData<UITrackInfo?> get() = _trackInfo

    private val lock = Any()
    @Volatile
    private var lastTrackInfo: UITrackInfo? = null

    // Atomic request ID tracking for race condition prevention
    private val expectedRequestId = AtomicReference<String?>(null)

    /**
     * Set the expected request ID for upcoming updates.
     * Updates with non-matching IDs will be ignored by updateTrackInfoIfCurrent().
     */
    fun setExpectedRequestId(requestId: String?) {
        expectedRequestId.set(requestId)
        Log.d("UITrackRepository", "🔑 Expected request ID set to: $requestId")
    }

    /**
     * Update track info only if the request ID matches the current expected request.
     * Returns true if update was applied, false if stale/ignored.
     */
    fun updateTrackInfoIfCurrent(info: UITrackInfo, requestId: String): Boolean {
        synchronized(lock) {
            val expected = expectedRequestId.get()
            if (expected != null && expected != requestId) {
                Log.d("UITrackRepository", "⏭️ Ignoring stale update for request $requestId (expected $expected)")
                return false
            }

            if (lastTrackInfo != info) {
                lastTrackInfo = info
                Log.d("UITrackRepository", "📡 Posting trackInfo: ${info.trackName} by ${info.artistName} (requestId=$requestId)")
                _trackInfo.postValue(info)
            }
            return true
        }
    }

    fun updateTrackInfo(info: UITrackInfo) {
        synchronized(lock) {
            if (lastTrackInfo != info) {
                lastTrackInfo = info
                Log.d("UITrackRepository", "📡 Posting trackInfo: ${info.trackName} by ${info.artistName}, spotifyUrl=${info.spotifyUrl.take(50)}...")
                _trackInfo.postValue(info)
            } else {
                Log.d("UITrackRepository", "⏭️ Skipping duplicate trackInfo: ${info.trackName}")
            }
        }
    }

    /**
     * Clear track info AND invalidate any pending updates by resetting expected request ID.
     */
    fun clearTrackInfoAndInvalidatePending() {
        synchronized(lock) {
            expectedRequestId.set(null)
            lastTrackInfo = null
            _trackInfo.postValue(null)
            Log.d("UITrackRepository", "🧹 Track info cleared, pending updates invalidated")
        }
    }

    fun clearTrackInfo() {
        synchronized(lock) {
            lastTrackInfo = null
            _trackInfo.postValue(null)
            Log.d("UITrackinfo", "Trackinfo cleared")
        }
    }
}

/**
 * ViewModel used by UI components to observe changes in [UITrackRepository].
 */
class UITrackViewModel : ViewModel() {
    val trackInfo: LiveData<UITrackInfo?> = UITrackRepository.trackInfo

    fun updateTrackInfo(info: UITrackInfo) = UITrackRepository.updateTrackInfo(info)

    fun clearTrackInfo() = UITrackRepository.clearTrackInfo()
}
