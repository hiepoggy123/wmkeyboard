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
            if (raw.contains("◌́") || raw.contains("́") || raw.contains("\u0301")) tone = "◌́"
            else if (raw.contains("◌̀") || raw.contains("̀") || raw.contains("\u0300")) tone = "◌̀"
            else if (raw.contains("◌̉") || raw.contains("̉") || raw.contains("\u0309")) tone = "◌̉"
            else if (raw.contains("◌̃") || raw.contains("̃") || raw.contains("\u0303")) tone = "◌̃"
            else if (raw.contains("◌̣") || raw.contains("̣") || raw.contains("\u0323")) tone = "◌̣"
            else if (raw.contains("◌")) tone = "◌"

            val prefix = raw.filter { it.isLetter() && it != '◌' && it !in "̣́̀̉̃\u0301\u0300\u0309\u0303\u0323" }
            return Pair(prefix, tone)
        }

        fun normalizeChar(char: Char): Char {
            return when (char.lowercaseChar()) {
                'đ' -> 'đ'
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
        for (i in 0 until enumCount) {
            val len = enumBuf.short.toInt() and 0xFFFF
            val strBytes = ByteArray(len)
            enumBuf.get(strBytes)
            val token = String(strBytes, Charsets.UTF_8)
            val id = enumBuf.int
            enumDict[token] = id
        }

        // 2. Load token_strings_21869.bin
        val tokenBuf = ByteBuffer.wrap(tokenBytes).order(ByteOrder.LITTLE_ENDIAN)
        val tokenCount = tokenBuf.int
        renumList.clear()
        for (i in 0 until tokenCount) {
            val len = tokenBuf.short.toInt() and 0xFFFF
            if (len == 0) {
                renumList.add(null)
            } else {
                val strBytes = ByteArray(len)
                tokenBuf.get(strBytes)
                renumList.add(String(strBytes, Charsets.UTF_8))
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
        // Tone check
        if (toneMark.isNotEmpty()) {
            if (idx < 1 || idx >= renumToneMark.size) return false
            if (renumToneMark[idx] != toneMark) return false
        }

        if (pattern.isEmpty()) return true

        if (word.length < pattern.length) return false

        for (i in pattern.indices) {
            if (normalizeChar(word[i]) != normalizeChar(pattern[i])) {
                return false
            }
        }
        return true
    }
}
