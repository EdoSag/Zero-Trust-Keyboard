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

    // The loaded model. Written on the worker thread under [handleLock]; [cancel] reads it
    // under the same lock, so the model cannot be freed while a cancel is using it.
    private val handleLock = Any()
    private var handle = 0L
    private var loadedPath: String? = null

    // Requests are numbered; a cancel covers every request made so far.
    private var requests = 0
    @Volatile private var cancelledUpTo = 0

    val available: Boolean get() = WhisperNative.available

    /**
     * Runs on a background thread and calls [onResult] on the main thread with the text,
     * or null when the model could not be loaded or the work was cancelled.
     * Call from the main thread.
     */
    fun transcribe(model: File, samples: FloatArray, language: String, onResult: (String?) -> Unit) {
        main.removeCallbacks(unload)
        val request = ++requests
        worker.execute {
            val text = try {
                transcribeNow(request, model, samples, language)
            } catch (t: Throwable) {
                Log.w(TAG, "transcription failed", t)
                null
            }
            main.post { onResult(text) }
            main.removeCallbacks(unload)
            main.postDelayed(unload, KEEP_LOADED_MILLIS)
        }
    }

    /** Abandons every transcription asked for so far, running or waiting. Call from the main thread. */
    fun cancel() {
        synchronized(handleLock) {
            cancelledUpTo = requests
            if (handle != 0L) WhisperNative.cancel(handle)
        }
    }

    /** Frees the model, for example before its file is replaced or deleted. */
    fun release() {
        main.removeCallbacks(unload)
        worker.execute { close() }
    }

    private fun transcribeNow(request: Int, model: File, samples: FloatArray, language: String): String? {
        if (!WhisperNative.available || request <= cancelledUpTo) return null
        if (handle == 0L || loadedPath != model.path) {
            close()
            val started = System.currentTimeMillis()
            val opened = WhisperNative.open(model.path)
            if (opened == 0L) return null
            synchronized(handleLock) { handle = opened }
            loadedPath = model.path
            Log.i(TAG, "loaded ${model.name} in ${System.currentTimeMillis() - started} ms")
        }
        // Loading can take seconds: the request may have been cancelled meanwhile. From here
        // on a cancel reaches the engine itself.
        synchronized(handleLock) {
            if (request <= cancelledUpTo) return null
            WhisperNative.allow(handle)
        }
        // A model for English only knows no other language.
        val code = if (WhisperNative.isMultilingual(handle)) language else "en"
        val started = System.currentTimeMillis()
        val bytes = WhisperNative.transcribe(handle, samples, samples.size, code, null, threads(), BEAM_SIZE)
        Log.i(TAG, "${samples.size / VoiceAudio.SAMPLE_RATE} s of speech in ${System.currentTimeMillis() - started} ms")
        return if (request <= cancelledUpTo) null else bytes?.toString(Charsets.UTF_8)
    }

    /** The fast cores of a phone are about half of all its cores. */
    private fun threads(): Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6)

    private fun close() {
        val old = synchronized(handleLock) {
            val current = handle
            handle = 0L
            current
        }
        loadedPath = null
        if (old != 0L) WhisperNative.close(old)
    }
}
