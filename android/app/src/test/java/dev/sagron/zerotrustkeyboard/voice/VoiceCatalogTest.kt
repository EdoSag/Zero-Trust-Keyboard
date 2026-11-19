// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCatalogTest {
    private val hash = "ab".repeat(32)

    @Test fun theListIsReadLineByLine() {
        val offers = VoiceCatalog.parse(
            "# file\tbytes\tsha256\tlanguages\tEnglish name\tHebrew name\n" +
                "ggml-ivrit-large-v3-turbo-q5_0.he.bin\t574000000\t$hash\the\tHebrew, recommended\tעברית, מומלץ\r\n" +
                "ggml-small-q5_1.bin\t190085487\t${hash.uppercase()}\t-\tAll languages\n" +
                "\n"
        )
        assertEquals(2, offers.size)
        assertEquals("ggml-ivrit-large-v3-turbo-q5_0.he.bin", offers[0].file)
        assertEquals(574000000L, offers[0].bytes)
        assertEquals(listOf("he"), offers[0].languages)
        assertEquals("עברית, מומלץ", offers[0].label(hebrew = true))
        assertEquals("Hebrew, recommended", offers[0].label(hebrew = false))
        assertEquals(hash, offers[1].sha256)
        assertTrue(offers[1].languages.isEmpty())
        // No Hebrew name: the English one is shown.
        assertEquals("All languages", offers[1].label(hebrew = true))
    }

    @Test fun linesThatCouldHarmAreSkipped() {
        fun line(file: String = "a.bin", bytes: String = "5000000", sha: String = hash, languages: String = "-") =
            "$file\t$bytes\t$sha\t$languages\tName\tשם"
        assertEquals(1, VoiceCatalog.parse(line()).size)
        assertTrue(VoiceCatalog.parse(line(file = "../a.bin")).isEmpty())
        assertTrue(VoiceCatalog.parse(line(file = "folder/a.bin")).isEmpty())
        assertTrue(VoiceCatalog.parse(line(file = "a.apk")).isEmpty())
        assertTrue(VoiceCatalog.parse(line(bytes = "0")).isEmpty())
        assertTrue(VoiceCatalog.parse(line(bytes = "many")).isEmpty())
        assertTrue(VoiceCatalog.parse(line(sha = "abc")).isEmpty())
        assertTrue(VoiceCatalog.parse(line(languages = "He")).isEmpty())
        assertTrue(VoiceCatalog.parse("a.bin\t5000000\t$hash").isEmpty())
        assertTrue(VoiceCatalog.parse("<html>Not found</html>").isEmpty())
    }

    @Test fun theLanguageOfAModelIsInItsName() {
        assertEquals("he", VoiceCatalog.languageOf("ggml-ivrit-large-v3-turbo-q5_0.he.bin"))
        assertEquals("en", VoiceCatalog.languageOf("ggml-base.en.bin"))
        assertNull(VoiceCatalog.languageOf("ggml-small-q5_1.bin"))
        assertNull(VoiceCatalog.languageOf("ggml-large-v3-turbo.bin"))
        assertNull(VoiceCatalog.languageOf("my.model.v2.bin"))
        assertNull(VoiceCatalog.languageOf("model.bin"))
    }

    @Test fun aTunedModelIsUsedForItsLanguageOnly() {
        val tuned = "ggml-ivrit-large-v3-turbo-q5_0.he.bin"
        val small = "ggml-small-q5_1.bin"
        val large = "ggml-large-v3-turbo-q5_0.bin"
        val all = listOf(tuned, large, small)
        assertEquals(tuned, VoiceCatalog.choose(all, emptyMap(), "he"))
        // Hebrew keeps its tuned model whatever is picked for the other languages.
        assertEquals(tuned, VoiceCatalog.choose(all, mapOf("" to small), "he"))
        assertEquals(small, VoiceCatalog.choose(all, mapOf("" to small), "en"))
        assertEquals(large, VoiceCatalog.choose(all, emptyMap(), "en"))
        assertEquals(large, VoiceCatalog.choose(all, emptyMap(), "auto"))
        // Without a tuned model Hebrew uses the model for every language.
        assertEquals(small, VoiceCatalog.choose(listOf(large, small), mapOf("" to small), "he"))
        // Only a model for another language: better than no voice typing.
        assertEquals(tuned, VoiceCatalog.choose(listOf(tuned), emptyMap(), "en"))
        assertNull(VoiceCatalog.choose(emptyList(), emptyMap(), "en"))
    }

    @Test fun thePickAmongTunedModelsIsKept() {
        val quantized = "ggml-ivrit-large-v3-turbo-q5_0.he.bin"
        val full = "ggml-ivrit-large-v3-turbo.he.bin"
        val small = "ggml-small-q5_1.bin"
        val all = listOf(quantized, full, small)
        assertEquals(full, VoiceCatalog.choose(all, mapOf("he" to full), "he"))
        assertEquals(setOf(full, small), VoiceCatalog.picks(all, mapOf("he" to full)))
        assertEquals(setOf(quantized, small), VoiceCatalog.picks(all, emptyMap()))
        // A pick that was deleted falls back to the first of the group.
        assertEquals(setOf(quantized, small), VoiceCatalog.picks(all, mapOf("he" to "gone.he.bin", "" to "gone.bin")))
    }
}
