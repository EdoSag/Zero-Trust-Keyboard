// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingValuesTest {
    @Test fun `typing options sync, this phone's own settings stay`() {
        assertTrue(SettingValues.syncs("auto_cap"))
        assertTrue(SettingValues.syncs("theme_colors"))
        assertTrue(SettingValues.syncs("enabled_subtypes"))
        assertTrue(SettingValues.syncs("user_colors_mine"))
        assertFalse(SettingValues.syncs("zt_voice_model"))
        assertFalse(SettingValues.syncs("selected_subtype"))
        assertFalse(SettingValues.syncs("version_code"))
        assertFalse(SettingValues.syncs("keyboard_height_scale_portrait"))
        assertFalse(SettingValues.syncs("floating_pos_x_landscape"))
        assertFalse(SettingValues.syncs("one_handed_mode_enabled_p_true"))
        assertFalse(SettingValues.syncs("saved_app_subtype_com.example"))
        assertFalse(SettingValues.syncs("layout_qwerty"))
        assertFalse(SettingValues.syncs(""))
    }

    @Test fun `every type comes back as the type it left with`() {
        val values = listOf<Pair<String, Any>>(
            "auto_cap" to true,
            "key_longpress_timeout" to 300,
            "some_time" to 1_700_000_000_000L,
            "font_scale" to 0.35f,
            "theme_colors" to "ocean",
            "pinned_keys" to setOf("b", "a"),
        )
        for ((name, value) in values) {
            val (typedName, sent) = SettingValues.encode(name, value)!!
            assertEquals(name to value, SettingValues.decode(typedName, sent))
            assertEquals(value::class, SettingValues.decode(typedName, sent)!!.second::class)
        }
    }

    @Test fun `whole numbers survive the channel, which hands back Int or Long as it likes`() {
        assertEquals("key_longpress_timeout" to 300, SettingValues.decode("i:key_longpress_timeout", 300L))
        assertEquals("some_time" to 5L, SettingValues.decode("l:some_time", 5))
        assertNull(SettingValues.decode("i:key_longpress_timeout", 5_000_000_000L))
        assertNull(SettingValues.decode("i:key_longpress_timeout", 1.5))
    }

    @Test fun `a float travels as text and comes back exactly`() {
        val (typedName, sent) = SettingValues.encode("font_scale", 0.35f)!!
        assertEquals("0.35", sent)
        assertEquals(0.35f, SettingValues.decode(typedName, sent)!!.second)
        assertNull(SettingValues.decode("f:font_scale", "NaN"))
        assertNull(SettingValues.decode("f:font_scale", 0.35))
    }

    @Test fun `a string set travels as a sorted list`() {
        assertEquals("S:pinned_keys" to listOf("a", "b"), SettingValues.encode("pinned_keys", setOf("b", "a")))
        assertNull(SettingValues.decode("S:pinned_keys", listOf("a", 1)))
    }

    @Test fun `a value of the wrong type is never written`() {
        assertNull(SettingValues.decode("b:auto_cap", "true"))
        assertNull(SettingValues.decode("s:theme_colors", 4))
        assertNull(SettingValues.decode("x:auto_cap", true))
        assertNull(SettingValues.decode("auto_cap", true))
        assertNull(SettingValues.decode("b:", true))
        assertNull(SettingValues.encode("auto_cap", null))
        assertNull(SettingValues.encode("auto_cap", 1.5))
    }

    @Test fun `a language list with a layout the user made is held, not sent or taken`() {
        val own = "en;KeyboardLayoutSet=MAIN:custom.mine."
        assertTrue(SettingValues.held("additional_subtypes", own))
        assertTrue(SettingValues.held("enabled_subtypes", own))
        assertFalse(SettingValues.held("additional_subtypes", "en;KeyboardLayoutSet=MAIN:qwerty"))
        assertFalse(SettingValues.held("space_bar_text", "custom.text"))
        assertNull(SettingValues.encode("additional_subtypes", own))
        assertNull(SettingValues.decode("s:additional_subtypes", own))
        assertEquals(
            "s:additional_subtypes" to "en;KeyboardLayoutSet=MAIN:qwerty",
            SettingValues.encode("additional_subtypes", "en;KeyboardLayoutSet=MAIN:qwerty"),
        )
    }

    @Test fun `a setting that stays is refused in both directions`() {
        assertNull(SettingValues.encode("zt_voice_model", "large"))
        assertNull(SettingValues.decode("s:zt_voice_model", "large"))
        assertNull(SettingValues.nameOf("s:zt_voice_model"))
        assertEquals("auto_cap", SettingValues.nameOf("b:auto_cap"))
    }
}
