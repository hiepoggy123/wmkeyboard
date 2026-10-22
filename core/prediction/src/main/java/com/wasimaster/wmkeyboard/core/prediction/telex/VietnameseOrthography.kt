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

    private val VIETNAMESE_VOWELS = "aeiouyăâêôơư"

    /**
     * Fast zero-allocation validator for valid Vietnamese consonant pairs.
     */
    fun isValidConsonantPair(a: Char, b: Char): Boolean {
        return when (a) {
            'c' -> b == 'h'
            'g' -> b == 'h' || b == 'i'
            'k' -> b == 'h'
            'n' -> b == 'h' || b == 'g'
            'p' -> b == 'h'
            'q' -> b == 'u'
            't' -> b == 'h' || b == 'r'
            else -> false
        }
    }

    /**
     * Gboard-style phonotactic prefix validator.
     * Prunes impossible Vietnamese consonant clusters and invalid onsets early during Beam Search.
     */
    fun isValidVietnamesePrefix(prefix: CharSequence): Boolean {
        val len = prefix.length
        if (len == 0) return true
        val c0 = prefix[0].lowercaseChar()
        if (c0 in "fjwz") return false
        if (len == 1) return true

        val c1 = prefix[1].lowercaseChar()
        when (c0) {
            'q' -> if (c1 != 'u') return false
            'k' -> if (c1 != 'h' && c1 !in "ieyê") return false
            'c' -> if (c1 != 'h' && c1 !in "aouăâôơư") return false
            'g' -> if (c1 != 'h' && c1 != 'i' && c1 !in "aouăâôơư") return false
            't' -> if (c1 != 'h' && c1 != 'r' && c1 !in VIETNAMESE_VOWELS) return false
            'n' -> if (c1 != 'g' && c1 != 'h' && c1 !in VIETNAMESE_VOWELS) return false
            'p' -> if (c1 != 'h' && c1 !in VIETNAMESE_VOWELS) return false
            'b', 'd', 'đ', 'h', 'l', 'm', 'r', 's', 'v', 'x' -> {
                if (c1 !in VIETNAMESE_VOWELS) return false
            }
        }
        if (len == 2) return true

        val c2 = prefix[2].lowercaseChar()
        if (c0 == 'n' && c1 == 'g') {
            if (c2 != 'h' && c2 !in VIETNAMESE_VOWELS) return false
        } else if (c1 == 'h' || c1 == 'r') {
            if (c2 !in VIETNAMESE_VOWELS) return false
        } else if (c0 == 'q' && c1 == 'u') {
            if (c2 !in VIETNAMESE_VOWELS) return false
        }

        // Check for impossible consonant clusters in single syllables (zero allocation)
        for (i in 0 until len - 1) {
            val a = prefix[i].lowercaseChar()
            val b = prefix[i + 1].lowercaseChar()
            if (a !in VIETNAMESE_VOWELS && b !in VIETNAMESE_VOWELS) {
                if (!isValidConsonantPair(a, b)) {
                    return false
                }
            }
        }
        return true
    }

    // Valid unaccented Vowel Cores / Rhymes
    private val VOWEL_CORES = hashSetOf(
        // Đơn
        "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y",
        // Đôi & Ba
        "ai", "ao", "au", "ay", "ây", "âu",
        "eo", "êu",
        "ia", "iê", "iu", "iêu",
        "oa", "oai", "oay", "oă", "oe", "oi", "ôi", "ơi",
        "ua", "uâ", "uay", "uây", "uô", "uôi", "uơ", "uê", "ui", "uy", "uya", "uyê", "uyu",
        "ưa", "ươ", "ưu", "ươi", "ươu",
        "ya", "yê", "yêu"
    )

    // Open-only vowels and diphthongs/triphthongs ending in off-glides (i, y, o, u) that cannot accompany a coda consonant
    private val OPEN_ONLY_VOWELS = hashSetOf(
        "ia", "ua", "ưa", "ya", "uơ",
        "ai", "ao", "au", "ay", "ây", "âu",
        "eo", "êu",
        "iu", "iêu", "yêu",
        "oi", "ôi", "ơi",
        "ui", "ưi", "ưu", "ươi", "ươu",
        "uôi", "oai", "oay", "uay", "uây", "uya", "uyu"
    )

    // Static candidate arrays to prevent garbage collection churn on every keystroke
    private val CANDIDATE_ONSETS = arrayOf("ngh", "ng", "nh", "ch", "gh", "kh", "ph", "th", "tr", "qu", "gi")
    private val CANDIDATE_CODAS = arrayOf("ng", "nh", "ch")

    // Concurrent cache for syllable validation to avoid repeated expensive Normalizer.normalize calls
    private val syllableValidityCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>(4096)

    /**
     * Checks if a word contains Vietnamese diacritical marks or tone accents.
     */
    fun hasVietnameseMark(word: String): Boolean {
        if (word.isEmpty()) return false
        val lower = word.lowercase()
        for (ch in lower) {
            if (ch !in 'a'..'z' && (ch.isLetter() || ch in '\u0300'..'\u036f' || ch in '\u1dc0'..'\u1dff')) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if a decomposed syllable is phonotactically valid in Vietnamese.
     */
    fun isValidVietnameseSyllable(word: String): Boolean {
        if (word.isBlank()) return false
        val clean = word.trim().lowercase()
        return syllableValidityCache.computeIfAbsent(clean) { computeIsValidVietnameseSyllable(it) }
    }

    private fun computeIsValidVietnameseSyllable(clean: String): Boolean {
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
        for (candidateOnset in CANDIDATE_ONSETS) {
            if (baseWord.startsWith(candidateOnset)) {
                // In Vietnamese orthography, when 'gi' is followed by coda consonants or end of word (e.g. "gì", "gìn"),
                // 'i' acts as the vowel nucleus, so the consonant onset is 'g'.
                // If followed by another vowel (e.g. "gió", "giúp", "giường", "giếng"), the onset is "gi".
                if (candidateOnset == "gi" && (baseWord.length == 2 || baseWord[2] !in "aeiouy\u0103\u00e2\u00ea\u00f4\u01a1\u01b0")) {
                    onset = "g"
                } else {
                    onset = candidateOnset
                }
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
        for (candidateCoda in CANDIDATE_CODAS) {
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

        // In Vietnamese phonotactics, open-only diphthongs (ia, ua, ưa) and vowels ending in off-glides cannot take codas
        if (coda.isNotEmpty() && OPEN_ONLY_VOWELS.contains(vowelPart)) return false

        // Validate Tone Phonotactics on Stop Codas (c, ch, p, t can ONLY have Acute or Dot tone)
        if (STOP_CODAS.contains(coda)) {
            if (toneMark != null && toneMark != '\u0301' && toneMark != '\u0323') {
                return false // Grave, Hook, Tilde cannot accompany stop codas
            }
        }

        return true
    }
}
