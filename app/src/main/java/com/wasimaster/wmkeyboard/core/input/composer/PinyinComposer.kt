package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Chinese Pinyin input. The roman buffer is toneless pinyin, shown as-is in the
 * composing region, and the strip offers Hanzi/word candidates from the shipped
 * pinyin→Hanzi table ([isConversion] + [candidates]).
 *
 * The buffer is segmented into syllables ([PinyinSyllables]) so a multi-syllable
 * reading like `nihao` offers both the whole-phrase 你好 and the single-syllable
 * 你/尼/… for its leading syllable. Each candidate remembers how many input chars
 * it consumed ([consumedFor]); the service commits that prefix and re-converts the
 * tail, instead of wiping the whole buffer. With no dictionary match the raw
 * pinyin commits, so the buffer never traps the user.
 */
object PinyinComposer : Composer {

    override val isTransliterating: Boolean get() = true
    override val isConversion: Boolean get() = true

    private const val LIMIT = 12

    /** The composing region shows the pinyin the user typed. */
    override fun composeBuffer(buffer: String): String = buffer.lowercase()

    override fun candidates(buffer: String): List<String> =
        ranked(buffer.lowercase()).map { it.text }

    override fun consumedFor(buffer: String, chosen: String): Int {
        val b = buffer.lowercase()
        return ranked(b).firstOrNull { it.text == chosen }?.consumed ?: b.length
    }

    /** A candidate word and the number of input-buffer chars it covers. */
    private data class Cand(val text: String, val consumed: Int)

    /**
     * Candidates for [buffer], longest reading first so whole-phrase entries
     * outrank their single-syllable pieces, each tagged with its consumed input
     * length. Falls back to the dictionary's own prefix matching when the buffer
     * has no segmentable syllable yet (still-typing first syllable).
     */
    private fun ranked(buffer: String): List<Cand> {
        if (buffer.isEmpty()) return emptyList()
        val dict = CjkDictionaries.pinyin
        val segs = PinyinSyllables.segment(buffer)
        if (segs.isEmpty()) {
            return dict.candidates(buffer, LIMIT).map { Cand(it, buffer.length) }
        }
        // Build each syllable-prefix reading (no apostrophes) with its input span.
        val reading = StringBuilder()
        var span = 0
        val prefixes = ArrayList<Pair<String, Int>>(segs.size)
        for (seg in segs) {
            reading.append(seg.syllable)
            span += seg.inputLen
            prefixes.add(reading.toString() to span)
        }
        // Longest reading first: 你好 (whole) ranks above 你 (leading syllable).
        val out = LinkedHashMap<String, Cand>()
        for ((read, consumed) in prefixes.asReversed()) {
            for (w in dict.exact(read)) {
                out.getOrPut(w) { Cand(w, consumed) }
                if (out.size >= LIMIT) break
            }
            if (out.size >= LIMIT) break
        }
        return out.values.toList()
    }
}
