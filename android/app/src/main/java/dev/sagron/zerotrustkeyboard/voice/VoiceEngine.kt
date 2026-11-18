// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import android.os.Handler
import android.os.Looper
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Turns recorded speech into text with the model in use. The model stays loaded for a few
 * minutes after the last use, so dictating several sentences in a row does not reload it,
 * and is then freed, because a model takes hundreds of megabytes of memory.
 */
object VoiceEngine {
    private const val TAG = "VoiceEngine"
    private const val KEEP_LOADED_MILLIS = 3 * 60 * 1000L

    /** Whisper's default (5). Slower than taking the first guess, and more accurate. */
    private const val BEAM_SIZE = 5

    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "VoiceEngine") }
    private val main = Handler(Looper.getMainLooper())
    private val unload = Runnable { worker.execute { close() } }

    // Only touched on the worker thread, except [handle] being read by [cancel].
    @Volatile private var handle = 0L
    private var loadedPath: String? = null

    val available: Boolean get() = WhisperNative.available

    /**
     * Runs on a background thread and calls [onResult] on the main thread with the text,
     * or null when the model could not be loaded or the work was cancelled.
     */
    fun transcribe(model: File, samples: FloatArray, language: String, onResult: (String?) -> Unit) {
        main.removeCallbacks(unload)
        worker.execute {
            val text = try {
                transcribeNow(model, samples, language)
            } catch (t: Throwable) {
                Log.w(TAG, "transcription failed", t)
                null
            }
            main.post { onResult(text) }
            main.removeCallbacks(unload)
            main.postDelayed(unload, KEEP_LOADED_MILLIS)
        }
    }

    /** Stops the transcription that is running, if any. */
    fun cancel() {
        val current = handle
        if (current != 0L) WhisperNative.cancel(current)
    }

    /** Frees the model, for example before its file is replaced or deleted. */
    fun release() {
        main.removeCallbacks(unload)
        worker.execute { close() }
    }

    private fun transcribeNow(model: File, samples: FloatArray, language: String): String? {
        if (!WhisperNative.available) return null
        if (handle == 0L || loadedPath != model.path) {
            close()
            val started = System.currentTimeMillis()
            handle = WhisperNative.open(model.path)
            if (handle == 0L) return null
            loadedPath = model.path
            Log.i(TAG, "loaded ${model.name} in ${System.currentTimeMillis() - started} ms")
        }
        // A model for English only knows no other language.
        val code = if (WhisperNative.isMultilingual(handle)) language else "en"
        val started = System.currentTimeMillis()
        val bytes = WhisperNative.transcribe(handle, samples, samples.size, code, null, threads(), BEAM_SIZE)
        Log.i(TAG, "${samples.size / VoiceAudio.SAMPLE_RATE} s of speech in ${System.currentTimeMillis() - started} ms")
        return bytes?.toString(Charsets.UTF_8)
    }

    /** The fast cores of a phone are about half of all its cores. */
    private fun threads(): Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6)

    private fun close() {
        if (handle != 0L) {
            val old = handle
            handle = 0L
            loadedPath = null
            WhisperNative.close(old)
        }
    }
}
