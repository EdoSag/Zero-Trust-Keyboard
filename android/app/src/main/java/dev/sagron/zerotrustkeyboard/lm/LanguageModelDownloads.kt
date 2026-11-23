// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.lm

import android.content.Context
import dev.sagron.zerotrustkeyboard.voice.VoiceDownloader
import dev.sagron.zerotrustkeyboard.voice.VoiceNetwork
import dev.sagron.zerotrustkeyboard.voice.VoiceOffer
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Downloading the language model, for the settings screen, from the keyboard's own release
 * "language-models" on GitHub. Only the full edition can; the Offline edition adds a model
 * from a file. Nothing is fetched until the user asks.
 */
object LanguageModelDownloads {
    enum class ListState { NOT_LOADED, LOADING, LOADED, FAILED }

    data class Progress(val file: String, val done: Long, val total: Long)

    val available: Boolean get() = VoiceNetwork.available && LanguageModels.available

    val listState = MutableStateFlow(ListState.NOT_LOADED)
    val offers = MutableStateFlow<List<VoiceOffer>>(emptyList())
    val progress = MutableStateFlow<Progress?>(null)
    val lastResult = MutableStateFlow<VoiceDownloader.Result?>(null)

    /** Goes up each time a model was added, so screens look again. */
    val finished = MutableStateFlow(0)

    private const val MAX_LIST_BYTES = 1 shl 20
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "zt-lm-download").apply { isDaemon = true }
    }
    @Volatile private var cancelRequested = false

    fun loadList() {
        if (!available || listState.value == ListState.LOADING) return
        listState.value = ListState.LOADING
        thread(name = "zt-lm-list", isDaemon = true) {
            val text = try {
                VoiceNetwork.open(LanguageModels.BASE_URL + LanguageModels.LIST_FILE, 0)?.stream?.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (out.size() < MAX_LIST_BYTES) {
                        val read = input.read(buffer, 0, minOf(buffer.size, MAX_LIST_BYTES - out.size()))
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                    String(out.toByteArray(), Charsets.UTF_8)
                }
            } catch (e: Exception) {
                null
            }
            val list = text?.let { LanguageModels.parseList(it) }
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
            val directory = LanguageModels.directory(appContext)
            val result = try {
                VoiceDownloader(VoiceNetwork::open, LanguageModels.BASE_URL, LanguageModels::isModelHeader).download(
                    offer, directory, { cancelRequested }
                ) { done ->
                    val before = progress.value
                    if (before == null || done shr 20 != before.done shr 20 || done == offer.bytes)
                        progress.value = Progress(offer.file, done, offer.bytes)
                }
            } catch (e: Exception) {
                VoiceDownloader.Result.FAILED
            }
            if (result == VoiceDownloader.Result.OK) {
                // Only one model is kept: the new one replaces any other.
                LanguageModels.keepOnly(appContext, File(directory, offer.file))
                finished.value++
            }
            if (result == VoiceDownloader.Result.CANCELLED)
                VoiceDownloader.partialFile(directory, offer.file).delete()
            progress.value = null
            lastResult.value = if (result == VoiceDownloader.Result.CANCELLED) null else result
        }
    }

    fun cancel() {
        cancelRequested = true
    }
}
