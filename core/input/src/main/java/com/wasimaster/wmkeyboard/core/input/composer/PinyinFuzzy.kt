package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Fuzzy Pinyin: the sound pairs many speakers (esp. southern Mandarin) do not
 * distinguish. When enabled the composer looks a syllable up under every valid
 * fuzzy variant too, so an imprecise spelling still finds the right characters —
 * `si` also matches `shi`, `fang` also matches `huang`'s neighbours, and so on.
 *
 * Expansion is validity-filtered against the syllable inventory, so it only ever
 * yields real syllables (never `xhang`-style noise), and the original is always
 * kept and ranked first by the caller.
 */
object PinyinFuzzy {

    // Initial confusions. Grouped bidirectionally: any initial in a group is
    // interchangeable with the others. `l` sits in two groups (n↔l and r↔l), so
    // its alternates are n, r and l.
    private val INITIAL_GROUPS = listOf(
        setOf("zh", "z"),
        setOf("ch", "c"),
        setOf("sh", "s"),
        setOf("n", "l"),
        setOf("r", "l"),
        setOf("f", "h"),
    )

    // Final confusions: front vs. back nasal endings. Matched on the whole final
    // so `ang`↔`an` never mangles `iang` (which pairs with `ian` instead).
    private val FINAL_GROUPS = listOf(
        setOf("an", "ang"),
        setOf("en", "eng"),
        setOf("in", "ing"),
        setOf("ian", "iang"),
        setOf("uan", "uang"),
    )

    // Two-letter initials must be tried before one-letter ones (zh before z).
    private val INITIALS = listOf(
        "zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h",
        "j", "q", "x", "r", "z", "c", "s", "y", "w",
    )

    private fun altsOf(part: String, groups: List<Set<String>>): Set<String> {
        val out = linkedSetOf(part)
        for (g in groups) if (part in g) out.addAll(g)
        return out
    }

    private fun initialOf(syllable: String): String =
        INITIALS.firstOrNull { syllable.startsWith(it) } ?: ""

    /**
     * Every valid fuzzy variant of [syllable] (including itself), keeping only
     * combinations that are real syllables per [valid]. With [valid] empty (no
     * inventory loaded) the syllable is returned unchanged.
     */
    fun expand(syllable: String, valid: Set<String>): Set<String> {
        if (syllable.isEmpty()) return emptySet()
        val initial = initialOf(syllable)
        val final = syllable.substring(initial.length)
        val initialAlts = altsOf(initial, INITIAL_GROUPS)
        val finalAlts = altsOf(final, FINAL_GROUPS)
        val out = linkedSetOf(syllable)
        for (i in initialAlts) for (f in finalAlts) {
            val cand = i + f
            if (cand == syllable || cand in valid) out.add(cand)
        }
        return out
    }
}
