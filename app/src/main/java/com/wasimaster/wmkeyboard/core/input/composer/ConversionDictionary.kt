package com.wasimaster.wmkeyboard.core.input.composer

/**
 * A reading→candidates table for a conversion IME (Pinyin→Hanzi, kana→Kanji),
 * loaded once from a shipped TSV asset or a downloaded pack. Each line is
 * `reading<TAB>word<TAB>freq`; a reading may repeat across lines, and its
 * candidates are returned most-frequent first.
 *
 * The table makes Chinese and Japanese *typeable* (type a reading, pick a
 * character) without an ML model. An unknown reading simply yields no
 * candidates, and the composer falls back to committing the raw reading (pinyin
 * letters, or kana).
 *
 * ### Why the rows are stored column-wise
 *
 * The packs are big: `ja_kana` is 1.08M rows over 744k distinct readings. Held
 * the obvious way — `Map<String, List<String>>` — the *text* is only ~20 MB but
 * the objects wrapped around it are not: two `String` headers per row, a map
 * node and a list per reading runs past 160 MB, and on a 256 MB heap the parse
 * dies with an `OutOfMemoryError` that a `runCatching` upstream quietly turns
 * into "no dictionary". So rows live in parallel primitive arrays indexed by row
 * number, with every reading and word concatenated into a [CharStore]: ~33 MB
 * for that same pack, no per-row objects, and `String`s are cut only for the
 * handful of candidates a query actually returns.
 *
 * Rows are kept sorted by reading, so a lookup is a binary search for the first
 * row with that reading followed by a walk over its (contiguous) run.
 */
class ConversionDictionary private constructor(
    private val readings: CharStore,
    private val readingAt: IntArray,
    private val readingLen: IntArray,
    private val words: CharStore,
    private val wordAt: IntArray,
    private val wordLen: IntArray,
    private val freqs: IntArray,
) {

    private val size: Int get() = freqs.size

    val isEmpty: Boolean get() = size == 0

    /** Candidates for [reading] exactly, best first; empty when unknown. */
    fun exact(reading: String): List<String> {
        if (reading.isEmpty() || size == 0) return emptyList()
        var i = lowerBound(reading)
        if (i >= size || !readingEquals(i, reading)) return emptyList()
        // One reading's run is short (a few dozen rows at most), so collecting it
        // to sort by frequency costs nothing measurable.
        val run = ArrayList<Int>(4)
        while (i < size && readingEquals(i, reading)) {
            run.add(i)
            i++
        }
        return run
            .sortedByDescending { freqs[it] }
            .map { words.substring(wordAt[it], wordLen[it]) }
            .distinct()
    }

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
            if (p != reading) out.addAll(exact(p))
            p = p.dropLast(1)
        }
        return out.take(limit)
    }

    /** First row whose reading is >= [key]. */
    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (readings.compare(readingAt[mid], readingLen[mid], key) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun readingEquals(row: Int, key: String): Boolean =
        readingLen[row] == key.length && readings.compare(readingAt[row], readingLen[row], key) == 0

    /**
     * Append-only character arena. Text goes into fixed-size chunks so growing it
     * never reallocates what is already stored — the point on a table this size,
     * where a doubling `StringBuilder` would spike to twice the finished table's
     * heap at exactly the wrong moment.
     */
    class CharStore {
        private val chunks = ArrayList<CharArray>()
        private var used = CHUNK // forces the first append to allocate a chunk

        /** Total chars stored; also the position the next [append] returns. */
        var length: Int = 0
            private set

        fun append(s: String): Int {
            val start = length
            var off = 0
            while (off < s.length) {
                if (used == CHUNK) {
                    chunks.add(CharArray(CHUNK))
                    used = 0
                }
                val n = minOf(CHUNK - used, s.length - off)
                s.toCharArray(chunks[chunks.size - 1], used, off, off + n)
                used += n
                off += n
            }
            length += s.length
            return start
        }

        fun charAt(pos: Int): Char = chunks[pos ushr CHUNK_BITS][pos and CHUNK_MASK]

        /** Lexicographic compare of the stored run at [start] of [len] against [key]. */
        fun compare(start: Int, len: Int, key: String): Int {
            val common = minOf(len, key.length)
            for (i in 0 until common) {
                val d = charAt(start + i) - key[i]
                if (d != 0) return d
            }
            return len - key.length
        }

        /** Lexicographic compare of two stored runs. */
        fun compare(aAt: Int, aLen: Int, bAt: Int, bLen: Int): Int {
            val common = minOf(aLen, bLen)
            for (i in 0 until common) {
                val d = charAt(aAt + i) - charAt(bAt + i)
                if (d != 0) return d
            }
            return aLen - bLen
        }

        fun substring(start: Int, len: Int): String {
            val out = CharArray(len)
            for (i in 0 until len) out[i] = charAt(start + i)
            return String(out)
        }

        private companion object {
            const val CHUNK_BITS = 20
            const val CHUNK = 1 shl CHUNK_BITS // 1M chars = 2 MB per chunk
            const val CHUNK_MASK = CHUNK - 1
        }
    }

    companion object {
        val EMPTY = ConversionDictionary(
            CharStore(), IntArray(0), IntArray(0), CharStore(), IntArray(0), IntArray(0), IntArray(0),
        )

        /**
         * Parses TSV lines into a table, keeping each reading's candidates ordered
         * by the frequency column (descending). Malformed lines are skipped, and
         * an input with no usable row yields [EMPTY].
         *
         * Streams straight into the compact layout — nothing per row is retained
         * beyond the arrays themselves. The packs already arrive sorted by reading,
         * so the sort below only does real work for a pack (or a test) that isn't.
         */
        fun parse(lines: Sequence<String>): ConversionDictionary {
            val readings = CharStore()
            val words = CharStore()
            var rAt = IntArray(1024)
            var rLen = IntArray(1024)
            var wAt = IntArray(1024)
            var wLen = IntArray(1024)
            var freq = IntArray(1024)
            var n = 0
            var sorted = true
            var prev: String? = null

            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val reading = line.substring(0, tab).trim()
                val rest = line.substring(tab + 1)
                val tab2 = rest.indexOf('\t')
                val word = (if (tab2 < 0) rest else rest.substring(0, tab2)).trim()
                if (reading.isEmpty() || word.isEmpty()) continue
                val f = if (tab2 < 0) 0 else rest.substring(tab2 + 1).trim().toIntOrNull() ?: 0

                if (n == freq.size) {
                    val cap = n * 2
                    rAt = rAt.copyOf(cap)
                    rLen = rLen.copyOf(cap)
                    wAt = wAt.copyOf(cap)
                    wLen = wLen.copyOf(cap)
                    freq = freq.copyOf(cap)
                }
                val last = prev
                if (sorted && last != null && reading < last) sorted = false
                prev = reading
                rAt[n] = readings.append(reading)
                rLen[n] = reading.length
                wAt[n] = words.append(word)
                wLen[n] = word.length
                freq[n] = f
                n++
            }
            if (n == 0) return EMPTY

            val order = IntArray(n) { it }
            if (!sorted) sortRows(order, n, readings, rAt, rLen)

            val outRAt = IntArray(n)
            val outRLen = IntArray(n)
            val outWAt = IntArray(n)
            val outWLen = IntArray(n)
            val outFreq = IntArray(n)
            for (i in 0 until n) {
                val src = order[i]
                outRAt[i] = rAt[src]
                outRLen[i] = rLen[src]
                outWAt[i] = wAt[src]
                outWLen[i] = wLen[src]
                outFreq[i] = freq[src]
            }
            return ConversionDictionary(readings, outRAt, outRLen, words, outWAt, outWLen, outFreq)
        }

        /**
         * Sorts [order] (row indices) by reading. Hand-rolled because the library
         * sorts want either boxed `Integer`s — a million of them here — or a
         * comparator over per-row objects this layout deliberately doesn't have.
         */
        private fun sortRows(order: IntArray, n: Int, store: CharStore, at: IntArray, len: IntArray) {
            fun cmp(a: Int, b: Int): Int = store.compare(at[a], len[a], at[b], len[b])

            fun swap(i: Int, j: Int) {
                val t = order[i]
                order[i] = order[j]
                order[j] = t
            }

            // Explicit stack: recursing over a million rows can outrun the real one.
            val stack = ArrayDeque<Int>()
            stack.addLast(0)
            stack.addLast(n - 1)
            while (stack.isNotEmpty()) {
                val hi = stack.removeLast()
                val lo = stack.removeLast()
                if (lo >= hi) continue
                if (hi - lo < 16) {
                    for (i in lo + 1..hi) {
                        var j = i
                        while (j > lo && cmp(order[j - 1], order[j]) > 0) {
                            swap(j - 1, j)
                            j--
                        }
                    }
                    continue
                }
                val mid = (lo + hi) ushr 1
                if (cmp(order[mid], order[lo]) < 0) swap(mid, lo)
                if (cmp(order[hi], order[lo]) < 0) swap(hi, lo)
                if (cmp(order[hi], order[mid]) < 0) swap(hi, mid)
                val pivot = order[mid]
                var i = lo
                var j = hi
                while (i <= j) {
                    while (cmp(order[i], pivot) < 0) i++
                    while (cmp(order[j], pivot) > 0) j--
                    if (i <= j) {
                        swap(i, j)
                        i++
                        j--
                    }
                }
                stack.addLast(lo)
                stack.addLast(j)
                stack.addLast(i)
                stack.addLast(hi)
            }
        }
    }
}
