package at.plankt0n.streamplay.helper

import android.content.res.Configuration
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment

object LayoutLogger {
    private const val TAG = "LayoutLogger"
    private var lastLoggedLayout: String? = null

    fun logLayoutInfo(fragment: Fragment, layoutResId: Int) {
        val context = fragment.context ?: return
        val config = context.resources.configuration

        val layoutInfo = buildLayoutInfo(config, layoutResId, fragment.javaClass.simpleName)

        // Only log if layout changed
        if (layoutInfo != lastLoggedLayout) {
            lastLoggedLayout = layoutInfo
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "📐 LAYOUT CHANGED")
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "Fragment: ${fragment.javaClass.simpleName}")
            Log.i(TAG, "Layout Resource: ${getResourceName(context, layoutResId)}")
            Log.i(TAG, "Orientation: ${getOrientationString(config.orientation)}")
            Log.i(TAG, "Screen Width: ${config.screenWidthDp}dp")
            Log.i(TAG, "Screen Height: ${config.screenHeightDp}dp")
            Log.i(TAG, "Smallest Width: ${config.smallestScreenWidthDp}dp")
            Log.i(TAG, "Layout Qualifier: ${getLayoutQualifier(config)}")
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }

    fun logLayoutInfoFromView(view: View, fragmentName: String) {
        val context = view.context
        val config = context.resources.configuration

        val layoutInfo = buildLayoutInfo(config, 0, fragmentName)

        if (layoutInfo != lastLoggedLayout) {
            lastLoggedLayout = layoutInfo
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "📐 LAYOUT CHANGED")
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "Fragment: $fragmentName")
            Log.i(TAG, "Orientation: ${getOrientationString(config.orientation)}")
            Log.i(TAG, "Screen Width: ${config.screenWidthDp}dp")
            Log.i(TAG, "Screen Height: ${config.screenHeightDp}dp")
            Log.i(TAG, "Smallest Width: ${config.smallestScreenWidthDp}dp")
            Log.i(TAG, "Layout Qualifier: ${getLayoutQualifier(config)}")
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }

    private fun buildLayoutInfo(config: Configuration, layoutResId: Int, fragmentName: String): String {
        return "$fragmentName|${config.orientation}|${config.screenWidthDp}|${config.smallestScreenWidthDp}|$layoutResId"
    }

    private fun getResourceName(context: android.content.Context, resId: Int): String {
        return try {
            context.resources.getResourceEntryName(resId)
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getOrientationString(orientation: Int): String {
        return when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "LANDSCAPE"
            Configuration.ORIENTATION_PORTRAIT -> "PORTRAIT"
            else -> "UNDEFINED"
        }
    }

    private fun getLayoutQualifier(config: Configuration): String {
        val qualifiers = mutableListOf<String>()

        // Orientation
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            qualifiers.add("land")
        }

        // Smallest width qualifiers (check from largest to smallest)
        val sw = config.smallestScreenWidthDp
        when {
            sw >= 800 -> qualifiers.add("sw800dp")
            sw >= 600 -> qualifiers.add("sw600dp")
            sw >= 384 -> qualifiers.add("sw384dp")
        }

        return if (qualifiers.isEmpty()) {
            "layout/ (default)"
        } else {
            "layout-${qualifiers.joinToString("-")}/"
        }
    }
}
