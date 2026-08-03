package com.wasimaster.wmkeyboard.core.tools

/**
 * Memory of each provider's category list.
 *
 * A taxonomy is not a feed: it barely moves between sessions, so the panel
 * asks once and then browses for free. Empty answers are cached too, which
 * matters more than the hits do — a provider with no categories endpoint is
 * then asked once a day instead of once per panel open.
 *
 * `nowMs` is passed in rather than read, so expiry is testable without
 * sleeping. Modelled on [PhotoCache].
 */
object MediaCategoryCache {

    private const val TTL_MS = 12 * 60 * 60_000L

    /** Two providers times GIFs and stickers, with room to spare. */
    private const val MAX_ENTRIES = 8

    private class Entry(val categories: List<MediaCategory>, val storedAtMs: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(source: GifSource, sticker: Boolean, nowMs: Long): List<MediaCategory>? {
        val key = keyOf(source, sticker)
        val entry = entries[key] ?: return null
        if (nowMs - entry.storedAtMs >= TTL_MS) {
            entries.remove(key)
            return null
        }
        return entry.categories
    }

    @Synchronized
    fun put(source: GifSource, sticker: Boolean, categories: List<MediaCategory>, nowMs: Long) {
        entries[keyOf(source, sticker)] = Entry(categories, nowMs)
    }

    /** Call when a key changes: results fetched with the old key are not ours. */
    @Synchronized
    fun clear() = entries.clear()

    /** GIFs and stickers have separate taxonomies, so the flag is in the key. */
    internal fun keyOf(source: GifSource, sticker: Boolean): String = "${source.name}|$sticker"
}
