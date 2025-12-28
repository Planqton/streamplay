package at.plankt0n.streamplay.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import at.plankt0n.streamplay.Keys
import at.plankt0n.streamplay.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import java.io.File
import java.io.FileOutputStream

/**
 * Helper to add overlay icons to bitmaps (e.g., "Spotify unavailable" indicator)
 */
object BitmapOverlayHelper {

    enum class OverlayPosition { LEFT, RIGHT }

    /**
     * Adds the "Spotify unavailable" overlay icon to a corner of a bitmap.
     * @param context Context for loading drawable resources and settings
     * @param source The original bitmap to overlay
     * @param overlayScale Size of overlay relative to bitmap (0.0 - 1.0), default 0.25 = 25%
     * @param margin Margin from edge in pixels, default 8
     * @param position Position of overlay (LEFT or RIGHT bottom corner)
     * @param opacity Opacity of overlay (0-100), default reads from settings
     * @return New bitmap with overlay applied
     */
    fun addSpotifyUnavailableOverlay(
        context: Context,
        source: Bitmap,
        overlayScale: Float = 0.25f,
        margin: Int = 8,
        position: OverlayPosition? = null,
        opacity: Int? = null
    ): Bitmap {
        // Read settings if not provided
        val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        val actualPosition = position ?: try {
            OverlayPosition.valueOf(prefs.getString(Keys.PREF_OVERLAY_POSITION, "LEFT") ?: "LEFT")
        } catch (e: Exception) {
            OverlayPosition.LEFT
        }
        val actualOpacity = opacity ?: prefs.getInt(Keys.PREF_OVERLAY_OPACITY, 40)

        android.util.Log.d("BitmapOverlayHelper", "🎨 addSpotifyUnavailableOverlay: size=${source.width}x${source.height}, position=$actualPosition, opacity=$actualOpacity")

        // Create mutable copy if needed
        val result = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val overlayDrawable = ContextCompat.getDrawable(context, R.drawable.ic_spotify_unavailable)?.mutate()
        if (overlayDrawable == null) {
            android.util.Log.e("BitmapOverlayHelper", "❌ Failed to load ic_spotify_unavailable drawable!")
            return result
        }

        // Apply opacity (0-100 -> 0-255)
        overlayDrawable.alpha = (actualOpacity * 255 / 100)

        // Calculate overlay size (percentage of smaller dimension)
        val overlaySize = (minOf(result.width, result.height) * overlayScale).toInt()

        // Position based on setting
        val left = when (actualPosition) {
            OverlayPosition.LEFT -> margin
            OverlayPosition.RIGHT -> result.width - overlaySize - margin
        }
        val top = result.height - overlaySize - margin

        android.util.Log.d("BitmapOverlayHelper", "✅ Drawing overlay: size=$overlaySize, position=($left, $top)")
        overlayDrawable.setBounds(left, top, left + overlaySize, top + overlaySize)
        overlayDrawable.draw(canvas)

        return result
    }

    /**
     * Saves a bitmap to the app's cache directory and returns a content:// URI via FileProvider.
     * Uses FileProvider to ensure Android Auto and other external apps can access the file.
     * @param context Context for cache directory access
     * @param bitmap The bitmap to save
     * @param filename Name for the cached file
     * @return Content URI string pointing to the cached bitmap
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, filename: String = "overlay_cover.png"): String {
        val cacheDir = File(context.cacheDir, "cover_overlays")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val file = File(cacheDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }

        // Use FileProvider to get a content:// URI that Android Auto can access
        return try {
            val authority = "${context.packageName}.provider"
            FileProvider.getUriForFile(context, authority, file).toString()
        } catch (e: Exception) {
            android.util.Log.e("BitmapOverlayHelper", "FileProvider error, falling back to file URI: ${e.message}")
            file.toURI().toString()
        }
    }

    /**
     * Clears old cached overlay images to prevent cache bloat.
     */
    fun clearOverlayCache(context: Context) {
        val cacheDir = File(context.cacheDir, "cover_overlays")
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * Result class containing both URI and bitmap bytes for MediaSession
     */
    data class OverlayResult(
        val uri: String,
        val bitmapBytes: ByteArray?
    )

    /**
     * Loads an image from URL, applies Spotify unavailable overlay, and returns
     * both a URI and bitmap bytes via callback.
     * The bitmap bytes can be used with setArtworkData() for Android Auto compatibility.
     * Respects the PREF_OVERLAY_ENABLED setting.
     *
     * @param context Context for Glide and file operations
     * @param imageUrl URL of the image to load
     * @param onComplete Callback with OverlayResult containing URI and bitmap bytes
     */
    fun loadAndOverlayForMediaSession(
        context: Context,
        imageUrl: String,
        onComplete: (OverlayResult) -> Unit
    ) {
        android.util.Log.d("BitmapOverlayHelper", "📱 loadAndOverlayForMediaSession called with: $imageUrl")

        // Check if overlay is enabled
        val prefs = context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
        val overlayEnabled = prefs.getBoolean(Keys.PREF_OVERLAY_ENABLED, true)
        if (!overlayEnabled) {
            android.util.Log.d("BitmapOverlayHelper", "⏭️ Overlay disabled in settings, skipping")
            onComplete(OverlayResult(imageUrl, null))
            return
        }

        if (imageUrl.isBlank()) {
            android.util.Log.w("BitmapOverlayHelper", "⚠️ imageUrl is blank, skipping overlay")
            onComplete(OverlayResult(imageUrl, null))
            return
        }

        try {
            Glide.with(context.applicationContext)
                .asBitmap()
                .load(imageUrl)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                        try {
                            android.util.Log.d("BitmapOverlayHelper", "📱 MediaSession: Creating overlay for $imageUrl")
                            val overlaidBitmap = addSpotifyUnavailableOverlay(context, bitmap)

                            // Convert bitmap to byte array for setArtworkData()
                            val stream = java.io.ByteArrayOutputStream()
                            overlaidBitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
                            val bitmapBytes = stream.toByteArray()

                            // Also save to cache for UI usage
                            val cachedUri = saveBitmapToCache(context, overlaidBitmap, "mediasession_overlay_${System.currentTimeMillis()}.png")
                            android.util.Log.d("BitmapOverlayHelper", "✅ MediaSession overlay saved: $cachedUri (${bitmapBytes.size} bytes)")

                            onComplete(OverlayResult(cachedUri, bitmapBytes))
                        } catch (e: Exception) {
                            android.util.Log.e("BitmapOverlayHelper", "Error creating overlay: ${e.message}")
                            onComplete(OverlayResult(imageUrl, null))
                        }
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        android.util.Log.e("BitmapOverlayHelper", "Failed to load image for overlay: $imageUrl")
                        onComplete(OverlayResult(imageUrl, null))
                    }
                })
        } catch (e: Exception) {
            android.util.Log.e("BitmapOverlayHelper", "Error loading image: ${e.message}")
            onComplete(OverlayResult(imageUrl, null))
        }
    }
}
