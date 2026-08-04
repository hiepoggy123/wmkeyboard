package com.wasimaster.wmkeyboard.core.prediction

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Zero-copy [WordSource] over a memory-mapped `.wmdict` file (see
 * [PackedTrieCodec] for the layout).
 *
 * The trie is never loaded into the Java heap: every lookup reads the mapped
 * pages directly, so "load time" is one `mmap` call, resident memory is OS
 * page cache the kernel can evict under pressure, and pages a user's typing
 * never touches are never read at all. Query semantics match [PackedTrie]
 * exactly — same CSR layout, same binary-searched edges, same
 * branch-and-bound completion.
 *
 * All reads use absolute [ByteBuffer] gets, which do not touch the buffer's
 * position, so a single instance is safe to share across threads.
 */
class MappedTrie private constructor(
    private val buf: ByteBuffer,
    val wordCount: Int,
    private val childStartOff: Int,
    private val edgeLabelOff: Int,
    private val edgeChildOff: Int,
    private val freqOff: Int,
    private val maxSubtreeOff: Int,
    private val isWordOff: Int,
) : WordSource, TrieWalker {

    private fun childStart(node: Int): Int = buf.getInt(childStartOff + node * 4)

    private fun edgeLabel(edge: Int): Char = buf.getChar(edgeLabelOff + edge * 2)

    private fun edgeChild(edge: Int): Int = buf.getInt(edgeChildOff + edge * 4)

    private fun freq(node: Int): Int = buf.getInt(freqOff + node * 4)

    override fun maxSubtree(node: Int): Int = buf.getInt(maxSubtreeOff + node * 4)

    override fun isWord(node: Int): Boolean =
        buf.get(isWordOff + (node ushr 3)).toInt() shr (node and 7) and 1 == 1

    override fun walkers(): List<TrieWalker> = listOf(this)

    override fun child(node: Int, label: Char): Int {
        val edge = childEdge(node, label)
        return if (edge < 0) -1 else edgeChild(edge)
    }

    override fun childrenInto(node: Int, out: ChildBuffer): Int {
        val start = childStart(node)
        val count = childStart(node + 1) - start
        out.ensure(count)
        for (i in 0 until count) {
            out.labels[i] = edgeLabel(start + i)
            out.nodes[i] = edgeChild(start + i)
        }
        return count
    }

    override fun frequency(node: Int): Int = if (isWord(node)) freq(node) else 0

    /** Node reached by walking [word] from the root, or -1 if absent. */
    private fun nodeFor(word: String): Int {
        var node = 0
        for (ch in word) {
            val edge = childEdge(node, ch)
            if (edge < 0) return -1
            node = edgeChild(edge)
        }
        return node
    }

    /** Edge index of [node]'s child labelled [ch], or -1. Binary search — the
     * edges of a node are contiguous and sorted by label. */
    private fun childEdge(node: Int, ch: Char): Int {
        var lo = childStart(node)
        var hi = childStart(node + 1) - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = edgeLabel(mid)
            when {
                c < ch -> lo = mid + 1
                c > ch -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    override fun frequencyOf(word: String): Int {
        val node = nodeFor(word)
        return if (node >= 0 && isWord(node)) freq(node) else 0
    }

    override fun contains(word: String): Boolean = frequencyOf(word) > 0

    override fun complete(prefix: String, limit: Int): List<Suggestion> =
        TrieCompleter.complete(this, prefix, limit)

    /**
     * Every `(word, frequency)` entry, by iterative depth-first walk. Used by
     * consumers that need the flat list (gesture lexicon, phonetic indexes) —
     * this materialises strings on the heap, so call it only to build another
     * index, never per keystroke.
     */
    fun entries(): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>(wordCount)
        val text = StringBuilder()
        // Stack of edge cursors: edgeStack[depth] is the next edge to try at
        // that depth; nodeStack[depth] is the node whose edges they are.
        val nodeStack = ArrayList<Int>()
        val edgeStack = ArrayList<Int>()
        nodeStack.add(0)
        edgeStack.add(childStart(0))
        while (nodeStack.isNotEmpty()) {
            val depth = nodeStack.size - 1
            val node = nodeStack[depth]
            val edge = edgeStack[depth]
            if (edge < childStart(node + 1)) {
                edgeStack[depth] = edge + 1
                val child = edgeChild(edge)
                text.append(edgeLabel(edge))
                if (isWord(child)) out.add(text.toString() to freq(child))
                nodeStack.add(child)
                edgeStack.add(childStart(child))
            } else {
                nodeStack.removeAt(depth)
                edgeStack.removeAt(depth)
                if (text.isNotEmpty()) text.setLength(text.length - 1)
            }
        }
        return out
    }

    companion object {

        /**
         * Maps [file] read-only and validates its header. Returns null (and
         * logs nothing — callers fall back to bundled data) when the file is
         * missing, truncated, or not a version-1 `.wmdict`.
         */
        fun open(file: File): MappedTrie? {
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

        private fun fromBuffer(buf: ByteBuffer, length: Long): MappedTrie? {
            if (length < PackedTrieCodec.HEADER_BYTES) return null
            if (buf.getInt(0) != PackedTrieCodec.MAGIC) return null
            if (buf.getShort(4).toInt() != PackedTrieCodec.VERSION) return null
            val wordCount = buf.getInt(8)
            val nodeCount = buf.getInt(12)
            val edgeCount = buf.getInt(16)
            if (nodeCount <= 0 || edgeCount != nodeCount - 1) return null
            val offsets = IntArray(6) { buf.getInt(20 + it * 4) }
            // The isWord bitset is the last section; check the file holds it all.
            val end = offsets[5] + PackedTrieCodec.pad4((nodeCount + 7) / 8)
            if (end > length) return null
            return MappedTrie(
                buf = buf,
                wordCount = wordCount,
                childStartOff = offsets[0],
                edgeLabelOff = offsets[1],
                edgeChildOff = offsets[2],
                freqOff = offsets[3],
                maxSubtreeOff = offsets[4],
                isWordOff = offsets[5],
            )
        }
    }
}
