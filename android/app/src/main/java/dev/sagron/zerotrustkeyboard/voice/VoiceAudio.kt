// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import java.util.Locale
import kotlin.math.sqrt

/** The parts of voice typing that need no phone: sound levels, end of speech, text clean-up. */
object VoiceAudio {
    const val SAMPLE_RATE = 16_000

    /** Recorded samples as Whisper wants them: -1..1. */
    fun toFloats(samples: ShortArray, count: Int): FloatArray =
        FloatArray(count) { samples[it] / 32768f }

    /** Loudness of a piece of sound, 0..32767 (root mean square). */
    fun loudness(samples: ShortArray, count: Int): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (i in 0 until count) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / count).toFloat()
    }

    /** The Whisper language code for a keyboard language. */
    fun language(locale: Locale): String = when (val code = locale.language.lowercase(Locale.ROOT)) {
        "" -> "auto"
        "iw" -> "he"
        "in" -> "id"
        "ji" -> "yi"
        "nb", "nn" -> "no"
        else -> code
    }

    private val NOISE_TAG = Regex("""\[[^\]]*]|\([^)]*\)|♪+""")
    private val SPACES = Regex("""\s+""")

    /**
     * What to type for what Whisper heard: without its notes about sounds ("[BLANK_AUDIO]",
     * "(music)"), on one line, and with a space in front when it follows a word.
     * @param before the character before the cursor, or null at the start of the field
     */
    fun textToType(heard: String, before: Char?): String {
        val text = SPACES.replace(NOISE_TAG.replace(heard, " "), " ").trim()
        if (text.isEmpty()) return ""
        val needsSpace = before != null && !before.isWhitespace() && before !in "([{\"'«„“"
        return if (needsSpace) " $text" else text
    }
}

/**
 * Listens for the end of speech: after something was said, a pause of [pauseMillis] means
 * the speaker is done. Adapts to the room's background noise.
 */
class SpeechEndDetector(
    private val pauseMillis: Int = 2500,
    private val nothingHeardMillis: Int = 8000,
    private val sampleRate: Int = VoiceAudio.SAMPLE_RATE,
) {
    enum class State { LISTENING, FINISHED, NOTHING_HEARD }

    private var noise = -1f
    private var speechMillis = 0
    private var quietMillis = 0
    private var totalMillis = 0

    /** True once the speaker has said something. */
    val heardSpeech: Boolean get() = speechMillis >= MIN_SPEECH_MILLIS

    /** Loudness of the last piece, 0..1, for the level display. */
    var level = 0f
        private set

    fun add(samples: ShortArray, count: Int): State {
        if (count <= 0) return State.LISTENING
        val millis = count * 1000 / sampleRate
        val loudness = VoiceAudio.loudness(samples, count)
        level = (loudness / 6000f).coerceIn(0f, 1f)
        // The quietest sound so far is the room.
        if (noise < 0f || loudness < noise) noise = loudness
        val speaking = loudness > maxOf(MIN_SPEECH_LOUDNESS, noise * 3f)
        // The room may get louder: follow it quickly between words, and only very slowly
        // while someone speaks, so that a long sentence is not taken for noise.
        noise = if (speaking) noise * 1.002f + 0.1f else noise + (loudness - noise) * 0.05f
        totalMillis += millis
        if (speaking) {
            speechMillis += millis
            quietMillis = 0
        } else {
            quietMillis += millis
        }
        return when {
            heardSpeech && quietMillis >= pauseMillis -> State.FINISHED
            !heardSpeech && totalMillis >= nothingHeardMillis -> State.NOTHING_HEARD
            else -> State.LISTENING
        }
    }

    private companion object {
        const val MIN_SPEECH_MILLIS = 300
        const val MIN_SPEECH_LOUDNESS = 350f
    }
}
