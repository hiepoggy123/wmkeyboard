package com.wasimaster.wmkeyboard.core.clipboard

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What a clip holds. [TEXT]/[HTML]/[LINK] are textual (insertable as text);
 * [IMAGE] is a file this app owns; [FILE]/[FOLDER] are content URIs owned by
 * the app that did the copying.
 */
@Serializable
enum class ClipKind {
    TEXT, HTML, LINK, IMAGE, FILE, FOLDER;

    /** True when the clip can be committed as plain text. */
    val isTextual: Boolean get() = this == TEXT || this == HTML || this == LINK
}

/** Open Graph / HTML metadata for a [ClipKind.LINK] clip, fetched on demand. */
@Serializable
data class LinkPreview(
    val title: String = "",
    val description: String = "",
    val siteName: String = "",
    val imageUrl: String = "",
    /** A finished attempt that found nothing; stops us re-fetching every open. */
    val failed: Boolean = false,
) {
    val isEmpty: Boolean get() = title.isBlank() && description.isBlank() && siteName.isBlank()
}

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
    /** Source content URI for [ClipKind.FILE] and [ClipKind.FOLDER] items. */
    val uriString: String? = null,
    /** Display name of a file/folder clip. */
    val fileName: String? = null,
    /** Byte size of a file clip; -1 when the provider didn't report one. */
    val fileSize: Long = -1,
    /** Fetched metadata for a [ClipKind.LINK] clip; null until fetched. */
    val linkPreview: LinkPreview? = null,
    /**
     * Human-readable label of the app the clip was copied from (falling back to
     * its package name when the label can't be resolved). Null when source
     * tracking is off, unavailable, or the source couldn't be determined.
     */
    val sourceApp: String? = null,
)

/**
 * Everything about a clip that its search query is matched against: the text
 * itself, a file's name, a link's fetched metadata, the source app, and the
 * kind/format words ("image", "png", "folder") so a clip with no text of its
 * own — an image, a screenshot — is still findable by what it is.
 */
fun ClipItem.searchHaystack(): String = buildList {
    add(text)
    fileName?.let(::add)
    linkPreview?.let { preview ->
        if (!preview.failed) {
            add(preview.title)
            add(preview.description)
            add(preview.siteName)
        }
    }
    sourceApp?.let(::add)
    add(
        when (kind) {
            ClipKind.TEXT -> "text"
            ClipKind.HTML -> "text rich text html"
            ClipKind.LINK -> "link url"
            ClipKind.IMAGE -> "image picture screenshot"
            ClipKind.FILE -> "file"
            ClipKind.FOLDER -> "folder directory"
        },
    )
    mimeType.substringAfterLast('/').takeIf { it.isNotBlank() }?.let(::add)
}.joinToString(" ")

/**
 * Whether this clip should be shown for [query]. A blank query matches
 * everything; otherwise the query is a case-insensitive substring of
 * [searchHaystack]. The clipboard panel's filter and [ClipboardStore.search]
 * both go through here so the two can never drift apart.
 */
fun ClipItem.matchesQuery(query: String): Boolean {
    val trimmed = query.trim()
    return trimmed.isEmpty() || searchHaystack().contains(trimmed, ignoreCase = true)
}

/** Recognises clips that are a bare URL, so they can be shown as links. */
object ClipLinks {

    private val URL_REGEX = Regex(
        """^(?:https?://|www\.)[^\s<>"']+$""",
        RegexOption.IGNORE_CASE,
    )

    /** The clip text as a URL, or null when it isn't a single bare link. */
    fun asUrl(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.length > 2048 || !URL_REGEX.matches(trimmed)) return null
        return if (trimmed.startsWith("www.", ignoreCase = true)) "https://$trimmed" else trimmed
    }

    /** Host without a leading `www.`, for the site label on a link card. */
    fun host(url: String): String =
        runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
            .removePrefix("www.")
}

/**
 * Clipboard history with pinning, persisted as JSON on device.
 *
 * The IME service feeds new clips in via [add]/[addHtml]/[addImage]/[addUri]
 * from its OnPrimaryClipChangedListener. Unpinned items expire after
 * [expiryMillis] (0 disables expiry); pinned items never expire.
 *
 * Image clips reference files the service copies into [imagesDir]; the store
 * owns their lifecycle and deletes the file whenever its item is removed
 * (expiry, cap, delete, clear). File and folder clips only record the source
 * URI — the copying app still owns those bytes, so nothing is deleted for them.
 */
class ClipboardStore(
    private val storageFile: File?,
    var expiryMillis: Long = DEFAULT_EXPIRY_MILLIS,
    private val imagesDir: File? = null,
    /** When true, [items] lists pinned entries last instead of first. */
    var pinnedLast: Boolean = false,
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
                items.addAll(
                    snapshot.items.filter {
                        when (it.kind) {
                            ClipKind.IMAGE -> it.imagePath != null
                            ClipKind.FILE, ClipKind.FOLDER -> it.uriString != null
                            else -> true
                        }
                    }
                )
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
    fun add(text: String, sourceApp: String? = null, now: Long = System.currentTimeMillis()): ClipItem? =
        addTextual(text, html = null, sourceApp = sourceApp, now = now)

    /** Styled text: [html] is the markup, [text] the plain-text rendering. */
    @Synchronized
    fun addHtml(
        text: String,
        html: String,
        sourceApp: String? = null,
        now: Long = System.currentTimeMillis(),
    ): ClipItem? = addTextual(text, html = html, sourceApp = sourceApp, now = now)

    /**
     * Registers an image already copied to [imageFile] (inside [imagesDir]).
     * The store takes ownership of the file.
     */
    @Synchronized
    fun addImage(
        imageFile: File,
        mimeType: String,
        sourceApp: String? = null,
        now: Long = System.currentTimeMillis(),
    ): ClipItem? {
        if (!imageFile.exists()) return null
        val item = ClipItem(
            id = nextId++,
            text = "",
            timestamp = now,
            kind = ClipKind.IMAGE,
            imagePath = imageFile.absolutePath,
            mimeType = mimeType,
            sourceApp = sourceApp,
        )
        items.add(0, item)
        prune(now)
        return item.takeIf { items.contains(item) }
    }

    /**
     * Registers a copied file or folder by its source URI. Nothing is copied —
     * the clip is a reference, and inserting it later depends on the URI grant
     * still being valid.
     */
    @Synchronized
    fun addUri(
        uriString: String,
        displayName: String,
        mimeType: String,
        isDirectory: Boolean,
        size: Long = -1,
        sourceApp: String? = null,
        now: Long = System.currentTimeMillis(),
    ): ClipItem? {
        if (uriString.isBlank()) return null
        // Re-copying the same file moves it to the top instead of duplicating.
        items.firstOrNull { it.uriString == uriString }?.let { existing ->
            items.remove(existing)
            val refreshed = existing.copy(timestamp = now, sourceApp = sourceApp ?: existing.sourceApp)
            items.add(0, refreshed)
            return refreshed
        }
        val item = ClipItem(
            id = nextId++,
            text = displayName,
            timestamp = now,
            kind = if (isDirectory) ClipKind.FOLDER else ClipKind.FILE,
            mimeType = mimeType,
            uriString = uriString,
            fileName = displayName,
            fileSize = if (isDirectory) -1 else size,
            sourceApp = sourceApp,
        )
        items.add(0, item)
        prune(now)
        return item
    }

    private fun addTextual(text: String, html: String?, sourceApp: String?, now: Long): ClipItem? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val isLink = html == null && ClipLinks.asUrl(trimmed) != null
        // Re-copying an existing item moves it to the top instead of duplicating.
        val existing = items.firstOrNull { it.kind.isTextual && it.text == trimmed }
        if (existing != null) {
            items.remove(existing)
            val refreshed = existing.copy(
                timestamp = now,
                kind = when {
                    html != null -> ClipKind.HTML
                    isLink -> ClipKind.LINK
                    else -> existing.kind
                },
                htmlText = html ?: existing.htmlText,
                sourceApp = sourceApp ?: existing.sourceApp,
            )
            items.add(0, refreshed)
            return refreshed
        }
        val item = ClipItem(
            id = nextId++,
            text = trimmed,
            timestamp = now,
            kind = when {
                html != null -> ClipKind.HTML
                isLink -> ClipKind.LINK
                else -> ClipKind.TEXT
            },
            htmlText = html,
            mimeType = if (html != null) "text/html" else "text/plain",
            sourceApp = sourceApp,
        )
        items.add(0, item)
        prune(now)
        return item
    }

    @Synchronized
    fun items(now: Long = System.currentTimeMillis()): List<ClipItem> {
        prune(now)
        // Within each group (pinned / unpinned) newest-first; [pinnedLast] only
        // flips which group leads. compareBy puts pinned=false first (pinned
        // last); compareByDescending puts pinned=true first (pinned first).
        val byPin = if (pinnedLast) {
            compareBy<ClipItem> { it.pinned }
        } else {
            compareByDescending<ClipItem> { it.pinned }
        }
        return items.sortedWith(byPin.thenByDescending { it.timestamp })
    }

    /** Most recent textual clip, for snippet {clip} expansion. */
    @Synchronized
    fun latestText(now: Long = System.currentTimeMillis()): String? =
        items(now).firstOrNull { it.kind.isTextual }?.text

    /** Link clips still missing metadata, oldest-first in display order. */
    @Synchronized
    fun linksNeedingPreview(now: Long = System.currentTimeMillis()): List<ClipItem> =
        items(now).filter { it.kind == ClipKind.LINK && it.linkPreview == null }

    @Synchronized
    fun setLinkPreview(id: Long, preview: LinkPreview) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = items[index].copy(linkPreview = preview)
    }

    /** Drops every fetched preview, e.g. when the user turns previews off. */
    @Synchronized
    fun clearLinkPreviews() {
        for (index in items.indices) {
            if (items[index].linkPreview != null) items[index] = items[index].copy(linkPreview = null)
        }
    }

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
    fun search(query: String): List<ClipItem> = items().filter { it.matchesQuery(query) }

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
