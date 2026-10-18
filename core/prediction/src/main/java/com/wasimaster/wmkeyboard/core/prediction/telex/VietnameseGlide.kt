package com.wasimaster.wmkeyboard.core.prediction.telex

import android.content.res.AssetManager
import androidx.annotation.VisibleForTesting
import com.wasimaster.wmkeyboard.core.gesture.RomanizedIndex
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.ln

/**
 * Provides unaccented-to-accented Vietnamese syllable and word mapping for Glide typing.
 * Builds a PackedTrie of unaccented stems and resolves gesture readings into native
 * Vietnamese syllables ranked by frequency and context.
 *
 * Supports Chinese Pinyin-style 3-column format (`reading\tword\tfreq`) and 2-column format,
 * integrating 1-syllable unigram frequencies from the bundled language model.
 */
object VietnameseGlide {
    private const val ASSET_PATH = "telex/vietnamese_glide_syllables.tsv"
    private const val UNIGRAMS_ASSET_PATH = "telex/unigrams.json"
    private const val VIETNAMESE_KEYS = "abcdefghijklmnopqrstuvwxyz"

    @Volatile
    private var cachedIndex: RomanizedIndex? = null
    @Volatile
    private var syllableMap: Map<String, List<String>>? = null

    /**
     * Loads the Vietnamese unaccented trie and builds the RomanizedIndex.
     */
    fun getIndex(assets: AssetManager): RomanizedIndex? {
        cachedIndex?.let { return it }
        synchronized(this) {
            cachedIndex?.let { return it }
            return try {
                val unigrams = try {
                    assets.open(UNIGRAMS_ASSET_PATH).use { parseUnigrams(it) }
                } catch (_: Exception) {
                    emptyMap()
                }
                assets.open(ASSET_PATH).use { stream ->
                    loadFromStream(stream, unigrams)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Parses word -> freq mapping from unigrams.json.
     */
    fun parseUnigrams(stream: InputStream): Map<String, Int> {
        return try {
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            val root = Json.parseToJsonElement(text).jsonObject
            val map = HashMap<String, Int>(root.size)
            for ((word, element) in root) {
                map[word.lowercase()] = element.jsonPrimitive.intOrNull ?: 1
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Internal loader from input stream with optional unigram frequency support.
     */
    fun loadFromStream(stream: InputStream, unigrams: Map<String, Int> = emptyMap()): RomanizedIndex {
        val map = HashMap<String, List<String>>(25000)
        val entries = ArrayList<Pair<String, Int>>(25000)
        val wordFreq = HashMap<String, Int>(25000)
        val stemMaxFreq = HashMap<String, Int>(25000)

        fun getWordFrequency(word: String, explicitFreq: Int?): Int {
            if (explicitFreq != null && explicitFreq > 0) return explicitFreq
            val unigram = unigrams[word.lowercase()]
            if (unigram != null && unigram > 0) return unigram
            if (word.contains(' ')) {
                val parts = word.lowercase().split(' ')
                val freqs = parts.mapNotNull { unigrams[it] }
                if (freqs.isNotEmpty()) {
                    return freqs.average().toInt().coerceAtLeast(100)
                }
                return 150
            }
            return 50
        }

        val intermediate = LinkedHashMap<String, MutableList<Pair<String, Int>>>()

        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val parts = trimmed.split('\t')
                if (parts.size < 2) continue
                val unaccented = parts[0].trim().lowercase()
                if (unaccented.isEmpty()) continue

                // Check 3-column Chinese format: reading \t word \t freq
                if (parts.size >= 3) {
                    val word = parts[1].trim()
                    val freq = parts[2].trim().toIntOrNull()
                    val finalFreq = getWordFrequency(word, freq)
                    val list = intermediate.getOrPut(unaccented) { mutableListOf() }
                    list.add(word to finalFreq)
                } else {
                    // 2-column format: reading \t form1,form2,... or form1:freq1,form2:freq2,...
                    val forms = parts[1].split(',')
                    val list = intermediate.getOrPut(unaccented) { mutableListOf() }
                    for (rawForm in forms) {
                        val formTrim = rawForm.trim()
                        if (formTrim.isEmpty()) continue
                        val colon = formTrim.lastIndexOf(':')
                        val (formWord, explicitFreq) = if (colon > 0 && colon < formTrim.length - 1) {
                            val candidateWord = formTrim.substring(0, colon)
                            val parsedFreq = formTrim.substring(colon + 1).toIntOrNull()
                            if (parsedFreq != null) {
                                candidateWord to parsedFreq
                            } else {
                                formTrim to null
                            }
                        } else {
                            formTrim to null
                        }
                        val finalFreq = getWordFrequency(formWord, explicitFreq)
                        list.add(formWord to finalFreq)
                    }
                }
            }
        }

        for ((unaccented, list) in intermediate) {
            val sorted = list.groupBy { it.first }
                .map { (word, pairs) -> word to pairs.maxOf { it.second } }
                .sortedByDescending { it.second }

            val formWords = sorted.map { it.first }
            map[unaccented] = formWords

            val maxFreq = sorted.firstOrNull()?.second ?: 1
            stemMaxFreq[unaccented] = maxFreq
            for ((w, f) in sorted) {
                wordFreq["$unaccented\t$w"] = f
            }

            entries.add(unaccented to maxFreq)
        }

        syllableMap = map
        val alphabet = VIETNAMESE_KEYS.mapTo(HashSet()) { it.code }
        val trie = PackedTrie.of(entries)
        val index = RomanizedIndex.multiMap(
            curated = trie,
            alphabet = alphabet,
            resolve = { unaccented ->
                map[unaccented] ?: emptyList()
            },
            scoreForm = { unaccented, form, formIndex ->
                val max = stemMaxFreq[unaccented] ?: 0
                val freq = wordFreq["$unaccented\t$form"] ?: unigrams[form.lowercase()] ?: 0
                if (max > 0 && freq > 0) {
                    ln(freq.toDouble() / max).coerceIn(-3.5, 0.0) - (formIndex * 0.001)
                } else {
                    -formIndex * 0.35
                }
            }
        )
        cachedIndex = index
        return index
    }

    fun lookup(unaccented: String): List<String> {
        return syllableMap?.get(unaccented) ?: emptyList()
    }

    @VisibleForTesting
    fun resetForTesting() {
        cachedIndex = null
        syllableMap = null
    }
}
