package com.wasimaster.wmkeyboard.core.prediction.telex

import android.content.res.AssetManager
import androidx.annotation.VisibleForTesting
import com.wasimaster.wmkeyboard.core.gesture.RomanizedIndex
import com.wasimaster.wmkeyboard.core.prediction.Accents
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
    private const val ASSET_PATH = "telex/vietnamese_glide_syllables.tsv"
    private const val UNIGRAMS_ASSET_PATH = "telex/unigrams.json"
    private const val BIGRAMS_ASSET_PATH = "telex/bigrams.json"
    private const val VIETNAMESE_KEYS = "abcdefghijklmnopqrstuvwxyz"

    private const val MIN_USER_COMPOUND_COUNT = 2
    private const val MAX_COMPOUND_STEM_LENGTH = 24

    private val TEENCODE_BLOCKED = setOf(
        "ko", "dc", "bt", "j", "z", "uhm", "uh", "ib", "nt", "mn", "hqua", "ngta", "ae", "đc", "k", "hổng",
    )

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

    // Dynamic compound stems learned from UserLexicon
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
     * Dynamically synthesizes unaccented compound stems from UserLexicon bigrams and trigrams.
     * Stems are filtered through a phonotactic gate to eliminate teencode/typos/noise and
     * registered with frequency scores proportional to user typed counts.
     */
    fun updateUserCompounds(userLexicon: UserLexicon?) {
        if (userLexicon == null) return
        val currentMutations = userLexicon.mutationCount()
        if (currentMutations == lastLearnedMutationCount && userCompoundsSnapshot.trie.walkers().isNotEmpty()) {
            return
        }
        try {
            val bigrams = userLexicon.allBigrams()
            val trigrams = userLexicon.allTrigrams()

            val freshMap = HashMap<String, MutableList<Pair<String, Int>>>()
            val freshWordFreq = HashMap<String, Int>()
            val freshStemMaxFreq = HashMap<String, Int>()

            fun processPhrase(parts: List<String>, count: Int) {
                if (count < MIN_USER_COMPOUND_COUNT) return
                if (parts.size !in 2..3) return
                for (part in parts) {
                    val p = part.trim()
                    if (p.isEmpty() || !isKnownSyllable(p)) {
                        return
                    }
                }
                val phrase = parts.joinToString(" ") { it.trim() }
                val stem = toUnaccentedStem(phrase)
                if (stem.length < 3 || stem.length > MAX_COMPOUND_STEM_LENGTH) return
                if (!stem.all { it in 'a'..'z' }) return

                // Score scales on the 140..255 scale, matching Tier.USER weighting and competing fairly with single syllables
                val freq = (140 + count * 25).coerceIn(140, 255)
                val list = freshMap.getOrPut(stem) { mutableListOf() }
                list.add(phrase to freq)
            }

            for (bg in bigrams) {
                processPhrase(listOf(bg.previous, bg.next), bg.count)
            }
            for (tg in trigrams) {
                processPhrase(listOf(tg.prev2, tg.prev1, tg.next), tg.count)
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
    fun getIndex(assets: AssetManager, userLexicon: UserLexicon? = null): RomanizedIndex? {
        cachedIndex?.let {
            if (userLexicon != null) {
                updateUserCompounds(userLexicon)
            }
            return it
        }
        synchronized(this) {
            cachedIndex?.let {
                if (userLexicon != null) {
                    updateUserCompounds(userLexicon)
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
                assets.open(ASSET_PATH).use { stream ->
                    loadFromStream(stream, unigrams, bigrams, userLexicon)
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
     * Internal loader from input stream with optional unigram and bigram frequency support.
     */
    fun loadFromStream(
        stream: InputStream,
        unigrams: Map<String, Int> = emptyMap(),
        bigrams: Map<String, Map<String, Int>> = emptyMap(),
        userLexicon: UserLexicon? = null,
    ): RomanizedIndex {
        cachedUnigrams = unigrams
        for (w in unigrams.keys) {
            if (!w.contains(' ')) {
                recognizedSyllables.add(w)
            }
        }

        val map = HashMap<String, List<String>>(25000)
        val entries = ArrayList<Pair<String, Int>>(25000)
        val wordFreq = HashMap<String, Int>(25000)
        val stemMaxFreq = HashMap<String, Int>(25000)

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
                        // Bigram frequency is on the 0-255 scale.
                        // Bound compound frequency proportionally so it competes realistically with single words (0-255).
                        val minPart = freqs.minOrNull() ?: 100
                        return ((bgFreq * 0.4) + (minPart * 0.5)).toInt().coerceIn(30, 220)
                    }
                }
                return (avgUnigram * 0.7).toInt().coerceIn(30, 160)
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
                    if (!word.contains(' ')) {
                        recognizedSyllables.add(word.lowercase())
                    }
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
                        if (!formWord.contains(' ')) {
                            recognizedSyllables.add(formWord.lowercase())
                        }
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

        VietnameseTrajectoryGlide.indexStems(stemMaxFreq)

        if (userLexicon != null) {
            updateUserCompounds(userLexicon)
        }

        val alphabet = VIETNAMESE_KEYS.mapTo(HashSet()) { it.code }
        val trie = PackedTrie.of(entries)
        val index = RomanizedIndex.multiMap(
            curated = trie,
            alphabet = alphabet,
            resolve = { unaccented ->
                val userForms = userCompoundsSnapshot.map[unaccented]
                val baseForms = map[unaccented]
                when {
                    userForms != null && baseForms != null -> (userForms + baseForms).distinct()
                    userForms != null -> userForms
                    baseForms != null -> baseForms
                    else -> emptyList()
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
                val baseUnigram = unigrams[formLower] ?: wordFreq["$unaccented\t$form"] ?: 0
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
                    if (hasSingleSyllable) -1.8 else -0.8
                } else 0.0

                val orthographyBonus = if (!isCompound && VietnameseOrthography.isValidVietnameseSyllable(formLower)) 0.35 else 0.0

                val userFreq = snapshot.wordFreq["$unaccented\t$form"]
                if (userMax != null && userFreq != null && userMax > 0 && userFreq > 0) {
                    ln(userFreq.toDouble() / userMax).coerceIn(-3.5, 0.0) - (formIndex * 0.001) + contextBonus + unigramBonus + orthographyBonus
                } else {
                    val max = stemMaxFreq[unaccented] ?: 0
                    val freq = wordFreq["$unaccented\t$form"] ?: baseUnigram
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
        return when {
            user != null && base != null -> (user + base).distinct()
            user != null -> user
            base != null -> base
            else -> emptyList()
        }
    }

    fun lookupUserCompounds(unaccented: String): List<String> {
        return userCompoundsSnapshot.map[unaccented] ?: emptyList()
    }

    @VisibleForTesting
    fun resetForTesting() {
        cachedIndex = null
        syllableMap = null
        cachedUnigrams = emptyMap()
        recognizedSyllables.clear()
        userCompoundsSnapshot = UserCompoundsSnapshot()
        currentContext = GlideContext()
        VietnameseTrajectoryGlide.resetForTesting()
    }
}
