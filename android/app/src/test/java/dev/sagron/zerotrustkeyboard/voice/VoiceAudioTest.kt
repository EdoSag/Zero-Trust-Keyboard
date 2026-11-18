// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

class VoiceAudioTest {
    /** A tenth of a second of a 220 Hz tone, [amplitude] 0..32767. */
    private fun tone(amplitude: Int): ShortArray = ShortArray(1600) {
        (amplitude * sin(2 * PI * 220 * it / VoiceAudio.SAMPLE_RATE)).toInt().toShort()
    }

    private fun feed(detector: SpeechEndDetector, amplitude: Int, tenths: Int): SpeechEndDetector.State {
        var state = SpeechEndDetector.State.LISTENING
        repeat(tenths) {
            state = detector.add(tone(amplitude), 1600)
            if (state != SpeechEndDetector.State.LISTENING) return state
        }
        return state
    }

    @Test fun samplesAreScaledToMinusOneToOne() {
        val floats = VoiceAudio.toFloats(shortArrayOf(0, 16384, -32768, 32767, 5), 4)
        assertEquals(4, floats.size)
        assertEquals(0f, floats[0], 0f)
        assertEquals(0.5f, floats[1], 0f)
        assertEquals(-1f, floats[2], 0f)
        assertTrue(floats[3] < 1f && floats[3] > 0.999f)
    }

    @Test fun loudnessOfSilenceAndOfATone() {
        assertEquals(0f, VoiceAudio.loudness(ShortArray(1600), 1600), 0f)
        assertEquals(0f, VoiceAudio.loudness(ShortArray(0), 0), 0f)
        // The root mean square of a sine wave is its amplitude divided by the square root of two.
        assertEquals(7071f, VoiceAudio.loudness(tone(10000), 1600), 30f)
    }

    @Test fun keyboardLanguageToWhisperLanguage() {
        assertEquals("he", VoiceAudio.language(Locale.forLanguageTag("he-IL")))
        assertEquals("he", VoiceAudio.language(Locale("iw")))
        assertEquals("en", VoiceAudio.language(Locale.US))
        assertEquals("no", VoiceAudio.language(Locale.forLanguageTag("nb")))
        assertEquals("auto", VoiceAudio.language(Locale.ROOT))
    }

    @Test fun heardTextIsCleanedAndSpaced() {
        assertEquals("Hello there.", VoiceAudio.textToType(" Hello there.", null))
        assertEquals(" Hello there.", VoiceAudio.textToType(" Hello there.", 'd'))
        assertEquals("Hello there.", VoiceAudio.textToType(" Hello there.", ' '))
        assertEquals("Hello there.", VoiceAudio.textToType(" Hello there.", '\n'))
        assertEquals("yes", VoiceAudio.textToType("yes", '('))
        assertEquals(" שלום, מה שלומך?", VoiceAudio.textToType(" שלום, מה שלומך?", '.'))
        assertEquals("one two", VoiceAudio.textToType(" one\n two ", null))
        assertEquals("", VoiceAudio.textToType(" [BLANK_AUDIO]", 'a'))
        assertEquals("", VoiceAudio.textToType(" (music) ♪♪", null))
        assertEquals("so yes", VoiceAudio.textToType("so [laughter] yes", null))
    }

    @Test fun pauseAfterSpeechEndsTheDictation() {
        val detector = SpeechEndDetector(pauseMillis = 2500)
        assertEquals(SpeechEndDetector.State.LISTENING, feed(detector, 60, 10))   // room noise
        assertFalse(detector.heardSpeech)
        assertEquals(SpeechEndDetector.State.LISTENING, feed(detector, 8000, 20)) // two seconds of speech
        assertTrue(detector.heardSpeech)
        assertEquals(SpeechEndDetector.State.LISTENING, feed(detector, 60, 24))   // 2.4 s pause: not yet
        assertEquals(SpeechEndDetector.State.FINISHED, feed(detector, 60, 1))
    }

    @Test fun shortPausesBetweenSentencesDoNotEndIt() {
        val detector = SpeechEndDetector(pauseMillis = 2500)
        repeat(5) {
            assertEquals(SpeechEndDetector.State.LISTENING, feed(detector, 8000, 15))
            assertEquals(SpeechEndDetector.State.LISTENING, feed(detector, 60, 15))
        }
    }

    @Test fun silenceFromTheStartIsNothingHeard() {
        val detector = SpeechEndDetector(nothingHeardMillis = 8000)
        assertEquals(SpeechEndDetector.State.LISTENING, feed(detector, 60, 79))
        assertEquals(SpeechEndDetector.State.NOTHING_HEARD, feed(detector, 60, 1))
        assertFalse(detector.heardSpeech)
    }

    @Test fun steadyBackgroundNoiseIsNotSpeech() {
        // A loud fan from the start: nothing rises above it.
        val detector = SpeechEndDetector(nothingHeardMillis = 8000)
        assertEquals(SpeechEndDetector.State.NOTHING_HEARD, feed(detector, 1500, 80))
        // Speech over the same fan is heard.
        val second = SpeechEndDetector()
        feed(second, 1500, 10)
        feed(second, 9000, 10)
        assertTrue(second.heardSpeech)
    }

    @Test fun speakingFromTheFirstMomentIsHeard() {
        // No quiet moment before the first word: the room is only known afterwards.
        val detector = SpeechEndDetector(pauseMillis = 2500, nothingHeardMillis = 8000)
        assertEquals(SpeechEndDetector.State.LISTENING, feed(detector, 8000, 20))
        assertEquals(SpeechEndDetector.State.LISTENING, feed(detector, 60, 1))
        assertTrue(detector.heardSpeech)
        // The pause is counted from the end of the speech, not from the start.
        assertEquals(SpeechEndDetector.State.LISTENING, feed(detector, 60, 23))
        assertEquals(SpeechEndDetector.State.FINISHED, feed(detector, 60, 1))
    }

    @Test fun levelFollowsTheVoice() {
        val detector = SpeechEndDetector()
        detector.add(tone(60), 1600)
        assertTrue(detector.level < 0.05f)
        detector.add(tone(8000), 1600)
        assertTrue(detector.level > 0.8f)
    }
}
