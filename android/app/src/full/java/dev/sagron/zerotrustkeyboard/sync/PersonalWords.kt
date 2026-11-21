// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.sync

import android.content.ContentValues
import android.content.Context
import android.provider.UserDictionary.Words

/**
 * The personal dictionary (words and shortcuts the user added), read and changed for the
 * ZT app's encrypted sync. It lives in Android's user dictionary, which only a keyboard
 * that is turned on may open.
 */
object PersonalWords {
    private const val WORD = "word"
    private const val SHORTCUT = "shortcut"
    private const val LOCALE = "locale"
    private const val WEIGHT = "weight"
    private const val DEFAULT_WEIGHT = 250

    /**
     * Every entry, as maps for the Flutter side. An empty [SHORTCUT] or [LOCALE] means none.
     * Null when the dictionary could not be read: that must never look like an empty
     * dictionary, or the sync would remove every word from the account.
     */
    fun all(context: Context): List<Map<String, Any>>? {
        val entries = ArrayList<Map<String, Any>>()
        val cursor = context.contentResolver.query(
            Words.CONTENT_URI, arrayOf(Words.WORD, Words.SHORTCUT, Words.LOCALE, Words.FREQUENCY), null, null, null
        ) ?: return null
        cursor.use {
            while (cursor.moveToNext()) {
                val word = cursor.getString(0)
                if (word.isNullOrEmpty()) continue
                entries.add(
                    mapOf(
                        WORD to word,
                        SHORTCUT to (cursor.getString(1) ?: ""),
                        LOCALE to (cursor.getString(2) ?: ""),
                        WEIGHT to (if (cursor.isNull(3)) DEFAULT_WEIGHT else cursor.getInt(3)),
                    )
                )
            }
        }
        return entries
    }

    /** Adds and removes entries; an entry that is already there is not added twice. */
    fun apply(context: Context, add: List<Map<String, Any?>>, remove: List<Map<String, Any?>>) {
        for (entry in remove) {
            val word = entry[WORD] as? String ?: continue
            val (where, args) = selection(word, entry[SHORTCUT] as? String, entry[LOCALE] as? String)
            context.contentResolver.delete(Words.CONTENT_URI, where, args)
        }
        for (entry in add) {
            val word = entry[WORD] as? String ?: continue
            if (word.isEmpty()) continue
            val shortcut = (entry[SHORTCUT] as? String).orEmpty()
            val locale = (entry[LOCALE] as? String).orEmpty()
            if (exists(context, word, shortcut, locale)) continue
            val weight = ((entry[WEIGHT] as? Number)?.toInt() ?: DEFAULT_WEIGHT).coerceIn(0, 255)
            // Written as Words.addWord does, but with the language exactly as it was read:
            // a text such as "sr__#Latn" does not survive a round trip through Locale.
            val values = ContentValues(5)
            values.put(Words.WORD, word)
            values.put(Words.FREQUENCY, weight)
            values.put(Words.LOCALE, locale.ifEmpty { null })
            values.put(Words.APP_ID, 0)
            values.put(Words.SHORTCUT, shortcut.ifEmpty { null })
            context.contentResolver.insert(Words.CONTENT_URI, values)
        }
    }

    private fun exists(context: Context, word: String, shortcut: String, locale: String): Boolean {
        val (where, args) = selection(word, shortcut, locale)
        return context.contentResolver.query(Words.CONTENT_URI, arrayOf(Words.WORD), where, args, null)
            ?.use { it.count > 0 } ?: false
    }

    /** The entry with exactly this word, shortcut and language; empty or null means none. */
    private fun selection(word: String, shortcut: String?, locale: String?): Pair<String, Array<String>> {
        val where = StringBuilder("${Words.WORD}=?")
        val args = arrayListOf(word)
        if (shortcut.isNullOrEmpty()) {
            where.append(" AND (${Words.SHORTCUT} IS NULL OR ${Words.SHORTCUT}='')")
        } else {
            where.append(" AND ${Words.SHORTCUT}=?")
            args.add(shortcut)
        }
        if (locale.isNullOrEmpty()) {
            where.append(" AND (${Words.LOCALE} IS NULL OR ${Words.LOCALE}='')")
        } else {
            where.append(" AND ${Words.LOCALE}=?")
            args.add(locale)
        }
        return where.toString() to args.toTypedArray()
    }
}
