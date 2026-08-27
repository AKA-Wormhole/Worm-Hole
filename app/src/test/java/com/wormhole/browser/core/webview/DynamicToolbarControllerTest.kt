package com.wormhole.browser.core.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicToolbarControllerTest {

    @Test
    fun hideAndShowFollowScrollThenSnap() {
        val bar = DynamicToolbarController()
        bar.updateToolbarHeight(200)

        bar.onScrollDelta(80, scrollY = 400)
        assertTrue(bar.translationY > 0f)

        bar.onScrollDelta(-120, scrollY = 280)
        val shown = bar.snapTarget(scrollY = 280)
        assertEquals(0f, shown, 0.01f)
    }

    @Test
    fun topOfPageForcesBarVisible() {
        val bar = DynamicToolbarController()
        bar.updateToolbarHeight(180)
        bar.onScrollDelta(180, scrollY = 400)
        val atTop = bar.onScrollDelta(10, scrollY = 4)
        assertEquals(0f, atTop, 0.01f)
    }
}
