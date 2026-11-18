// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Downloading speech models, for the settings screen. Only the full edition can: the
 * Offline edition has no INTERNET permission and its [VoiceNetwork] opens nothing.
 * Nothing is fetched until the user asks for the list or for a model.
 */
object VoiceDownloads {
    enum class ListState { NOT_LOADED, LOADING, LOADED, FAILED }

    data class Progress(val file: String, val done: Long, val total: Long)

    val available: Boolean get() = VoiceNetwork.available

    val listState = MutableStateFlow(ListState.NOT_LOADED)
    val offers = MutableStateFlow<List<VoiceOffer>>(emptyList())

    /** The download that is running, or null. */
    val progress = MutableStateFlow<Progress?>(null)

    /** How the last download ended, until the next one starts. */
    val lastResult = MutableStateFlow<VoiceDownloader.Result?>(null)

    /** Goes up each time a model was added, so screens read the folder again. */
    val finished = MutableStateFlow(0)

    private const val MAX_LIST_BYTES = 1 shl 20
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "zt-voice-download").apply { isDaemon = true }
    }
    @Volatile private var cancelRequested = false

    fun loadList() {
        if (!available || listState.value == ListState.LOADING) return
        listState.value = ListState.LOADING
        thread(name = "zt-voice-list", isDaemon = true) {
            val text = try {
                VoiceNetwork.open(VoiceCatalog.BASE_URL + VoiceCatalog.LIST_FILE, 0)?.stream?.use { input ->
                    val bytes = input.readNBytesCompat(MAX_LIST_BYTES)
                    String(bytes, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                null
            }
            val list = text?.let { VoiceCatalog.parse(it) }
            if (list.isNullOrEmpty()) {
                listState.value = ListState.FAILED
            } else {
                offers.value = list
                listState.value = ListState.LOADED
            }
        }
    }

    fun start(context: Context, offer: VoiceOffer) {
        if (!available || progress.value != null) return
        val appContext = context.applicationContext
        cancelRequested = false
        lastResult.value = null
        progress.value = Progress(offer.file, 0, offer.bytes)
        worker.execute {
            val result = try {
                VoiceDownloader(VoiceNetwork::open).download(
                    offer, VoiceModels.directory(appContext), { cancelRequested }
                ) { done ->
                    val before = progress.value
                    // One update per megabyte is plenty for the screen.
                    if (before == null || done shr 20 != before.done shr 20 || done == offer.bytes)
                        progress.value = Progress(offer.file, done, offer.bytes)
                }
            } catch (e: Exception) {
                VoiceDownloader.Result.FAILED
            }
            if (result == VoiceDownloader.Result.OK) {
                // A model with this name may have been loaded: the next use reads the new file.
                VoiceEngine.release()
                finished.value++
            }
            // A cancelled download is given up: its part of a file would only take space.
            if (result == VoiceDownloader.Result.CANCELLED)
                VoiceDownloader.partialFile(VoiceModels.directory(appContext), offer.file).delete()
            progress.value = null
            lastResult.value = if (result == VoiceDownloader.Result.CANCELLED) null else result
        }
    }

    fun cancel() {
        cancelRequested = true
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
