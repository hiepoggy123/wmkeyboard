package com.wasimaster.wmkeyboard.core.clipboard

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** What a clip holds: plain text, styled text (HTML source kept), or an image file. */
@Serializable
enum class ClipKind { TEXT, HTML, IMAGE }

@Serializable
data class ClipItem(
    val id: Long,
    val text: String,
    val pinned: Boolean = false,
    val timestamp: Long,
    val kind: ClipKind = ClipKind.TEXT,
    /** Original HTML markup for [ClipKind.HTML] items; [text] holds the plain-text form. */
    val htmlText: String? = null,
    /** Absolute path of the copied image for [ClipKind.IMAGE] items. */
    val imagePath: String? = null,
    val mimeType: String = "text/plain",
)

/**
 * Clipboard history with pinning, persisted as JSON on device.
 *
 * The IME service feeds new clips in via [add]/[addHtml]/[addImage] from its
 * OnPrimaryClipChangedListener. Unpinned items expire after
 * [expiryMillis] (0 disables expiry); pinned items never expire.
 *
 * Image clips reference files the service copies into [imagesDir]; the store
 * owns their lifecycle and deletes the file whenever its item is removed
 * (expiry, cap, delete, clear).
 */
class ClipboardStore(
    private val storageFile: File?,
    var expiryMillis: Long = DEFAULT_EXPIRY_MILLIS,
    private val imagesDir: File? = null,
) {

    @Serializable
    private data class Snapshot(val items: List<ClipItem> = emptyList())

    private val items = ArrayList<ClipItem>()
    private val json = Json { ignoreUnknownKeys = true }
    private var nextId = 1L

    companion object {
        const val DEFAULT_EXPIRY_MILLIS = 24L * 60 * 60 * 1000 // 1 day
        private const val MAX_ITEMS = 100
    }

    init {
        storageFile?.takeIf { it.exists() }?.let { file ->
            runCatching {
                val snapshot = json.decodeFromString<Snapshot>(file.readText())
                items.addAll(snapshot.items.filter { it.kind != ClipKind.IMAGE || it.imagePath != null })
                nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
            }
        }
        // Image files whose item is gone (crash between file copy and save).
        imagesDir?.listFiles()?.let { files ->
            val referenced = items.mapNotNull { it.imagePath }.toSet()
            files.filter { it.absolutePath !in referenced }.forEach { it.delete() }
        }
    }

    @Synchronized
    fun add(text: String, now: Long = System.currentTimeMillis()): ClipItem? =
        addTextual(text, html = null, now = now)

    /** Styled text: [html] is the markup, [text] the plain-text rendering. */
    @Synchronized
    fun addHtml(text: String, html: String, now: Long = System.currentTimeMillis()): ClipItem? =
        addTextual(text, html = html, now = now)

    /**
     * Registers an image already copied to [imageFile] (inside [imagesDir]).
     * The store takes ownership of the file.
     */
    @Synchronized
    fun addImage(imageFile: File, mimeType: String, now: Long = System.currentTimeMillis()): ClipItem? {
        if (!imageFile.exists()) return null
        val item = ClipItem(
            id = nextId++,
            text = "",
            timestamp = now,
            kind = ClipKind.IMAGE,
            imagePath = imageFile.absolutePath,
            mimeType = mimeType,
        )
        items.add(0, item)
        prune(now)
        return item.takeIf { items.contains(item) }
    }

    private fun addTextual(text: String, html: String?, now: Long): ClipItem? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        // Re-copying an existing item moves it to the top instead of duplicating.
        val existing = items.firstOrNull { it.kind != ClipKind.IMAGE && it.text == trimmed }
        if (existing != null) {
            items.remove(existing)
            val refreshed = existing.copy(
                timestamp = now,
                kind = if (html != null) ClipKind.HTML else existing.kind,
                htmlText = html ?: existing.htmlText,
            )
            items.add(0, refreshed)
            return refreshed
        }
        val item = ClipItem(
            id = nextId++,
            text = trimmed,
            timestamp = now,
            kind = if (html != null) ClipKind.HTML else ClipKind.TEXT,
            htmlText = html,
            mimeType = if (html != null) "text/html" else "text/plain",
        )
        items.add(0, item)
        prune(now)
        return item
    }

    @Synchronized
    fun items(now: Long = System.currentTimeMillis()): List<ClipItem> {
        prune(now)
        return items.sortedWith(
            compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.timestamp }
        )
    }

    /** Most recent textual clip, for snippet {clip} expansion. */
    @Synchronized
    fun latestText(now: Long = System.currentTimeMillis()): String? =
        items(now).firstOrNull { it.kind != ClipKind.IMAGE }?.text

    @Synchronized
    fun setPinned(id: Long, pinned: Boolean) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = items[index].copy(pinned = pinned)
    }

    @Synchronized
    fun remove(id: Long) {
        removeWhere { it.id == id }
    }

    @Synchronized
    fun clearUnpinned() {
        removeWhere { !it.pinned }
    }

    @Synchronized
    fun search(query: String): List<ClipItem> =
        items().filter { it.kind != ClipKind.IMAGE && it.text.contains(query, ignoreCase = true) }

    @Synchronized
    fun save() {
        val file = storageFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Snapshot(items.toList())))
        }
    }

    private fun prune(now: Long) {
        if (expiryMillis > 0) {
            removeWhere { !it.pinned && now - it.timestamp > expiryMillis }
        }
        while (items.count { !it.pinned } > MAX_ITEMS) {
            val oldest = items.filter { !it.pinned }.minByOrNull { it.timestamp } ?: break
            removeWhere { it === oldest }
        }
    }

    private fun removeWhere(predicate: (ClipItem) -> Boolean) {
        val removed = items.filter(predicate)
        items.removeAll(predicate)
        removed.forEach { item -> item.imagePath?.let { File(it).delete() } }
    }
}
