package at.plankt0n.streamplay.helper

import at.plankt0n.streamplay.view.VisualizerView

object StateHelper {
    var isPlaylistChangePending: Boolean = false
    var hasAutoOpenedDiscover: Boolean = false
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
