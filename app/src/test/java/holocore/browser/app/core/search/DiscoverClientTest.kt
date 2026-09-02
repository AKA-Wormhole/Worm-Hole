package holocore.browser.app.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverClientTest {
    @Test
    fun categorizePicksScienceAndTech() {
        assertEquals("Science", DiscoverClient.categorize("A new black hole discovery", "News"))
        assertEquals("Tech", DiscoverClient.categorize("On-device AI chips get faster", "News"))
        assertEquals("Travel", DiscoverClient.categorize("Hidden island you must visit", "News"))
    }

    @Test
    fun unsafeTitlesAreFiltered() {
        assertTrue(DiscoverClient.looksUnsafe("Explicit video leaks"))
        assertFalse(DiscoverClient.looksUnsafe("A new black hole discovery"))
    }
}
