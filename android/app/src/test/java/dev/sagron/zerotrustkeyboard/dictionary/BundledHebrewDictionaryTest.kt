// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.dictionary

import helium314.keyboard.latin.makedict.DictIO
import helium314.keyboard.latin.makedict.FusionDictionary
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The Hebrew dictionary in the app is built from the word list in /dictionaries with the
 * keyboard's own dictionary builder. This builds it again and compares, so the file in the
 * app cannot drift from its source.
 */
@RunWith(RobolectricTestRunner::class)
class BundledHebrewDictionaryTest {
    // Unit tests run in android/app.
    private val wordList = File("../../dictionaries/iw_wordlist.combined.gz")
    private val bundled = File("src/main/assets/dicts/main_he.dict")

    private val words: FusionDictionary by lazy { DictIO.readCompressedCombinedDictionary(wordList.path) }

    @Test fun theWordListIsHebrewAndFull() {
        assertTrue(wordList.path, wordList.isFile)
        assertEquals("iw", words.mOptions.mAttributes["locale"])
        assertEquals("main:iw", words.mOptions.mAttributes["dictionary"])
        var count = 0
        for (word in words) count++
        assertEquals(94_799, count)
        for (common in listOf("של", "את", "לא", "שלום", "תודה", "בבית")) {
            assertNotNull(common, FusionDictionary.findWordInTree(words.mRootNodeArray, common))
        }
    }

    @Test fun theBundledDictionaryIsBuiltFromTheWordList() {
        assertTrue(bundled.path, bundled.isFile)
        val built = File.createTempFile("main_he", ".dict")
        try {
            DictIO.writeBinaryDictionary(built.path, words)
            assertArrayEquals(
                "main_he.dict is not what the word list builds; build it again as dictionaries/README.md says",
                built.readBytes(), bundled.readBytes(),
            )
        } finally {
            built.delete()
        }
    }
}
