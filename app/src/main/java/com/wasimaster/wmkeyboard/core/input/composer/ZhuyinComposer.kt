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

    /** The buffer is already bopomofo — what the user typed is what is shown. */
    override fun composeBuffer(buffer: String): String = buffer

    override fun candidates(buffer: String): List<String> = ranked(buffer).map { it.text }

    override fun consumedFor(buffer: String, chosen: String): Int =
        ranked(buffer).firstOrNull { it.text == chosen }?.consumed ?: buffer.length

    /** A candidate and the number of bopomofo chars (tone marks included) it covers. */
    private data class Cand(val text: String, val consumed: Int)

    /**
     * Candidates for [buffer], longest reading first so a whole phrase outranks
     * its leading syllable, each tagged with its consumed bopomofo length. With
     * nothing segmentable — the table unloaded, or a half-typed syllable — it
     * yields nothing and the raw bopomofo commits, so the buffer never traps the
     * user.
     */
    private fun ranked(buffer: String): List<Cand> {
        if (buffer.isEmpty()) return emptyList()
        val segs = ZhuyinSyllables.segment(buffer)
        if (segs.isEmpty()) return emptyList()
        val dict = CjkDictionaries.pinyin
        // Each cumulative prefix: its pinyin reading and total consumed bopomofo.
        val prefixes = ArrayList<Pair<String, Int>>(segs.size)
        val reading = StringBuilder()
        var consumed = 0
        for (seg in segs) {
            reading.append(seg.pinyin)
            consumed += seg.inputLen
            prefixes.add(reading.toString() to consumed)
        }
        val out = LinkedHashMap<String, Cand>()
        for ((run, cons) in prefixes.asReversed()) {
            for (w in dict.exact(run)) {
                out.getOrPut(w) { Cand(w, cons) }
                if (out.size >= LIMIT) break
            }
            if (out.size >= LIMIT) break
        }
        return out.values.toList()
    }
}
