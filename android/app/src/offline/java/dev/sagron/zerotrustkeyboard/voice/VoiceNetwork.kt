// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

/**
 * The Offline edition never connects: speech models are added from a file only.
 */
object VoiceNetwork {
    val available: Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun open(url: String, from: Long): VoiceDownloader.Source? = null
}
