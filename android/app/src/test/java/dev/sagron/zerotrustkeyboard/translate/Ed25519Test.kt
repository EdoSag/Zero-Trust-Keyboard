// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Random

class Ed25519Test {
    private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // RFC 8032, section 7.1.
    private val key1 = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
    private val signature1 = hex(
        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155" +
            "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
    )
    private val key2 = hex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
    private val signature2 = hex(
        "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da" +
            "085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00"
    )
    private val key3 = hex("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025")
    private val signature3 = hex(
        "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac" +
            "18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a"
    )

    @Test fun `the signatures of the standard are accepted`() {
        assertTrue(Ed25519.verify(key1, ByteArray(0), signature1))
        assertTrue(Ed25519.verify(key2, hex("72"), signature2))
        assertTrue(Ed25519.verify(key3, hex("af82"), signature3))
    }

    @Test fun `a changed message, key or signature is refused`() {
        assertFalse(Ed25519.verify(key2, hex("73"), signature2))
        assertFalse(Ed25519.verify(key1, hex("72"), signature2))
        for (index in listOf(0, 31, 32, 63)) {
            val changed = signature2.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertFalse("byte $index", Ed25519.verify(key2, hex("72"), changed))
        }
    }

    @Test fun `wrong sizes and values that are no points are refused`() {
        assertFalse(Ed25519.verify(key1.copyOf(31), ByteArray(0), signature1))
        assertFalse(Ed25519.verify(key1, ByteArray(0), signature1.copyOf(63)))
        assertFalse(Ed25519.verify(ByteArray(32) { 0xff.toByte() }, ByteArray(0), signature1))
        // s must be below the group order: adding the order to it is refused.
        val order = hex("edd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010")
        val high = signature1.copyOf()
        var carry = 0
        for (i in 0 until 32) {
            val sum = (high[32 + i].toInt() and 0xff) + (order[i].toInt() and 0xff) + carry
            high[32 + i] = sum.toByte()
            carry = sum shr 8
        }
        assertFalse(Ed25519.verify(key1, ByteArray(0), high))
    }

    /** Against the signer that comes with Java: what it signs is accepted, anything else is not. */
    @Test fun `agrees with the platform signer`() {
        val random = Random(7)
        val generator = KeyPairGenerator.getInstance("Ed25519")
        repeat(12) { round ->
            val pair = generator.generateKeyPair()
            // The last 32 bytes of the encoded public key are the key itself.
            val publicKey = pair.public.encoded.let { it.copyOfRange(it.size - 32, it.size) }
            val message = ByteArray(random.nextInt(300)).also { random.nextBytes(it) }
            val signature = Signature.getInstance("Ed25519").run {
                initSign(pair.private)
                update(message)
                sign()
            }
            assertTrue("round $round", Ed25519.verify(publicKey, message, signature))
            assertFalse("round $round", Ed25519.verify(publicKey, message + 1, signature))
        }
    }
}
