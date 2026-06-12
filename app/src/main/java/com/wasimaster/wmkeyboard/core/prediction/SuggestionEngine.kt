package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.transliteration.AvroPhonetic
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex

/**
 * Produces the suggestion-bar candidates for the word being composed.
 *
 * Sources, merged and ranked by frequency:
 *  - prefix completions from the main dictionary trie (bundled word list
 *    plus everything the user has typed);
 *  - Norvig-style edit-distance-1 corrections when the typed word is not
 *    in the dictionary;
 *  - learned bigrams (backed by bundled seed pairs) for next-word
 *    prediction when composition is empty;
 *  - Bengali transliteration of the romanized composition when the Avro
 *    input mode is active, ranked against the Bengali dictionary so that
 *    common words (আছি) outrank raw phonetics (আসি).
 */
class SuggestionEngine(
    private val dictionary: Trie,
    private val bengaliIndex: BengaliPhoneticIndex,
    private val userLexicon: UserLexicon,
    private val loanwords: EnglishBengaliMap = EnglishBengaliMap.EMPTY,
    private val seedBigrams: SeedBigrams = SeedBigrams.EMPTY,
) {

    /**
     * Contact-name words, swapped in whenever the contacts permission and
     * setting allow (loaded async, cleared when the setting turns off).
     */
    @Volatile
    var contacts: ContactNames = ContactNames.EMPTY

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
        /** Learned words get a large boost so personalization wins quickly. */
        private const val USER_WORD_WEIGHT = 500
        /** Per-occurrence weight of a contact-name word (counts are tiny). */
        private const val CONTACT_WEIGHT = 3000
        /** Split suggestions score slightly under their rarer half. */
        private const val WEIGHT_SPLIT = 0.8

        /**
         * Autocorrect fires only when the best candidate outscores the
         * runner-up by this factor; anything closer is ambiguous and only
         * suggested, never forced.
         */
        private const val AUTOCORRECT_CONFIDENCE = 4.0

        // Likelihood weights per edit kind, multiplied into a candidate's
        // frequency. A neighbouring-key substitution is the classic
        // fat-finger slip; a distant substitution usually means the user
        // typed a different word on purpose.
        private const val WEIGHT_TRANSPOSITION = 0.9
        private const val WEIGHT_SUB_ADJACENT = 0.9
        private const val WEIGHT_DELETION = 0.7
        private const val WEIGHT_INSERT_ADJACENT = 0.7
        private const val WEIGHT_INSERT_FAR = 0.25
        private const val WEIGHT_SUB_FAR = 0.2
    }

    /**
     * @param composing the word currently being typed (may be empty)
     * @param previousWord last committed word, used for next-word prediction
     * @param avroMode when true, [composing] is romanized Bengali and the
     *        top suggestion is its transliteration
     */
    fun suggest(
        composing: String,
        previousWord: String?,
        avroMode: Boolean = false,
        limit: Int = 5,
    ): List<String> {
        if (composing.isEmpty()) {
            return nextWords(previousWord, limit)
        }
        if (avroMode) {
            return bengaliSuggestions(composing, limit)
        }

        val lower = composing.lowercase()
        val merged = LinkedHashMap<String, Int>()

        for (s in dictionary.complete(lower, limit * 2)) {
            merged.merge(s.word, s.frequency, ::maxOf)
        }
        for (s in userLexicon.complete(lower, limit)) {
            merged.merge(s.word, s.frequency * USER_WORD_WEIGHT, ::maxOf)
        }
        for (s in contacts.complete(lower, limit)) {
            merged.merge(s.word, s.frequency * CONTACT_WEIGHT, ::maxOf)
        }
        if (!dictionary.contains(lower) && !userLexicon.contains(lower)) {
            for ((candidate, weight) in edits1Weighted(lower)) {
                val freq = maxOf(
                    dictionary.frequencyOf(candidate),
                    userLexicon.frequencyOf(candidate) * USER_WORD_WEIGHT,
                )
                if (freq > 0) merged.merge(candidate, (freq * weight).toInt(), ::maxOf)
            }
            for ((split, score) in splitCandidates(lower)) {
                merged.merge(split, score, ::maxOf)
            }
        }

        // Contact words carry their own capitalization ("Wasi"), so the
        // same word can arrive in two cases; keep the better-scored one.
        val byLower = HashMap<String, Pair<String, Int>>()
        for ((word, score) in merged) {
            val key = word.lowercase()
            val current = byLower[key]
            if (current == null || score > current.second) byLower[key] = word to score
        }
        return byLower.values
            .sortedByDescending { it.second }
            .take(limit)
            .map { matchCase(composing, it.first) }
    }

    /**
     * Missing-space fixes: "ofthe" → "of the", scored by the rarer half so
     * two genuinely common words outrank a coincidental split.
     */
    private fun splitCandidates(word: String): List<Pair<String, Int>> {
        if (word.length < 4 || !word.all { it.isLetter() }) return emptyList()
        val results = ArrayList<Pair<String, Int>>()
        for (i in 1 until word.length) {
            val left = word.substring(0, i)
            val right = word.substring(i)
            val leftFreq = maxOf(
                dictionary.frequencyOf(left),
                userLexicon.frequencyOf(left) * USER_WORD_WEIGHT,
            )
            val rightFreq = maxOf(
                dictionary.frequencyOf(right),
                userLexicon.frequencyOf(right) * USER_WORD_WEIGHT,
            )
            if (leftFreq > 0 && rightFreq > 0) {
                results.add("$left $right" to (minOf(leftFreq, rightFreq) * WEIGHT_SPLIT).toInt())
            }
        }
        return results
    }

    private fun bengaliSuggestions(composing: String, limit: Int): List<String> {
        val phonetic = AvroPhonetic.transliterate(composing)
        val ordered = LinkedHashSet<String>()
        // Manually mapped loanwords win outright: "keyboard" → কিবোর্ড,
        // "chair" → চেয়ার. Avro phonetics can't reach these conventional
        // spellings, so the map is consulted before anything else.
        ordered.addAll(loanwords.lookup(composing))
        // Phonetic siblings from the dictionary come next (আছি for "asi"),
        // then the literal transliteration, so the primary suggestion is a
        // real word whenever one matches the sound of what was typed.
        ordered.addAll(bengaliIndex.lookup(composing))
        ordered.add(phonetic)
        return ordered.take(limit).toList()
    }

    private fun nextWords(previousWord: String?, limit: Int): List<String> {
        val prev = previousWord?.lowercase() ?: return emptyList()
        // Learned bigrams first — the user's own phrases always beat the
        // bundled seed pairs, which only cover the cold start.
        val ordered = LinkedHashSet<String>()
        ordered.addAll(userLexicon.nextWords(prev, limit))
        // A contact's name chains through the strip: "Wasi" offers "Mollik".
        ordered.addAll(contacts.nextWords(prev))
        ordered.addAll(seedBigrams.nextWords(prev))
        return ordered.take(limit).toList()
    }

    /**
     * The correction [word] should be silently replaced with on commit, or
     * null when it should be left alone.
     *
     * Null when the word is known (a known word is never corrected away),
     * or when no candidate is confident: a candidate wins outright only if
     * the bundled dictionary and the user's lexicon independently agree on
     * it, or its score beats the runner-up by [AUTOCORRECT_CONFIDENCE].
     * Anything closer stays in the suggestion strip for the user to pick.
     */
    fun shouldAutocorrect(word: String): String? {
        val lower = word.lowercase()
        if (lower.length < 3) return null
        if (dictionary.contains(lower) || userLexicon.contains(lower)) return null
        // Contact names are known words too — never "corrected" away.
        if (contacts.contains(lower)) return null

        var bestDict: String? = null
        var bestDictScore = 0.0
        var bestUser: String? = null
        var bestUserScore = 0.0
        val combined = HashMap<String, Double>()
        for ((candidate, weight) in edits1Weighted(lower)) {
            val dictScore = dictionary.frequencyOf(candidate) * weight
            val userScore = userLexicon.frequencyOf(candidate) * USER_WORD_WEIGHT * weight
            if (dictScore <= 0 && userScore <= 0) continue
            if (dictScore > bestDictScore) {
                bestDictScore = dictScore
                bestDict = candidate
            }
            if (userScore > bestUserScore) {
                bestUserScore = userScore
                bestUser = candidate
            }
            combined[candidate] = dictScore + userScore
        }

        // Two independent sources naming the same word is confidence enough
        // on its own.
        if (bestDict != null && bestDict == bestUser) return matchCase(word, bestDict)

        val ranked = combined.entries.sortedByDescending { it.value }
        val top = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.getOrNull(1)
        if (runnerUp != null && top.value < runnerUp.value * AUTOCORRECT_CONFIDENCE) return null
        return matchCase(word, top.key)
    }

    /**
     * All strings one edit away from [word] (Norvig's generator), each with
     * the likelihood weight of the edit that produced it. A candidate
     * reachable by several edits keeps the most likely one.
     */
    private fun edits1Weighted(word: String): Map<String, Double> {
        val result = HashMap<String, Double>()
        fun add(candidate: String, weight: Double) {
            if (candidate != word) result.merge(candidate, weight, ::maxOf)
        }
        for (i in word.indices) {
            add(word.removeRange(i, i + 1), WEIGHT_DELETION)
            if (i < word.length - 1) {
                add(
                    word.substring(0, i) + word[i + 1] + word[i] + word.substring(i + 2),
                    WEIGHT_TRANSPOSITION,
                )
            }
            for (c in ALPHABET) {
                val weight = if (KeyProximity.areAdjacent(word[i], c)) {
                    WEIGHT_SUB_ADJACENT
                } else {
                    WEIGHT_SUB_FAR
                }
                add(word.substring(0, i) + c + word.substring(i + 1), weight)
            }
        }
        for (i in 0..word.length) {
            for (c in ALPHABET) {
                // An accidental extra press usually lands next to one of the
                // characters it slipped in between.
                val nearNeighbour =
                    (i > 0 && KeyProximity.areAdjacent(word[i - 1], c)) ||
                        (i < word.length && KeyProximity.areAdjacent(word[i], c))
                val weight = if (nearNeighbour) WEIGHT_INSERT_ADJACENT else WEIGHT_INSERT_FAR
                add(word.substring(0, i) + c + word.substring(i), weight)
            }
        }
        return result
    }

    /** Applies the typed word's capitalization pattern to a suggestion. */
    private fun matchCase(typed: String, suggestion: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } ->
            suggestion.uppercase()
        typed.firstOrNull()?.isUpperCase() == true ->
            suggestion.replaceFirstChar { it.uppercase() }
        else -> suggestion
    }
}
