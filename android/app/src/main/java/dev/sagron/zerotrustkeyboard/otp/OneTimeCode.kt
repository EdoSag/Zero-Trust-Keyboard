// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.otp

/**
 * Finds the one-time code in a text message, such as "Your code is 482913" or
 * "קוד האימות שלך: 482913".
 *
 * Only messages that say they carry a code count, so an amount, a phone number or an order
 * number in an ordinary message is never offered. The code is 4 to 8 digits; "482 913" and
 * "482-913" are read as 482913, and "G-482913" as 482913.
 */
object OneTimeCode {
    private val KEYWORDS = Regex(
        "code|otp|passcode|password|pin\\b|verif|one[- ]time|2fa|two[- ]factor|log ?in|sign ?in|token" +
            "|קוד|סיסמ|אימות|לאימות|הזדהות|כניסה|התחברות|קוד חד[- ]פעמי",
        RegexOption.IGNORE_CASE,
    )

    // A run of 4 to 8 digits, or two groups of 3 or 4 split by one space or dash, that is not
    // part of a longer number, a word, a decimal or a time of day.
    private val CANDIDATE = Regex(
        "(?<![\\p{L}\\p{N}.,/+#-])(?:\\p{Lu}-)?(\\d{3,4})[ -](\\d{3,4})(?![\\p{N}.,:/-]\\d|\\p{L}|\\d)" +
            "|(?<![\\p{L}\\p{N}.,/+#-])(?:\\p{Lu}-)?(\\d{4,8})(?![\\p{N}]|[.,:/-]\\d|\\p{L})"
    )

    // Money, percentages and units right after a number: not a code.
    private val NOT_A_CODE_AFTER = Regex("^\\s*(?:%|₪|\\$|€|£|ש\"ח|ש״ח|שח\\b|nis\\b|ils\\b|usd\\b|eur\\b|שקל|דולר|יורו|אחוז)", RegexOption.IGNORE_CASE)
    private val NOT_A_CODE_BEFORE = Regex("(?:₪|\\$|€|£|nis|ils|usd|eur)\\s*$", RegexOption.IGNORE_CASE)

    /** The code in [message], or null when it carries none. */
    fun find(message: String?): String? {
        if (message.isNullOrBlank()) return null
        val keyword = KEYWORDS.find(message) ?: return null
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        for (match in CANDIDATE.findAll(message)) {
            val code = if (match.groupValues[3].isNotEmpty()) match.groupValues[3]
                else match.groupValues[1] + match.groupValues[2]
            if (code.length !in 4..8) continue
            if (NOT_A_CODE_AFTER.containsMatchIn(message.substring(match.range.last + 1))) continue
            if (NOT_A_CODE_BEFORE.containsMatchIn(message.substring(0, match.range.first))) continue
            if (looksLikeAYear(code, message, match.range)) continue
            // The code usually follows the word "code"; prefer the nearest number after it,
            // then the nearest one before it.
            val distance = if (match.range.first >= keyword.range.last) match.range.first - keyword.range.last
                else 10_000 + keyword.range.first - match.range.last
            if (distance < bestDistance) {
                best = code
                bestDistance = distance
            }
        }
        return best
    }

    private fun looksLikeAYear(code: String, message: String, range: IntRange): Boolean {
        if (code.length != 4 || !(code.startsWith("19") || code.startsWith("20"))) return false
        // "2026" in "1.1.2026" is already left out; here a year written alone near a month.
        val around = message.substring(maxOf(0, range.first - 12), minOf(message.length, range.last + 13))
        return Regex("jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|ינו|פבר|מרץ|אפר|מאי|יוני|יולי|אוג|ספט|אוק|נוב|דצמ", RegexOption.IGNORE_CASE)
            .containsMatchIn(around)
    }
}
