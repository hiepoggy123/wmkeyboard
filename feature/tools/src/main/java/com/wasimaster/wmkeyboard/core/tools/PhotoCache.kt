package com.wasimaster.wmkeyboard.core.tools

/**
 * Short-lived memory of pages already fetched.
 *
 * Against a 50-an-hour budget the cache is not a performance nicety: without
 * it, scrolling back up a grid, or leaving a photo's page and returning, costs
 * requests the user will want later. Only search pages are held — thumbnails
 * are Coil's problem, and it has a disk cache for them.
 *
 * `nowMs` is passed in rather than read, so expiry is testable without sleeping.
 */
object PhotoCache {

    /** Search results reorder server-side, so they go stale reasonably fast. */
    private const val SEARCH_TTL_MS = 15 * 60_000L

    /** A curated feed barely moves within a session. */
    private const val FEED_TTL_MS = 60 * 60_000L

    /** About 1500 results at the default page size; a few hundred KB. */
    private const val MAX_ENTRIES = 64

    private class Entry(val page: PhotoPage, val storedAtMs: Long, val ttlMs: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(source: PhotoSource, query: PhotoQuery, nowMs: Long): PhotoPage? {
        val key = keyOf(source, query)
        val entry = entries[key] ?: return null
        if (nowMs - entry.storedAtMs >= entry.ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.page
    }

    @Synchronized
    fun put(source: PhotoSource, query: PhotoQuery, page: PhotoPage, nowMs: Long) {
        val ttl = if (query.text.isBlank()) FEED_TTL_MS else SEARCH_TTL_MS
        entries[keyOf(source, query)] = Entry(page, nowMs, ttl)
    }

    /** Call when a key changes: results fetched with the old key are not ours. */
    @Synchronized
    fun clear() = entries.clear()

    /**
     * Case and surrounding spaces are normalised away. Without that, "Cats",
     * "cats" and "cats " are three separate requests out of fifty, and a user
     * who backspaces one character and retypes it pays twice.
     */
    internal fun keyOf(source: PhotoSource, query: PhotoQuery): String = listOf(
        source.name,
        query.text.trim().lowercase(),
        query.topicId,
        query.page.toString(),
        query.perPage.toString(),
        query.orientation.name,
        query.color.name,
        query.safe.toString(),
    ).joinToString("|")
}
