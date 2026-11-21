// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.ListPickerDialog
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory

/** Settings > Translate: the two languages of the Translate key, and the languages on the phone. */
@Composable
fun TranslateScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    var installed by remember { mutableStateOf(TranslateModels.installed(context)) }
    var languages by remember { mutableStateOf(TranslateModels.languages(context)) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: my language, false: the other one
    var toDownload by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<String?>(null) }
    val listState by TranslateDownloads.listState.collectAsState()
    val offered by TranslateDownloads.models.collectAsState()
    val download by TranslateDownloads.progress.collectAsState()
    val downloadResult by TranslateDownloads.lastResult.collectAsState()
    val changed by TranslateDownloads.changed.collectAsState()

    // A finished download or a delete changed what is on the phone.
    LaunchedEffect(changed) { installed = TranslateModels.installed(context) }

    val pivot = TranslateCatalog.PIVOT
    // A language is on the phone when it translates both to and from English.
    val onPhone = TranslateCatalog.languages(installed).filter { it != pivot }
    val (mine, other) = languages
    val ready = TranslateCatalog.route(installed, mine, other) != null &&
        TranslateCatalog.route(installed, other, mine) != null

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.zt_translate_title),
        settings = emptyList(),
        content = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(if (TranslateDownloads.available) R.string.zt_translate_intro else R.string.zt_translate_not_in_edition),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (TranslateDownloads.available) {
                    PreferenceCategory(stringResource(R.string.zt_translate_languages))
                    Preference(
                        name = stringResource(R.string.zt_translate_mine),
                        description = TranslateCatalog.name(mine),
                        onClick = { picking = true },
                    )
                    Preference(
                        name = stringResource(R.string.zt_translate_other),
                        description = TranslateCatalog.name(other),
                        onClick = { picking = false },
                    )
                    if (!ready) Text(
                        stringResource(R.string.zt_translate_pair_missing, TranslateCatalog.name(mine), TranslateCatalog.name(other)),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                    )

                    PreferenceCategory(stringResource(R.string.zt_translate_on_phone))
                    if (onPhone.isEmpty()) Text(
                        stringResource(R.string.zt_translate_none_on_phone),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    for (language in onPhone) {
                        val megabytes = toMegabytes(
                            listOfNotNull(installed["$language-$pivot"], installed["$pivot-$language"]).sumOf { it.diskBytes }
                        )
                        Preference(
                            name = TranslateCatalog.name(language),
                            description = stringResource(R.string.zt_translate_size, megabytes),
                            onClick = { },
                        ) {
                            TextButton(onClick = { toDelete = language }) { Text(stringResource(R.string.delete)) }
                        }
                    }

                    PreferenceCategory(stringResource(R.string.zt_translate_download))
                    val running = download
                    if (listState == TranslateDownloads.ListState.LOADED) {
                        for (language in TranslateCatalog.languages(offered).filter { it != pivot && it !in onPhone }) {
                            val megabytes = toMegabytes(TranslateDownloads.missing(language, installed).sumOf { it.downloadBytes })
                            val isRunning = running?.language == language
                            Preference(
                                name = TranslateCatalog.name(language),
                                description = if (isRunning && running != null)
                                    stringResource(R.string.zt_translate_downloading, toMegabytes(running.done), toMegabytes(running.total))
                                else stringResource(R.string.zt_translate_size, megabytes),
                                onClick = { if (running == null) toDownload = language },
                            ) {
                                if (isRunning) TextButton(onClick = { TranslateDownloads.cancel() }) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            }
                        }
                    } else {
                        Preference(
                            name = stringResource(R.string.zt_translate_show_downloads),
                            description = stringResource(
                                when (listState) {
                                    TranslateDownloads.ListState.LOADING -> R.string.zt_translate_list_loading
                                    TranslateDownloads.ListState.FAILED -> R.string.zt_translate_list_failed
                                    else -> R.string.zt_translate_show_downloads_summary
                                }
                            ),
                            onClick = { TranslateDownloads.loadList() },
                        )
                    }
                    downloadResult?.let { result ->
                        if (result != TranslateDownloader.Result.OK) Text(
                            stringResource(
                                when (result) {
                                    TranslateDownloader.Result.NO_CONNECTION -> R.string.zt_translate_download_stopped
                                    TranslateDownloader.Result.WRONG_FILE -> R.string.zt_translate_download_damaged
                                    TranslateDownloader.Result.NO_SPACE -> R.string.zt_translate_download_no_space
                                    else -> R.string.zt_translate_download_failed
                                }
                            ),
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    )

    picking?.let { forMine ->
        // The languages on the phone, and English, which needs no download of its own.
        val choices = (onPhone + pivot + mine + other).distinct().sortedBy { TranslateCatalog.name(it).lowercase() }
        ListPickerDialog(
            onDismissRequest = { picking = null },
            items = choices,
            onItemSelected = { chosen ->
                // Picking the language of the other side swaps the two.
                val pair = when {
                    forMine && chosen == other -> other to mine
                    !forMine && chosen == mine -> other to mine
                    forMine -> chosen to other
                    else -> mine to chosen
                }
                TranslateModels.setLanguages(context, pair.first, pair.second)
                languages = pair
            },
            title = { Text(stringResource(if (forMine) R.string.zt_translate_mine else R.string.zt_translate_other)) },
            selectedItem = if (forMine) mine else other,
            getItemName = { TranslateCatalog.name(it) },
        )
    }

    toDownload?.let { language ->
        val megabytes = toMegabytes(TranslateDownloads.missing(language, installed).sumOf { it.downloadBytes })
        ConfirmationDialog(
            onDismissRequest = { toDownload = null },
            onConfirmed = {
                TranslateDownloads.start(context, language)
                toDownload = null
            },
            content = { Text(stringResource(R.string.zt_translate_download_confirm, TranslateCatalog.name(language), megabytes)) },
        )
    }

    toDelete?.let { language ->
        ConfirmationDialog(
            onDismissRequest = { toDelete = null },
            onConfirmed = {
                TranslateDownloads.delete(context, language)
                toDelete = null
            },
            content = { Text(stringResource(R.string.zt_translate_delete_confirm, TranslateCatalog.name(language))) },
            confirmButtonText = stringResource(R.string.delete),
        )
    }
}

/** Bytes as whole megabytes, at least 1 for anything that is there. */
private fun toMegabytes(bytes: Long): Int = if (bytes <= 0) 0 else maxOf(1, ((bytes + (1 shl 19)) shr 20).toInt())
