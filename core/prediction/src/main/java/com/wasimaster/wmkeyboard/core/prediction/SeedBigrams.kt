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
    private val map: Map<String, List<Pair<String, Int>>>,
) {

    /** Continuations of [previous], best first. */
    fun nextWords(previous: String): List<String> =
        map[previous]?.map { it.first }.orEmpty()

    /** Whether the bundled pairs know [next] as a follower of [previous].
     * Lists are short (a handful of seeds per word), so the scan is cheap. */
    fun follows(previous: String, next: String): Boolean =
        map[previous]?.any { it.first == next } == true

    /** The bundled count of the pair, 0 when unknown. The list carried these
     * all along; the context reranker is what finally reads them. */
    fun count(previous: String, next: String): Int =
        map[previous]?.firstOrNull { it.first == next }?.second ?: 0

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
                pairs.mapValues { (_, list) -> list.sortedByDescending { it.second } }
            )
        }
    }
}
