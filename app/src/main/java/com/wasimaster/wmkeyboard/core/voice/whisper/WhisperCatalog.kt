package com.wasimaster.wmkeyboard.core.voice.whisper

/**
 * How much a Whisper model can be trusted, shown as a badge next to its name.
 * [STANDARD] is the unremarkable middle — it gets no badge. None of these are
 * device-verified on this keyboard yet, so nothing claims [RECOMMENDED] beyond
 * the one balanced default we point users at.
 */
enum class WhisperTier(val badge: String?) {
    RECOMMENDED("Recommended"),
    STANDARD(null),
    UNTESTED("Untested"),
}

/** Size class of a Whisper graph, ordered smallest to largest. Drives grouping and filters. */
enum class WhisperSize(val label: String) {
    TINY("Tiny"),
    BASE("Base"),
    SMALL("Small"),
    MEDIUM("Medium"),
    LARGE("Large"),
}

/**
 * One downloadable Whisper model. Unlike an LLM entry, each model needs a
 * companion `filters_vocab_*.bin` (mel filterbank + BPE vocab in one binary),
 * so a catalog entry maps to **two** files that both must land before the model
 * counts as downloaded.
 */
data class WhisperModel(
    /** Stable key — storage directory name and the whisperModelId setting value. */
    val id: String,
    val displayName: String,
    val size: WhisperSize,
    val repo: String,
    val modelFile: String,
    val vocabFile: String,
    val modelBytes: Long,
    val vocabBytes: Long,
    /**
     * The Whisper language codes this graph can transcribe. Empty means all 99 —
     * the `-transcribe-translate` models detect the language themselves.
     */
    val langCodes: Set<String> = emptySet(),
    /**
     * True for the grouped graphs that expose `serving_transcribe_lang` and take
     * a `lang_token` scalar, so the keyboard forces whichever of [langCodes] is
     * being typed in instead of letting the model guess.
     */
    val selectableLang: Boolean = false,
    /** True where a `serving_translate` signature exists (speech → English). */
    val supportsTranslate: Boolean = false,
    val tier: WhisperTier,
    val description: String,
) {
    /** Total download/footprint — used for the space preflight and progress fallback. */
    val sizeBytes: Long get() = modelBytes + vocabBytes

    val sizeLabel: String get() = size.label

    /** False only for the graphs locked to one language (distilled and `.en`). */
    val multilingual: Boolean get() = langCodes.size != 1

    /** Whisper language code a single-language model is locked to; null otherwise. */
    val fixedLang: String? get() = langCodes.singleOrNull()

    /** Whether this model can transcribe [code] at all. */
    fun covers(code: String): Boolean = langCodes.isEmpty() || code in langCodes

    /** How many of [codes] this model handles — ranks catalog picks against enabled languages. */
    fun coverageOf(codes: Collection<String>): Int = codes.count { covers(it) }

    /**
     * The `lang_token` to force for a keyboard language, or null when this model
     * takes no such input or does not cover the language (then it auto-detects).
     */
    fun langTokenFor(languageId: String): Int? {
        if (!selectableLang) return null
        val code = WhisperLanguages.codeForLanguage(languageId) ?: return null
        if (code !in langCodes) return null
        return WhisperLanguages.tokenFor(code)
    }

    /** Short "what languages" line for the UI, e.g. "99 languages", "40 languages", "German". */
    val languageLabel: String
        get() = when {
            langCodes.isEmpty() -> "99 languages"
            langCodes.size == 1 -> WhisperLanguages.label(langCodes.first())
            else -> "${langCodes.size} languages"
        }
}

/**
 * Every offline Whisper graph we can actually run, from the two public Hugging
 * Face collections that publish them (no token or licence gate):
 *
 *  - `DocWolle/whisper_tflite_models` — the F-Droid Whisper app's set.
 *  - `nyadla-sys/whisper-tiny.en.tflite` — the original conversion repo, which
 *    carries the extra per-language and medium/large graphs.
 *
 * Three shapes exist, and the settings UI leans on the distinction:
 *  - **auto** (`-transcribe-translate`): all 99 languages, detected per clip,
 *    plus a translate-to-English task.
 *  - **grouped** (`TOP_WORLD`, `EUROPEAN_UNION`): a language *subset* baked into
 *    one graph with a `lang_token` input, so the keyboard forces the language
 *    you are typing in — more accurate than auto-detect on short clips, and far
 *    less disk than one single-language model per language.
 *  - **single-language** (`base.de`, `small.en`, …): one language, no choice.
 *
 * Not included on purpose: `whisper-large-v3` and `whisper-turbo` need a
 * 128-bin mel spectrogram, and both [WhisperMel] and the shipped
 * `filters_vocab_*.bin` filterbanks are 80-bin, so they would decode garbage;
 * `whisper-small.tflite` forces no task and duplicates [small-multi].
 *
 * Order is a suggested-pick ranking, not a size ranking — the settings list
 * renders in catalog order, so the balanced default sits first.
 */
object WhisperCatalog {

    private const val REPO = "DocWolle/whisper_tflite_models"
    private const val REPO_NYADLA = "nyadla-sys/whisper-tiny.en.tflite"
    private const val VOCAB_MULTI = "filters_vocab_multilingual.bin"
    private const val VOCAB_EN = "filters_vocab_en.bin"
    private const val VOCAB_MULTI_BYTES = 572_421L
    private const val VOCAB_EN_BYTES = 586_174L

    /**
     * The 40 languages baked into the `TOP_WORLD` graphs, decoded from the
     * `whisper-base.TOP_WORLD.tokens` file that ships beside them.
     */
    private val TOP_WORLD = setOf(
        "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr", "pl", "ca", "nl",
        "ar", "sv", "it", "id", "fi", "vi", "he", "uk", "el", "ms", "cs", "ro", "da",
        "hu", "ta", "no", "th", "ur", "hr", "bg", "lt", "sk", "lv", "sl", "et", "mt", "lb",
    )

    /** The 26 languages in the `EUROPEAN_UNION` graph, from its own `.tokens` file. */
    private val EUROPEAN_UNION = setOf(
        "en", "de", "es", "fr", "pt", "tr", "pl", "nl", "sv", "it", "fi", "el", "cs",
        "ro", "da", "hu", "no", "hr", "bg", "lt", "sk", "lv", "sl", "et", "mt", "lb",
    )

    val models: List<WhisperModel> = buildList {
        // ---- Grouped, language-selectable: the best fit for a multilingual keyboard.
        add(
            WhisperModel(
                id = "base-world",
                displayName = "Whisper Base (40 languages)",
                size = WhisperSize.BASE,
                repo = REPO,
                modelFile = "whisper-base.TOP_WORLD.tflite",
                vocabFile = VOCAB_MULTI,
                modelBytes = 107_564_368L,
                vocabBytes = VOCAB_MULTI_BYTES,
                langCodes = TOP_WORLD,
                selectableLang = true,
                supportsTranslate = true,
                tier = WhisperTier.RECOMMENDED,
                description = "Forces the language you are typing in instead of guessing " +
                    "it, which is noticeably more reliable on short phrases. Best pick " +
                    "when your languages are in its list.",
            ),
        )
        add(
            WhisperModel(
                id = "base-eu",
                displayName = "Whisper Base (European)",
                size = WhisperSize.BASE,
                repo = REPO,
                modelFile = "whisper-base.EUROPEAN_UNION.tflite",
                vocabFile = VOCAB_MULTI,
                modelBytes = 94_819_264L,
                vocabBytes = VOCAB_MULTI_BYTES,
                langCodes = EUROPEAN_UNION,
                selectableLang = true,
                supportsTranslate = true,
                tier = WhisperTier.UNTESTED,
                description = "Same language-forcing as the 40-language model, trimmed to " +
                    "European languages and a smaller download.",
            ),
        )
        add(
            WhisperModel(
                id = "small-world",
                displayName = "Whisper Small (40 languages)",
                size = WhisperSize.SMALL,
                repo = REPO,
                modelFile = "whisper-small.TOP_WORLD.tflite",
                vocabFile = VOCAB_MULTI,
                modelBytes = 307_408_944L,
                vocabBytes = VOCAB_MULTI_BYTES,
                langCodes = TOP_WORLD,
                selectableLang = true,
                supportsTranslate = true,
                tier = WhisperTier.UNTESTED,
                description = "Language-forcing plus Small-model accuracy. The most accurate " +
                    "option that still fits comfortably on a phone.",
            ),
        )

        // ---- Auto-detecting, all 99 languages, translate-capable.
        add(
            WhisperModel(
                id = "base-multi",
                displayName = "Whisper Base",
                size = WhisperSize.BASE,
                repo = REPO,
                modelFile = "whisper-base-transcribe-translate.tflite",
                vocabFile = VOCAB_MULTI,
                modelBytes = 78_508_512L,
                vocabBytes = VOCAB_MULTI_BYTES,
                supportsTranslate = true,
                tier = WhisperTier.RECOMMENDED,
                description = "Balanced accuracy and speed, and the only kind that covers " +
                    "every language Whisper knows. Safe starting point.",
            ),
        )
        add(
            WhisperModel(
                id = "tiny-multi",
                displayName = "Whisper Tiny",
                size = WhisperSize.TINY,
                repo = REPO,
                modelFile = "whisper-tiny-transcribe-translate.tflite",
                vocabFile = VOCAB_MULTI,
                modelBytes = 42_103_936L,
                vocabBytes = VOCAB_MULTI_BYTES,
                supportsTranslate = true,
                tier = WhisperTier.STANDARD,
                description = "Fastest multilingual model. Fine for clear speech; struggles with noise.",
            ),
        )
        add(
            WhisperModel(
                id = "small-multi",
                displayName = "Whisper Small",
                size = WhisperSize.SMALL,
                repo = REPO,
                modelFile = "whisper-small-transcribe-translate.tflite",
                vocabFile = VOCAB_MULTI,
                modelBytes = 248_684_440L,
                vocabBytes = VOCAB_MULTI_BYTES,
                supportsTranslate = true,
                tier = WhisperTier.STANDARD,
                description = "Noticeably better on accents and noise than Base, slower and larger.",
            ),
        )
        add(
            WhisperModel(
                id = "medium-multi",
                displayName = "Whisper Medium",
                size = WhisperSize.MEDIUM,
                repo = REPO_NYADLA,
                modelFile = "whisper-medium-transcribe-translate.tflite",
                vocabFile = VOCAB_MULTI,
                modelBytes = 775_637_984L,
                vocabBytes = VOCAB_MULTI_BYTES,
                supportsTranslate = true,
                tier = WhisperTier.UNTESTED,
                description = "Near-desktop accuracy. Expect several seconds per phrase and " +
                    "close to a gigabyte of RAM while it runs.",
            ),
        )
        add(
            WhisperModel(
                id = "large-multi",
                displayName = "Whisper Large",
                size = WhisperSize.LARGE,
                repo = REPO_NYADLA,
                modelFile = "whisper-large-transcribe-translate.tflite",
                vocabFile = VOCAB_MULTI,
                modelBytes = 1_559_228_864L,
                vocabBytes = VOCAB_MULTI_BYTES,
                supportsTranslate = true,
                tier = WhisperTier.UNTESTED,
                description = "The most accurate Whisper graph there is, and far too big for " +
                    "most phones — a 1.5 GB download that only high-memory devices can load.",
            ),
        )

        // ---- Single-language graphs: one language each, no detection step.
        addAll(
            singleLanguage(
                Single("tiny-en", WhisperSize.TINY, "whisper-tiny.en.tflite", "en", REPO, 41_486_616L),
                Single("base-en", WhisperSize.BASE, "whisper-base.en.tflite", "en", REPO_NYADLA, 77_642_600L),
                Single("small-en", WhisperSize.SMALL, "whisper-small.en.tflite", "en", REPO_NYADLA, 247_048_280L),
                Single("medium-en", WhisperSize.MEDIUM, "whisper-medium.en.tflite", "en", REPO_NYADLA, 772_442_720L),
                Single("base-de", WhisperSize.BASE, "whisper-base.de.tflite", "de", REPO, 78_448_496L),
                Single("base-es", WhisperSize.BASE, "whisper-base.es.tflite", "es", REPO, 78_448_496L),
                Single("small-es", WhisperSize.SMALL, "whisper-small.es.tflite", "es", REPO_NYADLA, 247_025_152L),
                Single("base-fr", WhisperSize.BASE, "whisper-base.fr.tflite", "fr", REPO, 78_448_496L),
                Single("base-hi", WhisperSize.BASE, "whisper-base.hi.tflite", "hi", REPO, 78_448_496L),
                Single("small-hi", WhisperSize.SMALL, "whisper-small.hi.tflite", "hi", REPO_NYADLA, 247_025_144L),
                Single("base-it", WhisperSize.BASE, "whisper-base.it.tflite", "it", REPO, 78_448_496L),
                Single("base-ja", WhisperSize.BASE, "whisper-base.ja.tflite", "ja", REPO_NYADLA, 77_619_400L),
                Single("base-pt", WhisperSize.BASE, "whisper-base.pt.tflite", "pt", REPO, 78_448_496L),
                Single("base-ru", WhisperSize.BASE, "whisper-base.ru.tflite", "ru", REPO, 78_448_496L),
                Single("small-ta", WhisperSize.SMALL, "whisper-small.ta.tflite", "ta", REPO_NYADLA, 247_025_192L),
                Single("small-te", WhisperSize.SMALL, "whisper-small.te.tflite", "te", REPO_NYADLA, 247_025_192L),
                Single("base-ur", WhisperSize.BASE, "whisper-base.ur.tflite", "ur", REPO, 78_437_408L),
                Single("small-ur", WhisperSize.SMALL, "whisper-small.ur.tflite", "ur", REPO, 248_612_928L),
                Single("base-zh", WhisperSize.BASE, "whisper-base.zh.tflite", "zh", REPO, 78_448_496L),
            ),
        )
    }

    /** Every language at least one single-language graph exists for. */
    val singleLanguageCodes: Set<String> =
        models.mapNotNullTo(mutableSetOf()) { it.fixedLang }

    private class Single(
        val id: String,
        val size: WhisperSize,
        val file: String,
        val code: String,
        val repo: String,
        val bytes: Long,
    )

    /** Single-language graphs share one shape — spell out only what differs. */
    private fun singleLanguage(vararg entries: Single): List<WhisperModel> =
        entries.map { entry ->
            // The English graphs are built on the English-only tokenizer, so they
            // need the other vocab binary; every other one is a distilled
            // multilingual graph with its language forced.
            val english = entry.code == "en"
            val name = WhisperLanguages.label(entry.code)
            WhisperModel(
                id = entry.id,
                displayName = "Whisper ${entry.size.label} ($name)",
                size = entry.size,
                repo = entry.repo,
                modelFile = entry.file,
                vocabFile = if (english) VOCAB_EN else VOCAB_MULTI,
                modelBytes = entry.bytes,
                vocabBytes = if (english) VOCAB_EN_BYTES else VOCAB_MULTI_BYTES,
                langCodes = setOf(entry.code),
                tier = if (entry.id == "tiny-en") WhisperTier.STANDARD else WhisperTier.UNTESTED,
                description = "$name only — no language detection step, so it is faster and " +
                    "a little more accurate than a multilingual graph of the same size.",
            )
        }

    init {
        check(models.map { it.id }.toSet().size == models.size) { "catalog ids must be unique" }
    }

    fun byId(id: String): WhisperModel? = models.firstOrNull { it.id == id }

    /**
     * The models worth suggesting for an enabled-language set, best first: the
     * grouped or single-language graphs that actually cover those languages,
     * falling back to the all-languages default. Capped at [limit] so the
     * settings screen stays a shortlist rather than a second full catalog.
     */
    fun recommendedFor(codes: Set<String>, limit: Int = 4): List<WhisperModel> {
        if (codes.isEmpty()) return listOfNotNull(byId("base-multi"))
        val picks = LinkedHashSet<WhisperModel>()
        // Vetted before small: a tier we have actually checked is worth more than
        // shaving a few MB off the download.
        val bestFirst = compareBy<WhisperModel>({ it.tier != WhisperTier.RECOMMENDED }, { it.sizeBytes })

        // A grouped graph that covers everything the user types beats anything
        // else: one download, language forced per phrase.
        models.filter { it.selectableLang && it.coverageOf(codes) == codes.size }
            .minWithOrNull(bestFirst)
            ?.let { picks += it }

        // Then the single-language graphs, one per language, so a one-language
        // user is pointed straight at their own model.
        for (code in codes) {
            models.filter { it.fixedLang == code }.minWithOrNull(bestFirst)?.let { picks += it }
        }

        // Always leave an all-languages escape hatch — the only thing that
        // covers a language with no dedicated graph.
        byId("base-multi")?.let { picks += it }

        // Partial grouped coverage is still useful when nothing above fit well.
        models.filter { it.selectableLang && it.coverageOf(codes) > 0 }
            .sortedByDescending { it.coverageOf(codes) }
            .forEach { picks += it }

        return picks.take(limit)
    }

    fun downloadUrl(repo: String, fileName: String): String =
        "https://huggingface.co/$repo/resolve/main/$fileName"
}
