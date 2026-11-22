// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.lm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.sagron.zerotrustkeyboard.voice.VoiceCatalog
import dev.sagron.zerotrustkeyboard.voice.VoiceModels
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import java.io.File

/**
 * The language model on this phone, for writing help: one GGUF file (the format of
 * llama.cpp) in the keyboard's private storage. There is at most one; a new one replaces it.
 */
object LanguageModels {
    private const val TAG = "LanguageModels"

    /** Finish my sentence: offer the next few words after a pause. Off until turned on. */
    const val PREF_FINISH_SENTENCES = "zt_lm_finish_sentences"
    const val DEFAULT_FINISH_SENTENCES = false

    const val BASE_URL = "https://github.com/EdoSag/Zero-Trust-Keyboard/releases/download/language-models/"
    const val LIST_FILE = "language-models.tsv"

    private const val EXTENSION = ".gguf"
    private const val PARTIAL = ".part"
    private val MAGIC = "GGUF".toByteArray(Charsets.US_ASCII)
    private const val MIN_MODEL_BYTES = 10_000_000L

    enum class ImportResult { OK, NOT_A_MODEL, FAILED }

    /** True when this build has the engine at all. */
    val available: Boolean get() = LlamaNative.available

    fun directory(context: Context): File =
        File(DeviceProtectedUtils.getFilesDir(context), "lm").apply { mkdirs() }

    /** The model in use, or null when none was added yet. */
    fun installed(context: Context): File? =
        directory(context).listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
            ?.maxByOrNull { it.lastModified() }

    fun any(context: Context): Boolean = available && installed(context) != null

    fun isModelHeader(bytes: ByteArray, count: Int): Boolean =
        count >= MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    /** "qwen2.5-0.5b-instruct-q4_0.gguf" is shown as "qwen2.5-0.5b-instruct-q4_0". */
    fun label(fileName: String): String = fileName.removeSuffix(EXTENSION).ifEmpty { fileName }

    /** The downloadable models: the same list format as the speech models. */
    fun parseList(text: String) = VoiceCatalog.parse(text).filter { it.file.endsWith(EXTENSION) }

    /** Removes every model but [keep], after a new one arrived. */
    fun keepOnly(context: Context, keep: File) {
        LanguageModelEngine.release()
        directory(context).listFiles()?.forEach { if (it.isFile && it != keep && it.name.endsWith(EXTENSION)) it.delete() }
    }

    fun delete(context: Context) {
        LanguageModelEngine.release()
        directory(context).listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    /** Copies a model the user picked into the keyboard's storage. Call from a background thread. */
    fun importModel(context: Context, uri: Uri, onProgress: (Int) -> Unit): ImportResult {
        val resolver = context.contentResolver
        val displayName = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
        val name = VoiceModels.fileNameFor(displayName?.removeSuffix(EXTENSION)).removeSuffix(".bin") + EXTENSION
        val target = File(directory(context), name)
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
            LanguageModelEngine.release()
            target.delete()
            if (!partial.renameTo(target)) return ImportResult.FAILED
            keepOnly(context, target)
            return ImportResult.OK
        } catch (e: Exception) {
            Log.w(TAG, "could not copy the model", e)
            return ImportResult.FAILED
        } finally {
            partial.delete()
        }
    }
}
