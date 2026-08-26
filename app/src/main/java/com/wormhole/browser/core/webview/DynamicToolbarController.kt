package com.wormhole.browser.core.webview

import kotlin.math.abs

/**
 * Safari-style bottom chrome. translationY: 0 = shown, [toolbarHeight] = hidden.
 *
 * Overscroll bounce is ignored after a swipe has committed, so revealing the
 * bar cannot immediately snap it shut again.
 */
class DynamicToolbarController(
    private val snapThresholdFraction: Float = 0.42f,
    private val minScrollDelta: Int = 2,
    private val noiseDelta: Int = 12,
    private val commitTravel: Float = 32f,
) {
    var translationY: Float = 0f
        private set

    var toolbarHeight: Float = 0f
        private set

    var gestureTravel: Float = 0f
        private set

    var lastDelta: Int = 0
        private set

    /** True when the last [onScrollDelta] was bounce/noise and should not move chrome. */
    var lastIgnored: Boolean = false
        private set

    fun updateToolbarHeight(heightPx: Int) {
        toolbarHeight = heightPx.toFloat().coerceAtLeast(0f)
        translationY = translationY.coerceIn(0f, toolbarHeight)
    }

    fun syncTranslation(y: Float) {
        translationY = y.coerceIn(0f, toolbarHeight.coerceAtLeast(0f))
    }

    /** Positive [scrollDeltaY] = page scrolled down → hide bar. */
    fun onScrollDelta(scrollDeltaY: Int, scrollY: Int = Int.MAX_VALUE): Float {
        lastIgnored = false
        if (toolbarHeight <= 0f) return translationY

        if (scrollY <= 16) {
            gestureTravel = minOf(gestureTravel, 0f)
            lastDelta = -1
            translationY = 0f
            return translationY
        }

        if (abs(scrollDeltaY) < minScrollDelta) {
            lastIgnored = true
            return translationY
        }

        val opposingNoise = gestureTravel != 0f &&
            scrollDeltaY.toFloat() * gestureTravel < 0f &&
            abs(scrollDeltaY) < noiseDelta &&
            abs(gestureTravel) >= commitTravel
        if (opposingNoise) {
            lastIgnored = true
            return translationY
        }

        lastDelta = scrollDeltaY
        gestureTravel += scrollDeltaY
        translationY = (translationY + scrollDeltaY).coerceIn(0f, toolbarHeight)
        return translationY
    }

    fun snapTarget(scrollY: Int = Int.MAX_VALUE): Float {
        if (toolbarHeight <= 0f) return 0f
        if (scrollY <= 28) return 0f

        if (gestureTravel <= -commitTravel) return 0f
        if (gestureTravel >= commitTravel) return toolbarHeight

        if (lastDelta < 0 && translationY < toolbarHeight * 0.82f) return 0f
        if (lastDelta > 0 && translationY > toolbarHeight * 0.18f) return toolbarHeight

        val threshold = toolbarHeight * snapThresholdFraction
        return if (translationY >= threshold) toolbarHeight else 0f
    }

    fun onScrollEnd(): Float {
        translationY = snapTarget()
        gestureTravel = 0f
        return translationY
    }

    fun endGesture() {
        gestureTravel = 0f
    }

    fun forceExpand(): Float {
        translationY = 0f
        gestureTravel = 0f
        lastDelta = -1
        return translationY
    }

    fun forceCollapse(): Float {
        translationY = toolbarHeight
        gestureTravel = 0f
        lastDelta = 1
        return translationY
    }

    fun visibleHeight(): Float = (toolbarHeight - translationY).coerceAtLeast(0f)
}
