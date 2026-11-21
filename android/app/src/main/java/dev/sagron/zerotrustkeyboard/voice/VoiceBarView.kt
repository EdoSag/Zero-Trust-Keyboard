// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * The bar shown in place of the suggestions while voice typing is listening or working:
 * a sound level dot, what is happening, and Cancel / Done.
 */
class VoiceBarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    interface Listener {
        fun onDone()
        fun onCancel()
    }

    var listener: Listener? = null

    private val level = LevelDot(context)
    private val status = TextView(context)
    private val cancel = button(R.string.zt_voice_cancel) { listener?.onCancel() }
    private val done = button(R.string.zt_voice_done) { listener?.onDone() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // Swallow touches, so nothing under the bar is pressed.
        isClickable = true
        val pad = dp(12)
        setPadding(pad, 0, pad, 0)
        addView(level, LayoutParams(dp(28), dp(28)))
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        status.maxLines = 1
        status.setPadding(dp(10), 0, dp(10), 0)
        addView(status, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(cancel)
        addView(done)
    }

    private fun button(text: Int, onClick: () -> Unit) = TextView(context).apply {
        setText(text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        minWidth = dp(48)
        minHeight = dp(40)
        setPadding(dp(12), 0, dp(12), 0)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    /** Takes the colours of the keyboard theme in use. */
    fun applyColors() {
        val colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.STRIP_BACKGROUND)
        val text = colors.get(ColorType.KEY_TEXT)
        status.setTextColor(text)
        cancel.setTextColor(text)
        done.setTextColor(text)
        level.color = text
    }

    fun showListening(seconds: Int, loudness: Float, heardSpeech: Boolean) {
        level.visibility = VISIBLE
        level.level = loudness
        done.visibility = VISIBLE
        status.text = if (heardSpeech || seconds > 0)
            context.getString(R.string.zt_voice_listening_time, seconds / 60, seconds % 60)
        else context.getString(R.string.zt_voice_listening)
    }

    /** @param seconds how long the phone has been working on the words */
    fun showWorking(seconds: Int) {
        level.visibility = GONE
        done.visibility = GONE
        status.text = if (seconds < 2) context.getString(R.string.zt_voice_working)
        else context.getString(R.string.zt_voice_working_time, seconds / 60, seconds % 60)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    /** A dot that grows with the loudness of the voice. */
    private class LevelDot(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var color = 0
            set(value) { field = value; invalidate() }
        var level = 0f
            set(value) { field = value; invalidate() }

        override fun onDraw(canvas: Canvas) {
            val max = minOf(width, height) / 2f
            paint.color = color
            paint.alpha = 70
            canvas.drawCircle(width / 2f, height / 2f, max * (0.45f + 0.55f * level), paint)
            paint.alpha = 255
            canvas.drawCircle(width / 2f, height / 2f, max * 0.4f, paint)
        }
    }
}
