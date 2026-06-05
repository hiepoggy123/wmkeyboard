package com.wasimaster.wmkeyboard.core.snippets

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A reusable text snippet inserted from the keyboard's snippet panel. */
@Serializable
data class Snippet(
    val id: Long,
    val label: String,
    val text: String,
    val createdAt: Long = 0,
)

/**
 * User-defined snippets with template variables, persisted as JSON in
 * app-private storage (same offline-first pattern as ClipboardStore).
 *
 * Supported variables, expanded at insertion time:
 *  - `{date}`     → 19 Jul 2026
 *  - `{time}`     → 16:45
 *  - `{datetime}` → 19 Jul 2026 16:45
 *  - `{clip}`     → most recent clipboard entry (empty when none)
 */
class SnippetStore(private val storageFile: File?) {

    @Serializable
    private data class Snapshot(val snippets: List<Snippet> = emptyList())

    private val snippets = ArrayList<Snippet>()
    private val json = Json { ignoreUnknownKeys = true }
    private var nextId = 1L

    init {
        reload()
    }

    @Synchronized
    fun items(): List<Snippet> = snippets.toList()

    @Synchronized
    fun add(label: String, text: String, now: Long = System.currentTimeMillis()): Snippet {
        val snippet = Snippet(id = nextId++, label = label.trim(), text = text, createdAt = now)
        snippets.add(snippet)
        return snippet
    }

    @Synchronized
    fun update(id: Long, label: String, text: String) {
        val index = snippets.indexOfFirst { it.id == id }
        if (index >= 0) {
            snippets[index] = snippets[index].copy(label = label.trim(), text = text)
        }
    }

    @Synchronized
    fun remove(id: Long) {
        snippets.removeAll { it.id == id }
    }

    @Synchronized
    fun save() {
        val file = storageFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Snapshot(snippets.toList())))
        }
    }

    /** Re-reads the backing file (settings app and IME share the store). */
    @Synchronized
    fun reload() {
        snippets.clear()
        val file = storageFile ?: return
        if (!file.exists()) return
        runCatching {
            val snapshot = json.decodeFromString<Snapshot>(file.readText())
            snippets.addAll(snapshot.snippets)
        }
        nextId = (snippets.maxOfOrNull { it.id } ?: 0) + 1
    }

    companion object {
        /** Expands template variables. [clipboard] is the latest clip text. */
        fun expand(
            text: String,
            now: Long = System.currentTimeMillis(),
            clipboard: String? = null,
        ): String {
            val date = Date(now)
            fun fmt(pattern: String) = SimpleDateFormat(pattern, Locale.getDefault()).format(date)
            return text
                .replace("{date}", fmt("d MMM yyyy"))
                .replace("{time}", fmt("HH:mm"))
                .replace("{datetime}", fmt("d MMM yyyy HH:mm"))
                .replace("{clip}", clipboard.orEmpty())
        }
    }
}
