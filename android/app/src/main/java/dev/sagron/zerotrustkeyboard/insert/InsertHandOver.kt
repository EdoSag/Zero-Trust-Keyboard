// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.insert

/**
 * Carries a text picked in another Zero-Trust app back to the text field it was asked for.
 *
 * The keyboard closes while the other app is on screen, so the text cannot be typed at
 * once. It is kept here, in memory only, and typed when the keyboard next opens in the same
 * text field. It is dropped if the keyboard opens somewhere else first or too much time
 * passes, so a picked value never lands in a field it was not meant for.
 */
object InsertHandOver {
    /** How long a request, and then a picked text, waits for its text field. */
    const val WAIT_MS = 2 * 60 * 1000L

    /** Longer texts are cut: a keyboard types a snippet, not a document. */
    const val MAX_CHARS = 20_000

    private class Request(val app: String, val field: Int, val at: Long) {
        var text: String? = null
        var textAt = 0L
    }

    private var request: Request? = null

    /** The insert key was pressed in the text field [field] of [app]. */
    @Synchronized
    fun ask(app: String?, field: Int, now: Long) {
        request = if (app.isNullOrEmpty()) null else Request(app, field, now)
    }

    /** The other app answered with [text]. False when nobody is waiting for it any more. */
    @Synchronized
    fun answer(text: String?, now: Long): Boolean {
        val current = request ?: return false
        if (text.isNullOrEmpty() || now - current.at > WAIT_MS) {
            request = null
            return false
        }
        current.text = text.take(MAX_CHARS)
        current.textAt = now
        return true
    }

    /** Nothing was picked. */
    @Synchronized
    fun cancel() {
        request = null
    }

    /**
     * The keyboard opened in the text field [field] of [app]: the text to type there, if one
     * is waiting for exactly this field. A text waiting for another field is dropped.
     */
    @Synchronized
    fun take(app: String?, field: Int, now: Long): String? {
        val current = request ?: return null
        val text = current.text
        if (text == null) {
            // Still choosing in the other app (or the choice was given up): keep waiting
            // only as long as a choice can reasonably take.
            if (now - current.at > WAIT_MS) request = null
            return null
        }
        request = null
        if (app != current.app || field != current.field || now - current.textAt > WAIT_MS) return null
        return text
    }
}
