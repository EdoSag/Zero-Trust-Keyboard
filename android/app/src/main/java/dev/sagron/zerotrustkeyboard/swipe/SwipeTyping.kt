// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.swipe

import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.utils.Log
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.Executors

/**
 * Swipe typing for the keyboard: connects [SwipeDecoder] to the keyboard that is showing
 * and to the dictionaries that are loaded. Everything happens on the device.
 *
 * Word lists are read from the dictionaries once, in the background, and kept in memory
 * for as long as the dictionary is. Dictionaries that change while typing (learned words,
 * personal dictionary, contacts) are read again every few minutes.
 */
object SwipeTyping {
    private const val TAG = "SwipeTyping"

    /** How long a changing dictionary's word list is used before it is read again. */
    private const val REFRESH_MILLIS = 3 * 60 * 1000L

    /** One dictionary to swipe from. */
    class Source(
        val dictionary: Dictionary,
        val locale: Locale,
        /** 1 for the main language, less for a secondary one. */
        val weight: Float,
        /** True for dictionaries that gain words while typing. */
        val changes: Boolean,
    )

    class Result(val word: String, val score: Int, val dictionary: Dictionary, val possiblyOffensive: Boolean)

    private class Entry {
        @Volatile var lexicon: SwipeLexicon? = null
        @Volatile var offensive: Set<String> = emptySet()
        @Volatile var loading = false
        @Volatile var builtAt = 0L
    }

    private val entries = WeakHashMap<Dictionary, Entry>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SwipeLexicon").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }
    private val decoder = SwipeDecoder()
    private var layoutKeyboard = WeakReference<Keyboard>(null)
    private var layout: SwipeLayout? = null

    /** Starts reading the dictionary's words, so the first swipe finds them ready. */
    fun warmUp(dictionary: Dictionary, locale: Locale) {
        entry(Source(dictionary, locale, 1f, false))
    }

    /**
     * The words the swipe could mean, best first. Empty while the word lists are still
     * being read (the first seconds after the keyboard starts) or when the keyboard has
     * no letters (symbols, numbers).
     */
    fun decode(pointers: InputPointers, keyboard: Keyboard, sources: List<Source>): List<Result> {
        val count = pointers.pointerSize
        if (count < 2) return emptyList()
        val ready = ArrayList<Pair<Source, Entry>>(sources.size)
        for (source in sources) {
            val entry = entry(source)
            if (entry.lexicon != null) ready.add(source to entry)
        }
        if (ready.isEmpty()) return emptyList()
        val lexicons = ready.map { it.second.lexicon!! }
        val weights = FloatArray(ready.size) { ready[it].first.weight }
        val candidates = synchronized(decoder) {
            val layout = layoutFor(keyboard)
            decoder.decode(pointers.xCoordinates, pointers.yCoordinates, count, layout, lexicons, weights)
        }
        if (candidates.isEmpty()) return emptyList()
        val best = candidates[0].score
        return candidates.map { candidate ->
            val (source, entry) = ready[candidate.lexicon]
            // The keyboard's scores are whole numbers, higher is better; keep the order and
            // stay well above the level under which suggestions are dropped.
            val score = SCORE_BASE - ((best - candidate.score) * SCORE_PER_UNIT).toInt()
            Result(candidate.word, score, source.dictionary, candidate.word in entry.offensive)
        }
    }

    private const val SCORE_BASE = 1_500_000
    private const val SCORE_PER_UNIT = 10_000f

    private fun entry(source: Source): Entry {
        val entry = synchronized(entries) { entries.getOrPut(source.dictionary) { Entry() } }
        val stale = entry.lexicon == null ||
            (source.changes && System.currentTimeMillis() - entry.builtAt > REFRESH_MILLIS)
        if (stale && !entry.loading) {
            entry.loading = true
            val dictionary = source.dictionary
            val locale = source.locale
            executor.execute {
                try {
                    val started = System.currentTimeMillis()
                    val builder = SwipeLexicon.Builder(locale)
                    val offensive = HashSet<String>()
                    val listed = dictionary.forEachWord { word, probability, possiblyOffensive ->
                        builder.add(word, probability)
                        if (possiblyOffensive) offensive.add(word)
                    }
                    if (listed) {
                        val lexicon = builder.build()
                        // A dictionary that is still loading lists nothing; try again on the next swipe.
                        if (lexicon.size > 0 || source.changes) {
                            entry.offensive = offensive
                            entry.lexicon = lexicon
                            entry.builtAt = System.currentTimeMillis()
                        }
                        Log.i(TAG, "${dictionary.mDictType} $locale: ${lexicon.size} words in ${System.currentTimeMillis() - started} ms")
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "could not read ${dictionary.mDictType} $locale", t)
                } finally {
                    entry.loading = false
                }
            }
        }
        return entry
    }

    /** The letter keys of [keyboard]. Kept until another keyboard is shown. */
    private fun layoutFor(keyboard: Keyboard): SwipeLayout {
        layout?.let { if (layoutKeyboard.get() === keyboard) return it }
        val keys = ArrayList<SwipeKey>()
        for (key in keyboard.sortedKeys) {
            val code = key.code
            if (code <= 0 || code > Char.MAX_VALUE.code || !Character.isLetter(code)) continue
            // A shifted keyboard has capital letters on the same keys.
            val char = Character.toLowerCase(code).toChar()
            keys.add(SwipeKey(char, key.x + key.width / 2f, key.y + key.height / 2f))
        }
        val created = SwipeLayout(keys, keyboard.mMostCommonKeyWidth.toFloat().coerceAtLeast(1f))
        layout = created
        layoutKeyboard = WeakReference(keyboard)
        return created
    }
}
