// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
import dev.sagron.zerotrustkeyboard.voice.VoiceTyping
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SettingsDestination

/**
 * The Translate key: translates what is written in the text field (or the selected part
 * of it) between the user's two languages, in place. The bar that appears offers Undo.
 * The translation is worked out on the phone; the text is not sent anywhere.
 */
class TranslateTyping(private val ime: LatinIME) : TranslateBarView.Listener {
    private enum class State { IDLE, WORKING, DONE }

    /** What was translated, and where it sits in the text field. */
    private class Job(val start: Int, val original: String, val from: String, val to: String) {
        var translated: String? = null
    }

    private var state = State.IDLE
    private var job: Job? = null
    private var engineId = 0
    private var bar: TranslateBarView? = null
    private var containerVisibility: Int? = null

    /** Raised whenever work is abandoned, so a late answer is not typed. */
    private var session = 0

    // While the translation is worked out the bar counts the seconds, so a long first
    // run (the engine and the model are loaded) is not taken for a keyboard that hangs.
    private val handler = Handler(Looper.getMainLooper())
    private var workingSince = 0L
    private val tick = object : Runnable {
        override fun run() {
            val current = job
            if (state != State.WORKING || current == null) return
            bar?.showWorking(current.from, current.to, ((SystemClock.elapsedRealtime() - workingSince) / 1000).toInt())
            handler.postDelayed(this, 1000)
        }
    }

    /** Called when the keyboard's views are (re)created. */
    fun attach(bar: TranslateBarView?) {
        this.bar = bar
        bar?.listener = this
        val current = job
        if (state == State.IDLE || current == null) return
        showBar()
        if (state == State.WORKING) tick.run() else bar?.showDone(current.from, current.to)
    }

    /** The Translate key was pressed. */
    fun onTranslateKey() {
        when (state) {
            State.WORKING -> Unit
            State.DONE -> {
                // A second press on a finished translation starts over with what is there now.
                close()
                start()
            }
            State.IDLE -> start()
        }
    }

    /**
     * Something else was typed: the text is changing, so a translation in progress no longer
     * fits and a finished one can no longer be undone. Not for what the keyboard types itself.
     */
    fun onOtherInput() {
        if (state != State.IDLE && !typing) close()
    }

    /** The keyboard is closing or moving to another text field. */
    fun cancel() {
        if (state != State.IDLE) close()
    }

    private fun close() {
        session++
        if (state == State.WORKING) TranslateEngine.cancel(engineId)
        state = State.IDLE
        job = null
        handler.removeCallbacks(tick)
        hideBar()
    }

    private fun start() {
        if (!TranslateEngine.available) {
            toast(R.string.zt_translate_not_in_edition)
            return
        }
        if (Settings.getValues().mInputAttributes.mIsPasswordField) {
            toast(R.string.zt_translate_not_here)
            return
        }
        val source = source()
        if (source == null || source.text.isBlank()) {
            toast(R.string.zt_translate_nothing)
            return
        }
        if (source.text.length > TranslateText.MAX_CHARS) {
            toast(R.string.zt_translate_too_long)
            return
        }
        val (mine, other) = TranslateModels.languages(ime)
        val keyboard = TranslateCatalog.code(RichInputMethodManager.getInstance().currentSubtypeLocale.toLanguageTag())
        val (from, to) = TranslateText.direction(source.text, mine, other, keyboard)
        if (TranslateCatalog.route(TranslateModels.installed(ime), from, to) == null) {
            toast(R.string.zt_translate_needs_languages)
            openSettings()
            return
        }
        val pieces = TranslateText.pieces(source.text)
        val current = Job(source.start, source.text, from, to)
        job = current
        state = State.WORKING
        workingSince = SystemClock.elapsedRealtime()
        showBar()
        handler.removeCallbacks(tick)
        tick.run()
        val mySession = ++session
        engineId = TranslateEngine.translate(ime, bar, from, to, pieces.map { it.text }.filter { it.isNotEmpty() }) { out ->
            if (mySession != session) return@translate
            handler.removeCallbacks(tick)
            if (out == null) {
                close()
                toast(R.string.zt_translate_failed)
                return@translate
            }
            val translated = TranslateText.join(pieces, out)
            // Typing the translation is itself input; it must not close the bar.
            current.translated = translated
            typing = true
            try {
                ime.ztReplaceText(current.start, current.start + current.original.length, translated)
            } finally {
                typing = false
            }
            state = State.DONE
            bar?.showDone(from, to)
        }
    }

    /** True while the keyboard types a translation (or its undo) itself. */
    private var typing = false

    private class Source(val text: String, val start: Int)

    /** The selected text, or else everything in the text field, with its position there. */
    private fun source(): Source? {
        try {
            // The word being typed is part of the text: let it stand as typed first.
            ime.ztFinishWord()
            val connection = ime.currentInputConnection ?: return null
            val extracted = connection.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 100_000 }, 0)
            val whole = extracted?.text?.toString()
            if (extracted != null && whole != null) {
                val offset = maxOf(extracted.startOffset, 0)
                val a = minOf(extracted.selectionStart, extracted.selectionEnd).coerceIn(0, whole.length)
                val b = maxOf(extracted.selectionStart, extracted.selectionEnd).coerceIn(0, whole.length)
                return if (a != b) Source(whole.substring(a, b), offset + a) else Source(whole, offset)
            }
            // Some apps do not hand out their text that way: take it from around the cursor.
            val selection = ime.ztSelection()
            val selected = connection.getSelectedText(0)?.toString()
            if (!selected.isNullOrEmpty()) return Source(selected, minOf(selection[0], selection[1]))
            val before = connection.getTextBeforeCursor(TranslateText.MAX_CHARS + 1, 0)?.toString().orEmpty()
            val after = connection.getTextAfterCursor(TranslateText.MAX_CHARS + 1, 0)?.toString().orEmpty()
            val start = selection[0] - before.length
            return if (start < 0) null else Source(before + after, start)
        } catch (e: Exception) {
            Log.w(TAG, "could not read the text field", e)
            return null
        }
    }

    // TranslateBarView.Listener
    override fun onTranslateCancel() = close()

    override fun onTranslateDone() = close()

    override fun onTranslateUndo() {
        val current = job
        val translated = current?.translated
        if (state == State.DONE && current != null && translated != null) {
            typing = true
            try {
                ime.ztReplaceText(current.start, current.start + translated.length, current.original)
            } finally {
                typing = false
            }
        }
        close()
    }

    private fun showBar() {
        val bar = bar ?: return
        bar.applyColors()
        // The bar lives in the suggestion strip's place; show that place if it is hidden.
        val container = bar.parent as? View
        if (container != null && container.visibility != View.VISIBLE) {
            containerVisibility = container.visibility
            container.visibility = View.VISIBLE
        }
        bar.visibility = View.VISIBLE
    }

    private fun hideBar() {
        val bar = bar ?: return
        bar.visibility = View.GONE
        containerVisibility?.let { (bar.parent as? View)?.visibility = it }
        containerVisibility = null
    }

    private fun openSettings() {
        val intent = Intent(ime, SettingsActivity2::class.java)
            .putExtra(VoiceTyping.EXTRA_SCREEN, SettingsDestination.Translate)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            ime.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "could not open the translate settings", e)
        }
    }

    private fun toast(text: Int) = KeyboardSwitcher.getInstance().showToast(ime.getString(text), true)

    companion object {
        private const val TAG = "TranslateTyping"
    }
}
