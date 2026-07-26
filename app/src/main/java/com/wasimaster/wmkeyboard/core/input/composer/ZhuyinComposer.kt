package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Zhuyin / Bopomofo (注音) input, the standard method in Taiwan. The buffer holds
 * bopomofo symbols typed straight off the pad, and the strip offers Hanzi from
 * the same pinyin pack full-keyboard pinyin uses — the syllable sets are
 * identical, so [ZhuyinSyllables] translates each segmented syllable to its
 * pinyin spelling and looks that up. No Zhuyin dictionary is downloaded.
 *
 * Structurally this is [PinyinComposer] with the reading translated per segment.
 * It is simpler than [JapaneseComposer] in the one place that matters: the buffer
 * *is* the segmentation alphabet, so consumed lengths need no mapping back to
 * keystrokes and [consumedFor] just sums spans. Only the lookup key is
 * translated, never a length.
 */
object ZhuyinComposer : Composer {

    override val isTransliterating: Boolean get() = true
    override val isConversion: Boolean get() = true

    private const val LIMIT = 12

    /** Ranked depth for resolving a tap, so the expanded grid can be tapped too. */
    private const val LOOKUP_LIMIT = 128

    /** The buffer is already bopomofo — what the user typed is what is shown. */
    override fun composeBuffer(buffer: String): String = buffer

    override fun candidates(buffer: String): List<String> = candidates(buffer, LIMIT)

    override fun candidates(buffer: String, limit: Int): List<String> =
        ranked(buffer, limit).map { it.text }

    override fun consumedForIndex(buffer: String, index: Int): Int =
        ranked(buffer, LOOKUP_LIMIT).getOrNull(index)?.consumed ?: buffer.length

    override fun consumedFor(buffer: String, chosen: String): Int =
        ranked(buffer, LOOKUP_LIMIT).firstOrNull { it.text == chosen }?.consumed ?: buffer.length

    /** A candidate and the number of bopomofo chars (tone marks included) it covers. */
    private data class Cand(val text: String, val consumed: Int)

    /**
     * Candidates for [buffer], longest reading first so a whole phrase outranks
     * its leading syllable, each tagged with its consumed bopomofo length. With
     * nothing segmentable — the table unloaded, or a half-typed syllable — it
     * yields nothing and the raw bopomofo commits, so the buffer never traps the
     * user.
     */
    private fun ranked(buffer: String, limit: Int): List<Cand> {
        if (buffer.isEmpty()) return emptyList()
        return cache.get(buffer, limit) { rank(buffer, limit) }
    }

    private fun rank(buffer: String, limit: Int): List<Cand> {
        val segs = ZhuyinSyllables.segment(buffer)
        if (segs.isEmpty()) return emptyList()
        // Bopomofo is a second spelling of the same Mandarin syllables, so the
        // decoder sees the pinyin reading and the pack needs no Zhuyin of its own.
        val input = Lattice.input(segs.map { it.pinyin }, segs.map { it.inputLen })
        return Lattice.decode(input, CjkDictionaries.pinyin, CjkDictionaries.ngrams, Lattice.Opts(limit = limit))
            .map { Cand(HanVariant.toTraditional(it.text), it.consumed) }
            .distinctBy { it.text }
    }

    private val cache = RankCache<List<Cand>>()
}
