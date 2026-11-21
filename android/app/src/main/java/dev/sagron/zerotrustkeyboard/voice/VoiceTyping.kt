// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import android.Manifest
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SettingsDestination

/**
 * Voice typing in the keyboard: the microphone key starts listening, a second press (or
 * Done, or a pause in speech) stops, and the words are typed where the cursor is.
 * Recording and recognition happen on the phone; the sound is never stored or sent.
 */
class VoiceTyping(private val ime: LatinIME) : VoiceBarView.Listener, VoiceRecorder.Listener {
    private enum class State { IDLE, LISTENING, WORKING }

    private var state = State.IDLE
    private var recorder: VoiceRecorder? = null
    private var bar: VoiceBarView? = null
    private var containerVisibility: Int? = null

    /** Raised whenever work is abandoned, so a late result is not typed. */
    private var session = 0

    // While the words are worked out, the bar counts the seconds, so a long wait is not
    // taken for a keyboard that hangs.
    private val handler = Handler(Looper.getMainLooper())
    private var workingSince = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (state != State.WORKING) return
            bar?.showWorking(((SystemClock.elapsedRealtime() - workingSince) / 1000).toInt())
            handler.postDelayed(this, 1000)
        }
    }

    /** Called when the keyboard's views are (re)created. */
    fun attach(bar: VoiceBarView?) {
        this.bar = bar
        bar?.listener = this
        if (state != State.IDLE) showBar()
        if (state == State.WORKING) {
            bar?.showWorking(((SystemClock.elapsedRealtime() - workingSince) / 1000).toInt())
        }
    }

    /** The microphone key was pressed. */
    fun onVoiceKey() {
        when (state) {
            State.LISTENING -> finishListening()
            State.WORKING -> Unit
            State.IDLE -> start()
        }
    }

    /** The keyboard is closing or moving to another text field. */
    fun cancel() {
        if (state == State.IDLE) return
        session++
        recorder?.cancel()
        recorder = null
        if (state == State.WORKING) VoiceEngine.cancel()
        state = State.IDLE
        handler.removeCallbacks(tick)
        hideBar()
    }

    private fun start() {
        if (!VoiceEngine.available) {
            toast(R.string.zt_voice_not_supported)
            return
        }
        if (!VoiceModels.any(ime)) {
            toast(R.string.zt_voice_needs_model)
            openScreen(Intent(ime, SettingsActivity2::class.java).putExtra(EXTRA_SCREEN, SettingsDestination.VoiceTyping))
            return
        }
        if (!PermissionsUtil.checkAllPermissionsGranted(ime, Manifest.permission.RECORD_AUDIO)) {
            openScreen(Intent(ime, VoicePermissionActivity::class.java))
            return
        }
        val stopOnPause = ime.prefs().getBoolean(VoiceModels.PREF_STOP_ON_PAUSE, VoiceModels.DEFAULT_STOP_ON_PAUSE)
        val newRecorder = VoiceRecorder(stopOnPause, this)
        if (!newRecorder.start()) {
            toast(R.string.zt_voice_no_microphone)
            return
        }
        recorder = newRecorder
        state = State.LISTENING
        showBar()
        bar?.showListening(0, 0f, false)
    }

    private fun finishListening() {
        val samples = recorder?.stop()
        recorder = null
        val language = VoiceAudio.language(RichInputMethodManager.getInstance().currentSubtypeLocale)
        val model = VoiceModels.forLanguage(ime, language)
        // Under half a second there is no word to find.
        if (samples == null || samples.size < VoiceAudio.SAMPLE_RATE / 2 || model == null) {
            state = State.IDLE
            hideBar()
            return
        }
        state = State.WORKING
        workingSince = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        tick.run()
        val mine = ++session
        VoiceEngine.transcribe(model, samples, language) { heard, timing ->
            if (timing != null) {
                ime.prefs().edit().putString(
                    VoiceModels.PREF_LAST_TIMING,
                    "${timing.speechMillis} ${timing.loadMillis} ${timing.workMillis} ${timing.model}",
                ).apply()
            }
            if (mine != session) return@transcribe
            state = State.IDLE
            handler.removeCallbacks(tick)
            hideBar()
            if (heard == null) {
                toast(R.string.zt_voice_failed)
            } else {
                type(heard)
            }
        }
    }

    private fun type(heard: String) {
        val before = try {
            ime.currentInputConnection?.getTextBeforeCursor(1, 0)?.lastOrNull()
        } catch (e: Exception) {
            null
        }
        val text = VoiceAudio.textToType(heard, before)
        if (text.isEmpty()) {
            toast(R.string.zt_voice_nothing_heard)
            return
        }
        ime.onTextInput(text)
    }

    // VoiceBarView.Listener
    override fun onDone() {
        if (state == State.LISTENING) finishListening()
    }

    override fun onCancel() = cancel()

    // VoiceRecorder.Listener
    override fun onLevel(level: Float, seconds: Int, heardSpeech: Boolean) {
        if (state == State.LISTENING) bar?.showListening(seconds, level, heardSpeech)
    }

    override fun onShouldStop(nothingHeard: Boolean) {
        if (state != State.LISTENING) return
        if (nothingHeard) {
            cancel()
            toast(R.string.zt_voice_nothing_heard)
        } else {
            finishListening()
        }
    }

    override fun onError() {
        if (state != State.LISTENING) return
        cancel()
        toast(R.string.zt_voice_no_microphone)
    }

    private fun showBar() {
        val bar = bar ?: return
        bar.applyColors()
        // The bar lives in the suggestion strip's place; show that place if it is hidden.
        val container = bar.parent as? View
        if (container != null && container.visibility != View.VISIBLE) {
            containerVisibility = container.visibility
            container.visibility = View.VISIBLE
        }
        bar.visibility = View.VISIBLE
    }

    private fun hideBar() {
        val bar = bar ?: return
        bar.visibility = View.GONE
        containerVisibility?.let { (bar.parent as? View)?.visibility = it }
        containerVisibility = null
    }

    private fun openScreen(intent: Intent) {
        try {
            ime.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (e: Exception) {
            Log.w(TAG, "could not open ${intent.component}", e)
        }
    }

    private fun toast(text: Int) = KeyboardSwitcher.getInstance().showToast(ime.getString(text), true)

    companion object {
        private const val TAG = "VoiceTyping"

        /** Intent extra of the settings activity: the screen to open. */
        const val EXTRA_SCREEN = "zt_screen"
    }
}
