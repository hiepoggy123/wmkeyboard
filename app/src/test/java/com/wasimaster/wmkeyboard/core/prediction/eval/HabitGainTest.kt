// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.prediction.eval

import com.wasimaster.wmkeyboard.core.prediction.CorrectionMemory
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.EditHabits
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import com.wasimaster.wmkeyboard.core.prediction.SuggestionEngine
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import java.io.File
import java.util.Locale
import java.util.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Does a learned slip habit buy anything, and does it cost anything?
 *
 * A corpus of real words with one far substitution the population does not
 * make (`i` typed for `e`: not neighbours on QWERTY) is run cold, with no
 * habits, and warm, after the same slip has been taught enough times to move
 * the walk's prices. Warm has to beat cold on the words that carry the slip.
 * Then the *standard* typo corpus is run warm, and must not lose ground: a
 * habit only ever admits more candidates, and the confidence gate keeps the
 * false-correction rate where it was.
 */
class HabitGainTest {

    private companion object {
        const val SEED = 7L
        const val CASES = 1500
        const val LAYOUT = "qwerty"
        const val NO_HARM_TOLERANCE = 0.01
    }

    private fun realEntries(): List<Pair<String, Int>> {
        val candidates = listOf(File("dictionaries-src/en.txt"), File("app/dictionaries-src/en.txt"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("en.txt not found (cwd=${File(".").absolutePath})")
        return file.inputStream().use { DictionaryLoader.loadEntries(it) }
    }

    private fun engine(entries: List<Pair<String, Int>>): SuggestionEngine =
        SuggestionEngine(PackedTrie.of(entries), BengaliPhoneticIndex(emptyList()), UserLexicon(null))

    /** Habits of a hand that types `i` for `e`, taught past the warm-up. */
    private fun warmHabits(): EditHabits {
        val memory = CorrectionMemory(null)
        repeat(EditHabits.WARMUP * 2) {
            memory.teach("thim", "them", LAYOUT, CorrectionMemory.Kind.PAIR_AND_HABITS)
        }
        return memory.habitsFor(LAYOUT)
    }

    /** Real words carrying the slip: the first `e` typed as `i`. */
    private fun slipCorpus(entries: List<Pair<String, Int>>): List<Pair<String, String>> {
        val random = Random(SEED)
        val pool = entries.filter { (w, _) ->
            w.length in 4..10 && w.all { it in 'a'..'z' } && 'e' in w && 'i' !in w
        }
        val out = ArrayList<Pair<String, String>>(CASES)
        while (out.size < CASES) {
            val word = pool[random.nextInt(pool.size)].first
            out.add(word to word.replaceFirst('e', 'i'))
        }
        return out
    }

    private fun recall(engine: SuggestionEngine, corpus: List<Pair<String, String>>): Pair<Double, Double> {
        var top1 = 0
        var corrected = 0
        for ((intended, typed) in corpus) {
            if (engine.suggest(typed, previousWord = null).firstOrNull() == intended) top1++
            if (engine.shouldAutocorrect(typed) == intended) corrected++
        }
        return top1 / corpus.size.toDouble() to corrected / corpus.size.toDouble()
    }

    @Test
    fun aTaughtSlipLiftsTheWordsThatCarryIt() {
        val entries = realEntries()
        val engine = engine(entries)
        val corpus = slipCorpus(entries)
        val (coldTop1, coldRecall) = recall(engine, corpus)
        engine.editHabits = warmHabits()
        val (warmTop1, warmRecall) = recall(engine, corpus)
        println(
            "=== habit gain (i for e) === cold top1=${fmt(coldTop1)} recall=${fmt(coldRecall)} | " +
                "warm top1=${fmt(warmTop1)} recall=${fmt(warmRecall)}",
        )
        assertTrue("warm top1 $warmTop1 <= cold $coldTop1", warmTop1 > coldTop1)
        assertTrue("warm recall $warmRecall < cold $coldRecall", warmRecall >= coldRecall)
    }

    @Test
    fun aTaughtSlipDoesNoHarmToEverybodyElsesTypos() {
        val entries = realEntries()
        val corpus = TypoCorpus(42L)
        val cases = corpus.generate(entries, CASES)
        val clean = corpus.cleanWords(entries, CASES)
        fun run(engine: SuggestionEngine): Triple<Double, Double, Double> {
            var top1 = 0
            var fired = 0
            var firedCorrect = 0
            for (case in cases) {
                if (engine.suggest(case.typed, previousWord = case.previous).firstOrNull()
                        .equals(case.intended, ignoreCase = true)
                ) {
                    top1++
                }
                val correction = engine.shouldAutocorrect(case.typed)
                if (correction != null) {
                    fired++
                    if (correction.equals(case.intended, ignoreCase = true)) firedCorrect++
                }
            }
            val cleanFired = clean.count { engine.shouldAutocorrect(it) != null }
            return Triple(
                top1 / cases.size.toDouble(),
                if (fired == 0) 1.0 else firedCorrect / fired.toDouble(),
                cleanFired / clean.size.toDouble(),
            )
        }
        val cold = run(engine(entries))
        val warm = run(engine(entries).apply { editHabits = warmHabits() })
        println(
            "=== habit no-harm === cold top1=${fmt(cold.first)} precision=${fmt(cold.second)} fcr=${fmt(cold.third)}" +
                " | warm top1=${fmt(warm.first)} precision=${fmt(warm.second)} fcr=${fmt(warm.third)}",
        )
        assertTrue("warm top1 ${warm.first} fell under cold ${cold.first}", warm.first >= cold.first - NO_HARM_TOLERANCE)
        assertTrue("warm precision ${warm.second} fell under cold ${cold.second}", warm.second >= cold.second - NO_HARM_TOLERANCE)
        // The in-dictionary gate is untouched by habits, so this is exact.
        assertTrue("false corrections appeared: ${warm.third}", warm.third <= cold.third)
    }

    private fun fmt(v: Double) = String.format(Locale.ROOT, "%.4f", v)
}
