package at.plankt0n.streamplay.helper

import at.plankt0n.streamplay.view.VisualizerView
import java.util.concurrent.atomic.AtomicBoolean

object StateHelper {
    // Thread-safe mit AtomicBoolean (Race Condition Fix)
    private val _isPlaylistChangePending = AtomicBoolean(false)
    var isPlaylistChangePending: Boolean
        get() = _isPlaylistChangePending.get()
        set(value) = _isPlaylistChangePending.set(value)

    private val _hasAutoOpenedDiscover = AtomicBoolean(false)
    var hasAutoOpenedDiscover: Boolean
        get() = _hasAutoOpenedDiscover.get()
        set(value) = _hasAutoOpenedDiscover.set(value)
    var audioSessionId: Int = 0
    var visualizerStyle: VisualizerView.Style = VisualizerView.Style.BARS

    // Visualizer FFT data sharing
    interface VisualizerListener {
        fun onFftDataAvailable(magnitudes: FloatArray)
    }

    private val visualizerListeners = java.util.Collections.synchronizedSet(mutableSetOf<VisualizerListener>())

    fun addVisualizerListener(listener: VisualizerListener) {
        visualizerListeners.add(listener)
    }

    fun removeVisualizerListener(listener: VisualizerListener) {
        visualizerListeners.remove(listener)
    }

    fun notifyVisualizerData(magnitudes: FloatArray) {
        // Thread-safe copy for iteration
        synchronized(visualizerListeners) {
            visualizerListeners.toList()
        }.forEach { it.onFftDataAvailable(magnitudes) }
    }
}
