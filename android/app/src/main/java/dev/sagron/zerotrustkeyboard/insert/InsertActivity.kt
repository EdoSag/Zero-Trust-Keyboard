// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.insert

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log

/**
 * The insert key opens this see-through screen, because a keyboard cannot open another
 * app's screen and get an answer back by itself. It asks which Zero-Trust app to pick from
 * (when there is more than one), opens that app's pick screen and passes the chosen text on
 * to [InsertHandOver]. It shows nothing of its own apart from that one question.
 */
class InsertActivity : Activity() {
    private var asked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        asked = savedInstanceState?.getBoolean(ASKED) ?: false
        if (asked) return
        val sources = ZtApps.sources(this)
        when (sources.size) {
            0 -> {
                Toast.makeText(this, R.string.zt_insert_no_apps, Toast.LENGTH_LONG).show()
                giveUp()
            }
            1 -> open(sources[0])
            else -> AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.zt_insert_title)
                .setItems(sources.map { it.label }.toTypedArray()) { _, which -> open(sources[which]) }
                .setOnCancelListener { giveUp() }
                .show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ASKED, asked)
    }

    private fun open(source: ZtApps.Source) {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(ZtApps.pickIntent(source), REQUEST)
            asked = true
        } catch (e: Exception) {
            Log.w(TAG, "could not open ${source.component.packageName}", e)
            Toast.makeText(this, R.string.zt_insert_failed, Toast.LENGTH_LONG).show()
            giveUp()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST) return
        val text = if (resultCode == RESULT_OK) data?.getStringExtra(ZtApps.EXTRA_TEXT) else null
        if (text.isNullOrEmpty()) {
            giveUp()
            return
        }
        if (!InsertHandOver.answer(text, SystemClock.elapsedRealtime()))
            Toast.makeText(this, R.string.zt_insert_too_late, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun giveUp() {
        InsertHandOver.cancel()
        finish()
    }

    companion object {
        /** The insert key was pressed while typing in [editor]. */
        @JvmStatic
        fun start(context: Context, editor: EditorInfo?) {
            InsertHandOver.ask(editor?.packageName, editor?.fieldId ?: 0, SystemClock.elapsedRealtime())
            try {
                context.startActivity(
                    Intent(context, InsertActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
            } catch (e: Exception) {
                Log.w(TAG, "could not open the insert screen", e)
                InsertHandOver.cancel()
            }
        }

        private const val TAG = "InsertActivity"
        private const val REQUEST = 1
        private const val ASKED = "asked"
    }
}
