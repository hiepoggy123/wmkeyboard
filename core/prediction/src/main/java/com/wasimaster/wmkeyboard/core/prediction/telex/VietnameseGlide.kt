package com.wasimaster.wmkeyboard.core.prediction.telex

import android.content.res.AssetManager
import androidx.annotation.VisibleForTesting
import com.wasimaster.wmkeyboard.core.gesture.RomanizedIndex
import com.wasimaster.wmkeyboard.core.prediction.Accents
import com.wasimaster.wmkeyboard.core.prediction.CustomDictionaries
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import com.wasimaster.wmkeyboard.core.prediction.Suggestion
import com.wasimaster.wmkeyboard.core.prediction.Trie
import com.wasimaster.wmkeyboard.core.prediction.TrieWalker
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.prediction.WordSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln

/**
 * Provides unaccented-to-accented Vietnamese syllable and word mapping for Glide typing.
 * Builds a PackedTrie of unaccented stems and resolves gesture readings into native
 * Vietnamese syllables ranked by frequency and context.
 *
 * Supports Chinese Pinyin-style 3-column format (`reading\tword\tfreq`) and 2-column format,
 * integrating 1-syllable unigram frequencies from the bundled language model, as well as
 * dynamic multi-word compound stem synthesis from UserLexicon with phonotactic gating.
 */
object VietnameseGlide {
    private const val STEMS_ASSET_PATH = "telex/vietnamese_stems.tsv"
    private const val UNIGRAMS_ASSET_PATH = "telex/unigrams.json"
    private const val BIGRAMS_ASSET_PATH = "telex/bigrams.json"
    private const val VIETNAMESE_KEYS = "abcdefghijklmnopqrstuvwxyz"

    private const val MIN_USER_COMPOUND_COUNT = 2
    private const val MAX_COMPOUND_STEM_LENGTH = 24

    private val TEENCODE_BLOCKED = setOf(
        "ko", "dc", "bt", "j", "z", "uhm", "uh", "ib", "nt", "mn", "hqua", "ngta", "ae", "đc", "k", "hổng",
    )

    @Volatile
    var currentBlacklist: Set<String> = emptySet()

    @Volatile
    private var cachedIndex: RomanizedIndex? = null
    @Volatile
    private var syllableMap: Map<String, List<String>>? = null

    // Recognized single-syllable forms for phonotactic anti-junk filtering
    private val recognizedSyllables = ConcurrentHashMap.newKeySet<String>()

    // Unigrams frequency cache
    @Volatile
    private var cachedUnigrams: Map<String, Int> = emptyMap()

    data class GlideContext(
        val prev: String? = null,
        val prev2: String? = null
    )

    private class UserCompoundsSnapshot(
        val map: Map<String, List<String>> = emptyMap(),
        val wordFreq: Map<String, Int> = emptyMap(),
        val stemMaxFreq: Map<String, Int> = emptyMap(),
        val trie: Trie = Trie()
    )

    @Volatile
    private var currentContext = GlideContext()

    val currentPreviousWord: String?
        get() = currentContext.prev

    val currentPreviousWord2: String?
        get() = currentContext.prev2

    fun setContext(prev: String?, prev2: String?) {
        currentContext = GlideContext(prev, prev2)
    }

    // Dynamic compound stems learned from UserLexicon and custom dictionaries
    @Volatile
    private var lastLearnedMutationCount = -1L
    @Volatile
    private var userCompoundsSnapshot = UserCompoundsSnapshot()

    private val personalWordSource = object : WordSource {
        override fun complete(prefix: String, limit: Int): List<Suggestion> =
            userCompoundsSnapshot.trie.complete(prefix, limit)
        override fun frequencyOf(word: String): Int =
            userCompoundsSnapshot.trie.frequencyOf(word)
        override fun contains(word: String): Boolean =
            userCompoundsSnapshot.trie.contains(word)
        override fun walkers(): List<TrieWalker> =
            userCompoundsSnapshot.trie.walkers()
        override fun ownWalkers(): List<TrieWalker> =
            userCompoundsSnapshot.trie.ownWalkers()
    }

    /**
     * Converts a Vietnamese phrase or syllable into an unaccented Latin stem.
     * E.g. "chi rứa" -> "chirua", "Việt Nam" -> "vietnam".
     */
    fun toUnaccentedStem(phrase: String): String {
        val sb = StringBuilder(phrase.length)
        for (ch in phrase) {
            if (ch == ' ' || ch == '-' || ch == '_' || ch == '\t') continue
            val lower = ch.lowercaseChar()
            val bare = Accents.bare(lower)
            if (bare in 'a'..'z') {
                sb.append(bare)
            }
        }
        return sb.toString()
    }

    /**
     * Checks if a word is a valid Vietnamese syllable (exists in unigrams or syllable vocabulary).
     */
    fun isKnownSyllable(word: String): Boolean {
        val lower = word.lowercase()
        if (lower.length > 8 || lower in TEENCODE_BLOCKED) return false
        if (!lower.all { it.isLetter() }) return false
        return cachedUnigrams.containsKey(lower) || recognizedSyllables.contains(lower)
    }

    /**
     * Synthesizes unaccented compound stems from phrases explicitly added by user in Personal Dictionary
     * or custom imported dictionaries. Automatic n-gram learning is completely excluded to prevent junk stems.
     */
    fun updateUserCompounds(userLexicon: UserLexicon?, filesDir: File? = null) {
        val currentMutations = userLexicon?.mutationCount() ?: 0L
        if (currentMutations == lastLearnedMutationCount && lastLearnedMutationCount != -1L) {
            return
        }
        try {
            val freshMap = HashMap<String, MutableList<Pair<String, Int>>>()
            val freshWordFreq = HashMap<String, Int>()
            val freshStemMaxFreq = HashMap<String, Int>()

            fun processPhrase(parts: List<String>) {
                if (parts.size !in 2..4) return
                for (part in parts) {
                    val p = part.trim()
                    // Critical safety guard: NEVER allow 1-letter words (such as "ơ", "ở", "ý", "u", "ê")
                    // to be glued into compound stems like "okia", "onha", "odau".
                    if (p.length < 2) return
                    if (p.isEmpty() || !isKnownSyllable(p)) {
                        return
                    }
                }
                val phrase = parts.joinToString(" ") { it.trim() }
                val stem = toUnaccentedStem(phrase)
                if (stem.length < 3 || stem.length > MAX_COMPOUND_STEM_LENGTH) return
                if (!stem.all { it in 'a'..'z' }) return

                // User manual additions get top priority
                val freq = 250
                val list = freshMap.getOrPut(stem) { mutableListOf() }
                list.add(phrase to freq)
            }

            // 1. Process custom compound phrases explicitly added by hand in Personal Dictionary
            if (userLexicon != null) {
                for ((word, _) in userLexicon.allWords()) {
                    if (word.contains(' ') && userLexicon.isAddedByHand(word)) {
                        val parts = word.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
                        if (parts.size in 2..4) {
                            processPhrase(parts)
                        }
                    }
                }
            }

            // 2. Process custom compound phrases from imported Custom Dictionaries (filesDir/dictionaries/vi/*.txt)
            if (filesDir != null) {
                val customLists = CustomDictionaries.lists(filesDir, "vi")
                for (file in customLists) {
                    runCatching {
                        file.bufferedReader().useLines { lines ->
                            for (line in lines) {
                                val trimmed = line.trim()
                                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                                val phrase = trimmed.split(Regex("\\s+\\d+$"))[0].trim()
                                if (phrase.contains(' ')) {
                                    val parts = phrase.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
                                    if (parts.size in 2..4) {
                                        processPhrase(parts)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val finalMap = HashMap<String, List<String>>()
            val freshTrie = Trie()

            for ((stem, list) in freshMap) {
                val sorted = list.groupBy { it.first }
                    .map { (phrase, pairs) -> phrase to pairs.maxOf { it.second } }
                    .sortedByDescending { it.second }

                finalMap[stem] = sorted.map { it.first }
                val maxFreq = sorted.firstOrNull()?.second ?: 1
                freshStemMaxFreq[stem] = maxFreq

                for ((phrase, f) in sorted) {
                    freshWordFreq["$stem\t$phrase"] = f
                }

                freshTrie.insert(stem, maxFreq)
            }

            userCompoundsSnapshot = UserCompoundsSnapshot(
                map = finalMap,
                wordFreq = freshWordFreq,
                stemMaxFreq = freshStemMaxFreq,
                trie = freshTrie
            )

            VietnameseTrajectoryGlide.clearUserCompounds()
            for ((stem, maxFreq) in freshStemMaxFreq) {
                VietnameseTrajectoryGlide.addUserCompound(stem, maxFreq)
            }
            lastLearnedMutationCount = currentMutations
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Loads the Vietnamese unaccented trie and builds the RomanizedIndex.
     */
    fun getIndex(assets: AssetManager, userLexicon: UserLexicon? = null, filesDir: File? = null): RomanizedIndex? {
        cachedIndex?.let {
            if (userLexicon != null || filesDir != null) {
                updateUserCompounds(userLexicon, filesDir)
            }
            return it
        }
        synchronized(this) {
            cachedIndex?.let {
                if (userLexicon != null || filesDir != null) {
                    updateUserCompounds(userLexicon, filesDir)
                }
                return it
            }
            return try {
                val unigrams = try {
                    assets.open(UNIGRAMS_ASSET_PATH).use { parseUnigrams(it) }
                } catch (_: Exception) {
                    emptyMap()
                }
                val ngramPackLoaded = !TelexAutocorrectEngine.getInstance().languageModel.ngramPack.isEmpty
                val bigrams = if (!ngramPackLoaded) {
                    try {
                        assets.open(BIGRAMS_ASSET_PATH).use { parseBigrams(it) }
                    } catch (_: Exception) {
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }
                val stemsStream = try {
                    assets.open(STEMS_ASSET_PATH)
                } catch (_: Exception) {
                    null
                }
                if (stemsStream != null) {
                    stemsStream.use { loadFromStream(it, unigrams, bigrams, userLexicon, filesDir) }
                } else {
                    buildFromUnigrams(unigrams, bigrams, userLexicon, filesDir)
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
     * Parses word1 -> { word2 -> freq } mapping from bigrams.json.
     */
    fun parseBigrams(stream: InputStream): Map<String, Map<String, Int>> {
        return try {
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            val root = Json.parseToJsonElement(text).jsonObject
            val map = HashMap<String, Map<String, Int>>(root.size)
            for ((w1, inner) in root) {
                val innerObj = inner.jsonObject
                val innerMap = HashMap<String, Int>(innerObj.size)
                for ((w2, score) in innerObj) {
                    innerMap[w2.lowercase()] = score.jsonPrimitive.intOrNull ?: 150
                }
                map[w1.lowercase()] = innerMap
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Builds RomanizedIndex directly from unigrams (single-syllable Vietnamese vocabulary).
     */
    fun buildFromUnigrams(
        unigrams: Map<String, Int>,
        bigrams: Map<String, Map<String, Int>> = emptyMap(),
        userLexicon: UserLexicon? = null,
        filesDir: File? = null,
    ): RomanizedIndex {
        cachedUnigrams = unigrams
        for (w in unigrams.keys) {
            if (!w.contains(' ')) {
                recognizedSyllables.add(w)
            }
        }

        val intermediate = LinkedHashMap<String, MutableList<Pair<String, Int>>>()
        for ((word, freq) in unigrams) {
            val lower = word.lowercase().trim()
            if (lower.isEmpty() || lower.contains(' ')) continue
            val stem = toUnaccentedStem(lower)
            if (stem.isNotEmpty()) {
                val list = intermediate.getOrPut(stem) { mutableListOf() }
                list.add(lower to freq.coerceIn(1, 255))
            }
        }

        return finalizeIndex(intermediate, unigrams, bigrams, userLexicon, filesDir, emptySet())
    }

    /**
     * Internal loader from input stream with optional unigram and bigram frequency support.
     */
    fun loadFromStream(
        stream: InputStream,
        unigrams: Map<String, Int> = emptyMap(),
        bigrams: Map<String, Map<String, Int>> = emptyMap(),
        userLexicon: UserLexicon? = null,
        filesDir: File? = null,
    ): RomanizedIndex {
        cachedUnigrams = unigrams
        for (w in unigrams.keys) {
            if (!w.contains(' ')) {
                recognizedSyllables.add(w)
            }
        }

        fun getWordFrequency(word: String, explicitFreq: Int?): Int {
            if (explicitFreq != null && explicitFreq > 0) return explicitFreq.coerceIn(1, 255)
            val unigram = unigrams[word.lowercase()]
            if (unigram != null && unigram > 0) return unigram
            if (word.contains(' ')) {
                val parts = word.lowercase().split(' ')
                val freqs = parts.mapNotNull { unigrams[it] }
                val avgUnigram = if (freqs.isNotEmpty()) freqs.average().toInt() else 100
                if (parts.size == 2) {
                    val bgFreq = if (bigrams.isNotEmpty()) {
                        bigrams[parts[0]]?.get(parts[1])
                    } else {
                        TelexAutocorrectEngine.getInstance().languageModel.getBigramScore(parts[0], parts[1]).takeIf { it > 0 }
                    }
                    if (bgFreq != null && bgFreq > 0) {
                        val minPart = freqs.minOrNull() ?: 100
                        return ((bgFreq * 0.4) + (minPart * 0.5)).toInt().coerceIn(30, 220)
                    }
                }
                return (avgUnigram * 0.7).toInt().coerceIn(30, 160)
            }
            return 50
        }

        val intermediate = LinkedHashMap<String, MutableList<Pair<String, Int>>>()
        val compoundStems = HashSet<String>()

        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val parts = trimmed.split('	')
                if (parts.size < 2) continue
                val unaccented = parts[0].trim().lowercase()
                if (unaccented.isEmpty()) continue

                if (parts.size >= 3) {
                    val word = parts[1].trim()
                    val freq = parts[2].trim().toIntOrNull()
                    val finalFreq = getWordFrequency(word, freq)
                    val list = intermediate.getOrPut(unaccented) { mutableListOf() }
                    list.add(word to finalFreq)
                    if (word.contains(' ')) {
                        compoundStems.add(unaccented)
                    } else {
                        recognizedSyllables.add(word.lowercase())
                    }
                } else {
                    val rawForms = parts[1].split(',')
                    val list = intermediate.getOrPut(unaccented) { mutableListOf() }
                    for (rawForm in rawForms) {
                        val formTrim = rawForm.trim()
                        if (formTrim.isEmpty()) continue
                        val colon = formTrim.lastIndexOf(':')
                        val (formWord, explicitFreq) = if (colon > 0 && colon < formTrim.length - 1) {
                            val candidateWord = formTrim.substring(0, colon)
                            val parsedFreq = formTrim.substring(colon + 1).toIntOrNull()
                            if (parsedFreq != null) candidateWord to parsedFreq else formTrim to null
                        } else {
                            formTrim to null
                        }
                        val finalFreq = getWordFrequency(formWord, explicitFreq)
                        list.add(formWord to finalFreq)
                        if (formWord.contains(' ')) {
                            compoundStems.add(unaccented)
                        } else {
                            recognizedSyllables.add(formWord.lowercase())
                        }
                    }
                }
            }
        }

        return finalizeIndex(intermediate, unigrams, bigrams, userLexicon, filesDir, compoundStems)
    }

    private fun finalizeIndex(
        intermediate: LinkedHashMap<String, MutableList<Pair<String, Int>>>,
        unigrams: Map<String, Int>,
        bigrams: Map<String, Map<String, Int>>,
        userLexicon: UserLexicon?,
        filesDir: File?,
        compoundStems: Set<String>,
    ): RomanizedIndex {
        val map = HashMap<String, List<String>>(intermediate.size * 2)
        val entries = ArrayList<Pair<String, Int>>(intermediate.size)
        val wordFreq = HashMap<String, Int>(intermediate.size * 2)
        val stemMaxFreq = HashMap<String, Int>(intermediate.size)

        for ((unaccented, list) in intermediate) {
            val sorted = list.groupBy { it.first }
                .map { (word, pairs) -> word to pairs.maxOf { it.second } }
                .sortedByDescending { it.second }

            val formWords = sorted.map { it.first }
            map[unaccented] = formWords

            val maxFreq = sorted.firstOrNull()?.second ?: 1
            stemMaxFreq[unaccented] = maxFreq
            for ((w, f) in sorted) {
                wordFreq["$unaccented	$w"] = f
            }

            entries.add(unaccented to maxFreq)
        }

        syllableMap = map

        VietnameseTrajectoryGlide.indexStems(stemMaxFreq, compoundStems)

        if (userLexicon != null || filesDir != null) {
            updateUserCompounds(userLexicon, filesDir)
        }

        val alphabet = VIETNAMESE_KEYS.mapTo(HashSet()) { it.code }
        val trie = PackedTrie.of(entries)
        val index = RomanizedIndex.multiMap(
            curated = trie,
            alphabet = alphabet,
            resolve = { unaccented ->
                val userForms = userCompoundsSnapshot.map[unaccented]
                val baseForms = map[unaccented]
                val combined = when {
                    userForms != null && baseForms != null -> (userForms + baseForms).distinct()
                    userForms != null -> userForms
                    baseForms != null -> baseForms
                    else -> emptyList()
                }
                val bl = currentBlacklist
                if (bl.isNotEmpty()) {
                    combined.filterNot { it.lowercase() in bl }
                } else {
                    combined
                }
            },
            scoreForm = { unaccented, form, formIndex ->
                val ctx = currentContext
                val prev = ctx.prev?.lowercase()?.trim()
                val prev2 = ctx.prev2?.lowercase()?.trim()
                val formLower = form.lowercase().trim()

                // Context N-gram bonus from bundled language model
                var contextBonus = 0.0
                if (!prev.isNullOrEmpty()) {
                    val lm = TelexAutocorrectEngine.getInstance().languageModel
                    val biScore = lm.getBigramScore(prev, formLower)
                    if (biScore > 0) {
                        contextBonus += (ln(1.0 + biScore) * 1.5).coerceAtMost(6.0)
                    }
                    if (!prev2.isNullOrEmpty()) {
                        val triScore = lm.getTrigramScore(prev2, prev, formLower)
                        if (triScore > 0) {
                            contextBonus += (ln(1.0 + triScore) * 2.2).coerceAtMost(8.0)
                        }
                    }
                }

                // Absolute unigram frequency bonus (incorporating native language model popularity into candidate score)
                val baseUnigram = unigrams[formLower] ?: cachedUnigrams[formLower] ?: wordFreq["$unaccented	$form"] ?: 0
                val unigramBonus = if (baseUnigram > 0) {
                    (ln(1.0 + baseUnigram) * 0.45).coerceIn(0.0, 2.5)
                } else 0.0

                // Compound damping: unprompted multi-word compound without context evidence gets damping
                // to prevent rare multi-word compounds (e.g. "chiến sự") from hijacking common single syllables (e.g. "chiều")
                val isCompound = form.contains(' ')
                val hasSingleSyllable = syllableMap?.get(unaccented)?.any { !it.contains(' ') } == true
                val snapshot = userCompoundsSnapshot
                val userMax = snapshot.stemMaxFreq[unaccented]
                val compoundDamping = if (isCompound && contextBonus < 0.1 && userMax == null) {
                    if (hasSingleSyllable) -1.8 else 0.0
                } else 0.0

                val orthographyBonus = if (!isCompound && VietnameseOrthography.isValidVietnameseSyllable(formLower)) 0.35 else 0.0

                val userFreq = snapshot.wordFreq["$unaccented	$form"]
                if (userMax != null && userFreq != null && userMax > 0 && userFreq > 0) {
                    ln(userFreq.toDouble() / userMax).coerceIn(-3.5, 0.0) - (formIndex * 0.001) + contextBonus + unigramBonus + orthographyBonus
                } else {
                    val max = stemMaxFreq[unaccented] ?: 0
                    val freq = wordFreq["$unaccented	$form"] ?: baseUnigram
                    if (max > 0 && freq > 0) {
                        ln(freq.toDouble() / max).coerceIn(-3.5, 0.0) - (formIndex * 0.001) + contextBonus + unigramBonus + compoundDamping + orthographyBonus
                    } else {
                        -formIndex * 0.35 + contextBonus + compoundDamping + orthographyBonus
                    }
                }
            },
            personal = personalWordSource,
            trajectoryDecoder = { path, keys, keyWidth, limit ->
                VietnameseTrajectoryGlide.decode(path, keys, keyWidth, limit)
            },
        )
        cachedIndex = index
        return index
    }

    fun lookup(unaccented: String): List<String> {
        val user = userCompoundsSnapshot.map[unaccented]
        val base = syllableMap?.get(unaccented)
        val combined = when {
            user != null && base != null -> (user + base).distinct()
            user != null -> user
            base != null -> base
            else -> emptyList()
        }
        val bl = currentBlacklist
        return if (bl.isNotEmpty()) {
            combined.filterNot { it.lowercase() in bl }
        } else {
            combined
        }
    }

    fun lookupUserCompounds(unaccented: String): List<String> {
        return userCompoundsSnapshot.map[unaccented] ?: emptyList()
    }

    fun resetIndex() {
        synchronized(this) {
            cachedIndex = null
            lastLearnedMutationCount = -1L
        }
    }

    @VisibleForTesting
    fun resetForTesting() {
        currentBlacklist = emptySet()
        cachedIndex = null
        syllableMap = null
        cachedUnigrams = emptyMap()
        recognizedSyllables.clear()
        userCompoundsSnapshot = UserCompoundsSnapshot()
        lastLearnedMutationCount = -1L
        currentContext = GlideContext()
        VietnameseTrajectoryGlide.resetForTesting()
    }
}
