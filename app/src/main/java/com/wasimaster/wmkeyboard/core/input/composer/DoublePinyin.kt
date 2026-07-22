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
 */
object DoublePinyin {

    /** A scheme's key maps: first-key → initial, and final-key → candidate finals. */
    data class Table(
        val initials: Map<Char, String>,
        val finals: Map<Char, List<String>>,
    )

    private val TABLES: Map<DoublePinyinScheme, Table> = mapOf(
        DoublePinyinScheme.MICROSOFT to MICROSOFT,
        DoublePinyinScheme.SOGOU to SOGOU,
        DoublePinyinScheme.XIAOHE to XIAOHE,
        DoublePinyinScheme.ZIRANMA to ZIRANMA,
        DoublePinyinScheme.PINYINPP to PINYINPP,
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

/** Standard initials for Xiaohe, Microsoft, Sogou, and Ziranma: v=zh, i=ch, u=sh. */
private val STANDARD_INITIALS: Map<Char, String> = buildMap {
    for (c in listOf('b', 'p', 'm', 'f', 'd', 't', 'n', 'l', 'g', 'k', 'h',
            'j', 'q', 'x', 'r', 'z', 'c', 's', 'y', 'w')) put(c, c.toString())
    put('v', "zh"); put('i', "ch"); put('u', "sh")
}

/** Pinyin++ initials: v=zh, u=ch, i=sh. */
private val PINYINPP_INITIALS: Map<Char, String> = buildMap {
    for (c in listOf('b', 'p', 'm', 'f', 'd', 't', 'n', 'l', 'g', 'k', 'h',
            'j', 'q', 'x', 'r', 'z', 'c', 's', 'y', 'w')) put(c, c.toString())
    put('v', "zh"); put('u', "ch"); put('i', "sh")
}

/**
 * Xiaohe Shuangpin (小鹤双拼). Initials v/i/u stand for zh/ch/sh; every other
 * consonant key is itself. Final keys map to one or two finals, resolved by
 * validity. a/e/o additionally lead zero-initial syllables.
 */
private val XIAOHE = DoublePinyin.Table(
    initials = STANDARD_INITIALS,
    finals = mapOf(
        'a' to listOf("a"),
        'b' to listOf("in"),
        'c' to listOf("ao"),
        'd' to listOf("ai"),
        'e' to listOf("e"),
        'f' to listOf("en"),
        'g' to listOf("eng"),
        'h' to listOf("ang"),
        'i' to listOf("i"),
        'j' to listOf("an"),
        'k' to listOf("ing", "uai"),
        'l' to listOf("iang", "uang"),
        'm' to listOf("ian"),
        'n' to listOf("iao"),
        'o' to listOf("uo", "o"),
        'p' to listOf("ie"),
        'q' to listOf("iu"),
        'r' to listOf("uan", "er"),
        's' to listOf("ong", "iong"),
        't' to listOf("ue", "ve"),
        'u' to listOf("u"),
        'v' to listOf("ui", "v"),
        'w' to listOf("ei"),
        'x' to listOf("ia", "ua"),
        'y' to listOf("un"),
        'z' to listOf("ou"),
    ),
)

/** Microsoft Double Pinyin (微软双拼). */
private val MICROSOFT = DoublePinyin.Table(
    initials = STANDARD_INITIALS,
    finals = mapOf(
        'a' to listOf("a"),
        'b' to listOf("ou"),
        'c' to listOf("iao"),
        'd' to listOf("iang", "uang"),
        'e' to listOf("e"),
        'f' to listOf("en"),
        'g' to listOf("eng"),
        'h' to listOf("ang"),
        'i' to listOf("i"),
        'j' to listOf("an"),
        'k' to listOf("ao"),
        'l' to listOf("ai"),
        'm' to listOf("ian"),
        'n' to listOf("in"),
        'o' to listOf("uo", "o"),
        'p' to listOf("un"),
        'q' to listOf("iu"),
        'r' to listOf("uan", "er"),
        's' to listOf("ong", "iong"),
        't' to listOf("ue", "ve"),
        'u' to listOf("u"),
        'v' to listOf("ui", "v"),
        'w' to listOf("ia", "ua"),
        'x' to listOf("ie"),
        'y' to listOf("uai", "ing"),
        'z' to listOf("ei"),
    ),
)

/** Sogou Double Pinyin (搜狗双拼). Identical layout to Microsoft. */
private val SOGOU = MICROSOFT

/** Ziranma Double Pinyin (自然码双拼). */
private val ZIRANMA = DoublePinyin.Table(
    initials = STANDARD_INITIALS,
    finals = mapOf(
        'a' to listOf("a"),
        'b' to listOf("ou"),
        'c' to listOf("iao"),
        'd' to listOf("iang", "uang"),
        'e' to listOf("e"),
        'f' to listOf("en"),
        'g' to listOf("eng"),
        'h' to listOf("ang"),
        'i' to listOf("i"),
        'j' to listOf("an"),
        'k' to listOf("ao"),
        'l' to listOf("ai"),
        'm' to listOf("ian"),
        'n' to listOf("in"),
        'o' to listOf("uo", "o"),
        'p' to listOf("un"),
        'q' to listOf("iu"),
        'r' to listOf("uan", "er"),
        's' to listOf("ong", "iong"),
        't' to listOf("ue", "ve"),
        'u' to listOf("u"),
        'v' to listOf("ui", "v"),
        'w' to listOf("ia", "ua"),
        'x' to listOf("ie"),
        'y' to listOf("uai", "ing"),
        'z' to listOf("ei"),
    ),
)

/** Pinyin++ Double Pinyin (拼音加加双拼). Initials: v=zh, u=ch, i=sh. */
private val PINYINPP = DoublePinyin.Table(
    initials = PINYINPP_INITIALS,
    finals = mapOf(
        'a' to listOf("a"),
        'b' to listOf("ia", "ua"),
        'c' to listOf("uan"),
        'd' to listOf("ao"),
        'e' to listOf("e"),
        'f' to listOf("an"),
        'g' to listOf("ang"),
        'h' to listOf("iang", "uang"),
        'i' to listOf("i"),
        'j' to listOf("ian"),
        'k' to listOf("iao"),
        'l' to listOf("in"),
        'm' to listOf("ie"),
        'n' to listOf("iu"),
        'o' to listOf("uo", "o"),
        'p' to listOf("ou"),
        'q' to listOf("ing"),
        'r' to listOf("en"),
        't' to listOf("eng"),
        'u' to listOf("u"),
        'v' to listOf("ui", "v"),
        'w' to listOf("ei"),
        'x' to listOf("uai", "ue", "ve"),
        'y' to listOf("ong", "iong"),
        'z' to listOf("un"),
        's' to listOf("ai"),
    ),
)
