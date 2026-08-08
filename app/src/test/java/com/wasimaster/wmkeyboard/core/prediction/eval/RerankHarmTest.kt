// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.prediction.eval

import com.wasimaster.wmkeyboard.core.prediction.CandidateReranker
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
 * The counterweight to `RerankGainTest`: what the context reranker COSTS.
 *
 * Gain is measured on cases where the population prior happens to be right,
 * which is the easy half of the question and the only half the existing
 * harnesses ask. `RerankGainTest`'s random-context corpus cannot ask the other
 * half — a random previous word means the seed term almost never fires, so a
 * "0/1000 lists changed" reading is the reranker staying silent, not the
 * reranker being safe.
 *
 * This corpus is built to make it speak and be wrong. Each case types a real
 * dictionary word **correctly**, choosing one that sits an edit away from a
 * common follower of the preceding word. "hello" primes "world"; the user
 * types "words". Nothing is misspelled, so the right answer is unambiguous and
 * any drop in top1 is the prior burying a word the user actually typed.
 *
 * The number this prints is the one to weigh a cap change against. A reranker
 * cap is a trade, and a trade needs both sides measured.
 */
class RerankHarmTest {

    private companion object {
        const val CASES = 600
        const val SEED = 11L

        /** Neighbours rarer than this are not plausible intended words. */
        const val MIN_NEIGHBOUR_FREQ = 200
    }

    private fun realEntries(): List<Pair<String, Int>> {
        val candidates = listOf(
            File("dictionaries-src/en.txt"),
            File("app/dictionaries-src/en.txt"),
        )
        return candidates.firstOrNull { it.exists() }?.inputStream()
            ?.use { DictionaryLoader.loadEntries(it) }
            ?: error("en.txt not found")
    }

    private fun seedFile(): File = listOf(
        File("src/main/assets/dictionaries/en_bigrams.txt"),
        File("app/src/main/assets/dictionaries/en_bigrams.txt"),
    ).firstOrNull { it.exists() } ?: error("en_bigrams.txt not found")

    @Test
    fun seedPriorDoesNotBuryACorrectlyTypedNeighbour() {
        val entries = realEntries()
        val freq = entries.toMap()
        val seeds = seedFile().inputStream().use { SeedBigrams.load(it) }

        // (prev, follower) the seed list knows, grouped so a candidate
        // neighbour can be checked against every follower of the same prev —
        // a neighbour that is ITSELF a follower is not a harm case, it is an
        // agreement case.
        val followers = HashMap<String, MutableSet<String>>()
        seedFile().forEachLine { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEachLine
            val p = t.split(Regex("\\s+"))
            if (p.size >= 2) {
                followers.getOrPut(p[0].lowercase()) { HashSet() }.add(p[1].lowercase())
            }
        }

        val random = Random(SEED)
        val prevs = followers.keys.sorted()

        class Case(val previous: String, val typed: String, val primed: String)

        val cases = ArrayList<Case>(CASES)
        var attempts = 0
        while (cases.size < CASES && attempts < CASES * 400) {
            attempts++
            val prev = prevs[random.nextInt(prevs.size)]
            val known = followers[prev] ?: continue
            val primed = known.elementAt(random.nextInt(known.size))
            if (primed.length < 4 || !primed.all { it in 'a'..'z' }) continue
            // A real word one edit from the primed follower, common enough to
            // be a plausible thing to type, and not itself primed by `prev`.
            val neighbour = editNeighbours(primed)
                .filter { it !in known && (freq[it] ?: 0) >= MIN_NEIGHBOUR_FREQ }
                .minByOrNull { random.nextInt(1000) } ?: continue
            cases.add(Case(prev, neighbour, primed))
        }
        check(cases.size == CASES) { "only built ${cases.size} harm cases" }

        val dictionary = PackedTrie.of(entries)
        val lexicon = UserLexicon(null)
        val engine = SuggestionEngine(
            dictionary, BengaliPhoneticIndex(emptyList()), lexicon, seedBigrams = seeds,
        )

        fun top1(rerank: Boolean): Double {
            engine.reranker = if (rerank) {
                NgramReranker(lexicon, seeds, dictionaryFrequency = { dictionary.frequencyOf(it) })
            } else {
                CandidateReranker.NONE
            }
            var hits = 0
            for (case in cases) {
                val out = engine.suggest(
                    case.typed, previousWord = case.previous, allowRerank = rerank,
                )
                if (out.firstOrNull()?.equals(case.typed, ignoreCase = true) == true) hits++
            }
            return hits / cases.size.toDouble()
        }

        val base = top1(rerank = false)
        val reranked = top1(rerank = true)
        fun pct(v: Double) = String.format(Locale.ROOT, "%.4f", v)
        println(
            "rerank harm (correctly-typed neighbour of a primed word): " +
                "${pct(base)} -> ${pct(reranked)} (${pct(reranked - base)}), n=${cases.size}"
        )

        // The gate. The prior is allowed to cost something — it buys more than
        // it costs, which is the whole point of the trade — but a
        // correctly-typed word losing the strip's first slot is the failure
        // users notice, so the loss is bounded well under the gain the
        // matching RerankGainTest measures.
        assertTrue(
            "seed prior buries correctly-typed words too often: ${pct(base - reranked)}",
            base - reranked < 0.02,
        )
    }

    /** Every real-word string one substitution, deletion or insertion away. */
    private fun editNeighbours(word: String): List<String> {
        val out = ArrayList<String>(word.length * 60)
        for (i in word.indices) {
            for (c in 'a'..'z') {
                if (c != word[i]) out.add(word.substring(0, i) + c + word.substring(i + 1))
            }
            out.add(word.substring(0, i) + word.substring(i + 1))
        }
        for (i in 0..word.length) {
            for (c in 'a'..'z') out.add(word.substring(0, i) + c + word.substring(i))
        }
        return out
    }
}
