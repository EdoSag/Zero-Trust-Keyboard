// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.sync

import android.content.Context
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.database.ClipboardDao

/**
 * The text clips pinned in the keyboard's clipboard, read and changed for the ZT app's
 * encrypted sync. Pictures and files are left out, and so are very long texts: they stay
 * on this phone. Call on the main thread, like the clipboard view does.
 */
object PinnedClips {
    /** Longer texts are not synced. */
    const val MAX_LENGTH = 20_000

    /**
     * Every pinned text clip. Null when the clipboard could not be opened: that must never
     * look like "nothing is pinned", or the sync would remove every clip from the account.
     */
    fun all(context: Context): List<String>? {
        val dao = ClipboardDao.getInstance(context) ?: return null
        return dao.getAll().toList().mapNotNull { entry -> if (entry.isPinned) syncable(entry) else null }
    }

    /**
     * Pins [add], adding the clips the clipboard does not hold yet, and unpins [remove]: a
     * clip that was unpinned or deleted on another phone goes back to being ordinary
     * history here, and leaves with it. False when the clipboard could not be opened.
     */
    fun apply(context: Context, add: List<String>, remove: List<String>): Boolean {
        val dao = ClipboardDao.getInstance(context) ?: return false
        for (text in remove) {
            val entry = dao.getAll().toList().firstOrNull { it.isPinned && syncable(it) == text } ?: continue
            dao.togglePinned(entry.id)
        }
        for (text in add) {
            if (text.isEmpty() || text.length > MAX_LENGTH) continue
            val entry = dao.getAll().toList().firstOrNull { syncable(it) == text }
            if (entry == null) {
                dao.insertNewEntry(System.currentTimeMillis(), true, text, null, null, null)
            } else if (!entry.isPinned) {
                dao.togglePinned(entry.id)
            }
        }
        return true
    }

    /** The text of a clip that can sync, or null for pictures, files and very long texts. */
    private fun syncable(entry: ClipboardHistoryEntry): String? {
        val text = entry.text
        if (entry.filename != null || text.isNullOrEmpty() || text.length > MAX_LENGTH) return null
        return text
    }
}
