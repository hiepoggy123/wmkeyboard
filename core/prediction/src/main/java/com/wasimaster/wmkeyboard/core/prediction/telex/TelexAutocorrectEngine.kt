package com.wasimaster.wmkeyboard.core.prediction.telex

import android.content.Context
import android.content.res.AssetManager
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Data class representing a correction candidate for Vietnamese Telex input.
 */
data class TelexCorrectionCandidate(
    val word: String,          // Unicode Vietnamese word (e.g. "tuệ")
    val telex: String,         // Corresponding Telex keystroke sequence (e.g. "tueej")
    val penalty: Double,       // Keyboard proximity penalty (0.0 = exact keystrokes)
    val score: Double          // Combined Language Model probability score
) : Comparable<TelexCorrectionCandidate> {
    override fun compareTo(other: TelexCorrectionCandidate): Int {
        return other.score.compareTo(this.score) // Sort descending by score
    }
}

/**
 * Trie node storing valid Vietnamese Telex syllables.
 */
class TelexTrieNode {
    val children: HashMap<Char, TelexTrieNode> = HashMap()
    var word: String? = null
    var unigramScore: Int = 0
}

/**
 * Prefix Trie for ultra-fast Telex syllable lookups (< 1ms).
 */
class TelexTrie {
    val root: TelexTrieNode = TelexTrieNode()

    fun insert(telex: String, word: String, unigramScore: Int) {
        var current = root
        for (char in telex) {
            current = current.children.getOrPut(char) { TelexTrieNode() }
        }
        current.word = word
        current.unigramScore = unigramScore
    }
}

/**
 * Physical adjacent key neighbor on QWERTY layout.
 */
data class TelexKeyNeighbor(
    val key: Char,
    val distance: Double,
    val penalty: Double
)

/**
 * Manages QWERTY proximity matrix and key penalty calculations.
 */
class TelexProximityManager {
    val neighborsMap: HashMap<Char, List<TelexKeyNeighbor>> = HashMap()

    fun loadFromJson(jsonStr: String) {
        val root = Json.parseToJsonElement(jsonStr).jsonObject
        for ((keyStr, element) in root) {
            if (keyStr.isEmpty()) continue
            val keyChar = keyStr[0]
            val keyObj = element.jsonObject
            val neighborsArr = keyObj["neighbors"]?.jsonArray ?: continue

            val list = ArrayList<TelexKeyNeighbor>(neighborsArr.size)
            for (itemEl in neighborsArr) {
                val item = itemEl.jsonObject
                val kStr = item["key"]?.jsonPrimitive?.content ?: continue
                if (kStr.isEmpty()) continue
                val dist = item["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val pen = item["penalty"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                list.add(TelexKeyNeighbor(kStr[0], dist, pen))
            }
            neighborsMap[keyChar] = list
        }
    }

    fun getNeighbors(char: Char): List<TelexKeyNeighbor> {
        return neighborsMap[char] ?: listOf(TelexKeyNeighbor(char, 0.0, 0.0))
    }
}

/**
 * Language Model storing Unigrams and Bigrams for Vietnamese.
 */
class TelexLanguageModel {
    val unigrams: HashMap<String, Int> = HashMap()
    val bigrams: HashMap<String, HashMap<String, Int>> = HashMap()

    fun loadUnigrams(jsonStr: String) {
        val root = Json.parseToJsonElement(jsonStr).jsonObject
        for ((word, element) in root) {
            unigrams[word] = element.jsonPrimitive.intOrNull ?: 1
        }
    }

    fun loadBigrams(jsonStr: String) {
        val root = Json.parseToJsonElement(jsonStr).jsonObject
        for ((w1, element) in root) {
            val nextWordsObj = element.jsonObject
            val subMap = HashMap<String, Int>(nextWordsObj.size)
            for ((w2, scoreEl) in nextWordsObj) {
                subMap[w2] = scoreEl.jsonPrimitive.intOrNull ?: 1
            }
            bigrams[w1] = subMap
        }
    }

    fun getUnigramScore(word: String): Int {
        return unigrams[word] ?: 1
    }

    fun getBigramScore(prevWord: String, currentWord: String): Int {
        return bigrams[prevWord]?.get(currentWord) ?: 0
    }
}

/**
 * TELEX AUTOCORRECT ENGINE (Laban Key Principle)
 *
 * Implements proximity-based error correction for Vietnamese Telex typing:
 * 1. Takes raw typing keystrokes (e.g. "yueej", with previous context "trí").
 * 2. Explores valid Vietnamese syllables on the Telex Trie guided by QWERTY key proximity.
 * 3. Incorporates static Language Model (Unigram + Bigram) AND dynamic user learning (UserLexicon).
 * 4. Ranks candidates and preserves original typing capitalization.
 */
class TelexAutocorrectEngine private constructor() {

    val trie = TelexTrie()
    val proximityManager = TelexProximityManager()
    val languageModel = TelexLanguageModel()

    @Volatile
    private var isInitialized = false

    var isReady: Boolean
        get() = isInitialized
        private set(value) { isInitialized = value }

    companion object {
        @Volatile
        private var instance: TelexAutocorrectEngine? = null

        fun getInstance(): TelexAutocorrectEngine {
            return instance ?: synchronized(this) {
                instance ?: TelexAutocorrectEngine().also { instance = it }
            }
        }

        // Scoring weights
        private const val WEIGHT_PENALTY = 35.0         // Penalty multiplier for fat-finger keystrokes
        private const val WEIGHT_UNIGRAM = 0.5          // Base Unigram frequency weight
        private const val WEIGHT_BIGRAM = 1.2           // Context Bigram weight
        private const val WEIGHT_USER_UNIGRAM = 2.0     // Bonus weight for words learned from user
        private const val WEIGHT_USER_BIGRAM = 4.0      // Bonus weight for word pairs learned from user
        private const val MAX_PENALTY_THRESHOLD = 2.5   // Maximum allowed cumulative key distance penalty
    }

    @Synchronized
    fun initialize(assets: AssetManager) {
        if (isInitialized) return
        try {
            // 1. Syllables & Trie
            val syllablesJson = readAsset(assets, "telex/syllables_telex.json")
            loadSyllables(syllablesJson)

            // 2. QWERTY Proximity
            val proximityJson = readAsset(assets, "telex/qwerty_proximity.json")
            proximityManager.loadFromJson(proximityJson)

            // 3. Unigrams
            val unigramsJson = readAsset(assets, "telex/unigrams.json")
            languageModel.loadUnigrams(unigramsJson)

            // 4. Bigrams
            val bigramsJson = readAsset(assets, "telex/bigrams.json")
            languageModel.loadBigrams(bigramsJson)

            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSyllables(jsonStr: String) {
        val root = Json.parseToJsonElement(jsonStr).jsonObject
        for ((telex, element) in root) {
            val obj = element.jsonObject
            val word = obj["word"]?.jsonPrimitive?.content ?: continue
            val freq = obj["freq"]?.jsonPrimitive?.intOrNull ?: 1
            trie.insert(telex, word, freq)
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        initialize(context.assets)
    }

    /**
     * Finds the best correction candidates for a given raw keystroke buffer.
     *
     * @param rawInput Raw keystrokes (e.g. "yueej", "trid")
     * @param previousWord Previous committed word for bigram context (e.g. "trí")
     * @param userLexicon Optional UserLexicon for dynamic user personalized learning
     * @param maxResults Maximum number of suggestions to return
     */
    fun correct(
        rawInput: String,
        previousWord: String? = null,
        userLexicon: UserLexicon? = null,
        maxResults: Int = 3
    ): List<TelexCorrectionCandidate> {
        if (!isInitialized) return emptyList()

        val cleanInput = rawInput.trim().lowercase()
        if (cleanInput.isEmpty() || cleanInput.length > 12) return emptyList()

        val cleanPrev = previousWord?.trim()?.lowercase()

        // State for BFS on Trie: (Node, index, current_telex, total_penalty)
        data class SearchState(
            val node: TelexTrieNode,
            val index: Int,
            val currentTelex: String,
            val penalty: Double
        )

        val rawCandidates = ArrayList<TelexCorrectionCandidate>()
        val queue = ArrayDeque<SearchState>()
        queue.add(SearchState(trie.root, 0, "", 0.0))

        while (queue.isNotEmpty()) {
            val (node, idx, currStr, penalty) = queue.removeFirst()

            if (idx == cleanInput.length) {
                node.word?.let { unicodeWord ->
                    val baseUnigram = node.unigramScore
                    var baseBigram = 0
                    if (!cleanPrev.isNullOrEmpty()) {
                        baseBigram = languageModel.getBigramScore(cleanPrev, unicodeWord)
                    }

                    // Incorporate user learning from UserLexicon
                    val userUnigramCount = userLexicon?.frequencyOf(unicodeWord) ?: 0
                    val userBigramCount = if (!cleanPrev.isNullOrEmpty()) {
                        userLexicon?.bigramCount(cleanPrev, unicodeWord) ?: 0
                    } else 0

                    val totalScore = -(penalty * WEIGHT_PENALTY) +
                            (baseUnigram * WEIGHT_UNIGRAM) +
                            (baseBigram * WEIGHT_BIGRAM) +
                            (userUnigramCount * WEIGHT_USER_UNIGRAM) +
                            (userBigramCount * WEIGHT_USER_BIGRAM)

                    // Apply case style of rawInput to candidate
                    val casedWord = applyCasing(unicodeWord, rawInput)

                    rawCandidates.add(
                        TelexCorrectionCandidate(
                            word = casedWord,
                            telex = currStr,
                            penalty = penalty,
                            score = totalScore
                        )
                    )
                }
                continue
            }

            val targetChar = cleanInput[idx]
            val neighbors = proximityManager.getNeighbors(targetChar)

            for (neighbor in neighbors) {
                val nextChar = neighbor.key
                val stepPenalty = neighbor.penalty
                val nextTotalPenalty = penalty + stepPenalty

                if (nextTotalPenalty <= MAX_PENALTY_THRESHOLD) {
                    val nextNode = node.children[nextChar]
                    if (nextNode != null) {
                        queue.add(
                            SearchState(
                                node = nextNode,
                                index = idx + 1,
                                currentTelex = currStr + nextChar,
                                penalty = nextTotalPenalty
                            )
                        )
                    }
                }
            }
        }

        if (rawCandidates.isEmpty()) return emptyList()

        rawCandidates.sort()

        val uniqueResults = ArrayList<TelexCorrectionCandidate>()
        val seenWords = HashSet<String>()

        for (cand in rawCandidates) {
            if (seenWords.add(cand.word.lowercase())) {
                uniqueResults.add(cand)
                if (uniqueResults.size >= maxResults) break
            }
        }

        return uniqueResults
    }

    /**
     * Match output casing to input format (lowercase, Titlecase, UPPERCASE).
     */
    private fun applyCasing(word: String, originalInput: String): String {
        if (word.isEmpty() || originalInput.isEmpty()) return word
        val isAllUpper = originalInput.all { it.isUpperCase() }
        if (isAllUpper) return word.uppercase()
        val isFirstUpper = originalInput[0].isUpperCase()
        if (isFirstUpper) {
            return word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return word
    }

    private fun readAsset(assets: AssetManager, path: String): String {
        assets.open(path).use { stream ->
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            return reader.readText()
        }
    }
}
