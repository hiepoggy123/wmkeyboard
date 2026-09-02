package com.wasimaster.wmkeyboard.core.prediction.telex

import android.content.Context
import android.content.res.AssetManager
import androidx.annotation.VisibleForTesting
import com.wasimaster.wmkeyboard.core.prediction.MappedNgramPack
import com.wasimaster.wmkeyboard.core.prediction.NgramPack
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.File
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

    fun find(telex: String): TelexTrieNode? {
        var current = root
        for (char in telex) {
            current = current.children[char] ?: return null
        }
        return current
    }

    fun findWord(telex: String): String? {
        return find(telex)?.word
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
 * Language Model storing Unigrams, Bigrams, and Trigrams for Vietnamese.
 */
class TelexLanguageModel {
    val unigrams: HashMap<String, Int> = HashMap()
    val bigrams: HashMap<String, HashMap<String, Int>> = HashMap()
    val trigrams: HashMap<String, HashMap<String, Int>> = HashMap()
    var ngramPack: NgramPack = NgramPack.EMPTY

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

    fun loadTrigrams(jsonStr: String) {
        val root = Json.parseToJsonElement(jsonStr).jsonObject
        for ((prefix, element) in root) {
            val nextWordsObj = element.jsonObject
            val subMap = HashMap<String, Int>(nextWordsObj.size)
            for ((w3, scoreEl) in nextWordsObj) {
                subMap[w3] = scoreEl.jsonPrimitive.intOrNull ?: 1
            }
            trigrams[prefix] = subMap
        }
    }

    fun getUnigramScore(word: String): Int {
        return unigrams[word] ?: 1
    }

    fun getBigramScore(prevWord: String, currentWord: String): Int {
        val packScore = ngramPack.bigramCount(prevWord, currentWord)
        if (packScore > 0) return packScore
        return bigrams[prevWord]?.get(currentWord) ?: 0
    }

    fun getTrigramScore(prevWord2: String, prevWord1: String, currentWord: String): Int {
        val packScore = ngramPack.trigramCount(prevWord2, prevWord1, currentWord)
        if (packScore > 0) return packScore
        return trigrams["$prevWord2 $prevWord1"]?.get(currentWord) ?: 0
    }
}

/**
 * TELEX AUTOCORRECT ENGINE (Laban Key + OpenKey + V7 AI Model)
 *
 * Implements proximity-based and bimanual desync error correction for Vietnamese Telex typing:
 * 1. Takes raw typing keystrokes (e.g. "yueej", "rtowfi", with previous context "trí", "Hôm nay").
 * 2. Explores valid Vietnamese syllables on the Telex Trie guided by QWERTY key proximity.
 * 3. Incorporates Bimanual Desync Engine to fix left-right hand keystroke transposition.
 * 4. Incorporates static Language Model (Unigram + Bigram + Trigram) AND dynamic user learning.
 * 5. Strictly protects exact matches (penalty == 0) so valid words are never replaced by neighbors.
 * 6. Ranks candidates and preserves original typing capitalization.
 */
class TelexAutocorrectEngine private constructor() {

    val trie = TelexTrie()
    val proximityManager = TelexProximityManager()
    val languageModel = TelexLanguageModel()

    @Volatile
    private var isInitialized = false

    var isReady: Boolean
        get() = isInitialized || trie.root.children.isNotEmpty()
        set(value) { isInitialized = value }

    companion object {
        @Volatile
        private var instance: TelexAutocorrectEngine? = null

        fun getInstance(): TelexAutocorrectEngine {
            return instance ?: synchronized(this) {
                instance ?: TelexAutocorrectEngine().also { instance = it }
            }
        }

        // Scoring weights
        private const val EXACT_MATCH_BONUS = 5000.0    // Massive bonus when exact keystrokes match a valid word
        private const val WEIGHT_PENALTY = 200.0        // Penalty multiplier for fat-finger keystrokes
        private const val WEIGHT_UNIGRAM = 1.0          // Base Unigram frequency weight
        private const val WEIGHT_BIGRAM = 2.5           // Context Bigram weight
        private const val WEIGHT_TRIGRAM = 5.0          // Context Trigram weight (from V7 AI model)
        private const val WEIGHT_USER_UNIGRAM = 3.0     // Bonus weight for words learned from user
        private const val WEIGHT_USER_BIGRAM = 6.0      // Bonus weight for word pairs learned from user
        private const val MAX_PENALTY_THRESHOLD = 2.5   // Maximum allowed cumulative key distance penalty
    }

    @VisibleForTesting
    fun resetForTesting() {
        trie.root.children.clear()
        proximityManager.neighborsMap.clear()
        languageModel.unigrams.clear()
        languageModel.bigrams.clear()
        languageModel.trigrams.clear()
        languageModel.ngramPack = NgramPack.EMPTY
        isInitialized = false
    }

    @Synchronized
    fun initialize(assets: AssetManager, filesDir: File? = null) {
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

            // 4. Binary NgramPack (.wmng) or fallback to JSON
            var packLoaded = false
            if (filesDir != null) {
                val packFile = ensureBundledNgramPack(assets, filesDir)
                if (packFile != null) {
                    val mapped = MappedNgramPack.open(packFile)
                    if (mapped != null) {
                        languageModel.ngramPack = NgramPack.of(mapped)
                        packLoaded = true
                    }
                }
            }

            if (!packLoaded) {
                // Fallback to JSON if .wmng not available or no filesDir (e.g. test environments)
                try {
                    val bigramsJson = readAsset(assets, "telex/bigrams.json")
                    languageModel.loadBigrams(bigramsJson)
                } catch (_: Exception) {}

                try {
                    val trigramsJson = readAsset(assets, "telex/trigrams.json")
                    languageModel.loadTrigrams(trigramsJson)
                } catch (_: Exception) {}
            }

            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun ensureBundledNgramPack(assets: AssetManager, filesDir: File): File? {
        val dir = File(File(filesDir, "dict"), "bundled")
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val outFile = File(dir, "telex_ngrams.wmng")
        if (outFile.isFile && outFile.length() > 0) return outFile
        return try {
            val tmp = File(dir, "telex_ngrams.wmng.tmp")
            assets.open("telex/ngrams.wmng").use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (tmp.renameTo(outFile)) outFile else null
        } catch (_: Exception) {
            null
        }
    }

    fun loadNgramPack(file: File): Boolean {
        val mapped = MappedNgramPack.open(file) ?: return false
        languageModel.ngramPack = NgramPack.of(mapped)
        return true
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

    fun isWordInDictionary(word: String): Boolean {
        val clean = word.lowercase()
        return languageModel.unigrams.containsKey(clean) || 
               TelexWhitelist.isWhitelisted(clean) || 
               VietnameseOrthography.isValidVietnameseSyllable(clean)
    }

    fun isWhitelisted(word: String): Boolean {
        return TelexWhitelist.isWhitelisted(word)
    }

    @Synchronized
    fun initialize(context: Context) {
        initialize(context.assets, context.filesDir)
    }

    /**
     * Finds the best correction candidates for a given raw keystroke buffer.
     *
     * @param rawInput Raw keystrokes (e.g. "yueej", "trid", "rtowfi")
     * @param previousWord Previous committed word for bigram/trigram context (e.g. "nay")
     * @param previousWord2 Second previous word for trigram context (e.g. "Hôm")
     * @param userLexicon Optional UserLexicon for dynamic user personalized learning
     * @param maxResults Maximum number of suggestions to return
     */
    fun correct(
        rawInput: String,
        previousWord: String? = null,
        previousWord2: String? = null,
        userLexicon: UserLexicon? = null,
        maxResults: Int = 3
    ): List<TelexCorrectionCandidate> {
        if (!isReady) return emptyList()

        val cleanInput = rawInput.trim().lowercase()
        if (cleanInput.length < 3 || cleanInput.length > 12) return emptyList()
        if (TelexWhitelist.isWhitelisted(cleanInput)) return emptyList()

        val cleanPrev = previousWord?.trim()?.lowercase()
        val cleanPrev2 = previousWord2?.trim()?.lowercase()

        val rawCandidates = ArrayList<TelexCorrectionCandidate>()

        // 2. Bimanual Typing Desync Candidates (Left-Right hand timing errors)
        val desyncCandidates = BimanualDesyncEngine.generateCandidates(cleanInput, this)
        for (desync in desyncCandidates) {
            val unicodeWord = desync.word
            val baseUnigram = languageModel.getUnigramScore(unicodeWord)
            var baseBigram = 0
            var baseTrigram = 0
            if (!cleanPrev.isNullOrEmpty()) {
                baseBigram = languageModel.getBigramScore(cleanPrev, unicodeWord)
                if (!cleanPrev2.isNullOrEmpty()) {
                    baseTrigram = languageModel.getTrigramScore(cleanPrev2, cleanPrev, unicodeWord)
                }
            }
            val userUnigramCount = userLexicon?.frequencyOf(unicodeWord) ?: 0
            val userBigramCount = if (!cleanPrev.isNullOrEmpty()) {
                userLexicon?.bigramCount(cleanPrev, unicodeWord) ?: 0
            } else 0

            val totalScore = - (desync.penalty * WEIGHT_PENALTY) +
                    (baseUnigram * WEIGHT_UNIGRAM) +
                    (baseBigram * WEIGHT_BIGRAM) +
                    (baseTrigram * WEIGHT_TRIGRAM) +
                    (userUnigramCount * WEIGHT_USER_UNIGRAM) +
                    (userBigramCount * WEIGHT_USER_BIGRAM)

            rawCandidates.add(
                TelexCorrectionCandidate(
                    word = applyCasing(unicodeWord, rawInput),
                    telex = desync.telex,
                    penalty = desync.penalty,
                    score = totalScore
                )
            )
        }

        // 3. QWERTY Key Proximity Candidates (Zero-Allocation DFS)
        val charBuffer = CharArray(16)

        fun dfs(node: TelexTrieNode, idx: Int, currentPenalty: Double) {
            if (idx == cleanInput.length) {
                node.word?.let { unicodeWord ->
                    val baseUnigram = node.unigramScore
                    var baseBigram = 0
                    var baseTrigram = 0
                    if (!cleanPrev.isNullOrEmpty()) {
                        baseBigram = languageModel.getBigramScore(cleanPrev, unicodeWord)
                        if (!cleanPrev2.isNullOrEmpty()) {
                            baseTrigram = languageModel.getTrigramScore(cleanPrev2, cleanPrev, unicodeWord)
                        }
                    }

                    // Incorporate user learning from UserLexicon
                    val userUnigramCount = userLexicon?.frequencyOf(unicodeWord) ?: 0
                    val userBigramCount = if (!cleanPrev.isNullOrEmpty()) {
                        userLexicon?.bigramCount(cleanPrev, unicodeWord) ?: 0
                    } else 0

                    // Massive bonus if exact match (penalty == 0.0)
                    val exactBonus = if (currentPenalty < 0.001) EXACT_MATCH_BONUS else 0.0

                    val totalScore = exactBonus -
                            (currentPenalty * WEIGHT_PENALTY) +
                            (baseUnigram * WEIGHT_UNIGRAM) +
                            (baseBigram * WEIGHT_BIGRAM) +
                            (baseTrigram * WEIGHT_TRIGRAM) +
                            (userUnigramCount * WEIGHT_USER_UNIGRAM) +
                            (userBigramCount * WEIGHT_USER_BIGRAM)

                    // Apply case style of rawInput to candidate
                    val casedWord = applyCasing(unicodeWord, rawInput)

                    rawCandidates.add(
                        TelexCorrectionCandidate(
                            word = casedWord,
                            telex = String(charBuffer, 0, idx),
                            penalty = currentPenalty,
                            score = totalScore
                        )
                    )
                }
                return
            }

            val targetChar = cleanInput[idx]
            val neighbors = proximityManager.getNeighbors(targetChar)

            for (neighbor in neighbors) {
                val nextChar = neighbor.key
                val stepPenalty = neighbor.penalty
                val nextTotalPenalty = currentPenalty + stepPenalty

                if (nextTotalPenalty <= MAX_PENALTY_THRESHOLD) {
                    val nextNode = node.children[nextChar]
                    if (nextNode != null) {
                        charBuffer[idx] = nextChar
                        dfs(nextNode, idx + 1, nextTotalPenalty)
                    }
                }
            }
        }

        dfs(trie.root, 0, 0.0)

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
