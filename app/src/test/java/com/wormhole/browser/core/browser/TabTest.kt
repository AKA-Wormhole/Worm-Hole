package com.wormhole.browser.core.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabTest {

    @Test
    fun `new tab defaults to blank state`() {
        val tab = Tab()
        assertTrue(tab.isBlankTab)
        assertEquals("", tab.url)
        assertEquals("New Tab", tab.title)
        assertEquals("", tab.displayUrl)
    }

    @Test
    fun `new tab defaults to not incognito`() {
        val tab = Tab()
        assertTrue(!tab.isIncognito)
    }

    @Test
    fun `two default tabs get distinct ids`() {

        val first = Tab()
        val second = Tab()
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `copy preserves id while changing other fields`() {
        val original = Tab(title = "Example", url = "https://example.com")
        val updated = original.copy(title = "Example Updated")
        assertEquals(original.id, updated.id)
        assertEquals("Example Updated", updated.title)
        assertEquals(original.url, updated.url)
    }

    @Test
    fun `incognito tab can be constructed explicitly`() {
        val tab = Tab(isIncognito = true)
        assertTrue(tab.isIncognito)
    }

    @Test
    fun `default space id is used when not specified`() {
        val tab = Tab()
        assertEquals(Space.DEFAULT_SPACE_ID, tab.spaceId)
    }
}
