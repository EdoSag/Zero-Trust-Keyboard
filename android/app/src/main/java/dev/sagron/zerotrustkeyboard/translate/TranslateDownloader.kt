// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException

/**
 * Fetches the signed list of language models and the models themselves. Every file is
 * unpacked as it arrives and only kept when its size and sha256 are the ones in the
 * signed list. The connection comes from [open], so this class has no network code.
 */
class TranslateDownloader(private val open: (url: String) -> InputStream?) {

    enum class Result { OK, CANCELLED, NO_CONNECTION, WRONG_FILE, NO_SPACE, FAILED }

    /** The models on offer, or null when the list cannot be fetched or is not signed by the trusted key. */
    fun list(): Map<String, TranslateModel>? {
        val signature = fetch(TranslateCatalog.BASE_URL + TranslateCatalog.SIGNATURE_FILE, MAX_SIGNATURE_BYTES) ?: return null
        val list = fetch(TranslateCatalog.BASE_URL + TranslateCatalog.LIST_FILE, MAX_LIST_BYTES) ?: return null
        return TranslateCatalog.verified(list, String(signature, Charsets.UTF_8))
    }

    private fun fetch(url: String, limit: Int): ByteArray? = try {
        open(url)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                // More than a list could be: not ours.
                if (out.size() + read > limit) return null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    } catch (e: IOException) {
        null
    }

    /**
     * Downloads [model] into its folder under [root]. A file that is already there and
     * whole is not fetched again. [onProgress] gets the download bytes done so far.
     */
    fun download(model: TranslateModel, root: File, cancelled: () -> Boolean, onProgress: (Long) -> Unit): Result {
        val directory = File(root, model.pair)
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return Result.FAILED
            var done = 0L
            for (part in TranslateCatalog.PARTS) {
                val file = model.files[part] ?: return Result.WRONG_FILE
                val target = File(directory, file.name)
                if (!(target.isFile && target.length() == file.bytes && sha256(target) == file.sha256)) {
                    if (root.usableSpace < file.bytes + SPARE_BYTES) return Result.NO_SPACE
                    val result = one(file, target, cancelled) { onProgress(done + it) }
                    if (result != Result.OK) return result
                }
                done += file.gzBytes
                onProgress(done)
            }
            return Result.OK
        } catch (e: IOException) {
            return Result.FAILED
        } catch (e: SecurityException) {
            return Result.FAILED
        }
    }

    private fun one(file: TranslateFile, target: File, cancelled: () -> Boolean, onProgress: (Long) -> Unit): Result {
        val partial = File(target.parentFile, target.name + PARTIAL)
        val stream = open(TranslateCatalog.BASE_URL + file.asset) ?: return Result.NO_CONNECTION
        val packed = Counting(stream, file.gzBytes)
        val digest = MessageDigest.getInstance("SHA-256")
        var unpacked = 0L
        try {
            packed.use {
                // GZIPInputStream reads the header when it is made, so it is made inside the try.
                val input = GZIPInputStream(packed, 1 shl 16)
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        if (cancelled()) return discard(partial, Result.CANCELLED)
                        val read = input.read(buffer)
                        if (read < 0) break
                        unpacked += read
                        if (unpacked > file.bytes) return discard(partial, Result.WRONG_FILE)
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        onProgress(packed.count)
                    }
                }
            }
        } catch (e: TooLong) {
            return discard(partial, Result.WRONG_FILE)
        } catch (e: ZipException) {
            return discard(partial, Result.WRONG_FILE)
        } catch (e: IOException) {
            // A file that ends early is a connection that broke; so is a cancel during a stalled read.
            return discard(partial, if (cancelled()) Result.CANCELLED else Result.NO_CONNECTION)
        }
        if (unpacked != file.bytes || hex(digest.digest()) != file.sha256) return discard(partial, Result.WRONG_FILE)
        target.delete()
        return if (partial.renameTo(target)) Result.OK else discard(partial, Result.FAILED)
    }

    private fun discard(partial: File, result: Result): Result {
        partial.delete()
        return result
    }

    private class TooLong : IOException()

    /** Counts what is read and stops a download that is longer than the list says. */
    private class Counting(stream: InputStream, private val limit: Long) : FilterInputStream(stream) {
        var count = 0L
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) add(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) add(read)
            return read
        }

        private fun add(read: Int) {
            count += read
            if (count > limit) throw TooLong()
        }
    }

    companion object {
        const val PARTIAL = ".download"

        /** Kept free on the phone on top of the file itself. */
        const val SPARE_BYTES = 50L shl 20
        private const val MAX_LIST_BYTES = 2 shl 20
        private const val MAX_SIGNATURE_BYTES = 1024

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return hex(digest.digest())
        }

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
