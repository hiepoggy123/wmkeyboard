package com.wasimaster.wmkeyboard.core.prediction.eval

/** Suggestion-strip quality over the typo corpus. */
data class SuggestMetrics(
    val top1: Double,
    val top3: Double,
    val mrr: Double,
    val n: Int,
)

/**
 * Autocorrect quality. [falseCorrectionRate] is the trust metric: how often a
 * correctly-typed dictionary word gets "corrected" anyway — the failure users
 * never forgive.
 */
data class AutocorrectMetrics(
    val precision: Double,
    val recall: Double,
    val falseCorrectionRate: Double,
    val n: Int,
)

object EvalMetrics {

    fun suggest(ranks: List<Int?>): SuggestMetrics {
        val n = ranks.size
        require(n > 0) { "no suggest samples" }
        val top1 = ranks.count { it == 1 } / n.toDouble()
        val top3 = ranks.count { it != null && it <= 3 } / n.toDouble()
        val mrr = ranks.sumOf { rank -> if (rank == null) 0.0 else 1.0 / rank } / n
        return SuggestMetrics(top1 = top1, top3 = top3, mrr = mrr, n = n)
    }

    fun autocorrect(
        fired: Int,
        firedCorrect: Int,
        typoCount: Int,
        cleanFired: Int,
        cleanCount: Int,
    ): AutocorrectMetrics = AutocorrectMetrics(
        precision = if (fired == 0) 1.0 else firedCorrect / fired.toDouble(),
        recall = if (typoCount == 0) 0.0 else firedCorrect / typoCount.toDouble(),
        falseCorrectionRate = if (cleanCount == 0) 0.0 else cleanFired / cleanCount.toDouble(),
        n = typoCount,
    )
}
