package com.wasimaster.wmkeyboard.core.localllm

/**
 * Container format of a model file. Both load through the LiteRT-LM engine;
 * `.task` is the older MediaPipe bundle kept for models that don't publish
 * a `.litertlm` yet.
 */
enum class ModelFormat(val extension: String) {
    LITERTLM("litertlm"), TASK("task"),
}

/** One downloadable model in the curated catalog. */
data class LocalLlmModel(
    /** Stable key — used as the storage directory name and the settings value. */
    val id: String,
    val displayName: String,
    /** Parameter count as shown to the user, e.g. "270M". */
    val params: String,
    /** Hugging Face repo, e.g. "litert-community/Gemma3-1B-IT". */
    val repo: String,
    /** Exact file under the repo's resolve/main/. */
    val fileName: String,
    /** Approximate size — preflight space check and progress fallback. */
    val sizeBytes: Long,
    /** Gated repos need an accepted license + HF token to download. */
    val gated: Boolean,
    val format: ModelFormat,
    /** Advisory total-device-RAM floor; the UI warns below this, never blocks. */
    val minRamMb: Int,
    val description: String,
    /**
     * Reasoning model whose template opens a think block in the prompt —
     * output is bare reasoning up to a `</think>` (see AiThinking).
     */
    val reasoning: Boolean = false,
)

/**
 * Curated on-device models, smallest first. All files are 4/8-bit mobile
 * quantizations published by Google's litert-community on Hugging Face.
 *
 * Adding a model = appending one entry (keep the ascending-size order —
 * the settings list renders in catalog order).
 */
object LocalLlmCatalog {

    const val TOKEN_URL = "https://huggingface.co/settings/tokens"

    val models: List<LocalLlmModel> = listOf(
        LocalLlmModel(
            // SmolLM v1's .task bundle behaved like a base model (no chat
            // template applied); the v2 .litertlm is the proper instruct build.
            id = "smollm2-135m",
            displayName = "SmolLM2 135M",
            params = "135M",
            repo = "litert-community/SmolLM2-135M-Instruct",
            fileName = "SmolLM2_135M_Instruct.litertlm",
            sizeBytes = 143_000_000L,
            gated = false,
            format = ModelFormat.LITERTLM,
            minRamMb = 2048,
            description = "Tiny and fast. Fine for quick rewrites; limited reasoning.",
        ),
        LocalLlmModel(
            id = "gemma3-270m",
            displayName = "Gemma 3 270M",
            params = "270M",
            repo = "litert-community/gemma-3-270m-it",
            fileName = "gemma3-270m-it-q8.litertlm",
            sizeBytes = 304_000_000L,
            gated = true,
            format = ModelFormat.LITERTLM,
            minRamMb = 2048,
            description = "Google's smallest Gemma. Snappy on any phone.",
        ),
        LocalLlmModel(
            id = "qwen3-0.6b",
            displayName = "Qwen 3 0.6B",
            params = "0.6B",
            repo = "litert-community/Qwen3-0.6B-int4",
            fileName = "qwen3_0.6b_q4_block32_ekv1280.litertlm",
            sizeBytes = 347_000_000L,
            gated = false,
            format = ModelFormat.LITERTLM,
            minRamMb = 3072,
            description = "Recent small model with strong quality for its size.",
            reasoning = true,
        ),
        LocalLlmModel(
            id = "granite-350m",
            displayName = "Granite 4.0 350M",
            params = "350M",
            repo = "litert-community/granite-4.0-350m-litert-lm",
            fileName = "granite-4.0-350m_q8_ekv1280.litertlm",
            sizeBytes = 468_000_000L,
            gated = false,
            format = ModelFormat.LITERTLM,
            minRamMb = 3072,
            description = "IBM's compact model at higher 8-bit fidelity.",
        ),
        LocalLlmModel(
            id = "qwen25-0.5b",
            displayName = "Qwen 2.5 0.5B",
            params = "0.5B",
            repo = "litert-community/Qwen2.5-0.5B-Instruct",
            fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            sizeBytes = 546_000_000L,
            gated = false,
            format = ModelFormat.TASK,
            minRamMb = 3072,
            description = "Solid all-rounder that still fits modest phones.",
        ),
        LocalLlmModel(
            id = "gemma3-1b",
            displayName = "Gemma 3 1B",
            params = "1B",
            repo = "litert-community/Gemma3-1B-IT",
            fileName = "gemma3-1b-it-int4.litertlm",
            sizeBytes = 584_000_000L,
            gated = true,
            format = ModelFormat.LITERTLM,
            minRamMb = 4096,
            description = "Best small Gemma — good quality, 4K context.",
        ),
        LocalLlmModel(
            id = "qwen25-1.5b",
            displayName = "Qwen 2.5 1.5B",
            params = "1.5B",
            repo = "litert-community/Qwen2.5-1.5B-Instruct",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            sizeBytes = 1_600_000_000L,
            gated = false,
            format = ModelFormat.LITERTLM,
            minRamMb = 6144,
            description = "Noticeably smarter; needs a mid-range phone or better.",
        ),
        LocalLlmModel(
            id = "gemma4-e2b",
            displayName = "Gemma 4 E2B",
            params = "2B",
            repo = "litert-community/gemma-4-E2B-it-litert-lm",
            fileName = "gemma-4-E2B-it.litertlm",
            sizeBytes = 2_590_000_000L,
            gated = false,
            format = ModelFormat.LITERTLM,
            minRamMb = 6144,
            description = "Latest Gemma generation — flagship quality on-device.",
        ),
        LocalLlmModel(
            id = "gemma4-e4b",
            displayName = "Gemma 4 E4B",
            params = "4B",
            repo = "litert-community/gemma-4-E4B-it-litert-lm",
            fileName = "gemma-4-E4B-it.litertlm",
            sizeBytes = 3_660_000_000L,
            gated = false,
            format = ModelFormat.LITERTLM,
            minRamMb = 8192,
            description = "The biggest — best writing, needs a high-end phone.",
        ),
    )

    init {
        check(models == models.sortedBy { it.sizeBytes }) { "catalog must stay sorted by size" }
        check(models.map { it.id }.toSet().size == models.size) { "catalog ids must be unique" }
    }

    fun byId(id: String): LocalLlmModel? = models.firstOrNull { it.id == id }

    fun downloadUrl(model: LocalLlmModel): String =
        "https://huggingface.co/${model.repo}/resolve/main/${model.fileName}"

    /** The repo page — where a gated model's license is accepted. */
    fun licenseUrl(model: LocalLlmModel): String = "https://huggingface.co/${model.repo}"
}
