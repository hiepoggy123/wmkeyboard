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
 *  - learned bigrams for next-word prediction when composition is empty;
 *  - Bengali transliteration of the romanized composition when the Avro
 *    input mode is active, ranked against the Bengali dictionary so that
 *    common words (আছি) outrank raw phonetics (আসি).
 */
class SuggestionEngine(
    private val dictionary: Trie,
    private val bengaliIndex: BengaliPhoneticIndex,
    private val userLexicon: UserLexicon,
    private val loanwords: EnglishBengaliMap = EnglishBengaliMap.EMPTY,
) {

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
        /** Learned words get a large boost so personalization wins quickly. */
        private const val USER_WORD_WEIGHT = 500
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
        if (!dictionary.contains(lower) && !userLexicon.contains(lower)) {
            for (candidate in edits1(lower)) {
                val freq = maxOf(
                    dictionary.frequencyOf(candidate),
                    userLexicon.frequencyOf(candidate) * USER_WORD_WEIGHT,
                )
                if (freq > 0) merged.merge(candidate, freq, ::maxOf)
            }
        }

        return merged.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { matchCase(composing, it.key) }
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
        val fromBigrams = previousWord
            ?.let { userLexicon.nextWords(it.lowercase(), limit) }
            .orEmpty()
        return fromBigrams.take(limit)
    }

    /** True when [word] should be auto-corrected to [suggest]'s top hit. */
    fun shouldAutocorrect(word: String): String? {
        val lower = word.lowercase()
        if (lower.length < 3) return null
        if (dictionary.contains(lower) || userLexicon.contains(lower)) return null
        val best = edits1(lower)
            .map { it to dictionary.frequencyOf(it) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?: return null
        return matchCase(word, best.first)
    }

    /** All strings one edit away from [word] (Norvig's generator). */
    private fun edits1(word: String): Set<String> {
        val result = HashSet<String>()
        for (i in word.indices) {
            result.add(word.removeRange(i, i + 1)) // deletion
            if (i < word.length - 1) { // transposition
                result.add(
                    word.substring(0, i) + word[i + 1] + word[i] + word.substring(i + 2)
                )
            }
            for (c in ALPHABET) { // substitution
                result.add(word.substring(0, i) + c + word.substring(i + 1))
            }
        }
        for (i in 0..word.length) { // insertion
            for (c in ALPHABET) {
                result.add(word.substring(0, i) + c + word.substring(i))
            }
        }
        result.remove(word)
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
