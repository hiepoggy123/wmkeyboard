package com.wasimaster.wmkeyboard.core.voice.whisper

import java.io.File

/**
 * File layout for offline Whisper models. Each catalog entry downloads into
 * `filesDir/whisper/<id>/` as two files — the `.tflite` model and its
 * `filters_vocab_*.bin` — with a `<name>.part` while a file is in flight.
 *
 * Atomic-rename contract: a final (non-.part) file exists only once its
 * download finished with the expected byte count, so a model counts as
 * downloaded exactly when **both** final files are present.
 */
object WhisperStore {

    fun rootDir(filesDir: File): File = File(filesDir, "whisper")

    fun modelDir(filesDir: File, model: WhisperModel): File =
        File(rootDir(filesDir), model.id)

    fun modelFile(filesDir: File, model: WhisperModel): File =
        File(modelDir(filesDir, model), model.modelFile)

    fun vocabFile(filesDir: File, model: WhisperModel): File =
        File(modelDir(filesDir, model), model.vocabFile)

    /** Both files (model, vocab) that must exist for the model to be usable. */
    fun requiredFiles(filesDir: File, model: WhisperModel): List<File> =
        listOf(modelFile(filesDir, model), vocabFile(filesDir, model))

    fun isDownloaded(filesDir: File, model: WhisperModel): Boolean =
        requiredFiles(filesDir, model).all { it.isFile }

    /** True while a partial download for either file is on disk. */
    fun hasPartial(filesDir: File, model: WhisperModel): Boolean =
        requiredFiles(filesDir, model).any { File(it.parentFile, "${it.name}.part").isFile } ||
            // final model present but vocab still missing (interrupted between files)
            (!isDownloaded(filesDir, model) && requiredFiles(filesDir, model).any { it.isFile })

    /** Bytes already on disk for this model, finished and partial. */
    fun bytesOnDisk(filesDir: File, model: WhisperModel): Long =
        modelDir(filesDir, model).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /**
     * Resolves the whisperModelId setting to a downloaded model, or null when
     * nothing is selected or the files are gone.
     */
    fun selectedModel(filesDir: File, whisperModelId: String): WhisperModel? {
        if (whisperModelId.isBlank()) return null
        val model = WhisperCatalog.byId(whisperModelId) ?: return null
        return model.takeIf { isDownloaded(filesDir, it) }
    }

    /** The one downloaded model's id when exactly one exists — the "only model" fallback. */
    fun soleDownloadedId(filesDir: File): String? =
        WhisperCatalog.models.filter { isDownloaded(filesDir, it) }.map { it.id }.singleOrNull()

    /**
     * The effective model to run: the explicit selection if downloaded, else
     * the sole downloaded model. Null when none are ready.
     */
    fun effectiveModel(filesDir: File, whisperModelId: String): WhisperModel? =
        selectedModel(filesDir, whisperModelId)
            ?: soleDownloadedId(filesDir)?.let { WhisperCatalog.byId(it) }

    /** Directories left by models dropped from the catalog — offered for cleanup. */
    fun orphanDirs(filesDir: File): List<File> {
        val known = WhisperCatalog.models.map { it.id }.toSet()
        return rootDir(filesDir).listFiles().orEmpty()
            .filter { it.isDirectory && it.name !in known }
    }

    fun orphanBytes(filesDir: File): Long =
        orphanDirs(filesDir).sumOf { dir ->
            dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }

    fun deleteOrphans(filesDir: File) {
        orphanDirs(filesDir).forEach { it.deleteRecursively() }
    }

    fun delete(filesDir: File, model: WhisperModel) {
        modelDir(filesDir, model).deleteRecursively()
    }

    fun totalBytesUsed(filesDir: File): Long =
        rootDir(filesDir).walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}
