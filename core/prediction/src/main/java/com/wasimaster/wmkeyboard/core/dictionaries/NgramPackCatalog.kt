package com.wasimaster.wmkeyboard.core.dictionaries

/**
 * One downloadable n-gram pack: corpus bigram/trigram counts for a language,
 * hosted in the data repo next to its wordlists. Files are gzip text, one
 * `word... count` line each, count-descending — the same shape as every
 * other list the repo ships.
 */
class NgramPackEntry(
    /** Language id the pack serves ([com.wasimaster.wmkeyboard.core.language] registry). */
    val languageId: String,
    /** Directory under `data/` in the repo (languages share one, e.g. `bn`). */
    val repoCode: String,
    /** File stem, e.g. `bn_rom_bigrams` -> `data/bn/bn_rom_bigrams.txt.gz`. */
    val bigramStem: String,
    val trigramStem: String,
) {
    fun bigramUrl(): String = url(bigramStem)
    fun trigramUrl(): String = url(trigramStem)

    private fun url(stem: String): String =
        "https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/HEAD/data/$repoCode/$stem.txt.gz"
}

/**
 * Every language with a published n-gram pack. Small on purpose: entries are
 * added as the data repo grows them (currently English and romanized
 * Bengali).
 */
object NgramPackCatalog {

    val entries: List<NgramPackEntry> = listOf(
        NgramPackEntry(
            languageId = "en",
            repoCode = "en",
            bigramStem = "en_bigrams",
            trigramStem = "en_trigrams",
        ),
        // Banglish: bn_rom is its own first-class language (Latin script,
        // plain QWERTY, transliterated wordlist), so its romanized pairs
        // match that language's typing directly — the ordinary pipeline,
        // nothing Avro-specific. The files live under the repo's bn/ dir.
        NgramPackEntry(
            languageId = "bn_rom",
            repoCode = "bn",
            bigramStem = "bn_rom_bigrams",
            trigramStem = "bn_rom_trigrams",
        ),
    )

    fun forLanguage(languageId: String): NgramPackEntry? =
        entries.firstOrNull { it.languageId == languageId }
}
