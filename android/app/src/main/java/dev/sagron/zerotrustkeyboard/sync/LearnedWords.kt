// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.sync

/**
 * Which words of the typing history sync. Plain Kotlin in the shared sources (the sync itself
 * is only in the full edition), so it is tested without a phone.
 *
 * The history (dictionary format 403) holds every word typed with a count of how often, and
 * the time it was last typed; its "level" is always 0 in this format. Only words typed at
 * least [MIN_COUNT] times are sent: a word typed once is as likely a typo as a word.
 */
object LearnedWords {
    /** One entry of the typing history. */
    class Entry(val word: String, val count: Int, val timestamp: Int, val notAWord: Boolean)

    /**
     * How often a word must have been typed to be sent. A word that arrives is added with
     * this count, so the phone that received it reports it too and does not look as if it
     * had forgotten it.
     */
    const val MIN_COUNT = 2

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
            .filter { !it.notAWord && it.count >= MIN_COUNT && valid(it.word) }
            .sortedWith(compareByDescending<Entry> { it.count }.thenByDescending { it.timestamp }.thenBy { it.word })
            .map { it.word }
            .distinct()
            .take(max)
            .sorted()
            .toList()
}
