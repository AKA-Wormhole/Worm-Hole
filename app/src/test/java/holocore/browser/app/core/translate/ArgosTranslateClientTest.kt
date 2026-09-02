package holocore.browser.app.core.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArgosTranslateClientTest {

    @Test
    fun parseSingleTranslatedText() {
        val text = ArgosTranslateClient.parseTranslatedText(
            """{"translatedText":"Hola mundo"}""",
        )
        assertEquals("Hola mundo", text)
    }

    @Test
    fun parseTranslatedArray() {
        val list = ArgosTranslateClient.parseTranslatedList(
            """{"translatedText":["Hola","Mundo"]}""",
            expected = 2,
        )
        assertEquals(listOf("Hola", "Mundo"), list)
    }

    @Test
    fun rejectWrongArrayLength() {
        val list = ArgosTranslateClient.parseTranslatedList(
            """{"translatedText":["Hola"]}""",
            expected = 2,
        )
        assertNull(list)
    }
}
