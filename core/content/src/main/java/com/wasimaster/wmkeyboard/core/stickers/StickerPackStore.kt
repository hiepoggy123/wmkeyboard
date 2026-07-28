package com.wasimaster.wmkeyboard.core.stickers

import com.wasimaster.wmkeyboard.core.directboot.DirectBoot
import com.wasimaster.wmkeyboard.core.media.MediaMime
import com.wasimaster.wmkeyboard.core.tools.GifItem
import com.wasimaster.wmkeyboard.core.tools.GifSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * User sticker packs, persisted under `filesDir/stickers/`:
 *
 * ```
 * stickers/
 * ├── packs.json          manifest
 * └── <packId>/<stickerId>.webp|.gif
 * ```
 *
 * Unlike [com.wasimaster.wmkeyboard.core.snippets.SnippetStore] every mutator
 * writes the manifest immediately: the settings app and the IME each hold their
 * own instance, and an unsaved edit in one would be silently lost when the
 * other reloaded. [stateToken] lets the IME notice a change cheaply (the
 * DictionaryStore pattern) and call [reloadIfChanged] when its panel opens.
 *
 * The store owns the image files. Loading reconciles both directions: manifest
 * entries whose file vanished are dropped, and files or directories nothing
 * references are deleted — that's the recovery path for a crash between
 * writing bytes and writing the manifest.
 */
class StickerPackStore(private var baseDir: File?) {

    @Serializable
    private data class Snapshot(val version: Int = FORMAT_VERSION, val packs: List<StickerPack> = emptyList())

    private val packs = ArrayList<StickerPack>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var loadedToken = 0L

    init {
        reload()
    }

    companion object {
        @Volatile
        private var shared: StickerPackStore? = null

        /**
         * The one store for this process. The IME and the settings screens run
         * in the same process and both mutate packs, so they must not hold
         * separate in-memory copies of the same file.
         */
        fun get(context: android.content.Context): StickerPackStore =
            shared ?: synchronized(this) {
                shared ?: StickerPackStore(packsDir(context)).also { shared = it }
            }

        /**
         * Re-points the shared store after a direct-boot unlock. Until then it
         * has no directory at all — `filesDir` does not exist that early — and
         * reads as a user with no packs.
         */
        fun attach(context: android.content.Context) {
            get(context).attach(packsDir(context))
        }

        private fun packsDir(context: android.content.Context): File? =
            if (DirectBoot.isUserUnlocked(context)) {
                File(context.applicationContext.filesDir, DIR_NAME)
            } else {
                null
            }

        private const val FORMAT_VERSION = 1
        const val MAX_PACKS = 50
        const val MAX_STICKERS_PER_PACK = 200

        /** Prefix that marks a [GifItem] as coming from a local pack. */
        const val ITEM_PREFIX = "local_"

        /** Directory name under filesDir. */
        const val DIR_NAME = "stickers"

        /** Staging directories an import is still filling; skipped by reconcile. */
        private const val STAGING_PREFIX = ".staging_"

        fun newStickerId(): String = "s" + UUID.randomUUID().toString().replace("-", "").take(8)

        /** File name for a sticker; derived, never taken from untrusted input. */
        fun fileNameFor(id: String, mime: String): String = "$id.${MediaMime.extension(mime)}"
    }

    // ---- reading -------------------------------------------------------

    @Synchronized
    fun packs(): List<StickerPack> = packs.toList()

    @Synchronized
    fun pack(packId: String?): StickerPack? = packs.firstOrNull { it.id == packId }

    @Synchronized
    fun isEmpty(): Boolean = packs.isEmpty()

    @Synchronized
    fun totalStickers(): Int = packs.sumOf { it.stickers.size }

    /** Absolute file backing [sticker] in [packId]. */
    fun fileFor(packId: String, sticker: CustomSticker): File? =
        baseDir?.let { File(File(it, packId), sticker.fileName) }

    /** Resolves a `local_…` [GifItem.id] back to its pack and sticker. */
    @Synchronized
    fun findByItemId(itemId: String): Pair<StickerPack, CustomSticker>? {
        if (!itemId.startsWith(ITEM_PREFIX)) return null
        for (pack in packs) {
            val prefix = ITEM_PREFIX + pack.id + "_"
            if (!itemId.startsWith(prefix)) continue
            val stickerId = itemId.removePrefix(prefix)
            val sticker = pack.stickers.firstOrNull { it.id == stickerId } ?: continue
            return pack to sticker
        }
        return null
    }

    /**
     * Local stickers as grid items. [query] matches sticker name, emoji tags
     * and pack name case-insensitively; blank shows everything. [packId] limits
     * the result to one pack (null = all packs, in pack order).
     */
    @Synchronized
    fun searchAsGifItems(query: String, packId: String? = null): List<GifItem> {
        val dir = baseDir ?: return emptyList()
        val needle = query.trim()
        val scope = if (packId == null) packs else packs.filter { it.id == packId }
        val items = ArrayList<GifItem>()
        for (pack in scope) {
            val packMatches = needle.isEmpty() || pack.name.contains(needle, ignoreCase = true)
            for (sticker in pack.stickers) {
                val matches = packMatches ||
                    sticker.name.contains(needle, ignoreCase = true) ||
                    sticker.emojis.any { it.contains(needle, ignoreCase = true) }
                if (!matches) continue
                // Paths are ours — hex ids under a pack_<millis> directory —
                // so a plain file:// URL needs no escaping.
                val url = "file://" + File(File(dir, pack.id), sticker.fileName).absolutePath
                items += GifItem(
                    id = itemId(pack.id, sticker.id),
                    previewUrl = url,
                    fullUrl = url,
                    mime = sticker.mime,
                    aspectRatio = sticker.aspectRatio,
                    source = GifSource.LOCAL,
                )
            }
        }
        return items
    }

    private fun itemId(packId: String, stickerId: String) = "$ITEM_PREFIX${packId}_$stickerId"

    // ---- packs ---------------------------------------------------------

    /** Creates a pack, or returns null when [MAX_PACKS] is already reached. */
    @Synchronized
    fun createPack(name: String, now: Long = System.currentTimeMillis()): StickerPack? {
        if (packs.size >= MAX_PACKS) return null
        val pack = StickerPack(id = freePackId(now), name = uniqueName(name.trim().ifBlank { "My stickers" }), createdAt = now)
        baseDir?.let { File(it, pack.id).mkdirs() }
        packs.add(pack)
        save()
        return pack
    }

    /** Registers a pack whose directory was already filled (import path). */
    @Synchronized
    fun adoptPack(pack: StickerPack): StickerPack? {
        if (packs.size >= MAX_PACKS) return null
        val adopted = pack.copy(name = uniqueName(pack.name))
        packs.add(adopted)
        save()
        return adopted
    }

    /** A pack id whose directory doesn't exist yet. */
    @Synchronized
    fun freePackId(now: Long = System.currentTimeMillis()): String {
        var candidate = "pack_$now"
        var suffix = 1
        while (packs.any { it.id == candidate } || baseDir?.let { File(it, candidate).exists() } == true) {
            candidate = "pack_${now}_${suffix++}"
        }
        return candidate
    }

    /** Directory a pack's files live in, created if needed. */
    fun packDir(packId: String): File? =
        baseDir?.let { File(it, packId).apply { mkdirs() } }

    @Synchronized
    fun renamePack(packId: String, name: String) {
        val index = packs.indexOfFirst { it.id == packId }
        if (index < 0) return
        val trimmed = name.trim().ifBlank { return }
        if (trimmed == packs[index].name) return
        packs[index] = packs[index].copy(name = uniqueName(trimmed, exceptId = packId))
        save()
    }

    @Synchronized
    fun deletePack(packId: String) {
        if (!packs.removeAll { it.id == packId }) return
        baseDir?.let { File(it, packId).deleteRecursively() }
        save()
    }

    @Synchronized
    fun reorderPacks(order: List<String>) {
        val byId = packs.associateBy { it.id }
        val reordered = order.mapNotNull { byId[it] } + packs.filter { it.id !in order }
        if (reordered.size != packs.size) return
        packs.clear()
        packs.addAll(reordered)
        save()
    }

    // ---- stickers ------------------------------------------------------

    @Synchronized
    fun addSticker(
        packId: String,
        processed: ProcessedSticker,
        name: String = "",
        emojis: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): StickerAddResult {
        val index = packs.indexOfFirst { it.id == packId }
        if (index < 0) return StickerAddResult.PackMissing
        if (packs[index].stickers.size >= MAX_STICKERS_PER_PACK) return StickerAddResult.PackFull
        val dir = packDir(packId) ?: return StickerAddResult.WriteFailed
        val id = newStickerId()
        val fileName = fileNameFor(id, processed.mime)
        val written = runCatching {
            val part = File(dir, "$fileName.part")
            part.writeBytes(processed.bytes)
            val target = File(dir, fileName)
            if (!part.renameTo(target)) {
                target.delete()
                if (!part.renameTo(target)) {
                    part.delete()
                    return@runCatching false
                }
            }
            true
        }.getOrDefault(false)
        if (!written) return StickerAddResult.WriteFailed

        val sticker = CustomSticker(
            id = id,
            fileName = fileName,
            mime = processed.mime,
            name = name.trim(),
            emojis = emojis,
            animated = processed.animated,
            aspectRatio = processed.aspectRatio,
            addedAt = now,
        )
        packs[index] = packs[index].copy(stickers = packs[index].stickers + sticker)
        save()
        return StickerAddResult.Added(sticker)
    }

    @Synchronized
    fun removeSticker(packId: String, stickerId: String) {
        val index = packs.indexOfFirst { it.id == packId }
        if (index < 0) return
        val sticker = packs[index].stickers.firstOrNull { it.id == stickerId } ?: return
        packs[index] = packs[index].copy(stickers = packs[index].stickers - sticker)
        fileFor(packId, sticker)?.delete()
        save()
    }

    @Synchronized
    fun updateSticker(packId: String, stickerId: String, name: String, emojis: List<String>) {
        val index = packs.indexOfFirst { it.id == packId }
        if (index < 0) return
        val stickers = packs[index].stickers.toMutableList()
        val at = stickers.indexOfFirst { it.id == stickerId }
        if (at < 0) return
        stickers[at] = stickers[at].copy(name = name.trim(), emojis = emojis)
        packs[index] = packs[index].copy(stickers = stickers)
        save()
    }

    /** Moves [stickerId] to the end of [toPackId], carrying its file across. */
    @Synchronized
    fun moveSticker(fromPackId: String, stickerId: String, toPackId: String): Boolean {
        if (fromPackId == toPackId) return false
        val fromIndex = packs.indexOfFirst { it.id == fromPackId }
        val toIndex = packs.indexOfFirst { it.id == toPackId }
        if (fromIndex < 0 || toIndex < 0) return false
        if (packs[toIndex].stickers.size >= MAX_STICKERS_PER_PACK) return false
        val sticker = packs[fromIndex].stickers.firstOrNull { it.id == stickerId } ?: return false
        val source = fileFor(fromPackId, sticker) ?: return false
        val targetDir = packDir(toPackId) ?: return false
        val target = File(targetDir, sticker.fileName)
        val moved = runCatching {
            source.renameTo(target) || (source.copyTo(target, overwrite = true).exists() && source.delete())
        }.getOrDefault(false)
        if (!moved) return false
        packs[fromIndex] = packs[fromIndex].copy(stickers = packs[fromIndex].stickers - sticker)
        // Re-read the destination: it may be the same index we just rewrote.
        val destIndex = packs.indexOfFirst { it.id == toPackId }
        packs[destIndex] = packs[destIndex].copy(stickers = packs[destIndex].stickers + sticker)
        save()
        return true
    }

    /** Moves a sticker within its pack by [delta] positions. */
    @Synchronized
    fun reorderSticker(packId: String, stickerId: String, delta: Int) {
        val index = packs.indexOfFirst { it.id == packId }
        if (index < 0 || delta == 0) return
        val stickers = packs[index].stickers.toMutableList()
        val at = stickers.indexOfFirst { it.id == stickerId }
        if (at < 0) return
        val to = (at + delta).coerceIn(0, stickers.size - 1)
        if (to == at) return
        stickers.add(to, stickers.removeAt(at))
        packs[index] = packs[index].copy(stickers = stickers)
        save()
    }

    // ---- persistence ---------------------------------------------------

    private fun manifestFile(): File? = baseDir?.let { File(it, "packs.json") }

    @Synchronized
    fun save() {
        val file = manifestFile() ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val part = File(file.parentFile, "packs.json.part")
            part.writeText(json.encodeToString(Snapshot(packs = packs.toList())))
            if (!part.renameTo(file)) {
                file.delete()
                part.renameTo(file)
            }
        }
        loadedToken = currentToken()
    }

    /**
     * Cheap change signal: the manifest's size and mtime. The IME compares it
     * on panel open instead of re-parsing JSON on every keystroke.
     */
    fun stateToken(): Long = currentToken()

    private fun currentToken(): Long {
        val file = manifestFile() ?: return 0L
        if (!file.exists()) return 0L
        return file.length() * 31 + file.lastModified()
    }

    /** Reloads only when the manifest changed on disk. Returns true if it did. */
    @Synchronized
    fun reloadIfChanged(): Boolean {
        if (currentToken() == loadedToken) return false
        reload()
        return true
    }

    @Synchronized
    /**
     * Points the store at [dir] and re-reads it. Only ever called with a real
     * directory after a null one — the direct-boot unlock.
     */
    fun attach(dir: File?) {
        if (dir?.absolutePath == baseDir?.absolutePath) return
        baseDir = dir
        reload()
    }

    fun reload() {
        packs.clear()
        loadedToken = currentToken()
        val dir = baseDir ?: return
        val file = manifestFile() ?: return
        var readable = true
        if (file.exists()) {
            readable = runCatching {
                val snapshot = json.decodeFromString<Snapshot>(file.readText())
                packs.addAll(snapshot.packs.take(MAX_PACKS))
            }.isSuccess
        }
        // A manifest that exists but won't parse means we don't know what the
        // directories hold — sweeping them would destroy the user's images on
        // the strength of a bad read.
        reconcile(dir, sweepUnknown = readable)
    }

    /**
     * Makes the manifest and the files on disk agree: entries without a file
     * are dropped, files and directories nothing references are deleted.
     * Staging directories belong to an import in flight and are left alone.
     */
    private fun reconcile(dir: File, sweepUnknown: Boolean) {
        var changed = false
        for (i in packs.indices) {
            val pack = packs[i]
            val packDir = File(dir, pack.id)
            val alive = pack.stickers.filter { File(packDir, it.fileName).isFile }
                .take(MAX_STICKERS_PER_PACK)
            if (alive.size != pack.stickers.size) {
                packs[i] = pack.copy(stickers = alive)
                changed = true
            }
            val referenced = alive.mapTo(HashSet()) { it.fileName }
            packDir.listFiles()?.forEach { if (it.name !in referenced) it.deleteRecursively() }
        }
        val known = packs.mapTo(HashSet()) { it.id }
        if (sweepUnknown) dir.listFiles()?.forEach { child ->
            if (!child.isDirectory) return@forEach
            if (child.name in known || child.name.startsWith(STAGING_PREFIX)) return@forEach
            child.deleteRecursively()
        }
        if (changed) save()
    }

    /** A directory an import can fill before the pack is registered. */
    fun stagingDir(): File? =
        baseDir?.let { File(it, STAGING_PREFIX + UUID.randomUUID().toString().take(8)).apply { mkdirs() } }

    private fun uniqueName(name: String, exceptId: String? = null): String {
        val taken = packs.filter { it.id != exceptId }.mapTo(HashSet()) { it.name }
        if (name !in taken) return name
        var n = 2
        while ("$name ($n)" in taken) n++
        return "$name ($n)"
    }
}
