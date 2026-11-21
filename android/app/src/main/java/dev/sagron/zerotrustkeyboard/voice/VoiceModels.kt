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

    /** The last dictation: "<speech ms> <load ms> <work ms> <model file>". No words. */
    const val PREF_LAST_TIMING = "zt_voice_last_timing"

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

    /** The last dictation's times, or null when there was none yet. */
    fun lastTiming(context: Context): VoiceEngine.Timing? = parseTiming(context.prefs().getString(PREF_LAST_TIMING, null))

    fun parseTiming(saved: String?): VoiceEngine.Timing? {
        val parts = saved?.split(" ", limit = 4) ?: return null
        if (parts.size != 4) return null
        val speech = parts[0].toLongOrNull() ?: return null
        val load = parts[1].toLongOrNull() ?: return null
        val work = parts[2].toLongOrNull() ?: return null
        return VoiceEngine.Timing(parts[3], speech, load, work)
    }

    /** The pick among the models for every language is kept under [PREF_ACTIVE_MODEL]; the pick
     *  among the models tuned for one language under that name plus "_" and the language. */
    private fun prefKey(language: String?) = if (language == null) PREF_ACTIVE_MODEL else PREF_ACTIVE_MODEL + "_" + language

    private fun chosen(context: Context, models: List<File>): Map<String, String> {
        val prefs = context.prefs()
        val picks = HashMap<String, String>()
        for (language in models.map { VoiceCatalog.languageOf(it.name) }.distinct())
            prefs.getString(prefKey(language), null)?.let { picks[language ?: ""] = it }
        return picks
    }

    /**
     * The model voice typing uses for speech in [language] (a Whisper code, or "auto"):
     * one tuned for the language when there is one, else the chosen model for every language.
     */
    fun forLanguage(context: Context, language: String): File? {
        val models = installed(context)
        val name = VoiceCatalog.choose(models.map { it.name }, chosen(context, models), language)
        return models.firstOrNull { it.name == name }
    }

    /** The names of the models in use: one for every language, and one per tuned language. */
    fun inUse(context: Context): Set<String> {
        val models = installed(context)
        return VoiceCatalog.picks(models.map { it.name }, chosen(context, models))
    }

    /** True when there is a model to type with at all. */
    fun any(context: Context): Boolean = installed(context).isNotEmpty()

    fun setActive(context: Context, model: File) {
        context.prefs().edit { putString(prefKey(VoiceCatalog.languageOf(model.name)), model.name) }
    }

    fun delete(context: Context, model: File) {
        VoiceEngine.release()
        model.delete()
        val key = prefKey(VoiceCatalog.languageOf(model.name))
        if (context.prefs().getString(key, null) == model.name)
            context.prefs().edit { remove(key) }
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
