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
    )

    private var trie = Trie()
    private val words = HashMap<String, Int>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
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
        if (key.length < 2 || count <= 0) return
        words.merge(key, count, Int::plus)
        trie.reinforce(key, count)
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
        if (key.isEmpty()) return
        words.merge(key, boost, Int::plus)
        trie.reinforce(key, boost)
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
        rebuildTrie()
        load()
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
        bigrams.getOrPut(prev) { HashMap() }.merge(nxt, 1, Int::plus)
        dirty = true
    }

    @Synchronized
    fun nextWords(previous: String, limit: Int): List<String> =
        bigrams[previous]
            ?.entries
            ?.sortedByDescending { it.value }
            ?.take(limit)
            ?.map { it.key }
            .orEmpty()

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
        bigrams.remove(key)
        bigrams.values.forEach { it.remove(key) }
        rebuildTrie()
        dirty = true
    }

    @Synchronized
    fun clear() {
        words.clear()
        bigrams.clear()
        rebuildTrie()
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
        val snapshot = Snapshot(
            words = words,
            bigrams = bigrams.mapValues { it.value.toMap() },
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
                bigrams[prev] = HashMap(map)
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
}
