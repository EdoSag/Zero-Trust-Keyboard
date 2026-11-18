// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.swipe

import java.text.Normalizer
import java.util.Locale

/**
 * The words one language can swipe, taken from the dictionaries the keyboard already has
 * (main dictionary, personal dictionary, learned words). Held in memory in a compact form:
 * the word, the letters to trace for it, and how common it is.
 */
class SwipeLexicon private constructor(
    private val words: Array<String>,
    /** The keys to pass, in order: lowercase, accents removed, doubled letters once. */
    private val traces: Array<String>,
    /** 0..255, the dictionary's own scale (logarithmic: 15 steps is about ten times rarer). */
    private val frequencies: ByteArray,
    private val buckets: HashMap<Int, IntArray>,
) {
    val size: Int get() = words.size

    fun word(id: Int): String = words[id]
    fun trace(id: Int): String = traces[id]
    fun frequency(id: Int): Int = frequencies[id].toInt() and 0xFF

    /** Ids of the words whose trace starts with [first] and ends with [last]. */
    fun candidates(first: Char, last: Char): IntArray = buckets[bucketKey(first, last)] ?: EMPTY

    class Builder(private val locale: Locale) {
        private val byWord = HashMap<String, Int>()

        /** Adds a word; when it is added twice the higher frequency wins. */
        fun add(word: String, frequency: Int) {
            if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return
            val f = frequency.coerceIn(0, 255)
            val old = byWord[word]
            if (old == null || old < f) byWord[word] = f
        }

        fun build(): SwipeLexicon {
            val words = ArrayList<String>(byWord.size)
            val traces = ArrayList<String>(byWord.size)
            val frequencies = ArrayList<Int>(byWord.size)
            val bucketLists = HashMap<Int, ArrayList<Int>>()
            for ((word, frequency) in byWord) {
                val trace = traceOf(word, locale)
                if (trace.isEmpty()) continue
                val id = words.size
                words.add(word)
                // Most words are their own trace; share the string then.
                traces.add(if (trace == word) word else trace)
                frequencies.add(frequency)
                bucketLists.getOrPut(bucketKey(trace.first(), trace.last())) { ArrayList() }.add(id)
            }
            val buckets = HashMap<Int, IntArray>(bucketLists.size * 2)
            for ((key, ids) in bucketLists) buckets[key] = ids.toIntArray()
            return SwipeLexicon(
                words.toTypedArray(), traces.toTypedArray(),
                ByteArray(frequencies.size) { frequencies[it].toByte() }, buckets,
            )
        }
    }

    companion object {
        const val MAX_WORD_LENGTH = 40
        private val EMPTY = IntArray(0)
        private val MARKS = Regex("\\p{Mn}+")

        private fun bucketKey(first: Char, last: Char): Int = (first.code shl 16) or last.code

        /**
         * The letters a finger passes for [word]: lowercase, without accents (they sit on
         * the base letter's key), without characters that are not letters (apostrophe,
         * hyphen), and with a doubled letter once, because the finger does not leave the key.
         */
        fun traceOf(word: String, locale: Locale): String {
            var s = word.lowercase(locale)
            if (s.any { it.code > 0x7F }) {
                s = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "")
            }
            val out = StringBuilder(s.length)
            for (c in s) {
                if (!Character.isLetter(c)) continue
                if (out.isNotEmpty() && out[out.length - 1] == c) continue
                out.append(c)
            }
            return out.toString()
        }
    }
}
