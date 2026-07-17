package com.wasimaster.wmkeyboard.core.input.composer

/**
 * A reading→candidates table for a conversion IME (Pinyin→Hanzi, kana→Kanji),
 * loaded once from a shipped TSV asset. Each line is `reading<TAB>word<TAB>freq`;
 * a reading may repeat across lines, and its candidates are returned most-frequent
 * first.
 *
 * The table is deliberately small and additive: it makes Chinese and Japanese
 * *typeable* (type a reading, pick a character) without an ML model or a
 * multi-megabyte lexicon. An unknown reading simply yields no candidates, and the
 * composer falls back to committing the raw reading (pinyin letters, or kana).
 */
class ConversionDictionary private constructor(
    private val table: Map<String, List<String>>,
) {

    /** Candidates for [reading] exactly, best first; empty when unknown. */
    fun exact(reading: String): List<String> = table[reading].orEmpty()

    /**
     * Candidates for a buffer being built up: the exact match first, then matches
     * for its longest key-prefixes, so `nihao` offers 你好 while `ni` already
     * offers 你/尼/…. De-duplicated and capped at [limit].
     */
    fun candidates(reading: String, limit: Int = 12): List<String> {
        if (reading.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        out.addAll(exact(reading))
        var p = reading
        while (p.isNotEmpty() && out.size < limit) {
            if (p != reading) table[p]?.let(out::addAll)
            p = p.dropLast(1)
        }
        return out.take(limit)
    }

    val isEmpty: Boolean get() = table.isEmpty()

    companion object {
        val EMPTY = ConversionDictionary(emptyMap())

        /**
         * Parses TSV lines into a table, keeping each reading's candidates ordered
         * by the frequency column (descending). Malformed lines are skipped.
         */
        fun parse(lines: Sequence<String>): ConversionDictionary {
            val acc = HashMap<String, MutableList<Pair<String, Int>>>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split('\t')
                if (parts.size < 2) continue
                val reading = parts[0].trim()
                val word = parts[1].trim()
                if (reading.isEmpty() || word.isEmpty()) continue
                val freq = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                acc.getOrPut(reading) { mutableListOf() }.add(word to freq)
            }
            val table = acc.mapValues { (_, v) ->
                v.sortedByDescending { it.second }.map { it.first }.distinct()
            }
            return ConversionDictionary(table)
        }
    }
}
