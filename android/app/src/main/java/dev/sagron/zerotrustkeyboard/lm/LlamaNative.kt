// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.lm

/** The llama.cpp engine in libzt_llm.so (see android/llm). Runs on the device only. */
internal object LlamaNative {
    val available: Boolean = try {
        System.loadLibrary("zt_llm")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    /** Loads a GGUF model file; 0 when it cannot be read. */
    @JvmStatic external fun open(modelPath: String, threads: Int, contextSize: Int): Long
    @JvmStatic external fun close(handle: Long)

    /** Stops the [generate] that is running on another thread, and any later one until [allow]. */
    @JvmStatic external fun cancel(handle: Long)

    /** Takes back a [cancel]. */
    @JvmStatic external fun allow(handle: Long)

    /**
     * Continues [prompt] (UTF-8) with up to [maxTokens] tokens, always the most likely one.
     * @return the new text as UTF-8, or null when it failed or was cancelled
     */
    @JvmStatic external fun generate(handle: Long, prompt: ByteArray, maxTokens: Int, stopAtNewline: Boolean): ByteArray?
}
