package com.wasimaster.wmkeyboard.core.prediction

import java.io.InputStream

/**
 * Bundled next-word pairs so prediction works from the first keystroke.
 * The user's own bigrams ([UserLexicon]) always rank ahead of these; the
 * seed list only fills the strip until enough personal history exists.
 *
 * Format: one `previous next frequency` triple per line, `#` comments and
 * malformed lines skipped.
 */
class SeedBigrams private constructor(
    private val map: Map<String, List<String>>,
) {

    /** Continuations of [previous], best first. */
    fun nextWords(previous: String): List<String> = map[previous].orEmpty()

    companion object {
        val EMPTY = SeedBigrams(emptyMap())

        fun load(stream: InputStream): SeedBigrams {
            val pairs = HashMap<String, ArrayList<Pair<String, Int>>>()
            stream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size < 2) continue
                    val frequency = parts.getOrNull(2)?.toIntOrNull() ?: 1
                    pairs.getOrPut(parts[0].lowercase()) { ArrayList() }
                        .add(parts[1] to frequency)
                }
            }
            return SeedBigrams(
                pairs.mapValues { (_, list) ->
                    list.sortedByDescending { it.second }.map { it.first }
                }
            )
        }
    }
}
