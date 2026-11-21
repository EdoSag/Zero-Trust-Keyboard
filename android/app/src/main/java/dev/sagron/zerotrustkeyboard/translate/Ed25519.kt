// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Checks an Ed25519 signature (RFC 8032). Android only has this built in from version 13,
 * and the keyboard runs on older phones too, so the check is done here. It only ever
 * handles public values (a public key, a signature, a public file), so it has nothing to
 * keep secret and need not run in constant time.
 */
object Ed25519 {
    private val TWO = BigInteger.valueOf(2)
    private val P = TWO.pow(255) - BigInteger.valueOf(19)
    private val L = TWO.pow(252) + BigInteger("27742317777372353535851937790883648493")
    private val D = (-BigInteger.valueOf(121665) * BigInteger.valueOf(121666).modInverse(P)).mod(P)
    private val SQRT_MINUS_ONE = TWO.modPow((P - BigInteger.ONE) / BigInteger.valueOf(4), P)

    private class Point(val x: BigInteger, val y: BigInteger)

    private val NEUTRAL = Point(BigInteger.ZERO, BigInteger.ONE)
    private val BASE: Point = run {
        val y = (BigInteger.valueOf(4) * BigInteger.valueOf(5).modInverse(P)).mod(P)
        Point(xOf(y, false)!!, y)
    }

    /** True when [signature] (64 bytes) is [publicKey]'s (32 bytes) signature of [message]. */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        val a = decode(publicKey) ?: return false
        val rBytes = signature.copyOfRange(0, 32)
        val r = decode(rBytes) ?: return false
        val s = littleEndian(signature.copyOfRange(32, 64))
        if (s >= L) return false
        val digest = MessageDigest.getInstance("SHA-512")
        digest.update(rBytes)
        digest.update(publicKey)
        val h = littleEndian(digest.digest(message)).mod(L)
        // [s]B = R + [h]A
        val left = multiply(BASE, s)
        val right = add(r, multiply(a, h))
        return left.x == right.x && left.y == right.y
    }

    private fun littleEndian(bytes: ByteArray) = BigInteger(1, bytes.reversedArray())

    /** A point from its 32 bytes: y, with the sign of x in the top bit. Null when it is not on the curve. */
    private fun decode(bytes: ByteArray): Point? {
        val copy = bytes.copyOf()
        val negative = copy[31].toInt() and 0x80 != 0
        copy[31] = (copy[31].toInt() and 0x7f).toByte()
        val y = littleEndian(copy)
        if (y >= P) return null
        return Point(xOf(y, negative) ?: return null, y)
    }

    /** The x of the point with this [y], or null when there is none. */
    private fun xOf(y: BigInteger, negative: Boolean): BigInteger? {
        val yy = y * y
        val xx = ((yy - BigInteger.ONE) * (D * yy + BigInteger.ONE).modInverse(P)).mod(P)
        var x = xx.modPow((P + BigInteger.valueOf(3)) / BigInteger.valueOf(8), P)
        if ((x * x).mod(P) != xx) x = (x * SQRT_MINUS_ONE).mod(P)
        if ((x * x).mod(P) != xx) return null
        if (x.signum() == 0 && negative) return null
        if (x.testBit(0) != negative) x = P - x
        return x
    }

    private fun add(a: Point, b: Point): Point {
        val xx = a.x * b.x
        val yy = a.y * b.y
        val k = (D * xx * yy).mod(P)
        val x = ((a.x * b.y + b.x * a.y) * (BigInteger.ONE + k).modInverse(P)).mod(P)
        val y = ((yy + xx) * (BigInteger.ONE - k).mod(P).modInverse(P)).mod(P)
        return Point(x, y)
    }

    private fun multiply(point: Point, times: BigInteger): Point {
        var result = NEUTRAL
        for (bit in times.bitLength() - 1 downTo 0) {
            result = add(result, result)
            if (times.testBit(bit)) result = add(result, point)
        }
        return result
    }
}
