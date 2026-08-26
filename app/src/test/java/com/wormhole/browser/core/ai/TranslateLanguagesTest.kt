package com.wormhole.browser.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateLanguagesTest {

    @Test
    fun `list is not empty`() {
        assertTrue(TranslateLanguages.ALL.isNotEmpty())
    }

    @Test
    fun `every language code is unique`() {
        val codes = TranslateLanguages.ALL.map { it.code }
        assertEquals(
            "Duplicate language codes would let two picker rows resolve to the same target -- codes must be unique.",
            codes.size,
            codes.distinct().size,
        )
    }

    @Test
    fun `every language code is non-blank and lowercase`() {
        TranslateLanguages.ALL.forEach { language ->
            assertTrue("Code for '${language.displayName}' should not be blank", language.code.isNotBlank())
            assertEquals(
                "Code for '${language.displayName}' should be lowercase (ISO-639 convention)",
                language.code.lowercase(),
                language.code,
            )
        }
    }

    @Test
    fun `every display name is non-blank`() {
        TranslateLanguages.ALL.forEach { language ->
            assertTrue("Display name for code '${language.code}' should not be blank", language.displayName.isNotBlank())
        }
    }

    @Test
    fun `english is offered as a target language`() {
        assertTrue(TranslateLanguages.ALL.any { it.code == "en" })
    }

    @Test
    fun `unknown code is not present`() {
        assertFalse(TranslateLanguages.ALL.any { it.code == "xx-not-a-real-code" })
    }
}
