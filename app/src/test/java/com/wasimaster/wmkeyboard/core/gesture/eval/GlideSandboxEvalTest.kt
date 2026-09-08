// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.gesture.eval

import com.wasimaster.wmkeyboard.core.gesture.GlideBeam
import com.wasimaster.wmkeyboard.core.gesture.GlideKeyMap
import com.wasimaster.wmkeyboard.core.gesture.GlideWorkspace
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.FuzzyBeamSearch
import com.wasimaster.wmkeyboard.core.prediction.GlideSandboxLadder
import com.wasimaster.wmkeyboard.core.prediction.Trie
import java.io.File
import java.util.Locale
import kotlin.math.ln
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the sandbox policies are worth, and where
 * [GlideSandboxLadder.SANDBOX_MARGIN] belongs.
 *
 * The claim the policies rest on is that a swipe's accuracy is set by the size
 * of the vocabulary it chooses from, so answering out of the few thousand
 * words this user actually writes beats answering out of a dictionary that
 * also holds `van`, `nor` and `wold`. That is a measurable claim and this
 * measures it, on the same corpus and the same shipped word list the rest of
 * the gesture harness uses.
 *
 * **The synthetic user.** A personal lexicon is modelled as the commonest
 * [LEXICON_WORDS] words of the shipped list, carried at a use count derived
 * from their frequency — a mature user has written `the` far more often than
 * `quiet` — and weighted the way `SuggestionEngine` weights the real one. The
 * corpus then draws from the *full* list, so a realistic tail of strokes is for
 * words the sandbox has never seen. Both halves matter: the in-lexicon strokes
 * are where the policy is supposed to win, and the out-of-lexicon ones are the
 * whole of what it costs.
 *
 * **What it is not.** The lexicon here is a frequency prefix of a dictionary,
 * not a record of anyone's writing, so it is smoother and more predictable than
 * a real one — no names, no jargon, no misspellings that settled. Treat the
 * in-lexicon gain as an upper bound and the out-of-lexicon loss as a lower one.
 * The threshold this picks is a starting point for device QA, not a
 * measurement of a person.
 */
class GlideSandboxEvalTest {

    private companion object {
        const val SEED = 42L
        const val CASES_PER_LEVEL = 500
        const val RANK_DEPTH = 8

        /**
         * Words in the synthetic personal lexicon. The doc's own premise is
         * that the top 1,000 English words carry ~89% of writing and that most
         * people write with a few thousand; this sits in that range and is
         * deliberately not tuned to flatter the result.
         */
        const val LEXICON_WORDS = 2_000

        /** `SuggestionEngine.USER_WORD_WEIGHT`, which is private to it. */
        val LOG_USER_WEIGHT = ln(500.0)

        /** Noise levels worth reporting: a clean stroke was never the hard case. */
        val LEVELS = listOf(SwipeCorpus.Noise.TYPICAL, SwipeCorpus.Noise.SLOPPY)

        /** Thresholds swept for the fallback gate, in the decoder's cost units. */
        val SWEEP = listOf(0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 40.0, 60.0, Double.MAX_VALUE)

        /** Margins swept for the relative gate, in the same cost units. */
        val MARGINS = listOf(-4.0, -2.0, -1.0, 0.0, 1.0, 2.0, 4.0, 8.0, 16.0, Double.MAX_VALUE)
    }

    private fun load(name: String): List<Pair<String, Int>> {
        val candidates = listOf(File("dictionaries-src/$name"), File("app/dictionaries-src/$name"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("$name not found (cwd=${File(".").absolutePath})")
        return file.inputStream().use { DictionaryLoader.loadEntries(it) }
    }

    /**
     * One stroke, decoded both ways: whether the sandbox alone got it, what
     * that reading cost, and whether the full decode got it. The last is what
     * a fallback would have produced, so the sweep can price every threshold
     * without re-decoding.
     */
    private class Outcome(
        val sandboxHit: Boolean,
        val cost: Double,
        val fullHit: Boolean,
        val fullCost: Double,
        val inLexicon: Boolean,
    )

    @Test
    fun sandboxPoliciesAreWorthTheirCost() {
        val english = load("en.txt")
        val grid = GlideGrid.of(BuiltInLayouts.QWERTY)
        val keys = GlideKeyMap.of(grid.keyCenters(SwipeCorpus.KEY_WIDTH), SwipeCorpus.KEY_WIDTH)
        val beam = GlideBeam()
        val workspace = GlideWorkspace()

        val lexiconWords = english.take(LEXICON_WORDS)
        val known = lexiconWords.mapTo(HashSet()) { it.first.lowercase(Locale.ROOT) }

        val dictionary = Trie().apply { english.forEach { (w, f) -> insert(w, f) } }
        // Personal counts, not corpus frequencies: the lexicon records how
        // often this user wrote the word, which is a much flatter number.
        val lexicon = Trie().apply {
            lexiconWords.forEach { (w, f) -> insert(w, 1 + f / 500) }
        }

        val full = dictionary.walkers().map {
            FuzzyBeamSearch.WalkSource(it, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        } + lexicon.walkers().map {
            FuzzyBeamSearch.WalkSource(it, LOG_USER_WEIGHT, FuzzyBeamSearch.Tier.USER)
        }
        val sandboxOnly = lexicon.walkers().map {
            FuzzyBeamSearch.WalkSource(it, LOG_USER_WEIGHT, FuzzyBeamSearch.Tier.USER)
        }

        fun decode(
            path: List<com.wasimaster.wmkeyboard.core.gesture.GesturePoint>,
            sources: List<FuzzyBeamSearch.WalkSource>,
        ): List<GlideBeam.Candidate> =
            beam.decode(path, keys, SwipeCorpus.KEY_WIDTH, sources, workspace, RANK_DEPTH)

        val report = StringBuilder()
        report.append("lexicon=$LEXICON_WORDS words of ${english.size}\n")

        // Collected across levels so the threshold is picked once, on
        // everything, rather than per level where it would overfit.
        val roc = ArrayList<Outcome>()
        var offTop1 = 0
        var onlyTop1 = 0
        var total = 0
        var inLexicon = 0
        var offInLex = 0
        var onlyInLex = 0
        var offOutLex = 0
        var onlyOutLex = 0

        for (level in LEVELS) {
            val corpus = SwipeCorpus(SEED, grid = grid)
            for (case in corpus.generate(english, level, CASES_PER_LEVEL)) {
                val want = case.intended.lowercase(Locale.ROOT)
                val fullTop = decode(case.path, full).firstOrNull()
                val offHit = fullTop?.word?.equals(want, ignoreCase = true) == true
                val sandbox = decode(case.path, sandboxOnly)
                val onlyHit = sandbox.firstOrNull()?.word?.equals(want, ignoreCase = true) == true

                total++
                if (offHit) offTop1++
                if (onlyHit) onlyTop1++
                if (want in known) {
                    inLexicon++
                    if (offHit) offInLex++
                    if (onlyHit) onlyInLex++
                } else {
                    if (offHit) offOutLex++
                    if (onlyHit) onlyOutLex++
                }
                roc.add(
                    Outcome(
                        sandboxHit = onlyHit,
                        // An empty sandbox decode has no cost to judge and must
                        // always fall back, which an infinite cost expresses.
                        cost = sandbox.firstOrNull()?.shapeCost ?: Double.MAX_VALUE,
                        fullHit = offHit,
                        fullCost = fullTop?.shapeCost ?: Double.MAX_VALUE,
                        inLexicon = want in known,
                    ),
                )
            }
        }

        report.append(
            "in-lexicon strokes ${pct(inLexicon / total.toDouble())} of $total\n\n"
        )
        report.append("policy            top1     in-lex   out-lex  fallback\n")
        line(report, "OFF", offTop1, total, offInLex, inLexicon, offOutLex, total - inLexicon, 0.0)
        line(
            report, "LEARNED_ONLY", onlyTop1, total, onlyInLex, inLexicon,
            onlyOutLex, total - inLexicon, 0.0,
        )

        // An absolute gate on the sandbox's own cost, for the record. It does
        // not work and the table is here to show why: a sandbox that does not
        // hold the intended word still returns some *other* word that fits the
        // stroke well, so a low cost means "this reading is tidy", never "this
        // reading is right". Every threshold above zero trades OFF's accuracy
        // for the sandbox's blind spot, and the best row is the one that always
        // falls back — i.e. OFF.
        report.append("\nabsolute gate on sandbox cost (does not separate):\n")
        report.append("  cost      top1     fallback\n")
        var bestAbsolute = 0.0
        for (threshold in SWEEP) {
            var hits = 0
            var fell = 0
            for (o in roc) {
                if (o.cost <= threshold) {
                    if (o.sandboxHit) hits++
                } else {
                    fell++
                    if (o.fullHit) hits++
                }
            }
            val top1 = hits / total.toDouble()
            if (top1 > bestAbsolute) bestAbsolute = top1
            report.append(
                "  ${label(threshold)}  ${pct(top1)}  ${pct(fell / total.toDouble())}\n"
            )
        }

        // The gate that does work: both decodes run, and the sandbox's word is
        // taken only when it explains the stroke about as well as the best word
        // in the whole lexicon does. Cost is pure geometry and is the one thing
        // comparable across two decodes whose language models are not — the
        // user tier carries ln(500) that the dictionary tier does not.
        report.append("\nrelative gate (PREFER_LEARNED): sandbox wins when cost <= full + margin\n")
        report.append("  margin    top1     in-lex   out-lex  taken\n")
        var best = 0.0
        var bestTop1 = -1.0
        for (margin in MARGINS) {
            var hits = 0
            var taken = 0
            var inHits = 0
            var outHits = 0
            for (o in roc) {
                val useSandbox = o.cost <= o.fullCost + margin
                if (useSandbox) taken++
                val hit = if (useSandbox) o.sandboxHit else o.fullHit
                if (hit) hits++
                if (o.inLexicon) {
                    if (hit) inHits++
                } else if (hit) {
                    outHits++
                }
            }
            val top1 = hits / total.toDouble()
            report.append(
                "  ${label(margin)}  ${pct(top1)}  " +
                    "${pct(inHits / inLexicon.toDouble())}  " +
                    "${pct(outHits / (total - inLexicon).toDouble())}  " +
                    "${pct(taken / total.toDouble())}\n"
            )
            if (top1 > bestTop1) {
                bestTop1 = top1
                best = margin
            }
        }
        report.append("\nbest margin ${label(best)} at top1 ${pct(bestTop1)}\n")
        report.append("shipped SANDBOX_MARGIN ${GlideSandboxLadder.SANDBOX_MARGIN}\n")
        println(report)
        File("build/reports/gestureeval").mkdirs()
        File("build/reports/gestureeval/sandbox.txt").writeText(report.toString())

        // The premise the whole feature rests on: answering out of the sandbox
        // reads the strokes the sandbox knows *better* than the full lexicon
        // does. If this ever stops holding, `LEARNED_ONLY` is all cost and no
        // benefit and the setting should come out rather than be re-tuned
        // around — so it is asserted rather than merely printed.
        assertTrue(
            "sandbox did not beat the full lexicon on in-lexicon strokes: " +
                "${onlyInLex / inLexicon.toDouble()} vs ${offInLex / inLexicon.toDouble()}",
            onlyInLex > offInLex,
        )

        // And the negative result, asserted for the same reason: no gate on the
        // sandbox's own shape cost separates "holds the word" from "holds a
        // word that fits". If some future change makes one work, this fails and
        // whoever made it should read `PREFER_LEARNED`'s doc and rewrite it,
        // because that rung would then be worth something on its own.
        assertTrue(
            "an absolute cost gate now beats always falling back ($bestAbsolute > $offTop1); " +
                "PREFER_LEARNED may be worth more than instrumentation now",
            bestAbsolute <= offTop1 / total.toDouble() + 1e-9,
        )
    }

    private fun line(
        out: StringBuilder,
        name: String,
        hits: Int,
        total: Int,
        inHits: Int,
        inTotal: Int,
        outHits: Int,
        outTotal: Int,
        fallback: Double,
    ) {
        out.append(
            name.padEnd(18) +
                pct(hits / total.toDouble()) + "  " +
                pct(if (inTotal == 0) 0.0 else inHits / inTotal.toDouble()) + "  " +
                pct(if (outTotal == 0) 0.0 else outHits / outTotal.toDouble()) + "  " +
                pct(fallback) + "\n"
        )
    }

    private fun label(v: Double) =
        if (v == Double.MAX_VALUE) "  always" else String.format(Locale.ROOT, "%8.1f", v)

    private fun pct(v: Double) = String.format(Locale.ROOT, "%.4f", v)
}
