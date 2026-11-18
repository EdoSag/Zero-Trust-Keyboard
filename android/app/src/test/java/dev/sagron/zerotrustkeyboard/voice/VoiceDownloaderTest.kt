// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest

class VoiceDownloaderTest {
    private lateinit var directory: File

    /** A model of 300 000 bytes: the ggml first bytes, then a pattern. */
    private val model = ByteArray(300_000) { (it * 31 % 251).toByte() }.also {
        byteArrayOf(0x6c, 0x6d, 0x67, 0x67).copyInto(it)
    }
    private val offer = offerFor(model)
    private val target get() = File(directory, offer.file)
    private val partial get() = VoiceDownloader.partialFile(directory, offer.file)

    private fun offerFor(bytes: ByteArray) = VoiceOffer(
        "ggml-test.bin", bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        emptyList(), "Test", "",
    )

    /** A server with [bytes] that can continue from any place, and counts what it was asked. */
    private class Server(val bytes: ByteArray, val canContinue: Boolean = true, val breakAfter: Int = -1) {
        val asked = ArrayList<Long>()
        fun open(url: String, from: Long): VoiceDownloader.Source {
            asked.add(from)
            val start = if (canContinue) from.toInt() else 0
            val rest = ByteArrayInputStream(bytes, start, bytes.size - start)
            val stream = if (breakAfter < 0) rest else object : InputStream() {
                var sent = 0
                override fun read(): Int = throw UnsupportedOperationException()
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (sent >= breakAfter) throw IOException("connection lost")
                    val count = rest.read(buffer, offset, minOf(length, breakAfter - sent))
                    if (count > 0) sent += count
                    return count
                }
            }
            return VoiceDownloader.Source(stream, start.toLong())
        }
    }

    @Before fun setUp() {
        directory = Files.createTempDirectory("zt-voice").toFile()
    }

    @After fun tearDown() {
        directory.deleteRecursively()
    }

    private fun download(server: Server, cancelled: () -> Boolean = { false }, onProgress: (Long) -> Unit = {}) =
        VoiceDownloader(server::open).download(offer, directory, cancelled, onProgress)

    @Test fun aDownloadEndsAsTheModelFile() {
        val server = Server(model)
        var last = 0L
        assertEquals(VoiceDownloader.Result.OK, download(server) { last = it })
        assertArrayEquals(model, target.readBytes())
        assertFalse(partial.exists())
        assertEquals(model.size.toLong(), last)
        assertEquals(listOf(0L), server.asked)
    }

    @Test fun aLostConnectionContinuesWhereItStopped() {
        assertEquals(VoiceDownloader.Result.NO_CONNECTION, download(Server(model, breakAfter = 100_000)))
        assertFalse(target.exists())
        assertEquals(100_000L, partial.length())
        val second = Server(model)
        assertEquals(VoiceDownloader.Result.OK, download(second))
        assertEquals(listOf(100_000L), second.asked)
        assertArrayEquals(model, target.readBytes())
    }

    @Test fun aServerThatCannotContinueStartsOver() {
        assertEquals(VoiceDownloader.Result.NO_CONNECTION, download(Server(model, breakAfter = 100_000)))
        assertEquals(VoiceDownloader.Result.OK, download(Server(model, canContinue = false)))
        assertArrayEquals(model, target.readBytes())
    }

    @Test fun aFileWithAnotherHashIsRemoved() {
        val other = model.copyOf().also { it[200_000] = 7 }
        assertEquals(VoiceDownloader.Result.WRONG_FILE, download(Server(other)))
        assertFalse(target.exists())
        assertFalse(partial.exists())
    }

    @Test fun aFileThatIsTooLongIsRemoved() {
        assertEquals(VoiceDownloader.Result.WRONG_FILE, download(Server(model + ByteArray(10))))
        assertFalse(target.exists())
        assertFalse(partial.exists())
    }

    @Test fun aShortAnswerIsNotAccepted() {
        // The connection closes cleanly before the end, as an error page would.
        assertEquals(VoiceDownloader.Result.NO_CONNECTION, download(Server(model.copyOf(1000))))
        assertFalse(target.exists())
    }

    @Test fun aDamagedPartIsFoundByTheHash() {
        partial.writeBytes(ByteArray(50_000) { 1 })
        assertEquals(VoiceDownloader.Result.WRONG_FILE, download(Server(model)))
        assertFalse(partial.exists())
        // The next try starts clean and succeeds.
        assertEquals(VoiceDownloader.Result.OK, download(Server(model)))
    }

    @Test fun cancellingStopsWithoutAModel() {
        var seen = 0L
        val result = download(Server(model), cancelled = { seen >= 65_536 }) { seen = it }
        assertEquals(VoiceDownloader.Result.CANCELLED, result)
        assertFalse(target.exists())
        assertTrue(partial.length() < model.size)
    }

    @Test fun noConnectionLeavesNothingBehind() {
        val result = VoiceDownloader { _, _ -> null }.download(offer, directory, { false }) {}
        assertEquals(VoiceDownloader.Result.NO_CONNECTION, result)
        assertFalse(target.exists())
    }

    @Test fun aFinishedPartOnlyNeedsItsCheck() {
        partial.writeBytes(model)
        val server = Server(model)
        assertEquals(VoiceDownloader.Result.OK, download(server))
        assertTrue(server.asked.isEmpty())
        assertArrayEquals(model, target.readBytes())
    }

    @Test fun aNewDownloadReplacesAnOlderModelOfTheSameName() {
        target.writeBytes(ByteArray(10))
        assertEquals(VoiceDownloader.Result.OK, download(Server(model)))
        assertArrayEquals(model, target.readBytes())
    }
}
