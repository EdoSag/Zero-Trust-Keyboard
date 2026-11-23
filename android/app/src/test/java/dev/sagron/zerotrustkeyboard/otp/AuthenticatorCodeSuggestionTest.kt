// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.otp

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatorCodeSuggestionTest {
    private val number = InputType.TYPE_CLASS_NUMBER
    private val numberPassword = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    private val text = InputType.TYPE_CLASS_TEXT

    @Test fun codeFieldsGetTheChip() {
        assertTrue(AuthenticatorCodeSuggestion.fits(numberPassword, null, null, true))
        assertTrue(AuthenticatorCodeSuggestion.fits(number, "Enter the 6-digit code", null, true))
        assertTrue(AuthenticatorCodeSuggestion.fits(text, null, "Verification code", true))
        assertTrue(AuthenticatorCodeSuggestion.fits(text, "קוד אימות", null, true))
    }

    @Test fun otherFieldsDoNot() {
        // A plain number field (an amount, a phone number) says nothing about a code.
        assertFalse(AuthenticatorCodeSuggestion.fits(number, "Amount", null, true))
        assertFalse(AuthenticatorCodeSuggestion.fits(text, "Search", null, true))
        // Something is typed already.
        assertFalse(AuthenticatorCodeSuggestion.fits(numberPassword, null, null, false))
        // Long messages.
        assertFalse(AuthenticatorCodeSuggestion.fits(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE, "code", null, true))
        assertFalse(AuthenticatorCodeSuggestion.fits(InputType.TYPE_CLASS_DATETIME, "code", null, true))
    }
}
