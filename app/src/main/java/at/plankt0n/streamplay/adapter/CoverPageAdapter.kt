// Datei: at/plankt0n/streamplay/adapter/CoverPageAdapter.kt
package at.plankt0n.streamplay.adapter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.StationItem
import at.plankt0n.streamplay.helper.MediaServiceController
import at.plankt0n.streamplay.helper.LiveCoverHelper
import at.plankt0n.streamplay.helper.StateHelper
import at.plankt0n.streamplay.view.VisualizerView
import android.os.Handler
import android.os.Looper
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.imageview.ShapeableImageView

class CoverPageAdapter(
    private val mediaServiceController: MediaServiceController,
    var backgroundEffect: LiveCoverHelper.BackgroundEffect
) : RecyclerView.Adapter<CoverPageAdapter.CoverViewHolder>(), StateHelper.VisualizerListener {

    var mediaItems: List<StationItem> = mediaServiceController.getCurrentPlaylist()
        private set

    // Speichert die aktuell angezeigte Cover-URL pro Position (Metadata oder Station)
    private val currentCoverUrls = mutableMapOf<Int, String>()

    // Callback für Farbänderungen (für Overlay-Anpassung)
    var onColorChanged: ((Int, Int) -> Unit)? = null  // (position, color)

    // Track active visualizer views for FFT data updates - thread-safe
    private val activeVisualizerViews = java.util.Collections.synchronizedSet(mutableSetOf<VisualizerView>())
    @Volatile
    private var isListenerRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun updateMediaItems() {
        mediaItems = mediaServiceController.getCurrentPlaylist()
        currentCoverUrls.clear()
        notifyDataSetChanged()
    }

    fun setCoverUrlForPosition(position: Int, url: String) {
        currentCoverUrls[position] = url
    }

    fun updateCoverUrl(position: Int, url: String) {
        currentCoverUrls[position] = url
        notifyItemChanged(position)

        // Wenn Visualizer aktiv ist, Farben aus dem neuen Cover extrahieren
        if (backgroundEffect == LiveCoverHelper.BackgroundEffect.VISUALIZER && url.isNotBlank()) {
            extractAndUpdateVisualizerColors(url)
        }
    }

    @Volatile
    private var lastExtractedUrl: String? = null

    /**
     * Extrahiert Farben aus einem Cover-Bild und aktualisiert alle aktiven Visualizer
     */
    fun extractAndUpdateVisualizerColors(imageUrl: String) {
        if (imageUrl.isBlank() || activeVisualizerViews.isEmpty()) return
        if (imageUrl == lastExtractedUrl) return // Keine doppelte Extraktion

        lastExtractedUrl = imageUrl

        try {
            val context = activeVisualizerViews.firstOrNull()?.context ?: return

            Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                        Palette.from(bitmap).generate { palette ->
                            if (palette == null) return@generate

                            // Primärfarbe: Vibrant oder Dominant
                            val vibrantColor = palette.getVibrantColor(0)
                            val dominantColor = palette.getDominantColor(Color.parseColor("#6200EE"))
                            val primaryColor = if (vibrantColor != 0) vibrantColor else dominantColor

                            // Sekundärfarbe: Light Vibrant, Muted, oder aufgehellt
                            val lightVibrant = palette.getLightVibrantColor(0)
                            val mutedColor = palette.getMutedColor(0)
                            val secondaryColor = when {
                                lightVibrant != 0 -> lightVibrant
                                mutedColor != 0 -> mutedColor
                                else -> adjustBrightness(primaryColor, 1.3f)
                            }

                            Log.d("CoverPageAdapter", "Visualizer colors extracted from $imageUrl: primary=$primaryColor, secondary=$secondaryColor")

                            // Alle aktiven Visualizer aktualisieren (Thread-safe copy)
                            synchronized(activeVisualizerViews) {
                                activeVisualizerViews.toList()
                            }.forEach { view ->
                                view.setColors(primaryColor, secondaryColor)
                            }
                        }
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                })
        } catch (e: Exception) {
            Log.e("CoverPageAdapter", "Error extracting visualizer colors: ${e.message}")
        }
    }

    private fun adjustBrightness(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    fun getCoverUrlForPosition(position: Int): String? {
        return currentCoverUrls[position]
    }

    // StateHelper.VisualizerListener implementation
    override fun onFftDataAvailable(magnitudes: FloatArray) {
        // Thread-safe copy for iteration
        val views = synchronized(activeVisualizerViews) { activeVisualizerViews.toList() }
        if (views.isEmpty()) return

        // Ensure setMagnitudes is called on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            views.forEach { it.setMagnitudes(magnitudes) }
        } else {
            mainHandler.post {
                views.forEach { it.setMagnitudes(magnitudes) }
            }
        }
    }

    private fun registerVisualizerListener() {
        if (!isListenerRegistered && backgroundEffect == LiveCoverHelper.BackgroundEffect.VISUALIZER) {
            StateHelper.addVisualizerListener(this)
            isListenerRegistered = true
            Log.d("CoverPageAdapter", "Visualizer listener registered")
        }
    }

    private fun unregisterVisualizerListener() {
        if (isListenerRegistered) {
            StateHelper.removeVisualizerListener(this)
            isListenerRegistered = false
            Log.d("CoverPageAdapter", "Visualizer listener unregistered")
        }
    }

    inner class CoverViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val coverImage: ShapeableImageView = itemView.findViewById(R.id.cover_image)
        val visualizerView: VisualizerView = itemView.findViewById(R.id.visualizer_view)
        var lastColor: Int? = null
        var lastEffect: LiveCoverHelper.BackgroundEffect? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoverViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_player_coverpagview, parent, false)
        return CoverViewHolder(view)
    }

    override fun onBindViewHolder(holder: CoverViewHolder, position: Int) {
        // Bounds check for safety
        if (position < 0 || position >= mediaItems.size) return
        val item = mediaItems[position]
        // Verwende gespeicherte Cover-URL falls vorhanden, sonst Station-Icon
        val coverUrl = currentCoverUrls[position]?.takeIf { it.isNotBlank() }
            ?: item.iconURL.takeIf { it.isNotBlank() }

        // Handle Visualizer visibility
        if (backgroundEffect == LiveCoverHelper.BackgroundEffect.VISUALIZER) {
            Log.d("CoverPageAdapter", "onBindViewHolder VISUALIZER: position=$position, style=${StateHelper.visualizerStyle}")
            holder.visualizerView.visibility = View.VISIBLE
            holder.visualizerView.style = StateHelper.visualizerStyle
            holder.visualizerView.startFallbackAnimation() // Start with fallback until real data arrives
            activeVisualizerViews.add(holder.visualizerView)
            registerVisualizerListener()
        } else {
            holder.visualizerView.visibility = View.GONE
            holder.visualizerView.release()
            activeVisualizerViews.remove(holder.visualizerView)
            if (activeVisualizerViews.isEmpty()) {
                unregisterVisualizerListener()
            }
        }

        // Falls keine URL vorhanden, zeige Placeholder direkt
        if (coverUrl == null) {
            holder.coverImage.setImageResource(R.drawable.ic_placeholder_logo)
            holder.itemView.setBackgroundColor(holder.itemView.context.getColor(R.color.default_background))
            return
        }

        LiveCoverHelper.loadCoverWithBackground(
            context = holder.itemView.context,
            imageUrl = coverUrl,
            imageView = holder.coverImage,
            backgroundTarget = holder.itemView,
            defaultColor = holder.itemView.context.getColor(R.color.default_background),
            lastColor = holder.lastColor,
            lastEffect = holder.lastEffect,
            effect = backgroundEffect,
            onNewColor = { color ->
                holder.lastColor = color
                onColorChanged?.invoke(holder.bindingAdapterPosition, color)
            },
            onNewEffect = { holder.lastEffect = it },
            onVisualizerColors = { primary, secondary ->
                // Farben an alle aktiven VisualizerViews weitergeben
                holder.visualizerView.setColors(primary, secondary)
            }
        )
    }

    override fun onViewRecycled(holder: CoverViewHolder) {
        super.onViewRecycled(holder)
        // Release visualizer when view is recycled
        holder.visualizerView.release()
        activeVisualizerViews.remove(holder.visualizerView)
        if (activeVisualizerViews.isEmpty()) {
            unregisterVisualizerListener()
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        // Clean up all visualizer views and unregister listener
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(activeVisualizerViews) {
            activeVisualizerViews.forEach { it.release() }
            activeVisualizerViews.clear()
        }
        unregisterVisualizerListener()
    }

    override fun getItemCount(): Int = mediaItems.size
}
