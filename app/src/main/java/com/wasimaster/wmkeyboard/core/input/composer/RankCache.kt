package com.wasimaster.wmkeyboard.core.input.composer

/**
 * A one-entry cache of a composer's last ranking.
 *
 * The service asks for the same buffer's candidates more than once per commit —
 * `candidates()` fills the strip, then `consumedFor()` re-ranks the identical
 * buffer to find out how much of it to delete, and `flushConversion` does both
 * again for every word it commits. That was wasted work before the lattice and
 * would be expensive after it, so one slot is enough to remove it: the repeat
 * calls always come back-to-back on the same buffer.
 *
 * Keyed on [CjkDictionaries.generation] as well as the buffer, so a pack finishing
 * downloading, a settings toggle or a learned pick drops the entry rather than
 * serving an answer from tables that no longer exist.
 */
class RankCache<T> {

    private class Entry<T>(val buffer: String, val limit: Int, val generation: Int, val value: T)

    @Volatile
    private var entry: Entry<T>? = null

    fun get(buffer: String, limit: Int, compute: () -> T): T {
        val hit = entry
        val generation = CjkDictionaries.generation
        if (hit != null && hit.buffer == buffer && hit.limit == limit && hit.generation == generation) {
            return hit.value
        }
        val value = compute()
        entry = Entry(buffer, limit, generation, value)
        return value
    }
}
