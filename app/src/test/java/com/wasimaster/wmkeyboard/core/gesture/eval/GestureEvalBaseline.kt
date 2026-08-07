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

    /**
     * 2026-08-07, the `GlideBeam` trie lattice. The numbers it replaced, from
     * the SHARK²-lite `GestureDecoder` it retired, are kept alongside because
     * the shape of the change matters more than the totals: the gain grows with
     * how badly the swipe was drawn, which is the right way round.
     *
     *     overall  .8710 -> .9295      clean    .9640 -> .9720   (+0.8pt)
     *     top3     .9655 -> .9825      light    .9380 -> .9540   (+1.6pt)
     *     mrr      .9196 -> .9565      typical  .8760 -> .9300   (+5.4pt)
     *                                  sloppy   .7060 -> .8620  (+15.6pt)
     */
    const val TOP1 = 0.9295
    const val TOP3 = 0.9825
    const val MRR = 0.9565

    const val CLEAN_TOP1 = 0.9720
    const val LIGHT_TOP1 = 0.9540
    const val TYPICAL_TOP1 = 0.9300
    const val SLOPPY_TOP1 = 0.8620

    /** Run-to-run drift is nil (the corpus is seeded), so this is small. */
    const val TOLERANCE = 0.01
}
