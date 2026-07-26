package com.wasimaster.wmkeyboard.core.stickers

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The outcome of reading a `.wmstickers` file. */
sealed interface StickerImportResult {
    data class Imported(val pack: StickerPack, val repairs: List<String>) : StickerImportResult

    /** No manifest, or a manifest for something else entirely. */
    data object NotAStickerPack : StickerImportResult

    /** [StickerPackStore.MAX_PACKS] reached. */
    data object TooManyPacks : StickerImportResult

    /** Unreadable archive, or nowhere to write. */
    data object Failed : StickerImportResult
}

@Serializable
private data class StickerEnvelope(
    val format: String,
    val version: Int,
    val appVersion: Int = 0,
    val appVersionName: String = "",
    val pack: StickerPack,
)

/**
 * A sticker pack as a shareable file: a ZIP holding a `pack.json` manifest and
 * the sticker images beside it.
 *
 * ```
 * mypack.wmstickers
 * ├── pack.json
 * └── stickers/<fileName>
 * ```
 *
 * A ZIP rather than the base64-in-JSON the theme export uses, because a pack is
 * bulk binary: 200 stickers would be a ~27 MB JSON string that has to be held in
 * memory whole to parse, where the archive streams entry by entry.
 *
 * The manifest is the same versioned envelope as
 * [com.wasimaster.wmkeyboard.core.layout.LayoutFile] — the format tag is the one
 * strict check, and everything past it is repaired and reported rather than
 * refused, since a hand-built pack shouldn't fail on one bad entry.
 *
 * **Entry names are never used as paths.** Every file is written to a name
 * derived from a freshly generated sticker id, so neither `../` in an entry name
 * nor `../` in a manifest `fileName` can escape the pack directory.
 */
object StickerPackFile {

    const val FORMAT = "wmkeyboard-stickers"
    const val VERSION = 1
    const val FILE_EXTENSION = "wmstickers"
    const val MIME_TYPE = "application/zip"

    /**
     * What the import picker accepts. Permissive on purpose: providers report
     * a custom extension as `application/octet-stream` as often as not, and the
     * real check is the manifest inside.
     */
    val IMPORT_MIME_TYPES = arrayOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/octet-stream",
    )

    private const val MANIFEST = "pack.json"
    private const val STICKER_DIR = "stickers/"

    /** Zip-bomb guards: nothing legitimate comes close to either. */
    private const val MAX_ENTRIES = 500
    private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun fileName(pack: StickerPack): String {
        val stem = pack.name.ifBlank { "stickers" }
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            .trim()
            .ifBlank { "stickers" }
        return "$stem.$FILE_EXTENSION"
    }

    /**
     * Writes [pack] and its images to [out]. Stickers whose file has gone
     * missing are skipped — exporting is not the moment to fail over one.
     */
    fun write(
        out: OutputStream,
        pack: StickerPack,
        appVersion: Int,
        appVersionName: String,
        fileFor: (CustomSticker) -> File?,
    ) {
        ZipOutputStream(out.buffered()).use { zip ->
            val present = pack.stickers.filter { fileFor(it)?.isFile == true }
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(
                json.encodeToString(
                    StickerEnvelope(
                        format = FORMAT,
                        version = VERSION,
                        appVersion = appVersion,
                        appVersionName = appVersionName,
                        pack = pack.copy(stickers = present),
                    )
                ).toByteArray()
            )
            zip.closeEntry()
            for (sticker in present) {
                val file = fileFor(sticker) ?: continue
                zip.putNextEntry(ZipEntry(STICKER_DIR + sticker.fileName))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Reads a pack out of [input] and registers it with [store] under a fresh
     * id, so importing the same file twice can never collide with itself.
     *
     * [normalize] turns raw bytes into storable sticker bytes; the default runs
     * them through [StickerImage], which keeps conforming files untouched and
     * re-encodes everything else.
     */
    fun import(
        input: InputStream,
        store: StickerPackStore,
        now: Long = System.currentTimeMillis(),
        normalize: (ByteArray) -> ProcessedSticker? = { bytes ->
            (StickerImage.process(bytes) as? StickerImage.Result.Ok)?.sticker
        },
    ): StickerImportResult {
        val staging = store.stagingDir() ?: return StickerImportResult.Failed
        // Cleared once the pack is registered; until then any exit path —
        // including a normalizer that throws — takes the half-filled
        // directory with it.
        var unadopted: File? = null
        try {
            val staged = HashMap<String, File>()
            var manifest: String? = null
            val read = runCatching {
                ZipInputStream(input.buffered()).use { zip ->
                    var count = 0
                    var total = 0L
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        if (++count > MAX_ENTRIES) break
                        // The entry name is a lookup key, never a path.
                        val name = entry.name
                        if (name == MANIFEST) {
                            manifest = zip.readBytes().decodeToString()
                            continue
                        }
                        val target = File(staging, "e$count.bin")
                        var written = 0L
                        target.outputStream().buffered().use { sink ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n <= 0) break
                                written += n
                                total += n
                                if (written > StickerImage.MAX_SOURCE_BYTES || total > MAX_TOTAL_BYTES) break
                                sink.write(buffer, 0, n)
                            }
                        }
                        if (written > StickerImage.MAX_SOURCE_BYTES || total > MAX_TOTAL_BYTES) {
                            target.delete()
                            if (total > MAX_TOTAL_BYTES) break
                        } else {
                            staged[name] = target
                        }
                    }
                }
                true
            }.getOrDefault(false)
            if (!read && manifest == null) return StickerImportResult.Failed

            val envelope = manifest
                ?.let { runCatching { json.decodeFromString<StickerEnvelope>(it) }.getOrNull() }
                ?: return StickerImportResult.NotAStickerPack
            if (envelope.format != FORMAT) return StickerImportResult.NotAStickerPack

            val repairs = ArrayList<String>()
            val declared = envelope.pack.stickers
            if (declared.size > StickerPackStore.MAX_STICKERS_PER_PACK) {
                repairs += "Kept the first ${StickerPackStore.MAX_STICKERS_PER_PACK} stickers " +
                    "of ${declared.size}"
            }
            val packId = store.freePackId(now)
            val packDir = store.packDir(packId) ?: return StickerImportResult.Failed
            unadopted = packDir
            val kept = ArrayList<CustomSticker>()
            for (declaredSticker in declared.take(StickerPackStore.MAX_STICKERS_PER_PACK)) {
                val name = declaredSticker.fileName
                if (name.isBlank() || name.contains('/') || name.contains('\\') || name.contains("..")) {
                    repairs += "Dropped a sticker with an unusable file name"
                    continue
                }
                val source = staged[STICKER_DIR + name] ?: staged[name]
                if (source == null) {
                    repairs += "Dropped “${declaredSticker.label()}” — its image is missing"
                    continue
                }
                val processed = normalize(source.readBytes())
                if (processed == null) {
                    repairs += "Dropped “${declaredSticker.label()}” — its image couldn't be read"
                    continue
                }
                val id = StickerPackStore.newStickerId()
                val fileName = StickerPackStore.fileNameFor(id, processed.mime)
                val ok = runCatching { File(packDir, fileName).writeBytes(processed.bytes); true }
                    .getOrDefault(false)
                if (!ok) {
                    repairs += "Dropped “${declaredSticker.label()}” — it couldn't be saved"
                    continue
                }
                kept += declaredSticker.copy(
                    id = id,
                    fileName = fileName,
                    mime = processed.mime,
                    animated = processed.animated,
                    aspectRatio = processed.aspectRatio,
                    addedAt = if (declaredSticker.addedAt > 0) declaredSticker.addedAt else now,
                )
            }

            val pack = StickerPack(
                id = packId,
                name = envelope.pack.name.trim().ifBlank { "Imported stickers" },
                stickers = kept,
                createdAt = now,
            )
            val adopted = store.adoptPack(pack) ?: return StickerImportResult.TooManyPacks
            unadopted = null
            return StickerImportResult.Imported(adopted, repairs)
        } finally {
            staging.deleteRecursively()
            unadopted?.deleteRecursively()
        }
    }

    private fun CustomSticker.label(): String = name.ifBlank { fileName }
}
