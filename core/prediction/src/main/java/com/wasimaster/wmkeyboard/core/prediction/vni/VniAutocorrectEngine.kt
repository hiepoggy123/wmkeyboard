package com.wasimaster.wmkeyboard.core.prediction.vni

import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.prediction.telex.TelexAutocorrectEngine

/**
 * Data class representing a correction candidate for Vietnamese VNI input.
 */
data class VniCorrectionCandidate(
    val word: String,
    val raw: String,
    val penalty: Double,
    val score: Double
) : Comparable<VniCorrectionCandidate> {
    override fun compareTo(other: VniCorrectionCandidate): Int {
        return other.score.compareTo(this.score)
    }
}

/**
 * DEDICATED VNI AUTOCORRECT ENGINE
 *
 * Implements native, high-performance error correction designed specifically for VNI:
 * 1. Bimanual Typing Desynchronization: Fixes Left-Right hand speed differences where
 *    VNI digits (e.g. '6', '7', '8', '9', '1'..'5') are hit before their intended vowels
 *    (e.g. "vi6et5" -> "vie6t5" -> "việt", "vi6e5t" -> "vie6t5" -> "việt", "to1an" -> "toán").
 * 2. Number Row Horizontal Slips: Corrects adjacent digit fat-finger errors
 *    (e.g. "viet64" -> "viet65" -> "việt", "duong71" -> "duong72" -> "đường").
 * 3. Vertical Letter-to-Digit Slips: Corrects reaches where number row was intended but
 *    Row 1 letter key was hit (e.g. "viett6" -> "việt", "vieyt5" -> "việt", "thaw" -> "thà", "tee" -> "tẻ").
 * 4. Double-Tap Number Cancel Recovery: Recovers from accidental repeated digit cancellation (e.g. "viet655" -> "việt").
 * 5. QWERTY Consonant/Vowel Proximity: Fixes typos on physical adjacent letter keys (e.g. "thabh2" -> "thành").
 * 6. Direct Language Model Scoring: Evaluates directly against Vietnamese unigrams & n-grams with 0 intermediate conversion.
 */
class VniAutocorrectEngine private constructor() {

    companion object {
        @Volatile
        private var instance: VniAutocorrectEngine? = null

        fun getInstance(): VniAutocorrectEngine {
            return instance ?: synchronized(this) {
                instance ?: VniAutocorrectEngine().also { instance = it }
            }
        }

        private const val WEIGHT_PENALTY = 200.0
        private const val WEIGHT_UNIGRAM = 1.0
        private const val WEIGHT_BIGRAM = 2.5
        private const val WEIGHT_TRIGRAM = 5.0
        private const val WEIGHT_USER_UNIGRAM = 3.0
        private const val WEIGHT_USER_BIGRAM = 6.0

        // Vertical slips: letter on Row 1 reaching up to/down from Number Row directly above
        val VNI_VERTICAL_LETTER_TO_DIGIT: Map<Char, Char> = mapOf(
            'q' to '1', 'w' to '2', 'e' to '3', 'r' to '4', 't' to '5',
            'y' to '6', 'u' to '7', 'i' to '8', 'o' to '9', 'p' to '0'
        )

        // Vertical slips: digit on Number Row reaching down into Row 1 letter key directly below
        val VNI_VERTICAL_DIGIT_TO_LETTER: Map<Char, Char> = mapOf(
            '1' to 'q', '2' to 'w', '3' to 'e', '4' to 'r', '5' to 't',
            '6' to 'y', '7' to 'u', '8' to 'i', '9' to 'o', '0' to 'p'
        )

        // Horizontal slips on number row: adjacent digits
        val VNI_DIGIT_NEIGHBORS: Map<Char, List<Char>> = mapOf(
            '1' to listOf('2'),
            '2' to listOf('1', '3'),
            '3' to listOf('2', '4'),
            '4' to listOf('3', '5'),
            '5' to listOf('4', '6'),
            '6' to listOf('5', '7'),
            '7' to listOf('6', '8'),
            '8' to listOf('7', '9'),
            '9' to listOf('8', '0'),
            '0' to listOf('9')
        )
    }

    /**
     * Resolves all VNI errors (desync, slips, letter typos) directly from raw VNI typed buffer.
     */
    fun correct(
        typed: String,
        originalComposed: String,
        previousWord: String? = null,
        previousWord2: String? = null,
        userLexicon: UserLexicon? = null,
        composer: (String) -> String,
        maxResults: Int = 5
    ): List<VniCorrectionCandidate> {
        val telexEngine = TelexAutocorrectEngine.getInstance()
        if (!telexEngine.isReady || typed.length < 2) return emptyList()

        val cleanPrev = previousWord?.trim()?.lowercase()
        val cleanPrev2 = previousWord2?.trim()?.lowercase()
        val candidates = ArrayList<VniCorrectionCandidate>()
        val seenWords = HashSet<String>()
        val rawLower = typed.lowercase()

        fun evaluateCandidate(variant: String, penalty: Double) {
            val candidateWord = composer(variant)
            val lowerWord = candidateWord.lowercase()
            if (lowerWord.isEmpty() || lowerWord == originalComposed.lowercase()) return
            // Must not contain leftover raw digits unless original also had them
            if (candidateWord.any { it.isDigit() } && !originalComposed.any { it.isDigit() }) return

            val isDict = telexEngine.isWordInDictionary(candidateWord)
            val userCount = userLexicon?.frequencyOf(candidateWord) ?: 0
            if (!isDict && userCount <= 0) return

            if (seenWords.add(lowerWord)) {
                val baseUnigram = telexEngine.languageModel.getUnigramScore(candidateWord)
                var baseBigram = 0
                var baseTrigram = 0
                if (!cleanPrev.isNullOrEmpty()) {
                    baseBigram = telexEngine.languageModel.getBigramScore(cleanPrev, candidateWord)
                    if (!cleanPrev2.isNullOrEmpty()) {
                        baseTrigram = telexEngine.languageModel.getTrigramScore(cleanPrev2, cleanPrev, candidateWord)
                    }
                }
                val userBigramCount = if (!cleanPrev.isNullOrEmpty()) {
                    userLexicon?.bigramCount(cleanPrev, candidateWord) ?: 0
                } else 0

                val totalScore = - (penalty * WEIGHT_PENALTY) +
                        (baseUnigram * WEIGHT_UNIGRAM) +
                        (baseBigram * WEIGHT_BIGRAM) +
                        (baseTrigram * WEIGHT_TRIGRAM) +
                        (userCount * WEIGHT_USER_UNIGRAM) +
                        (userBigramCount * WEIGHT_USER_BIGRAM)

                candidates.add(
                    VniCorrectionCandidate(
                        word = applyCasing(candidateWord, typed),
                        raw = variant,
                        penalty = penalty,
                        score = totalScore
                    )
                )
            }
        }

        // 1. Bimanual Desync: Hand timing errors (e.g. "vi6et5" -> "vie6t5", "to1an" -> "toa1n")
        val chars = rawLower.toCharArray()
        // 1a. Swap adjacent digit and letter
        for (i in 0 until chars.size - 1) {
            val c1 = chars[i]
            val c2 = chars[i + 1]
            if (c1.isDigit() != c2.isDigit()) {
                chars[i] = c2
                chars[i + 1] = c1
                evaluateCandidate(String(chars), 0.10)
                chars[i] = c1
                chars[i + 1] = c2
            }
        }

        // 1b. Misplaced VNI diacritic digits (e.g. digits typed early inside syllable: "vi6et5" -> "viet65", "to1an" -> "toan1")
        val letterIndices = ArrayList<Int>()
        val digitIndices = ArrayList<Int>()
        for (i in rawLower.indices) {
            if (rawLower[i].isDigit()) digitIndices.add(i) else letterIndices.add(i)
        }
        if (digitIndices.isNotEmpty() && letterIndices.isNotEmpty()) {
            val lettersOnly = rawLower.filter { !it.isDigit() }
            val digitsOnly = rawLower.filter { it.isDigit() }
            // Try letters followed by digits: e.g. "vi6et5" -> "viet65", "vi6e5t" -> "viet65"
            evaluateCandidate(lettersOnly + digitsOnly, 0.12)
            // Try diacritic marks before tone marks (6,7,8,9 before 1..5): e.g. "viet56" -> "viet65"
            val marksFirst = digitsOnly.toList().sortedBy { if (it in '1'..'5') 1 else 0 }.joinToString("")
            if (marksFirst != digitsOnly) {
                evaluateCandidate(lettersOnly + marksFirst, 0.14)
            }
            // Try digits reversed if multiple: e.g. "viet56" -> "viet65"
            if (digitsOnly.length >= 2) {
                evaluateCandidate(lettersOnly + digitsOnly.reversed(), 0.15)
            }
        }

        // 2. Horizontal Digit Slips on Number Row (e.g. "viet64" -> "viet65", "duong71" -> "duong72")
        for (i in chars.indices) {
            val ch = chars[i]
            if (ch.isDigit()) {
                val neighbors = VNI_DIGIT_NEIGHBORS[ch] ?: continue
                for (n in neighbors) {
                    chars[i] = n
                    evaluateCandidate(String(chars), 0.40)
                    // Combine with end digits if bimanual desync also occurred
                    val lettersOnly = String(chars).filter { !it.isDigit() }
                    val digitsOnly = String(chars).filter { it.isDigit() }
                    evaluateCandidate(lettersOnly + digitsOnly, 0.50)
                    val marksFirst = digitsOnly.toList().sortedBy { if (it in '1'..'5') 1 else 0 }.joinToString("")
                    if (marksFirst != digitsOnly) {
                        evaluateCandidate(lettersOnly + marksFirst, 0.50)
                    }
                }
                chars[i] = ch
            }
        }

        // 3. Vertical Slips between Number Row and Row 1 Letters
        // 3a. Letter-to-Digit: e.g. "viett6" -> "viet56", "vieyt5" -> "viet65", "thaw" -> "tha2", "tee" -> "te3"
        for (i in chars.indices) {
            val ch = chars[i]
            val digit = VNI_VERTICAL_LETTER_TO_DIGIT[ch]
            if (digit != null) {
                chars[i] = digit
                evaluateCandidate(String(chars), 0.45)
                // Combine with end digits
                val lettersOnly = String(chars).filter { !it.isDigit() }
                val digitsOnly = String(chars).filter { it.isDigit() }
                evaluateCandidate(lettersOnly + digitsOnly, 0.55)
                val marksFirst = digitsOnly.toList().sortedBy { if (it in '1'..'5') 1 else 0 }.joinToString("")
                if (marksFirst != digitsOnly) {
                    evaluateCandidate(lettersOnly + marksFirst, 0.55)
                }
                chars[i] = ch
            }
        }
        // 3b. Digit-to-Letter: e.g. "vie55" -> "viet5" ("việt")
        for (i in chars.indices) {
            val ch = chars[i]
            val letter = VNI_VERTICAL_DIGIT_TO_LETTER[ch]
            if (letter != null) {
                chars[i] = letter
                evaluateCandidate(String(chars), 0.45)
                val lettersOnly = String(chars).filter { !it.isDigit() }
                val digitsOnly = String(chars).filter { it.isDigit() }
                if (digitsOnly.isNotEmpty()) {
                    evaluateCandidate(lettersOnly + digitsOnly, 0.55)
                    val marksFirst = digitsOnly.toList().sortedBy { if (it in '1'..'5') 1 else 0 }.joinToString("")
                    if (marksFirst != digitsOnly) {
                        evaluateCandidate(lettersOnly + marksFirst, 0.55)
                    }
                }
                chars[i] = ch
            }
        }

        // 4. Double-Tap Number Cancel Slips (e.g. "viet655" -> "viet65")
        for (i in 0 until chars.size - 1) {
            if (chars[i].isDigit() && chars[i] == chars[i + 1]) {
                val dedupped = rawLower.removeRange(i, i + 1)
                evaluateCandidate(dedupped, 0.30)
            }
        }

        // 5. QWERTY Physical Proximity on Letters (e.g. "thabh2" -> "thanh2")
        for (i in chars.indices) {
            val ch = chars[i]
            if (ch.isLetter()) {
                val neighbors = telexEngine.proximityManager.getNeighbors(ch)
                for (nb in neighbors) {
                    if (nb.key != ch) {
                        chars[i] = nb.key
                        evaluateCandidate(String(chars), 0.60 + nb.penalty)
                        chars[i] = ch
                    }
                }
            }
        }

        candidates.sort()
        return if (candidates.size > maxResults) candidates.subList(0, maxResults) else candidates
    }

    private fun applyCasing(word: String, originalInput: String): String {
        if (word.isEmpty() || originalInput.isEmpty()) return word
        val isAllUpper = originalInput.all { !it.isLetter() || it.isUpperCase() }
        if (isAllUpper && originalInput.any { it.isLetter() }) return word.uppercase()
        val firstLetter = originalInput.firstOrNull { it.isLetter() }
        if (firstLetter != null && firstLetter.isUpperCase()) {
            return word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return word
    }
}
