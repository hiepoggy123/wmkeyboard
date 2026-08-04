package com.wasimaster.wmkeyboard.core.prediction

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The user's personal vocabulary: words they have typed and the bigrams
 * between them, used for personalized completion and next-word prediction.
 *
 * Persisted as JSON in app-private storage. All typing data stays on
 * device; [clear] wipes it for the privacy setting.
 */
class UserLexicon(private val storageFile: File?) {

    @Serializable
    private data class Snapshot(
        val words: Map<String, Int> = emptyMap(),
        val bigrams: Map<String, Map<String, Int>> = emptyMap(),
        /** Save-session counter — the decay clock. Old files default to 0. */
        val generation: Long = 0L,
        /** Per-word last-touched generation, for lazy decay at compaction. */
        val wordGen: Map<String, Long> = emptyMap(),
    )

    /** A word's followers plus a lazily cached count-descending order, so the
     * per-strip-refresh nextWords read stops re-sorting on every call. */
    private class Followers {
        val counts = HashMap<String, Int>()
        var sorted: List<String>? = null

        fun bump(next: String) {
            counts.merge(next, 1, Int::plus)
            sorted = null
            if (counts.size > MAX_FOLLOWERS) {
                counts.remove(counts.minByOrNull { it.value }?.key)
            }
        }

        fun ordered(): List<String> = sorted ?: counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .also { sorted = it }

        fun total(): Int = counts.values.sum()
    }

    private var trie = Trie()
    private val words = HashMap<String, Int>()
    private val bigrams = HashMap<String, Followers>()
    private val wordGen = HashMap<String, Long>()
    private var generation = 0L
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Whether anything has been learned since the file last matched memory.
     *
     * [save] runs on the main thread every time the keyboard is dismissed, and
     * it serialises every word and bigram the user has ever typed. Most
     * dismissals have nothing new to write — the keyboard came up, the user
     * tapped a suggestion or typed nothing at all, and it went away again — so
     * the flag turns those into a return rather than a re-encode and a rewrite
     * of the whole file. It is deliberately not a "save later" scheme: when
     * there *is* something new it is still written synchronously, before the
     * process can be killed with the user's new words only in memory.
     */
    private var dirty = false

    /**
     * Bumped whenever the word set changes, so the engine's cached walk
     * results can key on it. Volatile: read lock-free on the suggestion
     * coroutine while learning happens on the main thread.
     */
    @Volatile
    private var mutations = 0L

    fun mutationCount(): Long = mutations

    init {
        load()
    }

    /**
     * [count] grades the strength of the signal: a suggestion the user
     * deliberately tapped teaches harder than a word that merely got
     * committed in passing.
     */
    @Synchronized
    fun learnWord(word: String, count: Int = 1) {
        val key = word.lowercase()
        if (key.length < 2 || key.length > MAX_WORD_LENGTH || count <= 0) return
        val before = words[key] ?: 0
        val merged = (before.toLong() + count).coerceAtMost(MAX_COUNT.toLong()).toInt()
        words[key] = merged
        // Reinforce by the clamped delta so the trie's copy can never race
        // past the cap (or overflow) while the word map stays clamped.
        trie.reinforce(key, merged - before)
        wordGen[key] = generation
        mutations++
        dirty = true
    }

    /**
     * User-added dictionary entry: weighted like a word typed [boost]
     * times so it competes with genuinely frequent words immediately and
     * is never "corrected" away.
     */
    @Synchronized
    fun addWord(word: String, boost: Int = 200) {
        val key = word.trim().lowercase()
        if (key.isEmpty() || key.length > MAX_WORD_LENGTH) return
        val before = words[key] ?: 0
        val merged = (before.toLong() + boost).coerceAtMost(MAX_COUNT.toLong()).toInt()
        words[key] = merged
        trie.reinforce(key, merged - before)
        wordGen[key] = generation
        mutations++
        dirty = true
    }

    /**
     * Re-reads the storage file. The settings app edits the file directly
     * (personal dictionary screen); the IME calls this when signalled so
     * its in-memory copy doesn't clobber those edits on the next save.
     */
    @Synchronized
    fun reload() {
        words.clear()
        bigrams.clear()
        wordGen.clear()
        rebuildTrie()
        load()
        mutations++
        // Memory is the file again, so there is nothing outstanding to write —
        // and writing would clobber the settings-app edit this reload exists
        // to pick up.
        dirty = false
    }

    @Synchronized
    fun learnBigram(previous: String, next: String) {
        val prev = previous.lowercase()
        val nxt = next.lowercase()
        if (prev.isEmpty() || nxt.isEmpty()) return
        if (prev.length > MAX_WORD_LENGTH || nxt.length > MAX_WORD_LENGTH) return
        bigrams.getOrPut(prev) { Followers() }.bump(nxt)
        dirty = true
    }

    @Synchronized
    fun nextWords(previous: String, limit: Int): List<String> =
        bigrams[previous]?.ordered()?.take(limit).orEmpty()

    /** Learned count of the pair (previous -> next), 0 when never seen. */
    @Synchronized
    fun bigramCount(previous: String, next: String): Int =
        bigrams[previous.lowercase()]?.counts?.get(next.lowercase()) ?: 0

    /** Defensive copy of a word's follower counts (capped at MAX_FOLLOWERS). */
    @Synchronized
    fun followerCounts(previous: String): Map<String, Int> =
        bigrams[previous.lowercase()]?.counts?.let(::HashMap) ?: emptyMap()

    @Synchronized
    fun complete(prefix: String, limit: Int): List<Suggestion> = trie.complete(prefix, limit)

    /**
     * Walkers over the learned-word trie for the fuzzy beam search. The walker
     * escapes this monitor, so a walk concurrent with learning has the same
     * (pre-existing, benign) staleness hazard as [complete] — callers
     * invalidate their caches on lexicon change.
     */
    @Synchronized
    fun walkers(): List<TrieWalker> = trie.walkers()

    @Synchronized
    fun contains(word: String): Boolean = trie.contains(word)

    @Synchronized
    fun frequencyOf(word: String): Int = trie.frequencyOf(word)

    /** Snapshot of all learned words with their counts. */
    @Synchronized
    fun allWords(): List<Pair<String, Int>> = words.toList()

    @Synchronized
    fun forget(word: String) {
        val key = word.lowercase()
        words.remove(key)
        wordGen.remove(key)
        bigrams.remove(key)
        bigrams.values.forEach {
            if (it.counts.remove(key) != null) it.sorted = null
        }
        rebuildTrie()
        mutations++
        dirty = true
    }

    @Synchronized
    fun clear() {
        words.clear()
        bigrams.clear()
        wordGen.clear()
        rebuildTrie()
        mutations++
        // The delete is the write, so there is normally nothing left to save.
        // If it failed, the file still holds the data this call was meant to
        // wipe — stay dirty so the next save overwrites it with the empty
        // snapshot, which is what the old unconditional save did.
        dirty = storageFile?.delete() == false
    }

    @Synchronized
    fun save() {
        val file = storageFile ?: return
        if (!dirty) return
        generation++
        compactIfNeeded()
        val snapshot = Snapshot(
            words = words,
            bigrams = bigrams.mapValues { it.value.counts.toMap() },
            generation = generation,
            wordGen = wordGen,
        )
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(snapshot))
        // Only on success: a write that failed leaves the file behind memory,
        // and the next dismissal should try again rather than assume it landed.
        }.onSuccess { dirty = false }
    }

    private fun load() {
        val file = storageFile ?: return
        if (!file.exists()) return
        runCatching {
            val snapshot = json.decodeFromString<Snapshot>(file.readText())
            words.putAll(snapshot.words)
            snapshot.bigrams.forEach { (prev, map) ->
                bigrams[prev] = Followers().also { it.counts.putAll(map) }
            }
            generation = snapshot.generation
            // Words with no recorded generation (legacy files, settings-app
            // rewrites) are treated as fresh rather than instantly decayed;
            // orphaned entries for words no longer present are dropped.
            for (word in words.keys) {
                wordGen[word] = snapshot.wordGen[word] ?: snapshot.generation
            }
            rebuildTrie()
        }
    }

    /** Trie is append-only, so removal rebuilds it from the word map. */
    private fun rebuildTrie() {
        val fresh = Trie()
        for ((word, count) in words) fresh.insert(word, count)
        trie = fresh
    }

    /**
     * Bounds the store, running only on a dirty save (dismissal-time, main
     * thread — the same moment that already rewrites the whole file). Words
     * are scored with lazy exponential decay — `count * 2^(-age/HALF_LIFE)`
     * with age in save-generations — so a year-old typo-learn finally loses
     * to anything the user still types, while raw counts are never rewritten
     * on the hot path. Deliberately user-added words ([addWord], count >=
     * STICKY_MIN_COUNT) are evicted only after every organic word is gone.
     */
    private fun compactIfNeeded() {
        if (words.size > MAX_WORDS) {
            fun score(word: String): Double {
                val age = (generation - (wordGen[word] ?: generation)).coerceAtLeast(0L)
                val halfLives = age.toDouble() / HALF_LIFE_GENERATIONS
                return (words[word] ?: 0) * Math.pow(2.0, -halfLives)
            }
            val evictable = words.keys
                .sortedWith(
                    compareBy(
                        { (words[it] ?: 0) >= STICKY_MIN_COUNT },
                        { score(it) },
                    )
                )
            val toEvict = evictable.take(words.size - EVICT_TO)
            for (word in toEvict) {
                words.remove(word)
                wordGen.remove(word)
                bigrams.remove(word)
                bigrams.values.forEach {
                    if (it.counts.remove(word) != null) it.sorted = null
                }
            }
            rebuildTrie()
            mutations++
        }
        if (bigrams.size > MAX_BIGRAM_PREVS) {
            val evictable = bigrams.entries.sortedBy { it.value.total() }
            for (entry in evictable.take(bigrams.size - MAX_BIGRAM_PREVS)) {
                bigrams.remove(entry.key)
            }
        }
    }

    private companion object {
        const val MAX_WORD_LENGTH = 32
        /** 1e6 x USER_WORD_WEIGHT(500) stays far inside Int range. */
        const val MAX_COUNT = 1_000_000
        const val MAX_WORDS = 10_000
        /** Eviction target below the cap: 10% hysteresis so compaction does
         * not churn on every save once the cap is reached. */
        const val EVICT_TO = 9_000
        const val MAX_BIGRAM_PREVS = 5_000
        const val MAX_FOLLOWERS = 32
        const val HALF_LIFE_GENERATIONS = 64.0
        /** addWord's default boost lands at 200; organic words rarely reach
         * this, so it doubles as the "deliberately added" marker. */
        const val STICKY_MIN_COUNT = 100
    }
}
