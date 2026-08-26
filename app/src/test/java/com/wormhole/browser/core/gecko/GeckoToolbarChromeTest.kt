package com.wormhole.browser.core.gecko

import org.junit.Assert.assertEquals
import org.junit.Test

class GeckoToolbarChromeTest {

    @Test
    fun `shown toolbar reports zero clipping like Iceraven`() {
        assertEquals(0, GeckoToolbarChrome.clippingForBottomToolbar(0f, 240))
    }

    @Test
    fun `hidden toolbar reports negative max height`() {
        assertEquals(-240, GeckoToolbarChrome.clippingForBottomToolbar(240f, 240))
    }

    @Test
    fun `mid translation is negated`() {
        assertEquals(-80, GeckoToolbarChrome.clippingForBottomToolbar(80f, 240))
    }

    @Test
    fun `system nav stays reserved when toolbar is hidden`() {
        assertEquals(-180, GeckoToolbarChrome.clippingForBottomToolbar(240f, 240, minReservedPx = 60))
    }
}
