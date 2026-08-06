package com.wasimaster.wmkeyboard.core.prediction

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Zero-copy reader over a memory-mapped `.wmng` pack (see [NgramPackCodec] for
 * the layout).
 *
 * Nothing is parsed at open and nothing but a few ints lands on the Java heap:
 * every lookup reads mapped pages, so resident memory is page cache the kernel
 * can evict and the parts of the corpus a user never types are never read.
 *
 * The reader leans throughout on the format's one structural promise — that
 * word ids are ranks in UTF-8 byte order. A follower run is stored sorted by
 * id, which therefore also means sorted alphabetically, so looking up
 * `bigramCount(prev, next)` binary-searches the run *by string* and never
 * touches the vocabulary index at all. Only heads go through [idOf], and there
 * are at most three distinct ones per keystroke, all cached.
 *
 * All reads use absolute [ByteBuffer] gets, so an instance is safe to share
 * across threads.
 */
class MappedNgramPack private constructor(
    private val buf: ByteBuffer,
    private val vocabCount: Int,
    private val contextCount: Int,
    private val vocabOffsetOff: Int,
    private val vocabBytesOff: Int,
    private val bigramRunOff: Int,
    private val bigramFollowerOff: Int,
    private val bigramCountOff: Int,
    private val contextKeyOff: Int,
    private val trigramRunOff: Int,
    private val trigramFollowerOff: Int,
    private val trigramCountOff: Int,
) {

    /** A resolved head and the slice of follower slots it owns. */
    private class Run(val key: String, val second: String?, val start: Int, val end: Int)

    // Two bigram slots and one trigram slot is exactly what a keystroke needs:
    // NgramReranker asks about `previousWord` and, when that word is unknown,
    // `previousWord2` as a skip-gram fallback, plus the one two-word context.
    // Immutable snapshots behind @Volatile rather than a mutable cache — the
    // pack is read from the async completion path and the main thread at once,
    // and a lost update only costs a re-resolve.
    @Volatile private var bigramSlot0: Run? = null
    @Volatile private var bigramSlot1: Run? = null
    @Volatile private var trigramSlot: Run? = null

    fun bigramCount(first: String, second: String): Int {
        val run = bigramRun(first)
        if (run.start >= run.end) return 0
        val slot = findFollower(bigramFollowerOff, run.start, run.end, second)
        return if (slot < 0) 0 else count(bigramCountOff, slot)
    }

    fun trigramCount(first: String, second: String, third: String): Int {
        val run = trigramRun(first, second)
        if (run.start >= run.end) return 0
        val slot = findFollower(trigramFollowerOff, run.start, run.end, third)
        return if (slot < 0) 0 else count(trigramCountOff, slot)
    }

    fun nextWords(first: String, limit: Int): List<String> =
        bestFollowers(bigramRun(first), bigramFollowerOff, bigramCountOff, limit)

    fun nextWordsAfter(first: String, second: String, limit: Int): List<String> =
        bestFollowers(trigramRun(first, second), trigramFollowerOff, trigramCountOff, limit)

    /** The word [id] stands for, or null when a corrupt file names a stranger. */
    fun word(id: Int): String? {
        if (id !in 0 until vocabCount) return null
        val start = buf.getInt(vocabOffsetOff + id * 4)
        val end = buf.getInt(vocabOffsetOff + (id + 1) * 4)
        if (start !in 0..end) return null
        val bytes = ByteArray(end - start) { buf.get(vocabBytesOff + start + it) }
        return String(bytes, Charsets.UTF_8)
    }

    /** Rank of [word] in the pack's vocabulary, or -1. */
    fun idOf(word: String): Int {
        val query = word.toByteArray(Charsets.UTF_8)
        var lo = 0
        var hi = vocabCount - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val diff = compareVocab(mid, query)
            when {
                diff < 0 -> lo = mid + 1
                diff > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun bigramRun(head: String): Run {
        bigramSlot0?.let { if (it.second == null && it.key == head) return it }
        bigramSlot1?.let {
            if (it.second == null && it.key == head) {
                // Promote, so alternating heads do not evict each other.
                bigramSlot1 = bigramSlot0
                bigramSlot0 = it
                return it
            }
        }
        val id = idOf(head)
        val fresh = if (id < 0) {
            // Cached as an empty run on purpose: an out-of-vocabulary previous
            // word is asked about once per candidate, and re-searching the
            // whole vocabulary each time is the worst case there is.
            Run(head, null, 0, 0)
        } else {
            Run(head, null, buf.getInt(bigramRunOff + id * 4), buf.getInt(bigramRunOff + (id + 1) * 4))
        }
        bigramSlot1 = bigramSlot0
        bigramSlot0 = fresh
        return fresh
    }

    private fun trigramRun(first: String, second: String): Run {
        trigramSlot?.let { if (it.key == first && it.second == second) return it }
        val firstId = idOf(first)
        val secondId = idOf(second)
        val fresh = if (firstId < 0 || secondId < 0) {
            Run(first, second, 0, 0)
        } else {
            val rank = contextRank((firstId.toLong() shl 24) or secondId.toLong())
            if (rank < 0) {
                Run(first, second, 0, 0)
            } else {
                Run(
                    first,
                    second,
                    buf.getInt(trigramRunOff + rank * 4),
                    buf.getInt(trigramRunOff + (rank + 1) * 4),
                )
            }
        }
        trigramSlot = fresh
        return fresh
    }

    private fun contextRank(key: Long): Int {
        var lo = 0
        var hi = contextCount - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val found = buf.getLong(contextKeyOff + mid * 6) ushr 16
            when {
                found < key -> lo = mid + 1
                found > key -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    /**
     * Slot of [word] within `[start, end)`, or -1. Binary search by string,
     * which only works because ids are UTF-8 ranks and so a run ordered by id
     * is ordered alphabetically too.
     */
    private fun findFollower(followerOff: Int, start: Int, end: Int, word: String): Int {
        val query = word.toByteArray(Charsets.UTF_8)
        var lo = start
        var hi = end - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val diff = compareVocab(followerId(followerOff, mid), query)
            when {
                diff < 0 -> lo = mid + 1
                diff > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    /**
     * The [limit] highest-counting followers of [run], best first, ties broken
     * alphabetically because that is the slot order.
     *
     * A linear scan of the count array rather than a stored best-first
     * permutation: counts are two bytes each and contiguous, so even the
     * fattest head in an English pack streams about 17 KB, while the median
     * head fits in one cache line. Storing the permutation would cost half a
     * megabyte to save a microsecond.
     */
    private fun bestFollowers(run: Run, followerOff: Int, countOff: Int, limit: Int): List<String> {
        if (limit <= 0 || run.start >= run.end) return emptyList()
        val slots = IntArray(limit)
        val codes = IntArray(limit)
        var held = 0
        for (slot in run.start until run.end) {
            // Raw codes compare correctly without decoding: the minifloat is
            // monotone in its bit pattern.
            val code = buf.getShort(countOff + slot * 2).toInt() and 0xFFFF
            if (held == limit && code <= codes[held - 1]) continue
            var at = if (held < limit) held++ else limit - 1
            while (at > 0 && codes[at - 1] < code) {
                codes[at] = codes[at - 1]
                slots[at] = slots[at - 1]
                at--
            }
            codes[at] = code
            slots[at] = slot
        }
        val out = ArrayList<String>(held)
        for (i in 0 until held) word(followerId(followerOff, slots[i]))?.let(out::add)
        return out
    }

    private fun followerId(followerOff: Int, slot: Int): Int =
        buf.getInt(followerOff + slot * 3) ushr 8

    private fun count(countOff: Int, slot: Int): Int =
        FrequencyCodec.decode(buf.getShort(countOff + slot * 2).toInt() and 0xFFFF)

    /** Stored word [id] against [query], as unsigned bytes. */
    private fun compareVocab(id: Int, query: ByteArray): Int {
        if (id !in 0 until vocabCount) return -1
        val start = buf.getInt(vocabOffsetOff + id * 4)
        val end = buf.getInt(vocabOffsetOff + (id + 1) * 4)
        val length = end - start
        val shared = minOf(length, query.size)
        for (i in 0 until shared) {
            val diff = (buf.get(vocabBytesOff + start + i).toInt() and 0xFF) -
                (query[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return length - query.size
    }

    companion object {

        /**
         * Maps [file] read-only and validates it. Returns null — so the caller
         * falls back to an empty pack — when the file is missing, truncated,
         * not a version-1 `.wmng`, or internally inconsistent.
         */
        fun open(file: File): MappedNgramPack? {
            if (!file.isFile) return null
            return try {
                val buf = RandomAccessFile(file, "r").use { raf ->
                    raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
                }
                fromBuffer(buf, file.length())
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        @Suppress("ReturnCount")
        private fun fromBuffer(buf: ByteBuffer, length: Long): MappedNgramPack? {
            if (length < NgramPackCodec.HEADER_BYTES) return null
            if (buf.getInt(0) != NgramPackCodec.MAGIC) return null
            if (buf.getShort(4).toInt() != NgramPackCodec.VERSION) return null
            val vocabCount = buf.getInt(8)
            val bigramCount = buf.getInt(12)
            val contextCount = buf.getInt(16)
            val trigramCount = buf.getInt(20)
            if (vocabCount !in 1..NgramPackCodec.MAX_VOCAB) return null
            if (bigramCount < 0 || contextCount < 0 || trigramCount < 0) return null
            val offsets = IntArray(NgramPackCodec.SECTIONS) { buf.getInt(24 + it * 4) }
            if (offsets[0] != NgramPackCodec.HEADER_BYTES) return null
            val vocabBytes = buf.getInt(offsets[0] + vocabCount * 4)
            if (vocabBytes < 0) return null
            // Every section sized exactly as the writer laid it out. The two
            // packed sections carry slack past their last element because a
            // u24 is read as one getInt and a u48 as one getLong, so checking
            // "the file is long enough overall" would not be enough: a short
            // interior section would only fail on whichever word sorts last.
            val sizes = intArrayOf(
                (vocabCount + 1) * 4,
                PackedTrieCodec.pad4(vocabBytes),
                (vocabCount + 1) * 4,
                PackedTrieCodec.pad4(bigramCount * 3 + NgramPackCodec.U24_TAIL),
                PackedTrieCodec.pad4(bigramCount * 2),
                PackedTrieCodec.pad4(contextCount * 6 + NgramPackCodec.U48_TAIL),
                (contextCount + 1) * 4,
                PackedTrieCodec.pad4(trigramCount * 3 + NgramPackCodec.U24_TAIL),
                PackedTrieCodec.pad4(trigramCount * 2),
            )
            for (i in 0 until NgramPackCodec.SECTIONS - 1) {
                if (offsets[i + 1] - offsets[i] < sizes[i]) return null
            }
            if (offsets[8].toLong() + sizes[8] > length) return null
            // Both run tables must close on their element counts, or a lookup
            // could hand a slice of one section into another.
            if (buf.getInt(offsets[2] + vocabCount * 4) != bigramCount) return null
            if (buf.getInt(offsets[6] + contextCount * 4) != trigramCount) return null
            return MappedNgramPack(
                buf = buf,
                vocabCount = vocabCount,
                contextCount = contextCount,
                vocabOffsetOff = offsets[0],
                vocabBytesOff = offsets[1],
                bigramRunOff = offsets[2],
                bigramFollowerOff = offsets[3],
                bigramCountOff = offsets[4],
                contextKeyOff = offsets[5],
                trigramRunOff = offsets[6],
                trigramFollowerOff = offsets[7],
                trigramCountOff = offsets[8],
            )
        }
    }
}
