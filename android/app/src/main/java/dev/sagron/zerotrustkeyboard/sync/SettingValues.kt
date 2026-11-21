// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.sync

/**
 * Which keyboard settings sync, and how a value travels. Plain Kotlin in the shared sources
 * (the sync itself is only in the full edition), so it is tested without a phone.
 *
 * A setting travels under its name with its type in front ("b:auto_cap"): the Flutter
 * side and JSON cannot tell an Int from a Long or a Float, and writing a setting back with
 * the wrong type would crash the keyboard the next time it reads it.
 */
object SettingValues {
    /** Settings that belong to this phone and never leave it. */
    private val STAYS = setOf(
        // What was used last, and what the app remembers about itself.
        "selected_subtype", "recent_emojis", "last_shown_emoji_category_id", "last_shown_emoji_category_page_id",
        "dont_show_missing_dict_dialog", "version_code", "lib_checksum", "emoji_max_sdk", "show_setup_wizard_icon",
        // The voice model is a file downloaded on this phone, and its speed is this phone's.
        "zt_voice_model", "zt_voice_last_timing",
        // Which release's dictionaries are unpacked on this phone.
        "zt_bundled_dictionaries_version",
    )

    /** The same, for settings whose names continue (per orientation, per app, per layout). */
    private val STAYS_PREFIXES = listOf(
        // Sizes and positions fit this phone's screen.
        "keyboard_height_scale", "bottom_row_scale", "bottom_padding_scale", "side_padding_scale",
        "split_spacer_scale", "split_keyboard", "floating_", "one_handed_mode_",
        // Point to files that are only on this phone, or to apps installed on it.
        "layout_", "saved_app_subtype_", "gesture_data",
    )

    /** The settings that list languages with their layouts. */
    private val LAYOUT_LISTS = setOf("additional_subtypes", "enabled_subtypes")

    /** How a layout the user made is named inside those lists; its file is only on this phone. */
    private const val OWN_LAYOUT = "custom."

    /**
     * True for a language list that names a layout the user made. The list then stays as it
     * is on this phone and is neither sent nor replaced: another phone does not have the
     * layout's file, and taking that phone's list would drop the layout here.
     */
    fun held(name: String, value: Any?): Boolean =
        name in LAYOUT_LISTS && value is String && value.contains(OWN_LAYOUT)

    fun syncs(name: String): Boolean =
        name.isNotEmpty() && name !in STAYS && STAYS_PREFIXES.none { name.startsWith(it) }

    /**
     * A setting as it travels: typed name and a value the Flutter channel and JSON keep
     * exactly. Null for a setting that stays, and for a value of a type settings never have.
     */
    fun encode(name: String, value: Any?): Pair<String, Any>? {
        if (!syncs(name) || held(name, value)) return null
        return when (value) {
            is Boolean -> "b:$name" to value
            is Int -> "i:$name" to value
            is Long -> "l:$name" to value
            // As text: a Float sent as a number comes back as a slightly different Double.
            is Float -> "f:$name" to value.toString()
            is String -> "s:$name" to value
            is Set<*> -> "S:$name" to value.filterIsInstance<String>().sorted()
            else -> null
        }
    }

    /**
     * The setting's name and its value with the type it must be written with (a Set for a
     * string set). Null when the typed name or the value is not what [encode] makes, or the
     * setting stays on the phone: such an entry is skipped, never written.
     */
    fun decode(typedName: String, value: Any?): Pair<String, Any>? {
        if (typedName.length < 3 || typedName[1] != ':') return null
        val name = typedName.substring(2)
        if (!syncs(name)) return null
        val decoded: Any? = when (typedName[0]) {
            'b' -> value as? Boolean
            'i' -> (value as? Number)?.takeIf { it !is Float && it !is Double }?.toLong()
                ?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
            'l' -> (value as? Number)?.takeIf { it !is Float && it !is Double }?.toLong()
            'f' -> (value as? String)?.toFloatOrNull()?.takeIf { it.isFinite() }
            's' -> value as? String
            'S' -> (value as? List<*>)?.takeIf { list -> list.all { it is String } }?.filterIsInstance<String>()?.toSet()
            else -> null
        }
        return decoded?.takeIf { !held(name, it) }?.let { name to it }
    }

    /** The name inside a typed name, or null when it is not one or the setting stays. */
    fun nameOf(typedName: String): String? {
        if (typedName.length < 3 || typedName[1] != ':') return null
        return typedName.substring(2).takeIf { syncs(it) }
    }
}
