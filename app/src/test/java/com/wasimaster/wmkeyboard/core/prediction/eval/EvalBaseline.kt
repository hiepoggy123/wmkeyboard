package com.wasimaster.wmkeyboard.core.prediction.eval

/**
 * Regression floors for [PredictionEvalTest], captured against the engine at a
 * known-good commit. These are floors/ceilings with tolerance, not exact
 * goldens: deliberate scoring tweaks may move the numbers, and the assertions
 * only catch regressions bigger than the tolerance.
 *
 * Baseline captured 2026-08-05 (seed 42, 2000 typo + 2000 clean cases,
 * app/dictionaries-src/en.txt, Norvig edits-1 engine): top1 0.8120,
 * top3 0.9330, mrr 0.8718, precision 0.9753, recall 0.7290, fcr 0.0000.
 * fcr is 0 by construction while the in-dictionary gate holds — any nonzero
 * value means the gate broke, which is why the ceiling stays this tight.
 */
object EvalBaseline {
    const val TOP1 = 0.8120
    const val TOP3 = 0.9330
    const val MRR = 0.8718
    const val AUTOCORRECT_PRECISION = 0.9753
    const val AUTOCORRECT_RECALL = 0.7290
    const val FALSE_CORRECTION_RATE = 0.0

    const val TOP1_TOLERANCE = 0.02
    const val TOP3_TOLERANCE = 0.02
    const val FCR_TOLERANCE = 0.005
}
