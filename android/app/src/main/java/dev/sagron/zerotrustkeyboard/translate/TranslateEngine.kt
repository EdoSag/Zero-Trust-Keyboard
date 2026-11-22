// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import helium314.keyboard.latin.utils.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Translates text on the phone with Firefox's translation engine (Bergamot, MPL-2.0, in
 * assets/translate) and the language models in [TranslateModels.directory]. It is the
 * engine ZT Browser uses, driven the same way.
 *
 * The engine runs in a hidden WebView whose only address is https://translate.zt, which
 * exists only inside the keyboard: every request it makes is answered here, from the
 * app's own files and the model files, and anything else is refused. So the engine cannot
 * reach the network, and the text never leaves the phone. It is stopped after a few idle
 * minutes to give its memory back.
 *
 * Everything here runs on the main thread.
 */
object TranslateEngine {
    private const val TAG = "TranslateEngine"
    private const val HOST = "translate.zt"
    private const val ORIGIN = "https://$HOST"
    private const val IDLE_MILLIS = 3 * 60 * 1000L

    /** Starting the engine and loading a model takes a while the first time; after this long it is given up. */
    private const val TIMEOUT_MILLIS = 90 * 1000L

    /** The Offline edition has no engine: it could never get a model. */
    val available: Boolean get() = TranslateNetwork.available

    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var ready = false
    private val queued = ArrayList<String>()
    private val pending = HashMap<Int, (List<String>?) -> Unit>()
    private var nextId = 1
    private var modelsDirectory: File? = null
    private val idleStop = Runnable { stop() }

    /**
     * Translates [texts] from [from] to [to]; [done] gets the translations in the same
     * order, or null when it failed. [host] is a view of the window the hidden engine is
     * attached to, so that it runs at full speed. Returns a number for [cancel].
     */
    fun translate(context: Context, host: View?, from: String, to: String, texts: List<String>, done: (List<String>?) -> Unit): Int {
        val id = nextId++
        if (!available) {
            main.post { done(null) }
            return id
        }
        pending[id] = done
        main.postDelayed({ finish(id, null) }, TIMEOUT_MILLIS)
        val job = JSONObject()
            .put("id", id)
            .put("from", from)
            .put("to", to)
            .put("html", false)
            .put("texts", JSONArray(texts))
        try {
            run(context.applicationContext, host, "zt.run($job)")
        } catch (e: Exception) {
            // No WebView on this phone, or it could not start.
            Log.w(TAG, "the engine did not start", e)
            stop()
        }
        return id
    }

    /** The answer of translation [id] is no longer wanted. */
    fun cancel(id: Int) {
        pending.remove(id)
        scheduleIdleStop()
    }

    /** The models changed (downloaded or deleted): load them afresh before the next translation. */
    fun reset() {
        main.post {
            val w = web ?: return@post
            if (ready) w.evaluateJavascript("zt.reset()", null) else queued.add("zt.reset()")
        }
    }

    fun stop() {
        main.removeCallbacks(idleStop)
        val w = web
        web = null
        ready = false
        queued.clear()
        if (w != null) {
            (w.parent as? ViewGroup)?.removeView(w)
            w.destroy()
        }
        val failed = pending.values.toList()
        pending.clear()
        failed.forEach { it(null) }
    }

    private fun run(context: Context, host: View?, script: String) {
        main.removeCallbacks(idleStop)
        val w = web ?: start(context, host)
        if (ready) w.evaluateJavascript(script, null) else queued.add(script)
    }

    private fun finish(id: Int, out: List<String>?) {
        val done = pending.remove(id) ?: return
        done(out)
        scheduleIdleStop()
    }

    private fun scheduleIdleStop() {
        main.removeCallbacks(idleStop)
        if (pending.isEmpty() && web != null) main.postDelayed(idleStop, IDLE_MILLIS)
    }

    private fun onMessage(raw: String) {
        val message = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return
        }
        if (message.optBoolean("ready")) {
            ready = true
            val w = web ?: return
            queued.forEach { w.evaluateJavascript(it, null) }
            queued.clear()
            return
        }
        val out = message.optJSONArray("out")
        if (out == null) Log.w(TAG, "translation failed: ${message.optString("error")}")
        finish(message.optInt("id"), out?.let { list -> List(list.length()) { list.optString(it) } })
    }

    /** What the page may call: one method, taking text. */
    private class Bridge {
        @JavascriptInterface
        fun postMessage(message: String) {
            main.post { onMessage(message) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun start(context: Context, host: View?): WebView {
        modelsDirectory = TranslateModels.directory(context)
        val w = WebView(context)
        web = w
        ready = false
        val settings = w.settings
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                serve(context, request.url)
        }
        // The only page this WebView ever loads is the keyboard's own, so only it sees the bridge.
        w.addJavascriptInterface(Bridge(), "ztTranslate")
        // Attached (invisible, one pixel) so that its timers and workers run at full speed.
        val root = host?.rootView as? ViewGroup
        if (root != null) {
            w.visibility = View.INVISIBLE
            try {
                root.addView(w, ViewGroup.LayoutParams(1, 1))
            } catch (e: Exception) {
                Log.w(TAG, "the engine runs unattached", e)
            }
        }
        w.loadUrl("$ORIGIN/index.html")
        return w
    }

    /** Answers every request of the engine's WebView; nothing goes out. Called on a WebView thread. */
    private fun serve(context: Context, url: Uri): WebResourceResponse {
        if (url.scheme != "https" || url.host != HOST) return refuse()
        val path = url.path?.trimStart('/') ?: return refuse()
        if (path.isEmpty() || path.contains("..")) return refuse()
        return try {
            if (path.startsWith("models/")) {
                val directory = modelsDirectory ?: return refuse()
                val file = File(directory, path.removePrefix("models/")).canonicalFile
                if (!file.path.startsWith(directory.canonicalPath + File.separator) || !file.isFile) return refuse(404)
                ok(mime(path), FileInputStream(file))
            } else {
                ok(mime(path), context.assets.open("translate/$path"))
            }
        } catch (e: Exception) {
            refuse(404)
        }
    }

    private fun ok(mime: String, data: InputStream) = WebResourceResponse(
        mime,
        if (mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json")) "utf-8" else null,
        200, "OK", mapOf("Cache-Control" to "no-store"), data,
    )

    private fun refuse(code: Int = 403) = WebResourceResponse(
        "text/plain", "utf-8", code, "Refused", emptyMap(), ByteArrayInputStream(ByteArray(0)),
    )

    private fun mime(path: String) = when (path.substringAfterLast('.', "")) {
        "html" -> "text/html"
        "js" -> "application/javascript"
        "wasm" -> "application/wasm"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }
}
