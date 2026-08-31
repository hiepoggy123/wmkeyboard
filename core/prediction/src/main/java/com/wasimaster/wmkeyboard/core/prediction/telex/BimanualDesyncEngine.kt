package com.wasimaster.wmkeyboard.core.prediction.telex

/**
 * Bimanual Typing Desynchronization Engine.
 * Fixes keystroke transposition errors caused by speed differences between Left and Right hands.
 */
object BimanualDesyncEngine {

    // QWERTY Left-Hand Keys
    private val LEFT_HAND_KEYS = hashSetOf(
        'q', 'w', 'e', 'r', 't',
        'a', 's', 'd', 'f', 'g',
        'z', 'x', 'c', 'v', 'b'
    )

    // QWERTY Right-Hand Keys
    private val RIGHT_HAND_KEYS = hashSetOf(
        'y', 'u', 'i', 'o', 'p',
        'h', 'j', 'k', 'l',
        'n', 'm'
    )

    /**
     * Determines whether two keys belong to opposite hands (Left vs Right).
     */
    fun isOppositeHand(c1: Char, c2: Char): Boolean {
        val lc1 = c1.lowercaseChar()
        val lc2 = c2.lowercaseChar()
        val isLeft1 = LEFT_HAND_KEYS.contains(lc1)
        val isRight1 = RIGHT_HAND_KEYS.contains(lc1)
        val isLeft2 = LEFT_HAND_KEYS.contains(lc2)
        val isRight2 = RIGHT_HAND_KEYS.contains(lc2)

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

            if (composed != null && (engine.isWordInDictionary(composed) || VietnameseOrthography.isValidVietnameseSyllable(composed))) {
                // Inter-hand swaps get low penalty (0.10), intra-hand gets 0.30
                val penalty = if (isOppositeHand(c1, c2)) 0.10 else 0.30
                val unigramScore = engine.languageModel.getUnigramScore(composed)
                val score = unigramScore.toDouble() / (1.0 + penalty * 5.0)

                results.add(
                    TelexCorrectionCandidate(
                        word = composed,
                        telex = swappedRaw,
                        penalty = penalty,
                        score = score
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
            if (composed != null && (engine.isWordInDictionary(composed) || VietnameseOrthography.isValidVietnameseSyllable(composed))) {
                val unigramScore = engine.languageModel.getUnigramScore(composed)
                results.add(
                    TelexCorrectionCandidate(
                        word = composed,
                        telex = swappedRaw,
                        penalty = 0.12,
                        score = unigramScore.toDouble() / 1.5
                    )
                )
            }
        }

        return results
    }
}
