// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.translate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** One file of a language model, as the signed list describes it. */
data class TranslateFile(
    /** The file's name on the release (gzip). */
    val asset: String,
    /** Mozilla's name for it, unpacked; it tells the engine the model's precision. */
    val name: String,
    val gzBytes: Long,
    val bytes: Long,
    val sha256: String,
)

/** A model translating [from] one language [to] another. */
data class TranslateModel(val from: String, val to: String, val files: Map<String, TranslateFile>) {
    /** "en-he". */
    val pair: String get() = "$from-$to"
    val downloadBytes: Long get() = files.values.sumOf { it.gzBytes }
    val diskBytes: Long get() = files.values.sumOf { it.bytes }
}

/**
 * The language models for translating on the phone. They are Mozilla's Firefox
 * Translations models (MPL-2.0), the same ones ZT Browser uses, copied to a release of
 * the ZT Browser repository together with a list signed by a key whose private half is a
 * GitHub Actions secret. Only a list with that signature is used, and each file must have
 * the size and sha256 the list gives, so a changed file on the download server is refused.
 */
object TranslateCatalog {
    const val BASE_URL = "https://github.com/EdoSag/Zero-Trust-Browser/releases/download/translation-models/"
    const val LIST_FILE = "models.json"
    const val SIGNATURE_FILE = "models.json.sig"

    /** Every model translates to or from English; other pairs go through it. */
    const val PIVOT = "en"

    /** The parts of a model, in the order they are downloaded. */
    val PARTS = listOf("model", "lex", "vocab")

    /** The public half of the signing key (Ed25519), the same one ZT Browser trusts. */
    private val PUBLIC_KEY = hex("1e5b14ece67006930d44da82167fbeb27f862faa3eac41b57fc28ea4ee15da6a")

    private val SAFE_NAME = Regex("[A-Za-z0-9._-]+")
    private val LANGUAGE = Regex("[a-z]{2,3}(_[a-z]{2,4})?")
    private val SHA256 = Regex("[0-9a-f]{64}")

    /** The models in the list [bytes], when [signature] (base64 text) signs it with the trusted key. */
    fun verified(bytes: ByteArray, signature: String, publicKey: ByteArray = PUBLIC_KEY): Map<String, TranslateModel>? {
        val raw = base64(signature.trim()) ?: return null
        if (!Ed25519.verify(publicKey, bytes, raw)) return null
        return parse(String(bytes, Charsets.UTF_8))
    }

    /** Reads a list of models. Entries that are not well formed are left out; null when it is no list at all. */
    fun parse(text: String): Map<String, TranslateModel>? {
        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        if ((root["version"] as? JsonPrimitive)?.intOrNull != 1) return null
        val models = root["models"] as? JsonObject ?: return null
        val result = LinkedHashMap<String, TranslateModel>()
        for ((pair, value) in models) {
            val entry = value as? JsonObject ?: continue
            val from = entry.text("from") ?: continue
            val to = entry.text("to") ?: continue
            if (pair != "$from-$to" || !LANGUAGE.matches(from) || !LANGUAGE.matches(to)) continue
            val files = entry["files"] as? JsonObject ?: continue
            val parts = PARTS.associateWith { file(files[it]) ?: return@associateWith null }
            if (parts.values.any { it == null }) continue
            @Suppress("UNCHECKED_CAST")
            result[pair] = TranslateModel(from, to, parts as Map<String, TranslateFile>)
        }
        return result
    }

    private fun file(value: JsonElement?): TranslateFile? {
        val entry = value as? JsonObject ?: return null
        val asset = entry.text("asset") ?: return null
        val name = entry.text("name") ?: return null
        val gzBytes = (entry["gzSize"] as? JsonPrimitive)?.longOrNull ?: return null
        val bytes = (entry["size"] as? JsonPrimitive)?.longOrNull ?: return null
        val sha256 = entry.text("sha256")?.lowercase() ?: return null
        // The names become files on the phone: only plain names pass.
        if (!SAFE_NAME.matches(asset) || !SAFE_NAME.matches(name) || name.startsWith(".")) return null
        if (gzBytes <= 0 || bytes <= 0 || bytes > MAX_FILE_BYTES || !SHA256.matches(sha256)) return null
        return TranslateFile(asset, name, gzBytes, bytes, sha256)
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** A list of models in the form [parse] reads: what is kept on the phone for the models in use. */
    fun write(models: Collection<TranslateModel>): String = buildJsonObject {
        put("version", 1)
        putJsonObject("models") {
            for (model in models) putJsonObject(model.pair) {
                put("from", model.from)
                put("to", model.to)
                putJsonObject("files") {
                    for ((part, file) in model.files) putJsonObject(part) {
                        put("asset", file.asset)
                        put("name", file.name)
                        put("gzSize", file.gzBytes)
                        put("size", file.bytes)
                        put("sha256", file.sha256)
                    }
                }
            }
        }
    }.toString()

    /** The engine's own list of the models on the phone; it asks for the files at [origin]. */
    fun engineRegistry(installed: Collection<TranslateModel>, origin: String): String = buildJsonArray {
        for (model in installed) add(buildJsonObject {
            put("from", model.from)
            put("to", model.to)
            putJsonObject("files") {
                for ((part, file) in model.files) putJsonObject(part) {
                    put("name", "$origin/${model.pair}/${file.name}")
                }
                putJsonObject("config") {
                    // Mozilla's "alphas" models carry their own quantisation scales.
                    val alphas = model.files["model"]?.name?.contains("alphas") == true
                    put("gemm-precision", if (alphas) "int8shiftAlphaAll" else "int8shiftAll")
                }
            }
        })
    }.toString()

    /**
     * The models needed to translate [from] to [to]: the direct one, or two through English.
     * Null when the list has no way.
     */
    fun route(models: Map<String, TranslateModel>, from: String, to: String): List<TranslateModel>? {
        if (from == to) return null
        models["$from-$to"]?.let { return listOf(it) }
        if (from == PIVOT || to == PIVOT) return null
        val first = models["$from-$PIVOT"] ?: return null
        val second = models["$PIVOT-$to"] ?: return null
        return listOf(first, second)
    }

    /** Languages that can be translated in both directions, English included, by code. */
    fun languages(models: Map<String, TranslateModel>): List<String> {
        val both = models.values.map { if (it.from == PIVOT) it.to else it.from }
            .filter { "$it-$PIVOT" in models && "$PIVOT-$it" in models }
        return (both + PIVOT).distinct().sortedBy { name(it).lowercase() }
    }

    /** The translation code of a keyboard language: "iw" and "he-IL" are "he". */
    fun code(languageTag: String): String {
        val language = languageTag.lowercase().substringBefore('-').substringBefore('_')
        return if (language == "iw") "he" else language
    }

    /** A language's name as its speakers write it, so it is found whatever the app's language. */
    fun name(code: String): String = NAMES[code] ?: code

    private val NAMES = mapOf(
        "af" to "Afrikaans", "ar" to "العربية", "az" to "Azərbaycan", "be" to "Беларуская",
        "bg" to "Български", "bn" to "বাংলা", "bs" to "Bosanski", "ca" to "Català", "cs" to "Čeština",
        "da" to "Dansk", "de" to "Deutsch", "el" to "Ελληνικά", "en" to "English", "es" to "Español",
        "et" to "Eesti", "eu" to "Euskara", "fa" to "فارسی", "fi" to "Suomi", "fr" to "Français",
        "gl" to "Galego", "gu" to "ગુજરાતી", "hbs" to "Srpskohrvatski", "he" to "עברית", "hi" to "हिन्दी",
        "hr" to "Hrvatski", "hu" to "Magyar", "id" to "Bahasa Indonesia", "is" to "Íslenska",
        "it" to "Italiano", "ja" to "日本語", "kn" to "ಕನ್ನಡ", "ko" to "한국어", "lt" to "Lietuvių",
        "lv" to "Latviešu", "ml" to "മലയാളം", "mr" to "मराठी", "ms" to "Bahasa Melayu",
        "nb" to "Norsk bokmål", "nl" to "Nederlands", "nn" to "Nynorsk", "no" to "Norsk", "pl" to "Polski",
        "pt" to "Português", "ro" to "Română", "ru" to "Русский", "sk" to "Slovenčina",
        "sl" to "Slovenščina", "sq" to "Shqip", "sr" to "Српски", "sv" to "Svenska", "ta" to "தமிழ்",
        "te" to "తెలుగు", "th" to "ไทย", "tl" to "Tagalog", "tr" to "Türkçe", "ug" to "ئۇيغۇرچە",
        "uk" to "Українська", "ur" to "اردو", "vi" to "Tiếng Việt", "zh" to "中文（简体）",
        "zh_hant" to "中文（繁體）",
    )

    /** No model file is anywhere near this size; a list that says otherwise is not ours. */
    private const val MAX_FILE_BYTES = 400L shl 20

    private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** Plain base64 with padding, or null. (java.util.Base64 is missing on the oldest phones the Offline edition runs on.) */
    internal fun base64(text: String): ByteArray? {
        if (text.isEmpty() || text.length % 4 != 0) return null
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val padding = text.takeLastWhile { it == '=' }.length
        if (padding > 2) return null
        val out = java.io.ByteArrayOutputStream()
        var index = 0
        while (index < text.length) {
            var group = 0
            for (offset in 0 until 4) {
                val char = text[index + offset]
                val value = if (char == '=' && index + offset >= text.length - padding) 0 else alphabet.indexOf(char)
                if (value < 0) return null
                group = (group shl 6) or value
            }
            out.write(group shr 16 and 0xff)
            out.write(group shr 8 and 0xff)
            out.write(group and 0xff)
            index += 4
        }
        return out.toByteArray().let { it.copyOf(it.size - padding) }
    }
}
