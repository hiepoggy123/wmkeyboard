package com.wasimaster.wmkeyboard.core.prediction.telex

import android.content.Context
import android.content.res.AssetManager
import androidx.annotation.VisibleForTesting
import com.wasimaster.wmkeyboard.core.prediction.CorrectionMemory
import com.wasimaster.wmkeyboard.core.prediction.MappedNgramPack
import com.wasimaster.wmkeyboard.core.prediction.NgramPack
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.prediction.matchesVietnamesePrefix
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
    var maxSubtreeScore: Int = 0
}

/**
 * Prefix Trie for ultra-fast Telex syllable lookups (< 1ms).
 */
class TelexTrie {
    val root: TelexTrieNode = TelexTrieNode()

    fun insert(telex: String, word: String, unigramScore: Int) {
        var current = root
        current.maxSubtreeScore = maxOf(current.maxSubtreeScore, unigramScore)
        for (char in telex) {
            current = current.children.getOrPut(char) { TelexTrieNode() }
            current.maxSubtreeScore = maxOf(current.maxSubtreeScore, unigramScore)
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

    @Volatile
    private var cachedTopUnigrams: List<String>? = null

    fun loadUnigrams(jsonStr: String) {
        val root = Json.parseToJsonElement(jsonStr).jsonObject
        for ((word, element) in root) {
            unigrams[word] = element.jsonPrimitive.intOrNull ?: 1
        }
        cachedTopUnigrams = null
    }

    fun clearCache() {
        cachedTopUnigrams = null
    }

    fun topUnigrams(limit: Int = 30): List<String> {
        val cached = cachedTopUnigrams
        if (cached != null) return cached.take(limit)
        val sorted = unigrams.entries
            .sortedByDescending { it.value }
            .map { it.key }
        cachedTopUnigrams = sorted
        return sorted.take(limit)
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
        get() = isInitialized || trie.root.children.isNotEmpty() || languageModel.unigrams.isNotEmpty()
        set(value) { isInitialized = value }

    companion object {
        @Volatile
        private var instance: TelexAutocorrectEngine? = null

        fun getInstance(): TelexAutocorrectEngine {
            return instance ?: synchronized(this) {
                instance ?: TelexAutocorrectEngine().also { instance = it }
            }
        }

        // Scoring weights (calibrated for LabanKey-style balance: exact match protected when neutral,
        // but high-frequency / strong context neighbors gracefully override obvious fat-finger typos)
        private const val EXACT_MATCH_BONUS = 110.0   // Favor exact match when context is neutral
        private const val WEIGHT_PENALTY = 55.0       // Penalty multiplier for fat-finger keystrokes
        private const val WEIGHT_UNIGRAM = 0.65       // Base Unigram frequency weight
        private const val WEIGHT_BIGRAM = 10.0        // Context Bigram weight (strengthened for LabanKey context sensitivity)
        private const val WEIGHT_TRIGRAM = 20.0       // Context Trigram weight
        private const val WEIGHT_USER_UNIGRAM = 2.5   // Bonus weight for words learned from user
        private const val WEIGHT_USER_BIGRAM = 12.0   // Bonus weight for word pairs learned from user
        private const val WEIGHT_USER_TRIGRAM = 24.0  // Bonus weight for 3-word habits learned from user
        private const val MAX_PENALTY_THRESHOLD = 1.4   // Maximum allowed cumulative key distance penalty for short words

        /**
         * Key slips on Telex diacritic modifier keys (QWERTY adjacent keys).
         * Mispressing an adjacent key instead of the intended tone or vowel modifier key.
         */
        val TELEX_DIACRITIC_SLIPS: Map<Char, List<Char>> = mapOf(
            // Physical QWERTY neighbors of 's' (sắc) and 'w' (vowel horn/breve)
            'a' to listOf('s', 'w'),
            // Neighbors of 's', 'f', 'r', 'x'
            'd' to listOf('s', 'f', 'r', 'x'),
            // Neighbors of 's'
            'w' to listOf('s'),
            // Neighbors of 'r', 'w', 's'
            'e' to listOf('r', 'w', 's'),
            // Neighbors of 'f' (huyền)
            'g' to listOf('f'),
            // Neighbors of 'r', 'f'
            't' to listOf('r', 'f'),
            // Neighbors of 'x' (ngã), 'f'
            'c' to listOf('x', 'f'),
            // Neighbors of 'f' (huyền)
            'v' to listOf('f'),
            // Neighbors of 'x', 's'
            'z' to listOf('x', 's'),
            // Neighbors of 's'
            'x' to listOf('s'),
            // Neighbors of 'x', 'w'
            's' to listOf('x', 'w'),
            // Neighbors of 'f'
            'r' to listOf('f'),
            // Neighbors of 'r'
            'f' to listOf('r'),
            // Neighbors of 'j' (nặng)
            'h' to listOf('j'),
            'k' to listOf('j'),
            'u' to listOf('j'),
            'i' to listOf('j'),
            'y' to listOf('j'),
            'n' to listOf('j'),
            'm' to listOf('j'),
            // Neighbors of 'w'
            'q' to listOf('w')
        )
    }

    @VisibleForTesting
    fun resetForTesting() {
        trie.root.children.clear()
        proximityManager.neighborsMap.clear()
        languageModel.unigrams.clear()
        languageModel.bigrams.clear()
        languageModel.trigrams.clear()
        languageModel.ngramPack = NgramPack.EMPTY
        languageModel.clearCache()
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

            // Fallback to JSON bigrams/trigrams only when binary pack is not loaded
            if (!packLoaded) {
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

    fun ensureBundledNgramPack(assets: AssetManager, filesDir: File): File? {
        val dir = File(File(filesDir, "dict"), "bundled")
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val outFile = File(dir, "telex_ngrams.wmng")
        val expectedLength = try {
            assets.openFd("telex/ngrams.wmng").length
        } catch (_: Exception) {
            -1L
        }
        if (outFile.isFile && outFile.length() > 0 && (expectedLength <= 0 || outFile.length() == expectedLength)) {
            return outFile
        }
        return try {
            val tmp = File(dir, "telex_ngrams.wmng.tmp")
            if (tmp.exists()) tmp.delete()
            assets.open("telex/ngrams.wmng").use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (outFile.exists()) outFile.delete()
            if (tmp.renameTo(outFile)) outFile else (if (outFile.isFile && outFile.length() > 0) outFile else null)
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
            if (!VietnameseOrthography.isValidVietnameseSyllable(word) && !TelexWhitelist.isWhitelisted(word)) continue
            trie.insert(telex, word, freq)
        }
    }

    fun isAccented(word: String): Boolean {
        if (word.isEmpty()) return false
        val lower = word.lowercase()
        if (TelexWhitelist.isWhitelisted(lower)) return false

        // 1. Unicode accented characters or combining marks
        for (ch in lower) {
            if (ch !in 'a'..'z') {
                if (ch.isLetter() || ch in '\u0300'..'\u036f' || ch in '\u1dc0'..'\u1dff') return true
            }
        }

        // 2. Telex vowel diacritics: 'w' (ă, ơ, ư), 'aa' (â), 'ee' (ê), 'oo' (ô), 'dd' (đ)
        if (lower.contains('w')) return true
        if (lower.contains("aa") || lower.contains("ee") || lower.contains("oo") || lower.contains("dd")) return true

        // 3. Telex tones:
        // 'f' (huyền) and 'j' (nặng) are tone markers when not at initial position (index > 0)
        for (i in 1 until lower.length) {
            val ch = lower[i]
            if (ch == 'f' || ch == 'j') return true
        }

        // 's' (sắc), 'r' (hỏi), 'x' (ngã) placed at the end of the syllable (standard/canonical Telex)
        val lastChar = lower.last()
        if (lastChar == 's' || lastChar == 'x') return true
        if (lastChar == 'r' && !(lower.length == 2 && lower.startsWith("t"))) return true

        // Transposed tone markers in bimanual desync (e.g. "toasn" -> 's' transposed before coda 'n', "tieesng" -> 's' before 'ng')
        // Left hand hits tone (s, r, x) before Right hand hits nasal coda (n, m, ng, nh)
        for (i in 1 until lower.length - 1) {
            val ch = lower[i]
            if ((ch == 's' || ch == 'r' || ch == 'x') && lower[i - 1] in "aeiouy") {
                val next = lower[i + 1]
                if (next == 'n' || next == 'm' || (next == 'g' && lower.endsWith("ng")) || (next == 'h' && lower.endsWith("nh"))) {
                    return true
                }
            }
        }

        return false
    }

    fun isWordInDictionary(word: String): Boolean {
        val clean = word.lowercase().trim()
        if (clean.isEmpty()) return false
        return languageModel.unigrams.containsKey(clean) ||
                TelexWhitelist.isWhitelisted(clean) ||
                (trie.root.children.isNotEmpty() && trie.findWord(toCanonicalTelex(clean)) != null)
    }

    fun isWhitelisted(word: String): Boolean {
        return TelexWhitelist.isWhitelisted(word)
    }


    @Synchronized
    fun initialize(context: Context) {
        initialize(context.assets, context.filesDir)
    }

    /**
     * Converts a Vietnamese word or composing buffer (which may contain precomposed
     * characters or combining tone marks from flick gestures) into a canonical Telex
     * keystroke sequence (e.g. "chao\u0300" -> "chaof", "toán" -> "toans", "tuệ" -> "tueej", "người" -> "nguowif").
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
                '\u031b' -> {
                    // Horn: 'o' or 'u' + 'w' -> 'ow' or 'uw'.
                    // For 'ươ' (u+horn + o+horn), canonical telex is 'uow', not 'uwow'.
                    if (sb.length >= 2 && sb[sb.length - 1].lowercaseChar() == 'o' && sb[sb.length - 2].lowercaseChar() == 'w') {
                        if (sb.length >= 3 && sb[sb.length - 3].lowercaseChar() == 'u') {
                            val upper = sb[sb.length - 1].isUpperCase()
                            sb.deleteCharAt(sb.length - 2)
                            sb.append(if (upper) 'W' else 'w')
                        } else {
                            sb.append('w')
                        }
                    } else {
                        sb.append('w')
                    }
                }
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
     * Prioritizes user habit learning (UserLexicon trigram/bigram), then binary NgramPack (.wmng),
     * and finally in-memory JSON fallback.
     */
    fun predictNextWords(
        previousWord: String?,
        previousWord2: String? = null,
        userLexicon: UserLexicon? = null,
        maxResults: Int = 3
    ): List<String> {
        if (!isReady || previousWord.isNullOrBlank()) return emptyList()
        val prev1 = previousWord.trim().lowercase()
        val prev2 = previousWord2?.trim()?.lowercase()

        val results = LinkedHashSet<String>()

        // 0. High priority: UserLexicon Trigrams & Bigrams (personalized learning)
        if (userLexicon != null) {
            if (!prev2.isNullOrBlank()) {
                val userTrigramCandidates = userLexicon.nextWordsAfter(prev2, prev1, maxResults)
                for (w in userTrigramCandidates) {
                    val display = userLexicon.displayOf(w) ?: w
                    results.add(display)
                    if (results.size >= maxResults) return results.toList()
                }
            }
            val userBigramCandidates = userLexicon.nextWords(prev1, maxResults)
            for (w in userBigramCandidates) {
                val display = userLexicon.displayOf(w) ?: w
                results.add(display)
                if (results.size >= maxResults) return results.toList()
            }
            if (!prev2.isNullOrBlank()) {
                val userSkipCandidates = userLexicon.skip1Followers(prev2, maxResults)
                for (w in userSkipCandidates) {
                    val display = userLexicon.displayOf(w) ?: w
                    results.add(display)
                    if (results.size >= maxResults) return results.toList()
                }
            }
        }

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

        // 5. Fallback to top unigrams if needed
        if (results.size < maxResults) {
            for (w in topUnigrams(maxResults)) {
                results.add(w)
                if (results.size >= maxResults) break
            }
        }

        return results.toList()
    }

    /**
     * Finds Vietnamese word completions matching a prefix (e.g. "việ" -> ["việt", "việc", ...],
     * "vie" -> ["việt", "việc", "viên", ...]).
     * Prioritizes completions from UserLexicon when available.
     */
    fun findCompletions(
        prefix: String,
        previousWord: String? = null,
        userLexicon: UserLexicon? = null,
        maxResults: Int = 5
    ): List<String> {
        if (!isReady || prefix.isBlank()) return emptyList()
        val clean = prefix.trim().lowercase()
        val prev = previousWord?.trim()?.lowercase()
        val results = LinkedHashSet<String>()

        // 1. User personalized completions
        if (userLexicon != null) {
            val userCompletions = userLexicon.complete(clean, maxResults)
            for (sug in userCompletions) {
                val sugWord = userLexicon.displayOf(sug.word) ?: sug.word
                if (!clean.contains(' ') && (sug.word.contains(' ') || sugWord.contains(' '))) continue
                if (sug.word != clean) {
                    results.add(sugWord)
                    if (results.size >= maxResults) return results.toList()
                }
            }
        }

        // 2. Static completions boosted by Bigram context when previousWord is present
        val candidates = languageModel.unigrams.entries
            .filter { (it.key.startsWith(clean) || matchesVietnamesePrefix(it.key, clean)) && it.key != clean }

        val sorted = if (!prev.isNullOrEmpty()) {
            candidates.sortedByDescending { entry ->
                val biScore = languageModel.getBigramScore(prev, entry.key)
                entry.value + biScore * 8
            }
        } else {
            candidates.sortedByDescending { it.value }
        }

        for (w in sorted.take(maxResults)) {
            results.add(w.key)
            if (results.size >= maxResults) break
        }

        return results.toList()
    }

    /**
     * Returns top Vietnamese unigrams sorted by frequency.
     */
    fun topUnigrams(limit: Int = 30): List<String> {
        if (!isReady) return emptyList()
        return languageModel.topUnigrams(limit)
    }

    /**
     * Next-letter distribution for smart key-hit detection (Autopilot) on Vietnamese Telex.
     * Walks the syllable trie to find all valid next keystrokes following [prefix],
     * weighted by the maximum unigram frequency in each subtree.
     */
    fun nextLetterWeights(prefix: String): Map<Char, Float> {
        if (!isReady || prefix.isEmpty()) return emptyMap()
        val lower = prefix.lowercase()
        val node = trie.find(lower) ?: trie.find(toCanonicalTelex(lower).lowercase()) ?: return emptyMap()
        if (node.children.isEmpty()) return emptyMap()

        val tally = HashMap<Char, Double>()
        for ((char, child) in node.children) {
            if (char.isLetter()) {
                val score = if (child.maxSubtreeScore > 0) child.maxSubtreeScore.toDouble() else 1.0
                tally[char] = score
            }
        }
        val max = tally.values.maxOrNull() ?: return emptyMap()
        if (max <= 0.0) return emptyMap()
        return tally.mapValues { (it.value / max).toFloat() }
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
        correctionMemory: CorrectionMemory? = null,
        maxResults: Int = 3
    ): List<TelexCorrectionCandidate> {
        if (!isReady) return emptyList()

        val cleanInput = rawInput.trim().lowercase()
        if (cleanInput.length < 2 || cleanInput.length > 16) return emptyList()
        if (TelexWhitelist.isWhitelisted(cleanInput)) return emptyList()

        val cleanPrev = previousWord?.trim()?.lowercase()
        val cleanPrev2 = previousWord2?.trim()?.lowercase()

        val rawCandidates = ArrayList<TelexCorrectionCandidate>()
        val seenWords = HashSet<String>()

        val taught = if (correctionMemory != null) {
            correctionMemory.fixFor(cleanInput) ?: correctionMemory.fixFor(toCanonicalTelex(cleanInput))
        } else null

        fun evaluateCandidate(unicodeWord: String, telexVariant: String, penalty: Double) {
            val lowerWord = unicodeWord.lowercase()
            if (lowerWord.isEmpty()) return
            val isTaught = taught != null && unicodeWord.equals(taught.fixed, ignoreCase = true)
            if (!isTaught && isWordInDictionary(cleanInput) && !isAccented(cleanInput) && isAccented(unicodeWord)) {
                val hasContext = !cleanPrev.isNullOrEmpty() && (
                    languageModel.getBigramScore(cleanPrev, unicodeWord) > 0 ||
                    (userLexicon?.bigramCount(cleanPrev, unicodeWord) ?: 0) > 0
                )
                if (!hasContext && cleanInput.equals(unicodeWord, ignoreCase = true)) return
            }
            val inUserLexicon = userLexicon?.contains(unicodeWord) == true
            if (!isTaught && !isWordInDictionary(unicodeWord) && !inUserLexicon) return
            if (!isTaught && !inUserLexicon && !VietnameseOrthography.isValidVietnameseSyllable(unicodeWord) && !TelexWhitelist.isWhitelisted(unicodeWord)) return
            if (!seenWords.add(lowerWord)) return

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
            val userTrigramCount = if (!cleanPrev.isNullOrEmpty() && !cleanPrev2.isNullOrEmpty()) {
                userLexicon?.trigramCount(cleanPrev2, cleanPrev, unicodeWord) ?: 0
            } else 0
            val exactBonus = if (penalty < 0.001) EXACT_MATCH_BONUS else 0.0

            val taughtBonus = if (isTaught && taught != null) {
                val isExactKnown = isWordInDictionary(cleanInput)
                if (!isExactKnown && taught.count >= CorrectionMemory.APPLY_AT) {
                    taught.count * 1500.0
                } else if (isExactKnown && taught.count >= CorrectionMemory.APPLY_KNOWN_AT) {
                    taught.count * 2000.0
                } else {
                    taught.count * 500.0
                }
            } else 0.0

            val totalScore = exactBonus + taughtBonus -
                    (penalty * WEIGHT_PENALTY) +
                    (baseUnigram * WEIGHT_UNIGRAM) +
                    (baseBigram * WEIGHT_BIGRAM) +
                    (baseTrigram * WEIGHT_TRIGRAM) +
                    (userUnigramCount * WEIGHT_USER_UNIGRAM) +
                    (userBigramCount * WEIGHT_USER_BIGRAM) +
                    (userTrigramCount * WEIGHT_USER_TRIGRAM)

            rawCandidates.add(
                TelexCorrectionCandidate(
                    word = applyCasing(unicodeWord, rawInput),
                    telex = telexVariant,
                    penalty = penalty,
                    score = totalScore
                )
            )
        }

        // 0. Learned correction candidate from CorrectionMemory
        if (taught != null && taught.count >= CorrectionMemory.APPLY_AT) {
            val telexFixed = toCanonicalTelex(taught.fixed)
            evaluateCandidate(taught.fixed, telexFixed, 0.0)
        }

        // 1. Exact match bonus
        val exactWord = trie.findWord(cleanInput)
        if (exactWord != null) {
            evaluateCandidate(exactWord, cleanInput, 0.0)
        }
        if (userLexicon?.contains(cleanInput) == true) {
            val userWord = userLexicon.displayOf(cleanInput) ?: cleanInput
            evaluateCandidate(userWord, cleanInput, 0.0)
        }

        // 2. Bimanual Typing Desync Candidates (Left-Right hand timing and transposition errors)
        val desyncCandidates = BimanualDesyncEngine.generateCandidates(cleanInput, this)
        for (desync in desyncCandidates) {
            evaluateCandidate(desync.word, desync.telex, desync.penalty)
        }

        // 3. Telex Diacritic Key Slips (Mispressing adjacent keys for Telex tones/modifiers)
        if (cleanInput.length >= 2) {
            val chars = cleanInput.toCharArray()
            val lastIdx = chars.lastIndex

            for (i in chars.indices) {
                val ch = chars[i]
                val slips = TELEX_DIACRITIC_SLIPS[ch] ?: continue

                // Contextual classification (UniKey KeyCorrection style):
                // 1) Is this key slip at the final diacritic position (end of syllable)?
                val isFinalPosition = (i == lastIdx)
                // 2) Is this key slip immediately following a vowel (post-nucleus position before coda)?
                val isPostVowel = (i > 0 && chars[i - 1] in "aeiouyw")
                val isBeforeCoda = (i < lastIdx && chars[i + 1] in "nmcptgh")

                for (replacement in slips) {
                    chars[i] = replacement
                    val variant = String(chars)
                    val word = trie.findWord(variant)

                    // Contextual penalty:
                    // Slipping onto an adjacent key at the syllable suffix or post-nucleus diacritic position
                    // is a high-probability diacritic slip error, so assign lower penalty to ensure top ranking.
                    val slipPenalty = when {
                        isFinalPosition && replacement in "sfrxjw" -> 0.16
                        isPostVowel && isBeforeCoda && replacement in "sfrxj" -> 0.19
                        isPostVowel && replacement in "sfrxjw" -> 0.22
                        else -> 0.28
                    }

                    if (word != null) {
                        evaluateCandidate(word, variant, slipPenalty)
                    }

                    val desyncSub = BimanualDesyncEngine.generateCandidates(variant, this)
                    for (sub in desyncSub) {
                        val desyncPenalty = if (isPostVowel && replacement in "sfrxj") 0.24 else 0.35
                        evaluateCandidate(sub.word, sub.telex, desyncPenalty)
                    }
                    chars[i] = ch
                }
            }
        }

        // 4. Missing / Slipped Modifier Recovery (e.g. 'd'+slip -> "dd", "d" -> "dd" for "đ", single vowel -> double vowel)
        // 4a. Initial 'd' modifier slip: e.g. user typed 'd' + neighbor ('s','f','e','c','x','r') instead of 'dd' (đ)
        if (cleanInput.length >= 2 && cleanInput[0] == 'd' && !cleanInput.startsWith("dd")) {
            val second = cleanInput[1]
            if (second in "sfecxr") {
                val withDd = "dd" + cleanInput.substring(2)
                val word = trie.findWord(withDd)
                if (word != null) {
                    evaluateCandidate(word, withDd, 0.16)
                }
                val desyncDd = BimanualDesyncEngine.generateCandidates(withDd, this)
                for (sub in desyncDd) {
                    evaluateCandidate(sub.word, sub.telex, 0.20)
                }
            }
        }

        // 4b. Single 'd' instead of 'dd'
        if (cleanInput.startsWith("d") && !cleanInput.startsWith("dd")) {
            val withDd = "d$cleanInput"
            val word = trie.findWord(withDd)
            if (word != null) {
                evaluateCandidate(word, withDd, 0.18)
            }
            val desyncDd = BimanualDesyncEngine.generateCandidates(withDd, this)
            for (sub in desyncDd) {
                evaluateCandidate(sub.word, sub.telex, 0.22)
            }
        }

        // 4c. Slipped second vowel in doubled vowel pairs (aa -> â, ee -> ê, oo -> ô)
        for (i in 0 until cleanInput.length - 1) {
            val vChar = cleanInput[i]
            val nextChar = cleanInput[i + 1]
            val vowelSlipNeighbors = when (vChar) {
                'a' -> "swqz"
                'e' -> "wrds"
                'o' -> "iplk"
                else -> null
            }
            if (vowelSlipNeighbors != null && nextChar in vowelSlipNeighbors) {
                val withDoubled = cleanInput.substring(0, i + 1) + vChar + cleanInput.substring(i + 2)
                val word = trie.findWord(withDoubled)
                if (word != null) {
                    evaluateCandidate(word, withDoubled, 0.18)
                }
                val desyncDoubled = BimanualDesyncEngine.generateCandidates(withDoubled, this)
                for (sub in desyncDoubled) {
                    evaluateCandidate(sub.word, sub.telex, 0.22)
                }
            }
        }

        // 4d. Single vowel instead of doubled vowel
        for (i in cleanInput.indices) {
            val ch = cleanInput[i]
            if (ch == 'a' || ch == 'e' || ch == 'o') {
                val doubled = cleanInput.substring(0, i + 1) + ch + cleanInput.substring(i + 1)
                val word = trie.findWord(doubled)
                if (word != null) {
                    evaluateCandidate(word, doubled, 0.22)
                }
            }
        }

        // 5. Repeated / Sticking Key Recovery (Double-tap cancel or key bounce: "toanss" -> "toans" [toán], "chaoff" -> "chào")
        for (i in 0 until cleanInput.length - 1) {
            if (cleanInput[i] == cleanInput[i + 1]) {
                val dedupped = cleanInput.removeRange(i, i + 1)
                val word = trie.findWord(dedupped)
                if (word != null) {
                    evaluateCandidate(word, dedupped, 0.15)
                }
            }
        }

        // 6. QWERTY Key Proximity Candidates (Zero-Allocation DFS)
        // Only run proximity substitution on words of length >= 3. For 2-letter words,
        // substituting 1 character alters 50% of the word, producing false positives on abbreviations
        // like "hn" -> "un" or "dt" -> "st". For longer words (>= 5), allow up to 2 proximity typos.
        if (cleanInput.length >= 3) {
            val charBuffer = CharArray(16)
            val maxAllowedErrors = if (cleanInput.length >= 5) 2 else 1
            val maxAllowedPenalty = if (cleanInput.length >= 5) 2.2 else MAX_PENALTY_THRESHOLD

            fun dfs(node: TelexTrieNode, idx: Int, currentPenalty: Double, errorCount: Int) {
                if (idx == cleanInput.length) {
                    node.word?.let { unicodeWord ->
                        evaluateCandidate(unicodeWord, String(charBuffer, 0, idx), currentPenalty)
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

                    // Allows 1 substituted key for short words, and 2 substituted keys for longer words (LabanKey style)
                    if (nextErrors <= maxAllowedErrors && nextTotalPenalty <= maxAllowedPenalty) {
                        val nextNode = node.children[nextChar]
                        if (nextNode != null) {
                            charBuffer[idx] = nextChar
                            dfs(nextNode, idx + 1, nextTotalPenalty, nextErrors)
                        }
                    }
                }
            }

            dfs(trie.root, 0, 0.0, 0)
        }

        // 7. Missing Tones / Unaccented Recovery via VietnameseGlide
        val unaccentedStem = VietnameseGlide.toUnaccentedStem(cleanInput)
        if (unaccentedStem.isNotEmpty() && unaccentedStem.length <= 8) {
            val glideCandidates = VietnameseGlide.lookup(unaccentedStem)
            for (candWord in glideCandidates) {
                if (!candWord.contains(' ')) {
                    val telex = toCanonicalTelex(candWord)
                    evaluateCandidate(candWord, telex, 0.15)
                }
            }
        }

        // 8. Vietnamese Dialect / Regional Sound Slips (s <-> x, tr <-> ch, d <-> gi <-> r)
        if (cleanInput.length >= 2) {
            val dialectVariants = generateDialectVariants(cleanInput)
            for (variant in dialectVariants) {
                val word = trie.findWord(variant)
                if (word != null) {
                    evaluateCandidate(word, variant, 0.25)
                }
                val unacc = VietnameseGlide.toUnaccentedStem(variant)
                val glideVars = VietnameseGlide.lookup(unacc)
                for (candWord in glideVars) {
                    if (!candWord.contains(' ')) {
                        evaluateCandidate(candWord, toCanonicalTelex(candWord), 0.30)
                    }
                }
            }
        }

        if (rawCandidates.isEmpty()) return emptyList()

        rawCandidates.sort()

        val uniqueResults = ArrayList<TelexCorrectionCandidate>()
        val resultWords = HashSet<String>()

        for (cand in rawCandidates) {
            if (resultWords.add(cand.word.lowercase())) {
                uniqueResults.add(cand)
                if (uniqueResults.size >= maxResults) break
            }
        }

        return uniqueResults
    }

    /**
     * Match output casing to input format (lowercase, Titlecase, UPPERCASE).
     */
    fun applyCasing(word: String, originalInput: String): String {
        if (word.isEmpty() || originalInput.isEmpty()) return word
        val isAllUpper = originalInput.all { it.isUpperCase() }
        if (isAllUpper) return word.uppercase()
        val isFirstUpper = originalInput[0].isUpperCase()
        if (isFirstUpper) {
            return word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return word
    }

    /**
     * Evaluates the score of a word given context (previousWord, previousWord2, userLexicon).
     * Used to compare a composed word against a top autocorrection candidate.
     */
    fun evaluateScore(
        word: String,
        previousWord: String?,
        previousWord2: String?,
        userLexicon: UserLexicon?
    ): Double {
        val cleanWord = word.trim().lowercase()
        val cleanPrev = previousWord?.trim()?.lowercase()
        val cleanPrev2 = previousWord2?.trim()?.lowercase()

        val baseUnigram = languageModel.getUnigramScore(cleanWord)
        var baseBigram = 0
        var baseTrigram = 0
        if (!cleanPrev.isNullOrEmpty()) {
            baseBigram = languageModel.getBigramScore(cleanPrev, cleanWord)
            if (!cleanPrev2.isNullOrEmpty()) {
                baseTrigram = languageModel.getTrigramScore(cleanPrev2, cleanPrev, cleanWord)
            }
        }
        val userUnigramCount = userLexicon?.frequencyOf(cleanWord) ?: 0
        val userBigramCount = if (!cleanPrev.isNullOrEmpty()) {
            userLexicon?.bigramCount(cleanPrev, cleanWord) ?: 0
        } else 0
        val userTrigramCount = if (!cleanPrev.isNullOrEmpty() && !cleanPrev2.isNullOrEmpty()) {
            userLexicon?.trigramCount(cleanPrev2, cleanPrev, cleanWord) ?: 0
        } else 0

        return EXACT_MATCH_BONUS +
                (baseUnigram * WEIGHT_UNIGRAM) +
                (baseBigram * WEIGHT_BIGRAM) +
                (baseTrigram * WEIGHT_TRIGRAM) +
                (userUnigramCount * WEIGHT_USER_UNIGRAM) +
                (userBigramCount * WEIGHT_USER_BIGRAM) +
                (userTrigramCount * WEIGHT_USER_TRIGRAM)
    }

    /**
     * Generates common Vietnamese regional / dialect sound slip variants
     * (e.g. s <-> x, tr <-> ch, d <-> gi <-> r) for fuzzy autocorrection.
     */
    private fun generateDialectVariants(input: String): List<String> {
        val lower = input.lowercase()
        val results = mutableListOf<String>()

        when {
            lower.startsWith("tr") -> results.add("ch" + lower.substring(2))
            lower.startsWith("ch") -> results.add("tr" + lower.substring(2))
            lower.startsWith("s") -> results.add("x" + lower.substring(1))
            lower.startsWith("x") -> results.add("s" + lower.substring(1))
            lower.startsWith("gi") -> {
                results.add("d" + lower.substring(2))
                results.add("r" + lower.substring(2))
            }
            lower.startsWith("d") && !lower.startsWith("dd") -> {
                results.add("gi" + lower.substring(1))
                results.add("r" + lower.substring(1))
            }
            lower.startsWith("r") -> {
                results.add("d" + lower.substring(1))
                results.add("gi" + lower.substring(1))
            }
            lower.startsWith("ngh") -> results.add("ng" + lower.substring(3))
            lower.startsWith("ng") && !lower.startsWith("ngh") -> results.add("ngh" + lower.substring(2))
        }

        return results
    }

    private fun readAsset(assets: AssetManager, path: String): String {
        assets.open(path).use { stream ->
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            return reader.readText()
        }
    }
}
