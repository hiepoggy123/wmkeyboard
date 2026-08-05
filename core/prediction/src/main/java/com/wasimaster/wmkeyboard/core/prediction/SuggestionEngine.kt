package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.transliteration.AvroPhonetic
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
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
     * secondary-language use, and so learned words tagged with a different
     * language are damped (see [rankedFor]). Blank when no language is set
     * (tests). Bumps the walk generation: the damp is applied inside the
     * cached walk, so a language switch must invalidate it.
     */
    @Volatile
    private var primaryLanguageIdField: String = ""
    var primaryLanguageId: String
        get() = primaryLanguageIdField
        set(value) {
            if (value == primaryLanguageIdField) return
            primaryLanguageIdField = value
            generation.incrementAndGet()
        }

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
     * Words recently committed in the app now being typed in — an in-memory
     * recency overlay from the IME, giving each app's own vocabulary a small
     * ranking edge there. Read post-cache, so no generation bump.
     */
    @Volatile
    var contextWords: Set<String> = emptySet()

    /**
     * Optional reordering model over the ranked top candidates; see
     * [CandidateReranker]. Applied only when the caller passes
     * `allowRerank = true` — the synchronous main-thread call sites never do.
     */
    @Volatile
    var reranker: CandidateReranker = CandidateReranker.NONE

    /**
     * Downloaded corpus n-grams for the active language ([NgramPack.EMPTY]
     * until a pack lands). Corpus context: consulted below every personal
     * store, and its counts are damped so one habitual personal pair beats
     * any population prior.
     */
    @Volatile
    var ngramPack: NgramPack = NgramPack.EMPTY

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
     * When on, [shouldAutocorrect] may return a two-word split ("kortehobe" →
     * "korte hobe") when no single-word correction fires and both halves are
     * known words that clear the same confidence gate. Off by default — the
     * IME turns it on from the user's setting; the standalone spell checker
     * judges isolated words and leaves it off.
     */
    @Volatile
    var autocorrectSplits: Boolean = false

    /**
     * When on, a composing word carrying exactly one digit may be corrected
     * by a same-length substitution at that digit ("as3" → "ase", a
     * number-row slip above the intended letter). Off by default: only the
     * IME path that actually buffers number-row digits into words sets it,
     * so the spell checker never rewrites genuine alphanumerics.
     */
    @Volatile
    var digitSlipCorrections: Boolean = false

    /**
     * Letters flanking the spacebar on the active layout's bottom row: a
     * stray one of these between two known words is read as a fat-fingered
     * space by [splitCandidates]. Defaults to the QWERTY family's set.
     */
    @Volatile
    var spaceAdjacentKeys: String = SPACE_ADJACENT_DEFAULT

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
     * Autocorrect's memory of its own mistakes: per-pair revert penalties and
     * the fired/reverted ratio behind [adaptiveConfidence]. The default is a
     * memory-only instance (null file), so tests and locked-boot sessions get
     * the session-scoped guarantees with no storage; the IME swaps in the
     * persisted store from attachPersonalStores.
     */
    @Volatile
    var correctionStats: CorrectionStats = CorrectionStats(null)

    /**
     * When on, the effective confidence gate is scaled by the user's recent
     * revert rate — a keyboard being corrected-then-undone often demands more
     * certainty before forcing anything. The slider setting stays the anchor.
     */
    @Volatile
    var adaptiveConfidence: Boolean = true

    /**
     * Register of the field being typed into, set by the IME from the target
     * app and field kind when the user enables register priors. CASUAL nudges
     * chat-speak up, FORMAL pushes it down; NEUTRAL (always, when the setting
     * is off) changes nothing. Ranking only — never gates what commits.
     */
    @Volatile
    var register: Register = Register.NEUTRAL

    /**
     * Records that the user undid the autocorrect of [typed] into
     * [corrected] (typically by backspacing over it). The exact pair is
     * blocked for this session and penalized across sessions; other
     * corrections of the same typed word are deliberately untouched.
     */
    fun rejectCorrection(typed: String, corrected: String) {
        if (typed.isNotEmpty() && corrected.isNotEmpty()) {
            correctionStats.recordRevert(typed, corrected)
        }
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
        val k = maxOf(limit * 2, WALK_K)
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
        // search() sizes its own result list as max(limit * 2, AUTOCORRECT_K);
        // k / 2 makes that exactly k.
        val walked = beam.search(
            walkSources(), lower, proximity, k / 2, beamWorkspace.get(), touch = scoring,
        )
        val ranked = dampMismatchedLanguages(walked)
        rankedWalk = RankedWalk(lower, gen, lexGen, k, touch?.let(::ArrayList), ranked)
        return ranked
    }

    /**
     * Damp learned-only words tagged with a language other than the active
     * one, so Bengali-romanized habits learned under bn_rom stop crowding the
     * English strip (and vice versa). Applies only to pure-user candidates —
     * anything a dictionary also knows is a real word of the active language
     * — and only to tagged words: untagged (legacy, settings-app) words
     * belong to every language. The damp is a ranking handicap, not a ban;
     * a strong habit still surfaces when nothing else fits.
     */
    private fun dampMismatchedLanguages(
        ranked: List<FuzzyBeamSearch.ScoredCandidate>,
    ): List<FuzzyBeamSearch.ScoredCandidate> {
        val active = primaryLanguageId
        if (active.isEmpty()) return ranked
        var changed = false
        val damped = ranked.map { c ->
            val pureUser = c.dictScore == Double.NEGATIVE_INFINITY &&
                c.userScore != Double.NEGATIVE_INFINITY
            if (!pureUser) return@map c
            val tag = userLexicon.languageOf(c.word) ?: return@map c
            if (tag == active) return@map c
            changed = true
            FuzzyBeamSearch.ScoredCandidate(
                c.word, c.score - LANG_MISMATCH_DAMP, c.editCost, c.edits,
                c.completedChars, c.tier, c.dictScore, c.userScore - LANG_MISMATCH_DAMP,
            )
        }
        if (!changed) return ranked
        return damped.sortedWith(
            compareByDescending<FuzzyBeamSearch.ScoredCandidate> { it.score }.thenBy { it.word }
        )
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

        /** Split weight when a stray boundary letter had to be dropped: ~1
         * nat under [WEIGHT_SPLIT], pricing the dropped letter like a far
         * substitution rather than a plain deletion. Tuned against the eval
         * harness — at deletion-cost pricing (0.55) these splits crowded
         * genuine one-edit corrections out of the strip's top-1 (-0.45pt);
         * the halves must together be ~3x more frequent than the correction
         * they outrank, which is what "both halves are clearly words" means. */
        private const val WEIGHT_SPLIT_DROPPED = 0.3

        /** Letters that flank the spacebar on a QWERTY-family bottom row. */
        const val SPACE_ADJACENT_DEFAULT = "cvbnm"

        /** Shortest typed run a split autocorrect may rewrite: two 2-letter
         * halves plus margin — below this, splits stay strip suggestions. */
        private const val SPLIT_AUTOCORRECT_MIN_LENGTH = 5

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

        /** Shape of the learned-bigram context boost on completions. */
        private const val CONTEXT_BIGRAM_BETA = 0.5

        /** Cap: habitual pairs may re-rank the strip, never bury an exact
         * high-frequency match (ln 4 — a 4x multiplicative equivalent). */
        private val MAX_CONTEXT_BOOST = ln(4.0)

        /** Seed pairs are weaker evidence than the user's own habits. */
        private val SEED_CONTEXT_BOOST = ln(1.5)

        /** Handicap on a once-reverted pair: the x0.25 of the old design. */
        private val PAIR_PENALTY = ln(4.0)

        /** Recency edge for words typed recently in the same app. */
        private val APP_RECENCY_BOOST = ln(1.3)

        /** Handicap on a learned-only word tagged with another language: a
         * 3x frequency disadvantage, enough to stop cross-language crowding
         * without ever hiding a genuinely strong habit. */
        private val LANG_MISMATCH_DAMP = ln(3.0)

        /** Register prior shifts: chat-speak's edge in casual fields, and its
         * (heavier) handicap in formal ones — misranking "lol" upward in an
         * email costs more than missing it in a chat. */
        private val CASUAL_INFORMAL_BOOST = ln(1.5)
        private val FORMAL_INFORMAL_DAMP = ln(2.5)

        // Join-chip guards: the joined word must be a reasonably common word
        // and clearly beat the rarer of its parts (mirror of WEIGHT_SPLIT's
        // conservatism, inverted).
        private const val JOIN_MAX_LENGTH = 24
        private const val JOIN_MIN_FREQ = 50
        private const val JOIN_CONFIDENCE = 1.25

        /** How many ranked candidates a reranker may reorder. */
        private const val RERANK_POOL = 8

        /**
         * How deep the fuzzy walk ranks for the suggest path. The post-walk
         * context boosts can only promote candidates the walk emitted, and
         * ContextMissRateTest measured that single-error targets land in the
         * walk's ranks 9-32 often enough to matter (11% of rarest-quartile
         * targets fall outside the top-8; under 1.2% fall outside the top-32).
         * Autocorrect deliberately still judges only the top
         * [FuzzyBeamSearch.AUTOCORRECT_K]: its confidence gate compares the
         * top pair, and deeper candidates should neither become silent
         * replacements nor stand in as runner-ups.
         */
        private const val WALK_K = 32

        /** Corpus n-gram counts divided by this before joining the personal
         * evidence scale: one personal use ~ this many corpus sightings. */
        private const val PACK_COUNT_SCALE = 50
    }

    /**
     * @param composing the word currently being typed (may be empty)
     * @param previousWord last committed word, used for next-word prediction
     * @param avroMode when true, [composing] is romanized Bengali and the
     *        top suggestion is its transliteration
     * @param limit how many candidates to return
     * @param touch per-character tap positions (null entries fall back to
     *        the discrete adjacency model)
     * @param previousWord2 the word before [previousWord], for trigram context
     * @param recentWords the last few committed words, a topical recency bag
     *        for the reranker
     * @param allowRerank whether [reranker] may reorder the head of the list
     *        (never set on the synchronous main-thread call sites)
     */
    fun suggest(
        composing: String,
        previousWord: String?,
        avroMode: Boolean = false,
        limit: Int = 5,
        touch: List<TouchPoint?>? = null,
        previousWord2: String? = null,
        recentWords: List<String> = emptyList(),
        allowRerank: Boolean = false,
    ): List<String> {
        if (composing.isEmpty()) {
            return nextWords(previousWord, previousWord2, limit)
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

        // Context re-rank: a candidate the user has typed after [previousWord]
        // before (or that the seed pairs know as a follower) gets a bounded
        // log-space boost. Completions historically ignored context entirely;
        // this is one map hit per candidate on the async path.
        val prev = previousWord?.lowercase()
        if (prev != null) {
            val prev2 = previousWord2?.lowercase()
            for (entry in merged.entries) {
                val candidate = entry.key.lowercase()
                val count = maxOf(
                    userLexicon.bigramCount(prev, candidate),
                    // The two-word context is rarer and stronger evidence;
                    // its raw count rides the same bounded boost curve.
                    if (prev2 != null) {
                        userLexicon.trigramCount(prev2, prev, candidate) * 2
                    } else {
                        0
                    },
                    // Corpus counts are damped so a personal pair typed once
                    // outranks a population prior seen dozens of times.
                    ngramPack.bigramCount(prev, candidate) / PACK_COUNT_SCALE,
                    if (prev2 != null) {
                        ngramPack.trigramCount(prev2, prev, candidate) * 2 / PACK_COUNT_SCALE
                    } else {
                        0
                    },
                )
                val boost = when {
                    count > 0 -> minOf(
                        ln(1.0 + CONTEXT_BIGRAM_BETA * ln(1.0 + count)),
                        MAX_CONTEXT_BOOST,
                    )
                    englishSources && seedBigrams.follows(prev, candidate) -> SEED_CONTEXT_BOOST
                    else -> 0.0
                }
                if (boost > 0.0) entry.setValue(entry.value + boost)
            }
        }

        // Words recently typed in this very app get a small recency edge.
        if (contextWords.isNotEmpty()) {
            for (entry in merged.entries) {
                if (entry.key.lowercase() in contextWords) {
                    entry.setValue(entry.value + APP_RECENCY_BOOST)
                }
            }
        }

        // Register prior: chat-speak rises in messaging fields, sinks in
        // formal ones. Bounded like every other boost — it re-ranks, never
        // hides; "lol" still surfaces in an email if nothing else matches.
        if (register != Register.NEUTRAL) {
            val shift = if (register == Register.CASUAL) {
                CASUAL_INFORMAL_BOOST
            } else {
                -FORMAL_INFORMAL_DAMP
            }
            for (entry in merged.entries) {
                if (entry.key.lowercase() in RegisterVocabulary.informal) {
                    entry.setValue(entry.value + shift)
                }
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
        val ranked = byLower.values
            .sortedWith(
                // Deterministic: score, then word — HashMap iteration order
                // must never decide a tie.
                compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first }
            )
            .asSequence()
            .map { it.first }
            .filterNot(::suppressed)
            .take(maxOf(limit, RERANK_POOL))
            .toList()

        // Optional model pass over the head of the list; null keeps our order.
        val reordered = if (allowRerank && reranker !== CandidateReranker.NONE) {
            val pool = ranked.take(RERANK_POOL)
            reranker.rerank(
                RerankContext(composing, previousWord, recentWords, previousWord2), pool,
            )
                ?.filter { it in pool }
                ?.let { it + ranked.filterNot(it::contains) }
        } else {
            null
        }

        return (reordered ?: ranked)
            .take(limit)
            // Emails are stored verbatim; case-matching the typed prefix would
            // corrupt the address ("John" -> "John.doe@..."). Commit as stored.
            .map { if (it.contains('@')) it else matchCase(composing, it) }
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
     * The inverse of a split: the previous word and the word being composed
     * concatenate into something more plausible than the parts — "some" +
     * "thing" -> "something". Chip-only (never an autocorrect: it rewrites
     * text already committed to the field) and deliberately conservative:
     * the joined word must be reasonably common and beat the rarer part by a
     * clear margin, so "a" + "nd" doesn't offer "and" on every stumble.
     */
    fun joinCandidate(previousWord: String?, composing: String): String? {
        val prev = previousWord?.lowercase() ?: return null
        if (WordContext.isSentinel(prev)) return null
        val lower = composing.lowercase()
        if (lower.length < 2 || prev.isEmpty()) return null
        if (!prev.all { it.isLetter() } || !lower.all { it.isLetter() }) return null
        val joined = prev + lower
        if (joined.length > JOIN_MAX_LENGTH || suppressed(joined)) return null
        fun freqOf(word: String) = maxOf(
            dictionaryFrequencyOf(word),
            weighted(userLexicon.frequencyOf(word), USER_WORD_WEIGHT),
        )
        val joinedFreq = freqOf(joined)
        if (joinedFreq < JOIN_MIN_FREQ) return null
        val rarerPart = minOf(freqOf(prev), freqOf(lower))
        if (joinedFreq * JOIN_CONFIDENCE <= rarerPart) return null
        return joined
    }

    /**
     * Missing-space fixes: "ofthe" → "of the", scored by the rarer half so
     * two genuinely common words outrank a coincidental split.
     *
     * Also covers the fat-fingered spacebar: a stray [spaceAdjacentKeys]
     * letter between two known words ("amibtomake") was probably a space
     * press that landed on the bottom row, so the split that drops it is
     * offered too — at a discount that mirrors the walk's deletion cost, so
     * an exact split of the same material always outranks a dropped-letter
     * reading of it.
     */
    private fun splitCandidates(word: String): List<Pair<String, Double>> {
        if (word.length < 4 || !word.all { it.isLetter() }) return emptyList()
        val results = ArrayList<Pair<String, Double>>()
        fun freqOf(part: String) = maxOf(
            dictionaryFrequencyOf(part),
            userLexicon.frequencyOf(part) * USER_WORD_WEIGHT,
        )
        for (i in 1 until word.length) {
            val left = word.substring(0, i)
            val leftFreq = freqOf(left)
            if (leftFreq <= 0) continue
            val right = word.substring(i)
            val rightFreq = freqOf(right)
            if (rightFreq > 0) {
                val score = ln(1.0 + minOf(leftFreq, rightFreq) * WEIGHT_SPLIT)
                results.add("$left $right" to score)
            }
            // Boundary char dropped: both halves must be real words of some
            // substance — single-letter halves ("a", "i") explain nearly any
            // string and would fire on every stumble.
            if (i + 1 < word.length - 1 && word[i] in spaceAdjacentKeys && left.length >= 2) {
                val tail = word.substring(i + 1)
                if (tail.length >= 2) {
                    val tailFreq = freqOf(tail)
                    if (tailFreq > 0) {
                        val score = ln(1.0 + minOf(leftFreq, tailFreq) * WEIGHT_SPLIT_DROPPED)
                        results.add("$left $tail" to score)
                    }
                }
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

    private fun nextWords(previousWord: String?, previousWord2: String?, limit: Int): List<String> {
        val prev = previousWord?.lowercase() ?: return emptyList()
        val ordered = LinkedHashSet<String>()
        // Most specific first: the two-word context, when known, beats the
        // bigram tail ("I was" -> "going" over everything "was" alone knows).
        previousWord2?.lowercase()?.let { prev2 ->
            ordered.addAll(userLexicon.nextWordsAfter(prev2, prev, limit))
        }
        // Learned bigrams next — the user's own phrases always beat the
        // bundled seed pairs, which only cover the cold start.
        ordered.addAll(userLexicon.nextWords(prev, limit))
        // A contact's name chains through the strip: "Wasi" offers "Mollik".
        ordered.addAll(contacts.nextWords(prev))
        // Corpus n-grams (downloaded pack): below everything personal, above
        // the bundled seeds they supersede. The trigram context first.
        if (!ngramPack.isEmpty) {
            previousWord2?.lowercase()?.let { prev2 ->
                ordered.addAll(ngramPack.nextWordsAfter(prev2, prev, limit))
            }
            ordered.addAll(ngramPack.nextWords(prev, limit))
        }
        // Seed bigrams are English pairs; they only cold-start English modes.
        if (englishSources) ordered.addAll(seedBigrams.nextWords(prev))
        // Skip-gram rescue: an unknown prev (a just-typed name, a typo) has
        // no followers anywhere and the strip would go quiet. Treat it as
        // transparent and backfill from the word before it — "met Priya"
        // still offers what tends to follow "met". Appended after every
        // direct source, so genuine followers of prev always rank first.
        if (ordered.size < limit && previousWord2 != null) {
            val prev2 = previousWord2.lowercase()
            ordered.addAll(userLexicon.nextWords(prev2, limit))
            if (!ngramPack.isEmpty) ordered.addAll(ngramPack.nextWords(prev2, limit))
        }
        var result = ordered.asSequence()
            .filterNot(::suppressed)
            // Belt and braces: the sentence-start sentinel is context, never
            // an offer — nothing should ever have learned it as a follower.
            .filterNot { WordContext.isSentinel(it) }
            .take(limit)
            .toList()
        // Formal fields: chat-speak yields its slot when anything else is
        // on offer (order-based here — this path carries no scores).
        if (register == Register.FORMAL) {
            result = result.sortedBy { it.lowercase() in RegisterVocabulary.informal }
        }
        return result
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
     *
     * @param word the composing word a space or enter is about to commit
     * @param touch per-character tap positions, as in [suggest]
     * @param timingMultiplier scales the confidence gate from the typing
     *        rhythm of this word: fast, sloppy bursts (< 1.0) correct more
     *        eagerly, slow deliberate typing (> 1.0) demands near-certainty.
     *        1.0 — the default, and always when the setting is off — keeps
     *        the gate exactly at the slider value.
     */
    fun shouldAutocorrect(
        word: String,
        touch: List<TouchPoint?>? = null,
        timingMultiplier: Double = 1.0,
    ): String? {
        val lower = word.lowercase()
        if (lower.length < 3) return null
        // An all-caps word is a deliberate acronym or shout, not a typo of a
        // lowercase word — don't "correct" it away when the user asked us not to.
        if (skipAllCapsAutocorrect && isAllCaps(word)) return null
        if (inDictionaries(lower) || userLexicon.contains(lower)) return null
        // Contact and app names are known words too — never "corrected" away.
        if (contacts.contains(lower) || apps.contains(lower)) return null
        // Digits: exactly one digit may be a number-row slip (when the IME
        // buffers those); anything more digit-heavy is deliberate input —
        // codes, model numbers — and is never rewritten.
        val digits = lower.count { it.isDigit() }
        if (digits > 0 && (!digitSlipCorrections || digits > 1)) return null

        // The walk ranks WALK_K deep for the strip's context boosts, but the
        // silent-replacement decision stays on the same top-8 it has always
        // judged: a rank-20 word must never fire as a correction, nor may it
        // appear as the runner-up that tightens (or loosens) the gate.
        val candidates = rankedFor(
            lower, FuzzyBeamSearch.AUTOCORRECT_K / 2, touch,
        ).take(FuzzyBeamSearch.AUTOCORRECT_K).filter { c ->
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
            // A digit-carrying word is only ever fixed in place: same length,
            // letters untouched, the digit swapped for a letter. A deletion
            // ("room3" -> "room") or completion reading would rewrite text
            // the user typed on purpose.
            correctionShaped && !suppressed(c.word) &&
                (digits == 0 || digitSubShape(lower, c.word))
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
        }.mapNotNull { c ->
            // Pair penalties: an exact correction the user undid is blocked
            // (never a target, never the runner-up that gates another fix);
            // a once-reverted pair fights with a heavy handicap and loses
            // its shortcut privileges.
            when (correctionStats.penalty(lower, c.word)) {
                CorrectionStats.Penalty.BLOCKED -> null
                CorrectionStats.Penalty.PENALIZED -> FuzzyBeamSearch.ScoredCandidate(
                    c.word, c.score - PAIR_PENALTY, c.editCost, c.edits,
                    c.completedChars, c.tier,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                )
                CorrectionStats.Penalty.NONE -> c
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

        val effectiveConfidence = (
            autocorrectConfidence *
                (if (adaptiveConfidence) correctionStats.confidenceMultiplier() else 1.0) *
                timingMultiplier
            ).coerceIn(MIN_AUTOCORRECT_CONFIDENCE, MAX_AUTOCORRECT_CONFIDENCE)
        val top = candidates.firstOrNull()
        val single = when {
            top == null -> null
            // A penalized candidate with no competition stays a suggestion:
            // the user already told us once that this exact fix was wrong.
            candidates.size == 1 && correctionStats.penalty(lower, top.word) !=
                CorrectionStats.Penalty.NONE -> null
            // With no runner-up, a synthetic floor stands in: an unopposed
            // but weak candidate (rare word reached by an expensive edit)
            // must not fire just because nothing else was nearby.
            top.score - (candidates.getOrNull(1)?.score ?: SOLO_RUNNER_UP_SCORE) <
                ln(effectiveConfidence) -> null
            else -> top.word
        }
        if (single != null) return matchCase(word, single)
        // No single word explains the typed string; a missing space might.
        val split = splitCorrection(lower, top?.score, effectiveConfidence) ?: return null
        return matchCase(word, split)
    }

    /**
     * Missing-space autocorrect: "kortehobe" → "korte hobe", including the
     * fat-fingered-space reading ("amibtomake" → "ami tomake"). Considered
     * only after every single-word gate declined, and held to the same
     * confidence discipline: the best split must beat the best single-word
     * candidate, the runner-up split, and the solo floor by the gate margin,
     * with halves of at least two letters. The committed text becomes two
     * words — the IME's learn/revert paths already handle multi-word commits.
     */
    private fun splitCorrection(
        lower: String,
        bestWordScore: Double?,
        effectiveConfidence: Double,
    ): String? {
        if (!autocorrectSplits) return null
        if (lower.length < SPLIT_AUTOCORRECT_MIN_LENGTH) return null
        val splits = splitCandidates(lower)
            .filter { (candidate, _) ->
                candidate.split(' ').all { it.length >= 2 && !suppressed(it) }
            }
            .sortedWith(
                compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first }
            )
        val (best, bestScore) = splits.firstOrNull() ?: return null
        // The same pair memory word corrections use: a split the user
        // reverted is never forced on them again.
        if (correctionStats.penalty(lower, best) != CorrectionStats.Penalty.NONE) return null
        val rival = maxOf(
            bestWordScore ?: Double.NEGATIVE_INFINITY,
            splits.getOrNull(1)?.second ?: Double.NEGATIVE_INFINITY,
            SOLO_RUNNER_UP_SCORE,
        )
        if (bestScore - rival < ln(effectiveConfidence)) return null
        return best
    }

    /** True when [candidate] is [typed] with its single digit swapped for a
     * letter and every other character untouched. */
    private fun digitSubShape(typed: String, candidate: String): Boolean {
        if (candidate.length != typed.length) return false
        for (i in typed.indices) {
            val t = typed[i]
            if (t.isDigit()) {
                if (candidate[i].isDigit()) return false
            } else if (candidate[i] != t) {
                return false
            }
        }
        return true
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
