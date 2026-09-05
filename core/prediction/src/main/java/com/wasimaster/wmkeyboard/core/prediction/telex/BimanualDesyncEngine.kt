package com.wasimaster.wmkeyboard.core.prediction.telex

/**
 * Bimanual Typing Desynchronization Engine.
 * Fixes keystroke transposition errors caused by speed differences between Left and Right hands.
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
     * Generates transposition candidates for a raw Telex input.
     * Evaluates swapped adjacent pairs and returns valid Vietnamese candidates with penalties.
     */
    fun generateCandidates(
        rawInput: String,
        engine: TelexAutocorrectEngine
    ): List<TelexCorrectionCandidate> {
        if (rawInput.length < 2) return emptyList()

        val results = ArrayList<TelexCorrectionCandidate>()
        val rawLower = rawInput.lowercase()

        // 1. Try swapping each adjacent pair (c_i, c_{i+1})
        val chars = rawLower.toCharArray()
        for (i in 0 until chars.size - 1) {
            val c1 = chars[i]
            val c2 = chars[i + 1]
            if (c1 == c2) continue

            // Swap
            chars[i] = c2
            chars[i + 1] = c1
            val swappedRaw = String(chars)

            // Look up in Telex Trie
            val composed = engine.trie.findWord(swappedRaw)

            if (composed != null && engine.isWordInDictionary(composed)) {
                // Inter-hand swaps get low penalty (0.10), intra-hand gets 0.30
                val penalty = if (isOppositeHand(c1, c2)) 0.10 else 0.30

                results.add(
                    TelexCorrectionCandidate(
                        word = composed,
                        telex = swappedRaw,
                        penalty = penalty,
                        score = 0.0 // Computed downstream by TelexAutocorrectEngine
                    )
                )
            }

            // Restore
            chars[i] = c1
            chars[i + 1] = c2
        }

        // 2. Specialized 3-letter onset cluster desync (e.g. gnh -> ngh, nhg -> ngh)
        if (rawLower.startsWith("gnh") || rawLower.startsWith("nhg")) {
            val swappedRaw = "ngh" + rawLower.substring(3)
            val composed = engine.trie.findWord(swappedRaw)
            if (composed != null && engine.isWordInDictionary(composed)) {
                results.add(
                    TelexCorrectionCandidate(
                        word = composed,
                        telex = swappedRaw,
                        penalty = 0.12,
                        score = 0.0
                    )
                )
            }
        }

        return results
    }
}
