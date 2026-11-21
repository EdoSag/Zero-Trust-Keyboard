// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.swipe

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A word the swipe could mean. Higher [score] is better; scores are only comparable within one decode. */
class SwipeCandidate(val word: String, val score: Float, val frequency: Int, val lexicon: Int)

/**
 * Turns the path of a finger over the keyboard into words. Open source and fully on the
 * device: no closed gesture library, no network.
 *
 * The method is shape matching (as in the SHARK2 papers by Kristensson and Zhai). Every
 * word has an ideal path: straight lines through the centres of its letters. The decoder
 * 1. keeps only words that start near where the finger went down and end near where it
 *    was lifted, and whose ideal path is about as long as the swipe,
 * 2. compares the swipe with each remaining ideal path, both cut into the same number of
 *    evenly spaced points, and
 * 3. for the best of those, checks that every letter of the word is passed in order,
 *    then ranks by closeness and by how common the word is.
 *
 * Not thread safe: use one decoder per thread, or call [decode] under a lock.
 */
class SwipeDecoder {
    private val inX = FloatArray(POINTS)
    private val inY = FloatArray(POINTS)
    private val tmpX = FloatArray(POINTS)
    private val tmpY = FloatArray(POINTS)
    private val polyX = FloatArray(SwipeLexicon.MAX_WORD_LENGTH)
    private val polyY = FloatArray(SwipeLexicon.MAX_WORD_LENGTH)
    private val dpPrev = FloatArray(POINTS)
    private val dpCur = FloatArray(POINTS)

    private class Scored(val lexicon: Int, val id: Int, var cost: Float)

    /**
     * @param xs,ys the finger path in keyboard pixels, [count] points
     * @param lexicons the languages in use; a word found in several is returned once
     * @param weights how much each lexicon counts (1 = fully), same order as [lexicons]
     * @param fitsContext how well a word of a lexicon follows the words before it, 0 (no
     *   idea) to 1 (the usual next word), see [contextFit]; null when nothing is known
     */
    fun decode(
        xs: IntArray, ys: IntArray, count: Int, layout: SwipeLayout,
        lexicons: List<SwipeLexicon>, weights: FloatArray? = null, maxResults: Int = 18,
        fitsContext: ((lexicon: Int, word: String, frequency: Int) -> Float)? = null,
    ): List<SwipeCandidate> {
        if (count < 2 || layout.size == 0) return emptyList()
        val kw = layout.keyWidth
        val length = pathLength(xs, ys, count)
        if (length < kw * MIN_SWIPE_KEYS) return emptyList()
        resample(FloatArray(count) { xs[it].toFloat() }, FloatArray(count) { ys[it].toFloat() }, count, length, inX, inY)

        val startKeys = layout.keysNear(inX[0], inY[0], kw * END_RADIUS_KEYS)
        val endKeys = layout.keysNear(inX[POINTS - 1], inY[POINTS - 1], kw * END_RADIUS_KEYS)
        if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()

        // Keys that differ only by an accent ("s" and "ş") lead to the same words: look once.
        val endPairs = LinkedHashSet<Int>()
        for (s in startKeys) for (e in endKeys) {
            endPairs.add((SwipeLexicon.baseOf(layout.charAt(s)).code shl 16) or SwipeLexicon.baseOf(layout.charAt(e)).code)
        }

        // Stage 1: shape distance for every word with fitting ends and length.
        val shortlist = ArrayList<Scored>(SHORTLIST + 1)
        var worst = Float.MAX_VALUE
        val minLen = length * MIN_LENGTH_RATIO - kw * LENGTH_SLACK_KEYS
        val maxLen = length * MAX_LENGTH_RATIO + kw * LENGTH_SLACK_KEYS
        for (l in lexicons.indices) {
            val lexicon = lexicons[l]
            for (pair in endPairs) {
                for (id in lexicon.candidates((pair ushr 16).toChar(), (pair and 0xFFFF).toChar())) {
                    val m = polyline(lexicon.trace(id), layout)
                    if (m < 2) continue
                    val wordLength = polylineLength(m)
                    if (wordLength < minLen || wordLength > maxLen) continue
                    val limit = if (shortlist.size < SHORTLIST) Float.MAX_VALUE else worst
                    val cost = shapeCost(m, wordLength, kw, limit)
                    if (cost >= limit) continue
                    shortlist.add(Scored(l, id, cost))
                    if (shortlist.size > SHORTLIST) {
                        shortlist.sortBy { it.cost }
                        shortlist.removeAt(shortlist.size - 1)
                        worst = shortlist[shortlist.size - 1].cost
                    } else if (shortlist.size == SHORTLIST) {
                        shortlist.sortBy { it.cost }
                        worst = shortlist[shortlist.size - 1].cost
                    }
                }
            }
        }
        if (shortlist.isEmpty()) return emptyList()

        // Stage 2: every letter must be passed, in order; then weigh in how common the word is.
        val results = ArrayList<SwipeCandidate>(shortlist.size)
        for (entry in shortlist) {
            val lexicon = lexicons[entry.lexicon]
            val m = polyline(lexicon.trace(entry.id), layout)
            val letters = letterCost(m, kw)
            val frequency = lexicon.frequency(entry.id)
            val weight = weights?.getOrNull(entry.lexicon) ?: 1f
            val ends = (sq(inX[0] - polyX[0], inY[0] - polyY[0]) +
                sq(inX[POINTS - 1] - polyX[m - 1], inY[POINTS - 1] - polyY[m - 1])) / (kw * kw)
            val word = lexicon.word(entry.id)
            val context = fitsContext?.invoke(entry.lexicon, word, frequency)?.coerceIn(0f, 1f) ?: 0f
            val score = -(SHAPE_WEIGHT * entry.cost + LETTER_WEIGHT * letters + END_WEIGHT * ends) +
                FREQUENCY_WEIGHT * frequency + LEXICON_WEIGHT * (weight - 1f) + CONTEXT_WEIGHT * context
            results.add(SwipeCandidate(word, score, frequency, entry.lexicon))
        }
        results.sortByDescending { it.score }
        // A word that is in several lexicons is offered once, with its best score.
        val seen = HashSet<String>()
        val unique = ArrayList<SwipeCandidate>(maxResults)
        for (candidate in results) {
            if (seen.add(candidate.word)) unique.add(candidate)
            if (unique.size == maxResults) break
        }
        return unique
    }

    /** Fills [polyX]/[polyY] with the key centres of [trace]; returns how many. */
    private fun polyline(trace: String, layout: SwipeLayout): Int {
        var m = 0
        var last = -1
        for (c in trace) {
            val key = layout.keyOf(c)
            if (key < 0 || key == last) continue
            polyX[m] = layout.xs[key]
            polyY[m] = layout.ys[key]
            last = key
            m++
        }
        return m
    }

    private fun polylineLength(m: Int): Float {
        var sum = 0f
        for (i in 1 until m) sum += dist(polyX[i] - polyX[i - 1], polyY[i] - polyY[i - 1])
        return sum
    }

    /**
     * Mean squared distance, in key widths, between the swipe and the word's ideal path at
     * [POINTS] evenly spaced points. Stops early (returning
     * at least [limit]) once the word cannot make the shortlist.
     */
    private fun shapeCost(m: Int, wordLength: Float, kw: Float, limit: Float): Float {
        resample(polyX, polyY, m, wordLength, tmpX, tmpY)
        val scale = 1f / (kw * kw)
        val bound = if (limit == Float.MAX_VALUE) Float.MAX_VALUE else limit * POINTS
        var sum = 0f
        for (i in 0 until POINTS) {
            val dx = inX[i] - tmpX[i]
            val dy = inY[i] - tmpY[i]
            sum += (dx * dx + dy * dy) * scale
            if (sum >= bound) return Float.MAX_VALUE
        }
        return sum / POINTS
    }

    /**
     * How well the swipe passes the word's letters in order: for the best such assignment
     * of letters to points of the swipe, the mean squared distance in key widths. The first
     * and last letter are tied to the two ends of the swipe.
     */
    private fun letterCost(m: Int, kw: Float): Float {
        val scale = 1f / (kw * kw)
        // dp[i] = best cost with the current letter matched to swipe point i.
        for (i in 0 until POINTS) dpPrev[i] = Float.MAX_VALUE
        dpPrev[0] = sq(inX[0] - polyX[0], inY[0] - polyY[0]) * scale
        for (j in 1 until m) {
            var best = Float.MAX_VALUE
            for (i in 0 until POINTS) {
                best = min(best, dpPrev[i])
                dpCur[i] = if (best == Float.MAX_VALUE) Float.MAX_VALUE
                else best + sq(inX[i] - polyX[j], inY[i] - polyY[j]) * scale
            }
            System.arraycopy(dpCur, 0, dpPrev, 0, POINTS)
        }
        return dpPrev[POINTS - 1] / m
    }

    companion object {
        /** Points each path is cut into. */
        const val POINTS = 40

        /** A swipe shorter than this many key widths is a tap that slipped. */
        private const val MIN_SWIPE_KEYS = 0.6f

        /** First and last letter must be within this many key widths of the swipe's ends. */
        private const val END_RADIUS_KEYS = 1.7f

        /** The word's ideal path may be this much shorter or longer than the swipe. */
        private const val MIN_LENGTH_RATIO = 0.6f
        private const val MAX_LENGTH_RATIO = 1.7f
        private const val LENGTH_SLACK_KEYS = 1.5f

        /** Words kept after stage 1. */
        private const val SHORTLIST = 100

        // Weights of the three things a word is ranked by. Set by measuring simulated swipes
        // of English and Hebrew words; to be tuned further with real swipes.
        private const val SHAPE_WEIGHT = 4.0f
        private const val LETTER_WEIGHT = 4.0f

        /** The two ends count extra: a finger starts and stops on purpose. */
        private const val END_WEIGHT = 2.0f

        /** Per step of dictionary frequency (0..255, logarithmic). */
        private const val FREQUENCY_WEIGHT = 0.09f

        /** Bonus or penalty for a secondary language's lexicon weight. */
        private const val LEXICON_WEIGHT = 2.0f

        /**
         * For a word that usually follows the word before it. Enough to choose between
         * words whose swipes look alike ("the" and "there"), also against a much more
         * common word, and not enough to replace a word that was swiped clearly.
         */
        private const val CONTEXT_WEIGHT = 5.0f

        /**
         * How well a word follows the words before it, 0..1, from two dictionary
         * probabilities (0..255, or negative for unknown): the word's own, and the word's
         * after those words. A dictionary that knows the pair gives the second one higher;
         * how much of the room up to 255 it takes is the strength of the pair.
         */
        fun contextFit(alone: Int, afterPrevious: Int): Float {
            if (alone < 0 || afterPrevious <= alone) return 0f
            return ((afterPrevious - alone).toFloat() / (MAX_PROBABILITY - alone).coerceAtLeast(1)).coerceIn(0f, 1f)
        }

        private const val MAX_PROBABILITY = 255

        private fun sq(dx: Float, dy: Float): Float = dx * dx + dy * dy
        private fun dist(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)

        fun pathLength(xs: IntArray, ys: IntArray, count: Int): Float {
            var sum = 0f
            for (i in 1 until count) sum += dist((xs[i] - xs[i - 1]).toFloat(), (ys[i] - ys[i - 1]).toFloat())
            return sum
        }

        /** Cuts the path into [POINTS] points evenly spaced along its length. */
        fun resample(xs: FloatArray, ys: FloatArray, count: Int, length: Float, outX: FloatArray, outY: FloatArray) {
            val n = outX.size
            if (count == 1 || length <= 0f) {
                for (i in 0 until n) { outX[i] = xs[0]; outY[i] = ys[0] }
                return
            }
            val step = length / (n - 1)
            outX[0] = xs[0]; outY[0] = ys[0]
            var out = 1
            var travelled = 0f   // length of the path up to point `seg`
            var seg = 0
            var segLength = dist(xs[1] - xs[0], ys[1] - ys[0])
            while (out < n - 1) {
                val target = step * out
                while (travelled + segLength < target && seg < count - 2) {
                    travelled += segLength
                    seg++
                    segLength = dist(xs[seg + 1] - xs[seg], ys[seg + 1] - ys[seg])
                }
                val t = if (segLength > 0f) max(0f, min(1f, (target - travelled) / segLength)) else 0f
                outX[out] = xs[seg] + (xs[seg + 1] - xs[seg]) * t
                outY[out] = ys[seg] + (ys[seg + 1] - ys[seg]) * t
                out++
            }
            outX[n - 1] = xs[count - 1]; outY[n - 1] = ys[count - 1]
        }
    }
}
