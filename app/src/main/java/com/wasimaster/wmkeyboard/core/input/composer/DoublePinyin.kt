package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Double Pinyin translation. Each syllable is two keystrokes — an initial key
 * and a final key — which this expands to full toneless Pinyin, after which the
 * ordinary [PinyinSyllables] pipeline takes over.
 *
 * Keys are ambiguous by design (one final key can stand for several finals, and
 * a/e/o double as zero-initial leads), so translation is *validity-filtered*:
 * the combination that forms a real syllable per the inventory wins. That both
 * disambiguates and makes the scheme tables forgiving of small mistakes.
 *
 * Only [DoublePinyinScheme.XIAOHE] ships a table here. The other schemes'
 * tables are large key→Pinyin maps generated separately; until one is added,
 * [tableFor] returns null and the composer stays on full Pinyin for it.
 */
object DoublePinyin {

    /** A scheme's key maps: first-key → initial, and final-key → candidate finals. */
    data class Table(
        val initials: Map<Char, String>,
        val finals: Map<Char, List<String>>,
    )

    private val TABLES: Map<DoublePinyinScheme, Table> = mapOf(
        DoublePinyinScheme.XIAOHE to XIAOHE,
    )

    fun tableFor(scheme: DoublePinyinScheme): Table? = TABLES[scheme]

    /**
     * The syllables of a Double Pinyin key [buffer], each spanning exactly two
     * keys, using [valid] to disambiguate. A dangling final key (odd length —
     * a syllable still being typed) is left unconsumed, mirroring a partial full
     * Pinyin syllable.
     */
    fun segments(buffer: String, table: Table, valid: Set<String>): List<PinyinSyllables.Seg> {
        val s = buffer.lowercase()
        val out = ArrayList<PinyinSyllables.Seg>(s.length / 2)
        var i = 0
        while (i + 1 < s.length) {
            out.add(PinyinSyllables.Seg(syllableFor(s[i], s[i + 1], table, valid), 2))
            i += 2
        }
        return out
    }

    /**
     * Full toneless Pinyin for a key [buffer], for the composing-region preview.
     * A dangling final key is appended raw so the user still sees their keystroke.
     */
    fun translate(buffer: String, table: Table, valid: Set<String>): String {
        val s = buffer.lowercase()
        val out = StringBuilder()
        var i = 0
        while (i + 1 < s.length) {
            out.append(syllableFor(s[i], s[i + 1], table, valid))
            i += 2
        }
        if (i < s.length) out.append(s[i])
        return out.toString()
    }

    private fun syllableFor(c1: Char, c2: Char, table: Table, valid: Set<String>): String {
        val finals = table.finals[c2] ?: listOf(c2.toString())
        val cands = LinkedHashSet<String>()
        table.initials[c1]?.let { init -> finals.forEach { cands.add(init + it) } }
        // a/e/o as a first key mean a zero-initial syllable (an, ang, ai, e, ou…).
        if (c1 == 'a' || c1 == 'e' || c1 == 'o') finals.forEach { cands.add(it) }
        cands.firstOrNull { it in valid }?.let { return it }
        // Nothing valid yet (mid-typing or unknown code): best-effort literal.
        return (table.initials[c1] ?: c1.toString()) + (finals.firstOrNull() ?: c2.toString())
    }
}

/**
 * Xiaohe Shuangpin (小鹤双拼). Initials v/i/u stand for zh/ch/sh; every other
 * consonant key is itself. Final keys map to one or two finals, resolved by
 * validity. a/e/o additionally lead zero-initial syllables.
 */
private val XIAOHE = DoublePinyin.Table(
    initials = buildMap {
        for (c in listOf('b', 'p', 'm', 'f', 'd', 't', 'n', 'l', 'g', 'k', 'h',
                'j', 'q', 'x', 'r', 'z', 'c', 's', 'y', 'w')) put(c, c.toString())
        put('v', "zh"); put('i', "ch"); put('u', "sh")
    },
    finals = mapOf(
        'a' to listOf("a"),
        'o' to listOf("uo", "o"),
        'e' to listOf("e"),
        'i' to listOf("i"),
        'u' to listOf("u"),
        'v' to listOf("ui", "v"),
        'b' to listOf("in"),
        'c' to listOf("ao"),
        'd' to listOf("ai"),
        'f' to listOf("en"),
        'g' to listOf("eng"),
        'h' to listOf("ang"),
        'j' to listOf("an"),
        'k' to listOf("ing", "uai"),
        'l' to listOf("iang", "uang"),
        'm' to listOf("ian"),
        'n' to listOf("iao"),
        'p' to listOf("ie"),
        'q' to listOf("iu"),
        'r' to listOf("uan", "er"),
        's' to listOf("ong", "iong"),
        't' to listOf("ue", "ve"),
        'w' to listOf("ei"),
        'x' to listOf("ia", "ua"),
        'y' to listOf("un"),
        'z' to listOf("ou"),
    ),
)
