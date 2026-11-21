// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.dictionary

import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.latin.App
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.makedict.DictIO
import helium314.keyboard.latin.makedict.WordProperty
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The emoji dictionaries in the app (Hebrew and English) are built from the word lists in
 * /dictionaries, which come from Unicode CLDR. They are what emoji search, emoji suggestions
 * and emoji names read, so the keyboard needs no download for them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class])
class BundledEmojiDictionaryTest {
    // Unit tests run in android/app.
    private fun wordList(language: String) = File("../../dictionaries/emoji_${language}_wordlist.combined.gz")
    private fun bundled(language: String) = File("src/main/assets/dicts/emoji_$language.dict")
    private fun words(language: String): Map<String, WordProperty> {
        val list = DictIO.readCompressedCombinedDictionary(wordList(language).path)
        assertEquals("emoji:$language", list.mOptions.mAttributes["dictionary"])
        return list.associateBy { it.mWord }
    }

    /** What a word finds, best first. */
    private fun Map<String, WordProperty>.shortcutsOf(word: String): List<String> =
        this[word]?.mShortcutTargets.orEmpty().sortedByDescending { it.probability }.map { it.mWord }

    @Test fun aHebrewWordFindsItsEmoji() {
        val hebrew = words("he")
        assertEquals(setOf("🐕", "🐶"), hebrew.shortcutsOf("כלב").take(2).toSet())
        assertEquals("❤️", hebrew.shortcutsOf("לב").first())
        assertTrue(hebrew.shortcutsOf("ישראל").contains("🇮🇱"))
        assertTrue(hebrew.shortcutsOf("פיצה").contains("🍕"))
        // "סטודנט/ית" is one word with an ending, the ending alone finds nothing.
        assertTrue(hebrew.shortcutsOf("ית").isEmpty())
    }

    @Test fun anEnglishWordFindsItsEmoji() {
        val english = words("en")
        assertEquals("❤️", english.shortcutsOf("heart").first())
        assertTrue(english.shortcutsOf("dog").contains("🐶"))
        assertTrue(english.shortcutsOf("israel").contains("🇮🇱"))
        assertTrue(english.shortcutsOf("pizza").contains("🍕"))
    }

    /** The name shown on a long press is the one shortcut of the emoji itself. */
    @Test fun anEmojiHasItsName() {
        assertEquals(listOf("פיצה"), words("he").shortcutsOf("🍕"))
        assertEquals(listOf("pizza"), words("en").shortcutsOf("🍕"))
        // An emoji is never offered as a word of its own.
        assertTrue(words("he").getValue("🍕").mIsNotAWord)
        assertFalse(words("he").getValue("פיצה").mIsNotAWord)
    }

    @Test fun noWordFindsMoreEmojiThanTheStripShows() {
        for (language in listOf("he", "en")) {
            for (word in words(language).values) {
                assertTrue(word.mWord, (word.mShortcutTargets?.size ?: 0) <= 18)
            }
        }
    }

    @Test fun theBundledDictionariesAreBuiltFromTheWordLists() {
        for (language in listOf("he", "en")) {
            assertTrue(bundled(language).path, bundled(language).isFile)
            val built = File.createTempFile("emoji_$language", ".dict")
            try {
                DictIO.writeBinaryDictionary(built.path, DictIO.readCompressedCombinedDictionary(wordList(language).path))
                assertArrayEquals(
                    "emoji_$language.dict is not what the word list builds; build it again as dictionaries/README.md says",
                    built.readBytes(), bundled(language).readBytes(),
                )
            } finally {
                built.delete()
            }
        }
    }

    /** Emoji search asks for "the emoji dictionary of this language"; the bundled one answers. */
    @Test fun theKeyboardFindsTheBundledDictionaryOfItsLanguage() {
        val context = ApplicationProvider.getApplicationContext<App>()
        val hebrew = DictionaryInfoUtils.getCachedDictForLocaleAndType("he".constructLocale(), Dictionary.TYPE_EMOJI, context)
        assertNotNull(hebrew)
        assertArrayEquals(bundled("he").readBytes(), hebrew!!.readBytes())
        assertTrue(DictionaryInfoUtils.isEmojiDictionaryFile(hebrew.name))

        val english = DictionaryInfoUtils.getCachedDictForLocaleAndType("en-US".constructLocale(), Dictionary.TYPE_EMOJI, context)
        assertNotNull(english)
        assertArrayEquals(bundled("en").readBytes(), english!!.readBytes())

        // Asking again gives the same file, not a second copy.
        assertEquals(hebrew, DictionaryInfoUtils.getCachedDictForLocaleAndType("he".constructLocale(), Dictionary.TYPE_EMOJI, context))
        // A language without a bundled emoji dictionary has none, and no other kind is made up.
        assertNull(DictionaryInfoUtils.getCachedDictForLocaleAndType("de".constructLocale(), Dictionary.TYPE_EMOJI, context))
        assertNull(DictionaryInfoUtils.getCachedDictForLocaleAndType("he".constructLocale(), "contacts", context))
        assertFalse(DictionaryInfoUtils.isEmojiDictionaryFile("main.dict"))
        assertTrue(DictionaryInfoUtils.isEmojiDictionaryFile("emoji_user.dict"))
    }
}
