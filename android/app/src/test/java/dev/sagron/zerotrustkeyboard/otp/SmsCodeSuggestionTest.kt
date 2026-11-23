// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.otp

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsCodeSuggestionTest {
    private fun field(type: Int) = EditorInfo().apply { inputType = type }

    @Test fun numberFieldsAlwaysTakeACode() {
        assertTrue(SmsCodeSuggestion.fits(field(InputType.TYPE_CLASS_NUMBER), fieldIsEmpty = false))
        assertTrue(SmsCodeSuggestion.fits(field(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD), fieldIsEmpty = true))
    }

    @Test fun oneLineTextFieldsTakeOneOnlyWhileEmpty() {
        val web = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT)
        assertTrue(SmsCodeSuggestion.fits(web, fieldIsEmpty = true))
        assertFalse(SmsCodeSuggestion.fits(web, fieldIsEmpty = false))
    }

    @Test fun messagesAndOtherFieldsNever() {
        val message = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        assertFalse(SmsCodeSuggestion.fits(message, fieldIsEmpty = true))
        assertFalse(SmsCodeSuggestion.fits(field(InputType.TYPE_CLASS_PHONE), fieldIsEmpty = true))
        assertFalse(SmsCodeSuggestion.fits(field(InputType.TYPE_CLASS_DATETIME), fieldIsEmpty = true))
        assertFalse(SmsCodeSuggestion.fits(null, fieldIsEmpty = true))
    }
}
