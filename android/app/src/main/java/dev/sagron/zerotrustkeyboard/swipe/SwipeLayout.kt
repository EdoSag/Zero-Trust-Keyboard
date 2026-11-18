// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.swipe

/** One letter key: the character it types and the centre of the key, in keyboard pixels. */
class SwipeKey(val char: Char, val x: Float, val y: Float)

/**
 * Where the letter keys are on the keyboard that is showing. Built from the real key
 * positions, so every layout, language, screen size and one-handed mode works without
 * a table of its own.
 */
class SwipeLayout(keys: List<SwipeKey>, /** Width of a typical letter key, the unit of distance. */ val keyWidth: Float) {
    private val index = HashMap<Char, Int>(keys.size * 2)
    private val chars = CharArray(keys.size)
    val xs = FloatArray(keys.size)
    val ys = FloatArray(keys.size)
    val size: Int get() = chars.size

    init {
        keys.forEachIndexed { i, key ->
            chars[i] = key.char
            xs[i] = key.x
            ys[i] = key.y
            index.putIfAbsent(key.char, i)
        }
    }

    /**
     * The key for [c]. A letter with an accent that has no key of its own is on its base
     * letter's key ("é" on "e"). -1 when the layout has no key for it at all.
     */
    fun keyOf(c: Char): Int {
        index[c]?.let { return it }
        if (c.code <= 0x7F) return -1
        val key = index[SwipeLexicon.baseOf(c)] ?: -1
        // Remember it: the same few letters are asked for with every swipe.
        index[c] = key
        return key
    }

    fun charAt(key: Int): Char = chars[key]

    /** Keys whose centre is within [radius] pixels of the point, nearest first. */
    fun keysNear(x: Float, y: Float, radius: Float): IntArray {
        val r2 = radius * radius
        val found = ArrayList<Int>(8)
        for (i in chars.indices) {
            val dx = xs[i] - x
            val dy = ys[i] - y
            if (dx * dx + dy * dy <= r2) found.add(i)
        }
        found.sortBy { i -> (xs[i] - x) * (xs[i] - x) + (ys[i] - y) * (ys[i] - y) }
        return found.toIntArray()
    }
}
