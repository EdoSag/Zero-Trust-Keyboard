// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.otp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.utils.Log

/**
 * One-time codes from text messages that arrived in the last few minutes.
 *
 * Android 15 and later hide these codes from apps that read notifications, so the keyboard
 * reads the newest messages itself, with the "read text messages" permission. It asks for
 * it only when the user turns the code suggestion on, and only the GitHub builds declare it:
 * Google Play allows it only for default SMS apps. Messages are read on the phone when a
 * text field opens or a message arrives; nothing is stored or sent, and a code that was used
 * or dismissed is remembered (in memory, by message id) so it is not offered again.
 */
object SmsCodes {
    /** A code older than this is no longer offered. */
    const val MAX_AGE_MS = 10 * 60 * 1000L

    /** At most this many recent messages are looked at. */
    private const val LOOK_AT = 5

    class Found(val messageId: Long, val code: String, val receivedAt: Long)

    private val done = HashSet<Long>()

    private var declared: Boolean? = null

    /** False in builds without the permission (Google Play): the setting is then hidden. */
    fun isDeclared(context: Context): Boolean = declared ?: try {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.contains(Manifest.permission.READ_SMS) == true
    } catch (e: Exception) {
        false
    }.also { declared = it }

    fun canRead(context: Context): Boolean = isDeclared(context) &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** The code of the newest recent message that carries one and was not used or dismissed. */
    fun latest(context: Context, now: Long = System.currentTimeMillis()): Found? {
        if (!canRead(context)) return null
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ?", arrayOf((now - MAX_AGE_MS).toString()),
                "${Telephony.Sms.DATE} DESC",
            )?.use { rows ->
                var seen = 0
                while (seen++ < LOOK_AT && rows.moveToNext()) {
                    val id = rows.getLong(0)
                    if (isDone(id)) continue
                    val code = OneTimeCode.find(rows.getString(1)) ?: continue
                    return Found(id, code, rows.getLong(2))
                }
            }
        } catch (e: Exception) {
            // Before the first unlock the messages cannot be read; nothing to offer then.
            Log.w("SmsCodes", "could not read recent messages: ${e.javaClass.simpleName}")
        }
        return null
    }

    @Synchronized private fun isDone(id: Long) = id in done

    /** The code of [messageId] was typed or dismissed. */
    @Synchronized fun markDone(messageId: Long) {
        done += messageId
    }

    /** Runs [onMessage] on the main thread when a message arrives, until [stop] is called. */
    class Watcher(private val context: Context, private val onMessage: Runnable) {
        private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onMessage.run()
        }
        private var watching = false

        fun start() {
            if (watching || !canRead(context)) return
            try {
                context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
                watching = true
            } catch (e: Exception) {
                Log.w("SmsCodes", "could not watch messages: ${e.javaClass.simpleName}")
            }
        }

        fun stop() {
            if (!watching) return
            context.contentResolver.unregisterContentObserver(observer)
            watching = false
        }
    }
}
