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
 * Per-*language* floors sit alongside both for the same kind of reason. A
 * decoder tuned on English can pay for its gains in a script nobody was
 * measuring, and until 2026-08-07 nobody was measuring one: glide only ran in
 * English, so the harness only knew English.
 *
 * Measured against the corpus in [SwipeCorpus] at seed 42 over the shipped word
 * lists. Both are inputs to the number: changing either invalidates it.
 */
object GestureEvalBaseline {

    /** One language's floors. Every field is asserted. */
    class Floors(
        val top1: Double,
        val top3: Double,
        val mrr: Double,
        val clean: Double,
        val light: Double,
        val typical: Double,
        val sloppy: Double,
    )

    /**
     * English on QWERTY, over the bundled 17k list.
     *
     * 2026-08-07, the `GlideBeam` trie lattice replacing the SHARK²-lite
     * `GestureDecoder`. Against the harness as it stood that day, on an
     * idealised key grid:
     *
     *     overall  .8710 -> .9295      clean    .9640 -> .9720   (+0.8pt)
     *     top3     .9655 -> .9825      light    .9380 -> .9540   (+1.6pt)
     *     mrr      .9196 -> .9565      typical  .8760 -> .9300   (+5.4pt)
     *                                  sloppy   .7060 -> .8620  (+15.6pt)
     *
     * Two things moved those numbers afterwards. The harness stopped carrying a
     * hand-written QWERTY table and started deriving geometry from the shipped
     * [com.wasimaster.wmkeyboard.core.layout.LayoutSpec], staggering the bottom
     * row by a shift key's width rather than by a guess — a different and real
     * keyboard, worth .9295 -> .9265 with the decoder untouched. Then the shape
     * channel and dwell-gated doubling landed: .9265 -> .9460, and clean strokes
     * from .9620 to .9860.
     */
    val ENGLISH = Floors(
        top1 = 0.9460,
        top3 = 0.9910,
        mrr = 0.9678,
        clean = 0.9860,
        light = 0.9700,
        typical = 0.9300,
        sloppy = 0.8980,
    )

    /**
     * Bengali on Probhat, over the bundled 20k list — the case the whole
     * multi-character-per-key design exists for, and the one that would catch it
     * breaking.
     *
     * These sit ~19pt below English and are expected to. Probhat asks the
     * decoder a harder question at every step: a key carries a consonant and its
     * aspirated twin, so ক and খ are the *same stroke* and nothing but the
     * language model can separate them, and Bengali's vowel signs and hasanta
     * put more characters into an average word than English needs for the same
     * meaning. The [CYRILLIC] control is what says this gap is Bengali's
     * ambiguity rather than a defect in the decoder — it scores above English on
     * a grid with more keys and no per-language machinery at all.
     *
     * The lever that would move these most is not the decoder. It is frequency:
     * these numbers ride on the bundled 20k list, which carries real counts,
     * while the 451k downloadable Bengali list ships every word at frequency 1.
     * On that list a stroke that fits ক and খ equally has nothing left to
     * consult, and top-1 would fall a long way below what is measured here.
     */
    val BENGALI = Floors(
        top1 = 0.7810,
        top3 = 0.8875,
        mrr = 0.8363,
        clean = 0.8080,
        light = 0.8340,
        typical = 0.7560,
        sloppy = 0.7260,
    )

    /**
     * The no-special-machinery control: a twelve-column non-Latin grid with a
     * synthetic lexicon (see `GestureEvalTest.cyrillicControl`). Nothing about
     * it is Bengali or English specific, so a regression that shows up only here
     * is a regression in the decoder itself rather than in either language's
     * arrangements.
     */
    val CYRILLIC = Floors(
        top1 = 0.9545,
        top3 = 0.9910,
        mrr = 0.9728,
        clean = 0.9820,
        light = 0.9800,
        typical = 0.9520,
        sloppy = 0.9040,
    )

    /**
     * Avro: a QWERTY grid, a romanized stroke, a Bengali answer.
     *
     * Two different things have to go right: the stroke has to decode to the
     * right romanized spelling, and that spelling has to stand for the right
     * Bengali. Only the first is the decoder's problem.
     *
     * It nonetheless scores ~17pt above Bengali on a fixed layout, and the
     * reason is worth keeping in mind before anyone tries to close that gap the
     * other way round: a romanization is written in an alphabet where no two
     * letters share a key, so the shapes are unambiguous in a way Probhat's ক/খ
     * never are. Avro is also the layout most Bengali typists actually use.
     */
    val AVRO = Floors(
        top1 = 0.9430,
        top3 = 0.9930,
        mrr = 0.9674,
        clean = 0.9820,
        light = 0.9640,
        typical = 0.9420,
        sloppy = 0.8840,
    )

    /** Run-to-run drift is nil (the corpus is seeded), so this is small. */
    const val TOLERANCE = 0.01
}
