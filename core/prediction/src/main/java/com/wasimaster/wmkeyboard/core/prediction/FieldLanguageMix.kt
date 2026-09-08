package com.wasimaster.wmkeyboard.core.prediction

import kotlin.math.min

/**
 * Tracks which language the text field in front of the user is being written
 * in, from the words already committed there, so [SuggestionEngine] can lean
 * its suggestions and autocorrect toward that language while it is the one
 * being typed.
 *
 * [LanguageMixConfidence] answers a different question — how much the user
 * leans on each language *over weeks* — and moves too slowly to notice that
 * this particular message switched to romanized Bengali three words ago. This
 * class is that fast signal: per field, seeded from the words already in the
 * field when the keyboard opens, updated on every commit, and thrown away when
 * the user moves on. Nothing here is persisted.
 *
 * Each recorded word credits every language whose dictionary contains it, so
 * a word valid in two languages of the mix pushes neither ahead. Tallies decay
 * per word, which keeps the signal on the last few words — mid-sentence
 * code-switching swings it back within two or three words.
 */
class FieldLanguageMix {

    /** Decayed credit per language id for words in the current field. */
    private val tally = HashMap<String, Double>()

    /**
     * Decayed count of words that at least one language of the mix owned.
     * Unclassified words (typos, names, another language entirely) still decay
     * the tallies — a long run of them means the old evidence is stale — but
     * add nothing, so the mix drifts back to neutral rather than to a guess.
     */
    private var evidence = 0.0

    /** Forget the field's words entirely (leaving a field, or detection off). */
    @Synchronized
    fun reset() {
        tally.clear()
        evidence = 0.0
    }

    /**
     * Start the field from where its app's fields usually go — see
     * [AppLanguageMix]. Worth [Prior.weight] words of evidence, capped at
     * [MAX_PRIOR_WEIGHT] so a habit never outweighs what is actually typed:
     * the next real word decays it like any older word and outvotes it.
     * Called on a fresh mix, before the field's own words are recorded.
     */
    @Synchronized
    fun seedPrior(prior: Prior) {
        val weight = prior.weight.coerceIn(0.0, MAX_PRIOR_WEIGHT)
        if (weight <= 0.0) return
        for ((langId, share) in prior.shares) {
            if (langId.isNotEmpty() && share > 0.0) tally.merge(langId, share * weight, Double::plus)
        }
        evidence += weight
    }

    /**
     * Where a field's language usually starts before a word is typed in it:
     * each language's share of the habit, and how many words of evidence the
     * habit is worth.
     */
    class Prior(val shares: Map<String, Double>, val weight: Double)

    /**
     * Record one committed word, credited to every language in [owners] —
     * empty when no dictionary in the mix knows the word. Older words fade
     * first so the most recent few dominate.
     */
    @Synchronized
    fun record(owners: Set<String>) {
        val iterator = tally.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val decayed = entry.value * DECAY
            if (decayed < FLOOR) iterator.remove() else entry.setValue(decayed)
        }
        evidence *= DECAY
        if (owners.isEmpty()) return
        for (langId in owners) {
            if (langId.isEmpty()) continue
            tally.merge(langId, 1.0, Double::plus)
        }
        evidence += 1.0
    }

    /**
     * The current per-language shares, or null while there is not yet enough
     * classified text to say anything — the caller must then behave exactly
     * as if this class did not exist.
     */
    @Synchronized
    fun shares(): Shares? {
        if (evidence < MIN_EVIDENCE) return null
        val total = tally.values.sum()
        if (total <= 0.0) return null
        val shares = HashMap<String, Double>(tally.size * 2)
        for ((langId, credit) in tally) shares[langId] = credit / total
        return Shares(shares, min(1.0, evidence / FULL_EVIDENCE))
    }

    /**
     * A read-consistent snapshot: each language's share of the classified
     * words in [0, 1], and [ramp] — how much of the full detection shift the
     * evidence so far justifies. One word moves the mix halfway; three make
     * it certain.
     */
    class Shares(private val shares: Map<String, Double>, val ramp: Double) {
        fun shareOf(langId: String): Double = shares[langId] ?: 0.0
    }

    companion object {
        /**
         * Per-word decay. 0.7 keeps roughly the last three words in charge,
         * so "ami tomake" flips the mix and the next English clause flips it
         * back just as fast.
         */
        private const val DECAY = 0.7

        /** Below this a language's credit is noise and is dropped. */
        private const val FLOOR = 0.01

        /** No shift at all until at least one classified word has been seen. */
        private const val MIN_EVIDENCE = 0.75

        /** Classified words (decayed) at which the shift reaches full size. */
        private const val FULL_EVIDENCE = 2.0

        /** The most a [Prior] may be worth: under [FULL_EVIDENCE], so a habit
         * alone never reaches the full swing. */
        const val MAX_PRIOR_WEIGHT = 1.5
    }
}
