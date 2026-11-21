// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import java.io.InputStream

/**
 * The Offline edition never connects, so it has no language models to download and no
 * Translate key.
 */
object TranslateNetwork {
    val available: Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun open(url: String): InputStream? = null
}
