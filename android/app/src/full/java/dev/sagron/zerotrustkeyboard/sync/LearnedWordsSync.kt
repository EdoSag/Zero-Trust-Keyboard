// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.sync

import android.content.Context
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.personalization.PersonalizationHelper
import helium314.keyboard.latin.personalization.UserHistoryDictionary
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import java.util.Locale

/**
 * The words the keyboard learned from typing, read and added to for the ZT app's encrypted
 * sync. Which words travel is in [LearnedWords]. Words are only ever added here: a word
 * another phone forgot fades on this one by itself if it is not used. Call off the main
 * thread: reading a history waits for the keyboard's dictionary thread.
 */
object LearnedWordsSync {
    /** How long to wait for one language's history before giving up on this round. */
    private const val READ_TIMEOUT_MILLIS = 5_000L

    /**
     * The learned words of every language that is turned on, by language tag. Null when
     * learning is switched off or a history could not be read: that must never look like
     * "nothing learned", or the sync would remove the words from the account.
     */
    fun all(context: Context): Map<String, List<String>>? {
        if (!learning(context)) return null
        val words = HashMap<String, List<String>>()
        for (locale in languages()) {
            val history = PersonalizationHelper.getUserHistoryDictionary(context, locale)
            val properties = history.getAllWordPropertiesOrNull(READ_TIMEOUT_MILLIS) ?: return null
            words[locale.toLanguageTag()] = LearnedWords.pick(properties.map {
                LearnedWords.Entry(
                    it.mWord,
                    it.mProbabilityInfo.mLevel,
                    it.mProbabilityInfo.mTimestamp,
                    it.mIsNotAWord || it.mIsBeginningOfSentence,
                )
            })
        }
        return words
    }

    /**
     * Adds words to the histories of the languages that are turned on here, as words the
     * keyboard may offer. False when learning is switched off, and nothing was added.
     */
    fun add(context: Context, words: Map<String, List<String>>): Boolean {
        if (!learning(context)) return false
        val languages = languages().associateBy { it.toLanguageTag() }
        val now = (System.currentTimeMillis() / 1000).toInt()
        for ((tag, list) in words) {
            val history = PersonalizationHelper.getUserHistoryDictionary(context, languages[tag] ?: continue)
            for (word in list) {
                if (!LearnedWords.valid(word)) continue
                UserHistoryDictionary.addToDictionary(history, NgramContext.EMPTY_PREV_WORDS_INFO, word, true, now)
            }
        }
        return true
    }

    private fun learning(context: Context): Boolean = context.prefs()
        .getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, Defaults.PREF_KEY_USE_PERSONALIZED_DICTS)

    /** The languages that are turned on. The app sets [SubtypeSettings] up when it starts. */
    private fun languages(): List<Locale> =
        SubtypeSettings.getEnabledSubtypes(true).map { it.locale() }.distinct()
}
