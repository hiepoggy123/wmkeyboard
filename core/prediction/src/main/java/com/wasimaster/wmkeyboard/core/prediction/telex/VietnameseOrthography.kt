package com.wasimaster.wmkeyboard.core.prediction.telex

import java.text.Normalizer

/**
 * Vietnamese Orthography & Phonotactic Validator (OpenKey principle).
 * Validates syllable structures, vowel-consonant clusters, and tone constraints.
 */
object VietnameseOrthography {

    // Valid Vietnamese Onsets (Phụ âm đầu)
    private val ONSETS = hashSetOf(
        "", "b", "c", "ch", "d", "đ", "g", "gh", "gi", "h", "k", "kh",
        "l", "m", "n", "ng", "ngh", "nh", "p", "ph", "qu", "r", "s",
        "t", "th", "tr", "v", "x"
    )

    // Valid Vietnamese Codas (Phụ âm cuối)
    private val CODAS = hashSetOf(
        "", "c", "ch", "m", "n", "ng", "nh", "p", "t"
    )

    // Closed / Stop codas (Phụ âm tắc - chỉ đi với dấu Sắc hoặc Nặng)
    private val STOP_CODAS = hashSetOf("c", "ch", "p", "t")

    // Valid unaccented Vowel Cores / Rhymes
    private val VOWEL_CORES = hashSetOf(
        // Đơn
        "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y",
        // Đôi & Ba
        "ai", "ao", "au", "ay", "ây",
        "eo", "êu",
        "ia", "iê", "iu",
        "oa", "oă", "oe", "oi", "ôi", "ơi",
        "ua", "uâ", "uô", "uơ", "ui", "uy", "uya", "uyê", "uyu",
        "ưa", "ươ", "ưu", "ươi", "ươu",
        "ya", "yê"
    )

    /**
     * Checks if a decomposed syllable is phonotactically valid in Vietnamese.
     */
    fun isValidVietnameseSyllable(word: String): Boolean {
        if (word.isBlank()) return false
        val clean = word.trim().lowercase()

        // Decompose NFC to NFD to separate base characters and diacritical combining marks
        val nfd = Normalizer.normalize(clean, Normalizer.Form.NFD)

        var toneMark: Char? = null
        val baseSb = StringBuilder()

        for (ch in nfd) {
            when (ch) {
                '\u0301', '\u0300', '\u0309', '\u0303', '\u0323' -> toneMark = ch // Acute, Grave, Hook, Tilde, Dot
                else -> baseSb.append(ch)
            }
        }

        val baseWord = Normalizer.normalize(baseSb.toString(), Normalizer.Form.NFC)
        if (baseWord.isEmpty()) return false

        // Parse: [Onset] + [Vowels] + [Coda]
        var onset = ""
        for (candidateOnset in listOf("ngh", "ng", "nh", "ch", "gh", "kh", "ph", "th", "tr", "qu", "gi")) {
            if (baseWord.startsWith(candidateOnset)) {
                onset = candidateOnset
                break
            }
        }
        if (onset.isEmpty() && baseWord.isNotEmpty() && baseWord[0] in "bcdđghklmnpqrstvx") {
            onset = baseWord.substring(0, 1)
        }

        val remainder = baseWord.substring(onset.length)
        if (remainder.isEmpty()) return false

        // Parse Coda from end
        var coda = ""
        for (candidateCoda in listOf("ng", "nh", "ch")) {
            if (remainder.endsWith(candidateCoda)) {
                coda = candidateCoda
                break
            }
        }
        if (coda.isEmpty() && remainder.isNotEmpty() && remainder.last() in "cmptn") {
            coda = remainder.takeLast(1)
        }

        val vowelPart = remainder.dropLast(coda.length)
        if (vowelPart.isEmpty()) return false

        // Validate Onset
        if (!ONSETS.contains(onset)) return false

        // Validate Coda
        if (!CODAS.contains(coda)) return false

        // Validate Vowel Core
        if (!VOWEL_CORES.contains(vowelPart)) return false

        // Validate Tone Phonotactics on Stop Codas (c, ch, p, t can ONLY have Acute or Dot tone)
        if (STOP_CODAS.contains(coda)) {
            if (toneMark != null && toneMark != '\u0301' && toneMark != '\u0323') {
                return false // Grave, Hook, Tilde cannot accompany stop codas
            }
        }

        return true
    }
}
