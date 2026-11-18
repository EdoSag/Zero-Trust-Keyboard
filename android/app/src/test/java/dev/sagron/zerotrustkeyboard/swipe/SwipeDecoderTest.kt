// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.swipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

class SwipeDecoderTest {
    private val keyWidth = 108f
    private val keyHeight = 150f

    /** A keyboard of rows of letters; each row may start some key widths in. */
    private fun layout(vararg rows: Pair<String, Float>): SwipeLayout {
        val keys = ArrayList<SwipeKey>()
        rows.forEachIndexed { row, (letters, offset) ->
            letters.forEachIndexed { i, c ->
                if (c.isLetter()) keys.add(SwipeKey(c, (offset + i + 0.5f) * keyWidth, (row + 0.5f) * keyHeight))
            }
        }
        return SwipeLayout(keys, keyWidth)
    }

    private val qwerty = layout("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)
    private val hebrew = layout("'-קראטוןםפ" to 0f, "שדגכעיחלךף" to 0f, "זסבהנמצתץ" to 0f)

    private fun lexicon(locale: Locale, vararg words: Pair<String, Int>): SwipeLexicon {
        val builder = SwipeLexicon.Builder(locale)
        for ((word, frequency) in words) builder.add(word, frequency)
        return builder.build()
    }

    // Frequencies as in the AOSP English word list.
    private val english = lexicon(
        Locale.US,
        "the" to 222, "is" to 200, "he" to 187, "they" to 174, "would" to 171, "there" to 169,
        "it's" to 162, "its" to 158, "help" to 149, "word" to 145, "world" to 145, "put" to 138,
        "Paris" to 130, "hello" to 120, "keyboard" to 118, "pit" to 117, "pot" to 110,
        "hell" to 108, "café" to 104, "cafe" to 103, "I" to 210, "a" to 215,
    )

    /** A finger moving in straight lines through the letters of [word], moved off centre by [dx], [dy]. */
    private fun swipe(word: String, layout: SwipeLayout, dx: Float = 0f, dy: Float = 0f): Pair<IntArray, IntArray> {
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        val keys = word.map { layout.keyOf(it) }.filter { it >= 0 }
        for (i in 0 until keys.size - 1) {
            val x0 = layout.xs[keys[i]] + dx
            val y0 = layout.ys[keys[i]] + dy
            val x1 = layout.xs[keys[i + 1]] + dx
            val y1 = layout.ys[keys[i + 1]] + dy
            val steps = (hypot(x1 - x0, y1 - y0) / 15f).toInt().coerceAtLeast(1)
            for (s in 0 until steps) {
                xs.add((x0 + (x1 - x0) * s / steps).toInt())
                ys.add((y0 + (y1 - y0) * s / steps).toInt())
            }
        }
        xs.add((layout.xs[keys.last()] + dx).toInt())
        ys.add((layout.ys[keys.last()] + dy).toInt())
        return xs.toIntArray() to ys.toIntArray()
    }

    private fun decode(word: String, layout: SwipeLayout, vararg lexicons: SwipeLexicon, dx: Float = 0f, dy: Float = 0f): List<String> {
        val (xs, ys) = swipe(word, layout, dx, dy)
        return SwipeDecoder().decode(xs, ys, xs.size, layout, lexicons.toList()).map { it.word }
    }

    @Test fun traceIsLowercaseWithoutAccentsPunctuationOrDoubledLetters() {
        assertEquals("helo", SwipeLexicon.traceOf("Hello", Locale.US))
        assertEquals("dont", SwipeLexicon.traceOf("don't", Locale.US))
        assertEquals("cafe", SwipeLexicon.traceOf("café", Locale.US))
        assertEquals("welknown", SwipeLexicon.traceOf("well-known", Locale.US))
        assertEquals("שלום", SwipeLexicon.traceOf("שלום", Locale.forLanguageTag("he")))
        assertEquals("", SwipeLexicon.traceOf("123", Locale.US))
    }

    @Test fun swipedWordComesFirst() {
        for (word in listOf("the", "they", "hello", "help", "world", "would", "word", "keyboard", "put")) {
            assertEquals("swiping $word", word, decode(word, qwerty, english).first())
        }
    }

    @Test fun shortWordIsNotSwallowedByALongerCommonOne() {
        // "he" is the end of the path of "the"; the start of the swipe tells them apart.
        assertEquals("he", decode("he", qwerty, english).first())
        assertEquals("is", decode("is", qwerty, english).first())
    }

    @Test fun sloppySwipeStillFindsTheWord() {
        // A third of a key off, in every direction.
        for ((dx, dy) in listOf(35f to 0f, -35f to 0f, 0f to 45f, 0f to -45f, 30f to 30f)) {
            assertEquals("world", decode("world", qwerty, english, dx = dx, dy = dy).first())
            assertEquals("keyboard", decode("keyboard", qwerty, english, dx = dx, dy = dy).first())
            // Ends between o and p when off to the right: "help" is as good a guess.
            assertTrue("hello" in decode("hello", qwerty, english, dx = dx, dy = dy).take(2))
        }
    }

    @Test fun lookAlikeSwipesOfferBothWords() {
        // The paths of these differ only by a short detour, so the much more common word
        // may come first; the swiped word must be right behind it.
        assertTrue("there" in decode("there", qwerty, english).take(2))
        assertTrue("its" in decode("its", qwerty, english).take(3))
        // p, o, i, u and t are in one row: one straight line for "put", "pit" and "pot".
        assertEquals(listOf("put", "pit", "pot"), decode("pit", qwerty, english).take(3))
    }

    @Test fun capitalsAndAccentsAreKeptInTheResult() {
        assertEquals("Paris", decode("paris", qwerty, english).first())
        // Same keys for both: the more common spelling wins, the other is offered too.
        val cafe = decode("cafe", qwerty, english)
        assertEquals("café", cafe[0])
        assertTrue("cafe" in cafe)
        val its = decode("its", qwerty, english)
        assertTrue(its.indexOf("it's") < its.indexOf("its"))
    }

    @Test fun hebrewWords() {
        val locale = Locale.forLanguageTag("he")
        val words = lexicon(locale, "של" to 209, "אני" to 183, "מה" to 177, "שלי" to 174, "ילדים" to 163, "שלום" to 147, "תודה" to 145)
        for (word in listOf("שלום", "של", "שלי", "תודה", "מה", "אני", "ילדים")) {
            assertEquals("swiping $word", word, decode(word, hebrew, words).first())
        }
    }

    @Test fun twoLanguagesAtOnce() {
        val german = lexicon(Locale.GERMAN, "welt" to 150, "hello" to 60, "der" to 220)
        assertEquals("welt", decode("welt", qwerty, english, german).first())
        assertEquals("world", decode("world", qwerty, english, german).first())
        // In both lexicons: offered once.
        assertEquals(1, decode("hello", qwerty, english, german).count { it == "hello" })
    }

    @Test fun nothingForATapOrAKeyboardWithoutLetters() {
        val decoder = SwipeDecoder()
        val x = qwerty.xs[qwerty.keyOf('h')].toInt()
        val y = qwerty.ys[qwerty.keyOf('h')].toInt()
        assertTrue(decoder.decode(intArrayOf(x, x + 10, x + 20), intArrayOf(y, y, y + 5), 3, qwerty, listOf(english)).isEmpty())
        assertTrue(decoder.decode(intArrayOf(x), intArrayOf(y), 1, qwerty, listOf(english)).isEmpty())
        val symbols = SwipeLayout(emptyList(), keyWidth)
        val (xs, ys) = swipe("hello", qwerty)
        assertTrue(decoder.decode(xs, ys, xs.size, symbols, listOf(english)).isEmpty())
        assertTrue(decoder.decode(xs, ys, xs.size, qwerty, emptyList()).isEmpty())
    }

    @Test fun resampleSpacesPointsEvenlyAndKeepsTheEnds() {
        val outX = FloatArray(SwipeDecoder.POINTS)
        val outY = FloatArray(SwipeDecoder.POINTS)
        // An L: 300 right in uneven steps, then 100 down.
        val xs = floatArrayOf(0f, 10f, 200f, 300f, 300f)
        val ys = floatArrayOf(0f, 0f, 0f, 0f, 100f)
        SwipeDecoder.resample(xs, ys, xs.size, 400f, outX, outY)
        assertEquals(0f, outX.first(), 0.001f)
        assertEquals(300f, outX.last(), 0.001f)
        assertEquals(100f, outY.last(), 0.001f)
        val step = 400f / (SwipeDecoder.POINTS - 1)
        for (i in 1 until SwipeDecoder.POINTS) {
            // Along an L the distance on the path is the sum of the two moves.
            val moved = abs(outX[i] - outX[i - 1]) + abs(outY[i] - outY[i - 1])
            assertEquals("step $i", step, moved, 0.01f)
        }
    }
}
