package com.wasimaster.wmkeyboard.core.prediction.ai

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tokenizer for Vietnamese GPT-2 (21,869 tokens) with support for:
 * - Greedy multi-word tokenization
 * - Context truncation to MAX_SEQUENCE_LEN (32 tokens)
 * - Shorthand / Prefix + Tone filtering
 */
class V7GPTTokenizer {

    companion object {
        const val MAX_SEQUENCE_LEN = 32
        const val VOCAB_SIZE = 21869
        const val BASE_VIET_VOCAB_SIZE = 17788

        val TONE_MAP = mapOf(
            "◌́" to listOf(0),
            "◌̀" to listOf(1),
            "◌̉" to listOf(2),
            "◌̃" to listOf(3),
            "◌̣" to listOf(4, 6),
            "◌" to listOf(5, 7)
        )

        fun extractShorthand(raw: String): Pair<String, String> {
            var tone = ""
            val clean = raw.trim().lowercase()

            if (clean.contains("◌́") || clean.contains("́") || clean.contains("\u0301")) tone = "◌́"
            else if (clean.contains("◌̀") || clean.contains("̀") || clean.contains("\u0300")) tone = "◌̀"
            else if (clean.contains("◌̉") || clean.contains("̉") || clean.contains("\u0309")) tone = "◌̉"
            else if (clean.contains("◌̃") || clean.contains("̃") || clean.contains("\u0303")) tone = "◌̃"
            else if (clean.contains("◌̣") || clean.contains("̣") || clean.contains("\u0323")) tone = "◌̣"
            else if (clean.contains("◌")) tone = "◌"

            // Support Telex tone key on consonant prefix (e.g. "ds" -> "d" + "◌́", "dj" -> "d" + "◌̣")
            if (tone.isEmpty() && clean.length >= 2) {
                val last = clean.last()
                val consonants = "bcdfghjklmnpqrstvwxzđ"
                val prefixPart = clean.dropLast(1)
                if (prefixPart.all { it in consonants }) {
                    when (last) {
                        's' -> tone = "◌́"
                        'f' -> tone = "◌̀"
                        'r' -> tone = "◌̉"
                        'x' -> tone = "◌̃"
                        'j' -> tone = "◌̣"
                    }
                    if (tone.isNotEmpty()) {
                        return Pair(prefixPart, tone)
                    }
                }
            }

            val prefix = clean.filter { it.isLetter() && it != '◌' && it !in "̣́̀̉̃\u0301\u0300\u0309\u0303\u0323" }
            return Pair(prefix, tone)
        }

        private val VOWEL_BASE_MAP = mapOf(
            'á' to 'a', 'à' to 'a', 'ả' to 'a', 'ã' to 'a', 'ạ' to 'a',
            'ắ' to 'ă', 'ằ' to 'ă', 'ẳ' to 'ă', 'ẵ' to 'ă', 'ặ' to 'ă',
            'ấ' to 'â', 'ầ' to 'â', 'ẩ' to 'â', 'ẫ' to 'â', 'ậ' to 'â',
            'é' to 'e', 'è' to 'e', 'ẻ' to 'e', 'ẽ' to 'e', 'ẹ' to 'e',
            'ế' to 'ê', 'ề' to 'ê', 'ể' to 'ê', 'ễ' to 'ê', 'ệ' to 'ê',
            'í' to 'i', 'ì' to 'i', 'ỉ' to 'i', 'ĩ' to 'i', 'ị' to 'i',
            'ó' to 'o', 'ò' to 'o', 'ỏ' to 'o', 'õ' to 'o', 'ọ' to 'o',
            'ố' to 'ô', 'ồ' to 'ô', 'ổ' to 'ô', 'ỗ' to 'ô', 'ộ' to 'ô',
            'ớ' to 'ơ', 'ờ' to 'ơ', 'ở' to 'ơ', 'ỡ' to 'ơ', 'ợ' to 'ơ',
            'ú' to 'u', 'ù' to 'u', 'ủ' to 'u', 'ũ' to 'u', 'ụ' to 'u',
            'ứ' to 'ư', 'ừ' to 'ư', 'ử' to 'ư', 'ữ' to 'ư', 'ự' to 'ư',
            'ý' to 'y', 'ỳ' to 'y', 'ỷ' to 'y', 'ỹ' to 'y', 'ỵ' to 'y'
        )

        private val CHAR_TONE_CODE_MAP = mapOf(
            'á' to 0, 'ắ' to 0, 'ấ' to 0, 'é' to 0, 'ế' to 0, 'í' to 0, 'ó' to 0, 'ố' to 0, 'ớ' to 0, 'ú' to 0, 'ứ' to 0, 'ý' to 0,
            'à' to 1, 'ằ' to 1, 'ầ' to 1, 'è' to 1, 'ề' to 1, 'ì' to 1, 'ò' to 1, 'ồ' to 1, 'ờ' to 1, 'ù' to 1, 'ừ' to 1, 'ỳ' to 1,
            'ả' to 2, 'ẳ' to 2, 'ẩ' to 2, 'ẻ' to 2, 'ể' to 2, 'ỉ' to 2, 'ỏ' to 2, 'ổ' to 2, 'ở' to 2, 'ủ' to 2, 'ử' to 2, 'ỷ' to 2,
            'ã' to 3, 'ẵ' to 3, 'ẫ' to 3, 'ẽ' to 3, 'ễ' to 3, 'ĩ' to 3, 'õ' to 3, 'ỗ' to 3, 'ỡ' to 3, 'ũ' to 3, 'ữ' to 3, 'ỹ' to 3,
            'ạ' to 4, 'ặ' to 4, 'ậ' to 4, 'ẹ' to 4, 'ệ' to 4, 'ị' to 4, 'ọ' to 4, 'ộ' to 4, 'ợ' to 4, 'ụ' to 4, 'ự' to 4, 'ỵ' to 4
        )

        fun getVowelBase(c: Char): Char = VOWEL_BASE_MAP[c.lowercaseChar()] ?: c.lowercaseChar()

        fun getToneCodeOfChar(c: Char): Int? = CHAR_TONE_CODE_MAP[c.lowercaseChar()]

        fun normalizeChar(char: Char): Char {
            return when (char.lowercaseChar()) {
                'đ' -> 'd'
                'ă', 'ắ', 'ằ', 'ẳ', 'ẵ', 'ặ', 'â', 'ấ', 'ầ', 'ẩ', 'ẫ', 'ậ', 'á', 'à', 'ả', 'ã', 'ạ' -> 'a'
                'ê', 'ế', 'ề', 'ể', 'ễ', 'ệ', 'é', 'è', 'ẻ', 'ẽ', 'ẹ' -> 'e'
                'í', 'ì', 'ỉ', 'ĩ', 'ị' -> 'i'
                'ô', 'ố', 'ồ', 'ổ', 'ỗ', 'ộ', 'ơ', 'ớ', 'ờ', 'ở', 'ỡ', 'ợ', 'ó', 'ò', 'ỏ', 'õ', 'ọ' -> 'o'
                'ư', 'ứ', 'ừ', 'ử', 'ữ', 'ự', 'ú', 'ù', 'ủ', 'ũ', 'ụ' -> 'u'
                'ý', 'ỳ', 'ỷ', 'ỹ', 'ỵ' -> 'y'
                else -> char.lowercaseChar()
            }
        }

        fun toneCodeToMark(code: Int): String {
            return when (code) {
                0 -> "◌́"
                1 -> "◌̀"
                2 -> "◌̉"
                3 -> "◌̃"
                4, 6 -> "◌̣"
                else -> "◌"
            }
        }
    }

    val enumDict = HashMap<String, Int>(VOCAB_SIZE)
    val renumList = ArrayList<String?>(VOCAB_SIZE + 1)
    val renumToneList = ArrayList<Int?>(VOCAB_SIZE + 1)
    val renumToneMark = ArrayList<String?>(VOCAB_SIZE + 1)

    var isLoaded = false
        private set

    fun load(
        enumStream: InputStream,
        tokenStringsStream: InputStream,
        tokenTonesStream: InputStream
    ) {
        val enumBytes = enumStream.readBytes()
        val tokenBytes = tokenStringsStream.readBytes()
        val toneBytes = tokenTonesStream.readBytes()

        // 1. Load enum_21869.bin
        val enumBuf = ByteBuffer.wrap(enumBytes).order(ByteOrder.LITTLE_ENDIAN)
        val enumCount = enumBuf.int
        enumDict.clear()
        renumList.clear()
        while (renumList.size <= VOCAB_SIZE + 1000) {
            renumList.add(null)
        }

        for (i in 0 until enumCount) {
            val len = enumBuf.short.toInt() and 0xFFFF
            val strBytes = ByteArray(len)
            enumBuf.get(strBytes)
            val token = String(strBytes, Charsets.UTF_8)
            val id = enumBuf.int
            enumDict[token] = id
            if (id in 1 until renumList.size) {
                renumList[id] = token
            }
        }

        // 2. Load token_strings_21869.bin (fill any remaining slots)
        val tokenBuf = ByteBuffer.wrap(tokenBytes).order(ByteOrder.LITTLE_ENDIAN)
        val tokenCount = tokenBuf.int
        for (i in 0 until tokenCount) {
            val len = tokenBuf.short.toInt() and 0xFFFF
            if (len > 0) {
                val strBytes = ByteArray(len)
                tokenBuf.get(strBytes)
                val token = String(strBytes, Charsets.UTF_8)
                val idx = i + 1
                if (idx < renumList.size && renumList[idx] == null) {
                    renumList[idx] = token
                }
            }
        }

        // 3. Load token_tones.bin
        val toneBuf = ByteBuffer.wrap(toneBytes).order(ByteOrder.LITTLE_ENDIAN)
        val toneCount = toneBuf.int
        renumToneList.clear()
        renumToneMark.clear()
        renumToneList.add(null) // index 0 is reserved
        renumToneMark.add(null)

        for (i in 0 until toneCount) {
            val code = toneBuf.get().toInt() and 0xFF
            renumToneList.add(code)
            renumToneMark.add(toneCodeToMark(code))
        }

        isLoaded = true
    }

    /**
     * Greedy forward tokenization matching the swift tokenizer.
     */
    fun tokenize(text: String): LongArray {
        val words = text.lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val ids = ArrayList<Long>()

        var i = 0
        while (i < words.size) {
            // Try matching longest multi-word token first (up to 4 words)
            var matched = false
            for (len in minOf(4, words.size - i) downTo 1) {
                val phrase = words.subList(i, i + len).joinToString(" ")
                val id = enumDict[phrase]
                if (id != null && id in 1..BASE_VIET_VOCAB_SIZE) {
                    ids.add(id.toLong())
                    i += len
                    matched = true
                    break
                }
            }
            if (!matched) {
                // If word not in base vocab, try single-char token if available in base vocab
                val singleWord = words[i]
                val singleId = enumDict[singleWord]
                if (singleId != null && singleId in 1..BASE_VIET_VOCAB_SIZE) {
                    ids.add(singleId.toLong())
                }
                i++
            }
        }

        // Truncate to MAX_SEQUENCE_LEN (32 tokens)
        val startIdx = maxOf(0, ids.size - MAX_SEQUENCE_LEN)
        val result = LongArray(ids.size - startIdx)
        for (j in startIdx until ids.size) {
            result[j - startIdx] = ids[j]
        }
        return result
    }

    /**
     * Filter candidates from model predictions by prefix and optional tone.
     */
    fun filter(
        pattern: String,
        predictionIds: IntArray,
        toneMark: String = "",
        maxResults: Int = 10
    ): List<String> {
        val result = ArrayList<String>(maxResults)
        val cleanPattern = pattern.trim().lowercase()

        val maxIterate = if (toneMark.isNotEmpty()) VOCAB_SIZE else 2048 * 3
        var iterate = 0

        for (idx in predictionIds) {
            iterate++
            if (iterate > maxIterate) break
            if (idx >= renumList.size) continue
            val word = renumList[idx] ?: continue

            if (isMatch(cleanPattern, word, idx, toneMark)) {
                result.add(word)
                if (result.size >= maxResults) break
            }
        }
        return result
    }

    fun isMatch(
        pattern: String,
        word: String,
        idx: Int,
        toneMark: String
    ): Boolean {
        val cleanWord = word.lowercase()
        val cleanPattern = pattern.lowercase()

        // 1. Tone check from explicit toneMark argument
        if (toneMark.isNotEmpty()) {
            if (idx < 1 || idx >= renumToneMark.size) return false
            if (renumToneMark[idx] != toneMark) return false
        }

        // 2. Embedded tone check from characters in pattern (e.g. "tiể" -> Hỏi, "tiệ" -> Nặng)
        val embeddedTone = cleanPattern.firstNotNullOfOrNull { getToneCodeOfChar(it) }
        if (embeddedTone != null) {
            val wordToneCode = renumToneList.getOrNull(idx) ?: 5
            if (embeddedTone == 4) {
                if (wordToneCode != 4 && wordToneCode != 6) return false
            } else if (embeddedTone != wordToneCode) {
                return false
            }
        }

        if (cleanPattern.isEmpty()) return true
        if (cleanWord.length < cleanPattern.length) return false

        // 3. Character comparison
        for (i in cleanPattern.indices) {
            val p = cleanPattern[i]
            val w = cleanWord[i]

            if (p == 'đ') {
                if (w != 'đ') return false
            } else {
                val pBase = getVowelBase(p)
                val wBase = getVowelBase(w)
                if (p in "ăâêôơư") {
                    if (w != p && getToneCodeOfChar(w) != null) return false
                    if (wBase != p) return false
                } else if (pBase in "ăâêôơư") {
                    if (pBase != wBase) return false
                } else {
                    if (normalizeChar(w) != normalizeChar(p)) return false
                }
            }
        }
        return true
    }
}
