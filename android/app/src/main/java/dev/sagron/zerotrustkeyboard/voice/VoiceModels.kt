// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.io.File

/**
 * The speech models on this phone. A model is a Whisper file in ggml format (the format of
 * whisper.cpp), kept in the keyboard's private storage.
 */
object VoiceModels {
    private const val TAG = "VoiceModels"
    const val PREF_ACTIVE_MODEL = "zt_voice_model"
    const val PREF_STOP_ON_PAUSE = "zt_voice_stop_on_pause"
    const val DEFAULT_STOP_ON_PAUSE = true

    private const val EXTENSION = ".bin"
    private const val PARTIAL = ".part"

    /** The first four bytes of a ggml file: the number 0x67676d6c, lowest byte first. */
    private val MAGIC = byteArrayOf(0x6c, 0x6d, 0x67, 0x67)

    /** Smaller than any real model (the smallest is about 30 MB). */
    private const val MIN_MODEL_BYTES = 1_000_000L

    enum class ImportResult { OK, NOT_A_MODEL, FAILED }

    fun directory(context: Context): File =
        File(DeviceProtectedUtils.getFilesDir(context), "voice").apply { mkdirs() }

    fun installed(context: Context): List<File> =
        directory(context).listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
            ?.sortedBy { it.name } ?: emptyList()

    /** The model voice typing uses: the chosen one, or the only one. */
    fun active(context: Context): File? {
        val models = installed(context)
        val chosen = context.prefs().getString(PREF_ACTIVE_MODEL, null)
        return models.firstOrNull { it.name == chosen } ?: models.firstOrNull()
    }

    fun setActive(context: Context, model: File) {
        context.prefs().edit { putString(PREF_ACTIVE_MODEL, model.name) }
    }

    fun delete(context: Context, model: File) {
        VoiceEngine.release()
        model.delete()
        if (context.prefs().getString(PREF_ACTIVE_MODEL, null) == model.name)
            context.prefs().edit { remove(PREF_ACTIVE_MODEL) }
    }

    fun isModelHeader(bytes: ByteArray, count: Int): Boolean =
        count >= MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    /** "ggml-small-q5_1.bin" is shown as "small-q5_1". */
    fun label(fileName: String): String =
        fileName.removeSuffix(EXTENSION).removePrefix("ggml-").ifEmpty { fileName }

    /** A safe file name for a model the user picked. */
    fun fileNameFor(displayName: String?): String {
        val base = (displayName ?: "model").substringAfterLast('/').removeSuffix(EXTENSION)
        val safe = base.map { if (it.isLetterOrDigit() || it in "-_.") it else '_' }.joinToString("").trim('.')
        return (safe.ifEmpty { "model" }).take(80) + EXTENSION
    }

    /**
     * Copies a model the user picked into the keyboard's storage and makes it the one in use.
     * Call from a background thread. [onProgress] gets the megabytes copied so far.
     */
    fun importModel(context: Context, uri: Uri, onProgress: (Int) -> Unit): ImportResult {
        val resolver = context.contentResolver
        val displayName = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
        val target = File(directory(context), fileNameFor(displayName))
        val partial = File(target.path + PARTIAL)
        try {
            val input = resolver.openInputStream(uri) ?: return ImportResult.FAILED
            var total = 0L
            input.use {
                partial.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var first = true
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (first) {
                            if (!isModelHeader(buffer, read)) return ImportResult.NOT_A_MODEL
                            first = false
                        }
                        output.write(buffer, 0, read)
                        val before = total shr 20
                        total += read
                        if (total shr 20 != before) onProgress((total shr 20).toInt())
                    }
                }
            }
            if (total < MIN_MODEL_BYTES) return ImportResult.NOT_A_MODEL
            VoiceEngine.release()
            target.delete()
            if (!partial.renameTo(target)) return ImportResult.FAILED
            setActive(context, target)
            return ImportResult.OK
        } catch (e: Exception) {
            Log.w(TAG, "could not copy the model", e)
            return ImportResult.FAILED
        } finally {
            partial.delete()
        }
    }
}
