package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Cantonese Jyutping (粵拼) input. The roman buffer is Jyutping with optional
 * tone digits, and the strip offers Hanzi from the Cantonese pack — a table of
 * its own, since Cantonese readings are nothing like Mandarin ones (`nei5hou2`
 * where Mandarin has `nihao`).
 *
 * The same segmenting, prefix-committing shape as [PinyinComposer]: a
 * multi-syllable buffer offers both the whole phrase and its leading syllable,
 * each tagged with the input length it consumed, so a commit takes that prefix
 * and re-converts the tail.
 */
object JyutpingComposer : Composer {

    override val isTransliterating: Boolean get() = true
    override val isConversion: Boolean get() = true

    /**
     * Tones are digits 1–6, so a digit typed mid-syllable feeds the buffer
     * instead of committing it and typing a number. Unlike T9 a digit cannot
     * *start* a syllable, so [digitsStartBuffer] stays false and typing a plain
     * number on an empty buffer still works.
     */
    override val bufferDigits: Boolean get() = true

    private const val LIMIT = 12

    /** The composing region shows the Jyutping as typed, tone digits and all. */
    override fun composeBuffer(buffer: String): String = buffer.lowercase()

    override fun candidates(buffer: String): List<String> =
        ranked(buffer.lowercase()).map { it.text }

    override fun consumedFor(buffer: String, chosen: String): Int {
        val b = buffer.lowercase()
        return ranked(b).firstOrNull { it.text == chosen }?.consumed ?: b.length
    }

    /** A candidate word and the number of input chars (tone digits included) it covers. */
    private data class Cand(val text: String, val consumed: Int)

    /**
     * Candidates for [buffer], longest reading first so a whole phrase outranks
     * its leading syllable, each tagged with its consumed input length. Falls
     * back to the dictionary's own prefix matching when nothing segments yet — a
     * still-typing first syllable — mirroring [PinyinComposer].
     */
    private fun ranked(buffer: String): List<Cand> {
        if (buffer.isEmpty()) return emptyList()
        val dict = CjkDictionaries.jyutping
        val segs = JyutpingSyllables.segment(buffer)
        if (segs.isEmpty()) {
            return dict.candidates(buffer, LIMIT).map { Cand(it, buffer.length) }
        }
        // Each cumulative prefix: its toneless reading and total consumed input.
        val prefixes = ArrayList<Pair<String, Int>>(segs.size)
        val reading = StringBuilder()
        var consumed = 0
        for (seg in segs) {
            reading.append(seg.syllable)
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
