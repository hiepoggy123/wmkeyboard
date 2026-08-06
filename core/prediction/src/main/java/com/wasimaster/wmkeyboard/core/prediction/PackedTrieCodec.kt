package com.wasimaster.wmkeyboard.core.prediction

import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.Arrays

/**
 * Serializer for the `.wmdict` binary dictionary format (version 2).
 *
 * The file is an uncompressed, big-endian image of [PackedTrie]'s arrays, laid
 * out in 4-byte-aligned sections so [MappedTrie] can traverse it directly
 * through a memory-mapped buffer — no parse, no heap copy, no load-time pass
 * (which is why [PackedTrie.maxSubtree] is stored rather than recomputed).
 * Compression is transport-only: the APK deflates bundled `.wmdict` assets and
 * the download pipeline inflates `.gz` wordlists *before* writing this format.
 *
 * Version 2 halves version 1 by spending fewer bytes on the same trie:
 *  - `edgeChild` is gone. [PackedTrie.of] numbers nodes breadth-first, handing
 *    out `nextId` and `edgeCursor` in the same loop iteration, so the target of
 *    edge `e` is always node `e + 1` — v1 stored an identity function in 22% of
 *    every file. [write] asserts the invariant rather than trusting it.
 *  - Edge labels are 1-byte indices into a per-file symbol table whenever the
 *    trie uses at most [MAX_SYMBOLS] distinct code units, which covers every
 *    alphabetic script. Japanese and Korean word lists blow past it, so the
 *    [FLAG_LABELS_U8] bit says which encoding a file actually used.
 *  - Frequencies and subtree bounds are [FrequencyCodec] 16-bit minifloats.
 *
 * Layout:
 * ```
 * magic       u32   "WMDC" (0x574D4443)
 * version     u16   2
 * flags       u16   bit 0 = FLAG_LABELS_U8
 * wordCount   i32   distinct words (UI display; not needed for traversal)
 * nodeCount   i32
 * edgeCount   i32   (= nodeCount - 1)
 * symbolCount i32   distinct edge labels when FLAG_LABELS_U8, else 0
 * offsets     i32 × 6   absolute file offset of each section, in order:
 *                       symbols, childStart, edgeLabel, freq, maxSubtree, isWord
 * symbols     u16 × symbolCount, ascending, zero-padded to a 4-byte boundary
 *                   (empty unless FLAG_LABELS_U8)
 * childStart  i32 × (nodeCount + 1)
 * edgeLabel   u8 × edgeCount when FLAG_LABELS_U8 (indices into symbols),
 *             else u16 × edgeCount; zero-padded to a 4-byte boundary
 * freq        u16 × nodeCount, zero-padded to a 4-byte boundary
 * maxSubtree  u16 × nodeCount, zero-padded to a 4-byte boundary
 * isWord      bitset, ceil(nodeCount / 8) bytes (LSB-first within each byte),
 *             zero-padded to a 4-byte boundary
 * ```
 */
object PackedTrieCodec {

    const val MAGIC = 0x574D4443
    const val VERSION = 2

    /** Set when [edgeLabel][PackedTrie.edgeLabel] is stored as symbol-table indices. */
    const val FLAG_LABELS_U8 = 1

    /** A byte index addresses this many symbols, so this many distinct labels. */
    const val MAX_SYMBOLS = 256

    /** Fixed byte size of everything before the first section. */
    internal const val HEADER_BYTES = 4 + 2 + 2 + 4 + 4 + 4 + 4 + 6 * 4

    class Header(val wordCount: Int, val nodeCount: Int, val edgeCount: Int)

    fun write(trie: PackedTrie, out: OutputStream) {
        val nodeCount = trie.freq.size
        val edgeCount = trie.edgeLabel.size
        val bitsetBytes = (nodeCount + 7) / 8

        // v2 drops the edgeChild section because BFS numbering makes it
        // redundant. If a future builder ever numbers nodes some other way the
        // file would be silently wrong, so refuse to write it.
        for (edge in 0 until edgeCount) {
            require(trie.edgeChild[edge] == edge + 1) {
                "edgeChild[$edge] is ${trie.edgeChild[edge]}, expected ${edge + 1}: " +
                    "the .wmdict format requires breadth-first node numbering"
            }
        }

        val symbols = trie.edgeLabel.toSortedSet().toCharArray()
        val labelsAreBytes = symbols.size <= MAX_SYMBOLS
        val symbolCount = if (labelsAreBytes) symbols.size else 0
        val labelBytes = if (labelsAreBytes) edgeCount else edgeCount * 2

        var cursor = HEADER_BYTES
        val offsets = IntArray(6)
        offsets[0] = cursor.also { cursor += pad4(symbolCount * 2) } // symbols
        offsets[1] = cursor.also { cursor += (nodeCount + 1) * 4 } // childStart
        offsets[2] = cursor.also { cursor += pad4(labelBytes) } // edgeLabel
        offsets[3] = cursor.also { cursor += pad4(nodeCount * 2) } // freq
        offsets[4] = cursor.also { cursor += pad4(nodeCount * 2) } // maxSubtree
        offsets[5] = cursor // isWord

        val data = DataOutputStream(out.buffered())
        data.writeInt(MAGIC)
        data.writeShort(VERSION)
        data.writeShort(if (labelsAreBytes) FLAG_LABELS_U8 else 0)
        data.writeInt(trie.wordCount)
        data.writeInt(nodeCount)
        data.writeInt(edgeCount)
        data.writeInt(symbolCount)
        for (offset in offsets) data.writeInt(offset)

        for (index in 0 until symbolCount) data.writeChar(symbols[index].code)
        data.pad(pad4(symbolCount * 2) - symbolCount * 2)

        for (value in trie.childStart) data.writeInt(value)

        if (labelsAreBytes) {
            for (label in trie.edgeLabel) data.writeByte(Arrays.binarySearch(symbols, label))
        } else {
            for (label in trie.edgeLabel) data.writeChar(label.code)
        }
        data.pad(pad4(labelBytes) - labelBytes)

        for (value in trie.freq) data.writeShort(FrequencyCodec.encode(value))
        data.pad(pad4(nodeCount * 2) - nodeCount * 2)
        for (value in trie.maxSubtree) data.writeShort(FrequencyCodec.encode(value))
        data.pad(pad4(nodeCount * 2) - nodeCount * 2)

        var byte = 0
        for (id in 0 until nodeCount) {
            if (trie.isWord[id]) byte = byte or (1 shl (id and 7))
            if (id and 7 == 7) {
                data.writeByte(byte)
                byte = 0
            }
        }
        if (nodeCount and 7 != 0) data.writeByte(byte)
        data.pad(pad4(bitsetBytes) - bitsetBytes)
        data.flush()
    }

    /**
     * Reads and validates just the fixed header of a `.wmdict` file — enough
     * for UI ("152,000 words") without mapping the whole file.
     *
     * @throws IOException if the file is not a well-formed version-2 dictionary.
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

    /**
     * True when [file] is a `.wmdict` this build can actually read. Callers use
     * it instead of `File.isFile` so that a file left behind by an older format
     * reads as "not downloaded" — and so gets offered for download again —
     * rather than as a dictionary that reports zero words forever.
     */
    fun isReadable(file: File): Boolean =
        file.isFile && runCatching { readHeader(file) }.isSuccess

    internal fun pad4(bytes: Int): Int = (bytes + 3) and (-4)

    private fun DataOutputStream.pad(bytes: Int) = repeat(bytes) { writeByte(0) }
}

/**
 * The 16-bit minifloat that `.wmdict` stores frequencies and subtree bounds in:
 * a 5-bit exponent and an 11-bit mantissa with an implicit leading bit.
 *
 * Three properties make this a free swap for the `Int` frequencies it replaces.
 *
 * It is **exact below 2048**, which covers every absolute threshold the engine
 * tests against (`JOIN_MIN_FREQ`, `MIN_CORPUS_COUNT`) as well as most of a
 * bundled word list. Above that the relative error is at most `1 / 2048`.
 *
 * It is **monotone in the raw 16-bit pattern**, so quantising can never reverse
 * two frequencies — only tie them, and only when they were within 0.05% of each
 * other. Consumers weigh frequencies as `ln(1 + f)` against edit costs of
 * `-ln(0.9)` and larger, so the worst perturbation is some two hundred times
 * smaller than the smallest decision the engine makes.
 *
 * Monotone also means it **commutes with `max`**: `round(max(fs))` equals
 * `max(round(f))` for each `f`. [PackedTrie.maxSubtree] therefore stays the
 * exact subtree maximum rather than becoming a loose upper bound, so the
 * branch-and-bound pruning in [TrieCompleter] and [FuzzyBeamSearch] keeps
 * cutting exactly the branches it cut before.
 */
object FrequencyCodec {

    private const val MANTISSA_BITS = 11
    private const val MANTISSA_MASK = (1 shl MANTISSA_BITS) - 1
    private const val IMPLICIT_BIT = 1 shl MANTISSA_BITS

    /** Largest code [encode] can produce: `Int.MAX_VALUE` lands here. */
    const val MAX_CODE = 0xA7FF

    /** [value] as a 16-bit code. Values at or below [MANTISSA_MASK] are stored verbatim. */
    fun encode(value: Int): Int {
        if (value <= MANTISSA_MASK) return if (value <= 0) 0 else value
        val exponent = 32 - value.countLeadingZeroBits() - MANTISSA_BITS
        return (exponent shl MANTISSA_BITS) or ((value ushr (exponent - 1)) and MANTISSA_MASK)
    }

    /** The frequency a 16-bit [code] stands for. */
    fun decode(code: Int): Int {
        val exponent = code ushr MANTISSA_BITS
        if (exponent == 0) return code
        // Guards a corrupt file: exponent 31 would shift the implicit bit off
        // the top of an Int and come back negative.
        val value = (IMPLICIT_BIT or (code and MANTISSA_MASK)) shl (exponent - 1)
        return if (value < 0) Int.MAX_VALUE else value
    }

    /** [value] snapped to the nearest representable frequency. */
    fun round(value: Int): Int = decode(encode(value))
}
