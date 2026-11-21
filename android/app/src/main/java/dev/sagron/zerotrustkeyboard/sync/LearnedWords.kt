// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.sync

/**
 * Which words of the typing history sync. Plain Kotlin in the shared sources (the sync itself
 * is only in the full edition), so it is tested without a phone.
 *
 * The history holds every word typed, with a level that rises with use and falls with time.
 * Only words the keyboard already offers on their own (level [MIN_LEVEL] and up) are worth
 * sending: the rest are typos and one-offs that it is still making up its mind about.
 */
object LearnedWords {
    /** One entry of the typing history. */
    class Entry(val word: String, val level: Int, val timestamp: Int, val notAWord: Boolean)

    /** The level from which the keyboard offers a word from the history (native MIN_VISIBLE_LEVEL). */
    const val MIN_LEVEL = 2

    /** At most this many words per language are sent: the most used, then the most recent. */
    const val MAX_WORDS = 5_000

    /** The longest word a dictionary holds. */
    private const val MAX_LENGTH = 48

    /** True for a word that may be sent, and added when it arrives. */
    fun valid(word: String): Boolean =
        word.isNotEmpty() && word.length <= MAX_LENGTH && word.none { it.isWhitespace() || it.isISOControl() }

    /** The words of one language's history that sync, sorted. */
    fun pick(entries: List<Entry>, max: Int = MAX_WORDS): List<String> =
        entries.asSequence()
            .filter { !it.notAWord && it.level >= MIN_LEVEL && valid(it.word) }
            .sortedWith(compareByDescending<Entry> { it.level }.thenByDescending { it.timestamp }.thenBy { it.word })
            .map { it.word }
            .distinct()
            .take(max)
            .sorted()
            .toList()
}
