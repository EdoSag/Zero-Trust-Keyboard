// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.sync

import dev.sagron.zerotrustkeyboard.sync.LearnedWords.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedWordsTest {
    @Test fun `only words the keyboard already offers are sent`() {
        val picked = LearnedWords.pick(
            listOf(
                Entry("zerotrust", 3, 100, false),
                Entry("typo", 0, 100, false),
                Entry("once", 1, 100, false),
                Entry("שלום", 2, 100, false),
            )
        )
        assertEquals(listOf("zerotrust", "שלום"), picked)
    }

    @Test fun `entries that are not words stay`() {
        val picked = LearnedWords.pick(
            listOf(
                Entry("ok", 5, 100, false),
                Entry("", 5, 100, false),
                Entry("marker", 5, 100, true),
                Entry("two words", 5, 100, false),
                Entry("x".repeat(49), 5, 100, false),
            )
        )
        assertEquals(listOf("ok"), picked)
    }

    @Test fun `over the limit, the most used and then the most recent are kept`() {
        val picked = LearnedWords.pick(
            listOf(
                Entry("old", 2, 10, false),
                Entry("recent", 2, 50, false),
                Entry("favourite", 9, 1, false),
            ),
            max = 2,
        )
        assertEquals(listOf("favourite", "recent"), picked)
    }

    @Test fun `a word that arrives is checked the same way`() {
        assertTrue(LearnedWords.valid("שלום"))
        assertTrue(LearnedWords.valid("don't"))
        assertFalse(LearnedWords.valid(""))
        assertFalse(LearnedWords.valid("a b"))
        assertFalse(LearnedWords.valid("a\nb"))
        assertFalse(LearnedWords.valid("x".repeat(49)))
    }
}
