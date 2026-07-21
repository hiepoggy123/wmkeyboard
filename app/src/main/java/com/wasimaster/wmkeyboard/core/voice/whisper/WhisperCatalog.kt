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
    /** Size class shown to the user, e.g. "Tiny", "Base", "Small". */
    val sizeLabel: String,
    /** What it covers, e.g. "99 languages", "English only", "German". */
    val languageLabel: String,
    val repo: String,
    val modelFile: String,
    val vocabFile: String,
    val modelBytes: Long,
    val vocabBytes: Long,
    /**
     * True for the transcribe-translate graphs that accept a forced_decoder_ids
     * input (language selectable + translate task). False for single-language
     * distilled graphs and the English-only model.
     */
    val multilingual: Boolean,
    /** Whisper language code a single-language model is locked to; null otherwise. */
    val fixedLang: String?,
    val tier: WhisperTier,
    val description: String,
) {
    /** Total download/footprint — used for the space preflight and progress fallback. */
    val sizeBytes: Long get() = modelBytes + vocabBytes
}

/**
 * Curated offline Whisper models from DocWolle/whisper_tflite_models on
 * Hugging Face (public — no token needed). The `-transcribe-translate` graphs
 * cover ~99 languages and a translate-to-English task; the distilled
 * per-language graphs are smaller and faster when a user only ever dictates one
 * language.
 *
 * Order is a suggested-pick ranking, not a size ranking — the settings list
 * renders in catalog order, so the balanced default sits first.
 */
object WhisperCatalog {

    private const val REPO = "DocWolle/whisper_tflite_models"
    private const val VOCAB_MULTI = "filters_vocab_multilingual.bin"
    private const val VOCAB_EN = "filters_vocab_en.bin"
    private const val VOCAB_MULTI_BYTES = 572_421L
    private const val VOCAB_EN_BYTES = 586_174L

    val models: List<WhisperModel> = listOf(
        WhisperModel(
            id = "base-multi",
            displayName = "Whisper Base",
            sizeLabel = "Base",
            languageLabel = "99 languages · translate",
            repo = REPO,
            modelFile = "whisper-base-transcribe-translate.tflite",
            vocabFile = VOCAB_MULTI,
            modelBytes = 78_508_512L,
            vocabBytes = VOCAB_MULTI_BYTES,
            multilingual = true,
            fixedLang = null,
            tier = WhisperTier.RECOMMENDED,
            description = "Balanced accuracy and speed across all languages. Best starting point.",
        ),
        WhisperModel(
            id = "small-multi",
            displayName = "Whisper Small",
            sizeLabel = "Small",
            languageLabel = "99 languages · translate",
            repo = REPO,
            modelFile = "whisper-small-transcribe-translate.tflite",
            vocabFile = VOCAB_MULTI,
            modelBytes = 248_684_440L,
            vocabBytes = VOCAB_MULTI_BYTES,
            multilingual = true,
            fixedLang = null,
            tier = WhisperTier.STANDARD,
            description = "Most accurate — noticeably better on accents and noise, slower and larger.",
        ),
        WhisperModel(
            id = "tiny-multi",
            displayName = "Whisper Tiny",
            sizeLabel = "Tiny",
            languageLabel = "99 languages · translate",
            repo = REPO,
            modelFile = "whisper-tiny-transcribe-translate.tflite",
            vocabFile = VOCAB_MULTI,
            modelBytes = 42_103_936L,
            vocabBytes = VOCAB_MULTI_BYTES,
            multilingual = true,
            fixedLang = null,
            tier = WhisperTier.STANDARD,
            description = "Fastest multilingual model. Fine for clear speech; struggles with noise.",
        ),
        WhisperModel(
            id = "tiny-en",
            displayName = "Whisper Tiny (English)",
            sizeLabel = "Tiny",
            languageLabel = "English only",
            repo = REPO,
            modelFile = "whisper-tiny.en.tflite",
            vocabFile = VOCAB_EN,
            modelBytes = 41_486_616L,
            vocabBytes = VOCAB_EN_BYTES,
            multilingual = false,
            fixedLang = "en",
            tier = WhisperTier.STANDARD,
            description = "English-only and fastest of all. Best pick if you only dictate English.",
        ),
    ) + distilled(
        // Base-size single-language graphs — smaller mental model than "pick a
        // language on the multilingual model", faster, but one language each.
        "de" to "German", "es" to "Spanish", "fr" to "French", "hi" to "Hindi",
        "it" to "Italian", "pt" to "Portuguese", "ru" to "Russian", "zh" to "Chinese",
    ) + listOf(
        // Urdu ships both a base and a small distilled graph.
        WhisperModel(
            id = "base-ur",
            displayName = "Whisper Base (Urdu)",
            sizeLabel = "Base",
            languageLabel = "Urdu",
            repo = REPO,
            modelFile = "whisper-base.ur.tflite",
            vocabFile = VOCAB_MULTI,
            modelBytes = 78_437_408L,
            vocabBytes = VOCAB_MULTI_BYTES,
            multilingual = false,
            fixedLang = "ur",
            tier = WhisperTier.UNTESTED,
            description = "Single-language Urdu model.",
        ),
        WhisperModel(
            id = "small-ur",
            displayName = "Whisper Small (Urdu)",
            sizeLabel = "Small",
            languageLabel = "Urdu",
            repo = REPO,
            modelFile = "whisper-small.ur.tflite",
            vocabFile = VOCAB_MULTI,
            modelBytes = 248_612_928L,
            vocabBytes = VOCAB_MULTI_BYTES,
            multilingual = false,
            fixedLang = "ur",
            tier = WhisperTier.UNTESTED,
            description = "More accurate Urdu model; larger and slower.",
        ),
    )

    /** Base-size distilled graphs share one shape — build them from (code, name). */
    private fun distilled(vararg langs: Pair<String, String>): List<WhisperModel> =
        langs.map { (code, name) ->
            WhisperModel(
                id = "base-$code",
                displayName = "Whisper Base ($name)",
                sizeLabel = "Base",
                languageLabel = name,
                repo = REPO,
                modelFile = "whisper-base.$code.tflite",
                vocabFile = VOCAB_MULTI,
                modelBytes = 78_448_496L,
                vocabBytes = VOCAB_MULTI_BYTES,
                multilingual = false,
                fixedLang = code,
                tier = WhisperTier.UNTESTED,
                description = "Single-language $name model — smaller and faster than the multilingual ones.",
            )
        }

    init {
        check(models.map { it.id }.toSet().size == models.size) { "catalog ids must be unique" }
    }

    fun byId(id: String): WhisperModel? = models.firstOrNull { it.id == id }

    fun downloadUrl(repo: String, fileName: String): String =
        "https://huggingface.co/$repo/resolve/main/$fileName"
}
