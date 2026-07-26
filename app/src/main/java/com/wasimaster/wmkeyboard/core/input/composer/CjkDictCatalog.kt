package com.wasimaster.wmkeyboard.core.input.composer

import java.io.File

/**
 * One downloadable CJK conversion-dictionary pack. These tables are large and
 * useful only to a minority, so they are NOT bundled — the pack is the only
 * source. Until it is downloaded the composer types the raw reading with no
 * candidates. Checksum-verified after download (a corrupt table is worse than
 * none); see [ConversionDictionary].
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
    /** On-disk file name for the downloaded pack. */
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
            displayName = "Chinese Pinyin dictionary",
            description = "CC-CEDICT-derived Hanzi & phrases (120k entries). Required for Chinese conversion.",
            url = "https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/HEAD/cjk/pinyin.tsv",
            sizeBytes = 2_489_699L,
            sha256 = "8baab4c758499272e36dba4bda4253317a4f93bb88bcf386ea86196c50d73715",
            fileName = "pinyin.tsv",
        ),
        CjkDictPack(
            id = "ja_kana",
            langId = "ja",
            displayName = "Japanese kana→kanji dictionary",
            description = "mozc-derived readings (1.08M entries, ~42 MB). Required for Japanese conversion.",
            url = "https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/HEAD/cjk/ja_kana.tsv",
            sizeBytes = 41_531_397L,
            sha256 = "189214b81968c857d7cb020c52fc087ee44918ab28534194f79ea66f45c17a70",
            fileName = "ja_kana.tsv",
        ),
        CjkDictPack(
            id = "stroke",
            langId = "zh",
            displayName = "Chinese stroke (笔画) dictionary",
            description = "Stroke-order → Hanzi table. Required for the stroke keyboard.",
            url = "https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/HEAD/cjk/stroke.tsv",
            sizeBytes = 445_686L,
            sha256 = "b3bb4669cf8e411fd63b3a15e07e32db84ed1f2cfe09fb67066866ebe9b2278c",
            fileName = "stroke.tsv",
        ),
        CjkDictPack(
            id = "cangjie",
            langId = "zh",
            displayName = "Cangjie (倉頡) dictionary",
            description = "Radical-code → Hanzi table (29k characters). Required for the Cangjie and Quick keyboards.",
            url = "https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/HEAD/cjk/cangjie.tsv",
            sizeBytes = 351_337L,
            sha256 = "77a5a4c054019e3a3ea875e86a37cb08b71ce29392b6680208bb0c5749feb25d",
            fileName = "cangjie.tsv",
        ),
        CjkDictPack(
            id = "jyutping",
            langId = "yue",
            displayName = "Cantonese Jyutping dictionary",
            description = "Jyutping reading → Hanzi table (144k entries). Required for Cantonese conversion.",
            url = "https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/HEAD/cjk/jyutping.tsv",
            sizeBytes = 2_993_536L,
            sha256 = "955764a4bbff61b62de9fb55d4f1fd78c25e995b00ee2afdfdc0a005ae06ea3c",
            fileName = "jyutping.tsv",
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

    /** The downloaded pack file for [id] if present, else null (no candidates until downloaded). */
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
