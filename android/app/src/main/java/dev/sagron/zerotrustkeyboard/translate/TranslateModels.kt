// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import android.content.Context
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import java.io.File

/**
 * The language models on this phone, and the two languages the Translate key works
 * between. The models are kept in the keyboard's own storage:
 *
 *   translate/installed.json      the models in use, in the form of the signed list
 *   translate/registry.json       the same, as the engine wants it
 *   translate/<pair>/<file>       the files of a model, for example translate/he-en/vocab.heen.spm
 */
object TranslateModels {
    private const val TAG = "TranslateModels"

    /** The language the user writes in, and the one to translate to and from. */
    const val PREF_MINE = "zt_translate_mine"
    const val PREF_OTHER = "zt_translate_other"

    const val INSTALLED_FILE = "installed.json"
    const val REGISTRY_FILE = "registry.json"

    /** The address the engine asks for model files at; it exists only inside the keyboard. */
    const val MODELS_ORIGIN = "https://translate.zt/models"

    fun directory(context: Context): File =
        File(DeviceProtectedUtils.getFilesDir(context), "translate").apply { mkdirs() }

    /** The models on the phone, by pair. A model with a file missing is not one. */
    fun installed(context: Context): Map<String, TranslateModel> {
        val root = directory(context)
        val text = try {
            File(root, INSTALLED_FILE).takeIf { it.isFile }?.readText()
        } catch (e: Exception) {
            null
        } ?: return emptyMap()
        return TranslateCatalog.parse(text).orEmpty().filterValues { model ->
            model.files.values.all { File(File(root, model.pair), it.name).isFile }
        }
    }

    fun add(context: Context, model: TranslateModel) = save(context, installed(context) + (model.pair to model))

    fun delete(context: Context, pair: String) {
        save(context, installed(context) - pair)
        File(directory(context), pair).deleteRecursively()
    }

    private fun save(context: Context, models: Map<String, TranslateModel>) {
        val root = directory(context)
        try {
            write(File(root, INSTALLED_FILE), TranslateCatalog.write(models.values))
            write(File(root, REGISTRY_FILE), TranslateCatalog.engineRegistry(models.values, MODELS_ORIGIN))
        } catch (e: Exception) {
            Log.w(TAG, "could not save the list of models", e)
        }
        // The engine reads the list again before its next translation.
        TranslateEngine.reset()
    }

    private fun write(file: File, text: String) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(file)) {
            file.delete()
            temporary.renameTo(file)
        }
    }

    /**
     * The user's two languages as (mine, other). Until they are chosen: the first keyboard
     * language that is not English, and English.
     */
    fun languages(context: Context): Pair<String, String> {
        val prefs = context.prefs()
        val other = prefs.getString(PREF_OTHER, null) ?: TranslateCatalog.PIVOT
        val mine = prefs.getString(PREF_MINE, null) ?: defaultMine(other)
        return mine to other
    }

    private fun defaultMine(other: String): String {
        val keyboards = try {
            SubtypeSettings.getEnabledSubtypes(true).map { TranslateCatalog.code(it.locale().toLanguageTag()) }
        } catch (e: Exception) {
            emptyList()
        }
        return keyboards.firstOrNull { it != other } ?: if (other == "he") TranslateCatalog.PIVOT else "he"
    }

    fun setLanguages(context: Context, mine: String, other: String) {
        context.prefs().edit().putString(PREF_MINE, mine).putString(PREF_OTHER, other).apply()
    }
}
