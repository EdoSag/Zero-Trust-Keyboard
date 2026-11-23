// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The full edition's connection for downloading models (speech, and the language model for
 * writing help). The only addresses asked for are the model lists and the model files of
 * the keyboard's own releases on GitHub.
 */
object VoiceNetwork {
    val available: Boolean = true

    /** Every address asked for starts with this: a release of the keyboard's own repository. */
    private const val RELEASES = "https://github.com/EdoSag/Zero-Trust-Keyboard/releases/download/"

    /** Opens [url] from byte [from], or returns null when there is no usable answer. */
    fun open(url: String, from: Long): VoiceDownloader.Source? {
        if (!url.startsWith(RELEASES)) return null
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "ZT-Keyboard")
            // The bytes as they are, so sizes and byte positions mean what they say.
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (from > 0) connection.setRequestProperty("Range", "bytes=$from-")
            return when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> VoiceDownloader.Source(connection.inputStream, 0)
                HttpURLConnection.HTTP_PARTIAL -> VoiceDownloader.Source(connection.inputStream, from)
                else -> {
                    connection.disconnect()
                    null
                }
            }
        } catch (e: IOException) {
            connection?.disconnect()
            return null
        } catch (e: SecurityException) {
            connection?.disconnect()
            return null
        }
    }
}
