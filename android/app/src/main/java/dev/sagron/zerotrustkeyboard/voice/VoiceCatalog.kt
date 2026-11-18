// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.voice

/** A speech model that can be downloaded: one line of the list published with the models. */
data class VoiceOffer(
    val file: String,
    val bytes: Long,
    val sha256: String,
    /** Whisper language codes the model is tuned for; empty for a model for every language. */
    val languages: List<String>,
    val labelEn: String,
    val labelHe: String,
) {
    fun label(hebrew: Boolean): String = if (hebrew && labelHe.isNotEmpty()) labelHe else labelEn
}

/**
 * The list of speech models to download, and the choice of the model for a language.
 * The models are mirrored to a release of the keyboard's own repository by the
 * "Voice models" workflow (voice-models/ in the repository), which also writes the list.
 */
object VoiceCatalog {
    const val BASE_URL = "https://github.com/EdoSag/Zero-Trust-Keyboard/releases/download/voice-models/"
    const val LIST_FILE = "voice-models.tsv"

    private val SHA256 = Regex("[0-9a-f]{64}")
    private val LANGUAGE = Regex("[a-z]{2,3}")

    /**
     * Reads the list: one model per line, fields separated by tabs:
     * file, bytes, sha256, languages ("-" or codes separated by commas), English name, Hebrew name.
     * Lines that start with # and lines that are not well formed are skipped.
     */
    fun parse(text: String): List<VoiceOffer> = text.lineSequence().mapNotNull { line ->
        if (line.startsWith("#")) return@mapNotNull null
        val fields = line.trimEnd('\r').split('\t')
        if (fields.size < 5) return@mapNotNull null
        val file = fields[0]
        val bytes = fields[1].toLongOrNull() ?: return@mapNotNull null
        val sha256 = fields[2].lowercase()
        val languages = if (fields[3] == "-") emptyList() else fields[3].split(',')
        // The file name becomes a file in the keyboard's storage: only plain names pass.
        if (file != VoiceModels.fileNameFor(file) || bytes <= 0 || !SHA256.matches(sha256)) return@mapNotNull null
        if (languages.any { !LANGUAGE.matches(it) }) return@mapNotNull null
        VoiceOffer(file, bytes, sha256, languages, fields[4], fields.getOrElse(5) { "" })
    }.toList()

    /**
     * The language a model file is for, from its name: "ggml-base.en.bin" is English only,
     * "ggml-ivrit-large-v3-turbo-q5_0.he.bin" is tuned for Hebrew. Null for every language.
     */
    fun languageOf(fileName: String): String? {
        val base = fileName.removeSuffix(".bin")
        val last = base.substringAfterLast('.', "")
        return if (last != base && LANGUAGE.matches(last)) last else null
    }

    /**
     * The model to use for speech in [language] (a Whisper code, or "auto").
     * A model tuned for the language wins; then a model for every language; a model for
     * another language is the last resort. [chosen] is the user's pick for a group, by
     * language code, with "" for the models for every language.
     */
    fun choose(installed: List<String>, chosen: Map<String, String>, language: String): String? {
        fun pick(group: List<String>, key: String) = group.firstOrNull { it == chosen[key] } ?: group.firstOrNull()
        val tuned = installed.filter { languageOf(it) == language }
        val general = installed.filter { languageOf(it) == null }
        return pick(tuned, language) ?: pick(general, "") ?: installed.firstOrNull()
    }

    /** The model in use in each group: the models for every language, and each language's tuned models. */
    fun picks(installed: List<String>, chosen: Map<String, String>): Set<String> =
        installed.groupBy { languageOf(it) ?: "" }
            .mapNotNull { (key, group) -> group.firstOrNull { it == chosen[key] } ?: group.firstOrNull() }
            .toSet()
}
