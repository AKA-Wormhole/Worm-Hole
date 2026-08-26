package com.wormhole.browser.core.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationUrlsTest {

    @Test
    fun `www and apex hosts are equivalent`() {
        assertTrue(
            NavigationUrls.areEquivalent(
                "https://www.amazon.in",
                "https://amazon.in",
            ),
        )
        assertTrue(
            NavigationUrls.areEquivalent(
                "https://www.amazon.in/",
                "https://amazon.in",
            ),
        )
    }

    @Test
    fun `http and https on the same host are equivalent for reload suppression`() {
        assertTrue(
            NavigationUrls.areEquivalent(
                "http://example.com/path",
                "https://example.com/path",
            ),
        )
    }

    @Test
    fun `different paths are not equivalent`() {
        assertFalse(
            NavigationUrls.areEquivalent(
                "https://amazon.in/gp/cart",
                "https://www.amazon.in/",
            ),
        )
    }

    @Test
    fun `automatic load is skipped after www canonicalization`() {
        assertTrue(
            NavigationUrls.shouldSkipAutomaticLoad(
                requested = "https://www.amazon.in/",
                lastRequested = "https://amazon.in",
                lastCommitted = "https://www.amazon.in/",
            ),
        )
    }

    @Test
    fun `first load is not skipped`() {
        assertFalse(
            NavigationUrls.shouldSkipAutomaticLoad(
                requested = "https://amazon.in",
                lastRequested = "",
                lastCommitted = "",
            ),
        )
    }

    @Test
    fun `in-flight load of the same url is not restarted`() {
        assertTrue(
            NavigationUrls.shouldSkipAutomaticLoad(
                requested = "https://amazon.in",
                lastRequested = "https://amazon.in",
                lastCommitted = "",
            ),
        )
    }
}
