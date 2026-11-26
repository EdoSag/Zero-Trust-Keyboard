// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.assist

import dev.sagron.zerotrustkeyboard.lm.WritingPrompts

/**
 * What the keyboard asks ZT Assist for, and what it makes of the answer.
 *
 * ZT Assist is the Zero-Trust app that holds a larger language model (Gemma) on the phone
 * and answers the other Zero-Trust apps. The keyboard uses it for the Proofread key, where
 * it does better than the keyboard's own small model, above all in Hebrew, and for the
 * Rewrite key, which the small model cannot do at all.
 *
 * The names and numbers here are the contract with AssistService.java in Zero-Trust-Assist
 * (and ZtAssistPlugin.java in Zero-Trust-Core, the same client for the Flutter apps).
 * Change them in all three.
 */
object AssistWriting {
    const val PACKAGE = "dev.sagron.zerotrustassist"
    const val SERVICE = "$PACKAGE.AssistService"

    // To ZT Assist.
    const val MSG_ASK = 2
    const val MSG_CANCEL = 3

    // From ZT Assist.
    const val MSG_CHUNK = 102
    const val MSG_DONE = 103
    const val MSG_FAILED = 104

    /** Use ZT Assist for the Proofread key when it is on the phone. On until turned off. */
    const val PREF_USE_ASSIST = "zt_lm_use_assist"
    const val DEFAULT_USE_ASSIST = true

    /**
     * Longer texts are not sent at once: select a part. ZT Assist itself takes more, but it
     * writes its answer letter by letter and a keyboard should not be waited on for minutes.
     */
    const val MAX_CHARS = WritingPrompts.MAX_PROOFREAD_CHARS

    /** A job ZT Assist does for the keyboard, with the name it goes by there. */
    enum class Task(val wire: String) {
        PROOFREAD("proofread"),
        REWRITE("rewrite"),
    }

    /** Whether ZT Assist is on this phone and is the real one. */
    enum class Presence {
        READY,

        /** Something else holds ZT Assist's name: it is not signed with the keyboard's key. */
        UNTRUSTED,
        NOT_INSTALLED,
    }

    /** Why ZT Assist did not answer. */
    enum class Failure {
        NOT_INSTALLED,

        /** ZT Assist is there, but no model was downloaded in it yet. */
        NO_MODEL,

        /** The model could not be loaded, mostly for lack of free memory. */
        LOAD_FAILED,

        /** This ZT Assist is older than the job asked of it. */
        NEEDS_UPDATE,

        /** ZT Assist was stopped in the middle (the phone took its memory back). */
        STOPPED,
        FAILED;

        /**
         * Whether the keyboard's own model is worth a try instead: yes when ZT Assist cannot
         * do the job at all, no when it was on it and something went wrong, where a second
         * wait would only be a second disappointment.
         */
        val ownModelCanTry: Boolean
            get() = this == NOT_INSTALLED || this == NO_MODEL || this == LOAD_FAILED || this == NEEDS_UPDATE
    }

    /** How one request ended. */
    sealed class Result {
        class Answer(val text: String) : Result()
        class Failed(val failure: Failure) : Result()
    }

    /** The failure behind a code from ZT Assist (see MSG_FAILED). */
    fun failure(code: String?): Failure = when (code) {
        "not_installed", "untrusted" -> Failure.NOT_INSTALLED
        "no_model" -> Failure.NO_MODEL
        "load_failed" -> Failure.LOAD_FAILED
        "unsupported" -> Failure.NEEDS_UPDATE
        "stopped" -> Failure.STOPPED
        else -> Failure.FAILED
    }

    /**
     * How a request that reached its end is to be taken. An answer that was stopped, or
     * that was written for a part of the text only, must not replace the whole text.
     */
    fun result(text: String, stopped: Boolean, truncated: Boolean): Result = when {
        stopped -> Result.Failed(Failure.STOPPED)
        truncated || text.isBlank() -> Result.Failed(Failure.FAILED)
        else -> Result.Answer(text)
    }

    /**
     * The text to put in place of [original], or null when the answer cannot be that text
     * done over: empty, or far longer or shorter, as when the model answers the text
     * instead of working on it.
     */
    fun clean(task: Task, original: String, answer: String?): String? {
        val plain = unfenced(answer ?: return null)
        return when (task) {
            // The same checks as for the keyboard's own model.
            Task.PROOFREAD -> WritingPrompts.cleanProofread(original, plain)
            Task.REWRITE -> cleanRewrite(original, plain)
        }
    }

    // The model may wrap its whole answer in a code block (``` ... ```).
    private fun unfenced(answer: String): String {
        val text = answer.trim()
        if (!text.startsWith("```") || !text.endsWith("```") || text.length < 7) return text
        val inside = text.substring(3, text.length - 3)
        // The word after the opening marks ("text", "markdown") is not part of the answer.
        val firstBreak = inside.indexOf('\n')
        val label = if (firstBreak >= 0) inside.substring(0, firstBreak).trim().lowercase() else null
        val body = if (label != null && label in FENCE_LABELS) inside.substring(firstBreak + 1) else inside
        return body.trim()
    }

    private val FENCE_LABELS = setOf("", "text", "txt", "plaintext", "markdown", "md")

    private fun cleanRewrite(original: String, answer: String): String? {
        var text = answer.trim()
        if (text.isEmpty()) return null
        // Quotes the model put around the whole text.
        for ((open, close) in listOf("\"" to "\"", "“" to "”", "«" to "»", "„" to "“")) {
            if (text.length > 2 && text.startsWith(open) && text.endsWith(close) &&
                !(original.trim().startsWith(open) && original.trim().endsWith(close))
            ) text = text.substring(open.length, text.length - close.length).trim()
        }
        if (text.isEmpty()) return null
        // A rewrite may be a good deal shorter or longer than the text, but not another text.
        // A few words more are always fine: "ok" may well become "That works for me".
        val length = original.trim().length
        if (text.length < length * 0.2 || text.length > length * 2.5 + 20) return null
        // Keep the spaces and line breaks around the text as they were.
        val lead = original.takeWhile { it.isWhitespace() }
        val trail = original.takeLastWhile { it.isWhitespace() }
        return lead + text + trail
    }
}
