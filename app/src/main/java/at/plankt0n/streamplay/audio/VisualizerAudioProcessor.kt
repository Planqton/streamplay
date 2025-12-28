package at.plankt0n.streamplay.audio

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

@UnstableApi
class VisualizerAudioProcessor : AudioProcessor {

    companion object {
        private const val FFT_SIZE = 512
        private const val BAND_COUNT = 32
        private const val UPDATE_INTERVAL_MS = 50L
    }

    interface Listener {
        fun onFftDataAvailable(magnitudes: FloatArray)
    }

    private var listener: Listener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var inputAudioFormat = AudioFormat.NOT_SET
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    private val sampleBuffer = FloatArray(FFT_SIZE)
    @Volatile
    private var sampleIndex = 0
    private var lastUpdateTime = 0L
    private val bufferLock = Any()

    // Pre-computed Hann window
    private val hannWindow = FloatArray(FFT_SIZE) { i ->
        (0.5 * (1 - cos(2 * PI * i / (FFT_SIZE - 1)))).toFloat()
    }

    // FFT working arrays
    private val fftReal = FloatArray(FFT_SIZE)
    private val fftImag = FloatArray(FFT_SIZE)
    private val magnitudes = FloatArray(BAND_COUNT)

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioFormat.NOT_SET
        }
        this.inputAudioFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return inputAudioFormat != AudioFormat.NOT_SET && listener != null
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) {
            outputBuffer = inputBuffer
            return
        }

        val position = inputBuffer.position()
        val limit = inputBuffer.limit()

        // Process samples for visualization
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            processPcm16(inputBuffer)
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            processPcmFloat(inputBuffer)
        }

        // Reset buffer position
        inputBuffer.position(position)
        inputBuffer.limit(limit)

        // Pass through unchanged
        outputBuffer = inputBuffer
    }

    private fun processPcm16(buffer: ByteBuffer) {
        val order = buffer.order()
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val channelCount = inputAudioFormat.channelCount
        val bytesPerSample = 2 * channelCount

        while (buffer.remaining() >= bytesPerSample) {
            // Mix channels to mono
            var sample = 0f
            for (c in 0 until channelCount) {
                sample += buffer.short.toFloat() / 32768f
            }
            sample /= channelCount

            sampleBuffer[sampleIndex] = sample
            sampleIndex = (sampleIndex + 1) % FFT_SIZE

            // Check if we should update
            if (sampleIndex == 0) {
                maybeComputeAndNotify()
            }
        }

        buffer.order(order)
    }

    private fun processPcmFloat(buffer: ByteBuffer) {
        val order = buffer.order()
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val channelCount = inputAudioFormat.channelCount
        val bytesPerSample = 4 * channelCount

        while (buffer.remaining() >= bytesPerSample) {
            // Mix channels to mono
            var sample = 0f
            for (c in 0 until channelCount) {
                sample += buffer.float
            }
            sample /= channelCount

            sampleBuffer[sampleIndex] = sample
            sampleIndex = (sampleIndex + 1) % FFT_SIZE

            // Check if we should update
            if (sampleIndex == 0) {
                maybeComputeAndNotify()
            }
        }

        buffer.order(order)
    }

    private fun maybeComputeAndNotify() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < UPDATE_INTERVAL_MS) {
            return
        }
        lastUpdateTime = now

        // Apply Hann window and copy to FFT arrays
        for (i in 0 until FFT_SIZE) {
            val idx = (sampleIndex + i) % FFT_SIZE
            fftReal[i] = sampleBuffer[idx] * hannWindow[i]
            fftImag[i] = 0f
        }

        // Perform FFT
        fft(fftReal, fftImag)

        // Compute magnitudes for frequency bands
        computeBandMagnitudes()

        // Notify listener on main thread
        val magnitudesCopy = magnitudes.copyOf()
        mainHandler.post {
            listener?.onFftDataAvailable(magnitudesCopy)
        }
    }

    private fun computeBandMagnitudes() {
        val nyquist = FFT_SIZE / 2

        // Use logarithmic frequency bands for better visual appearance
        for (band in 0 until BAND_COUNT) {
            val lowFreqRatio = band.toFloat() / BAND_COUNT
            val highFreqRatio = (band + 1).toFloat() / BAND_COUNT

            // Map to logarithmic scale
            val lowBin = (lowFreqRatio.pow(2) * nyquist).toInt().coerceIn(0, nyquist - 1)
            val highBin = (highFreqRatio.pow(2) * nyquist).toInt().coerceIn(lowBin + 1, nyquist)

            var sum = 0f
            for (bin in lowBin until highBin) {
                val magnitude = hypot(fftReal[bin], fftImag[bin])
                sum += magnitude
            }

            val avgMagnitude = sum / (highBin - lowBin).coerceAtLeast(1)

            // Convert to dB scale and normalize
            val db = 20 * kotlin.math.log10(avgMagnitude.coerceAtLeast(0.0001f))
            val normalized = ((db + 60) / 60).coerceIn(0f, 1f)

            magnitudes[band] = normalized
        }
    }

    // In-place Cooley-Tukey FFT
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n == 1) return

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                var temp = real[i]
                real[i] = real[j]
                real[j] = temp
                temp = imag[i]
                imag[i] = imag[j]
                imag[j] = temp
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        // Cooley-Tukey iterative FFT
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = -2 * PI / len
            val wReal = cos(angle).toFloat()
            val wImag = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wr = 1f
                var wi = 0f

                for (k in 0 until halfLen) {
                    val u = i + k
                    val v = i + k + halfLen

                    val tReal = wr * real[v] - wi * imag[v]
                    val tImag = wr * imag[v] + wi * real[v]

                    real[v] = real[u] - tReal
                    imag[v] = imag[u] - tImag
                    real[u] = real[u] + tReal
                    imag[u] = imag[u] + tImag

                    val newWr = wr * wReal - wi * wImag
                    wi = wr * wImag + wi * wReal
                    wr = newWr
                }

                i += len
            }

            len *= 2
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer == AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        synchronized(bufferLock) {
            sampleIndex = 0
            sampleBuffer.fill(0f)
        }
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioFormat.NOT_SET
        listener = null
    }
}
