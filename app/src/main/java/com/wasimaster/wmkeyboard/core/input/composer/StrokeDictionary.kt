package com.wasimaster.wmkeyboard.core.input.composer

/**
 * A stroke-sequence → Hanzi table for the 笔画 (stroke) input method. Every Han
 * character has a canonical stroke order expressed in the five stroke classes,
 * encoded here as the digits 1–5 (一丨丿丶乙 → héng/shù/piě/diǎn/zhé). Typing a
 * stroke prefix filters to the characters whose sequence starts with it,
 * frequency-ranked.
 *
 * Entries are kept sorted by stroke code so a prefix query is a binary-searched
 * contiguous range rather than a full scan — the table can hold tens of
 * thousands of characters. A `.` in the query is a wildcard (any single stroke);
 * a query containing one falls back to a linear pattern scan.
 *
 * Loaded from a downloadable pack (Unihan/stroke-order derived); absent, the
 * composer simply offers no candidates. Line format: `strokeCode<TAB>hanzi<TAB>freq`.
 */
class StrokeDictionary private constructor(
    private val codes: Array<String>,
    private val words: Array<String>,
    private val freqs: IntArray,
) {

    val isEmpty: Boolean get() = codes.isEmpty()

    /** Characters whose stroke sequence starts with [pattern], best (most frequent) first. */
    fun candidates(pattern: String, limit: Int = 24): List<String> {
        if (pattern.isEmpty() || codes.isEmpty()) return emptyList()
        val hits = if ('.' in pattern) wildcardHits(pattern) else prefixHits(pattern)
        return hits
            .sortedByDescending { freqs[it] }
            .map { words[it] }
            .distinct()
            .take(limit)
    }

    /** Indices whose code starts with [prefix], via the sorted range [lo, hi). */
    private fun prefixHits(prefix: String): List<Int> {
        val lo = lowerBound(prefix)
        val hi = lowerBound(prefix + '￿')
        return (lo until hi).toList()
    }

    /** Indices matching [pattern] where `.` is any stroke; linear (wildcards are rare). */
    private fun wildcardHits(pattern: String): List<Int> {
        val out = ArrayList<Int>()
        for (i in codes.indices) if (matches(codes[i], pattern)) out.add(i)
        return out
    }

    private fun matches(code: String, pattern: String): Boolean {
        if (code.length < pattern.length) return false
        for (j in pattern.indices) {
            val p = pattern[j]
            if (p != '.' && p != code[j]) return false
        }
        return true
    }

    /** First index whose code is >= [key] (binary search over the sorted codes). */
    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = codes.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (codes[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        val EMPTY = StrokeDictionary(emptyArray(), emptyArray(), IntArray(0))

        /** Parses `strokeCode<TAB>hanzi<TAB>freq` lines into a sorted table. */
        fun parse(lines: Sequence<String>): StrokeDictionary {
            data class Row(val code: String, val word: String, val freq: Int)
            val rows = ArrayList<Row>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split('\t')
                if (parts.size < 2) continue
                val code = parts[0].trim()
                val word = parts[1].trim()
                if (code.isEmpty() || word.isEmpty() || code.any { it !in '1'..'5' }) continue
                rows.add(Row(code, word, parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0))
            }
            rows.sortBy { it.code }
            return StrokeDictionary(
                Array(rows.size) { rows[it].code },
                Array(rows.size) { rows[it].word },
                IntArray(rows.size) { rows[it].freq },
            )
        }
    }
}
