package com.wasimaster.wmkeyboard.core.transliteration

/**
 * Rule-based Avro-style phonetic transliteration from romanized Bengali
 * ("banglish") to Bengali script.
 *
 * The engine is a greedy longest-match transliterator over a rule table,
 * with three pieces of context sensitivity:
 *
 *  1. Vowels render as independent letters at a word boundary or after
 *     another vowel, and as vowel signs (kar) after a consonant.
 *  2. Consecutive consonants are joined into conjuncts with a hasant.
 *     The inherent vowel "o" produces no glyph but breaks the cluster,
 *     so "kolokata" stays কলকাতা rather than forming a false conjunct
 *     (whereas "kolkata", with no vowel between l and k, does conjunct).
 *  3. "o" is inherent (silent) between consonants, অ at a word start and
 *     ো at a word end — matching how Avro users expect "valo" → ভালো
 *     while "kori" → করি. After a conjunct, a final "o" stays silent
 *     ("dhorrmo" → ধর্ম, "shopno" → স্বপ্ন), since ো-final words like
 *     ভালো always end in a plain consonant.
 *  4. "rr" spells reph: it renders as a single র that conjuncts with the
 *     following consonant ("dhorrmo" → ধর্ম), except in "rri" (ঋ) or when
 *     no consonant follows.
 *  5. An "a" right after a kar glides with য় ("kiamot" → কিয়ামত, "piano"
 *     → পিয়ানো). After an inherent (silent) vowel the independent আ
 *     survives ("kuroan" → কুরআন), and capital "A" is always explicit আ.
 *
 * Dictionary-level corrections (e.g. "asi" → আছি rather than আসি) are the
 * suggestion engine's job, not this transliterator's.
 */
object AvroPhonetic {

    private const val HASANT = '্'

    private enum class Kind { CONSONANT, VOWEL, OTHER }

    /**
     * @param full independent form (word start or after a vowel)
     * @param kar vowel-sign form used after a consonant; null means the rule
     *        is not a vowel
     */
    private data class Rule(
        val match: String,
        val full: String,
        val kar: String? = null,
        val kind: Kind = if (kar != null) Kind.VOWEL else Kind.CONSONANT,
    )

    private val rules: List<Rule> = buildList {
        // Vowels — longest patterns first within same prefix handled by sort below.
        add(Rule("OI", "ঐ", "ৈ"))
        add(Rule("OU", "ঔ", "ৌ"))
        add(Rule("rri", "ঋ", "ৃ"))
        add(Rule("a", "আ", "া"))
        add(Rule("A", "আ", "া"))
        add(Rule("i", "ই", "ি"))
        add(Rule("I", "ঈ", "ী"))
        add(Rule("u", "উ", "ু"))
        add(Rule("U", "ঊ", "ূ"))
        add(Rule("e", "এ", "ে"))
        add(Rule("E", "এ", "ে"))
        add(Rule("O", "ও", "ো"))
        // "o" handled specially in transliterate().

        // Common fixed conjuncts spelled by digraph in Avro (longest match wins).
        add(Rule("kkh", "ক্ষ")) // লক্ষ, ক্ষমা — "kkh" is the conventional key

        // Aspirated / two-letter consonants first (longest match wins).
        add(Rule("kh", "খ"))
        add(Rule("gh", "ঘ"))
        add(Rule("Ng", "ঙ"))
        add(Rule("ch", "ছ"))
        add(Rule("jh", "ঝ"))
        add(Rule("NG", "ঞ"))
        add(Rule("Th", "ঠ"))
        add(Rule("Dh", "ঢ"))
        add(Rule("th", "থ"))
        add(Rule("dh", "ধ"))
        add(Rule("ph", "ফ"))
        add(Rule("bh", "ভ"))
        add(Rule("sh", "শ"))
        add(Rule("Sh", "ষ"))
        add(Rule("Rh", "ঢ়"))
        add(Rule("ng", "ং"))

        // Single consonants.
        add(Rule("k", "ক"))
        add(Rule("g", "গ"))
        add(Rule("c", "চ"))
        add(Rule("j", "জ"))
        add(Rule("T", "ট"))
        add(Rule("D", "ড"))
        add(Rule("N", "ণ"))
        add(Rule("t", "ত"))
        add(Rule("d", "দ"))
        add(Rule("n", "ন"))
        add(Rule("p", "প"))
        add(Rule("f", "ফ"))
        add(Rule("b", "ব"))
        add(Rule("v", "ভ"))
        add(Rule("m", "ম"))
        add(Rule("z", "য"))
        add(Rule("r", "র"))
        add(Rule("l", "ল"))
        add(Rule("S", "শ"))
        add(Rule("s", "স"))
        add(Rule("h", "হ"))
        add(Rule("R", "ড়"))
        // "y"/"Y" are context-sensitive (jofola vs য়), handled in transliterate().
        add(Rule("w", "ও", "ো"))
        add(Rule("x", "ক্স"))

        // Signs and digits.
        add(Rule(":", "ঃ", null, Kind.OTHER))
        add(Rule("^", "ঁ", null, Kind.OTHER))
        add(Rule(",,", HASANT.toString(), null, Kind.OTHER))
        add(Rule("..", ".", null, Kind.OTHER))
        add(Rule(".", "।", null, Kind.OTHER))
        add(Rule("$", "৳", null, Kind.OTHER))
        "0123456789".forEachIndexed { index, digit ->
            add(Rule(digit.toString(), ('০' + index).toString(), null, Kind.OTHER))
        }
    }.sortedByDescending { it.match.length }

    /** Transliterates one romanized word (or free text) into Bengali script. */
    fun transliterate(input: String): String {
        val out = StringBuilder()
        var prev = Kind.OTHER
        // Whether the last vowel rendered as a kar (vowel sign). An "a" right
        // after a kar glides with য় instead of standing as independent আ.
        var prevKar = false
        var i = 0
        while (i < input.length) {
            // Inherent vowel: silent between consonants, অ at word start, ো at word end.
            if (input[i] == 'o' && !input.startsWith("oo", i)) {
                val atWordEnd = i == input.length - 1 || !input[i + 1].isLetter()
                val afterConjunct = out.length >= 2 && out[out.length - 2] == HASANT
                when {
                    prev == Kind.CONSONANT && atWordEnd && !afterConjunct -> out.append('ো')
                    prev == Kind.CONSONANT -> Unit // inherent vowel, no glyph
                    else -> out.append('অ')
                }
                prev = Kind.VOWEL
                prevKar = false
                i++
                continue
            }
            if (input.startsWith("oo", i)) {
                val asKar = prev == Kind.CONSONANT
                out.append(if (asKar) "ু" else "উ")
                prev = Kind.VOWEL
                prevKar = asKar
                i += 2
                continue
            }

            // "a" right after a kar glides with য় — "kiamot" → কিয়ামত,
            // "piano" → পিয়ানো, "dea" → দেয়া — matching pronunciation.
            // After an inherent (silent) vowel the independent আ survives
            // ("kuroan" → কুরআন), and a capital "A" always stays explicit আ.
            if (input[i] == 'a' && prev == Kind.VOWEL && prevKar) {
                out.append("য়া") // U+09DF (precomposed) + U+09BE
                prev = Kind.VOWEL
                prevKar = true
                i++
                continue
            }

            // "y": jofola (্য) after a consonant — "shyam" → শ্যাম — and
            // য় elsewhere (word start, after a vowel) — "meye" → মেয়ে.
            // "Y" is always য় and never joins the running cluster, so য়
            // itself stays reachable right after a consonant ("kY" → কয়).
            if (input[i] == 'y' || input[i] == 'Y') {
                if (input[i] == 'y' && prev == Kind.CONSONANT) {
                    out.append(HASANT).append('য')
                } else {
                    out.append('য়')
                }
                prev = Kind.CONSONANT
                prevKar = false
                i++
                continue
            }

            // "rr" is reph: a single র that conjuncts with the following
            // consonant ("borrno" → বর্ন), not a doubled র্র. "rri" (ঋ) has
            // already priority, and with no consonant following, "rr" falls
            // through to the plain r-rule.
            if (input.startsWith("rr", i) && !input.startsWith("rri", i)) {
                val next = rules.firstOrNull { input.startsWith(it.match, i + 2) }
                val joinsConsonant = next?.kind == Kind.CONSONANT ||
                    (i + 2 < input.length && input[i + 2] == 'y')
                if (joinsConsonant) {
                    if (prev == Kind.CONSONANT) out.append(HASANT)
                    out.append('র')
                    prev = Kind.CONSONANT
                    prevKar = false
                    i += 2
                    continue
                }
            }

            val rule = rules.firstOrNull { input.startsWith(it.match, i) }
            if (rule == null) {
                out.append(input[i])
                prev = Kind.OTHER
                prevKar = false
                i++
                continue
            }
            when (rule.kind) {
                Kind.VOWEL -> {
                    val asKar = prev == Kind.CONSONANT
                    out.append(if (asKar) rule.kar else rule.full)
                    prevKar = asKar
                }
                Kind.CONSONANT -> {
                    if (prev == Kind.CONSONANT) out.append(HASANT)
                    out.append(rule.full)
                    prevKar = false
                }
                Kind.OTHER -> {
                    out.append(rule.full)
                    prevKar = false
                }
            }
            prev = rule.kind
            i += rule.match.length
        }
        return out.toString()
    }
}
