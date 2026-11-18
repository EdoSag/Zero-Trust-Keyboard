// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import helium314.keyboard.latin.utils.Log

/**
 * Records from the microphone into memory: 16 kHz mono, at most [MAX_SECONDS]. Nothing is
 * written to storage. Callbacks arrive on the main thread.
 */
class VoiceRecorder(private val stopOnPause: Boolean, private val listener: Listener) {
    interface Listener {
        /** About ten times a second. [level] is 0..1. */
        fun onLevel(level: Float, seconds: Int, heardSpeech: Boolean)

        /** The speaker paused, the time is up, or nothing was said. The recorder is still running: call [stop] or [cancel]. */
        fun onShouldStop(nothingHeard: Boolean)

        /** The microphone could not be used. */
        fun onError()
    }

    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var samples = ShortArray(VoiceAudio.SAMPLE_RATE * 30)
    private var count = 0
    @Volatile private var running = false
    private var thread: Thread? = null

    /** Caller has checked the microphone permission. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(
            VoiceAudio.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, VoiceAudio.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, VoiceAudio.SAMPLE_RATE)
            )
        } catch (e: Exception) {
            Log.w(TAG, "no microphone", e)
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.w(TAG, "could not start recording", e)
            record.release()
            return false
        }
        running = true
        thread = Thread({ read(record) }, "VoiceRecorder").apply { start() }
        return true
    }

    /** Stops and returns what was recorded. */
    fun stop(): FloatArray {
        finish()
        synchronized(lock) { return VoiceAudio.toFloats(samples, count) }
    }

    /** Stops and forgets what was recorded. */
    fun cancel() {
        finish()
        synchronized(lock) { count = 0 }
    }

    private fun finish() {
        running = false
        thread?.let {
            try {
                it.join(1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        thread = null
    }

    private fun read(record: AudioRecord) {
        val detector = SpeechEndDetector()
        val chunk = ShortArray(VoiceAudio.SAMPLE_RATE / 10)
        var asked = false
        try {
            while (running) {
                val read = record.read(chunk, 0, chunk.size)
                if (read < 0) {
                    if (running) main.post { if (running) listener.onError() }
                    break
                }
                if (read == 0) continue
                val full = synchronized(lock) {
                    val room = VoiceAudio.SAMPLE_RATE * MAX_SECONDS - count
                    val take = minOf(read, room)
                    if (count + take > samples.size) samples = samples.copyOf(minOf(samples.size * 2, VoiceAudio.SAMPLE_RATE * MAX_SECONDS))
                    System.arraycopy(chunk, 0, samples, count, take)
                    count += take
                    take < read || room == take
                }
                val state = detector.add(chunk, read)
                val seconds = synchronized(lock) { count } / VoiceAudio.SAMPLE_RATE
                val level = detector.level
                val heard = detector.heardSpeech
                main.post { if (running) listener.onLevel(level, seconds, heard) }
                val nothingHeard = state == SpeechEndDetector.State.NOTHING_HEARD
                val paused = stopOnPause && state == SpeechEndDetector.State.FINISHED
                if (!asked && (full || paused || nothingHeard)) {
                    asked = true
                    main.post { if (running) listener.onShouldStop(nothingHeard && !full) }
                }
            }
        } finally {
            try {
                record.stop()
            } catch (e: Exception) {
                // already stopped
            }
            record.release()
        }
    }

    companion object {
        private const val TAG = "VoiceRecorder"

        /** Longest dictation in one go. */
        const val MAX_SECONDS = 120
    }
}
