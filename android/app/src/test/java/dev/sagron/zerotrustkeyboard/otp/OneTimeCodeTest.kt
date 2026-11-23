// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneTimeCodeTest {
    @Test fun findsTheCodeInEverydayMessages() {
        assertEquals("482913", OneTimeCode.find("Your verification code is 482913. Do not share it."))
        assertEquals("482913", OneTimeCode.find("קוד האימות שלך הוא: 482913"))
        assertEquals("4829", OneTimeCode.find("4829 is your login code"))
        assertEquals("482913", OneTimeCode.find("G-482913 is your Google verification code."))
        assertEquals("482913", OneTimeCode.find("Your code: 482 913"))
        assertEquals("482913", OneTimeCode.find("Your code: 482-913"))
        assertEquals("12345678", OneTimeCode.find("Bank: one-time password 12345678, valid for 10 minutes"))
        assertEquals("5531", OneTimeCode.find("קוד:5531"))
        assertEquals("773201", OneTimeCode.find("סיסמה חד פעמית לכניסה: 773201 תקפה ל-5 דקות"))
    }

    @Test fun prefersTheNumberRightAfterTheWordCode() {
        assertEquals("482913", OneTimeCode.find("Order 55667788 shipped. Your code is 482913"))
        assertEquals("482913", OneTimeCode.find("Use code 482913 to confirm order 55667788"))
        assertEquals("482913", OneTimeCode.find("To log in to account 1234, use code 482913"))
        assertEquals("482913", OneTimeCode.find("כניסה לחשבון 5566: הקוד שלך הוא 482913"))
        assertEquals("482913", OneTimeCode.find("Transaction 7788 needs verification. Code: 482913"))
    }

    @Test fun ignoresMessagesWithoutACode() {
        assertNull(OneTimeCode.find("Meet you at 1830 near the station"))
        assertNull(OneTimeCode.find("נתראה מחר"))
        assertNull(OneTimeCode.find(""))
        assertNull(OneTimeCode.find(null))
    }

    @Test fun ignoresMoneyPhoneNumbersDatesAndTimes() {
        assertNull(OneTimeCode.find("Your code expires at 10:30. Call 054-123-4567"))
        assertNull(OneTimeCode.find("Your PIN payment of 1500 ₪ was approved"))
        assertNull(OneTimeCode.find("Your PIN payment of ₪1500 was approved"))
        assertNull(OneTimeCode.find("Your PIN change on 22/11/2026 was approved"))
        assertNull(OneTimeCode.find("Login from +972541234567"))
        assertNull(OneTimeCode.find("Code valid until Nov 2026"))
        assertEquals("482913", OneTimeCode.find("Your code is 482913. Questions? Call 054-1234567"))
    }
}
