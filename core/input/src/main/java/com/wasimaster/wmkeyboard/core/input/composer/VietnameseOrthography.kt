package com.wasimaster.wmkeyboard.core.input.composer

import java.text.Normalizer

/**
 * Whether a string is shaped like a Vietnamese syllable.
 *
 * Vietnamese spelling is unusually closed: a syllable is one onset from a list
 * of 28, one vowel nucleus from a list of about 50, one coda from a list of 8,
 * and a tone the coda may veto. Nothing else is a syllable, which makes the
 * question decidable from a few tables instead of a dictionary — worth having
 * for a language whose transliterator will happily produce `tést` out of an
 * English word and hand it to the personal dictionary as a new spelling.
 *
 * Deliberately a shape test and not a word test: `blup` fails because no
 * Vietnamese syllable starts `bl`, while `mèo` passes whether or not any
 * wordlist has heard of it. Used to decide what may be *learned*, never what
 * may be typed — a syllable that fails here still commits exactly as typed.
 */
object VietnameseOrthography {

    /** Phụ âm đầu. The empty string is one: a syllable may start with a vowel. */
    private val ONSETS = hashSetOf(
        "", "b", "c", "ch", "d", "đ", "g", "gh", "gi", "h", "k", "kh",
        "l", "m", "n", "ng", "ngh", "nh", "p", "ph", "qu", "r", "s",
        "t", "th", "tr", "v", "x",
    )

    /** Phụ âm cuối. Also possibly empty — an open syllable ends on its vowel. */
    private val CODAS = hashSetOf("", "c", "ch", "m", "n", "ng", "nh", "p", "t")

    /**
     * The stop codas. A syllable that ends in one can only carry sắc or nặng:
     * the other three tones need a voiced ending to be pronounced on, so `bàc`
     * and `bảt` are not misspellings but non-words.
     */
    private val STOP_CODAS = hashSetOf("c", "ch", "p", "t")

    /** Every vowel nucleus, written without its tone. */
    private val NUCLEI = hashSetOf(
        "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y",
        "ai", "ao", "au", "ay", "ây", "âu",
        "eo", "êu",
        "ia", "iê", "iu", "iêu",
        "oa", "oai", "oay", "oă", "oe", "oeo", "oi", "oo", "ôi", "ơi",
        "ua", "uâ", "uay", "uây", "uô", "uôi", "uơ", "uê", "ui", "uy", "uya",
        "uyê", "uyu",
        "ưa", "ươ", "ưi", "ưu", "ươi", "ươu",
        "ya", "yê", "yêu",
    )

    /** Multi-letter onsets, longest first so `ngh` is not read as `ng`. */
    private val LONG_ONSETS = arrayOf("ngh", "ng", "nh", "ch", "gh", "kh", "ph", "th", "tr", "qu", "gi")

    /** Multi-letter codas, longest first. */
    private val LONG_CODAS = arrayOf("ng", "nh", "ch")

    /** Single letters that can stand alone as an onset. */
    private const val SINGLE_ONSETS = "bcdđghklmnpqrstvx"

    /** Single letters that can stand alone as a coda. */
    private const val SINGLE_CODAS = "cmnpt"

    /** The vowels, marked and unmarked, that decide where an onset ends. */
    private const val VOWELS = "aeiouyăâêôơư"

    private const val ACUTE = '́'
    private const val GRAVE = '̀'
    private const val HOOK = '̉'
    private const val TILDE = '̃'
    private const val DOT = '̣'

    /** Whether [word] could be one Vietnamese syllable. */
    fun isSyllable(word: String): Boolean {
        val clean = word.trim().lowercase()
        if (clean.isEmpty()) return false

        // Split the tone off its letter: the tables below are written without
        // tones, and a tone is a property of the whole syllable anyway.
        var tone: Char? = null
        val bare = StringBuilder(clean.length)
        for (ch in Normalizer.normalize(clean, Normalizer.Form.NFD)) {
            when (ch) {
                ACUTE, GRAVE, HOOK, TILDE, DOT -> {
                    // Two tones on one syllable is not a syllable.
                    if (tone != null) return false
                    tone = ch
                }
                else -> bare.append(ch)
            }
        }
        val base = Normalizer.normalize(bare, Normalizer.Form.NFC)
        if (base.isEmpty()) return false

        val onset = onsetOf(base)
        val rest = base.substring(onset.length)
        if (rest.isEmpty()) return false
        val coda = codaOf(rest)
        val nucleus = rest.dropLast(coda.length)

        if (nucleus.isEmpty()) return false
        if (onset !in ONSETS) return false
        if (coda !in CODAS) return false
        if (nucleus !in NUCLEI) return false
        // Sắc and nặng are the only tones a stop coda can carry.
        if (coda in STOP_CODAS && tone != null && tone != ACUTE && tone != DOT) return false
        return true
    }

    private fun onsetOf(base: String): String {
        for (candidate in LONG_ONSETS) {
            if (!base.startsWith(candidate)) continue
            // `gi` is the one ambiguous onset: before another vowel it is the
            // onset (gió, giúp), but in `gì` and `gìn` the i *is* the nucleus
            // and the onset is a bare g.
            if (candidate == "gi" && (base.length == 2 || base[2] !in VOWELS)) return "g"
            return candidate
        }
        return if (base[0] in SINGLE_ONSETS) base.substring(0, 1) else ""
    }

    private fun codaOf(rest: String): String {
        for (candidate in LONG_CODAS) {
            if (rest.endsWith(candidate)) return candidate
        }
        return if (rest.last() in SINGLE_CODAS) rest.takeLast(1) else ""
    }
}
