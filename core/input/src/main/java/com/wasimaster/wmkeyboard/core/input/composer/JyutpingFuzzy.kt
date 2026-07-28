package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Lazy pronunciation (懶音): the sound mergers most Hong Kong speakers under
 * about fifty have, and therefore spell. Someone who says 你 as *lei5* will type
 * `lei`, and without this the dictionary — which has it under *nei5* — offers
 * nothing at all.
 *
 * The Cantonese counterpart to [PinyinFuzzy], and mostly the same shape: expand
 * a syllable into the spellings it could stand for, keep only the ones the
 * inventory says are real, and let the caller rank the typed spelling first.
 *
 * It needs one mechanism [PinyinFuzzy] does not, though, and that is why this is
 * a separate file rather than another table. Two of the real mergers are
 * *conditional on the final*: 國 gwok3 → gok3 and 廣 gwong2 → gong2 happen
 * because the rounded -o swallows the labialisation, but 誇 kwaa1 never becomes
 * kaa1. A flat initial group would produce that, and produce it as a plausible
 * word rather than obvious noise.
 *
 * The mergers are also **bidirectional on purpose**. Hypercorrection is as
 * common as the merger itself — a speaker who knows 你 "should" be *nei* will
 * write 靚 leng3 as *neng3*, and 愛 oi3 as *ngoi3* — so both directions have to
 * find their words.
 */
object JyutpingFuzzy {

    /**
     * Unconditional initial mergers.
     *
     * n↔l is the big one (你 nei5 → lei5, 男 naam4 → laam4). ng↔∅ is the other
     * (我 ngo5 → o5), with the hypercorrect direction adding ng- to syllables
     * that never had it. z↔j and c↔ch are not 懶音 at all but romanisation
     * habit: Yale and older systems spell Jyutping's z- and c- that way, and
     * someone who learned those will type them.
     */
    private val INITIAL_GROUPS = listOf(
        setOf("n", "l"),
        setOf("ng", ""),
        setOf("z", "j"),
        setOf("c", "ch"),
    )

    /**
     * Labialised initials collapsing before a rounded final only — the condition
     * a flat group cannot express. See the class note on 誇 kwaa1.
     */
    private val LABIAL_MERGERS = listOf(
        Triple("gw", "g", "o"),
        Triple("kw", "k", "o"),
    )

    /**
     * Coda mergers, all of them collapsing toward the alveolar place: -ng → -n
     * (恒 hang4 → han4), -m → -n (感 gam2 → gan2), and the matching stops -k → -t
     * (塞 sak1 → sat1) and -p → -t (急 gap1 → gat1).
     *
     * Matched on the whole final rather than the last letter, so a merger can
     * never reshape the vowel — the same reason [PinyinFuzzy] groups whole
     * finals. Only pairs where both halves are real Jyutping finals are listed:
     * -oeng and -eon have no -oen or -eong to merge with, so 香 hoeng keeps its
     * ending. [expand]'s inventory filter is still the backstop, so a pair that
     * turns out not to exist costs nothing.
     */
    private val FINAL_GROUPS = listOf(
        setOf("aan", "aang"), setOf("an", "ang"), setOf("en", "eng"),
        setOf("in", "ing"), setOf("on", "ong"), setOf("un", "ung"),
        setOf("aam", "aan"), setOf("am", "an"), setOf("im", "in"),
        setOf("aak", "aat"), setOf("ak", "at"), setOf("ek", "et"),
        setOf("ik", "it"), setOf("ok", "ot"), setOf("uk", "ut"),
        setOf("aap", "aat"), setOf("ap", "at"), setOf("ip", "it"),
    )

    /**
     * Longest first, so `ng`/`gw`/`kw`/`ch` are never read as `n`/`g`/`k`/`c`.
     * The empty initial is not a member: it is what [initialOf] falls back to,
     * exactly as in [PinyinFuzzy].
     */
    private val INITIALS = listOf(
        "ng", "gw", "kw", "ch", "b", "p", "m", "f", "d", "t",
        "n", "l", "g", "k", "h", "z", "c", "s", "j", "w",
    )

    private fun altsOf(part: String, groups: List<Set<String>>): Set<String> {
        val out = linkedSetOf(part)
        for (group in groups) if (part in group) out.addAll(group)
        return out
    }

    private fun initialOf(syllable: String): String =
        INITIALS.firstOrNull { syllable.startsWith(it) }.orEmpty()

    /**
     * Every lazy-pronunciation variant of [syllable] (including itself) that
     * [valid] says is a real syllable. With [valid] empty — the inventory not
     * loaded yet — the syllable comes back alone, so nothing breaks before the
     * asset lands.
     */
    fun expand(syllable: String, valid: Set<String>): Set<String> {
        if (syllable.isEmpty()) return emptySet()
        val initial = initialOf(syllable)
        val final = syllable.substring(initial.length)
        val out = linkedSetOf(syllable)

        for (i in altsOf(initial, INITIAL_GROUPS)) {
            for (f in altsOf(final, FINAL_GROUPS)) {
                // A syllable has to have *something* in it; ng- dropping its
                // initial off a bare syllabic ng (吳) would leave nothing.
                val cand = i + f
                if (cand.isNotEmpty() && (cand == syllable || cand in valid)) out.add(cand)
            }
        }
        // Conditional labial mergers, both directions, gated on the final.
        for ((labial, plain, condition) in LABIAL_MERGERS) {
            if (!final.startsWith(condition)) continue
            val cand = when (initial) {
                labial -> plain + final
                plain -> labial + final
                else -> continue
            }
            if (cand in valid) out.add(cand)
        }
        return out
    }
}
