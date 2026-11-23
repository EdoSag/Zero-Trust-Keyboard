// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.otp

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import dev.sagron.zerotrustkeyboard.insert.InsertActivity
import dev.sagron.zerotrustkeyboard.insert.ZtApps
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

/**
 * The chip "Code from ZT Authenticator" in the suggestion strip, on fields that ask for a
 * one-time code. A tap opens ZT Authenticator (which unlocks as usual), and the code it
 * hands back is typed in this field, without being learned or put on the clipboard.
 * It is only shown when ZT Authenticator is on the phone and signed with our key, and not
 * while the field already shows an autofill suggestion (ZT Authenticator can offer the code
 * that way too).
 */
object AuthenticatorCodeSuggestion {
    private val CODE_HINT = Regex(
        "code|otp|one[- ]time|2fa|two[- ]factor|verif|token|authenticat|קוד|אימות|הזדהות",
        RegexOption.IGNORE_CASE,
    )

    /** Fields dismissed by the user, as "package/fieldId": no chip there again until the keyboard restarts. */
    private val dismissed = HashSet<String>()

    /**
     * Whether the field asks for a one-time code: an empty one-line field whose hint or label
     * says so, or an empty numeric password field (the usual shape of a code box).
     */
    fun fits(inputType: Int, hint: CharSequence?, label: CharSequence?, fieldIsEmpty: Boolean): Boolean {
        if (!fieldIsEmpty) return false
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        if (inputClass != InputType.TYPE_CLASS_NUMBER && inputClass != InputType.TYPE_CLASS_TEXT) return false
        if (inputClass == InputType.TYPE_CLASS_TEXT && inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) return false
        if (inputClass == InputType.TYPE_CLASS_NUMBER &&
            inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return true
        return listOfNotNull(hint, label).any { CODE_HINT.containsMatchIn(it) }
    }

    /** The chip for this text field, or null when it does not belong here. */
    fun view(ime: LatinIME, editorInfo: EditorInfo?, parent: ViewGroup?, fieldIsEmpty: Boolean, autofillShown: Boolean): View? {
        if (parent == null || editorInfo == null || autofillShown) return null
        if (Settings.getValues().mIsLocked) return null
        if (!fits(editorInfo.inputType, editorInfo.hintText, editorInfo.label, fieldIsEmpty)) return null
        val key = "${editorInfo.packageName}/${editorInfo.fieldId}"
        if (key in dismissed) return null
        if (editorInfo.packageName == ZtApps.AUTHENTICATOR_PACKAGE) return null
        val source = ZtApps.authenticator(ime) ?: return null

        val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(ime), parent, false)
        val textView = binding.clipboardSuggestionText
        val icon = ContextCompat.getDrawable(ime, R.drawable.ic_zt_authenticator_code)?.mutate()
        icon?.setBounds(0, 0, textView.lineHeight, textView.lineHeight)
        textView.setCompoundDrawablesRelative(icon, null, null, null)
        KeyboardTypeface.applyToTextView(textView)
        textView.text = ime.getString(R.string.zt_authenticator_code, source.label)
        textView.setOnClickListener {
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            binding.root.isGone = true
            ime.removeExternalSuggestions()
            InsertActivity.startCode(ime, editorInfo)
        }

        val closeButton = binding.clipboardSuggestionClose
        closeButton.setImageDrawable(KeyboardIconsSet.instance.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.contentDescription = ime.getString(R.string.zt_authenticator_code_dismiss)
        closeButton.layoutParams.width = textView.lineHeight
        closeButton.layoutParams.height = textView.lineHeight
        closeButton.setOnClickListener {
            dismissed.add(key)
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
