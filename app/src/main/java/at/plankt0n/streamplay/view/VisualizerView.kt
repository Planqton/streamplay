package at.plankt0n.streamplay.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "VisualizerView"
        private const val BAR_COUNT = 32
    }

    enum class Style {
        BARS,           // Klassische Frequenzbalken von der Mitte
        WAVE,           // Wellenform gefüllt
        CIRCLE,         // Kreisförmiger Visualizer
        LINE,           // Pulsierende Linie mit Punkten
        SPECTRUM,       // Spektrum-Analyzer von unten
        RINGS,          // Konzentrische Ringe
        BLOB,           // Organische Blob-Form
        MIRROR,         // Gespiegelte Balken links/rechts
        DNA,            // Doppelhelix
        FOUNTAIN,       // Springbrunnen-Effekt
        EQUALIZER,      // Klassischer Equalizer mit Peaks
        RADAR,          // Radar-Sweep mit Daten
        PULSE,          // Pulsierender Glow-Effekt
        PLASMA,         // Plasma/Lava-Lampen Effekt
        ORBITS,         // Umlaufbahnen mit Partikeln
        BLUR_MOTION     // Animierter Blur-Effekt
    }

    var style: Style = Style.BARS
        set(value) {
            field = value
            invalidate()
        }

    private val barPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val glowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val wavePath = Path()

    // Smoothing for animations
    private val smoothedMagnitudes = FloatArray(BAR_COUNT)
    private val targetMagnitudes = FloatArray(BAR_COUNT)
    private val peakMagnitudes = FloatArray(BAR_COUNT) // For peak hold
    private val peakDecay = FloatArray(BAR_COUNT)
    private val smoothingFactor = 0.3f

    // Colors for gradient
    private var primaryColor = Color.parseColor("#6200EE")
    private var secondaryColor = Color.parseColor("#03DAC5")

    // Animation state
    private var animationTime = 0f
    private var radarAngle = 0f
    private val animationHandler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            animationTime += 0.05f
            radarAngle = (radarAngle + 3f) % 360f
            // Generate fake magnitudes for fallback
            for (i in 0 until BAR_COUNT) {
                val phase = animationTime + i * 0.2f
                targetMagnitudes[i] = (sin(phase.toDouble()).toFloat() * 0.5f + 0.5f) * 0.7f + 0.1f
            }
            updateSmoothing()
            invalidate()
            animationHandler.postDelayed(this, 50)
        }
    }
    private var isAnimating = false
    private var hasRealData = false

    fun setColors(primary: Int, secondary: Int) {
        primaryColor = primary
        secondaryColor = secondary
        invalidate()
    }

    fun setMagnitudes(magnitudes: FloatArray) {
        hasRealData = true
        stopFallbackAnimation()

        val count = minOf(magnitudes.size, BAR_COUNT)
        for (i in 0 until count) {
            targetMagnitudes[i] = magnitudes[i]
        }
        for (i in count until BAR_COUNT) {
            targetMagnitudes[i] = 0f
        }

        updateSmoothing()
        updatePeaks()
        radarAngle = (radarAngle + 3f) % 360f
        invalidate()
    }

    private fun updateSmoothing() {
        for (i in 0 until BAR_COUNT) {
            smoothedMagnitudes[i] = smoothedMagnitudes[i] * (1 - smoothingFactor) +
                    targetMagnitudes[i] * smoothingFactor
        }
    }

    private fun updatePeaks() {
        for (i in 0 until BAR_COUNT) {
            if (smoothedMagnitudes[i] > peakMagnitudes[i]) {
                peakMagnitudes[i] = smoothedMagnitudes[i]
                peakDecay[i] = 0f
            } else {
                peakDecay[i] += 0.02f
                peakMagnitudes[i] = (peakMagnitudes[i] - peakDecay[i]).coerceAtLeast(0f)
            }
        }
    }

    fun startFallbackAnimation() {
        if (!isAnimating) {
            hasRealData = false
            isAnimating = true
            animationHandler.post(animationRunnable)
        }
    }

    fun stopFallbackAnimation() {
        if (isAnimating) {
            isAnimating = false
            animationHandler.removeCallbacks(animationRunnable)
        }
    }

    fun release() {
        stopFallbackAnimation()
        hasRealData = false
        smoothedMagnitudes.fill(0f)
        targetMagnitudes.fill(0f)
        peakMagnitudes.fill(0f)
        peakDecay.fill(0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (style) {
            Style.BARS -> drawBars(canvas)
            Style.WAVE -> drawWave(canvas)
            Style.CIRCLE -> drawCircle(canvas)
            Style.LINE -> drawLine(canvas)
            Style.SPECTRUM -> drawSpectrum(canvas)
            Style.RINGS -> drawRings(canvas)
            Style.BLOB -> drawBlob(canvas)
            Style.MIRROR -> drawMirror(canvas)
            Style.DNA -> drawDNA(canvas)
            Style.FOUNTAIN -> drawFountain(canvas)
            Style.EQUALIZER -> drawEqualizer(canvas)
            Style.RADAR -> drawRadar(canvas)
            Style.PULSE -> drawPulse(canvas)
            Style.PLASMA -> drawPlasma(canvas)
            Style.ORBITS -> drawOrbits(canvas)
            Style.BLUR_MOTION -> drawBlurMotion(canvas)
        }
    }

    private fun drawBars(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2

        val totalBarWidth = width / BAR_COUNT
        val barWidth = totalBarWidth * 0.7f
        val barSpacing = totalBarWidth * 0.15f

        for (i in 0 until BAR_COUNT) {
            val smoothedMagnitude = smoothedMagnitudes[i]
            val barHeight = smoothedMagnitude * height * 0.45f
            val x = barSpacing + i * totalBarWidth

            val colorFraction = i.toFloat() / BAR_COUNT
            val barColor = interpolateColor(primaryColor, secondaryColor, colorFraction)

            barPaint.color = barColor

            canvas.drawRoundRect(x, centerY - barHeight, x + barWidth, centerY, barWidth / 4, barWidth / 4, barPaint)
            canvas.drawRoundRect(x, centerY, x + barWidth, centerY + barHeight, barWidth / 4, barWidth / 4, barPaint)

            if (smoothedMagnitude > 0.5f) {
                glowPaint.color = adjustAlpha(barColor, ((smoothedMagnitude - 0.5f) * 0.6f).toInt() * 255 / 100)
                val glowRadius = barWidth * 0.3f
                canvas.drawRoundRect(x - glowRadius, centerY - barHeight - glowRadius, x + barWidth + glowRadius, centerY + barHeight + glowRadius, barWidth / 2, barWidth / 2, glowPaint)
            }
        }
    }

    private fun drawWave(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2

        wavePath.reset()
        val segmentWidth = width / (BAR_COUNT - 1)

        wavePath.moveTo(0f, centerY)
        for (i in 0 until BAR_COUNT) {
            val x = i * segmentWidth
            val magnitude = smoothedMagnitudes[i]
            val y = centerY - magnitude * height * 0.4f
            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
        }

        for (i in BAR_COUNT - 1 downTo 0) {
            val x = i * segmentWidth
            val magnitude = smoothedMagnitudes[i]
            val y = centerY + magnitude * height * 0.4f
            wavePath.lineTo(x, y)
        }
        wavePath.close()

        barPaint.color = adjustAlpha(primaryColor, 180)
        canvas.drawPath(wavePath, barPaint)

        linePaint.color = secondaryColor
        linePaint.strokeWidth = 3f
        canvas.drawPath(wavePath, linePaint)
    }

    private fun drawCircle(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2
        val centerY = height / 2
        val baseRadius = min(width, height) * 0.25f

        for (i in 0 until BAR_COUNT) {
            val magnitude = smoothedMagnitudes[i]
            val angle = (i.toFloat() / BAR_COUNT) * 2 * Math.PI.toFloat()

            val innerRadius = baseRadius
            val outerRadius = baseRadius + magnitude * baseRadius * 0.8f

            val innerX = centerX + cos(angle) * innerRadius
            val innerY = centerY + sin(angle) * innerRadius
            val outerX = centerX + cos(angle) * outerRadius
            val outerY = centerY + sin(angle) * outerRadius

            val colorFraction = i.toFloat() / BAR_COUNT
            val barColor = interpolateColor(primaryColor, secondaryColor, colorFraction)

            linePaint.color = barColor
            linePaint.strokeWidth = 8f
            canvas.drawLine(innerX, innerY, outerX, outerY, linePaint)

            if (magnitude > 0.5f) {
                linePaint.color = adjustAlpha(barColor, 80)
                linePaint.strokeWidth = 16f
                canvas.drawLine(innerX, innerY, outerX, outerY, linePaint)
            }
        }

        barPaint.color = adjustAlpha(primaryColor, 150)
        canvas.drawCircle(centerX, centerY, baseRadius * 0.3f, barPaint)
    }

    private fun drawLine(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2
        val segmentWidth = width / (BAR_COUNT - 1)

        val avgMagnitude = smoothedMagnitudes.average().toFloat()

        if (avgMagnitude > 0.2f) {
            linePaint.color = adjustAlpha(secondaryColor, (avgMagnitude * 100).toInt())
            linePaint.strokeWidth = 20f + avgMagnitude * 30f

            wavePath.reset()
            for (i in 0 until BAR_COUNT) {
                val x = i * segmentWidth
                val magnitude = smoothedMagnitudes[i]
                val y = centerY - magnitude * height * 0.3f + (if (i % 2 == 0) magnitude * 20 else -magnitude * 20)
                if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            canvas.drawPath(wavePath, linePaint)
        }

        linePaint.strokeWidth = 6f
        wavePath.reset()
        for (i in 0 until BAR_COUNT) {
            val x = i * segmentWidth
            val magnitude = smoothedMagnitudes[i]
            val y = centerY - magnitude * height * 0.3f + (if (i % 2 == 0) magnitude * 20 else -magnitude * 20)
            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
        }
        linePaint.color = primaryColor
        canvas.drawPath(wavePath, linePaint)

        for (i in 0 until BAR_COUNT) {
            val x = i * segmentWidth
            val magnitude = smoothedMagnitudes[i]
            val y = centerY - magnitude * height * 0.3f + (if (i % 2 == 0) magnitude * 20 else -magnitude * 20)
            val colorFraction = i.toFloat() / BAR_COUNT
            barPaint.color = interpolateColor(primaryColor, secondaryColor, colorFraction)
            val dotRadius = 4f + magnitude * 8f
            canvas.drawCircle(x, y, dotRadius, barPaint)
        }
    }

    private fun drawSpectrum(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()

        val totalBarWidth = width / BAR_COUNT
        val barWidth = totalBarWidth * 0.8f
        val barSpacing = totalBarWidth * 0.1f

        for (i in 0 until BAR_COUNT) {
            val magnitude = smoothedMagnitudes[i]
            val barHeight = magnitude * height * 0.85f
            val x = barSpacing + i * totalBarWidth

            val colorFraction = i.toFloat() / BAR_COUNT
            val barColor = interpolateColor(primaryColor, secondaryColor, colorFraction)

            // Main bar
            barPaint.color = barColor
            canvas.drawRoundRect(x, height - barHeight, x + barWidth, height, barWidth / 4, barWidth / 4, barPaint)

            // Glow at top
            if (magnitude > 0.3f) {
                glowPaint.color = adjustAlpha(barColor, (magnitude * 100).toInt())
                canvas.drawCircle(x + barWidth / 2, height - barHeight, barWidth * 0.6f, glowPaint)
            }
        }
    }

    private fun drawRings(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2
        val centerY = height / 2
        val maxRadius = min(width, height) * 0.45f

        linePaint.style = Paint.Style.STROKE

        val ringCount = 8
        for (ring in 0 until ringCount) {
            val ringFraction = ring.toFloat() / ringCount
            val baseRadius = maxRadius * (0.2f + ringFraction * 0.8f)

            // Get average magnitude for this ring's frequency range
            val startIdx = (ring * BAR_COUNT / ringCount)
            val endIdx = ((ring + 1) * BAR_COUNT / ringCount).coerceAtMost(BAR_COUNT)
            var avgMagnitude = 0f
            for (i in startIdx until endIdx) {
                avgMagnitude += smoothedMagnitudes[i]
            }
            avgMagnitude /= (endIdx - startIdx).coerceAtLeast(1)

            val radius = baseRadius + avgMagnitude * maxRadius * 0.15f
            val colorFraction = ringFraction
            val ringColor = interpolateColor(primaryColor, secondaryColor, colorFraction)

            linePaint.color = ringColor
            linePaint.strokeWidth = 4f + avgMagnitude * 8f
            canvas.drawCircle(centerX, centerY, radius, linePaint)

            if (avgMagnitude > 0.5f) {
                linePaint.color = adjustAlpha(ringColor, 60)
                linePaint.strokeWidth = 12f + avgMagnitude * 16f
                canvas.drawCircle(centerX, centerY, radius, linePaint)
            }
        }

        linePaint.style = Paint.Style.STROKE
    }

    private fun drawBlob(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2
        val centerY = height / 2
        val baseRadius = min(width, height) * 0.25f

        wavePath.reset()

        val points = BAR_COUNT
        for (i in 0..points) {
            val idx = i % BAR_COUNT
            val magnitude = smoothedMagnitudes[idx]
            val angle = (i.toFloat() / points) * 2 * Math.PI.toFloat()
            val radius = baseRadius + magnitude * baseRadius * 0.6f

            val x = centerX + cos(angle) * radius
            val y = centerY + sin(angle) * radius

            if (i == 0) {
                wavePath.moveTo(x, y)
            } else {
                // Smooth bezier curve
                val prevIdx = (i - 1) % BAR_COUNT
                val prevMagnitude = smoothedMagnitudes[prevIdx]
                val prevAngle = ((i - 1).toFloat() / points) * 2 * Math.PI.toFloat()
                val prevRadius = baseRadius + prevMagnitude * baseRadius * 0.6f

                val midAngle = (prevAngle + angle) / 2
                val midRadius = (prevRadius + radius) / 2
                val ctrlX = centerX + cos(midAngle) * midRadius * 1.1f
                val ctrlY = centerY + sin(midAngle) * midRadius * 1.1f

                wavePath.quadTo(ctrlX, ctrlY, x, y)
            }
        }
        wavePath.close()

        // Fill
        barPaint.color = adjustAlpha(primaryColor, 150)
        canvas.drawPath(wavePath, barPaint)

        // Outline
        linePaint.color = secondaryColor
        linePaint.strokeWidth = 4f
        linePaint.style = Paint.Style.STROKE
        canvas.drawPath(wavePath, linePaint)

        // Inner glow
        val avgMagnitude = smoothedMagnitudes.average().toFloat()
        barPaint.color = adjustAlpha(secondaryColor, (avgMagnitude * 100).toInt())
        canvas.drawCircle(centerX, centerY, baseRadius * 0.4f * (1 + avgMagnitude), barPaint)
    }

    private fun drawMirror(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2

        val halfBars = BAR_COUNT / 2
        val barWidth = (centerX / halfBars) * 0.7f
        val spacing = (centerX / halfBars) * 0.15f

        for (i in 0 until halfBars) {
            val magnitude = smoothedMagnitudes[i]
            val barHeight = magnitude * height * 0.8f
            val y = (height - barHeight) / 2

            val colorFraction = i.toFloat() / halfBars
            val barColor = interpolateColor(primaryColor, secondaryColor, colorFraction)
            barPaint.color = barColor

            // Right side
            val xRight = centerX + spacing + i * (barWidth + spacing * 2)
            canvas.drawRoundRect(xRight, y, xRight + barWidth, y + barHeight, barWidth / 4, barWidth / 4, barPaint)

            // Left side (mirrored)
            val xLeft = centerX - spacing - barWidth - i * (barWidth + spacing * 2)
            canvas.drawRoundRect(xLeft, y, xLeft + barWidth, y + barHeight, barWidth / 4, barWidth / 4, barPaint)

            // Glow
            if (magnitude > 0.5f) {
                glowPaint.color = adjustAlpha(barColor, 50)
                canvas.drawRoundRect(xRight - 4, y - 4, xRight + barWidth + 4, y + barHeight + 4, barWidth / 3, barWidth / 3, glowPaint)
                canvas.drawRoundRect(xLeft - 4, y - 4, xLeft + barWidth + 4, y + barHeight + 4, barWidth / 3, barWidth / 3, glowPaint)
            }
        }

        // Center line
        linePaint.color = adjustAlpha(secondaryColor, 100)
        linePaint.strokeWidth = 2f
        canvas.drawLine(centerX, 0f, centerX, height, linePaint)
    }

    private fun drawDNA(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2

        val segmentWidth = width / BAR_COUNT
        val amplitude = height * 0.3f
        val phase = animationTime * 2

        for (i in 0 until BAR_COUNT) {
            val magnitude = smoothedMagnitudes[i]
            val x = i * segmentWidth + segmentWidth / 2
            val waveOffset = sin((i * 0.3f + phase).toDouble()).toFloat()

            val y1 = centerY + waveOffset * amplitude * (0.5f + magnitude * 0.5f)
            val y2 = centerY - waveOffset * amplitude * (0.5f + magnitude * 0.5f)

            val colorFraction = i.toFloat() / BAR_COUNT
            val color1 = interpolateColor(primaryColor, secondaryColor, colorFraction)
            val color2 = interpolateColor(secondaryColor, primaryColor, colorFraction)

            val dotSize = 6f + magnitude * 10f

            // Strand 1
            barPaint.color = color1
            canvas.drawCircle(x, y1, dotSize, barPaint)

            // Strand 2
            barPaint.color = color2
            canvas.drawCircle(x, y2, dotSize, barPaint)

            // Connecting line
            if (abs(waveOffset) < 0.3f) {
                linePaint.color = adjustAlpha(color1, 80)
                linePaint.strokeWidth = 2f + magnitude * 4f
                canvas.drawLine(x, y1, x, y2, linePaint)
            }
        }
    }

    private fun drawFountain(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2

        val totalWidth = width * 0.8f
        val barWidth = totalWidth / BAR_COUNT * 0.6f
        val spacing = totalWidth / BAR_COUNT * 0.2f
        val startX = (width - totalWidth) / 2

        for (i in 0 until BAR_COUNT) {
            val magnitude = smoothedMagnitudes[i]

            // Distance from center affects height
            val distFromCenter = abs(i - BAR_COUNT / 2).toFloat() / (BAR_COUNT / 2)
            val heightMultiplier = 1f - distFromCenter * 0.5f // Taller in center

            val barHeight = magnitude * height * 0.7f * heightMultiplier
            val x = startX + spacing + i * (barWidth + spacing * 2)

            val colorFraction = 1f - distFromCenter
            val barColor = interpolateColor(primaryColor, secondaryColor, colorFraction)

            // Water drop effect
            barPaint.color = adjustAlpha(barColor, 200)
            canvas.drawRoundRect(x, height - barHeight, x + barWidth, height, barWidth / 2, barWidth / 2, barPaint)

            // Splash at top
            if (magnitude > 0.4f) {
                val splashRadius = barWidth * (0.5f + magnitude * 0.5f)
                barPaint.color = adjustAlpha(barColor, (magnitude * 150).toInt())
                canvas.drawCircle(x + barWidth / 2, height - barHeight - splashRadius, splashRadius, barPaint)
            }
        }

        // Base pool
        barPaint.color = adjustAlpha(primaryColor, 50)
        canvas.drawRoundRect(startX, height - 20, startX + totalWidth, height, 10f, 10f, barPaint)
    }

    private fun drawEqualizer(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()

        val totalBarWidth = width / BAR_COUNT
        val barWidth = totalBarWidth * 0.7f
        val barSpacing = totalBarWidth * 0.15f

        for (i in 0 until BAR_COUNT) {
            val magnitude = smoothedMagnitudes[i]
            val peak = peakMagnitudes[i]
            val barHeight = magnitude * height * 0.8f
            val peakY = height - peak * height * 0.8f
            val x = barSpacing + i * totalBarWidth

            val colorFraction = i.toFloat() / BAR_COUNT
            val barColor = interpolateColor(primaryColor, secondaryColor, colorFraction)

            // Segmented bar effect
            val segmentHeight = height * 0.03f
            val segmentGap = height * 0.01f
            var currentY = height

            barPaint.color = barColor
            while (currentY > height - barHeight) {
                val segTop = currentY - segmentHeight
                if (segTop < height - barHeight) break
                canvas.drawRoundRect(x, segTop, x + barWidth, currentY, 2f, 2f, barPaint)
                currentY -= segmentHeight + segmentGap
            }

            // Peak indicator
            barPaint.color = secondaryColor
            canvas.drawRoundRect(x, peakY - 4, x + barWidth, peakY, 2f, 2f, barPaint)
        }
    }

    private fun drawRadar(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2
        val centerY = height / 2
        val maxRadius = min(width, height) * 0.4f

        linePaint.style = Paint.Style.STROKE

        // Draw radar circles
        linePaint.color = adjustAlpha(primaryColor, 40)
        linePaint.strokeWidth = 1f
        for (r in 1..4) {
            val radius = maxRadius * r / 4
            canvas.drawCircle(centerX, centerY, radius, linePaint)
        }

        // Draw cross lines
        canvas.drawLine(centerX - maxRadius, centerY, centerX + maxRadius, centerY, linePaint)
        canvas.drawLine(centerX, centerY - maxRadius, centerX, centerY + maxRadius, linePaint)

        // Draw data points
        for (i in 0 until BAR_COUNT) {
            val magnitude = smoothedMagnitudes[i]
            val angle = (i.toFloat() / BAR_COUNT) * 2 * Math.PI.toFloat()
            val radius = magnitude * maxRadius

            val x = centerX + cos(angle) * radius
            val y = centerY + sin(angle) * radius

            val colorFraction = i.toFloat() / BAR_COUNT
            val dotColor = interpolateColor(primaryColor, secondaryColor, colorFraction)

            barPaint.color = adjustAlpha(dotColor, 180)
            val dotSize = 4f + magnitude * 8f
            canvas.drawCircle(x, y, dotSize, barPaint)

            // Connect to center with line
            linePaint.color = adjustAlpha(dotColor, 60)
            linePaint.strokeWidth = 1f + magnitude * 2f
            canvas.drawLine(centerX, centerY, x, y, linePaint)
        }

        // Sweep line
        val sweepAngle = Math.toRadians(radarAngle.toDouble()).toFloat()
        val sweepX = centerX + cos(sweepAngle) * maxRadius
        val sweepY = centerY + sin(sweepAngle) * maxRadius

        linePaint.color = secondaryColor
        linePaint.strokeWidth = 3f
        canvas.drawLine(centerX, centerY, sweepX, sweepY, linePaint)

        // Sweep glow
        barPaint.color = adjustAlpha(secondaryColor, 30)
        wavePath.reset()
        wavePath.moveTo(centerX, centerY)
        val sweepSpan = 30f
        for (deg in 0..sweepSpan.toInt()) {
            val a = Math.toRadians((radarAngle - deg).toDouble()).toFloat()
            val px = centerX + cos(a) * maxRadius
            val py = centerY + sin(a) * maxRadius
            wavePath.lineTo(px, py)
        }
        wavePath.close()
        canvas.drawPath(wavePath, barPaint)

        linePaint.style = Paint.Style.STROKE
    }

    private fun drawPulse(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2
        val centerY = height / 2
        val maxRadius = min(width, height) * 0.6f

        // Calculate overall intensity from bass frequencies
        val bassIntensity = (smoothedMagnitudes.take(8).average() * 1.5f).toFloat().coerceIn(0f, 1f)
        val midIntensity = smoothedMagnitudes.drop(8).take(16).average().toFloat()
        val highIntensity = smoothedMagnitudes.drop(24).average().toFloat()

        // Outer glow pulse (bass)
        val outerRadius = maxRadius * (0.6f + bassIntensity * 0.4f)
        glowPaint.color = adjustAlpha(primaryColor, (bassIntensity * 80).toInt())
        canvas.drawCircle(centerX, centerY, outerRadius, glowPaint)

        // Middle ring (mids)
        val midRadius = maxRadius * (0.4f + midIntensity * 0.3f)
        glowPaint.color = adjustAlpha(interpolateColor(primaryColor, secondaryColor, 0.5f), (midIntensity * 120).toInt())
        canvas.drawCircle(centerX, centerY, midRadius, glowPaint)

        // Inner core (highs)
        val innerRadius = maxRadius * (0.15f + highIntensity * 0.2f)
        glowPaint.color = adjustAlpha(secondaryColor, (150 + highIntensity * 105).toInt())
        canvas.drawCircle(centerX, centerY, innerRadius, glowPaint)

        // Pulsing ring outlines
        linePaint.style = Paint.Style.STROKE
        for (i in 0..2) {
            val ringPhase = (animationTime * 0.5f + i * 0.33f) % 1f
            val ringRadius = maxRadius * ringPhase * (0.5f + bassIntensity * 0.5f)
            val alpha = ((1f - ringPhase) * 100 * bassIntensity).toInt()

            linePaint.color = adjustAlpha(primaryColor, alpha)
            linePaint.strokeWidth = 3f + (1f - ringPhase) * 8f
            canvas.drawCircle(centerX, centerY, ringRadius, linePaint)
        }

        // Subtle particle effect around the edge
        for (i in 0 until 16) {
            val magnitude = smoothedMagnitudes[i * 2]
            if (magnitude > 0.3f) {
                val angle = (i.toFloat() / 16 * 2 * Math.PI + animationTime).toFloat()
                val dist = outerRadius * (0.8f + magnitude * 0.3f)
                val px = centerX + cos(angle) * dist
                val py = centerY + sin(angle) * dist

                barPaint.color = adjustAlpha(secondaryColor, (magnitude * 200).toInt())
                canvas.drawCircle(px, py, 4f + magnitude * 8f, barPaint)
            }
        }
    }

    private fun drawPlasma(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2
        val centerY = height / 2

        val time = animationTime * 0.3f
        val avgMagnitude = smoothedMagnitudes.average().toFloat()

        // Create multiple moving blob centers
        val blobCount = 5
        val blobs = mutableListOf<Triple<Float, Float, Float>>() // x, y, intensity

        for (i in 0 until blobCount) {
            val angle = time + i * (2 * Math.PI / blobCount).toFloat()
            val dist = min(width, height) * 0.2f * (1f + smoothedMagnitudes[i * 6 % BAR_COUNT] * 0.5f)
            val bx = centerX + cos(angle) * dist
            val by = centerY + sin(angle) * dist
            val intensity = smoothedMagnitudes[i * 6 % BAR_COUNT]
            blobs.add(Triple(bx, by, intensity))
        }

        // Draw gradient blobs
        for ((bx, by, intensity) in blobs) {
            val blobRadius = min(width, height) * 0.25f * (0.5f + intensity)

            // Multiple layers for gradient effect
            for (layer in 5 downTo 1) {
                val layerRadius = blobRadius * layer / 5
                val layerAlpha = (40 * (6 - layer) * (0.3f + intensity * 0.7f)).toInt().coerceIn(0, 255)
                val colorFraction = layer / 5f
                glowPaint.color = adjustAlpha(interpolateColor(primaryColor, secondaryColor, colorFraction), layerAlpha)
                canvas.drawCircle(bx, by, layerRadius, glowPaint)
            }
        }

        // Central glow
        val coreRadius = min(width, height) * 0.15f * (1f + avgMagnitude * 0.5f)
        glowPaint.color = adjustAlpha(secondaryColor, (avgMagnitude * 150).toInt())
        canvas.drawCircle(centerX, centerY, coreRadius, glowPaint)

        // Edge particles
        for (i in 0 until BAR_COUNT) {
            val magnitude = smoothedMagnitudes[i]
            if (magnitude > 0.2f) {
                val angle = (i.toFloat() / BAR_COUNT * 2 * Math.PI + time * 0.5f).toFloat()
                val dist = min(width, height) * 0.35f + magnitude * min(width, height) * 0.1f
                val px = centerX + cos(angle) * dist
                val py = centerY + sin(angle) * dist

                val colorFrac = i.toFloat() / BAR_COUNT
                barPaint.color = adjustAlpha(interpolateColor(primaryColor, secondaryColor, colorFrac), (magnitude * 180).toInt())
                canvas.drawCircle(px, py, 3f + magnitude * 6f, barPaint)
            }
        }
    }

    private fun drawOrbits(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2
        val centerY = height / 2
        val maxRadius = min(width, height) * 0.4f

        val time = animationTime

        // Draw orbit paths
        linePaint.style = Paint.Style.STROKE
        val orbitCount = 4

        for (orbit in 0 until orbitCount) {
            val orbitRadius = maxRadius * (0.4f + orbit * 0.2f)
            val orbitMagnitude = smoothedMagnitudes.drop(orbit * 8).take(8).average().toFloat()

            // Orbit ellipse (slightly tilted for 3D effect)
            val tilt = 0.3f + orbit * 0.1f

            linePaint.color = adjustAlpha(interpolateColor(primaryColor, secondaryColor, orbit.toFloat() / orbitCount), 40)
            linePaint.strokeWidth = 1f

            // Draw ellipse path
            wavePath.reset()
            for (deg in 0..360 step 5) {
                val angle = Math.toRadians(deg.toDouble()).toFloat()
                val px = centerX + cos(angle) * orbitRadius
                val py = centerY + sin(angle) * orbitRadius * tilt
                if (deg == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
            }
            wavePath.close()
            canvas.drawPath(wavePath, linePaint)

            // Orbiting particles
            val particleCount = 3 + orbit
            for (p in 0 until particleCount) {
                val particleAngle = time * (1.5f - orbit * 0.3f) + p * (2 * Math.PI / particleCount).toFloat()
                val px = centerX + cos(particleAngle) * orbitRadius
                val py = centerY + sin(particleAngle) * orbitRadius * tilt

                val particleSize = 4f + orbitMagnitude * 12f
                val colorFrac = (orbit + p.toFloat() / particleCount) / orbitCount

                // Particle glow
                glowPaint.color = adjustAlpha(interpolateColor(primaryColor, secondaryColor, colorFrac), (orbitMagnitude * 100).toInt())
                canvas.drawCircle(px, py, particleSize * 2, glowPaint)

                // Particle core
                barPaint.color = interpolateColor(primaryColor, secondaryColor, colorFrac)
                canvas.drawCircle(px, py, particleSize, barPaint)

                // Trail
                for (t in 1..5) {
                    val trailAngle = particleAngle - t * 0.1f
                    val trailX = centerX + cos(trailAngle) * orbitRadius
                    val trailY = centerY + sin(trailAngle) * orbitRadius * tilt
                    val trailAlpha = ((1f - t / 5f) * orbitMagnitude * 80).toInt()
                    barPaint.color = adjustAlpha(interpolateColor(primaryColor, secondaryColor, colorFrac), trailAlpha)
                    canvas.drawCircle(trailX, trailY, particleSize * (1f - t * 0.15f), barPaint)
                }
            }
        }

        // Central sun
        val sunIntensity = smoothedMagnitudes.take(4).average().toFloat()
        val sunRadius = maxRadius * 0.15f * (1f + sunIntensity * 0.5f)

        glowPaint.color = adjustAlpha(secondaryColor, (sunIntensity * 100).toInt())
        canvas.drawCircle(centerX, centerY, sunRadius * 1.5f, glowPaint)

        barPaint.color = secondaryColor
        canvas.drawCircle(centerX, centerY, sunRadius, barPaint)

        barPaint.color = adjustAlpha(primaryColor, 200)
        canvas.drawCircle(centerX, centerY, sunRadius * 0.6f, barPaint)
    }

    private fun drawBlurMotion(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2
        val centerY = height / 2
        val maxDim = maxOf(width, height)

        val time = animationTime * 0.15f

        // Calculate frequency band intensities - smooth and subtle
        val bassIntensity = smoothedMagnitudes.take(8).average().toFloat().coerceIn(0f, 1f)
        val midIntensity = smoothedMagnitudes.drop(8).take(12).average().toFloat().coerceIn(0f, 1f)
        val overallIntensity = smoothedMagnitudes.average().toFloat()

        // Base layer - fills the entire view with a soft gradient like blurred cover
        // Primary color fills most of the background
        glowPaint.color = adjustAlpha(primaryColor, 40)
        canvas.drawRect(0f, 0f, width, height, glowPaint)

        // Create soft, large blur spots that move slowly - simulating blurred cover regions
        val spotCount = 4

        for (spot in 0 until spotCount) {
            val spotFrac = spot.toFloat() / spotCount

            // Very slow, gentle movement
            val angle = time * 0.5f + spot * (Math.PI / 2).toFloat()
            val moveRadius = maxDim * 0.08f * (1f + bassIntensity * 0.3f)

            // Position spots in different quadrants
            val baseX = when (spot) {
                0 -> width * 0.3f
                1 -> width * 0.7f
                2 -> width * 0.25f
                else -> width * 0.75f
            }
            val baseY = when (spot) {
                0 -> height * 0.35f
                1 -> height * 0.3f
                2 -> height * 0.7f
                else -> height * 0.65f
            }

            val offsetX = cos(angle) * moveRadius
            val offsetY = sin(angle) * moveRadius
            val spotX = baseX + offsetX
            val spotY = baseY + offsetY

            // Large, soft blur radius that pulses gently
            val intensity = if (spot < 2) bassIntensity else midIntensity
            val baseRadius = maxDim * 0.35f
            val radius = baseRadius * (0.9f + intensity * 0.2f)

            // Alternate between primary and secondary color
            val spotColor = if (spot % 2 == 0) primaryColor else secondaryColor

            // Draw multiple soft layers for blur effect
            for (layer in 5 downTo 1) {
                val layerFrac = layer.toFloat() / 5
                val layerRadius = radius * layerFrac
                val alpha = ((1f - layerFrac * 0.6f) * 25 * (0.6f + intensity * 0.4f)).toInt().coerceIn(0, 60)

                glowPaint.color = adjustAlpha(spotColor, alpha)
                canvas.drawCircle(spotX, spotY, layerRadius, glowPaint)
            }
        }

        // Subtle central glow that breathes with the bass
        val breathScale = 1f + bassIntensity * 0.15f
        val coreRadius = maxDim * 0.25f * breathScale

        for (layer in 4 downTo 1) {
            val layerFrac = layer.toFloat() / 4
            val layerRadius = coreRadius * layerFrac
            val alpha = ((1f - layerFrac * 0.5f) * 20 * (0.5f + overallIntensity * 0.5f)).toInt().coerceIn(0, 40)

            val blendColor = interpolateColor(primaryColor, secondaryColor, layerFrac)
            glowPaint.color = adjustAlpha(blendColor, alpha)
            canvas.drawCircle(centerX, centerY, layerRadius, glowPaint)
        }

        // Soft edge vignette that subtly pulses
        val vignetteAlpha = (15 + overallIntensity * 10).toInt().coerceIn(0, 30)
        glowPaint.color = adjustAlpha(primaryColor, vignetteAlpha)

        // Draw corner glows
        val cornerRadius = maxDim * 0.4f * (1f + midIntensity * 0.1f)
        canvas.drawCircle(0f, 0f, cornerRadius, glowPaint)
        canvas.drawCircle(width, 0f, cornerRadius, glowPaint)
        canvas.drawCircle(0f, height, cornerRadius, glowPaint)
        canvas.drawCircle(width, height, cornerRadius, glowPaint)
    }

    private fun interpolateColor(color1: Int, color2: Int, fraction: Float): Int {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)

        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)

        val r = (r1 + (r2 - r1) * fraction).toInt()
        val g = (g1 + (g2 - g1) * fraction).toInt()
        val b = (b1 + (b2 - b1) * fraction).toInt()

        return Color.rgb(r, g, b)
    }

    private fun adjustAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
        // Ensure all handler callbacks are removed to prevent memory leaks
        animationHandler.removeCallbacksAndMessages(null)
    }
}
