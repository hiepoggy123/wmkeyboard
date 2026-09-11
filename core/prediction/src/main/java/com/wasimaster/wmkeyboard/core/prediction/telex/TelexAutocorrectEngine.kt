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
import java.text.Normalizer

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
        private const val MAX_PENALTY_THRESHOLD = 1.4   // Maximum allowed cumulative key distance penalty (strictly enforces single-key proximity error, edit distance <= 1)
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

    private val rejectedFlickCorrections: HashMap<String, HashSet<String>> = HashMap()

    val FLICK_NEIGHBORS: Map<Char, List<Char>> = mapOf(
        'd' to listOf('s', 'f', 'e', 'x', 'c'),
        's' to listOf('a', 'w', 'd', 'z', 'x', 'e'),
        'f' to listOf('d', 'r', 'g', 'c', 'v'),
        'e' to listOf('w', 'r', 's', 'd'),
        'r' to listOf('e', 't', 'd', 'f'),
        'a' to listOf('q', 'w', 's', 'z'),
        'x' to listOf('z', 's', 'd', 'c'),
        'j' to listOf('h', 'k', 'u', 'n', 'm'),
        'o' to listOf('i', 'p', 'k', 'l'),
        'u' to listOf('y', 'i', 'h', 'j')
    )

    val FLICK_KEY_TELEX: Map<Char, List<String>> = mapOf(
        's' to listOf("s"),
        'f' to listOf("f"),
        'r' to listOf("r"),
        'x' to listOf("x"),
        'j' to listOf("j"),
        'a' to listOf("aa", "aw"),
        'e' to listOf("ee"),
        'o' to listOf("oo", "ow"),
        'u' to listOf("uw"),
        'd' to listOf("dd")
    )

    fun isAccented(word: String): Boolean {
        for (ch in word) {
            val lc = ch.lowercaseChar()
            if (lc !in 'a'..'z') return true
        }
        return false
    }

    fun rejectFlickCorrection(original: String, committed: String) {
        val origClean = original.lowercase().trim()
        val commClean = committed.lowercase().trim()
        if (origClean.isNotEmpty() && commClean.isNotEmpty()) {
            rejectedFlickCorrections.getOrPut(origClean) { HashSet() }.add(commClean)
        }
    }

    fun isFlickCorrectionRejected(original: String, committed: String): Boolean {
        val origClean = original.lowercase().trim()
        val commClean = committed.lowercase().trim()
        return rejectedFlickCorrections[origClean]?.contains(commClean) == true
    }

    fun isWordInDictionary(word: String): Boolean {
        val clean = word.lowercase()
        return languageModel.unigrams.containsKey(clean) || 
               TelexWhitelist.isWhitelisted(clean)
    }

    fun isWhitelisted(word: String): Boolean {
        return TelexWhitelist.isWhitelisted(word)
    }

    /**
     * Resolves ghost flick errors where a finger flicked on an adjacent key (e.g. key 'd' instead of 's').
     * Tests neighboring keys' flick outputs and scores candidates via language model context.
     */
    fun resolveFlickNeighbors(
        tokens: List<TypingToken>,
        originalComposed: String,
        previousWord: String? = null,
        previousWord2: String? = null,
        userLexicon: UserLexicon? = null,
        maxResults: Int = 3
    ): List<TelexCorrectionCandidate> {
        if (!isReady || tokens.isEmpty()) return emptyList()

        val flickIndices = tokens.indices.filter { tokens[it].isFlick }
        if (flickIndices.isEmpty()) return emptyList()

        val candidates = ArrayList<TelexCorrectionCandidate>()
        val cleanPrev = previousWord?.trim()?.lowercase()
        val cleanPrev2 = previousWord2?.trim()?.lowercase()

        for (flickIdx in flickIndices) {
            val token = tokens[flickIdx]
            val baseKey = token.baseKey ?: continue
            val neighbors = FLICK_NEIGHBORS[baseKey] ?: continue

            val prefixSb = StringBuilder()
            for (i in 0 until flickIdx) {
                val t = tokens[i]
                if (t.isFlick) {
                    prefixSb.append(toCanonicalTelex(t.char.toString()))
                } else {
                    prefixSb.append(t.char.lowercaseChar())
                }
            }
            val prefix = prefixSb.toString()

            val suffixSb = StringBuilder()
            for (i in (flickIdx + 1) until tokens.size) {
                val t = tokens[i]
                if (t.isFlick) {
                    suffixSb.append(toCanonicalTelex(t.char.toString()))
                } else {
                    suffixSb.append(t.char.lowercaseChar())
                }
            }
            val suffix = suffixSb.toString()

            for (neighborKey in neighbors) {
                val telexOptions = FLICK_KEY_TELEX[neighborKey] ?: continue
                for (opt in telexOptions) {
                    val telexCandidates = if (opt in listOf("s", "f", "r", "x", "j")) {
                        listOf(prefix + opt + suffix, prefix + suffix + opt)
                    } else {
                        listOf(prefix + opt + suffix)
                    }

                    for (telexSeq in telexCandidates) {
                        val word = trie.findWord(telexSeq) ?: continue
                        if (!VietnameseOrthography.isValidVietnameseSyllable(word)) continue
                        if (isFlickCorrectionRejected(originalComposed, word)) continue

                        val baseUnigram = languageModel.getUnigramScore(word)
                        var baseBigram = 0
                        var baseTrigram = 0
                        if (!cleanPrev.isNullOrEmpty()) {
                            baseBigram = languageModel.getBigramScore(cleanPrev, word)
                            if (!cleanPrev2.isNullOrEmpty()) {
                                baseTrigram = languageModel.getTrigramScore(cleanPrev2, cleanPrev, word)
                            }
                        }
                        val userUnigramCount = userLexicon?.frequencyOf(word) ?: 0
                        val userBigramCount = if (!cleanPrev.isNullOrEmpty()) {
                            userLexicon?.bigramCount(cleanPrev, word) ?: 0
                        } else 0

                        val totalScore = (baseUnigram * WEIGHT_UNIGRAM) +
                                (baseBigram * WEIGHT_BIGRAM) +
                                (baseTrigram * WEIGHT_TRIGRAM) +
                                (userUnigramCount * WEIGHT_USER_UNIGRAM) +
                                (userBigramCount * WEIGHT_USER_BIGRAM)

                        candidates.add(
                            TelexCorrectionCandidate(
                                word = applyCasing(word, originalComposed),
                                telex = telexSeq,
                                penalty = 0.5,
                                score = totalScore
                            )
                        )
                    }
                }
            }
        }

        candidates.sort()
        val distinct = ArrayList<TelexCorrectionCandidate>()
        val seen = HashSet<String>()
        for (c in candidates) {
            if (seen.add(c.word.lowercase())) {
                distinct.add(c)
                if (distinct.size >= maxResults) break
            }
        }
        return distinct
    }

    @Synchronized
    fun initialize(context: Context) {
        initialize(context.assets, context.filesDir)
    }

    /**
     * Converts a Vietnamese word or composing buffer (which may contain precomposed
     * characters or combining tone marks from flick gestures) into a canonical Telex
     * keystroke sequence (e.g. "chao\u0300" -> "chaof", "toán" -> "toans", "tuệ" -> "tueej").
     */
    fun toCanonicalTelex(input: String): String {
        if (input.isEmpty()) return ""
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        val sb = StringBuilder()
        var toneChar: Char? = null

        var i = 0
        while (i < normalized.length) {
            val ch = normalized[i]
            val lc = ch.lowercaseChar()
            when (lc) {
                // Diacritical tone marks in NFD
                '\u0301' -> toneChar = 's' // sắc (acute)
                '\u0300' -> toneChar = 'f' // huyền (grave)
                '\u0309' -> toneChar = 'r' // hỏi (hook)
                '\u0303' -> toneChar = 'x' // ngã (tilde)
                '\u0323' -> toneChar = 'j' // nặng (dot below)
                '\u0302' -> {
                    // Circumflex: double the previous vowel ('a'->'aa', 'e'->'ee', 'o'->'oo')
                    val lastChar = sb.lastOrNull()
                    if (lastChar != null) {
                        val prev = lastChar.lowercaseChar()
                        if (prev == 'a' || prev == 'e' || prev == 'o') {
                            sb.append(prev)
                        }
                    }
                }
                '\u0306' -> sb.append('w') // Breve: 'a' + 'w' -> 'aw'
                '\u031b' -> sb.append('w') // Horn: 'o' or 'u' + 'w' -> 'ow' or 'uw'
                'đ' -> sb.append("dd")
                'Đ' -> sb.append("DD")
                else -> {
                    // Precomposed tones or direct popup/flick marks
                    when (ch) {
                        '́' -> toneChar = 's'
                        '̀' -> toneChar = 'f'
                        '̉' -> toneChar = 'r'
                        '̃' -> toneChar = 'x'
                        '̣' -> toneChar = 'j'
                        else -> sb.append(ch)
                    }
                }
            }
            i++
        }
        toneChar?.let { sb.append(it) }
        return sb.toString()
    }

    /**
     * Predicts the most likely next Vietnamese words given the previous words context.
     * Prioritizes binary NgramPack (.wmng) before falling back to in-memory JSON bigrams/trigrams.
     */
    fun predictNextWords(
        previousWord: String?,
        previousWord2: String? = null,
        maxResults: Int = 3
    ): List<String> {
        if (!isReady || previousWord.isNullOrBlank()) return emptyList()
        val prev1 = previousWord.trim().lowercase()
        val prev2 = previousWord2?.trim()?.lowercase()

        val results = LinkedHashSet<String>()

        // 1. Trigrams from binary NgramPack
        if (!prev2.isNullOrBlank() && !languageModel.ngramPack.isEmpty) {
            val triCandidates = languageModel.ngramPack.nextWordsAfter(prev2, prev1, maxResults)
            for (w in triCandidates) {
                results.add(w)
                if (results.size >= maxResults) return results.toList()
            }
        }

        // 2. Trigrams from in-memory fallback
        if (!prev2.isNullOrBlank()) {
            val trigramMap = languageModel.trigrams["$prev2 $prev1"]
            if (trigramMap != null) {
                val sorted = trigramMap.entries.sortedByDescending { it.value }.map { it.key }
                for (w in sorted) {
                    results.add(w)
                    if (results.size >= maxResults) return results.toList()
                }
            }
        }

        // 3. Bigrams from binary NgramPack
        if (!languageModel.ngramPack.isEmpty) {
            val biCandidates = languageModel.ngramPack.nextWords(prev1, maxResults)
            for (w in biCandidates) {
                results.add(w)
                if (results.size >= maxResults) return results.toList()
            }
        }

        // 4. Bigrams from in-memory fallback
        val bigramMap = languageModel.bigrams[prev1]
        if (bigramMap != null) {
            val sorted = bigramMap.entries.sortedByDescending { it.value }.map { it.key }
            for (w in sorted) {
                results.add(w)
                if (results.size >= maxResults) return results.toList()
            }
        }

        return results.toList()
    }

    /**
     * Finds Vietnamese word completions matching a prefix (e.g. "việ" -> ["việt", "việc", ...]).
     */
    fun findCompletions(prefix: String, maxResults: Int = 5): List<String> {
        if (!isReady || prefix.isBlank()) return emptyList()
        val clean = prefix.trim().lowercase()
        return languageModel.unigrams.entries
            .filter { it.key.startsWith(clean) && it.key != clean }
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
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
        maxResults: Int = 3,
        hasFlick: Boolean = true
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
            if (!hasFlick && !isAccented(cleanInput) && isAccented(unicodeWord)) {
                continue
            }
            if (isFlickCorrectionRejected(cleanInput, unicodeWord)) {
                continue
            }
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

        fun dfs(node: TelexTrieNode, idx: Int, currentPenalty: Double, errorCount: Int) {
            if (idx == cleanInput.length) {
                node.word?.let { unicodeWord ->
                    if (!hasFlick && !isAccented(cleanInput) && isAccented(unicodeWord)) {
                        return
                    }
                    if (isFlickCorrectionRejected(cleanInput, unicodeWord)) {
                        return
                    }
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
                val nextErrors = if (nextChar != targetChar) errorCount + 1 else errorCount

                // Strictly allow at most 1 substituted key (proximity edit distance <= 1) and penalty <= 1.4
                if (nextErrors <= 1 && nextTotalPenalty <= MAX_PENALTY_THRESHOLD) {
                    val nextNode = node.children[nextChar]
                    if (nextNode != null) {
                        charBuffer[idx] = nextChar
                        dfs(nextNode, idx + 1, nextTotalPenalty, nextErrors)
                    }
                }
            }
        }

        dfs(trie.root, 0, 0.0, 0)

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
