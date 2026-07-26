package com.wasimaster.wmkeyboard.core.icons

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The `.wmicons` icon-pack file: the app's native icon export and import.
 *
 * ```
 * mypack.wmicons
 * ├── pack.json
 * └── icons/<slotId>.svg
 * ```
 *
 * `pack.json` is the same versioned envelope the layout and sticker formats
 * use — `format` is the one strict check, and everything past it is repaired
 * and reported rather than refused, because a hand-built pack shouldn't fail
 * over one bad file:
 *
 * ```json
 * {
 *   "format": "wmkeyboard-icons",
 *   "version": 1,
 *   "appVersion": 41,
 *   "appVersionName": "1.4.0",
 *   "pack": {
 *     "id": "rounded",
 *     "name": "Rounded",
 *     "author": "…",
 *     "description": "…",
 *     "version": "1.0.0",
 *     "slots": ["tool.clipboard", "key.enter_send", "…"]
 *   }
 * }
 * ```
 *
 * A ZIP rather than the base64-in-JSON the theme export uses, for the same
 * reason the sticker format is: a full pack is ~90 separate files and the
 * archive streams entry by entry.
 *
 * Slot ids are the file names — an icon for `tool.clipboard` is
 * `icons/tool.clipboard.svg`. The list in `slots` is advisory; the import
 * walks the archive's entries and keeps every one whose name matches a slot
 * [IconSlots] knows, so a pack hand-assembled without a careful manifest still
 * works.
 *
 * **Entry names are never used as paths.** An entry is matched against the
 * known slot ids and then written to a name derived from *that id*, so neither
 * `../` in an entry name nor anything in the manifest can escape the pack
 * directory. Icons the app has no slot for are dropped, not stored.
 */
object IconPackFile {

    const val FORMAT = "wmkeyboard-icons"
    const val VERSION = 1
    const val FILE_EXTENSION = "wmicons"
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

    const val MANIFEST = "pack.json"
    private const val ICON_DIR = "icons/"

    /** Zip-bomb guards: a full pack is ~90 small SVGs, so nothing legitimate is near these. */
    private const val MAX_ENTRIES = 400
    private const val MAX_TOTAL_BYTES = 8L * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    @Serializable
    private data class IconEnvelope(
        val format: String = "",
        val version: Int = 0,
        val appVersion: Int = 0,
        val appVersionName: String = "",
        val pack: IconPack = IconPack(id = "", name = ""),
    )

    fun fileName(pack: IconPack): String {
        val stem = pack.name.ifBlank { "icons" }
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            .trim()
            .ifBlank { "icons" }
        return "$stem.$FILE_EXTENSION"
    }

    /**
     * Writes [pack] and its SVGs to [out]. A slot whose file has gone missing
     * is skipped and left out of the manifest — exporting is not the moment to
     * fail over one.
     */
    fun write(
        out: OutputStream,
        pack: IconPack,
        appVersion: Int,
        appVersionName: String,
        fileFor: (String) -> File?,
    ) {
        ZipOutputStream(out.buffered()).use { zip ->
            val present = pack.slots.filter { fileFor(it)?.isFile == true }
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(
                json.encodeToString(
                    IconEnvelope(
                        format = FORMAT,
                        version = VERSION,
                        appVersion = appVersion,
                        appVersionName = appVersionName,
                        pack = pack.copy(slots = present),
                    )
                ).toByteArray()
            )
            zip.closeEntry()
            for (slot in present) {
                val file = fileFor(slot) ?: continue
                zip.putNextEntry(ZipEntry(ICON_DIR + IconPackStore.fileNameFor(slot)))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Reads a pack out of [input] and registers it with [store] under a fresh
     * id, so importing the same file twice can never collide with itself.
     */
    fun import(
        input: InputStream,
        store: IconPackStore,
        now: Long = System.currentTimeMillis(),
    ): IconImportResult {
        val staging = store.stagingDir() ?: return IconImportResult.Failed
        // Cleared once the pack is registered; until then any exit path takes
        // the half-filled directory with it.
        var unadopted: File? = null
        try {
            // Slot id → the staged file holding its SVG.
            val staged = HashMap<String, File>()
            val skipped = ArrayList<String>()
            var manifest: String? = null
            val read = runCatching {
                ZipInputStream(input.buffered()).use { zip ->
                    var count = 0
                    var total = 0L
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        if (++count > MAX_ENTRIES) break
                        val name = entry.name
                        if (name == MANIFEST) {
                            manifest = zip.readBytes().decodeToString()
                            continue
                        }
                        // The entry name is matched against the known slots and
                        // then discarded; it never reaches the filesystem.
                        val slot = slotForEntry(name)
                        if (slot == null) {
                            if (skipped.size < 8) skipped += name.substringAfterLast('/')
                            continue
                        }
                        val target = File(staging, "e$count.svg")
                        var written = 0L
                        target.outputStream().buffered().use { sink ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n <= 0) break
                                written += n
                                total += n
                                if (written > SvgParser.MAX_SOURCE_BYTES || total > MAX_TOTAL_BYTES) break
                                sink.write(buffer, 0, n)
                            }
                        }
                        if (written > SvgParser.MAX_SOURCE_BYTES || total > MAX_TOTAL_BYTES) {
                            target.delete()
                            if (total > MAX_TOTAL_BYTES) break
                        } else {
                            staged[slot] = target
                        }
                    }
                }
                true
            }.getOrDefault(false)
            if (!read && manifest == null) return IconImportResult.Failed

            val envelope = manifest
                ?.let { runCatching { json.decodeFromString<IconEnvelope>(it) }.getOrNull() }
                ?: return IconImportResult.NotAnIconPack
            if (envelope.format != FORMAT) return IconImportResult.NotAnIconPack

            val repairs = ArrayList<String>()
            if (skipped.isNotEmpty()) {
                repairs += "Ignored ${skipped.size} file(s) this version has no icon for: " +
                    skipped.joinToString(", ")
            }

            val packId = store.freePackId(now)
            val packDir = store.packDir(packId) ?: return IconImportResult.Failed
            unadopted = packDir
            val kept = ArrayList<String>()
            // Manifest order first so the pack author's ordering survives, then
            // anything in the archive the manifest forgot to list.
            val order = envelope.pack.slots.filter { it in staged } +
                staged.keys.filter { it !in envelope.pack.slots }
            for (slot in order.distinct()) {
                val source = staged[slot] ?: continue
                val svg = runCatching { source.readText() }.getOrNull()
                if (svg == null || SvgParser.parse(svg) == null) {
                    repairs += "Dropped “$slot” — its SVG couldn't be read"
                    continue
                }
                val ok = runCatching {
                    File(packDir, IconPackStore.fileNameFor(slot)).writeText(svg)
                    true
                }.getOrDefault(false)
                if (!ok) {
                    repairs += "Dropped “$slot” — it couldn't be saved"
                    continue
                }
                kept += slot
            }
            if (kept.isEmpty()) return IconImportResult.Failed

            val pack = IconPack(
                id = packId,
                name = envelope.pack.name.trim().ifBlank { "Imported icons" },
                author = envelope.pack.author.trim(),
                description = envelope.pack.description.trim(),
                version = envelope.pack.version.trim(),
                slots = kept,
                createdAt = now,
            )
            val adopted = store.adoptPack(pack) ?: return IconImportResult.TooManyPacks
            unadopted = null
            return IconImportResult.Imported(adopted, repairs)
        } finally {
            staging.deleteRecursively()
            unadopted?.deleteRecursively()
        }
    }

    /**
     * `icons/tool.clipboard.svg` → `tool.clipboard`, or null when the entry
     * names no slot this version knows. Accepts the file at the archive root
     * too, since that is how a pack assembled by hand often ends up.
     */
    fun slotForEntry(entryName: String): String? {
        val base = entryName.substringAfterLast('/').substringAfterLast('\\')
        if (!base.endsWith(".svg", ignoreCase = true)) return null
        val slot = base.dropLast(4).lowercase()
        if (!IconSlots.isWellFormed(slot)) return null
        return if (IconSlots.byId(slot) != null) slot else null
    }
}
