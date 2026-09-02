package holocore.browser.app.ui.theme

import android.app.Activity
import android.os.Build
import android.view.Display
import android.view.View
import android.view.Window
import android.view.WindowManager
import kotlin.math.abs

/**
 * Locks the window and key views to the panel's native high refresh rate
 * (90 / 120 / 144 / 165 Hz, whatever the display actually advertises).
 */
object HighRefreshRate {
    const val TARGET_HZ = 165f

    fun apply(activity: Activity) {
        apply(activity.window)
        applyToView(activity.window.decorView)
    }

    fun apply(window: Window) {
        val display = currentDisplay(window) ?: return
        val choice = pickMode(display) ?: return
        val rate = choice.refreshRate

        val attrs = window.attributes
        attrs.preferredDisplayModeId = choice.modeId
        attrs.preferredRefreshRate = rate
        window.attributes = attrs
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

        applyToView(window.decorView, rate)
    }

    fun applyToView(view: View, hz: Float? = null) {
        val display = view.display ?: view.context.display
        val rate = (hz ?: display?.let { pickMode(it)?.refreshRate } ?: 60f).coerceAtLeast(60f)
        runCatching {
            val floatT = java.lang.Float.TYPE
            val intT = java.lang.Integer.TYPE
            if (Build.VERSION.SDK_INT >= 31) {
                view.javaClass.getMethod("setFrameRate", floatT, intT, intT)
                    .invoke(view, rate, /* FIXED_SOURCE */ 0, /* ALWAYS */ 1)
            } else if (Build.VERSION.SDK_INT >= 30) {
                view.javaClass.getMethod("setFrameRate", floatT, intT)
                    .invoke(view, rate, /* FIXED_SOURCE */ 0)
            }
        }
    }

    fun pickMode(display: Display): Display.Mode? {
        val modes = display.supportedModes
        if (modes.isEmpty()) return null
        val current = if (Build.VERSION.SDK_INT >= 23) display.mode else modes.first()
        val sameSize = modes.filter { mode ->
            abs(mode.physicalWidth - current.physicalWidth) <= 8 &&
                abs(mode.physicalHeight - current.physicalHeight) <= 8
        }.ifEmpty { modes.toList() }
        return sameSize.maxByOrNull { it.refreshRate }
    }

    private fun currentDisplay(window: Window): Display? {
        @Suppress("DEPRECATION")
        return window.windowManager.defaultDisplay
    }
}
