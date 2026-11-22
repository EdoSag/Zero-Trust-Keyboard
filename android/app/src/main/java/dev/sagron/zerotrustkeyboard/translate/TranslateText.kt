// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

/** What the Translate key works out about the text in front of it, without any model. */
object TranslateText {
    /** The longest text translated in one go. Longer text is translated a selection at a time. */
    const val MAX_CHARS = 2000

    /** The writing system most of the letters of [text] are in, or null when it has too few letters to tell. */
    fun script(text: String): String? {
        val counts = HashMap<String, Int>()
        var letters = 0
        var index = 0
        while (index < text.length) {
            val code = text.codePointAt(index)
            index += Character.charCount(code)
            val script = scriptOf(code) ?: continue
            counts[script] = (counts[script] ?: 0) + 1
            letters++
        }
        if (letters < 2) return null
        val (script, count) = counts.maxByOrNull { it.value } ?: return null
        return if (count * 2 > letters) script else null
    }

    private fun scriptOf(code: Int): String? = when (code) {
        in 0x41..0x5A, in 0x61..0x7A, in 0xC0..0x24F -> "latin"
        in 0x590..0x5FF -> "hebrew"
        in 0x600..0x6FF, in 0x750..0x77F -> "arabic"
        in 0x400..0x52F -> "cyrillic"
        in 0x370..0x3FF -> "greek"
        in 0x900..0x97F -> "devanagari"
        in 0x980..0x9FF -> "bengali"
        in 0xA80..0xAFF -> "gujarati"
        in 0xB80..0xBFF -> "tamil"
        in 0xC00..0xC7F -> "telugu"
        in 0xC80..0xCFF -> "kannada"
        in 0xD00..0xD7F -> "malayalam"
        in 0xE00..0xE7F -> "thai"
        in 0x3040..0x30FF -> "kana"
        in 0xAC00..0xD7AF -> "hangul"
        in 0x4E00..0x9FFF -> "han"
        else -> null
    }

    /** The writing system of a language, by its translation code. */
    fun scriptOf(language: String): String = when (language) {
        "he" -> "hebrew"
        "ar", "fa", "ur", "ug" -> "arabic"
        "ru", "uk", "be", "bg", "sr" -> "cyrillic"
        "el" -> "greek"
        "hi", "mr" -> "devanagari"
        "bn" -> "bengali"
        "gu" -> "gujarati"
        "ta" -> "tamil"
        "te" -> "telugu"
        "kn" -> "kannada"
        "ml" -> "malayalam"
        "th" -> "thai"
        "ja" -> "kana"
        "ko" -> "hangul"
        "zh", "zh_hant" -> "han"
        else -> "latin"
    }

    /**
     * Which way to translate [text] between the user's two languages, as (from, to).
     * When the two are written differently (Hebrew and English), the letters of the text
     * decide. Otherwise the language the keyboard is in is the one the text is taken to be
     * in: typing on the [other] language's keyboard translates back to [mine].
     */
    fun direction(text: String, mine: String, other: String, keyboard: String): Pair<String, String> {
        val mineScript = scriptOf(mine)
        val otherScript = scriptOf(other)
        if (mineScript != otherScript) {
            when (script(text)) {
                mineScript -> return mine to other
                otherScript -> return other to mine
            }
        }
        return if (keyboard == other) other to mine else mine to other
    }

    /** A piece of the text: [text] is translated, [before] and [after] (spaces, line ends) are kept as they are. */
    data class Piece(val before: String, val text: String, val after: String)

    /** The text a line at a time, so line breaks and the spaces around a line survive the translation. */
    fun pieces(text: String): List<Piece> {
        val pieces = ArrayList<Piece>()
        var start = 0
        while (start <= text.length) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            val line = text.substring(start, end)
            val core = line.trim()
            val lead = line.length - line.trimStart().length
            val before = line.substring(0, if (core.isEmpty()) line.length else lead)
            val after = (if (core.isEmpty()) "" else line.substring(lead + core.length)) + if (end < text.length) "\n" else ""
            pieces.add(Piece(before, core, after))
            start = end + 1
        }
        return pieces
    }

    /** Puts [translated] (one per piece that had text, in order) back between the kept parts. */
    fun join(pieces: List<Piece>, translated: List<String>): String {
        val out = StringBuilder()
        var next = 0
        for (piece in pieces) {
            out.append(piece.before)
            if (piece.text.isNotEmpty()) out.append(translated.getOrElse(next++) { piece.text }.trim())
            out.append(piece.after)
        }
        return out.toString()
    }

    /**
     * True when [whole], a stretch of the text field that begins at position [offset] of it,
     * has exactly [expected] at position [start] of the text field.
     */
    fun holds(whole: String, offset: Int, start: Int, expected: String): Boolean {
        val from = start - offset
        if (from < 0 || from + expected.length > whole.length) return false
        return whole.regionMatches(from, expected, 0, expected.length)
    }
}
