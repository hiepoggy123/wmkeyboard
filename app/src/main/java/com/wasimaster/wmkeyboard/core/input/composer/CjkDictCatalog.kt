package com.wasimaster.wmkeyboard.core.input.composer

import java.io.File

/**
 * One downloadable CJK conversion-dictionary pack. Small (single-digit MB)
 * compared to the [com.wasimaster.wmkeyboard.core.localllm] models, so unlike
 * those it is checksum-verified after download — a corrupt pinyin table is worse
 * than none. A pack replaces the small bundled base dictionary of the same [id]
 * once downloaded; absent, the bundled asset is used ([ConversionDictionary]).
 */
data class CjkDictPack(
    /** Stable key — storage subdir and the bundled asset base name (`pinyin`). */
    val id: String,
    /** Owning language id (`zh` / `ja`) — gates which detail screen shows it. */
    val langId: String,
    val displayName: String,
    val description: String,
    /** Where the pack is hosted. Blank until the maintainer fills it in. */
    val url: String,
    /** Approximate size — preflight space check and progress fallback. */
    val sizeBytes: Long,
    /** Lowercase-hex SHA-256 of the downloaded file; verified before it goes live. */
    val sha256: String,
    /** On-disk file name, matching the bundled asset it supersedes. */
    val fileName: String,
) {
    /** A pack can only be offered once it has a hosting URL and a checksum. */
    val available: Boolean get() = url.isNotBlank() && sha256.isNotBlank()
}

/**
 * The curated CJK dictionary packs, one per language. Bigger and better than the
 * tiny bundled `pinyin.tsv` / `ja_kana.tsv`: CC-CEDICT-derived Hanzi for Chinese,
 * a mozc/SudachiDict-derived kana→kanji table for Japanese. The URLs and
 * checksums are filled once the packs are built and hosted; until then the pack
 * is [CjkDictPack.available] == false and the settings row says so.
 */
object CjkDictCatalog {

    val packs: List<CjkDictPack> = listOf(
        CjkDictPack(
            id = "pinyin",
            langId = "zh",
            displayName = "Chinese Pinyin (large)",
            description = "CC-CEDICT-derived Hanzi & phrases — far more than the built-in set.",
            url = "",
            sizeBytes = 6_000_000L,
            sha256 = "",
            fileName = "pinyin.tsv",
        ),
        CjkDictPack(
            id = "ja_kana",
            langId = "ja",
            displayName = "Japanese kana→kanji (large)",
            description = "mozc/SudachiDict-derived readings — richer romaji→kanji conversion.",
            url = "",
            sizeBytes = 8_000_000L,
            sha256 = "",
            fileName = "ja_kana.tsv",
        ),
    )

    init {
        check(packs.map { it.id }.toSet().size == packs.size) { "pack ids must be unique" }
    }

    fun byId(id: String): CjkDictPack? = packs.firstOrNull { it.id == id }

    fun forLang(langId: String): List<CjkDictPack> = packs.filter { it.langId == langId }
}

/**
 * File layout for downloaded packs: `filesDir/cjk_dicts/<id>/<fileName>` with a
 * `<fileName>.part` while in flight. The atomic-rename + checksum contract means
 * a final (non-.part) file exists only if its download completed and verified,
 * so presence == valid, exactly as [com.wasimaster.wmkeyboard.core.localllm.LocalLlmStore].
 */
object CjkDictStore {

    private fun dictsDir(filesDir: File) = File(filesDir, "cjk_dicts")

    fun packDir(filesDir: File, pack: CjkDictPack): File = File(dictsDir(filesDir), pack.id)

    fun packFile(filesDir: File, pack: CjkDictPack): File =
        File(packDir(filesDir, pack), pack.fileName)

    fun partFile(filesDir: File, pack: CjkDictPack): File =
        File(packDir(filesDir, pack), "${pack.fileName}.part")

    fun isDownloaded(filesDir: File, pack: CjkDictPack): Boolean = packFile(filesDir, pack).isFile

    fun delete(filesDir: File, pack: CjkDictPack) {
        packDir(filesDir, pack).deleteRecursively()
    }

    /** The downloaded pack file for [id] if present, else null (use the bundled asset). */
    fun downloadedFileFor(filesDir: File, id: String): File? =
        CjkDictCatalog.byId(id)?.let { packFile(filesDir, it).takeIf(File::isFile) }

    /**
     * A monotonically-changing token summarising which packs are on disk. The
     * service compares it against the token it last loaded from and re-parses the
     * conversion tables only when it differs — so a pack finished in Settings goes
     * live the next time a field is focused, without re-parsing on every keypress.
     */
    fun stateToken(filesDir: File): Int =
        CjkDictCatalog.packs.fold(0) { acc, pack ->
            acc * 31 + if (isDownloaded(filesDir, pack)) 1 else 0
        }
}
