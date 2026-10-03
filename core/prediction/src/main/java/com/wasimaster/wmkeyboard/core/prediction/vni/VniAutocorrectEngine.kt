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
 * 2. Left-Right Key Slips on Standard QWERTY Layout:
 *    Only considers horizontal slip errors (mispressing adjacent keys left or right on the
 *    standard QWERTY rows: Q-W-E-R..., A-S-D-F..., Z-X-C-V...). The top number row of VNI is
 *    completely skipped/ignored for key slips.
 * 3. Double-Tap Number Cancel Recovery: Recovers from accidental repeated digit cancellation (e.g. "viet655" -> "việt").
 * 4. Strict Input Mode Separation:
 *    - If user did NOT type any digit: User is writing English or unaccented Vietnamese.
 *      Candidates MUST NOT have any Vietnamese diacritics / tone marks.
 *    - If user DID type digits: User is writing accented Vietnamese.
 *      Candidates MUST be accented Vietnamese words (chữ tiếng Việt có dấu hoặc nguyên âm có dấu).
 * 5. Direct Language Model Scoring: Evaluates directly against Vietnamese unigrams & n-grams.
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

        // Standard QWERTY horizontal (left-right) neighbors on the 3 letter rows.
        // Hàng số trên cùng của VNI hoàn toàn được bỏ qua; chỉ xét bấm lệch trái phải trên layout QWERTY tiêu chuẩn.
        val QWERTY_HORIZONTAL_NEIGHBORS: Map<Char, List<Char>> = mapOf(
            // Row 1: q w e r t y u i o p
            'q' to listOf('w'),
            'w' to listOf('q', 'e'),
            'e' to listOf('w', 'r'),
            'r' to listOf('e', 't'),
            't' to listOf('r', 'y'),
            'y' to listOf('t', 'u'),
            'u' to listOf('y', 'i'),
            'i' to listOf('u', 'o'),
            'o' to listOf('i', 'p'),
            'p' to listOf('o'),

            // Row 2: a s d f g h j k l
            'a' to listOf('s'),
            's' to listOf('a', 'd'),
            'd' to listOf('s', 'f'),
            'f' to listOf('d', 'g'),
            'g' to listOf('f', 'h'),
            'h' to listOf('g', 'j'),
            'j' to listOf('h', 'k'),
            'k' to listOf('j', 'l'),
            'l' to listOf('k'),

            // Row 3: z x c v b n m
            'z' to listOf('x'),
            'x' to listOf('z', 'c'),
            'c' to listOf('x', 'v'),
            'v' to listOf('c', 'b'),
            'b' to listOf('v', 'n'),
            'n' to listOf('b', 'm'),
            'm' to listOf('n')
        )

        val VIETNAMESE_ACCENTED_CHARS: Set<Char> = setOf(
            'à', 'á', 'ả', 'ã', 'ạ',
            'ă', 'ằ', 'ắ', 'ẳ', 'ẵ', 'ặ',
            'â', 'ầ', 'ấ', 'ẩ', 'ẫ', 'ậ',
            'è', 'é', 'ẻ', 'ẽ', 'ẹ',
            'ê', 'ề', 'ế', 'ể', 'ễ', 'ệ',
            'ì', 'í', 'ỉ', 'ĩ', 'ị',
            'ò', 'ó', 'ỏ', 'õ', 'ọ',
            'ô', 'ồ', 'ố', 'ổ', 'ỗ', 'ộ',
            'ơ', 'ờ', 'ớ', 'ở', 'ỡ', 'ợ',
            'ù', 'ú', 'ủ', 'ũ', 'ụ',
            'ư', 'ừ', 'ứ', 'ử', 'ữ', 'ự',
            'ỳ', 'ý', 'ỷ', 'ỹ', 'ỵ',
            'đ'
        )

        fun hasVietnameseDiacritics(word: String): Boolean {
            val lower = word.lowercase()
            for (ch in lower) {
                if (ch in VIETNAMESE_ACCENTED_CHARS || ch in '\u0300'..'\u036f' || ch in '\u1dc0'..'\u1dff') {
                    return true
                }
            }
            return false
        }
    }

    /**
     * Resolves VNI autocorrect candidates directly from raw VNI typed buffer.
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
        val hasDigits = rawLower.any { it.isDigit() }

        fun evaluateCandidate(variant: String, penalty: Double) {
            val candidateWord = composer(variant)
            val lowerWord = candidateWord.lowercase()
            if (lowerWord.isEmpty() || lowerWord == originalComposed.lowercase()) return

            // Rule:
            // 1. If user typed numbers -> must be accented Vietnamese word (có dấu hoặc nguyên âm có dấu),
            //    and cannot have leftover digits.
            // 2. If user did NOT type numbers -> must be English or unaccented Vietnamese (không có dấu).
            if (hasDigits) {
                if (!hasVietnameseDiacritics(candidateWord)) return
                if (candidateWord.any { it.isDigit() }) return
            } else {
                if (hasVietnameseDiacritics(candidateWord)) return
                if (candidateWord.any { it.isDigit() }) return
            }

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

        if (!hasDigits) {
            // Case A: User typed NO numbers -> Writing English or Vietnamese without accents.
            // Only evaluate horizontal left-right slips on standard QWERTY letter keys.
            val chars = rawLower.toCharArray()
            for (i in chars.indices) {
                val ch = chars[i]
                val neighbors = QWERTY_HORIZONTAL_NEIGHBORS[ch] ?: continue
                for (nb in neighbors) {
                    chars[i] = nb
                    evaluateCandidate(String(chars), 0.40)
                    chars[i] = ch
                }
            }
        } else {
            // Case B: User typed numbers -> Writing Vietnamese with accents.
            val chars = rawLower.toCharArray()

            // 1. Hand timing / Bimanual Desync: swap adjacent digit and letter
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

            // 2. Misplaced diacritic digits inside syllable (e.g. "vi6et5" -> "viet65", "to1an" -> "toan1")
            val lettersOnly = rawLower.filter { !it.isDigit() }
            val digitsOnly = rawLower.filter { it.isDigit() }
            if (lettersOnly.isNotEmpty() && digitsOnly.isNotEmpty()) {
                evaluateCandidate(lettersOnly + digitsOnly, 0.12)
                // Marks before tone: e.g. 6,7,8 before 1..5: "viet56" -> "viet65"
                val marksFirst = digitsOnly.toList().sortedBy { if (it in '1'..'5') 1 else 0 }.joinToString("")
                if (marksFirst != digitsOnly) {
                    evaluateCandidate(lettersOnly + marksFirst, 0.14)
                }
                if (digitsOnly.length >= 2) {
                    evaluateCandidate(lettersOnly + digitsOnly.reversed(), 0.15)
                }
            }

            // 3. Double-tap number cancel recovery (e.g. "viet655" -> "viet65")
            for (i in 0 until chars.size - 1) {
                if (chars[i].isDigit() && chars[i] == chars[i + 1]) {
                    val dedupped = rawLower.removeRange(i, i + 1)
                    evaluateCandidate(dedupped, 0.25)
                }
            }

            // 4. Standard QWERTY horizontal (left-right) slips on letters
            // (e.g. "thabh2" -> 'b' left-right slip to 'n' -> "thanh2" -> "thành")
            for (i in chars.indices) {
                val ch = chars[i]
                if (ch.isLetter()) {
                    val neighbors = QWERTY_HORIZONTAL_NEIGHBORS[ch] ?: continue
                    for (nb in neighbors) {
                        chars[i] = nb
                        val variant = String(chars)
                        evaluateCandidate(variant, 0.40)

                        // Combine with letters-then-digits if desync also happened
                        val vLetters = variant.filter { !it.isDigit() }
                        val vDigits = variant.filter { it.isDigit() }
                        if (vLetters.isNotEmpty() && vDigits.isNotEmpty()) {
                            evaluateCandidate(vLetters + vDigits, 0.50)
                            val vMarksFirst = vDigits.toList().sortedBy { if (it in '1'..'5') 1 else 0 }.joinToString("")
                            if (vMarksFirst != vDigits) {
                                evaluateCandidate(vLetters + vMarksFirst, 0.52)
                            }
                        }
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
