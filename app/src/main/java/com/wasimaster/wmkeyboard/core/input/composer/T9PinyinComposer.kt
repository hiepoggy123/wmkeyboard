package com.wasimaster.wmkeyboard.core.input.composer

/**
 * T9 / 九宫格 pinyin: Chinese on a 9-key phone pad. The buffer is digits, each
 * standing for three or four pinyin letters, and the strip offers Hanzi from the
 * same pinyin pack full-keyboard pinyin uses — no separate dictionary.
 *
 * Structurally this is [PinyinComposer] with one extra degree of freedom. There,
 * a segment resolves to exactly one syllable; here a segment resolves to a digit
 * *code* that several syllables share ([T9Pinyin.index]), so each reading is a
 * capped cartesian product over the segments' alternatives — the same widening
 * [PinyinComposer] already does for Fuzzy Pinyin, applied to keypad ambiguity
 * instead of confusable spellings.
 *
 * Because the input alphabet (digits) *is* the segmentation alphabet, consumed
 * lengths need no translation back to keystrokes — unlike [JapaneseComposer],
 * which segments kana but must report romaji. [consumedFor] counts digits
 * directly, so prefix commit works unchanged.
 */
object T9PinyinComposer : Composer {

    override val isTransliterating: Boolean get() = true
    override val isConversion: Boolean get() = true

    /** The whole input alphabet is digits, and they must be able to *start* a buffer. */
    override val bufferDigits: Boolean get() = true
    override val digitsStartBuffer: Boolean get() = true

    private const val LIMIT = 12

    /** Ceiling on the readings one buffer expands to, so a long run can't blow up. */
    private const val VARIANT_CAP = 24

    /**
     * The composing region shows the pinyin of the best candidate rather than the
     * raw digits: `426` reads back as `hao`, which is what the user is spelling.
     * Falls back to the digits themselves when nothing segments yet.
     */
    override fun composeBuffer(buffer: String): String =
        ranked(buffer).firstOrNull()?.reading ?: buffer

    override fun candidates(buffer: String): List<String> = ranked(buffer).map { it.text }

    override fun consumedFor(buffer: String, chosen: String): Int =
        ranked(buffer).firstOrNull { it.text == chosen }?.consumed ?: buffer.length

    /** A candidate, the digits it covers, and the reading that produced it. */
    private data class Cand(val text: String, val consumed: Int, val reading: String)

    /**
     * Splits the digit buffer against the digit codes of real syllables. Reuses
     * [PinyinSyllables.segment] verbatim — greedy longest match with `'` as a
     * forced boundary — because segmenting digit codes and segmenting syllables
     * are the same problem over a different alphabet. Each returned
     * [PinyinSyllables.Seg.syllable] is therefore a digit code, not a syllable.
     */
    private fun segments(buffer: String): List<PinyinSyllables.Seg> =
        PinyinSyllables.segment(buffer, T9Pinyin.index.keys)

    /**
     * Every pinyin reading a run of digit [codes] could spell: the cartesian
     * product of each code's syllables, capped at [VARIANT_CAP].
     */
    private fun readingVariants(codes: List<String>): List<String> {
        var acc = listOf("")
        for (code in codes) {
            val options = T9Pinyin.index[code] ?: return emptyList()
            val next = ArrayList<String>(minOf(VARIANT_CAP, acc.size * options.size))
            outer@ for (a in acc) for (o in options) {
                next.add(a + o)
                if (next.size >= VARIANT_CAP) break@outer
            }
            acc = next
        }
        return acc
    }

    /**
     * Candidates for [buffer], longest reading first so a whole-phrase entry
     * outranks its leading syllable, each tagged with the number of *digits* it
     * consumed. Mirrors [PinyinComposer.ranked]; with no segmentable code (a
     * digit run that spells no syllable) it yields nothing and the raw digits
     * commit, so the buffer never traps the user.
     */
    private fun ranked(buffer: String): List<Cand> {
        if (buffer.isEmpty()) return emptyList()
        val segs = segments(buffer)
        if (segs.isEmpty()) return emptyList()
        val dict = CjkDictionaries.pinyin
        // Each cumulative prefix: its digit codes and total consumed digits.
        val prefixes = ArrayList<Pair<List<String>, Int>>(segs.size)
        val codes = ArrayList<String>(segs.size)
        var consumed = 0
        for (seg in segs) {
            codes.add(seg.syllable)
            consumed += seg.inputLen
            prefixes.add(codes.toList() to consumed)
        }
        val out = LinkedHashMap<String, Cand>()
        for ((run, cons) in prefixes.asReversed()) {
            for (reading in readingVariants(run)) {
                for (raw in dict.exact(reading)) {
                    val w = HanVariant.toTraditional(raw)
                    out.getOrPut(w) { Cand(w, cons, reading) }
                    if (out.size >= LIMIT) break
                }
                if (out.size >= LIMIT) break
            }
            if (out.size >= LIMIT) break
        }
        return out.values.toList()
    }
}
