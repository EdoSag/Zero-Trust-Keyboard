// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslateTextTest {
    @Test fun `the letters say what a text is written in`() {
        assertEquals("hebrew", TranslateText.script("שלום, מה שלומך?"))
        assertEquals("latin", TranslateText.script("Hello, how are you?"))
        assertEquals("cyrillic", TranslateText.script("Привет"))
        assertEquals("hebrew", TranslateText.script("נפגשים ב WhatsApp או בטלפון, מה שנוח לך"))
        assertNull(TranslateText.script("123 !?"))
        assertNull(TranslateText.script("a"))
        assertNull(TranslateText.script("אב ab"))
    }

    @Test fun `Hebrew and English are told apart by the text, whatever the keyboard is in`() {
        assertEquals("he" to "en", TranslateText.direction("מה נשמע", "he", "en", "en"))
        assertEquals("en" to "he", TranslateText.direction("what's up", "he", "en", "he"))
        // Nothing to go by: the keyboard's language is the one being written.
        assertEquals("he" to "en", TranslateText.direction("123", "he", "en", "he"))
        assertEquals("en" to "he", TranslateText.direction("123", "he", "en", "en"))
    }

    @Test fun `two languages with the same letters follow the keyboard`() {
        assertEquals("fr" to "en", TranslateText.direction("bonjour", "fr", "en", "fr"))
        assertEquals("en" to "fr", TranslateText.direction("hello", "fr", "en", "en"))
        // On a third keyboard the text is taken to be in the user's own language.
        assertEquals("fr" to "en", TranslateText.direction("hello", "fr", "en", "de"))
    }

    @Test fun `lines and the spaces around them survive`() {
        val text = "  שלום  \n\nמה נשמע?\n"
        val pieces = TranslateText.pieces(text)
        assertEquals(listOf("שלום", "", "מה נשמע?", ""), pieces.map { it.text })
        assertEquals("  Hello  \n\nWhat's up?\n", TranslateText.join(pieces, listOf("Hello", " What's up? ")))
        // Unchanged pieces give the text back as it was.
        assertEquals(text, TranslateText.join(pieces, pieces.map { it.text }.filter { it.isNotEmpty() }))
        assertEquals("one line", TranslateText.join(TranslateText.pieces("one line"), listOf("one line")))
        assertEquals("", TranslateText.join(TranslateText.pieces(""), emptyList()))
        // A missing translation leaves the original in place rather than dropping it.
        assertEquals("a\nb", TranslateText.join(TranslateText.pieces("a\nb"), listOf("a")))
    }
}
