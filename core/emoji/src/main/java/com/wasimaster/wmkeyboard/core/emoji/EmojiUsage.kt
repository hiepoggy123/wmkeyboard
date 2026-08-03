package com.wasimaster.wmkeyboard.core.emoji

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Tracks recently and frequently used emojis, user favourites, and the
 * preferred skin-tone/gender variant per emoji — persisted as JSON on
 * device.
 *
 * Favourites are pinned: [recents] and [frequents] always list them first
 * (in the order they were favourited), whether or not they were ever used.
 */
class EmojiUsage(private val storageFile: File?) {

    @Serializable
    private data class Snapshot(
        val recents: List<String> = emptyList(),
        val counts: Map<String, Int> = emptyMap(),
        val favourites: List<String> = emptyList(),
        /** Palette-grid base emoji → the variant the user last picked for it. */
        val variantPrefs: Map<String, String> = emptyMap(),
    )

    private val recents = ArrayDeque<String>()
    private val counts = HashMap<String, Int>()
    private val favourites = ArrayList<String>()
    private val variantPrefs = HashMap<String, String>()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val MAX_RECENTS = 32
        private const val MAX_FAVOURITES = 64
    }

    /**
     * Whether anything has been used, pinned or reordered since the file last
     * matched memory. [save] runs on the main thread on every keyboard
     * dismissal; without this it re-encoded and rewrote the whole history even
     * when no emoji had been touched, which is nearly every dismissal. See
     * [com.wasimaster.wmkeyboard.core.prediction.UserLexicon] for the same flag
     * and the reason the write itself stays synchronous.
     */
    private var dirty = false

    init {
        load()
    }

    /**
     * Re-reads the file, dropping whatever is held in memory. For when the
     * settings app has rewritten it — a restored backup, say — and this copy
     * would otherwise save over it at the end of the next field.
     */
    @Synchronized
    fun reload() {
        recents.clear()
        counts.clear()
        favourites.clear()
        variantPrefs.clear()
        load()
        // Memory matches the file again, and saving would undo the rewrite
        // this reload exists to pick up.
        dirty = false
    }

    private fun load() {
        storageFile?.takeIf { it.exists() }?.let { file ->
            runCatching {
                val snapshot = json.decodeFromString<Snapshot>(file.readText())
                recents.addAll(snapshot.recents.take(MAX_RECENTS))
                counts.putAll(snapshot.counts)
                favourites.addAll(snapshot.favourites.distinct().take(MAX_FAVOURITES))
                variantPrefs.putAll(snapshot.variantPrefs)
            }
        }
    }

    @Synchronized
    fun record(emoji: String) {
        recents.remove(emoji)
        recents.addFirst(emoji)
        while (recents.size > MAX_RECENTS) recents.removeLast()
        counts.merge(emoji, 1, Int::plus)
        dirty = true
    }

    /** Favourites first, then the most recently used non-favourites. */
    @Synchronized
    fun recents(limit: Int = MAX_RECENTS): List<String> =
        pinned(recents.asSequence(), limit)

    /** Favourites first, then the most frequently used non-favourites. */
    @Synchronized
    fun frequents(limit: Int = MAX_RECENTS): List<String> =
        pinned(
            counts.entries.asSequence()
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key },
            limit,
        )

    private fun pinned(used: Sequence<String>, limit: Int): List<String> =
        (favourites.asSequence() + used.filter { it !in favourites })
            .distinct()
            .take(limit)
            .toList()

    @Synchronized
    fun favourites(): List<String> = favourites.toList()

    @Synchronized
    fun isFavourite(emoji: String): Boolean = emoji in favourites

    /** Toggles favourite status; returns true when [emoji] is now a favourite. */
    @Synchronized
    fun toggleFavourite(emoji: String): Boolean {
        dirty = true
        return if (favourites.remove(emoji)) {
            false
        } else {
            favourites.add(emoji)
            while (favourites.size > MAX_FAVOURITES) favourites.removeAt(0)
            true
        }
    }

    /**
     * Rewrites the favourites order to [order]. Only current favourites
     * survive (deduped); any favourite absent from [order] is appended at the
     * tail, so a stale or partial order can never silently drop a pinned
     * emoji.
     */
    @Synchronized
    fun reorderFavourites(order: List<String>) {
        val current = favourites.toHashSet()
        val next = order.asSequence().filter { it in current }.distinct().toMutableList()
        for (fav in favourites) if (fav !in next) next.add(fav)
        favourites.clear()
        favourites.addAll(next)
        dirty = true
    }

    @Synchronized
    fun preferredVariant(base: String): String? = variantPrefs[base]

    /** Remembers the user's variant pick; passing the base itself resets it. */
    @Synchronized
    fun setPreferredVariant(base: String, variant: String) {
        if (variant == base) variantPrefs.remove(base) else variantPrefs[base] = variant
        dirty = true
    }

    @Synchronized
    fun variantPrefs(): Map<String, String> = HashMap(variantPrefs)

    @Synchronized
    fun save() {
        val file = storageFile ?: return
        if (!dirty) return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(
                json.encodeToString(
                    Snapshot(recents.toList(), counts, favourites.toList(), variantPrefs)
                )
            )
        // Only on success, so a failed write is retried at the next dismissal.
        }.onSuccess { dirty = false }
    }

    /** Clears only the recents list, keeping counts, favourites and prefs. */
    @Synchronized
    fun clearRecents() {
        recents.clear()
        dirty = true
    }

    /**
     * Drops [emoji] from history entirely — recents, usage counts, and (if
     * pinned) favourites, since favourites head every history listing and
     * would keep the emoji visible after a "remove". Variant prefs stay.
     */
    @Synchronized
    fun removeFromHistory(emoji: String) {
        recents.remove(emoji)
        counts.remove(emoji)
        favourites.remove(emoji)
        dirty = true
    }

    @Synchronized
    fun clear() {
        recents.clear()
        counts.clear()
        favourites.clear()
        variantPrefs.clear()
        // The delete is the write; a later save must not recreate the file.
        // A delete that FAILED leaves the wiped history on disk, so stay dirty
        // and let the next save overwrite it with the empty snapshot.
        dirty = storageFile?.delete() == false
    }
}
