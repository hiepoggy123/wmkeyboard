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
    bengaliIndex: BengaliPhoneticIndex,
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

    /**
     * Contact email addresses, completed as whole tokens ("john" →
     * john.doe@gmail.com) when the user opts in. Fed from the service on the
     * same Contacts permission as [contacts]; cleared when the setting is off.
     */
    @Volatile
    var contactEmails: ContactEmails = ContactEmails.EMPTY

    /**
     * Words from the labels of installed apps, so app names complete while
     * typing ("sign" → Signal). Swapped in when the setting allows, cleared
     * when it turns off.
     */
    @Volatile
    var apps: AppNames = AppNames.EMPTY

    /**
     * Adjacency map for typo weighting, following the active Latin layout
     * (set by the IME on input-mode changes; AZERTY/Dvorak fat-fingers land
     * on different neighbours than QWERTY's).
     */
    @Volatile
    var proximity: KeyProximity = KeyProximity.QWERTY

    /**
     * Whether the bundled English word list and seed bigrams participate.
     * Off for Latin languages without a bundled dictionary (French, German,
     * Spanish): completions and corrections then come only from the user's
     * learned lexicon and contacts, and English autocorrect never mangles
     * their words.
     */
    @Volatile
    var englishSources: Boolean = true

    /**
     * Word list the user imported for the language now being typed (empty
     * when they have imported none). Swapped by the IME on every input-mode
     * change, so an imported French list never leaks into English.
     *
     * Unlike [dictionary] this is not gated by [englishSources]: it is the
     * whole point of the feature that French, German and Spanish — which
     * ship no bundled list — can get completions this way.
     */
    @Volatile
    var customDictionary: Trie = Trie()

    /**
     * Dictionaries for the user's secondary languages, consulted alongside the
     * primary so a bilingual typist gets both without switching. These are the
     * freq-1 imported lists, weighted below every primary source; a word valid
     * in one is never autocorrected away.
     */
    @Volatile
    var secondaryDictionaries: List<Trie> = emptyList()

    /**
     * True when English is a secondary language and the primary is not: the
     * bundled English list then participates at a fraction of its frequency
     * (it carries real frequencies, unlike the freq-1 [secondaryDictionaries]),
     * covering the common "native language + English" pairing.
     */
    @Volatile
    var englishAsSecondary: Boolean = false

    /**
     * Bengali index, rebuilt when an imported Bengali list arrives so its
     * words become reachable by transliteration too.
     */
    @Volatile
    var bengaliIndex: BengaliPhoneticIndex = bengaliIndex

    /**
     * How much the winning candidate must outscore the runner-up before
     * autocorrect fires, set from the user's confidence slider. Higher is
     * stricter: fewer corrections, but fewer wrong ones. Defaults to
     * [DEFAULT_AUTOCORRECT_CONFIDENCE].
     */
    @Volatile
    var autocorrectConfidence: Double = DEFAULT_AUTOCORRECT_CONFIDENCE

    /**
     * Words the user never wants offered. Stored lowercased; any candidate
     * whose lowercase form is in here is dropped from the strip and never used
     * as an autocorrect target. This only suppresses *suggesting* the word —
     * the user can still type and commit it. Empty by default.
     */
    @Volatile
    var blacklist: Set<String> = emptySet()

    /**
     * When on, a word typed entirely in capitals (SHOUTING, or an acronym like
     * ASAP, OFC) is never autocorrected — those are deliberate, and "fixing"
     * them to a lowercase dictionary word is almost always wrong. Off treats
     * all-caps like any other word. Set from the user's setting.
     */
    @Volatile
    var skipAllCapsAutocorrect: Boolean = true

    private val emptyTrie = Trie()

    /** True when [word] is on the suggestion blacklist (case-insensitive). */
    private fun blacklisted(word: String): Boolean =
        blacklist.isNotEmpty() && word.lowercase() in blacklist

    /** The bundled dictionary, or an empty one when [englishSources] is off. */
    private val activeDictionary: Trie
        get() = if (englishSources) dictionary else emptyTrie

    /** English's bundled frequency when it is a secondary language, else 0. */
    private fun secondaryEnglishFrequencyOf(word: String): Int =
        if (englishAsSecondary && !englishSources) {
            dictionary.frequencyOf(word) / SECONDARY_ENGLISH_DIVISOR
        } else {
            0
        }

    /** Best frequency for a word across the primary and secondary lists. */
    private fun dictionaryFrequencyOf(word: String): Int = maxOf(
        activeDictionary.frequencyOf(word),
        customDictionary.frequencyOf(word) * CUSTOM_WORD_WEIGHT,
        secondaryEnglishFrequencyOf(word),
        secondaryDictionaries.maxOfOrNull { it.frequencyOf(word) * SECONDARY_WORD_WEIGHT } ?: 0,
    )

    private fun inDictionaries(word: String): Boolean =
        activeDictionary.contains(word) || customDictionary.contains(word) ||
            (englishAsSecondary && !englishSources && dictionary.contains(word)) ||
            secondaryDictionaries.any { it.contains(word) }

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
        /** Learned words get a large boost so personalization wins quickly. */
        private const val USER_WORD_WEIGHT = 500

        /**
         * Imported word lists usually carry no frequency column, so every
         * word lands at 1 and would rank below the bundled list's rarest
         * tail. This lifts them to roughly mid-dictionary — present and
         * correctable, without outranking words the user actually types.
         */
        private const val CUSTOM_WORD_WEIGHT = 100

        /** Imported secondary-language lists rank below the primary custom list. */
        private const val SECONDARY_WORD_WEIGHT = 40

        /** English-as-secondary participates at a fraction of its real frequency. */
        private const val SECONDARY_ENGLISH_DIVISOR = 2
        /** Per-occurrence weight of a contact-name word (counts are tiny). */
        private const val CONTACT_WEIGHT = 3000

        /**
         * A matched contact email is an exact prefix of one of the user's own
         * addresses, so it wins strongly over ordinary completions.
         */
        private const val CONTACT_EMAIL_WEIGHT = 4000

        /**
         * Don't offer email completions for a single-letter prefix — that
         * would list every address the moment a letter is typed.
         */
        private const val CONTACT_EMAIL_MIN_PREFIX = 2

        /**
         * App-label words rank below contacts: you type a friend's name far
         * more often than an app's, and app labels contain ordinary words
         * ("Files", "Photos", "Clock") that must not outrank the dictionary.
         */
        private const val APP_WEIGHT = 400
        /** Split suggestions score slightly under their rarer half. */
        private const val WEIGHT_SPLIT = 0.8

        /**
         * Autocorrect fires only when the best candidate outscores the
         * runner-up by this factor; anything closer is ambiguous and only
         * suggested, never forced.
         */
        const val DEFAULT_AUTOCORRECT_CONFIDENCE = 4.0

        /** Slider bounds: 1.5 corrects eagerly, 10 only on near-certainty. */
        const val MIN_AUTOCORRECT_CONFIDENCE = 1.5
        const val MAX_AUTOCORRECT_CONFIDENCE = 10.0

        /**
         * A Bengali phonetic sibling only outranks the literal
         * transliteration (which is what the composing preview shows) when
         * it is at least this many times more frequent. আছি (6900) beats
         * আসি (2300) for "asi", but হল (1986) never steals "holO" from
         * হলো (1900).
         */
        private const val SIBLING_CONFIDENCE = 2.0

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

        for (s in activeDictionary.complete(lower, limit * 2)) {
            merged.merge(s.word, s.frequency, ::maxOf)
        }
        for (s in customDictionary.complete(lower, limit * 2)) {
            merged.merge(s.word, s.frequency * CUSTOM_WORD_WEIGHT, ::maxOf)
        }
        if (englishAsSecondary && !englishSources) {
            for (s in dictionary.complete(lower, limit * 2)) {
                merged.merge(s.word, s.frequency / SECONDARY_ENGLISH_DIVISOR, ::maxOf)
            }
        }
        for (t in secondaryDictionaries) {
            for (s in t.complete(lower, limit * 2)) {
                merged.merge(s.word, s.frequency * SECONDARY_WORD_WEIGHT, ::maxOf)
            }
        }
        for (s in userLexicon.complete(lower, limit)) {
            merged.merge(s.word, s.frequency * USER_WORD_WEIGHT, ::maxOf)
        }
        for (s in contacts.complete(lower, limit)) {
            merged.merge(s.word, s.frequency * CONTACT_WEIGHT, ::maxOf)
        }
        // Whole contact emails complete from their local part; short prefixes
        // are ignored so a single letter doesn't dump the address book.
        if (lower.length >= CONTACT_EMAIL_MIN_PREFIX) {
            for (email in contactEmails.complete(lower, limit)) {
                merged.merge(email, CONTACT_EMAIL_WEIGHT, ::maxOf)
            }
        }
        for (s in apps.complete(lower, limit)) {
            merged.merge(s.word, s.frequency * APP_WEIGHT, ::maxOf)
        }
        if (!inDictionaries(lower) && !userLexicon.contains(lower)) {
            for ((candidate, weight) in edits1Weighted(lower)) {
                val freq = maxOf(
                    dictionaryFrequencyOf(candidate),
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
            .asSequence()
            .map { it.first }
            .filterNot(::blacklisted)
            .take(limit)
            .map { matchCase(composing, it) }
            .toList()
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
                dictionaryFrequencyOf(left),
                userLexicon.frequencyOf(left) * USER_WORD_WEIGHT,
            )
            val rightFreq = maxOf(
                dictionaryFrequencyOf(right),
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
        // Phonetic siblings from the dictionary (আছি for "asi") outrank the
        // literal transliteration only when clearly more common — the commit
        // path takes the first entry, and the preview showed the literal, so
        // a near-tie sibling silently replacing it reads as a bug (হলো
        // becoming হল). A literal that isn't a dictionary word at all always
        // yields to siblings.
        val siblings = bengaliIndex.lookup(composing)
        val literalFreq = bengaliIndex.frequencyOf(phonetic)
        val topSiblingFreq = siblings.firstOrNull()?.let { bengaliIndex.frequencyOf(it) } ?: 0
        if (literalFreq > 0 && topSiblingFreq < literalFreq * SIBLING_CONFIDENCE) {
            ordered.add(phonetic)
        }
        ordered.addAll(siblings)
        ordered.add(phonetic)
        return ordered.asSequence().filterNot(::blacklisted).take(limit).toList()
    }

    private fun nextWords(previousWord: String?, limit: Int): List<String> {
        val prev = previousWord?.lowercase() ?: return emptyList()
        // Learned bigrams first — the user's own phrases always beat the
        // bundled seed pairs, which only cover the cold start.
        val ordered = LinkedHashSet<String>()
        ordered.addAll(userLexicon.nextWords(prev, limit))
        // A contact's name chains through the strip: "Wasi" offers "Mollik".
        ordered.addAll(contacts.nextWords(prev))
        // Seed bigrams are English pairs; they only cold-start English modes.
        if (englishSources) ordered.addAll(seedBigrams.nextWords(prev))
        return ordered.asSequence().filterNot(::blacklisted).take(limit).toList()
    }

    /**
     * The correction [word] should be silently replaced with on commit, or
     * null when it should be left alone.
     *
     * Null when the word is known (a known word — bundled, imported or
     * learned — is never corrected away), or when no candidate is
     * confident: a candidate wins outright only if the dictionaries and
     * the user's lexicon independently agree on
     * it, or its score beats the runner-up by [autocorrectConfidence].
     * Anything closer stays in the suggestion strip for the user to pick.
     */
    fun shouldAutocorrect(word: String): String? {
        val lower = word.lowercase()
        if (lower.length < 3) return null
        // An all-caps word is a deliberate acronym or shout, not a typo of a
        // lowercase word — don't "correct" it away when the user asked us not to.
        if (skipAllCapsAutocorrect && isAllCaps(word)) return null
        if (inDictionaries(lower) || userLexicon.contains(lower)) return null
        // Contact and app names are known words too — never "corrected" away.
        if (contacts.contains(lower) || apps.contains(lower)) return null

        var bestDict: String? = null
        var bestDictScore = 0.0
        var bestUser: String? = null
        var bestUserScore = 0.0
        val combined = HashMap<String, Double>()
        for ((candidate, weight) in edits1Weighted(lower)) {
            // A blacklisted word is never offered, so it is never a correction
            // target either — otherwise it would be forced in silently.
            if (blacklisted(candidate)) continue
            val dictScore = dictionaryFrequencyOf(candidate) * weight
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
        if (runnerUp != null && top.value < runnerUp.value * autocorrectConfidence) return null
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
                val weight = if (proximity.areAdjacent(word[i], c)) {
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
                    (i > 0 && proximity.areAdjacent(word[i - 1], c)) ||
                        (i < word.length && proximity.areAdjacent(word[i], c))
                val weight = if (nearNeighbour) WEIGHT_INSERT_ADJACENT else WEIGHT_INSERT_FAR
                add(word.substring(0, i) + c + word.substring(i), weight)
            }
        }
        return result
    }

    /** True when [word] has letters and every one of them is uppercase. */
    private fun isAllCaps(word: String): Boolean =
        word.length > 1 && word.any { it.isLetter() } && word.all { !it.isLetter() || it.isUpperCase() }

    /** Applies the typed word's capitalization pattern to a suggestion. */
    private fun matchCase(typed: String, suggestion: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } ->
            suggestion.uppercase()
        typed.firstOrNull()?.isUpperCase() == true ->
            suggestion.replaceFirstChar { it.uppercase() }
        else -> suggestion
    }
}
