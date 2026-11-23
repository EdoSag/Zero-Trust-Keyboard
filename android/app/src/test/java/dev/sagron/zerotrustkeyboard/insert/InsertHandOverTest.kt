// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.insert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InsertHandOverTest {
    @Before fun reset() = InsertHandOver.cancel()

    @Test fun `a picked text is typed once, in the field that asked`() {
        InsertHandOver.ask("chat", 7, 1_000)
        // The keyboard may open again before the other app has answered.
        assertNull(InsertHandOver.take("chat", 7, 2_000))
        assertTrue(InsertHandOver.answer("054-1234567", 5_000))
        assertEquals("054-1234567", InsertHandOver.take("chat", 7, 6_000))
        assertNull(InsertHandOver.take("chat", 7, 7_000))
    }

    @Test fun `a text never lands in another field or app`() {
        InsertHandOver.ask("chat", 7, 0)
        InsertHandOver.answer("secret note", 10)
        assertNull(InsertHandOver.take("chat", 8, 20))
        // It is gone, not kept for later.
        assertNull(InsertHandOver.take("chat", 7, 30))

        InsertHandOver.ask("chat", 7, 0)
        InsertHandOver.answer("secret note", 10)
        assertNull(InsertHandOver.take("browser", 7, 20))
        assertNull(InsertHandOver.take("chat", 7, 30))
    }

    @Test fun `nothing is typed without a request, after a cancel, or too late`() {
        assertFalse(InsertHandOver.answer("x", 0))
        assertNull(InsertHandOver.take("chat", 7, 0))

        InsertHandOver.ask("chat", 7, 0)
        InsertHandOver.cancel()
        assertFalse(InsertHandOver.answer("x", 10))

        InsertHandOver.ask("chat", 7, 0)
        assertFalse(InsertHandOver.answer("x", InsertHandOver.WAIT_MS + 1))

        InsertHandOver.ask("chat", 7, 0)
        assertTrue(InsertHandOver.answer("x", 10))
        assertNull(InsertHandOver.take("chat", 7, 10 + InsertHandOver.WAIT_MS + 1))

        InsertHandOver.ask(null, 7, 0)
        assertFalse(InsertHandOver.answer("x", 10))
        InsertHandOver.ask("chat", 7, 0)
        assertFalse(InsertHandOver.answer("", 10))
    }

    @Test fun `a new request replaces the old one and long texts are cut`() {
        InsertHandOver.ask("chat", 7, 0)
        InsertHandOver.ask("mail", 3, 5)
        InsertHandOver.answer("a".repeat(InsertHandOver.MAX_CHARS + 50), 10)
        assertNull(InsertHandOver.take("chat", 7, 20))

        InsertHandOver.ask("mail", 3, 5)
        InsertHandOver.answer("a".repeat(InsertHandOver.MAX_CHARS + 50), 10)
        assertEquals(InsertHandOver.MAX_CHARS, InsertHandOver.take("mail", 3, 20)!!.length)
    }

    @Test fun `a one-time code is not typed once it stopped working`() {
        InsertHandOver.ask("bank", 3, 0)
        assertTrue(InsertHandOver.answer("482913", 1_000, validUntil = 50_000))
        assertNull(InsertHandOver.take("bank", 3, 2_000, wallNow = 50_000))

        InsertHandOver.ask("bank", 3, 0)
        assertTrue(InsertHandOver.answer("482913", 1_000, validUntil = 50_000))
        assertEquals("482913", InsertHandOver.take("bank", 3, 2_000, wallNow = 49_000))
    }
}
