package com.wormhole.browser.core.gecko

import kotlin.math.abs
import kotlin.math.roundToInt
import org.mozilla.geckoview.GeckoView

/**
 * Port of Iceraven / Firefox [EngineViewClippingBehavior] + [BaseBrowserFragment.initializeEngineView].
 *
 * Gecko's ICB is sized with [GeckoView.setDynamicToolbarMaxHeight]. After bug 1586144 the
 * current toolbar offset is reported as a **negative** [GeckoView.setVerticalClipping]:
 *
 * - toolbar shown (`translationY == 0`) → clipping `0` → fixed `bottom:0` sits on the bar
 * - toolbar hiding → clipping `-translationY`
 * - toolbar hidden (`translationY == maxHeight`) → clipping `-maxHeight`
 *
 * [GeckoView.setDynamicToolbarMaxHeight] resets clipping to 0, so it is only called when
 * the max height actually changes.
 */
class GeckoToolbarChromeState {
    var lastMaxHeight: Int = Int.MIN_VALUE
    var lastClipping: Int = Int.MIN_VALUE
}

object GeckoToolbarChrome {
    fun clippingForBottomToolbar(
        translationY: Float,
        maxHeightPx: Int,
        minReservedPx: Int = 0,
    ): Int {
        if (maxHeightPx <= 0) return 0
        val boundedTranslation = translationY.coerceIn(0f, maxHeightPx.toFloat())
        var clipping = -boundedTranslation.roundToInt()
        val minClipping = (minReservedPx.coerceAtLeast(0) - maxHeightPx).coerceAtMost(0)
        clipping = clipping.coerceAtLeast(minClipping)
        // Bug 2005988: treat "almost fully hidden" as fully hidden, unless we must
        // keep the system navigation bar reserved.
        if (minReservedPx <= 0 && abs(maxHeightPx + clipping) in 0..2) {
            clipping = -maxHeightPx
        }
        return clipping
    }

    fun apply(
        view: GeckoView,
        state: GeckoToolbarChromeState,
        maxHeightPx: Int,
        translationY: Float,
        minReservedPx: Int = 0,
    ) {
        val laidOutHeight = view.height
        // Gecko sizes the page from this height. If the reserved toolbar is
        // larger than the surface, ICB becomes 0 and the page never paints.
        if (laidOutHeight < 200) return
        val maxSafe = (laidOutHeight * 0.28f).toInt().coerceAtLeast(0)
        val maxHeight = maxHeightPx.coerceIn(0, maxSafe)
        if (laidOutHeight - maxHeight < 160) {
            if (state.lastMaxHeight != 0) {
                view.setDynamicToolbarMaxHeight(0)
                view.setVerticalClipping(0)
                state.lastMaxHeight = 0
                state.lastClipping = 0
            }
            return
        }
        val clipping = clippingForBottomToolbar(translationY, maxHeight, minReservedPx.coerceAtMost(maxHeight))
        if (state.lastMaxHeight != maxHeight) {
            view.setDynamicToolbarMaxHeight(maxHeight)
            state.lastMaxHeight = maxHeight
            state.lastClipping = 0
        }
        if (state.lastClipping != clipping) {
            view.setVerticalClipping(clipping)
            state.lastClipping = clipping
        }
    }
}
