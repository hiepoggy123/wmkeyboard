package com.wasimaster.wmkeyboard.core.prediction

import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Serializer for the `.wmdict` binary dictionary format (version 1).
 *
 * The file is an uncompressed, big-endian image of [PackedTrie]'s six arrays,
 * laid out in 4-byte-aligned sections so [MappedTrie] can traverse it directly
 * through a memory-mapped buffer — no parse, no heap copy, no load-time pass
 * (which is why [PackedTrie.maxSubtree] is stored rather than recomputed).
 * Compression is transport-only: the APK deflates bundled `.wmdict` assets and
 * the download pipeline inflates `.gz` wordlists *before* writing this format.
 *
 * Layout:
 * ```
 * magic       u32   "WMDC" (0x574D4443)
 * version     u16   1
 * flags       u16   0 (reserved)
 * wordCount   i32   distinct words (UI display; not needed for traversal)
 * nodeCount   i32
 * edgeCount   i32   (= nodeCount - 1)
 * offsets     i32 × 6   absolute file offset of each section, in order:
 *                       childStart, edgeLabel, edgeChild, freq, maxSubtree, isWord
 * childStart  i32 × (nodeCount + 1)
 * edgeLabel   u16 × edgeCount, zero-padded to a 4-byte boundary
 * edgeChild   i32 × edgeCount
 * freq        i32 × nodeCount
 * maxSubtree  i32 × nodeCount
 * isWord      bitset, ceil(nodeCount / 8) bytes (LSB-first within each byte),
 *             zero-padded to a 4-byte boundary
 * ```
 */
object PackedTrieCodec {

    const val MAGIC = 0x574D4443
    const val VERSION = 1

    /** Fixed byte size of everything before the first section. */
    internal const val HEADER_BYTES = 4 + 2 + 2 + 4 + 4 + 4 + 6 * 4

    class Header(val wordCount: Int, val nodeCount: Int, val edgeCount: Int)

    fun write(trie: PackedTrie, out: OutputStream) {
        val nodeCount = trie.freq.size
        val edgeCount = trie.edgeLabel.size
        val bitsetBytes = (nodeCount + 7) / 8

        var cursor = HEADER_BYTES
        val offsets = IntArray(6)
        offsets[0] = cursor.also { cursor += (nodeCount + 1) * 4 } // childStart
        offsets[1] = cursor.also { cursor += pad4(edgeCount * 2) } // edgeLabel
        offsets[2] = cursor.also { cursor += edgeCount * 4 } // edgeChild
        offsets[3] = cursor.also { cursor += nodeCount * 4 } // freq
        offsets[4] = cursor.also { cursor += nodeCount * 4 } // maxSubtree
        offsets[5] = cursor // isWord

        val data = DataOutputStream(out.buffered())
        data.writeInt(MAGIC)
        data.writeShort(VERSION)
        data.writeShort(0)
        data.writeInt(trie.wordCount)
        data.writeInt(nodeCount)
        data.writeInt(edgeCount)
        for (offset in offsets) data.writeInt(offset)

        for (value in trie.childStart) data.writeInt(value)
        for (label in trie.edgeLabel) data.writeChar(label.code)
        repeat(pad4(edgeCount * 2) - edgeCount * 2) { data.writeByte(0) }
        for (child in trie.edgeChild) data.writeInt(child)
        for (value in trie.freq) data.writeInt(value)
        for (value in trie.maxSubtree) data.writeInt(value)
        var byte = 0
        for (id in 0 until nodeCount) {
            if (trie.isWord[id]) byte = byte or (1 shl (id and 7))
            if (id and 7 == 7) {
                data.writeByte(byte)
                byte = 0
            }
        }
        if (nodeCount and 7 != 0) data.writeByte(byte)
        repeat(pad4(bitsetBytes) - bitsetBytes) { data.writeByte(0) }
        data.flush()
    }

    /**
     * Reads and validates just the fixed header of a `.wmdict` file — enough
     * for UI ("152,000 words") without mapping the whole file.
     *
     * @throws IOException if the file is not a well-formed version-1 dictionary.
     */
    fun readHeader(file: File): Header = RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < HEADER_BYTES) throw IOException("truncated dictionary header")
        if (raf.readInt() != MAGIC) throw IOException("not a .wmdict file")
        val version = raf.readUnsignedShort()
        if (version != VERSION) throw IOException("unsupported dictionary version $version")
        raf.readUnsignedShort() // flags
        Header(
            wordCount = raf.readInt(),
            nodeCount = raf.readInt(),
            edgeCount = raf.readInt(),
        )
    }

    internal fun pad4(bytes: Int): Int = (bytes + 3) and (-4)
}
