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
import com.wasimaster.wmkeyboard.core.prediction.Trie
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What early prediction is worth, and what it costs to show.
 *
 * The literature on mid-swipe prediction is unanimous that top-1 accuracy on
 * finished strokes says nothing useful about it, and asks for three things
 * instead: accuracy at fixed points *through* a stroke, how often a wrong guess
 * is put on screen, and how early a right one arrives. This measures all three,
 * by drawing a full stroke for a word and then decoding its first 40%, 60% and
 * 80% as if the finger were still moving.
 *
 * **What a truncated stroke is and is not.** Cutting a finished stroke at 60%
 * of its samples is not the same as watching a real finger at 60% — a real one
 * has not decided how it will finish, and its speed and direction carry
 * information about that which a truncation cannot. It is, though, exactly the
 * geometry the decoder would see, which is the part being measured here. Treat
 * the numbers as the decoder's ceiling rather than as a user study.
 */
class GlideLookAheadEvalTest {

    private companion object {
        const val SEED = 42L
        const val CASES = 400
        const val RANK_DEPTH = 8

        /** Where through the stroke the finger is pretended to be. */
        val POINTS = listOf(0.4, 0.6, 0.8)

        /** Margins a guess must clear to be shown, in nats — the shipped tiers. */
        val MARGINS = listOf(0.0, 1.0, 4.0, 8.0)

        /** Letters below which a word is not worth finishing early. */
        const val MIN_LENGTH = 6
    }

    private fun load(): List<Pair<String, Int>> {
        val candidates = listOf(File("dictionaries-src/en.txt"), File("app/dictionaries-src/en.txt"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("en.txt not found (cwd=${File(".").absolutePath})")
        return file.inputStream().use { DictionaryLoader.loadEntries(it) }
    }

    @Test
    fun earlyPredictionArrivesEarlyAndIsMostlyRight() {
        val entries = load()
        val grid = GlideGrid.of(BuiltInLayouts.QWERTY)
        val keys = GlideKeyMap.of(grid.keyCenters(SwipeCorpus.KEY_WIDTH), SwipeCorpus.KEY_WIDTH)
        val beam = GlideBeam()
        val ws = GlideWorkspace()
        fun sourcesOf(list: List<Pair<String, Int>>): List<FuzzyBeamSearch.WalkSource> {
            val trie = Trie().apply { list.forEach { (w, f) -> insert(w, f) } }
            return trie.walkers().map {
                FuzzyBeamSearch.WalkSource(it, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
            }
        }
        // Two vocabularies, because the claim this feature comes from is about
        // *personalisation* — "because I typed that word a lot, it was
        // offered". Guessing the rest of a word out of a whole dictionary and
        // guessing it out of the couple of thousand words one person writes are
        // not the same problem, and the doc that proposed early prediction says
        // as much: it wanted the mode only ever used inside a sandbox.
        // Each arm is a coherent user: the words they *write* are the words
        // their keyboard knows. Drawing strokes for words outside the arm's own
        // vocabulary would measure nothing but how often the guess cannot
        // possibly be right.
        val arms = listOf(
            "full 17k dictionary" to entries,
            "2k personal lexicon" to entries.take(2_000),
        )


        val out = StringBuilder("early prediction, words of $MIN_LENGTH+ letters, typical strokes\n")
        out.append("  a guess is shown when it beats the best ordinary reading by `margin`\n\n")
        var anyEarly = false
        // Accuracy at the deepest point, per arm, for the assertion below.
        var dictionaryRight = 0.0
        var personalRight = 0.0
        for ((arm, vocabulary) in arms) {
            val sources = sourcesOf(vocabulary)
            // Long words only: finishing "the" early saves nobody anything, and
            // including them would bury the effect under cases the feature is
            // not for.
            val long = vocabulary.filter { it.first.length >= MIN_LENGTH }
            val cases = SwipeCorpus(SEED, grid = grid)
                .generate(long, SwipeCorpus.Noise.TYPICAL, CASES)
            for (fraction in POINTS) {
            out.append("[$arm] ")
            out.append("at ${(fraction * 100).toInt()}% of the stroke:\n")
            out.append("  margin   shown   right   wrong\n")
            for (margin in MARGINS) {
                var shown = 0
                var right = 0
                var wrong = 0
                for (case in cases) {
                    val cut = case.path.take(maxOf(2, (case.path.size * fraction).toInt()))
                    val decoded = beam.decode(
                        cut, keys, SwipeCorpus.KEY_WIDTH, sources, ws, RANK_DEPTH,
                        lookAhead = RANK_DEPTH,
                    )
                    val bestRead = decoded.firstOrNull { it.ahead == 0 }
                    val guess = decoded.firstOrNull { it.ahead > 0 } ?: continue
                    val clears = bestRead == null || guess.score - bestRead.score >= margin
                    // Only a guess that also leads is on screen: one sitting
                    // behind an ordinary reading is an alternate, not a claim.
                    if (!clears || decoded.first().ahead == 0) continue
                    shown++
                    if (guess.word.equals(case.intended, ignoreCase = true)) right++ else wrong++
                }
                if (shown > 0 && fraction <= 0.6) anyEarly = true
                val accuracy = if (shown == 0) 0.0 else right / shown.toDouble()
                if (fraction == POINTS.last() && margin == MARGINS.first()) {
                    if (vocabulary.size == entries.size) {
                        dictionaryRight = accuracy
                    } else {
                        personalRight = accuracy
                    }
                }
                out.append(
                    "  ${String.format(Locale.ROOT, "%6.1f", margin)}  " +
                        "${pct(shown / cases.size.toDouble())}  " +
                        "${pct(accuracy)}  " +
                        "${pct(if (shown == 0) 0.0 else wrong / shown.toDouble())}\n"
                )
            }
            out.append("\n")
            }
        }
        println(out)
        File("build/reports/gestureeval").mkdirs()
        File("build/reports/gestureeval/lookahead.txt").writeText(out.toString())

        // The feature's reason to exist: a word must sometimes be offered
        // before the stroke is most of the way through it. If this stops being
        // true the mechanism is inert and the setting is a lie.
        assertTrue(
            "no word was ever offered before 60% of its stroke; look-ahead is inert",
            anyEarly,
        )
        // And the finding that decides how the setting is described: guessing
        // the rest of a word is markedly better out of one person's vocabulary
        // than out of a dictionary. If that ever stops holding, the advice to
        // pair this with a sandbox policy stops being true and both docs need
        // rewriting.
        assertTrue(
            "the personal lexicon no longer beats the dictionary at guessing ahead " +
                "($personalRight vs $dictionaryRight at 80%)",
            personalRight > dictionaryRight,
        )
    }

    private fun pct(v: Double) = String.format(Locale.ROOT, "%.4f", v)
}
