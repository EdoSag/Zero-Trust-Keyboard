// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * The bar shown in place of the suggestions while a translation (or a proofreading) is worked
 * out, and after it: what is happening, and Cancel, or Undo and Done.
 */
class TranslateBarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    interface Listener {
        fun onTranslateCancel()
        fun onTranslateUndo()
        fun onTranslateDone()
    }

    var listener: Listener? = null

    private val status = TextView(context)
    private val cancel = button(R.string.zt_translate_cancel) { listener?.onTranslateCancel() }
    private val undo = button(R.string.zt_translate_undo) { listener?.onTranslateUndo() }
    private val done = button(R.string.zt_translate_done) { listener?.onTranslateDone() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // Swallow touches, so nothing under the bar is pressed.
        isClickable = true
        val pad = dp(12)
        setPadding(pad, 0, pad, 0)
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        status.maxLines = 1
        status.ellipsize = TextUtils.TruncateAt.END
        addView(status, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(cancel)
        addView(undo)
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
        listOf(status, cancel, undo, done).forEach { it.setTextColor(text) }
    }

    /** While the phone works: [text] says what it is doing, with Cancel. */
    fun showWorking(text: String) {
        cancel.visibility = VISIBLE
        undo.visibility = GONE
        done.visibility = GONE
        status.text = text
    }

    /** When it is done: [text] says what was done, with Undo and Done. */
    fun showDone(text: String) {
        cancel.visibility = GONE
        undo.visibility = VISIBLE
        done.visibility = VISIBLE
        status.text = text
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
