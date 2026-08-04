// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.prediction.eval

import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import com.wasimaster.wmkeyboard.core.prediction.SuggestionEngine
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline quality harness: runs the real engine over the real shipped English
 * dictionary against a reproducible synthetic typo corpus, and asserts the
 * headline metrics never regress past [EvalBaseline]'s floors.
 *
 * Prints a metric table to stdout and writes a diffable JSON artifact to
 * `build/reports/predeval/metrics.json`.
 */
class PredictionEvalTest {

    private companion object {
        const val SEED = 42L
        const val TYPO_CASES = 2000
        const val CLEAN_CASES = 2000
    }

    private fun realEntries(): List<Pair<String, Int>> {
        val candidates = listOf(
            File("dictionaries-src/en.txt"),
            File("app/dictionaries-src/en.txt"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("en.txt not found (cwd=${File(".").absolutePath})")
        return file.inputStream().use { DictionaryLoader.loadEntries(it) }
    }

    private fun engine(entries: List<Pair<String, Int>>): SuggestionEngine =
        SuggestionEngine(PackedTrie.of(entries), BengaliPhoneticIndex(emptyList()), UserLexicon(null))

    @Test
    fun qualityDoesNotRegress() {
        val entries = realEntries()
        val engine = engine(entries)
        val corpus = TypoCorpus(SEED)
        val cases = corpus.generate(entries, TYPO_CASES)
        val clean = corpus.cleanWords(entries, CLEAN_CASES)

        val ranks = ArrayList<Int?>(cases.size)
        var fired = 0
        var firedCorrect = 0
        for (case in cases) {
            val suggestions = engine.suggest(case.typed, previousWord = case.previous)
            val rank = suggestions.indexOfFirst { it.equals(case.intended, ignoreCase = true) }
            ranks.add(if (rank >= 0) rank + 1 else null)
            val correction = engine.shouldAutocorrect(case.typed)
            if (correction != null) {
                fired++
                if (correction.equals(case.intended, ignoreCase = true)) firedCorrect++
            }
        }
        var cleanFired = 0
        for (word in clean) {
            if (engine.shouldAutocorrect(word) != null) cleanFired++
        }

        val suggest = EvalMetrics.suggest(ranks)
        val autocorrect = EvalMetrics.autocorrect(
            fired = fired,
            firedCorrect = firedCorrect,
            typoCount = cases.size,
            cleanFired = cleanFired,
            cleanCount = clean.size,
        )

        report(suggest, autocorrect)

        assertTrue(
            "top1 ${suggest.top1} regressed below ${EvalBaseline.TOP1 - EvalBaseline.TOP1_TOLERANCE}",
            suggest.top1 >= EvalBaseline.TOP1 - EvalBaseline.TOP1_TOLERANCE,
        )
        assertTrue(
            "top3 ${suggest.top3} regressed below ${EvalBaseline.TOP3 - EvalBaseline.TOP3_TOLERANCE}",
            suggest.top3 >= EvalBaseline.TOP3 - EvalBaseline.TOP3_TOLERANCE,
        )
        assertTrue(
            "false-correction rate ${autocorrect.falseCorrectionRate} rose above " +
                "${EvalBaseline.FALSE_CORRECTION_RATE + EvalBaseline.FCR_TOLERANCE}",
            autocorrect.falseCorrectionRate <= EvalBaseline.FALSE_CORRECTION_RATE + EvalBaseline.FCR_TOLERANCE,
        )
    }

    private fun report(suggest: SuggestMetrics, autocorrect: AutocorrectMetrics) {
        fun pct(v: Double) = String.format(Locale.ROOT, "%.4f", v)
        println("=== prediction eval (seed=$SEED, typos=${suggest.n}, clean=$CLEAN_CASES) ===")
        println(
            "suggest   top1=${pct(suggest.top1)}  top3=${pct(suggest.top3)}  mrr=${pct(suggest.mrr)}"
        )
        println(
            "autocorrect precision=${pct(autocorrect.precision)}  recall=${pct(autocorrect.recall)}" +
                "  falseCorrectionRate=${pct(autocorrect.falseCorrectionRate)}"
        )
        val json = buildString {
            append("{\n")
            append("  \"seed\": $SEED,\n")
            append("  \"typoCases\": ${suggest.n},\n")
            append("  \"cleanCases\": $CLEAN_CASES,\n")
            append("  \"top1\": ${pct(suggest.top1)},\n")
            append("  \"top3\": ${pct(suggest.top3)},\n")
            append("  \"mrr\": ${pct(suggest.mrr)},\n")
            append("  \"autocorrectPrecision\": ${pct(autocorrect.precision)},\n")
            append("  \"autocorrectRecall\": ${pct(autocorrect.recall)},\n")
            append("  \"falseCorrectionRate\": ${pct(autocorrect.falseCorrectionRate)}\n")
            append("}\n")
        }
        val out = File("build/reports/predeval/metrics.json")
        out.parentFile?.mkdirs()
        out.writeText(json)
        println("metrics written to ${out.path}")
    }
}
