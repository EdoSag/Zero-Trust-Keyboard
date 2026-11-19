// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelsTest {
    @Test fun ggmlFilesAreRecognisedByTheirFirstBytes() {
        // 0x67676d6c ("ggml"), lowest byte first.
        assertTrue(VoiceModels.isModelHeader(byteArrayOf(0x6c, 0x6d, 0x67, 0x67, 1, 2, 3), 7))
        assertFalse(VoiceModels.isModelHeader(byteArrayOf(0x67, 0x67, 0x6d, 0x6c), 4))
        assertFalse(VoiceModels.isModelHeader("PK\u0003\u0004 zip".toByteArray(), 8))
        assertFalse(VoiceModels.isModelHeader(byteArrayOf(0x6c, 0x6d, 0x67, 0x67), 3))
        assertFalse(VoiceModels.isModelHeader(ByteArray(0), 0))
    }

    @Test fun labelsAreShortModelNames() {
        assertEquals("small-q5_1", VoiceModels.label("ggml-small-q5_1.bin"))
        assertEquals("large-v3-turbo-q5_0", VoiceModels.label("ggml-large-v3-turbo-q5_0.bin"))
        assertEquals("my model", VoiceModels.label("my model.bin"))
    }

    @Test fun pickedFilesGetSafeNames() {
        assertEquals("ggml-small-q5_1.bin", VoiceModels.fileNameFor("ggml-small-q5_1.bin"))
        assertEquals("model.bin", VoiceModels.fileNameFor(null))
        assertEquals("model.bin", VoiceModels.fileNameFor(""))
        assertEquals("passwd.bin", VoiceModels.fileNameFor("../../etc/passwd"))
        assertEquals("my_model__1_.bin", VoiceModels.fileNameFor("my model (1).bin"))
        assertEquals("model.bin", VoiceModels.fileNameFor("..."))
        assertTrue(VoiceModels.fileNameFor("a".repeat(300)).length <= 84)
    }
}
