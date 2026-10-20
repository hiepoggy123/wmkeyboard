package com.wasimaster.wmkeyboard.core.prediction.telex

/**
 * Bimanual Typing Desynchronization Engine for Vietnamese Telex.
 * Fixes keystroke transposition and timing errors caused by speed differences
 * between Left and Right hands on touchscreens.
 */
object BimanualDesyncEngine {

    // Fast QWERTY Left-Hand vs Right-Hand ASCII tables (zero allocation, no autoboxing)
    private val IS_LEFT_HAND = BooleanArray(128).apply {
        for (c in "qwertasdfgzxcvbQWERTASDFGZXCVB") {
            this[c.code] = true
        }
    }

    private val IS_RIGHT_HAND = BooleanArray(128).apply {
        for (c in "yuiophjklnmYUIOPHJKLNM") {
            this[c.code] = true
        }
    }

    // Vietnamese codas (ordered: longer/2-letter first)
    private val CODAS_2 = listOf("ng", "nh", "ch")
    private val CODAS_1 = listOf("c", "m", "n", "p", "t")

    /**
     * Determines whether two keys belong to opposite hands (Left vs Right).
     */
    fun isOppositeHand(c1: Char, c2: Char): Boolean {
        val code1 = c1.code
        val code2 = c2.code
        val isLeft1 = code1 < 128 && IS_LEFT_HAND[code1]
        val isRight1 = code1 < 128 && IS_RIGHT_HAND[code1]
        val isLeft2 = code2 < 128 && IS_LEFT_HAND[code2]
        val isRight2 = code2 < 128 && IS_RIGHT_HAND[code2]

        return (isLeft1 && isRight2) || (isRight1 && isLeft2)
    }

    /**
     * Generates transposition and timing desynchronization candidates for raw Telex input.
     * Evaluates:
     * 1. Diacritic / tone keys typed before coda consonants (e.g. "tieesng" -> "tieengs" [tiếng], "toasn" -> "toans" [toán]).
     * 2. Swapped adjacent opposite-hand key pairs (e.g. "htaatj" -> "thaatj" [thật], "haonf" -> "hoanf" [hoàn]).
     * 3. Specialized onset cluster desync (e.g. "gnh" / "nhg" -> "ngh").
     * 4. Onset consonant + vowel inversion (e.g. "tehes" -> "thees" [thế], "cehaf" -> "chaaf" [chà]).
     */
    fun generateCandidates(
        rawInput: String,
        engine: TelexAutocorrectEngine
    ): List<TelexCorrectionCandidate> {
        if (rawInput.length < 2) return emptyList()

        val results = ArrayList<TelexCorrectionCandidate>()
        val seenTelex = HashSet<String>()
        val seenWords = HashSet<String>()
        val rawLower = rawInput.lowercase()

        fun tryAdd(telex: String, penalty: Double) {
            if (!seenTelex.add(telex)) return
            val word = engine.trie.findWord(telex)
            if (word != null && engine.isWordInDictionary(word)) {
                if (seenWords.add(word)) {
                    results.add(
                        TelexCorrectionCandidate(
                            word = word,
                            telex = telex,
                            penalty = penalty,
                            score = 0.0 // Computed downstream by TelexAutocorrectEngine
                        )
                    )
                }
            }
        }

        // 1. Diacritic / tone typed before coda (very common bimanual timing error)
        // Hand typing the tone key hits before the opposite hand completes the syllable coda
        for (i in 1 until rawLower.length - 1) {
            val tChar = rawLower[i]
            if (tChar in "sfrxj") {
                val prevChar = rawLower[i - 1]
                if (prevChar in "aeiouyw") {
                    val remainder = rawLower.substring(i + 1)
                    // Check 2-letter codas: "ng", "nh", "ch"
                    for (coda in CODAS_2) {
                        if (remainder.startsWith(coda)) {
                            val prefix = rawLower.substring(0, i)
                            val afterCoda = remainder.substring(coda.length)
                            val variant = prefix + coda + tChar + afterCoda
                            tryAdd(variant, 0.08)
                            if (afterCoda.isNotEmpty()) {
                                tryAdd(prefix + coda + afterCoda + tChar, 0.09)
                                tryAdd(prefix + coda + afterCoda, 0.10)
                            }
                        }
                    }
                    // Check 1-letter codas: "c", "m", "n", "p", "t"
                    for (coda in CODAS_1) {
                        if (remainder.startsWith(coda)) {
                            val prefix = rawLower.substring(0, i)
                            val afterCoda = remainder.substring(coda.length)
                            val variant = prefix + coda + tChar + afterCoda
                            tryAdd(variant, 0.08)
                            if (afterCoda.isNotEmpty()) {
                                tryAdd(prefix + coda + afterCoda + tChar, 0.09)
                            }
                        }
                    }
                }
            }
        }

        // 2. Swapped adjacent opposite-hand pairs
        val chars = rawLower.toCharArray()
        for (i in 0 until chars.size - 1) {
            val c1 = chars[i]
            val c2 = chars[i + 1]
            if (c1 == c2) continue
            if (!isOppositeHand(c1, c2)) continue

            chars[i] = c2
            chars[i + 1] = c1
            val swappedRaw = String(chars)
            tryAdd(swappedRaw, 0.10)
            chars[i] = c1
            chars[i + 1] = c2
        }

        // 3. Specialized 3-letter onset cluster desync (e.g. gnh -> ngh, nhg -> ngh)
        if (rawLower.startsWith("gnh") || rawLower.startsWith("nhg")) {
            val swappedRaw = "ngh" + rawLower.substring(3)
            tryAdd(swappedRaw, 0.10)
        }

        // 4. Onset consonant + vowel inversion (e.g. "tehes" -> "thees" [thế], "cehaf" -> "chaaf" [chà])
        // Left hand hits vowel 'e' or 'a' before Right hand hits onset 'h'
        if (rawLower.length >= 3) {
            val c0 = rawLower[0]
            val c1 = rawLower[1]
            val c2 = rawLower[2]
            if ((c0 == 't' || c0 == 'c' || c0 == 'g' || c0 == 'k' || c0 == 'p') &&
                (c1 in "eaou") && c2 == 'h') {
                val inverted = "$c0$c2$c1" + rawLower.substring(3)
                tryAdd(inverted, 0.12)
            }
        }

        return results
    }
}

