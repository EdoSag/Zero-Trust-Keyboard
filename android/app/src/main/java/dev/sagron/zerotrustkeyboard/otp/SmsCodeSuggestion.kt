// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.otp

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.prefs

/**
 * The chip in the suggestion strip that types a one-time code from a recent text message.
 * It looks like the clipboard suggestion and sits where it does, ahead of it.
 */
object SmsCodeSuggestion {
    /**
     * Whether a code makes sense in this text field: a number field takes one any time, other
     * one-line fields only while still empty. Long-message fields never get one.
     */
    fun fits(editorInfo: EditorInfo?, fieldIsEmpty: Boolean): Boolean {
        val type = editorInfo?.inputType ?: return false
        val inputClass = type and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_NUMBER) return true
        if (inputClass != InputType.TYPE_CLASS_TEXT) return false
        if (type and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) return false
        return fieldIsEmpty
    }

    /** The chip for this text field, or null when there is no code to offer here. */
    fun view(ime: LatinIME, editorInfo: EditorInfo?, parent: ViewGroup?, fieldIsEmpty: Boolean): View? {
        if (parent == null) return null
        if (!ime.prefs().getBoolean(Settings.PREF_ZT_SMS_CODES, Defaults.PREF_ZT_SMS_CODES)) return null
        if (Settings.getValues().mIsLocked) return null
        if (!fits(editorInfo, fieldIsEmpty)) return null
        // Never offer a code inside the messaging app it came from.
        if (editorInfo?.packageName == android.provider.Telephony.Sms.getDefaultSmsPackage(ime)) return null
        val found = SmsCodes.latest(ime) ?: return null

        val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(ime), parent, false)
        val textView = binding.clipboardSuggestionText
        val icon = ContextCompat.getDrawable(ime, R.drawable.ic_zt_sms_code)?.mutate()
        icon?.setBounds(0, 0, textView.lineHeight, textView.lineHeight)
        textView.setCompoundDrawablesRelative(icon, null, null, null)
        KeyboardTypeface.applyToTextView(textView)
        textView.text = found.code
        textView.contentDescription = ime.getString(R.string.zt_sms_code_spoken, found.code.toCharArray().joinToString(" "))
        textView.setOnClickListener {
            SmsCodes.markDone(found.messageId)
            ime.onTextInput(found.code)
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            binding.root.isGone = true
        }

        val closeButton = binding.clipboardSuggestionClose
        closeButton.setImageDrawable(KeyboardIconsSet.instance.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.contentDescription = ime.getString(R.string.zt_sms_code_dismiss)
        closeButton.layoutParams.width = textView.lineHeight
        closeButton.layoutParams.height = textView.lineHeight
        closeButton.setOnClickListener {
            SmsCodes.markDone(found.messageId)
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
}
