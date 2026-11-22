// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.insert

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * The Zero-Trust apps on this phone that can hand a text to the keyboard: a contact's
 * number from ZT Contacts, a line of a note, a date.
 *
 * How it works: the keyboard opens the other app's own "pick" screen, the user unlocks that
 * app as usual and chooses, and the app gives back only the chosen text. The keyboard never
 * reads another app's data. Both sides check that the other one is signed with the same
 * key, so no other app can offer itself here or ask a Zero-Trust app for a value.
 */
object ZtApps {
    /** Asks a Zero-Trust app to let the user pick a text. Answered with [EXTRA_TEXT]. */
    const val ACTION_PICK_TEXT = "bz.zerotrust.action.PICK_TEXT"
    const val EXTRA_TEXT = "bz.zerotrust.extra.TEXT"

    class Source(val component: ComponentName, val label: String)

    /** The apps that answer [ACTION_PICK_TEXT] and are signed with the keyboard's own key. */
    fun sources(context: Context): List<Source> {
        val pm = context.packageManager
        val found = try {
            pm.queryIntentActivities(Intent(ACTION_PICK_TEXT), 0)
        } catch (e: Exception) {
            emptyList()
        }
        return found.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (activity.packageName == context.packageName) return@mapNotNull null
            if (pm.checkSignatures(context.packageName, activity.packageName) != PackageManager.SIGNATURE_MATCH)
                return@mapNotNull null
            val label = activity.applicationInfo.loadLabel(pm).toString()
            Source(ComponentName(activity.packageName, activity.name), label)
        }.sortedBy { it.label.lowercase() }
    }

    fun pickIntent(source: Source): Intent = Intent(ACTION_PICK_TEXT).setComponent(source.component)
}
