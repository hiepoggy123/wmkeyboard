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
 * call — rescores the head of the list with the full interpolated
 * evidence. It returns null whenever no candidate has any
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
    /** The active language's downloaded corpus pack, read live for the same
     * reason. Defaults to empty for callers without packs. */
    private val ngramPack: () -> NgramPack = { NgramPack.EMPTY },
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

        val pack = ngramPack()
        // Skip-gram backoff: when the immediate previous word is one no store
        // has ever seen (a name, a typo — the common reason context suddenly
        // goes silent), treat it as transparent and let the word before it
        // vouch through its own bigrams: "met Priya at" still knows "at"
        // follows "met". Only ever consulted for an OOV prev — a known prev's
        // direct evidence must never be diluted by the gappy kind.
        val skipContext = if (
            prev2 != null && dictionaryFrequency(prev) == 0 && !userLexicon.contains(prev)
        ) {
            prev2
        } else {
            null
        }
        var anyEvidence = false
        val scored = candidates.mapIndexed { index, word ->
            val w = word.lowercase()
            val user3 = if (prev2 != null) userLexicon.trigramCount(prev2, prev, w) else 0
            val user2 = userLexicon.bigramCount(prev, w)
            val pack3 = if (prev2 != null) pack.trigramCount(prev2, prev, w) else 0
            val pack2 = pack.bigramCount(prev, w)
            val seed = seedBigrams.count(prev, w)
            val recency = if (w in recent) 1 else 0
            val skipUser = if (skipContext != null) userLexicon.bigramCount(skipContext, w) else 0
            val skipPack = if (skipContext != null) pack.bigramCount(skipContext, w) else 0
            val evidence = term(WEIGHT_USER_TRIGRAM, user3, CAP_USER_TRIGRAM) +
                term(WEIGHT_USER_BIGRAM, user2, CAP_USER_BIGRAM) +
                term(WEIGHT_PACK_TRIGRAM, pack3 / PACK_COUNT_SCALE, CAP_PACK_TRIGRAM) +
                term(WEIGHT_PACK_BIGRAM, pack2 / PACK_COUNT_SCALE, CAP_PACK_BIGRAM) +
                term(WEIGHT_SEED_BIGRAM, seed, CAP_SEED) +
                term(WEIGHT_SKIP_USER, skipUser, CAP_SKIP_USER) +
                term(WEIGHT_SKIP_PACK, skipPack / PACK_COUNT_SCALE, CAP_SKIP_PACK) +
                WEIGHT_RECENCY * recency
            if (evidence > 0.0) anyEvidence = true
            // The base is the ENGINE'S rank, not a recomputed frequency: the
            // incoming order already encodes edit costs, touch likelihoods
            // and every capped boost, and recomputing raw frequency here
            // discards exactly that typed evidence. Context can therefore
            // lift a candidate a bounded number of places — measured on the
            // real corpus, a raw-frequency base regressed 7 points where
            // this formulation gains.
            word to (-index * RANK_STEP + evidence)
        }
        if (!anyEvidence) return null

        // Stable sort: candidates with equal scores keep the engine's order.
        return scored.sortedByDescending { it.second }.map { it.first }
    }

    private fun term(weight: Double, count: Int, cap: Double): Double =
        (weight * ln(1.0 + count)).coerceAtMost(cap)

    private companion object {
        /** One engine rank, the unit context evidence competes against. */
        const val RANK_STEP = 1.0

        // Interpolation weights and per-term caps (in ranks): the more
        // specific and personal the evidence, the harder it counts and the
        // further it may lift a candidate. Corpus counts are damped first
        // (one personal use ~ PACK_COUNT_SCALE corpus sightings) and capped
        // lower — a population prior may flip near-ties, never vault the
        // typed evidence the incoming order encodes.
        const val WEIGHT_USER_TRIGRAM = 1.2
        const val CAP_USER_TRIGRAM = 2.5
        const val WEIGHT_USER_BIGRAM = 0.8
        const val CAP_USER_BIGRAM = 2.0
        const val WEIGHT_PACK_TRIGRAM = 0.6
        const val CAP_PACK_TRIGRAM = 1.5
        const val WEIGHT_PACK_BIGRAM = 0.45
        const val CAP_PACK_BIGRAM = 1.2
        const val WEIGHT_SEED_BIGRAM = 0.4

        /**
         * Exactly one rank, and that makes the seed term a deliberate no-op
         * on its own — worth stating plainly, because the value reads like a
         * tuning constant and behaves like a switch that is off.
         *
         * To pass the candidate above it, evidence must strictly exceed the
         * rank gap; a term pinned at exactly [RANK_STEP] only ties it, and
         * the stable sort then keeps the engine's order. Seed counts are
         * large enough (median ~78 on the bundled list) that this term sits
         * at its cap essentially always. So bundled bigrams can never flip a
         * pair by themselves — only agreement with another signal can, which
         * is the invariant `NgramRerankerTest` pins.
         *
         * Measured cost of that choice, on cold users who have learned no
         * pairs of their own (`RerankGainTest`, `GlideRerankOracleTest`):
         * typing top1 +0.0000, glide top1 +0.0000. Raising this to anything
         * in 1.2..1.9 buys +6.7pt typing / +3.5pt glide and costs -2.2pt on
         * correctly-typed neighbours of a primed word (`RerankHarmTest`).
         * The trade is roughly 3:1 in favour and the loss is a demotion to
         * slot 2, never a silent replacement — `shouldAutocorrect` reads the
         * raw walk, not this order. It is still a product call, not a tuning
         * one, so the conservative value stands until someone takes it.
         *
         * The weight, not this cap, is the real switch: below ~0.25 the term
         * cannot clear one rank at any cap, and at or above it the term
         * clears one rank at every cap in 1.2..1.9. There is no knee between.
         */
        const val CAP_SEED = 1.0
        const val WEIGHT_RECENCY = 0.3
        const val PACK_COUNT_SCALE = 50

        // Gappy (skip-one) bigram evidence, used only behind an OOV prev:
        // weaker than the direct bigram it stands in for — a rescue signal
        // for a context the direct stores are blind to, never a competitor.
        const val WEIGHT_SKIP_USER = 0.5
        const val CAP_SKIP_USER = 1.2
        const val WEIGHT_SKIP_PACK = 0.3
        const val CAP_SKIP_PACK = 0.8
    }
}
