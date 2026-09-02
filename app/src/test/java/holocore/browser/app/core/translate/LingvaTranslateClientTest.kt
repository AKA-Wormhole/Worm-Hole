package holocore.browser.app.core.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LingvaTranslateClientTest {

    @Test
    fun parseSimpleTranslation() {
        val text = LingvaTranslateClient.parseTranslation(
            """{"translation":"hello"}""",
        )
        assertEquals("hello", text)
    }

    @Test
    fun rejectErrorPayload() {
        assertNull(LingvaTranslateClient.parseTranslation("""{"error":"Not found"}"""))
    }
}
