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
    }

    @Synchronized
    fun learnBigram(previous: String, next: String) {
        val prev = previous.lowercase()
        val nxt = next.lowercase()
        if (prev.isEmpty() || nxt.isEmpty()) return
        bigrams.getOrPut(prev) { HashMap() }.merge(nxt, 1, Int::plus)
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
    }

    @Synchronized
    fun clear() {
        words.clear()
        bigrams.clear()
        rebuildTrie()
        storageFile?.delete()
    }

    @Synchronized
    fun save() {
        val file = storageFile ?: return
        val snapshot = Snapshot(
            words = words,
            bigrams = bigrams.mapValues { it.value.toMap() },
        )
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(snapshot))
        }
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
