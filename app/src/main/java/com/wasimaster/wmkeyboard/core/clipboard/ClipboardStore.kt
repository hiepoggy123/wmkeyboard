package com.wasimaster.wmkeyboard.core.clipboard

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ClipItem(
    val id: Long,
    val text: String,
    val pinned: Boolean = false,
    val timestamp: Long,
)

/**
 * Clipboard history with pinning, persisted as JSON on device.
 *
 * The IME service feeds new clips in via [add] from its
 * OnPrimaryClipChangedListener. Unpinned items expire after
 * [expiryMillis] (0 disables expiry); pinned items never expire.
 */
class ClipboardStore(
    private val storageFile: File?,
    var expiryMillis: Long = DEFAULT_EXPIRY_MILLIS,
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
                items.addAll(snapshot.items)
                nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
            }
        }
    }

    @Synchronized
    fun add(text: String, now: Long = System.currentTimeMillis()): ClipItem? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        // Re-copying an existing item moves it to the top instead of duplicating.
        val existing = items.firstOrNull { it.text == trimmed }
        if (existing != null) {
            items.remove(existing)
            val refreshed = existing.copy(timestamp = now)
            items.add(0, refreshed)
            return refreshed
        }
        val item = ClipItem(id = nextId++, text = trimmed, timestamp = now)
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

    @Synchronized
    fun setPinned(id: Long, pinned: Boolean) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = items[index].copy(pinned = pinned)
    }

    @Synchronized
    fun remove(id: Long) {
        items.removeAll { it.id == id }
    }

    @Synchronized
    fun clearUnpinned() {
        items.removeAll { !it.pinned }
    }

    @Synchronized
    fun search(query: String): List<ClipItem> =
        items().filter { it.text.contains(query, ignoreCase = true) }

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
            items.removeAll { !it.pinned && now - it.timestamp > expiryMillis }
        }
        while (items.count { !it.pinned } > MAX_ITEMS) {
            val oldest = items.filter { !it.pinned }.minByOrNull { it.timestamp } ?: break
            items.remove(oldest)
        }
    }
}
