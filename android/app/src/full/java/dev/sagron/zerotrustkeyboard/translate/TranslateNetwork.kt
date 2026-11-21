// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The full edition's connection for downloading language models. The only addresses asked
 * for are the signed list and the model files of ZT Browser's "translation-models" release
 * on GitHub. Nothing about the phone or the text to translate is sent: translating itself
 * needs no connection.
 */
object TranslateNetwork {
    val available: Boolean = true

    /** Opens [url], or returns null when there is no usable answer. */
    fun open(url: String): InputStream? {
        if (!url.startsWith(TranslateCatalog.BASE_URL)) return null
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "ZT-Keyboard")
            // The bytes as they are: the files are gzip themselves and are unpacked by the keyboard.
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (connection.responseCode == HttpURLConnection.HTTP_OK) return connection.inputStream
            connection.disconnect()
            return null
        } catch (e: IOException) {
            connection?.disconnect()
            return null
        } catch (e: SecurityException) {
            connection?.disconnect()
            return null
        }
    }
}
