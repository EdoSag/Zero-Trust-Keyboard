// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Downloads one speech model into a folder. A download that stopped continues where it
 * was; the finished file must have the size and the sha256 from the list before it is
 * accepted. The connection itself comes from [open], so this class has no network code.
 */
class VoiceDownloader(
    private val open: (url: String, from: Long) -> Source?,
    /** Where the files are: the release of the speech models, or another release of the keyboard. */
    private val baseUrl: String = VoiceCatalog.BASE_URL,
    /** Whether the first bytes of a finished file look like a model of the expected kind. */
    private val isModelHeader: (ByteArray, Int) -> Boolean = VoiceModels::isModelHeader,
) {

    /** An open download. [startsAt] is the first byte the server sends: [from] if it can continue, else 0. */
    class Source(val stream: InputStream, val startsAt: Long)

    enum class Result { OK, CANCELLED, NO_CONNECTION, WRONG_FILE, NO_SPACE, FAILED }

    fun download(
        offer: VoiceOffer,
        directory: File,
        cancelled: () -> Boolean,
        onProgress: (Long) -> Unit,
    ): Result {
        val target = File(directory, offer.file)
        val partial = partialFile(directory, offer.file)
        try {
            var have = if (partial.exists()) partial.length() else 0L
            if (have > offer.bytes) {
                partial.delete()
                have = 0
            }
            if (directory.usableSpace < offer.bytes - have + SPARE_BYTES) return Result.NO_SPACE
            onProgress(have)
            if (have < offer.bytes) {
                val source = open(baseUrl + offer.file, have) ?: return Result.NO_CONNECTION
                source.stream.use { input ->
                    if (source.startsAt != have) {
                        // The server started over (or somewhere else): so do we.
                        if (source.startsAt != 0L) return Result.FAILED
                        have = 0
                    }
                    FileOutputStream(partial, have > 0).use { output ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            if (cancelled()) return Result.CANCELLED
                            val read = try {
                                input.read(buffer)
                            } catch (e: IOException) {
                                // A cancel during a stalled read shows up here, as its timeout.
                                return if (cancelled()) Result.CANCELLED else Result.NO_CONNECTION
                            }
                            if (read < 0) break
                            have += read
                            if (have > offer.bytes) {
                                output.close()
                                partial.delete()
                                return Result.WRONG_FILE
                            }
                            output.write(buffer, 0, read)
                            onProgress(have)
                        }
                    }
                }
                // The connection closed early; the next try continues from here.
                if (have < offer.bytes) return if (cancelled()) Result.CANCELLED else Result.NO_CONNECTION
            }
            val sha256 = sha256(partial, cancelled) ?: return Result.CANCELLED
            if (sha256 != offer.sha256 || !startsLikeAModel(partial)) {
                partial.delete()
                return Result.WRONG_FILE
            }
            target.delete()
            return if (partial.renameTo(target)) Result.OK else Result.FAILED
        } catch (e: IOException) {
            return Result.FAILED
        } catch (e: SecurityException) {
            return Result.FAILED
        }
    }

    private fun startsLikeAModel(file: File): Boolean {
        val head = ByteArray(4)
        val count = file.inputStream().use { it.read(head) }
        return isModelHeader(head, count)
    }

    companion object {
        /** Kept free on the phone on top of the model itself. */
        const val SPARE_BYTES = 50L shl 20

        fun partialFile(directory: File, fileName: String) = File(directory, "$fileName.download")

        /** The sha256 of a file in lowercase hex, or null when [cancelled] says stop. */
        fun sha256(file: File, cancelled: () -> Boolean = { false }): String? {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    if (cancelled()) return null
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
