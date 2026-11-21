// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.sync

import android.content.Context
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs

/**
 * The keyboard's own settings, read and changed for the ZT app's encrypted sync. Which
 * settings travel, and how, is in [SettingValues].
 */
object KeyboardSettingsSync {
    /**
     * The settings that sync, with the version of the settings format: a newer keyboard can
     * move settings to new names, so the app only takes settings written by the same version.
     */
    fun all(context: Context): Map<String, Any> {
        val values = HashMap<String, Any>()
        for ((name, value) in context.prefs().all) {
            val (typedName, encoded) = SettingValues.encode(name ?: continue, value) ?: continue
            values[typedName] = encoded
        }
        return mapOf("version" to BuildConfig.UPSTREAM_VERSION_CODE, "prefs" to values)
    }

    /**
     * Writes [set] and puts [remove] back to their defaults. An entry that is not a setting
     * that syncs, or whose value has the wrong type, is skipped. A setting this phone holds
     * with another type is skipped too: writing over it would crash the keyboard.
     */
    fun apply(context: Context, set: Map<String, Any?>, remove: List<String>) {
        val prefs = context.prefs()
        val current = prefs.all
        prefs.edit {
            for (typedName in remove) {
                remove(SettingValues.nameOf(typedName) ?: continue)
            }
            for ((typedName, value) in set) {
                val (name, decoded) = SettingValues.decode(typedName, value) ?: continue
                val old = current[name]
                if (old != null && !sameType(old, decoded)) continue
                when (decoded) {
                    is Boolean -> putBoolean(name, decoded)
                    is Int -> putInt(name, decoded)
                    is Long -> putLong(name, decoded)
                    is Float -> putFloat(name, decoded)
                    is String -> putString(name, decoded)
                    is Set<*> -> putStringSet(name, decoded.filterIsInstance<String>().toSet())
                }
            }
        }
        // Settings reloads itself when a setting changes; languages and the theme do not.
        runCatching { SubtypeSettings.reloadEnabledSubtypes(context) }
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    private fun sameType(a: Any, b: Any): Boolean = when (a) {
        is Set<*> -> b is Set<*>
        else -> a::class == b::class
    }
}
