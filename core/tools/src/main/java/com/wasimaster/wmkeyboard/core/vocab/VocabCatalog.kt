package com.wasimaster.wmkeyboard.core.vocab

/**
 * One downloadable vocabulary pack in the wmkeyboard-data repo
 * (https://github.com/wasi-master/wmkeyboard-data), under
 * `vocab/<langId>/<id>.wmvocab.json.gz`, with its translation sidecars at
 * `vocab/<langId>/<id>.tr.<code>.json.gz` for each of [translationCodes].
 *
 * Counts and sizes are display and progress hints, not checksums, so drift
 * against a newer repo is harmless.
 */
data class VocabCatalogEntry(
    val id: String,
    val name: String,
    val langId: String,
    /** The word list the pack was built from — the same id, for now. */
    val sourceId: String,
    val wordCount: Int,
    /** Compressed size of the pack — the progress denominator. */
    val approxGzBytes: Long,
    /** Languages a translation sidecar exists for. */
    val translationCodes: List<String> = emptyList(),
) {
    val url: String
        get() = "$BASE/$langId/$id.wmvocab.json.gz"

    fun translationUrl(code: String): String = "$BASE/$langId/$id.tr.$code.json.gz"

    private companion object {
        const val BASE = "https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/HEAD/vocab"
    }
}

/**
 * The packs the data repo carries. Regenerate with
 * `tools/vocab/generate_catalog.py --data <path to a wmkeyboard-data checkout>`.
 */
object VocabCatalog {

    // GENERATED — do not edit by hand; run tools/vocab/generate_catalog.py.
    val entries: List<VocabCatalogEntry> = listOf(
        VocabCatalogEntry(
            "b1100", "Barron's 1100", "en", "b1100",
            1085, 675769L,
            listOf(
                "ar", "az", "be", "bg", "bn", "ca", "cmn", "cs", "cy", "da", "de", "el",
                "eo", "es", "et", "fa", "fi", "fr", "ga", "gd", "gl", "grc", "he", "hi",
                "hu", "hy", "id", "io", "is", "it", "ja", "ka", "kk", "ko", "la", "mi",
                "mk", "ms", "nb", "nl", "nn", "no", "ota", "pl", "pt", "ro", "ru", "sh",
                "sk", "sl", "sv", "th", "tl", "tr", "uk", "vi",
            ),
        ),
        VocabCatalogEntry(
            "b333", "Barron's 333", "en", "b333",
            332, 231542L,
            listOf(
                "ar", "az", "be", "bg", "bn", "ca", "cmn", "cs", "cy", "da", "de", "el",
                "eo", "es", "et", "fa", "fi", "fr", "ga", "gd", "gl", "grc", "gv", "he",
                "hi", "hu", "hy", "id", "io", "is", "it", "ja", "ka", "ko", "la", "mi",
                "mk", "ms", "nb", "nl", "nn", "no", "oc", "ota", "pl", "pt", "ro", "ru",
                "sh", "sk", "sv", "th", "tl", "tr", "uk", "vi",
            ),
        ),
        VocabCatalogEntry(
            "b800", "Barron's 800", "en", "b800",
            794, 550143L,
            listOf(
                "ar", "az", "be", "bg", "bn", "ca", "cmn", "cs", "cy", "da", "de", "el",
                "eo", "es", "et", "fa", "fi", "fr", "ga", "gd", "gl", "grc", "he", "hi",
                "hu", "hy", "id", "io", "is", "it", "ja", "ka", "kk", "ko", "la", "lt",
                "mi", "mk", "ms", "nb", "nl", "nn", "no", "oc", "ota", "pl", "pt", "ro",
                "ru", "sh", "sk", "sl", "sv", "th", "tl", "tr", "uk", "vi",
            ),
        ),
        VocabCatalogEntry(
            "gm1100", "GregMat 1100", "en", "gm1100",
            744, 525804L,
            listOf(
                "ang", "ar", "az", "be", "bg", "bn", "ca", "cmn", "cs", "cy", "da", "de",
                "el", "eo", "es", "et", "fa", "fi", "fr", "ga", "gd", "gl", "grc", "he",
                "hi", "hu", "hy", "id", "io", "is", "it", "ja", "ka", "kk", "ko", "la",
                "lt", "lv", "mi", "mk", "nb", "nl", "nn", "no", "oc", "ota", "pdt", "pl",
                "pt", "ro", "ru", "sa", "sh", "sk", "sl", "sv", "th", "tl", "tr", "uk",
                "vi",
            ),
        ),
        VocabCatalogEntry(
            "k900", "Kaplan 900", "en", "k900",
            761, 539661L,
            listOf(
                "ar", "az", "be", "bg", "bn", "ca", "cmn", "cs", "cy", "da", "de", "el",
                "eo", "es", "et", "fa", "fi", "fr", "ga", "gd", "gl", "grc", "he", "hi",
                "hu", "hy", "id", "io", "is", "it", "ja", "ka", "kk", "ko", "la", "mi",
                "mk", "nb", "nl", "nn", "no", "oc", "ota", "pl", "pt", "ro", "ru", "sh",
                "sk", "sl", "sv", "tl", "tr", "uk", "vi",
            ),
        ),
        VocabCatalogEntry(
            "mg1000", "Magoosh 1000", "en", "mg1000",
            1000, 705666L,
            listOf(
                "ar", "az", "be", "bg", "bn", "ca", "cmn", "cs", "cy", "da", "de", "el",
                "eo", "es", "et", "fa", "fi", "fr", "ga", "gd", "gl", "grc", "he", "hi",
                "hu", "hy", "id", "io", "is", "it", "ja", "ka", "kk", "ko", "la", "mi",
                "mk", "nb", "nl", "nn", "no", "oc", "ota", "pl", "pt", "ro", "ru", "sh",
                "sk", "sl", "sv", "th", "tl", "tr", "uk", "vi",
            ),
        ),
        VocabCatalogEntry(
            "mp1000", "Manhattan Prep 1000", "en", "mp1000",
            993, 722021L,
            listOf(
                "ar", "az", "be", "bg", "bn", "ca", "cmn", "cs", "cy", "da", "de", "el",
                "eo", "es", "et", "fa", "fi", "fr", "ga", "gd", "gl", "grc", "he", "hi",
                "hu", "hy", "id", "io", "is", "it", "ja", "ka", "kk", "ko", "la", "lv",
                "mi", "mk", "ms", "nb", "nl", "nn", "no", "oc", "ota", "pl", "pt", "ro",
                "ru", "sh", "sk", "sl", "sv", "th", "tl", "tr", "uk", "vi",
            ),
        ),
        VocabCatalogEntry(
            "p700", "Powerscore 700", "en", "p700",
            698, 500238L,
            listOf(
                "ar", "az", "be", "bg", "bn", "ca", "cmn", "cs", "cy", "da", "de", "el",
                "eo", "es", "et", "fa", "fi", "fr", "ga", "gd", "gl", "grc", "he", "hi",
                "hu", "hy", "id", "io", "is", "it", "ja", "ka", "kk", "ko", "la", "mi",
                "mk", "nb", "nl", "nn", "no", "oc", "ota", "pl", "pt", "ro", "ru", "sh",
                "sk", "sl", "sv", "tl", "tr", "uk", "vi",
            ),
        ),
        VocabCatalogEntry(
            "sn1000", "SparkNotes 1000", "en", "sn1000",
            990, 712273L,
            listOf(
                "ar", "az", "be", "bg", "bn", "ca", "cmn", "cs", "cy", "da", "de", "el",
                "eo", "es", "et", "fa", "fi", "fr", "ga", "gd", "gl", "grc", "he", "hi",
                "hu", "hy", "id", "io", "is", "it", "ja", "ka", "kk", "ko", "la", "mi",
                "mk", "ms", "nb", "nl", "nn", "no", "oc", "ota", "pl", "pt", "ro", "ru",
                "sh", "sk", "sl", "sv", "th", "tl", "tr", "uk", "vi",
            ),
        ),
        VocabCatalogEntry(
            "ws1", "Word Smart 1", "en", "ws1",
            850, 629030L,
            listOf(
                "ar", "az", "be", "bg", "bn", "ca", "cmn", "cs", "cy", "da", "de", "el",
                "eo", "es", "et", "fa", "fi", "fr", "ga", "gd", "gl", "grc", "he", "hi",
                "hu", "hy", "id", "io", "is", "it", "ja", "ka", "kk", "ko", "la", "lt",
                "lv", "mi", "mk", "ms", "nb", "nl", "nn", "no", "oc", "ota", "pl", "pt",
                "ro", "ru", "sh", "sk", "sl", "sq", "sv", "th", "tl", "tr", "uk", "vi",
            ),
        ),
        VocabCatalogEntry(
            "ws2", "Word Smart 2", "en", "ws2",
            829, 600777L,
            listOf(
                "ar", "az", "be", "bg", "bn", "ca", "cmn", "cs", "cy", "da", "de", "el",
                "eo", "es", "et", "fa", "fi", "fr", "ga", "gd", "gl", "grc", "he", "hi",
                "hu", "hy", "id", "io", "is", "it", "ja", "ka", "kk", "ko", "la", "lt",
                "lv", "mi", "mk", "ms", "nb", "nl", "nn", "no", "oc", "ota", "pl", "pt",
                "ro", "ru", "sh", "sk", "sl", "sq", "sv", "th", "tl", "tr", "uk", "vi",
            ),
        ),
    )
    // END GENERATED

    private val byId: Map<String, VocabCatalogEntry> = entries.associateBy { it.id }

    init {
        check(byId.size == entries.size) { "duplicate vocabulary catalog ids" }
    }

    fun byId(id: String): VocabCatalogEntry? = byId[id]

    fun forLanguage(langId: String): List<VocabCatalogEntry> = entries.filter { it.langId == langId }

    /** Every language the catalogue has a pack for. */
    val languages: List<String> get() = entries.map { it.langId }.distinct()
}
