package com.wasimaster.wmkeyboard.core.prediction

import java.io.InputStream

/**
 * Loads a bundled word list into a [Trie].
 *
 * Format: one entry per line, `word<space>frequency`. Lines starting with
 * `#` and malformed lines are skipped, so lists can carry comments and
 * survive hand editing.
 */
object DictionaryLoader {

    fun load(stream: InputStream): Trie {
        val trie = Trie()
        for ((word, frequency) in loadEntries(stream)) trie.insert(word, frequency)
        return trie
    }

    fun loadEntries(stream: InputStream): List<Pair<String, Int>> {
        val entries = ArrayList<Pair<String, Int>>()
        stream.bufferedReader().useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val separator = trimmed.lastIndexOf(' ')
                if (separator <= 0) {
                    entries.add(trimmed to 1)
                    continue
                }
                val word = trimmed.substring(0, separator).trim()
                val frequency = trimmed.substring(separator + 1).toIntOrNull() ?: 1
                entries.add(word to frequency)
            }
        }
        return entries
    }
}
