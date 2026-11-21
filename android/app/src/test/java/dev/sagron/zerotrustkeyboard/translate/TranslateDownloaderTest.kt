// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

class TranslateDownloaderTest {
    private val root: File = Files.createTempDirectory("zt-translate").toFile()

    @After fun cleanUp() {
        root.deleteRecursively()
    }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val contents = mapOf(
        "model" to ByteArray(200_000) { (it * 31).toByte() },
        "lex" to ByteArray(5_000) { (it * 7).toByte() },
        "vocab" to "vocabulary".toByteArray(),
    )
    private val packed = contents.mapValues { gzip(it.value) }
    private val model = TranslateModel("en", "he", contents.mapValues { (part, bytes) ->
        TranslateFile("en-he.$part.bin.gz", "$part.enhe.bin", packed.getValue(part).size.toLong(), bytes.size.toLong(), sha256(bytes))
    })

    /** A server holding [files] by address; counts what was asked for. */
    private class Server(val files: Map<String, ByteArray>) {
        val asked = ArrayList<String>()
        fun open(url: String): InputStream? {
            asked.add(url.removePrefix(TranslateCatalog.BASE_URL))
            return files[url]?.let { ByteArrayInputStream(it) }
        }
    }

    private fun server(change: (MutableMap<String, ByteArray>) -> Unit = {}): Server {
        val files = packed.mapKeys { TranslateCatalog.BASE_URL + "en-he.${it.key}.bin.gz" }.toMutableMap()
        change(files)
        return Server(files)
    }

    private fun download(server: Server, cancelled: () -> Boolean = { false }, onProgress: (Long) -> Unit = {}) =
        TranslateDownloader(server::open).download(model, root, cancelled, onProgress)

    private fun folder() = File(root, "en-he")

    @Test fun `a model is unpacked into its folder and checked`() {
        val progress = ArrayList<Long>()
        assertEquals(TranslateDownloader.Result.OK, download(server()) { progress.add(it) })
        for ((part, bytes) in contents) {
            assertTrue(part, File(folder(), "$part.enhe.bin").readBytes().contentEquals(bytes))
        }
        assertEquals(3, folder().list()!!.size)
        assertEquals(model.downloadBytes, progress.last())
        assertEquals(progress.sorted(), progress)
    }

    @Test fun `files already on the phone are not fetched again`() {
        assertEquals(TranslateDownloader.Result.OK, download(server()))
        // One file is damaged afterwards: only that one is fetched.
        File(folder(), "lex.enhe.bin").writeBytes(ByteArray(5_000))
        val again = server()
        assertEquals(TranslateDownloader.Result.OK, download(again))
        assertEquals(listOf("en-he.lex.bin.gz"), again.asked)
        assertTrue(File(folder(), "lex.enhe.bin").readBytes().contentEquals(contents.getValue("lex")))
    }

    @Test fun `a file that is not the one in the list is refused and not kept`() {
        val other = gzip(ByteArray(200_000) { 1 })
        val wrong = server { it[TranslateCatalog.BASE_URL + "en-he.model.bin.gz"] = other }
        // Same size once unpacked, other contents: the sha256 catches it.
        assertEquals(TranslateDownloader.Result.WRONG_FILE, download(wrong))
        assertFalse(File(folder(), "model.enhe.bin").exists())
        assertEquals(0, folder().list()!!.size)

        val longer = server { it[TranslateCatalog.BASE_URL + "en-he.model.bin.gz"] = gzip(ByteArray(300_000) { (it * 31).toByte() }) }
        assertEquals(TranslateDownloader.Result.WRONG_FILE, download(longer))
        val notPacked = server { it[TranslateCatalog.BASE_URL + "en-he.model.bin.gz"] = ByteArray(1000) { 9 } }
        assertEquals(TranslateDownloader.Result.WRONG_FILE, download(notPacked))
        assertEquals(0, folder().list()!!.size)
    }

    @Test fun `a broken connection and a cancel leave nothing half done`() {
        val cut = server { files ->
            val key = TranslateCatalog.BASE_URL + "en-he.model.bin.gz"
            files[key] = files.getValue(key).copyOf(files.getValue(key).size / 2)
        }
        assertEquals(TranslateDownloader.Result.NO_CONNECTION, download(cut))
        val missing = server { it.remove(TranslateCatalog.BASE_URL + "en-he.vocab.bin.gz") }
        assertEquals(TranslateDownloader.Result.NO_CONNECTION, download(missing))
        assertFalse(File(folder(), "vocab.enhe.bin").exists())
        root.deleteRecursively()
        assertEquals(TranslateDownloader.Result.CANCELLED, download(server(), cancelled = { true }))
        assertEquals(0, folder().list().orEmpty().size)
    }

    @Test fun `the list is only used with its signature`() {
        val list = javaClass.getResourceAsStream("/translate/models.json")!!.use { it.readBytes() }
        val signature = javaClass.getResourceAsStream("/translate/models.json.sig")!!.use { it.readBytes() }
        val good = Server(mapOf(TranslateCatalog.BASE_URL + "models.json" to list, TranslateCatalog.BASE_URL + "models.json.sig" to signature))
        assertEquals(104, TranslateDownloader(good::open).list()?.size)
        val changed = Server(good.files + (TranslateCatalog.BASE_URL + "models.json" to list + ' '.code.toByte()))
        assertNull(TranslateDownloader(changed::open).list())
        val unsigned = Server(mapOf(TranslateCatalog.BASE_URL + "models.json" to list))
        assertNull(TranslateDownloader(unsigned::open).list())
        val huge = Server(good.files + (TranslateCatalog.BASE_URL + "models.json" to ByteArray(3 shl 20)))
        assertNull(TranslateDownloader(huge::open).list())
    }
}
