package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Chinese Pinyin input. The roman buffer is toneless pinyin (or Double Pinyin
 * key codes), shown in the composing region, and the strip offers Hanzi/word
 * candidates from the pinyin→Hanzi pack ([isConversion] + [candidates]).
 *
 * The buffer is split into syllables so a multi-syllable reading like `nihao`
 * offers both the whole-phrase 你好 and the leading-syllable 你/尼/…, each
 * remembering how many *input* chars it consumed ([consumedFor]) so the service
 * commits that prefix and re-converts the tail. Two [CjkConfig] knobs feed in at
 * call time: Double Pinyin ([DoublePinyin]) changes how the buffer segments, and
 * Fuzzy Pinyin ([PinyinFuzzy]) widens each syllable's lookup. With no dictionary
 * match the raw pinyin commits, so the buffer never traps the user.
 */
object PinyinComposer : Composer {

    override val isTransliterating: Boolean get() = true
    override val isConversion: Boolean get() = true

    private const val LIMIT = 12
    private const val FUZZY_CAP = 24

    /** The composing region shows the pinyin — translated from key codes in Double Pinyin. */
    override fun composeBuffer(buffer: String): String {
        val table = doublePinyinTable()
        return if (table != null) DoublePinyin.translate(buffer.lowercase(), table, PinyinSyllables.valid)
        else buffer.lowercase()
    }

    override fun candidates(buffer: String): List<String> =
        ranked(buffer.lowercase()).map { it.text }

    override fun consumedFor(buffer: String, chosen: String): Int {
        val b = buffer.lowercase()
        return ranked(b).firstOrNull { it.text == chosen }?.consumed ?: b.length
    }

    /** A candidate word and the number of input-buffer chars (keys) it covers. */
    private data class Cand(val text: String, val consumed: Int)

    private fun doublePinyinTable(): DoublePinyin.Table? {
        val scheme = CjkConfig.doublePinyin
        return if (scheme == DoublePinyinScheme.OFF) null else DoublePinyin.tableFor(scheme)
    }

    /** Syllables of [buffer] with per-syllable input spans, in the active input mode. */
    private fun segments(buffer: String): List<PinyinSyllables.Seg> {
        val table = doublePinyinTable()
        return if (table != null) DoublePinyin.segments(buffer, table, PinyinSyllables.valid)
        else PinyinSyllables.segment(buffer)
    }

    /**
     * The reading strings to look up for a run of [syllables]: just their
     * concatenation, unless Fuzzy Pinyin is on, in which case the cartesian
     * product of each syllable's fuzzy variants (capped, so a long run can't
     * explode the lookup).
     */
    private fun readingVariants(syllables: List<String>): List<String> {
        if (!CjkConfig.fuzzyPinyin) return listOf(syllables.joinToString(""))
        var acc = listOf("")
        for (syl in syllables) {
            val exps = PinyinFuzzy.expand(syl, PinyinSyllables.valid)
            val next = ArrayList<String>(minOf(FUZZY_CAP, acc.size * exps.size))
            outer@ for (a in acc) for (e in exps) {
                next.add(a + e)
                if (next.size >= FUZZY_CAP) break@outer
            }
            acc = next
        }
        return acc
    }

    /**
     * Candidates for [buffer], longest reading first so whole-phrase entries
     * outrank their single-syllable pieces, each tagged with its consumed input
     * length. Falls back to the dictionary's own prefix matching when the buffer
     * has no segmentable syllable yet (a still-typing first syllable).
     */
    private fun ranked(buffer: String): List<Cand> {
        if (buffer.isEmpty()) return emptyList()
        val dict = CjkDictionaries.pinyin
        val segs = segments(buffer)
        if (segs.isEmpty()) {
            return dict.candidates(buffer, LIMIT)
                .map { Cand(HanVariant.toTraditional(it), buffer.length) }
        }
        // Each cumulative prefix: its syllables and total consumed input length.
        val prefixes = ArrayList<Pair<List<String>, Int>>(segs.size)
        val sylls = ArrayList<String>(segs.size)
        var consumed = 0
        for (seg in segs) {
            sylls.add(seg.syllable)
            consumed += seg.inputLen
            prefixes.add(sylls.toList() to consumed)
        }
        // Longest reading first: 你好 (whole) ranks above 你 (leading syllable).
        val out = LinkedHashMap<String, Cand>()
        for ((run, cons) in prefixes.asReversed()) {
            for (reading in readingVariants(run)) {
                for (w in matching(dict.exact(reading), run.size)) {
                    out.getOrPut(w) { Cand(w, cons) }
                    if (out.size >= LIMIT) break
                }
                if (out.size >= LIMIT) break
            }
            if (out.size >= LIMIT) break
        }
        return out.values.toList()
    }

    /**
     * [raw] in Traditional-or-not output order, with the words of [syllables]
     * characters first — one Hanzi spells one syllable, so a reading segmented
     * into two syllables means a two-character word.
     *
     * The dictionary keys a joined reading, so a phrase shares its key with every
     * single character read the same way: 西安 and 现 both sit under `xian`, and
     * on frequency alone the rare place-name lost to 80 common characters. The
     * syllable count is what tells the two apart — it is the whole reason `xi'an`
     * is worth typing over `xian`. Stable, so frequency still orders each group.
     */
    private fun matching(raw: List<String>, syllables: Int): List<String> =
        raw.map(HanVariant::toTraditional).sortedBy { if (it.length == syllables) 0 else 1 }
}
