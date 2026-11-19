// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle

/**
 * Asks for the microphone permission. A keyboard cannot ask by itself, so the microphone
 * key opens this see-through screen the first time; it closes when the user has answered.
 */
class VoicePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && savedInstanceState == null) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }
}
