package com.wormhole.browser.core.webview.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentBlockerHostMatchTest {

    private val blocker = ContentBlocker()
    private val hosts = setOf("doubleclick.net", "ads.example.com")

    @Test
    fun `exact host match is blocked`() {
        with(blocker) {
            assertTrue("doubleclick.net".matchesAny(hosts))
        }
    }

    @Test
    fun `subdomain of a blocked host is blocked`() {
        with(blocker) {
            assertTrue("stats.g.doubleclick.net".matchesAny(hosts))
        }
    }

    @Test
    fun `unrelated host is not blocked`() {
        with(blocker) {
            assertFalse("example.com".matchesAny(hosts))
        }
    }

    @Test
    fun `host that merely contains a blocked suffix as a substring is not blocked`() {

        with(blocker) {
            assertFalse("notdoubleclick.net".matchesAny(hosts))
        }
    }

    @Test
    fun `blocked host is matched exactly and unrelated hosts are not`() {
        with(blocker) {
            assertTrue("ads.example.com".matchesAny(hosts))
            assertFalse("evil.com".matchesAny(hosts))
        }
    }

    @Test
    fun `empty host set never matches`() {
        with(blocker) {
            assertFalse("doubleclick.net".matchesAny(emptySet()))
        }
    }
}
