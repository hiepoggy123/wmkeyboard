package com.wasimaster.wmkeyboard.core.dictionaries

import android.content.Context
import com.wasimaster.wmkeyboard.BuildConfig
import java.io.File
import java.io.IOException

/**
 * File layout for `.wmdict` binary dictionaries under `filesDir/dict/`:
 *
 *  - `dict/bundled/<name>.wmdict` — the compiled bundled lists (en, bn),
 *    inflated once out of the APK so they can be memory-mapped ([ensureBundled];
 *    AAPT-deflated assets cannot be mapped in place). Re-extracted when the app
 *    updates, tracked by a `.version` marker holding the versionCode.
 *  - `dict/<langId>/main.wmdict` — a downloaded wordlist for that language,
 *    with `main.wmdict.part` while the download/convert pipeline runs. The
 *    atomic-rename contract means a final file exists only if it was written
 *    completely, so presence == valid — same as `LocalLlmStore`.
 *
 * Deliberately separate from `filesDir/dictionaries/` (user-imported custom
 * `.txt` lists, [com.wasimaster.wmkeyboard.core.prediction.CustomDictionaries])
 * so that feature's glob-and-parse semantics stay untouched.
 */
object DictionaryStore {

    private const val FILE_NAME = "main.wmdict"
    private const val BUNDLED_DIR = "bundled"

    private fun root(filesDir: File) = File(filesDir, "dict")

    private fun bundledDir(filesDir: File) = File(root(filesDir), BUNDLED_DIR)

    fun downloadedFile(filesDir: File, langId: String): File =
        File(File(root(filesDir), langId), FILE_NAME)

    fun partFile(filesDir: File, langId: String): File =
        File(File(root(filesDir), langId), "$FILE_NAME.part")

    fun isDownloaded(filesDir: File, langId: String): Boolean =
        downloadedFile(filesDir, langId).isFile

    fun delete(filesDir: File, langId: String) {
        File(root(filesDir), langId).takeIf { it.name != BUNDLED_DIR }?.deleteRecursively()
    }

    /**
     * Which catalog entry a language's downloaded dictionary came from. Only
     * matters where several entries feed one language (Portuguese Europe vs
     * Brazil): the UI shows "downloaded" on the matching row alone.
     */
    fun sourceEntryId(filesDir: File, langId: String): String? =
        runCatching { File(File(root(filesDir), langId), "source").readText().trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() && isDownloaded(filesDir, langId) }

    fun writeSourceEntryId(filesDir: File, langId: String, entryId: String) {
        runCatching { File(File(root(filesDir), langId), "source").writeText(entryId) }
    }

    /** Language ids that have a completed downloaded dictionary on disk. */
    fun downloadedLanguageIds(filesDir: File): List<String> =
        root(filesDir).listFiles { file -> file.isDirectory && file.name != BUNDLED_DIR }
            .orEmpty()
            .filter { File(it, FILE_NAME).isFile }
            .map { it.name }
            .sorted()

    /**
     * A monotonically-changing token summarising which downloaded dictionaries
     * are on disk (and which bytes). The service compares it against the token
     * it last loaded from and rebuilds its word sources only when it differs —
     * so a download finished in Settings goes live on the next field focus
     * without re-checking files on every keypress.
     */
    fun stateToken(filesDir: File): Int =
        downloadedLanguageIds(filesDir).fold(0) { acc, langId ->
            val file = downloadedFile(filesDir, langId)
            var h = acc * 31 + langId.hashCode()
            h = h * 31 + file.length().toInt()
            h * 31 + file.lastModified().toInt()
        }

    /**
     * Guarantees the bundled dictionary [baseName] (e.g. `"en"`) exists as a
     * plain file and returns it, inflating `assets/dictionaries/<baseName>.wmdict`
     * on first run or after an app update. Returns null if the asset is missing
     * or the copy fails (disk full) — callers degrade to an empty dictionary.
     */
    fun ensureBundled(context: Context, baseName: String): File? {
        val dir = bundledDir(context.filesDir)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val marker = File(dir, ".version")
        val version = BuildConfig.VERSION_CODE.toString()
        val stale = runCatching { marker.readText() }.getOrNull() != version
        if (stale) {
            // Formats or wordlists may have changed with the app; drop every
            // extracted copy so each is re-inflated from the new APK.
            dir.listFiles { file -> file.name.endsWith(".wmdict") }?.forEach { it.delete() }
            runCatching { marker.writeText(version) }.getOrElse { return null }
        }
        val out = File(dir, "$baseName.wmdict")
        if (out.isFile) return out
        return try {
            val tmp = File(dir, "$baseName.wmdict.tmp")
            context.assets.open("dictionaries/$baseName.wmdict").use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (tmp.renameTo(out)) out else throw IOException("rename failed")
        } catch (_: IOException) {
            null
        }
    }
}
