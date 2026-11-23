// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.lm

import android.os.Handler
import android.os.Looper
import dev.sagron.zerotrustkeyboard.voice.VoiceAudio
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Runs the language model on the phone. One request at a time, on a background thread; a
 * new request or [cancel] drops the one before. The model stays loaded for two minutes after
 * the last use and is then freed, because it takes hundreds of megabytes of memory.
 * Nothing that is asked or answered is kept.
 */
object LanguageModelEngine {
    private const val TAG = "LanguageModelEngine"
    private const val KEEP_LOADED_MILLIS = 2 * 60 * 1000L
    private const val CONTEXT_TOKENS = 2048

    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "LanguageModelEngine") }
    private val main = Handler(Looper.getMainLooper())
    private val unload = Runnable { worker.execute { close() } }

    private val handleLock = Any()
    private var handle = 0L
    private var loadedPath: String? = null

    private var requests = 0
    @Volatile private var cancelledUpTo = 0

    /**
     * Continues [prompt] with up to [maxTokens] tokens and calls [onResult] on the main thread
     * with the new text, or null when there is no model, it failed or it was cancelled.
     * Call from the main thread.
     */
    fun generate(model: File, prompt: String, maxTokens: Int, stopAtNewline: Boolean, onResult: (String?) -> Unit) {
        main.removeCallbacks(unload)
        cancel()
        val request = ++requests
        worker.execute {
            val text = try {
                generateNow(request, model, prompt, maxTokens, stopAtNewline)
            } catch (t: Throwable) {
                Log.w(TAG, "the model failed", t)
                null
            }
            main.post {
                if (request > cancelledUpTo) onResult(text) else onResult(null)
            }
            main.removeCallbacks(unload)
            main.postDelayed(unload, KEEP_LOADED_MILLIS)
        }
    }

    /** Abandons every request made so far, running or waiting. Call from the main thread. */
    fun cancel() {
        synchronized(handleLock) {
            cancelledUpTo = requests
            if (handle != 0L) LlamaNative.cancel(handle)
        }
    }

    /** Frees the model, for example before its file is replaced or deleted. */
    fun release() {
        main.removeCallbacks(unload)
        worker.execute { close() }
    }

    private fun generateNow(request: Int, model: File, prompt: String, maxTokens: Int, stopAtNewline: Boolean): String? {
        if (!LlamaNative.available || request <= cancelledUpTo) return null
        if (handle == 0L || loadedPath != model.path) {
            close()
            val started = System.currentTimeMillis()
            val opened = LlamaNative.open(model.path, threads, CONTEXT_TOKENS)
            if (opened == 0L) return null
            synchronized(handleLock) { handle = opened }
            loadedPath = model.path
            Log.i(TAG, "loaded ${model.name} in ${System.currentTimeMillis() - started} ms")
        }
        synchronized(handleLock) {
            if (request <= cancelledUpTo) return null
            LlamaNative.allow(handle)
        }
        val started = System.currentTimeMillis()
        val bytes = LlamaNative.generate(handle, prompt.toByteArray(Charsets.UTF_8), maxTokens, stopAtNewline)
        Log.i(TAG, "answered in ${System.currentTimeMillis() - started} ms on $threads threads")
        if (request <= cancelledUpTo) return null
        return bytes?.toString(Charsets.UTF_8)
    }

    private val threads: Int by lazy {
        val cores = Runtime.getRuntime().availableProcessors()
        VoiceAudio.threads(List(cores) { core ->
            try {
                File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
            } catch (e: Exception) {
                0L
            }
        })
    }

    private fun close() {
        val old = synchronized(handleLock) {
            val current = handle
            handle = 0L
            current
        }
        loadedPath = null
        if (old != 0L) LlamaNative.close(old)
    }
}
