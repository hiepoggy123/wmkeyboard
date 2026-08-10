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

    /**
     * One confusion group, and the id it is stored under.
     *
     * [id] is the two members joined by a hyphen. It is persisted, so it is
     * append-only in the same way an enum's ordinals are: renaming one silently
     * turns that pair back on for everybody who had switched it off.
     */
    data class Pair(val id: String, val members: Set<String>, val initial: Boolean)

    private fun initialPair(a: String, b: String) = Pair("$a-$b", setOf(a, b), true)

    private fun finalPair(a: String, b: String) = Pair("$a-$b", setOf(a, b), false)

    /**
     * Every group, in the order the settings screen lists them.
     *
     * Initial confusions are bidirectional: any initial in a group is
     * interchangeable with the others. `l` sits in two groups (n↔l and r↔l), so
     * its alternates are n, r and l when both are on.
     *
     * Final confusions are front vs. back nasal endings, matched on the whole
     * final so `ang`↔`an` never mangles `iang` (which pairs with `ian` instead).
     */
    val PAIRS = listOf(
        initialPair("zh", "z"),
        initialPair("ch", "c"),
        initialPair("sh", "s"),
        initialPair("n", "l"),
        initialPair("r", "l"),
        initialPair("f", "h"),
        finalPair("an", "ang"),
        finalPair("en", "eng"),
        finalPair("in", "ing"),
        finalPair("ian", "iang"),
        finalPair("uan", "uang"),
    )

    /**
     * What "Fuzzy Pinyin is on" means with nothing else chosen: all eleven
     * groups, which is what the composer did before any of them could be picked
     * individually.
     */
    val ALL_PAIRS: Set<String> = PAIRS.mapTo(LinkedHashSet()) { it.id }

    // Two-letter initials must be tried before one-letter ones (zh before z).
    private val INITIALS = listOf(
        "zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h",
        "j", "q", "x", "r", "z", "c", "s", "y", "w",
    )

    private fun altsOf(part: String, initial: Boolean, enabled: Set<String>): Set<String> {
        val out = linkedSetOf(part)
        for (p in PAIRS) {
            if (p.initial != initial || p.id !in enabled) continue
            if (part in p.members) out.addAll(p.members)
        }
        return out
    }

    private fun initialOf(syllable: String): String =
        INITIALS.firstOrNull { syllable.startsWith(it) }.orEmpty()

    /**
     * Every valid fuzzy variant of [syllable] (including itself), keeping only
     * combinations that are real syllables per [valid]. With [valid] empty (no
     * inventory loaded) the syllable is returned unchanged.
     *
     * [enabled] is the set of [Pair.id]s to apply, defaulting to all of them.
     * An empty set expands nothing, which is what a user who turned every group
     * off asked for — the caller checks the Fuzzy Pinyin switch itself.
     */
    fun expand(
        syllable: String,
        valid: Set<String>,
        enabled: Set<String> = ALL_PAIRS,
    ): Set<String> {
        if (syllable.isEmpty()) return emptySet()
        val initial = initialOf(syllable)
        val final = syllable.substring(initial.length)
        val initialAlts = altsOf(initial, initial = true, enabled = enabled)
        val finalAlts = altsOf(final, initial = false, enabled = enabled)
        val out = linkedSetOf(syllable)
        for (i in initialAlts) for (f in finalAlts) {
            val cand = i + f
            if (cand == syllable || cand in valid) out.add(cand)
        }
        return out
    }
}
