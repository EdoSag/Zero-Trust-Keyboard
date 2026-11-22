// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.lm

import android.content.Intent
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import dev.sagron.zerotrustkeyboard.voice.VoiceDownloader
import dev.sagron.zerotrustkeyboard.voice.VoiceOffer
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.filePicker
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.preferences.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Settings > Writing help: the small language model on the phone (download, add from a file,
 * remove) and what it is used for: the Proofread key, and finishing the sentence being typed.
 */
@Composable
fun WritingHelpScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var model by remember { mutableStateOf(LanguageModels.installed(context)) }
    var copiedMegabytes by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<Int?>(null) }
    var toDownload by remember { mutableStateOf<VoiceOffer?>(null) }
    var toDelete by remember { mutableStateOf(false) }
    val listState by LanguageModelDownloads.listState.collectAsState()
    val offers by LanguageModelDownloads.offers.collectAsState()
    val download by LanguageModelDownloads.progress.collectAsState()
    val downloadResult by LanguageModelDownloads.lastResult.collectAsState()
    val downloadsFinished by LanguageModelDownloads.finished.collectAsState()
    val uiLocale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val hebrew = uiLocale.language == "iw" || uiLocale.language == "he"

    LaunchedEffect(downloadsFinished) { model = LanguageModels.installed(context) }

    val picker = filePicker { uri ->
        if (copiedMegabytes >= 0) return@filePicker
        error = null
        copiedMegabytes = 0
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LanguageModels.importModel(context.applicationContext, uri) { copiedMegabytes = it }
            }
            copiedMegabytes = -1
            error = when (result) {
                LanguageModels.ImportResult.OK -> null
                LanguageModels.ImportResult.NOT_A_MODEL -> R.string.zt_writing_not_a_model
                LanguageModels.ImportResult.FAILED -> R.string.zt_writing_copy_failed
            }
            model = LanguageModels.installed(context)
        }
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.zt_writing_title),
        settings = emptyList(),
        content = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(if (LanguageModels.available) R.string.zt_writing_intro else R.string.zt_writing_not_in_build),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!LanguageModels.available) return@Column

                SwitchPreference(
                    name = stringResource(R.string.zt_writing_finish),
                    description = stringResource(R.string.zt_writing_finish_summary),
                    key = LanguageModels.PREF_FINISH_SENTENCES,
                    default = LanguageModels.DEFAULT_FINISH_SENTENCES,
                )

                PreferenceCategory(stringResource(R.string.zt_writing_model))
                val current = model
                if (current == null) {
                    Text(
                        stringResource(R.string.zt_writing_no_model),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    Preference(
                        name = LanguageModels.label(current.name),
                        description = stringResource(R.string.zt_writing_size, (current.length() shr 20).toInt()),
                        onClick = { },
                    ) {
                        TextButton(onClick = { toDelete = true }) { Text(stringResource(R.string.delete)) }
                    }
                }

                if (LanguageModelDownloads.available) {
                    PreferenceCategory(stringResource(R.string.zt_writing_download))
                    val running = download
                    if (listState == LanguageModelDownloads.ListState.LOADED) {
                        for (offer in offers.filter { it.file != current?.name }) {
                            val isRunning = running?.file == offer.file
                            Preference(
                                name = offer.label(hebrew),
                                description = if (isRunning && running != null)
                                    stringResource(R.string.zt_writing_downloading, (running.done shr 20).toInt(), (running.total shr 20).toInt())
                                else stringResource(R.string.zt_writing_size, (offer.bytes shr 20).toInt()),
                                onClick = { if (running == null) toDownload = offer },
                            ) {
                                if (isRunning) TextButton(onClick = { LanguageModelDownloads.cancel() }) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            }
                        }
                    } else {
                        Preference(
                            name = stringResource(R.string.zt_writing_show_downloads),
                            description = stringResource(
                                when (listState) {
                                    LanguageModelDownloads.ListState.LOADING -> R.string.zt_writing_list_loading
                                    LanguageModelDownloads.ListState.FAILED -> R.string.zt_writing_list_failed
                                    else -> R.string.zt_writing_show_downloads_summary
                                }
                            ),
                            onClick = { LanguageModelDownloads.loadList() },
                        )
                    }
                    downloadResult?.let { result ->
                        if (result != VoiceDownloader.Result.OK) Text(
                            stringResource(
                                when (result) {
                                    VoiceDownloader.Result.NO_CONNECTION -> R.string.zt_writing_download_stopped
                                    VoiceDownloader.Result.WRONG_FILE -> R.string.zt_writing_download_damaged
                                    VoiceDownloader.Result.NO_SPACE -> R.string.zt_writing_download_no_space
                                    else -> R.string.zt_writing_download_failed
                                }
                            ),
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Preference(
                    name = stringResource(R.string.zt_writing_add_file),
                    description = if (copiedMegabytes >= 0) stringResource(R.string.zt_writing_copying, copiedMegabytes)
                    else stringResource(R.string.zt_writing_add_file_summary),
                    onClick = { if (copiedMegabytes < 0) picker.launch(MODEL_FILE_INTENT) },
                )
                error?.let {
                    Text(
                        stringResource(it),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    stringResource(R.string.zt_writing_privacy),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    )

    toDownload?.let { offer ->
        ConfirmationDialog(
            onDismissRequest = { toDownload = null },
            onConfirmed = {
                LanguageModelDownloads.start(context, offer)
                toDownload = null
            },
            content = { Text(stringResource(R.string.zt_writing_download_confirm, offer.label(hebrew), (offer.bytes shr 20).toInt())) },
        )
    }

    if (toDelete) {
        ConfirmationDialog(
            onDismissRequest = { toDelete = false },
            onConfirmed = {
                LanguageModels.delete(context)
                model = LanguageModels.installed(context)
                toDelete = false
            },
            content = { Text(stringResource(R.string.zt_writing_delete_confirm)) },
            confirmButtonText = stringResource(R.string.delete),
        )
    }
}

private val MODEL_FILE_INTENT: Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    .addCategory(Intent.CATEGORY_OPENABLE)
    .setType("*/*")
