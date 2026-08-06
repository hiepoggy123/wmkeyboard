package com.wasimaster.wmkeyboard.core.gesture.eval

/**
 * Committed glide-decoder quality floors. A change that drops any of these past
 * its tolerance fails the build; a change that raises them should raise the
 * numbers here in the same commit, so the file reads as the decoder's history.
 *
 * Per-noise floors sit alongside the overall ones on purpose. An overall
 * average hides the trade every shape decoder is tempted to make — buying
 * accuracy on tidy swipes by giving it up on messy ones, which is backwards,
 * because a tidy swipe was never the hard case.
 *
 * Measured against the corpus in [SwipeCorpus] at seed 42 over the shipped
 * `en.txt`. Both are inputs to the number: changing either invalidates it.
 */
object GestureEvalBaseline {

    /** Baseline 2026-08-06: the SHARK²-lite `GestureDecoder`, before any rework. */
    const val TOP1 = 0.8710
    const val TOP3 = 0.9655
    const val MRR = 0.9196

    const val CLEAN_TOP1 = 0.9640
    const val LIGHT_TOP1 = 0.9380
    const val TYPICAL_TOP1 = 0.8760
    const val SLOPPY_TOP1 = 0.7060

    /** Run-to-run drift is nil (the corpus is seeded), so this is small. */
    const val TOLERANCE = 0.01
}
