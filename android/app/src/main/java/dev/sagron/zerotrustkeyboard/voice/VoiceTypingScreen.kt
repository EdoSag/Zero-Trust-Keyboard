// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import helium314.keyboard.latin.R
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.filePicker
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.preferences.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Settings > Voice typing: the microphone permission and the speech models on the phone. */
@Composable
fun VoiceTypingScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf(VoiceModels.installed(context)) }
    var active by remember { mutableStateOf(VoiceModels.active(context)?.name) }
    var copiedMegabytes by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<Int?>(null) }
    var toDelete by remember { mutableStateOf<File?>(null) }
    var microphone by remember { mutableStateOf(hasMicrophone(context)) }
    var askedBefore by remember { mutableStateOf(false) }

    fun refresh() {
        models = VoiceModels.installed(context)
        active = VoiceModels.active(context)?.name
    }

    // The permission can change in the system settings while this screen is in the back.
    LifecycleResumeEffect(Unit) {
        microphone = hasMicrophone(context)
        onPauseOrDispose { }
    }
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        microphone = it
        askedBefore = true
    }
    val picker = filePicker { uri ->
        if (copiedMegabytes >= 0) return@filePicker
        error = null
        copiedMegabytes = 0
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                VoiceModels.importModel(context.applicationContext, uri) { copiedMegabytes = it }
            }
            copiedMegabytes = -1
            error = when (result) {
                VoiceModels.ImportResult.OK -> null
                VoiceModels.ImportResult.NOT_A_MODEL -> R.string.zt_voice_not_a_model
                VoiceModels.ImportResult.FAILED -> R.string.zt_voice_copy_failed
            }
            refresh()
        }
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.zt_voice_title),
        settings = emptyList(),
        content = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.zt_voice_intro),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Preference(
                    name = stringResource(R.string.zt_voice_microphone),
                    description = stringResource(
                        when {
                            microphone -> R.string.zt_voice_microphone_allowed
                            askedBefore -> R.string.zt_voice_microphone_denied
                            else -> R.string.zt_voice_microphone_ask
                        }
                    ),
                    onClick = {
                        if (microphone) return@Preference
                        if (askedBefore) {
                            // Asked and refused: Android may not show the question again.
                            val intent = Intent(
                                SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.getActivity()?.startActivity(intent)
                        } else {
                            askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
                SwitchPreference(
                    name = stringResource(R.string.zt_voice_stop_on_pause),
                    description = stringResource(R.string.zt_voice_stop_on_pause_summary),
                    key = VoiceModels.PREF_STOP_ON_PAUSE,
                    default = VoiceModels.DEFAULT_STOP_ON_PAUSE,
                )
                PreferenceCategory(stringResource(R.string.zt_voice_models))
                if (models.isEmpty()) {
                    Text(
                        stringResource(R.string.zt_voice_no_models),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                for (model in models) {
                    val megabytes = (model.length() shr 20).toInt()
                    Preference(
                        name = VoiceModels.label(model.name),
                        description = stringResource(R.string.zt_voice_model_size, megabytes) +
                            if (model.name == active) " · " + stringResource(R.string.zt_voice_model_in_use) else "",
                        onClick = {
                            VoiceModels.setActive(context, model)
                            refresh()
                        },
                    ) {
                        RadioButton(selected = model.name == active, onClick = {
                            VoiceModels.setActive(context, model)
                            refresh()
                        })
                        TextButton(onClick = { toDelete = model }) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
                Preference(
                    name = stringResource(R.string.zt_voice_add_model),
                    description = if (copiedMegabytes >= 0) stringResource(R.string.zt_voice_copying, copiedMegabytes)
                        else stringResource(R.string.zt_voice_add_model_summary),
                    onClick = {
                        if (copiedMegabytes < 0) picker.launch(MODEL_FILE_INTENT)
                    },
                )
                error?.let {
                    Text(
                        stringResource(it),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    )

    toDelete?.let { model ->
        ConfirmationDialog(
            onDismissRequest = { toDelete = null },
            onConfirmed = {
                VoiceModels.delete(context, model)
                toDelete = null
                refresh()
            },
            content = { Text(stringResource(R.string.zt_voice_delete_model, VoiceModels.label(model.name))) },
            confirmButtonText = stringResource(R.string.delete),
        )
    }
}

private fun hasMicrophone(context: android.content.Context) =
    PermissionsUtil.checkAllPermissionsGranted(context, Manifest.permission.RECORD_AUDIO)

private val MODEL_FILE_INTENT: Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    .addCategory(Intent.CATEGORY_OPENABLE)
    .setType("*/*")
