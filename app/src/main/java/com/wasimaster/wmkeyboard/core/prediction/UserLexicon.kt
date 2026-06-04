package com.wasimaster.wmkeyboard.core.prediction

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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

    @Synchronized
    fun learnWord(word: String) {
        val key = word.lowercase()
        if (key.length < 2) return
        words.merge(key, 1, Int::plus)
        trie.reinforce(key)
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
