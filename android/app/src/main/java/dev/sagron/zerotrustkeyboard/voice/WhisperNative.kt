// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

/** The whisper.cpp engine in libzt_whisper.so (see android/whisper). Runs on the device only. */
internal object WhisperNative {
    val available: Boolean = try {
        System.loadLibrary("zt_whisper")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    /** Loads a model file; 0 when it cannot be read. */
    @JvmStatic external fun open(modelPath: String): Long
    @JvmStatic external fun close(handle: Long)
    @JvmStatic external fun isMultilingual(handle: Long): Boolean

    /** Stops a [transcribe] that is running on another thread. */
    @JvmStatic external fun cancel(handle: Long)

    /**
     * @param samples 16 kHz mono, -1..1
     * @param language a Whisper language code, or "auto"
     * @param prompt words the speaker is likely to say, or null
     * @return the text as UTF-8, or null when it failed or was cancelled
     */
    @JvmStatic external fun transcribe(
        handle: Long, samples: FloatArray, count: Int, language: String, prompt: String?,
        threads: Int, beamSize: Int,
    ): ByteArray?
}
