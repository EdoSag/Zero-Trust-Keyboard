// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.lm

/**
 * What the language model is asked, and how its answer is cleaned up before it is typed.
 * The model (Qwen2.5 Instruct) reads a conversation in the ChatML form.
 */
object WritingPrompts {
    /** Longer texts are not proofread at once: select a part. */
    const val MAX_PROOFREAD_CHARS = 1500

    /** How much of the text before the cursor the model reads to finish a sentence. */
    const val COMPLETION_CONTEXT_CHARS = 400

    /** At most this many words are offered to finish a sentence. */
    const val COMPLETION_WORDS = 4

    /** Tokens the model may write to finish a sentence. */
    const val COMPLETION_TOKENS = 10

    private const val PROOFREAD_SYSTEM =
        "You correct spelling, grammar and punctuation. Keep the language, the meaning, the tone " +
            "and the line breaks of the text. Change as little as possible. Reply with the corrected " +
            "text only, without quotes, notes or explanations. If nothing needs fixing, reply with the text unchanged."

    private fun chat(system: String, user: String) =
        "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n"

    // The model's own markers must not come from the user's text.
    private fun plain(text: String) = text.replace("<|", "< |")

    fun proofreadPrompt(text: String): String = chat(PROOFREAD_SYSTEM, plain(text))

    /** Room for the corrected text: about as long as the text, plus a little. */
    fun proofreadTokens(text: String): Int = text.length / 2 + 64

    /**
     * The corrected text, or null when the answer cannot be the same text corrected (empty, or
     * far longer or shorter, as when the model answers the text instead of fixing it).
     */
    fun cleanProofread(original: String, answer: String?): String? {
        var text = answer?.trim() ?: return null
        if (text.isEmpty()) return null
        // Quotes the model put around the whole text.
        for ((open, close) in listOf("\"" to "\"", "“" to "”", "«" to "»", "„" to "“")) {
            if (text.length > 2 && text.startsWith(open) && text.endsWith(close) &&
                !(original.trim().startsWith(open) && original.trim().endsWith(close))
            ) text = text.substring(open.length, text.length - close.length).trim()
        }
        val ratio = text.length.toDouble() / original.trim().length.coerceAtLeast(1)
        if (ratio < 0.5 || ratio > 1.6) return null
        // Keep the spaces and line breaks around the text as they were.
        val lead = original.takeWhile { it.isWhitespace() }
        val trail = original.takeLastWhile { it.isWhitespace() }
        return lead + text + trail
    }

    /**
     * The text the model continues to finish a sentence: the end of what is before the
     * cursor, from a word boundary, without the space the user typed last.
     */
    fun completionPrompt(before: String): String {
        var text = before.takeLast(COMPLETION_CONTEXT_CHARS)
        if (text.length < before.length) text = text.substringAfter(' ', text)
        return plain(text.trimEnd())
    }

    /**
     * Whether it is worth asking: the cursor sits after a space that follows a few words
     * of a sentence that is not finished.
     */
    fun wantsCompletion(before: String): Boolean {
        if (!before.endsWith(" ") || before.endsWith("  ")) return false
        val text = before.trimEnd()
        if (text.isEmpty() || text.last() in ".!?:;\n") return false
        val sentence = text.substringAfterLast('\n').split(Regex("(?<=[.!?])\\s+")).last()
        return sentence.split(Regex("\\s+")).count { it.any(Char::isLetter) } >= 2
    }

    /**
     * The words to offer after the text, or null. The model's last word may be cut, so it is
     * dropped unless the answer ended the sentence; at most [COMPLETION_WORDS] words.
     */
    fun cleanCompletion(answer: String?): String? {
        val text = answer?.replace('\n', ' ')?.trim() ?: return null
        if (text.isEmpty() || text.contains("<|")) return null
        var words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val endsSentence = text.last() in ".!?"
        // The end of a sentence closes the offer.
        val end = words.indexOfFirst { it.last() in ".!?" }
        if (end >= 0) words = words.take(end + 1)
        else if (!endsSentence && words.size > 1) words = words.dropLast(1)
        words = words.take(COMPLETION_WORDS)
        if (words.isEmpty() || words.none { w -> w.any(Char::isLetter) }) return null
        return words.joinToString(" ")
    }
}
