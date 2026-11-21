// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateCatalogTest {
    // The real list and its signature, as published on 2026-11-17.
    private val list = resource("models.json")
    private val signature = String(resource("models.json.sig"), Charsets.UTF_8)

    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/translate/$name")!!.use { it.readBytes() }

    private val models = TranslateCatalog.verified(list, signature)!!

    @Test fun `the published list is signed with the key the keyboard trusts`() {
        assertEquals(104, models.size)
        val toHebrew = models.getValue("en-he")
        assertEquals("en", toHebrew.from)
        assertEquals("he", toHebrew.to)
        assertEquals(setOf("model", "lex", "vocab"), toHebrew.files.keys)
        assertEquals("en-he.model.bin.gz", toHebrew.files.getValue("model").asset)
        assertEquals("model.enhe.intgemm.alphas.bin", toHebrew.files.getValue("model").name)
        assertEquals(14_958_840L, toHebrew.downloadBytes)
        assertEquals(21_052_116L, toHebrew.diskBytes)
    }

    @Test fun `a changed list or another key is refused`() {
        val changed = list.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertNull(TranslateCatalog.verified(changed, signature))
        assertNull(TranslateCatalog.verified(list, signature, ByteArray(32) { 7 }))
        assertNull(TranslateCatalog.verified(list, "not a signature"))
        assertNull(TranslateCatalog.verified(list, ""))
    }

    @Test fun `a pair goes directly or through English`() {
        assertEquals(listOf("he-en"), TranslateCatalog.route(models, "he", "en")?.map { it.pair })
        assertEquals(listOf("en-he"), TranslateCatalog.route(models, "en", "he")?.map { it.pair })
        assertEquals(listOf("he-en", "en-fr"), TranslateCatalog.route(models, "he", "fr")?.map { it.pair })
        assertNull(TranslateCatalog.route(models, "he", "he"))
        assertNull(TranslateCatalog.route(models, "he", "xx"))
        // Russian only translates to English in the list, so it is not offered as a language to write in.
        assertNull(TranslateCatalog.route(models, "en", "ru"))
        val languages = TranslateCatalog.languages(models)
        assertTrue("he" in languages && "en" in languages && "fr" in languages)
        assertTrue("ru" !in languages)
        assertEquals(languages.distinct(), languages)
    }

    @Test fun `the list kept on the phone reads back the same`() {
        val kept = listOf(models.getValue("en-he"), models.getValue("he-en"))
        val again = TranslateCatalog.parse(TranslateCatalog.write(kept))
        assertEquals(kept, again?.values?.toList())
        assertEquals(emptyMap<String, TranslateModel>(), TranslateCatalog.parse(TranslateCatalog.write(emptyList())))
    }

    @Test fun `the engine is told where each file is and how the model is packed`() {
        val registry = TranslateCatalog.engineRegistry(listOf(models.getValue("en-he")), "https://translate.zt/models")
        assertTrue(registry, registry.contains("\"name\":\"https://translate.zt/models/en-he/model.enhe.intgemm.alphas.bin\""))
        assertTrue(registry, registry.contains("\"gemm-precision\":\"int8shiftAlphaAll\""))
        assertTrue(registry, registry.startsWith("[{\"from\":\"en\",\"to\":\"he\""))
        assertEquals("[]", TranslateCatalog.engineRegistry(emptyList(), "x"))
    }

    @Test fun `names that could leave the folder and entries that are not whole are left out`() {
        fun entry(asset: String, name: String, size: Long = 10) =
            """{"asset":"$asset","name":"$name","gzSize":5,"size":$size,"sha256":"${"a".repeat(64)}"}"""
        fun list(file: String, pair: String = "en-he", from: String = "en", to: String = "he") =
            """{"version":1,"models":{"$pair":{"from":"$from","to":"$to","files":{"model":$file,"lex":$file,"vocab":$file}}}}"""
        assertEquals(1, TranslateCatalog.parse(list(entry("a.gz", "a.bin")))?.size)
        assertEquals(0, TranslateCatalog.parse(list(entry("../a.gz", "a.bin")))?.size)
        assertEquals(0, TranslateCatalog.parse(list(entry("a.gz", "dir/a.bin")))?.size)
        assertEquals(0, TranslateCatalog.parse(list(entry("a.gz", ".hidden")))?.size)
        assertEquals(0, TranslateCatalog.parse(list(entry("a.gz", "a.bin", size = 1L shl 40)))?.size)
        assertEquals(0, TranslateCatalog.parse(list(entry("a.gz", "a.bin"), pair = "en-fr"))?.size)
        assertEquals(0, TranslateCatalog.parse(list(entry("a.gz", "a.bin"), pair = "../x-he", from = "../x"))?.size)
        assertNull(TranslateCatalog.parse("""{"version":2,"models":{}}"""))
        assertNull(TranslateCatalog.parse("not json"))
        assertNull(TranslateCatalog.parse("[]"))
    }

    @Test fun `keyboard languages map to translation codes and names`() {
        assertEquals("he", TranslateCatalog.code("iw"))
        assertEquals("he", TranslateCatalog.code("he-IL"))
        assertEquals("en", TranslateCatalog.code("en_US"))
        assertEquals("עברית", TranslateCatalog.name("he"))
        assertEquals("xx", TranslateCatalog.name("xx"))
        assertNotNull(TranslateCatalog.base64("AAECAw=="))
        assertEquals(listOf<Byte>(0, 1, 2, 3), TranslateCatalog.base64("AAECAw==")!!.toList())
        assertNull(TranslateCatalog.base64("AAE=AAAA"))
        assertNull(TranslateCatalog.base64("AAECA"))
    }
}
