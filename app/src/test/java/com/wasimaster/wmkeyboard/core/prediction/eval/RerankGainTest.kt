// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.prediction.eval

import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.NgramReranker
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import com.wasimaster.wmkeyboard.core.prediction.SeedBigrams
import com.wasimaster.wmkeyboard.core.prediction.SuggestionEngine
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import java.io.File
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Measures what the n-gram reranker actually buys.
 *
 * The standard corpus cannot show it: its previous-word context is random,
 * so by construction the reranker sees no evidence and returns null on every
 * case — asserted below as the no-regression guarantee for users it can't
 * help. The real measurement uses contextful cases built from the bundled
 * seed pairs (typo the follower, keep its real predecessor as context), in
 * two user states: cold (seed counts only) and warm (the pair also learned
 * in the personal lexicon, i.e. the user has typed this phrase before).
 */
class RerankGainTest {

    private fun realEntries(): List<Pair<String, Int>> {
        val candidates = listOf(
            File("dictionaries-src/en.txt"),
            File("app/dictionaries-src/en.txt"),
        )
        return candidates.firstOrNull { it.exists() }?.inputStream()
            ?.use { DictionaryLoader.loadEntries(it) }
            ?: error("en.txt not found")
    }

    private fun realSeeds(): SeedBigrams {
        val candidates = listOf(
            File("src/main/assets/dictionaries/en_bigrams.txt"),
            File("app/src/main/assets/dictionaries/en_bigrams.txt"),
        )
        return candidates.firstOrNull { it.exists() }?.inputStream()
            ?.use { SeedBigrams.load(it) }
            ?: error("en_bigrams.txt not found")
    }

    private class Setup(entries: List<Pair<String, Int>>, seeds: SeedBigrams) {
        val lexicon = UserLexicon(null)
        val dictionary = PackedTrie.of(entries)
        val engine = SuggestionEngine(
            dictionary, BengaliPhoneticIndex(emptyList()), lexicon,
            seedBigrams = seeds,
        ).also { engine ->
            engine.reranker = NgramReranker(
                lexicon, seeds, dictionaryFrequency = { dictionary.frequencyOf(it) },
            )
        }
    }

    @Test
    fun randomContextCorpusIsUntouched() {
        val entries = realEntries()
        val setup = Setup(entries, realSeeds())
        val cases = TypoCorpus(42L).generate(entries, 1000)
        var identical = 0
        for (case in cases) {
            val base = setup.engine.suggest(case.typed, previousWord = case.previous)
            val reranked = setup.engine.suggest(
                case.typed, previousWord = case.previous, allowRerank = true,
            )
            if (base == reranked) identical++
        }
        // Random context means near-zero evidence; the strip must be
        // (essentially) untouched. A handful of accidental seed-pair
        // coincidences are allowed — that is the reranker doing its job.
        println("rerank random-context: ${cases.size - identical}/${cases.size} lists changed")
        assertTrue("random context changed too much", cases.size - identical < cases.size / 20)
    }

    @Test
    fun contextfulGainIsMeasuredColdAndWarm() {
        val entries = realEntries()
        val seeds = realSeeds()
        val dictWords = entries.toMap()
        val corpus = TypoCorpus(7L)
        val random = Random(7L)

        // Contextful cases from the real seed pairs: the intended word is a
        // known follower of its real predecessor.
        data class Case(val previous: String, val intended: String, val typed: String)
        val pairs = ArrayList<Pair<String, String>>()
        for ((prev, next) in seedPairs(seeds)) {
            if (next.length >= 3 && next.all { it in 'a'..'z' } && dictWords.containsKey(next)) {
                pairs.add(prev to next)
            }
        }
        val cases = ArrayList<Case>()
        while (cases.size < 600) {
            val (prev, next) = pairs[random.nextInt(pairs.size)]
            val typed = corpus.corrupt(next) ?: continue
            cases.add(Case(prev, next, typed))
        }

        fun top1(setup: Setup, rerank: Boolean): Double {
            var hits = 0
            for (case in cases) {
                val out = setup.engine.suggest(
                    case.typed, previousWord = case.previous, allowRerank = rerank,
                )
                if (out.firstOrNull()?.equals(case.intended, ignoreCase = true) == true) hits++
            }
            return hits / cases.size.toDouble()
        }

        // Cold user: only the bundled seed counts speak for context.
        val cold = Setup(entries, seeds)
        val coldBase = top1(cold, rerank = false)
        val coldRerank = top1(cold, rerank = true)

        // Warm user: the phrase has been typed before (learned bigrams too).
        val warm = Setup(entries, seeds)
        for ((prev, next) in pairs) repeat(3) { warm.lexicon.learnBigram(prev, next) }
        val warmBase = top1(warm, rerank = false)
        val warmRerank = top1(warm, rerank = true)

        fun pct(v: Double) = String.format(Locale.ROOT, "%.4f", v)
        println(
            "rerank contextful gain: cold ${pct(coldBase)} -> ${pct(coldRerank)} " +
                "(+${pct(coldRerank - coldBase)}), warm ${pct(warmBase)} -> ${pct(warmRerank)} " +
                "(+${pct(warmRerank - warmBase)}), n=${cases.size}"
        )
        assertTrue("cold rerank regressed", coldRerank >= coldBase)
        assertTrue("warm rerank regressed", warmRerank >= warmBase)
        assertTrue("no measurable warm gain", warmRerank > warmBase)
        // No cold-gain gate on purpose. Every case here IS a bundled seed
        // pair, so the seed term has evidence on all of them and still moves
        // nothing: it is capped at exactly one rank, which cannot break a
        // one-rank tie (see NgramReranker.CAP_SEED). The cold number is
        // therefore expected to print +0.0000 today. Assert it only if that
        // cap is ever raised — asserting it now would gate the suite on a
        // product decision nobody has taken.
    }

    /** Every (prev, next) the seed list knows. */
    private fun seedPairs(seeds: SeedBigrams): List<Pair<String, String>> {
        val candidates = listOf(
            File("src/main/assets/dictionaries/en_bigrams.txt"),
            File("app/src/main/assets/dictionaries/en_bigrams.txt"),
        )
        val file = candidates.first { it.exists() }
        val out = ArrayList<Pair<String, String>>()
        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size >= 2) out.add(parts[0].lowercase() to parts[1].lowercase())
        }
        // Sanity: the parsed pairs agree with the loaded SeedBigrams.
        check(out.all { (p, n) -> seeds.follows(p, n) })
        return out
    }
}
