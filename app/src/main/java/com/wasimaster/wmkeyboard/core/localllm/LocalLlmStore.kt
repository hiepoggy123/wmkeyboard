package com.wasimaster.wmkeyboard.core.localllm

import java.io.File

/**
 * File layout for on-device models. Catalog downloads live in
 * `filesDir/models/<id>/<fileName>` with a `<fileName>.part` while the
 * download is in flight; user-imported files live in `filesDir/models/custom/`.
 *
 * The atomic-rename contract: a final (non-.part) file exists only if its
 * download completed with the expected byte count, so presence == valid.
 * The custom directory scan is the whole registry for imports — no index
 * file to fall out of sync (same approach as CustomDictionaries).
 */
object LocalLlmStore {

    /** Prefix marking an imported file in the aiLocalModelId setting. */
    const val CUSTOM_PREFIX = "custom:"

    private val MODEL_EXTENSIONS = ModelFormat.entries.map { it.extension }.toSet()

    fun modelsDir(filesDir: File): File = File(filesDir, "models")

    fun modelDir(filesDir: File, model: LocalLlmModel): File =
        File(modelsDir(filesDir), model.id)

    fun modelFile(filesDir: File, model: LocalLlmModel): File =
        File(modelDir(filesDir, model), model.fileName)

    fun partFile(filesDir: File, model: LocalLlmModel): File =
        File(modelDir(filesDir, model), "${model.fileName}.part")

    fun isDownloaded(filesDir: File, model: LocalLlmModel): Boolean =
        modelFile(filesDir, model).isFile

    fun customDir(filesDir: File): File = File(modelsDir(filesDir), "custom")

    fun customModels(filesDir: File): List<File> =
        customDir(filesDir).listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.lowercase() in MODEL_EXTENSIONS }
            .sortedBy { it.name.lowercase() }

    /**
     * Resolves the aiLocalModelId setting — a catalog id or a
     * [CUSTOM_PREFIX]ed file name — to an existing model file, or null when
     * nothing is selected or the file is gone (deleted, never finished).
     */
    fun selectedModelFile(filesDir: File, aiLocalModelId: String): File? {
        if (aiLocalModelId.isBlank()) return null
        val file = if (aiLocalModelId.startsWith(CUSTOM_PREFIX)) {
            val name = aiLocalModelId.removePrefix(CUSTOM_PREFIX)
            // Reject path separators so a malformed setting can't escape custom/.
            if (name.isBlank() || name != File(name).name) return null
            File(customDir(filesDir), name)
        } else {
            LocalLlmCatalog.byId(aiLocalModelId)?.let { modelFile(filesDir, it) } ?: return null
        }
        return file.takeIf { it.isFile }
    }

    /**
     * When exactly one model exists on disk (catalog or custom), its
     * selectable id — the "only model" fallback that makes an explicit
     * selection unnecessary until there's an actual choice.
     */
    fun soleDownloadedId(filesDir: File): String? {
        val ids = LocalLlmCatalog.models
            .filter { isDownloaded(filesDir, it) }
            .map { it.id } +
            customModels(filesDir).map { CUSTOM_PREFIX + it.name }
        return ids.singleOrNull()
    }

    fun delete(filesDir: File, model: LocalLlmModel) {
        modelDir(filesDir, model).deleteRecursively()
    }

    fun deleteCustom(filesDir: File, name: String) {
        if (name != File(name).name) return
        File(customDir(filesDir), name).delete()
    }

    /** Bytes on disk across all models, finished and partial. */
    fun totalBytesUsed(filesDir: File): Long =
        modelsDir(filesDir).walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}
