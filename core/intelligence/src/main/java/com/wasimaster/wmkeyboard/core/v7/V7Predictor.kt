package com.wasimaster.wmkeyboard.core.v7

import android.content.Context
import java.util.Locale
import java.util.regex.Pattern

/**
 * Intelligent Vietnamese shorthand & contextual phrase predictor based on V7.
 */
class V7Predictor(
    private val vocab: V7Vocab,
    private val runtime: V7ModelRuntime
) {
    private val doubleConsonants = listOf(
        "ch", "kh", "ng", "nh", "ph", "th", "tr"
    )

    private val singleConsonants = listOf(
        "b", "d", "đ", "g", "z", "h", "k", "l", "m", "n", "p", "r", "s", "t", "v", "x"
    )

    data class RawPart(
        val consonant: String,
        val rhyme: String,
        val tone: Int,
        val isCapitalized: Boolean
    )

    fun separateRaws(input: String): List<String> {
        val pattern = Pattern.compile("[a-zA-Z\\.]+(?:\\d|$)")
        val matcher = pattern.matcher(input)
        val raws = mutableListOf<String>()
        while (matcher.find()) {
            val part = matcher.group()
            if (part.isNotEmpty()) {
                raws.add(part)
            }
        }
        return raws
    }

    fun parseRaw(raw: String): RawPart? {
        if (raw.isEmpty()) return null
        val isCap = raw.first().isUpperCase()
        val lower = raw.lowercase(Locale.ROOT)

        val lastChar = lower.last()
        val tone = if (lastChar.isDigit()) {
            lastChar.digitToInt()
        } else {
            0
        }

        val textPart = if (lastChar.isDigit()) lower.dropLast(1) else lower
        if (textPart.isEmpty() && lastChar.isDigit()) {
            return RawPart("0", "", tone, isCap)
        }

        var normalizedText = textPart
        if (normalizedText.startsWith("dd")) {
            normalizedText = "đ" + normalizedText.drop(2)
        }

        var consonant = "0"
        var rhyme = normalizedText

        for (dc in doubleConsonants) {
            if (normalizedText.startsWith(dc)) {
                consonant = dc
                rhyme = normalizedText.drop(dc.length)
                break
            }
        }

        if (consonant == "0") {
            for (sc in singleConsonants) {
                if (normalizedText.startsWith(sc)) {
                    consonant = sc
                    rhyme = normalizedText.drop(sc.length)
                    break
                }
            }
        }

        if (consonant == "0" && normalizedText.isNotEmpty()) {
            val first = normalizedText.first()
            if (first == 'c' || first == 'q') {
                consonant = "k"
                rhyme = normalizedText.drop(1)
            }
        }

        return RawPart(consonant, rhyme, tone, isCap)
    }

    /**
     * Predict candidates for shorthand input (e.g., "x0ch2" -> ["xin chào", ...])
     */
    fun predict(inputString: String, context: String = "tôi", limit: Int = 6): List<String> {
        val raws = separateRaws(inputString)
        if (raws.isEmpty()) return emptyList()

        val parsedParts = raws.mapNotNull { parseRaw(it) }
        if (parsedParts.size != raws.size) return emptyList()

        val ctxWords = context.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val contextTokenIds = ctxWords.map { vocab.getId(it).toLong() }.toMutableList()

        val currentResult = mutableListOf<String>()

        // For all but the last syllable: greedily pick the top-1 matching word
        for (i in 0 until parsedParts.size - 1) {
            val part = parsedParts[i]
            val logits = runtime.predictLogits(contextTokenIds.toLongArray())
            val sortedIndices = getSortedIndices(logits)

            var bestWord: String? = null
            var bestId = 0
            for (tid in sortedIndices) {
                if (tid == 0) continue
                val c = vocab.getConsonant(tid)
                val t = vocab.getTone(tid)
                if (part.consonant != "0" && c != part.consonant) continue
                if (t != part.tone) continue

                val word = vocab.getWord(tid) ?: continue
                bestWord = if (part.isCapitalized) word.replaceFirstChar { it.uppercase() } else word
                bestId = tid
                break
            }

            if (bestWord == null) return emptyList()
            currentResult.add(bestWord)
            contextTokenIds.add(bestId.toLong())
            if (contextTokenIds.size > 32) contextTokenIds.removeAt(0)
        }

        // For the last syllable: gather up to [limit] candidates
        val lastPart = parsedParts.last()
        val logits = runtime.predictLogits(contextTokenIds.toLongArray())
        val sortedIndices = getSortedIndices(logits)

        val candidates = mutableListOf<String>()
        for (tid in sortedIndices) {
            if (tid == 0) continue
            val c = vocab.getConsonant(tid)
            val t = vocab.getTone(tid)
            if (lastPart.consonant != "0" && c != lastPart.consonant) continue
            if (t != lastPart.tone) continue

            val word = vocab.getWord(tid) ?: continue
            val formattedWord = if (lastPart.isCapitalized) word.replaceFirstChar { it.uppercase() } else word
            val phrase = if (currentResult.isEmpty()) {
                formattedWord
            } else {
                currentResult.joinToString(" ") + " " + formattedWord
            }
            candidates.add(phrase)
            if (candidates.size >= limit) break
        }

        return candidates
    }

    private fun getSortedIndices(logits: FloatArray): IntArray {
        val indices = Array(logits.size) { it }
        indices.sortWith { a, b -> logits[b].compareTo(logits[a]) }
        val result = IntArray(indices.size)
        for (i in indices.indices) {
            result[i] = indices[i]
        }
        return result
    }

    companion object {
        @Volatile
        private var instance: V7Predictor? = null

        fun getInstance(context: Context): V7Predictor {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val vocab = V7Vocab.getInstance(context)
                    val runtime = V7ModelRuntime.getInstance(context)
                    V7Predictor(vocab, runtime).also { instance = it }
                }
            }
        }
    }
}
