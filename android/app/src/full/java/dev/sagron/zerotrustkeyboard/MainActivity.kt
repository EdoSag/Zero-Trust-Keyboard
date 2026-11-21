// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import dev.sagron.zerotrustkeyboard.sync.PersonalWords
import helium314.keyboard.settings.SettingsActivity2
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * The ZT app around the keyboard (full edition only): setup, optional Zero-Trust account
 * and the shared settings, drawn by Flutter. The keyboard itself is the native service in
 * `helium314.keyboard.latin.LatinIME` and does not depend on this activity.
 *
 * FlutterFragmentActivity, not FlutterActivity: the biometric prompt (local_auth) needs a
 * FragmentActivity.
 */
class MainActivity : FlutterFragmentActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "imeStatus" -> result.success(
                        mapOf("enabled" to isEnabled(), "selected" to isSelected())
                    )
                    "openImeSettings" -> {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        result.success(null)
                    }
                    "showImePicker" -> {
                        imm().showInputMethodPicker()
                        result.success(null)
                    }
                    "openKeyboardSettings" -> {
                        // SettingsActivity2 is the entry that is never disabled: an older build
                        // could switch SettingsActivity off (hidden launcher icon, Android 9).
                        startActivity(Intent(this, SettingsActivity2::class.java))
                        result.success(null)
                    }
                    // The personal dictionary, for the account's encrypted sync. Android
                    // opens it only to a keyboard that is turned on; for any other app it
                    // looks empty, which must not be taken for "the user deleted every word".
                    "personalWords" -> inBackground(result) {
                        if (isEnabled()) PersonalWords.all(applicationContext) else null
                    }
                    "applyPersonalWords" -> {
                        val add = call.argument<List<Map<String, Any?>>>("add").orEmpty()
                        val remove = call.argument<List<Map<String, Any?>>>("remove").orEmpty()
                        inBackground(result) {
                            if (!isEnabled()) return@inBackground false
                            PersonalWords.apply(applicationContext, add, remove)
                            true
                        }
                    }
                    else -> result.notImplemented()
                }
            }
    }

    /** Runs [work] off the main thread and answers the Flutter side on it. */
    private fun inBackground(result: MethodChannel.Result, work: () -> Any?) {
        Thread {
            val outcome = runCatching(work)
            runOnUiThread {
                outcome.fold(
                    onSuccess = { result.success(it) },
                    onFailure = { result.error("failed", it.message, null) },
                )
            }
        }.start()
    }

    private fun imm() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    /** Turned on under the phone's keyboard list. */
    private fun isEnabled(): Boolean =
        imm().enabledInputMethodList.any { it.packageName == packageName }

    /** The keyboard that opens when a text field is tapped. */
    private fun isSelected(): Boolean {
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return current?.substringBefore('/') == packageName
    }

    private companion object {
        const val CHANNEL = "zt_keyboard/host"
    }
}
