package com.wasimaster.wmkeyboard.core.emoji

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Tracks recently and frequently used emojis, persisted as JSON on device.
 */
class EmojiUsage(private val storageFile: File?) {

    @Serializable
    private data class Snapshot(
        val recents: List<String> = emptyList(),
        val counts: Map<String, Int> = emptyMap(),
    )

    private val recents = ArrayDeque<String>()
    private val counts = HashMap<String, Int>()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val MAX_RECENTS = 32
    }

    init {
        storageFile?.takeIf { it.exists() }?.let { file ->
            runCatching {
                val snapshot = json.decodeFromString<Snapshot>(file.readText())
                recents.addAll(snapshot.recents.take(MAX_RECENTS))
                counts.putAll(snapshot.counts)
            }
        }
    }

    @Synchronized
    fun record(emoji: String) {
        recents.remove(emoji)
        recents.addFirst(emoji)
        while (recents.size > MAX_RECENTS) recents.removeLast()
        counts.merge(emoji, 1, Int::plus)
    }

    @Synchronized
    fun recents(limit: Int = MAX_RECENTS): List<String> = recents.take(limit)

    @Synchronized
    fun frequents(limit: Int = 16): List<String> =
        counts.entries.sortedByDescending { it.value }.take(limit).map { it.key }

    @Synchronized
    fun save() {
        val file = storageFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Snapshot(recents.toList(), counts)))
        }
    }

    @Synchronized
    fun clear() {
        recents.clear()
        counts.clear()
        storageFile?.delete()
    }
}
