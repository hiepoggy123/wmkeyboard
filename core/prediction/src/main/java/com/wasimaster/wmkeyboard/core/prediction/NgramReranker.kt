package com.wasimaster.wmkeyboard.core.prediction

import kotlin.math.ln

/**
 * The context reranker: a deterministic interpolated n-gram rescorer over the
 * engine's top candidates, built entirely from data the keyboard already has
 * — the user's learned bigrams and trigrams, the bundled seed-pair counts
 * (which the strip's own ranking never read), and the last few committed
 * words as a topical recency bag. No network, no native code, no model file.
 *
 * Where the engine's in-strip context boost is deliberately capped (a habit
 * may re-rank but never bury a frequent exact match), this pass — opt-in per
 * call, Play-channel builds only — rescores the head of the list with the
 * full interpolated evidence. It returns null whenever no candidate has any
 * context evidence at all, which is the common case and means the engine's
 * order stands untouched; a reorder can therefore only ever be
 * evidence-driven.
 *
 * Cost: a handful of synchronized map reads over at most eight candidates —
 * microseconds, no deadline machinery needed.
 */
class NgramReranker(
    private val userLexicon: UserLexicon,
    private val seedBigrams: SeedBigrams,
    /** Live dictionary frequency, read through to whatever list the engine
     * currently holds (downloads swap it at runtime). */
    private val dictionaryFrequency: (String) -> Int,
) : CandidateReranker {

    override fun rerank(context: RerankContext, candidates: List<String>): List<String>? {
        val prev = context.previousWord?.lowercase() ?: return null
        if (WordContext.isSentinel(prev) || candidates.isEmpty()) return null
        val prev2 = context.previousWord2?.lowercase()
        val recent = if (context.recentWords.isEmpty()) {
            emptySet()
        } else {
            context.recentWords.mapTo(HashSet()) { it.lowercase() }
        }

        var anyEvidence = false
        val scored = candidates.map { word ->
            val w = word.lowercase()
            val user3 = if (prev2 != null) userLexicon.trigramCount(prev2, prev, w) else 0
            val user2 = userLexicon.bigramCount(prev, w)
            val seed = seedBigrams.count(prev, w)
            val recency = if (w in recent) 1 else 0
            val evidence = WEIGHT_USER_TRIGRAM * ln(1.0 + user3) +
                WEIGHT_USER_BIGRAM * ln(1.0 + user2) +
                WEIGHT_SEED_BIGRAM * ln(1.0 + seed) +
                WEIGHT_RECENCY * recency
            if (evidence > 0.0) anyEvidence = true
            val base = ln(
                1.0 + dictionaryFrequency(w) +
                    userLexicon.frequencyOf(w).toDouble() * USER_WORD_WEIGHT
            )
            word to base + evidence
        }
        if (!anyEvidence) return null

        // Stable sort: candidates with equal scores keep the engine's order.
        return scored.sortedByDescending { it.second }.map { it.first }
    }

    private companion object {
        /** Mirrors SuggestionEngine's user-word weighting so the base term
         * ranks personal words the way the strip already does. */
        const val USER_WORD_WEIGHT = 500

        // Interpolation weights: the more specific and personal the evidence,
        // the harder it counts. Seed pairs are population priors; recency is
        // a flat topical nudge.
        const val WEIGHT_USER_TRIGRAM = 1.2
        const val WEIGHT_USER_BIGRAM = 0.8
        const val WEIGHT_SEED_BIGRAM = 0.4
        const val WEIGHT_RECENCY = 0.3
    }
}
