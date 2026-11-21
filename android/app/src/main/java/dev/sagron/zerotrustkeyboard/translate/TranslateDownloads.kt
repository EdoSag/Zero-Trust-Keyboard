// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Downloading language models, for the settings screen. Only the full edition can: the
 * Offline edition has no INTERNET permission and its [TranslateNetwork] opens nothing.
 * Nothing is fetched until the user asks for the list or for a language.
 */
object TranslateDownloads {
    enum class ListState { NOT_LOADED, LOADING, LOADED, FAILED }

    /** A download in progress: the language being fetched, and bytes done of the total. */
    data class Progress(val language: String, val done: Long, val total: Long)

    val available: Boolean get() = TranslateNetwork.available

    val listState = MutableStateFlow(ListState.NOT_LOADED)
    val models = MutableStateFlow<Map<String, TranslateModel>>(emptyMap())

    /** The download that is running, or null. */
    val progress = MutableStateFlow<Progress?>(null)

    /** How the last download ended, until the next one starts. */
    val lastResult = MutableStateFlow<TranslateDownloader.Result?>(null)

    /** Goes up each time the models on the phone changed, so screens read them again. */
    val changed = MutableStateFlow(0)

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "zt-translate-download").apply { isDaemon = true }
    }
    @Volatile private var cancelRequested = false

    fun loadList() {
        if (!available || listState.value == ListState.LOADING) return
        listState.value = ListState.LOADING
        thread(name = "zt-translate-list", isDaemon = true) {
            val list = try {
                TranslateDownloader(TranslateNetwork::open).list()
            } catch (e: Exception) {
                null
            }
            if (list.isNullOrEmpty()) {
                listState.value = ListState.FAILED
            } else {
                models.value = list
                listState.value = ListState.LOADED
            }
        }
    }

    /** The models of [language] (both directions with English) that are not on the phone yet. */
    fun missing(language: String, installed: Map<String, TranslateModel>): List<TranslateModel> {
        val pivot = TranslateCatalog.PIVOT
        return listOf("$language-$pivot", "$pivot-$language")
            .filter { it !in installed }
            .mapNotNull { models.value[it] }
    }

    /** Downloads what [language] still needs to be translated to and from English. */
    fun start(context: Context, language: String) {
        if (!available || progress.value != null) return
        val appContext = context.applicationContext
        val todo = missing(language, TranslateModels.installed(appContext))
        if (todo.isEmpty()) return
        val total = todo.sumOf { it.downloadBytes }
        cancelRequested = false
        lastResult.value = null
        progress.value = Progress(language, 0, total)
        worker.execute {
            var before = 0L
            var result = TranslateDownloader.Result.OK
            for (model in todo) {
                result = try {
                    TranslateDownloader(TranslateNetwork::open).download(
                        model, TranslateModels.directory(appContext), { cancelRequested }
                    ) { done ->
                        val now = before + done
                        val shown = progress.value
                        // One update per megabyte is plenty for the screen.
                        if (shown == null || now shr 20 != shown.done shr 20) progress.value = Progress(language, now, total)
                    }
                } catch (e: Exception) {
                    TranslateDownloader.Result.FAILED
                }
                if (result != TranslateDownloader.Result.OK) break
                TranslateModels.add(appContext, model)
                changed.value++
                before += model.downloadBytes
            }
            progress.value = null
            lastResult.value = if (result == TranslateDownloader.Result.CANCELLED) null else result
        }
    }

    fun cancel() {
        cancelRequested = true
    }

    /** Removes [language]'s models from the phone. */
    fun delete(context: Context, language: String) {
        val pivot = TranslateCatalog.PIVOT
        TranslateModels.delete(context, "$language-$pivot")
        TranslateModels.delete(context, "$pivot-$language")
        changed.value++
    }
}
