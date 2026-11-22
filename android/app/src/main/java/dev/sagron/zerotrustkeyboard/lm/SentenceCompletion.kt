// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.lm

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.databinding.ClipboardSuggestionBinding
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.prefs

/**
 * Finish my sentence: when the user stops after a word and a space, the language model
 * guesses the next few words, and they are offered as a chip in the suggestion strip. One
 * tap types them. Off until turned on; never in passwords, in incognito mode, or while the
 * phone is locked. The text is read on the phone only and is not kept.
 */
class SentenceCompletion(private val ime: LatinIME) {
    private val handler = Handler(Looper.getMainLooper())
    private var waiting: String? = null
    private val check = Runnable { start() }

    /** Something was typed: whatever was being guessed no longer fits. Wait for the next pause. */
    fun onInput() {
        cancel()
        if (!enabled()) return
        handler.postDelayed(check, PAUSE_MILLIS)
    }

    /** The keyboard is closing or moving to another text field. */
    fun cancel() {
        handler.removeCallbacks(check)
        if (waiting != null) {
            waiting = null
            LanguageModelEngine.cancel()
        }
    }

    private fun enabled(): Boolean =
        ime.prefs().getBoolean(LanguageModels.PREF_FINISH_SENTENCES, LanguageModels.DEFAULT_FINISH_SENTENCES) &&
            LanguageModels.available

    private fun fieldFits(): Boolean {
        val settings = Settings.getValues()
        if (settings.mIsLocked || settings.mIncognitoModeEnabled || settings.mInputAttributes.mIsPasswordField) return false
        val type = ime.currentInputEditorInfo?.inputType ?: return false
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return when (type and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_FILTER,
            InputType.TYPE_TEXT_VARIATION_PERSON_NAME, InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_PHONETIC -> false
            else -> true
        }
    }

    private fun start() {
        if (!enabled() || !fieldFits()) return
        val model = LanguageModels.installed(ime) ?: return
        val before = ime.currentInputConnection
            ?.getTextBeforeCursor(WritingPrompts.COMPLETION_CONTEXT_CHARS + 50, 0)?.toString() ?: return
        if (!WritingPrompts.wantsCompletion(before)) return
        waiting = before
        LanguageModelEngine.generate(model, WritingPrompts.completionPrompt(before), WritingPrompts.COMPLETION_TOKENS, true) { answer ->
            if (waiting != before) return@generate
            waiting = null
            val words = WritingPrompts.cleanCompletion(answer) ?: return@generate
            // Only if nothing changed while the model was thinking.
            val now = ime.currentInputConnection
                ?.getTextBeforeCursor(WritingPrompts.COMPLETION_CONTEXT_CHARS + 50, 0)?.toString()
            if (now != before) return@generate
            ime.ztShowSuggestionChip(view(words, ime.ztSuggestionStrip()))
        }
    }

    private fun view(words: String, parent: ViewGroup?): View? {
        if (parent == null) return null
        val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(ime), parent, false)
        val textView = binding.clipboardSuggestionText
        val icon = ContextCompat.getDrawable(ime, R.drawable.ic_zt_writing)?.mutate()
        icon?.setBounds(0, 0, textView.lineHeight, textView.lineHeight)
        textView.setCompoundDrawablesRelative(icon, null, null, null)
        KeyboardTypeface.applyToTextView(textView)
        textView.text = words
        textView.contentDescription = ime.getString(R.string.zt_writing_finish_spoken, words)
        textView.setOnClickListener {
            binding.root.isGone = true
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            ime.onTextInput("$words ")
        }
        val closeButton = binding.clipboardSuggestionClose
        closeButton.setImageDrawable(KeyboardIconsSet.instance.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.contentDescription = ime.getString(R.string.zt_writing_finish_dismiss)
        closeButton.layoutParams.width = textView.lineHeight
        closeButton.layoutParams.height = textView.lineHeight
        closeButton.setOnClickListener {
            binding.root.isGone = true
            ime.removeExternalSuggestions()
        }
        val colors = Settings.getValues().mColors
        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
        icon?.let { colors.setColor(it, ColorType.CLIPBOARD_SUGGESTION_ICON) }
        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)
        return binding.root
    }

    companion object {
        /** How long the user has to stop typing before the model is asked. */
        const val PAUSE_MILLIS = 600L
    }
}
