package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.transliteration.AvroPhonetic
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln

/**
 * A secondary-language word list paired with the id of the language it belongs
 * to, so [SuggestionEngine] can attribute committed words back to a language and
 * adapt how strongly that language participates in the mix.
 */
data class SecondaryDictionary(val langId: String, val source: WordSource)

/**
 * Produces the suggestion-bar candidates for the word being composed.
 *
 * Sources, merged and ranked by frequency:
 *  - prefix completions and trie-guided fuzzy corrections (one shared
 *    [FuzzyBeamSearch] walk over every dictionary and the user lexicon)
 *    when the typed word is not in the dictionary;
 *  - learned bigrams (backed by bundled seed pairs) for next-word
 *    prediction when composition is empty;
 *  - Bengali transliteration of the romanized composition when the Avro
 *    input mode is active, ranked against the Bengali dictionary so that
 *    common words (আছি) outrank raw phonetics (আসি).
 */
class SuggestionEngine(
    dictionary: WordSource,
    bengaliIndex: BengaliPhoneticIndex,
    private val userLexicon: UserLexicon,
    private val loanwords: EnglishBengaliMap = EnglishBengaliMap.EMPTY,
    private val seedBigrams: SeedBigrams = SeedBigrams.EMPTY,
    private val mixConfidence: LanguageMixConfidence = LanguageMixConfidence(),
) {

    /**
     * Epoch for every input the fuzzy walk depends on. Any setter below that
     * changes what the walk would return bumps it, invalidating [rankedWalk].
     */
    private val generation = java.util.concurrent.atomic.AtomicLong()

    /**
     * The primary (bundled or downloaded) dictionary. A var so the IME can
     * swap in a bigger downloaded English list the moment its download
     * finishes, without rebuilding the engine.
     */
    @Volatile
    private var dictionaryField: WordSource = dictionary
    var dictionary: WordSource
        get() = dictionaryField
        set(value) {
            dictionaryField = value
            generation.incrementAndGet()
        }

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
    /**
     * Key-center model of the live layout in key-width units, fed by the IME
     * alongside proximity. Null (or a tap list of nulls) falls back to the
     * discrete adjacency weights, so hardware keyboards, pasted text and
     * re-armed words behave exactly as before.
     */
    @Volatile
    private var touchModelField: KeyTouchModel? = null
    var touchModel: KeyTouchModel?
        get() = touchModelField
        set(value) {
            touchModelField = value
            generation.incrementAndGet()
        }

    @Volatile
    private var proximityField: KeyProximity = KeyProximity.QWERTY
    var proximity: KeyProximity
        get() = proximityField
        set(value) {
            // The IME rebuilds KeyProximity on every settings emission; only a
            // genuinely different layout invalidates cached walk results.
            if (value == proximityField) return
            proximityField = value
            generation.incrementAndGet()
        }

    /**
     * Whether the bundled English word list and seed bigrams participate.
     * Off for Latin languages without a bundled dictionary (French, German,
     * Spanish): completions and corrections then come only from the user's
     * learned lexicon and contacts, and English autocorrect never mangles
     * their words.
     */
    @Volatile
    private var englishSourcesField: Boolean = true
    var englishSources: Boolean
        get() = englishSourcesField
        set(value) {
            englishSourcesField = value
            generation.incrementAndGet()
        }

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
    private var customDictionaryField: WordSource = PackedTrie.EMPTY
    var customDictionary: WordSource
        get() = customDictionaryField
        set(value) {
            customDictionaryField = value
            generation.incrementAndGet()
        }

    /**
     * Dictionaries for the user's secondary languages, consulted alongside the
     * primary so a bilingual typist gets both without switching. These are the
     * freq-1 imported lists, weighted below every primary source; a word valid
     * in one is never autocorrected away. Each is tagged with its language id so
     * its share of the strip adapts to how much the user actually types it (see
     * [mixConfidence] / [recordUsage]).
     */
    @Volatile
    private var secondaryDictionariesField: List<SecondaryDictionary> = emptyList()
    var secondaryDictionaries: List<SecondaryDictionary>
        get() = secondaryDictionariesField
        set(value) {
            secondaryDictionariesField = value
            generation.incrementAndGet()
        }

    /**
     * Language id of the primary (on-screen) language, so a committed word the
     * primary already covers is attributed to it rather than mistaken for
     * secondary-language use. Blank when no language is set (tests).
     */
    @Volatile
    var primaryLanguageId: String = ""

    /**
     * True when English is a secondary language and the primary is not: the
     * bundled English list then participates at a fraction of its frequency
     * (it carries real frequencies, unlike the freq-1 [secondaryDictionaries]),
     * covering the common "native language + English" pairing. Its share is
     * scaled by the adaptive confidence for "en" like any other secondary.
     */
    @Volatile
    private var englishAsSecondaryField: Boolean = false
    var englishAsSecondary: Boolean
        get() = englishAsSecondaryField
        set(value) {
            englishAsSecondaryField = value
            generation.incrementAndGet()
        }

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
     * When on, words on the bundled [offensiveWords] set are treated like the
     * blacklist: never offered in the strip and never used as an autocorrect
     * target, so the keyboard won't suggest or "correct" a neutral typo into a
     * slur. The user can still type and commit any of them verbatim — this only
     * suppresses *suggesting* them. Off lets them suggest like any other word.
     */
    @Volatile
    var blockOffensiveWords: Boolean = false

    /**
     * The bundled set of potentially-offensive words, lowercased. Only consulted
     * when [blockOffensiveWords] is on. Empty until the service loads it.
     */
    @Volatile
    var offensiveWords: Set<String> = emptySet()

    /**
     * When on, a word typed entirely in capitals (SHOUTING, or an acronym like
     * ASAP, OFC) is never autocorrected — those are deliberate, and "fixing"
     * them to a lowercase dictionary word is almost always wrong. Off treats
     * all-caps like any other word. Set from the user's setting.
     */
    @Volatile
    var skipAllCapsAutocorrect: Boolean = true

    /**
     * Words whose autocorrect the user has undone, lowercased. Typing one again
     * commits it as typed: undoing a correction is a stronger statement than
     * any confidence score, and a word that gets re-corrected every time is one
     * the user has no way to type at all.
     *
     * Deliberately separate from the personal dictionary, which only records
     * words when learning is on — the rejection has to hold in incognito and
     * with "learn from typing" off too. Lives for the process, not on disk:
     * this is a "not now" memory, and the lexicon is where a lasting one goes.
     * Written from the main thread, read on the suggestion coroutine.
     */
    private val rejectedCorrections: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /**
     * Records that the user undid the autocorrect of [word] (typically by
     * backspacing over it), so it is never corrected again; see
     * [rejectedCorrections].
     */
    fun rejectCorrection(word: String) {
        val lower = word.lowercase()
        if (lower.isNotEmpty()) rejectedCorrections.add(lower)
    }

    private val emptyTrie: WordSource = PackedTrie.EMPTY

    /** True when [word] is on the suggestion blacklist (case-insensitive). */
    private fun blacklisted(word: String): Boolean =
        blacklist.isNotEmpty() && word.lowercase() in blacklist

    /** True when the offensive filter is on and [word] is a blocked word. */
    private fun offensive(word: String): Boolean =
        blockOffensiveWords && offensiveWords.isNotEmpty() && word.lowercase() in offensiveWords

    /**
     * True when [word] must not be offered or used as an autocorrect target,
     * for either reason (user blacklist or the offensive-words filter).
     */
    private fun suppressed(word: String): Boolean = blacklisted(word) || offensive(word)

    /** The bundled dictionary, or an empty one when [englishSources] is off. */
    private val activeDictionary: WordSource
        get() = if (englishSources) dictionary else emptyTrie

    /**
     * Fraction of its real frequency at which bundled English participates as a
     * secondary language: the base [SECONDARY_ENGLISH_DIVISOR] scaled by how
     * much the user actually mixes English in. At the neutral (untrained)
     * confidence this is exactly `1 / SECONDARY_ENGLISH_DIVISOR`, matching the
     * old fixed behaviour.
     */
    private fun englishSecondaryFactor(): Double =
        mixConfidence.confidenceFor(EN) / SECONDARY_ENGLISH_DIVISOR

    /** Adaptive weight for a secondary language's imported list. */
    private fun secondaryWeight(langId: String): Int =
        (SECONDARY_WORD_WEIGHT * mixConfidence.confidenceFor(langId)).toInt()

    /** English's bundled frequency when it is a secondary language, else 0. */
    private fun secondaryEnglishFrequencyOf(word: String): Int =
        if (englishAsSecondary && !englishSources) {
            (dictionary.frequencyOf(word) * englishSecondaryFactor()).toInt()
        } else {
            0
        }

    private val beam = FuzzyBeamSearch()
    private val beamWorkspace = ThreadLocal.withInitial { BeamWorkspace() }

    /**
     * The last walk's ranked result and everything it depended on. One
     * keystroke asks the same question twice — [suggest] builds the strip,
     * then [shouldAutocorrect] (precomputing what a space would commit) asks
     * for the same word moments later — so answering the second ask from the
     * first halves the per-keystroke walk cost. Single @Volatile slot: racing
     * threads at worst both recompute the same immutable value.
     */
    private class RankedWalk(
        val word: String,
        val generation: Long,
        val lexMutations: Long,
        val k: Int,
        /** Defensive copy of the tap list; compared structurally (element
         * identity) so in-place mutation of the caller's buffer misses. */
        val touch: List<TouchPoint?>?,
        val ranked: List<FuzzyBeamSearch.ScoredCandidate>,
    )

    @Volatile
    private var rankedWalk: RankedWalk? = null

    private fun rankedFor(
        lower: String,
        limit: Int,
        touch: List<TouchPoint?>?,
    ): List<FuzzyBeamSearch.ScoredCandidate> {
        val k = maxOf(limit * 2, FuzzyBeamSearch.AUTOCORRECT_K)
        val gen = generation.get()
        val lexGen = userLexicon.mutationCount()
        rankedWalk?.let { cached ->
            if (cached.word == lower && cached.generation == gen &&
                cached.lexMutations == lexGen && cached.k >= k && cached.touch == touch
            ) {
                return cached.ranked
            }
        }
        val model = touchModelField
        val scoring = if (model != null && touch != null && touch.any { it != null }) {
            FuzzyBeamSearch.TouchScoring(model, touch)
        } else {
            null
        }
        val ranked = beam.search(
            walkSources(), lower, proximity, limit, beamWorkspace.get(), touch = scoring,
        )
        rankedWalk = RankedWalk(lower, gen, lexGen, k, touch?.let(::ArrayList), ranked)
        return ranked
    }

    /**
     * The weighted trie sources one fuzzy walk covers. Built per call —
     * cheap — with each language's mix confidence read once, not once per
     * candidate (LanguageMixConfidence is synchronized; per-candidate reads
     * were the one real lock-contention point on the hot path).
     */
    private fun walkSources(): List<FuzzyBeamSearch.WalkSource> {
        val sources = ArrayList<FuzzyBeamSearch.WalkSource>()
        fun add(wordSource: WordSource, logWeight: Double, tier: FuzzyBeamSearch.Tier) {
            for (walker in wordSource.walkers()) {
                sources.add(FuzzyBeamSearch.WalkSource(walker, logWeight, tier))
            }
        }
        add(activeDictionary, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        add(customDictionary, LOG_CUSTOM_WORD_WEIGHT, FuzzyBeamSearch.Tier.DICTIONARY)
        if (englishAsSecondary && !englishSources) {
            val factor = englishSecondaryFactor()
            if (factor > 0) add(dictionary, ln(factor), FuzzyBeamSearch.Tier.DICTIONARY)
        }
        for (t in secondaryDictionaries) {
            val weight = SECONDARY_WORD_WEIGHT * mixConfidence.confidenceFor(t.langId)
            if (weight > 0) add(t.source, ln(weight), FuzzyBeamSearch.Tier.DICTIONARY)
        }
        for (walker in userLexicon.walkers()) {
            sources.add(
                FuzzyBeamSearch.WalkSource(walker, LOG_USER_WORD_WEIGHT, FuzzyBeamSearch.Tier.USER)
            )
        }
        return sources
    }

    /** Best frequency for a word across the primary and secondary lists. */
    private fun dictionaryFrequencyOf(word: String): Int = maxOf(
        activeDictionary.frequencyOf(word),
        weighted(customDictionary.frequencyOf(word), CUSTOM_WORD_WEIGHT),
        secondaryEnglishFrequencyOf(word),
        secondaryDictionaries.maxOfOrNull {
            weighted(it.source.frequencyOf(word), secondaryWeight(it.langId))
        } ?: 0,
    )

    /**
     * Frequency × weight, widened to Long and clamped to the Int range.
     * Imported frequency lists (OpenSubtitles-style raw counts) can carry
     * tens of millions; a plain Int×Int would overflow negative and sink the
     * most common words to the bottom of the suggestions.
     */
    private fun weighted(frequency: Int, weight: Int): Int =
        (frequency.toLong() * weight).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun inDictionaries(word: String): Boolean =
        activeDictionary.contains(word) || customDictionary.contains(word) ||
            (englishAsSecondary && !englishSources && dictionary.contains(word)) ||
            secondaryDictionaries.any { it.source.contains(word) }

    /**
     * Attribute one committed [word] to the language in the active mix that owns
     * it, feeding [mixConfidence] so the secondary tier's weighting tracks real
     * use. A no-op unless a secondary mix is configured, so a monolingual user
     * pays nothing. Words the primary already covers count toward the primary,
     * which is how a lightly-used secondary ends up damped relative to it.
     */
    fun recordUsage(word: String) {
        if (secondaryDictionaries.isEmpty() && !englishAsSecondary) return
        val lower = word.lowercase()
        val langId = when {
            activeDictionary.contains(lower) || customDictionary.contains(lower) ||
                userLexicon.contains(lower) -> primaryLanguageId
            englishAsSecondary && !englishSources && dictionary.contains(lower) -> EN
            else -> secondaryDictionaries.firstOrNull { it.source.contains(lower) }?.langId
                ?: primaryLanguageId
        }
        mixConfidence.record(langId)
        // Confidences weight the walk sources; recorded use invalidates
        // cached walk results.
        generation.incrementAndGet()
    }

    companion object {
        /** Language id of bundled English, the only special-cased secondary. */
        private const val EN = "en"
        /** Learned words get a large boost so personalization wins quickly. */
        /** Completions scanned per source when building the next-letter map. */
        private const val NEXT_LETTER_SCAN = 24
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

        /** Log-space forms of the source weights, fed to the fuzzy walk. */
        private val LOG_USER_WORD_WEIGHT = ln(USER_WORD_WEIGHT.toDouble())
        private val LOG_CUSTOM_WORD_WEIGHT = ln(CUSTOM_WORD_WEIGHT.toDouble())

        /**
         * Synthetic runner-up score when a correction has no competition at
         * all: an unopposed but weak candidate — a rare word reached by an
         * expensive edit — must clear `SOLO_RUNNER_UP_SCORE + ln(confidence)`
         * or stay a suggestion. Tuned so a mid-frequency word one far slip
         * away (hallo -> hello at frequency 70) still fires at the default
         * confidence, while a lone two-edit hit on a rare word does not.
         */
        private const val SOLO_RUNNER_UP_SCORE = 1.0
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
        touch: List<TouchPoint?>? = null,
    ): List<String> {
        if (composing.isEmpty()) {
            return nextWords(previousWord, limit)
        }
        if (avroMode) {
            return bengaliSuggestions(composing, limit)
        }

        val lower = composing.lowercase()
        val known = inDictionaries(lower) || userLexicon.contains(lower)
        val merged = HashMap<String, Double>()

        // One fuzzy walk covers completions AND corrections over every trie
        // source. Corrections (edited paths) are admitted only when the typed
        // word is unknown, matching the historical gate; pure completions
        // (edits == 0) always participate.
        for (c in rankedFor(lower, limit, touch)) {
            if (c.edits > 0 && known) continue
            merged.merge(c.word, c.score, ::maxOf)
        }
        for (s in contacts.complete(lower, limit)) {
            merged.merge(s.word, flatScore(s.frequency, CONTACT_WEIGHT), ::maxOf)
        }
        // Whole contact emails complete from their local part; short prefixes
        // are ignored so a single letter doesn't dump the address book.
        if (lower.length >= CONTACT_EMAIL_MIN_PREFIX) {
            for (email in contactEmails.complete(lower, limit)) {
                merged.merge(email, flatScore(1, CONTACT_EMAIL_WEIGHT), ::maxOf)
            }
        }
        for (s in apps.complete(lower, limit)) {
            merged.merge(s.word, flatScore(s.frequency, APP_WEIGHT), ::maxOf)
        }
        if (!known) {
            for ((split, score) in splitCandidates(lower)) {
                merged.merge(split, score, ::maxOf)
            }
        }

        // Contact words carry their own capitalization ("Wasi"), so the
        // same word can arrive in two cases; keep the better-scored one.
        val byLower = HashMap<String, Pair<String, Double>>()
        for ((word, score) in merged) {
            val key = word.lowercase()
            val current = byLower[key]
            if (current == null || score > current.second) byLower[key] = word to score
        }
        return byLower.values
            .sortedWith(
                // Deterministic: score, then word — HashMap iteration order
                // must never decide a tie.
                compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first }
            )
            .asSequence()
            .map { it.first }
            .filterNot(::suppressed)
            .take(limit)
            // Emails are stored verbatim; case-matching the typed prefix would
            // corrupt the address ("John" -> "John.doe@..."). Commit as stored.
            .map { if (it.contains('@')) it else matchCase(composing, it) }
            .toList()
    }

    /** Log-space score for the flat (non-trie) sources, comparable with the
     * beam's `logWeight + ln(1 + freq)` shape. */
    private fun flatScore(frequency: Int, weight: Int): Double =
        ln(1.0 + frequency.toDouble() * weight)

    /**
     * A distribution over the character most likely to be typed next, given the
     * word-so-far [prefix]. Each dictionary word that starts with [prefix]
     * contributes its frequency to the single letter that would extend the
     * prefix by one; the personal lexicon counts extra so learned habits bias
     * the keyboard. Values are normalised to 0..1 with the top letter at 1.0.
     * Empty when the prefix is blank or completes to nothing.
     *
     * Deliberately cheap and approximate — it feeds smart key-hit detection,
     * which only nudges boundary taps, so an imperfect distribution is fine.
     */
    fun nextLetterWeights(prefix: String): Map<Char, Float> {
        if (prefix.isEmpty()) return emptyMap()
        val lower = prefix.lowercase()
        val at = lower.length
        val tally = HashMap<Char, Double>()
        fun fold(weight: Double, complete: (String, Int) -> List<Suggestion>) {
            for (s in complete(lower, NEXT_LETTER_SCAN)) {
                // Only genuine extensions; a completion equal to the prefix (the
                // word itself) predicts no next letter.
                if (s.word.length <= at) continue
                val ch = s.word[at].lowercaseChar()
                if (!ch.isLetter()) continue
                tally.merge(ch, s.frequency.toDouble() * weight, Double::plus)
            }
        }
        fold(1.0, activeDictionary::complete)
        fold(USER_WORD_WEIGHT.toDouble(), userLexicon::complete)
        fold(CUSTOM_WORD_WEIGHT.toDouble(), customDictionary::complete)
        val max = tally.values.maxOrNull() ?: return emptyMap()
        if (max <= 0.0) return emptyMap()
        return tally.mapValues { (it.value / max).toFloat() }
    }

    /**
     * Missing-space fixes: "ofthe" → "of the", scored by the rarer half so
     * two genuinely common words outrank a coincidental split.
     */
    private fun splitCandidates(word: String): List<Pair<String, Double>> {
        if (word.length < 4 || !word.all { it.isLetter() }) return emptyList()
        val results = ArrayList<Pair<String, Double>>()
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
                val score = ln(1.0 + minOf(leftFreq, rightFreq) * WEIGHT_SPLIT)
                results.add("$left $right" to score)
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
        return ordered.asSequence().filterNot(::suppressed).take(limit).toList()
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
        return ordered.asSequence().filterNot(::suppressed).take(limit).toList()
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
    fun shouldAutocorrect(word: String, touch: List<TouchPoint?>? = null): String? {
        val lower = word.lowercase()
        if (lower.length < 3) return null
        // An all-caps word is a deliberate acronym or shout, not a typo of a
        // lowercase word — don't "correct" it away when the user asked us not to.
        if (skipAllCapsAutocorrect && isAllCaps(word)) return null
        // The user already undid this exact correction once.
        if (lower in rejectedCorrections) return null
        if (inDictionaries(lower) || userLexicon.contains(lower)) return null
        // Contact and app names are known words too — never "corrected" away.
        if (contacts.contains(lower) || apps.contains(lower)) return null

        val candidates = rankedFor(
            lower, FuzzyBeamSearch.AUTOCORRECT_K / 2, touch,
        ).filter { c ->
            // Silent replacement only trusts classic one-edit shapes: a single
            // edit within one character of the typed length, or the
            // one-extra-letter completion the old insert-at-end edit produced.
            // Two-edit words and edited-then-completed words stay in the strip
            // — and, crucially, they don't stand in as runner-ups that block
            // an otherwise-unopposed correction. A suppressed word
            // (blacklisted or offensive) is never a target either.
            // Exact shapes only: a pure edit with no completion tail, or the
            // one-extra-letter pure completion. An edited-then-completed
            // inflection ("questiom" -> "questions") must be neither a target
            // nor the runner-up that blocks the real fix.
            val correctionShaped = when (c.edits) {
                0 -> c.completedChars == 1 && c.word != lower
                1 -> c.completedChars == 0
                else -> false
            }
            correctionShaped && !suppressed(c.word)
        }.map { c ->
            // A one-extra-letter completion rides the walk at zero cost, but
            // as a *correction* it is the old insert-at-end edit and must
            // carry that edit's weight — both as a target and as the
            // runner-up that gates someone else's correction.
            if (c.edits == 0) {
                FuzzyBeamSearch.ScoredCandidate(
                    c.word, c.score - FuzzyBeamSearch.COST_INSERT_ADJACENT,
                    FuzzyBeamSearch.COST_INSERT_ADJACENT, 1, 0, c.tier,
                    c.dictScore - FuzzyBeamSearch.COST_INSERT_ADJACENT,
                    c.userScore - FuzzyBeamSearch.COST_INSERT_ADJACENT,
                )
            } else {
                c
            }
        }.sortedWith(
            compareByDescending<FuzzyBeamSearch.ScoredCandidate> { it.score }.thenBy { it.word }
        )

        // Two independent sources naming the same word is confidence enough
        // on its own.
        var bestDict: String? = null
        var bestDictScore = Double.NEGATIVE_INFINITY
        var bestUser: String? = null
        var bestUserScore = Double.NEGATIVE_INFINITY
        for (c in candidates) {
            if (c.dictScore > bestDictScore) {
                bestDictScore = c.dictScore
                bestDict = c.word
            }
            if (c.userScore > bestUserScore) {
                bestUserScore = c.userScore
                bestUser = c.word
            }
        }
        if (bestDict != null && bestUser != null && bestDict == bestUser) {
            return matchCase(word, bestDict)
        }

        val top = candidates.firstOrNull() ?: return null
        // With no runner-up, a synthetic floor stands in: an unopposed but
        // weak candidate (rare word reached by an expensive edit) must not
        // fire just because nothing else was nearby.
        val runnerUpScore = candidates.getOrNull(1)?.score ?: SOLO_RUNNER_UP_SCORE
        if (top.score - runnerUpScore < ln(autocorrectConfidence)) return null
        return matchCase(word, top.word)
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
