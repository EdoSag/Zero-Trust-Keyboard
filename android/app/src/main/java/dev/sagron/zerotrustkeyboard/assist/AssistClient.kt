// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.assist

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import helium314.keyboard.latin.utils.Log

/**
 * Carries the keyboard's requests to ZT Assist and its answers back: the keyboard's own
 * version of ZtAssistPlugin.java in Zero-Trust-Core, which the Flutter apps use.
 *
 * ZT Assist answers from a service on the same phone, so the text never leaves it. Before
 * anything is sent, the app that holds the name dev.sagron.zerotrustassist must be signed
 * with the same key as the keyboard. Otherwise another app installed under that name could
 * read what the user asks to have proofread. ZT Assist checks the keyboard the same way.
 *
 * One request at a time: a new one, or [cancel], drops the one before. Call from the main
 * thread only; answers come back on it.
 */
class AssistClient(context: Context) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** ZT Assist, once connected. */
    private var service: Messenger? = null
    private var bound = false

    /** Messages written before the connection was up. */
    private val unsent = ArrayList<Message>()

    private class Request(val id: String, val onResult: (AssistWriting.Result) -> Unit) {
        val text = StringBuilder()
    }

    private var requests = 0
    private var current: Request? = null

    private val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
        onReply(message)
        true
    })

    private val disconnectWhenIdle = Runnable { disconnect() }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val to = Messenger(binder)
            service = to
            val waiting = ArrayList(unsent)
            unsent.clear()
            for (message in waiting) {
                // A failed delivery has already ended the request and closed the connection.
                if (!deliver(to, message)) break
            }
        }

        // ZT Assist stopped: the phone took its memory, or it crashed.
        override fun onServiceDisconnected(name: ComponentName?) = lost(AssistWriting.Failure.STOPPED)

        override fun onBindingDied(name: ComponentName?) = lost(AssistWriting.Failure.STOPPED)

        override fun onNullBinding(name: ComponentName?) = lost(AssistWriting.Failure.NOT_INSTALLED)
    }

    /** Whether ZT Assist is on this phone and signed like the keyboard. */
    fun presence(): AssistWriting.Presence = presence(context)

    /**
     * Asks ZT Assist to do [task] on [text] and calls [onResult] once, with the whole answer
     * or with why there is none. Not called for a request that was cancelled.
     */
    fun ask(task: AssistWriting.Task, text: String, onResult: (AssistWriting.Result) -> Unit) {
        cancel()
        if (presence() != AssistWriting.Presence.READY) {
            // After this call has returned, like every other answer.
            main.post { onResult(AssistWriting.Result.Failed(AssistWriting.Failure.NOT_INSTALLED)) }
            return
        }
        val request = Request("kb-${++requests}", onResult)
        current = request
        main.removeCallbacks(disconnectWhenIdle)
        val data = Bundle()
        data.putString("id", request.id)
        data.putString("task", task.wire)
        data.putString("text", text)
        if (!post(AssistWriting.MSG_ASK, data)) end(request, AssistWriting.Result.Failed(AssistWriting.Failure.NOT_INSTALLED))
    }

    /** Abandons the request in progress, if there is one: its answer is no longer wanted. */
    fun cancel() {
        val request = current ?: return
        current = null
        // Tells the model to stop writing. The "done" that follows is for a request the
        // keyboard has forgotten, and is dropped.
        // Only over a connection that is there or on its way: a stop is no reason to
        // start ZT Assist.
        if (service != null || bound) {
            val data = Bundle()
            data.putString("id", request.id)
            post(AssistWriting.MSG_CANCEL, data)
        }
        idle()
    }

    /** Lets go of ZT Assist at once, so it can give the model's memory back. */
    fun release() {
        current = null
        disconnect()
    }

    /** False when ZT Assist cannot be reached at all. */
    private fun post(what: Int, data: Bundle): Boolean {
        val message = Message.obtain(null, what)
        message.data = data
        message.replyTo = replies
        val to = service
        if (to != null) return deliver(to, message)
        if (!bound) {
            val intent = Intent().setComponent(ComponentName(AssistWriting.PACKAGE, AssistWriting.SERVICE))
            bound = try {
                // Important: ZT Assist is not on screen while it answers, and Android gives a
                // background app only its slow cores. This keeps it as fast as the app the
                // user is looking at.
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
            } catch (refused: SecurityException) {
                false
            }
            if (!bound) {
                // Android wants the connection given back even when the bind did not take.
                try {
                    context.unbindService(connection)
                } catch (neverBound: RuntimeException) {
                    // Nothing to give back.
                }
                return false
            }
        }
        unsent.add(message)
        return true
    }

    /** False when ZT Assist was gone; the request in progress has then failed. */
    private fun deliver(to: Messenger, message: Message): Boolean =
        try {
            to.send(message)
            true
        } catch (gone: RemoteException) {
            lost(AssistWriting.Failure.STOPPED)
            false
        }

    private fun onReply(message: Message) {
        val data = message.data ?: return
        val request = current ?: return
        if (data.getString("id") != request.id) return
        when (message.what) {
            AssistWriting.MSG_CHUNK -> request.text.append(data.getString("text", ""))
            AssistWriting.MSG_DONE -> end(
                request,
                AssistWriting.result(
                    request.text.toString(),
                    stopped = data.getBoolean("stopped", false),
                    truncated = data.getBoolean("truncated", false),
                ),
            )
            AssistWriting.MSG_FAILED -> {
                val code = data.getString("code", "failed")
                Log.w(TAG, "ZT Assist did not answer: $code")
                end(request, AssistWriting.Result.Failed(AssistWriting.failure(code)))
            }
        }
    }

    private fun end(request: Request, result: AssistWriting.Result) {
        if (current !== request) return
        current = null
        idle()
        request.onResult(result)
    }

    /** The connection is gone: the request in progress ends with [failure]. */
    private fun lost(failure: AssistWriting.Failure) {
        val request = current
        current = null
        disconnect()
        // After the call that noticed has returned, like every other answer.
        if (request != null) main.post { request.onResult(AssistWriting.Result.Failed(failure)) }
    }

    /**
     * Starts the wait after which an unused connection is closed. While it is kept the model
     * stays in memory, so a second press starts at once; after it ZT Assist gives the
     * memory back.
     */
    private fun idle() {
        main.removeCallbacks(disconnectWhenIdle)
        main.postDelayed(disconnectWhenIdle, KEEP_CONNECTED_MILLIS)
    }

    private fun disconnect() {
        main.removeCallbacks(disconnectWhenIdle)
        unsent.clear()
        service = null
        if (!bound) return
        bound = false
        try {
            context.unbindService(connection)
        } catch (alreadyGone: RuntimeException) {
            // Nothing to give back.
        }
    }

    companion object {
        private const val TAG = "AssistClient"
        private const val KEEP_CONNECTED_MILLIS = 2 * 60 * 1000L

        /** Whether ZT Assist is on this phone and signed like the keyboard. */
        fun presence(context: Context): AssistWriting.Presence {
            val packages = context.packageManager
            try {
                packages.getPackageInfo(AssistWriting.PACKAGE, 0)
            } catch (missing: PackageManager.NameNotFoundException) {
                return AssistWriting.Presence.NOT_INSTALLED
            } catch (e: RuntimeException) {
                return AssistWriting.Presence.NOT_INSTALLED
            }
            return if (packages.checkSignatures(context.packageName, AssistWriting.PACKAGE) == PackageManager.SIGNATURE_MATCH)
                AssistWriting.Presence.READY
            else AssistWriting.Presence.UNTRUSTED
        }
    }
}
