// The harness runs on the JVM only and stdout IS its report channel — the
// device-invisibility rationale behind the println ban does not apply here.
@file:Suppress("ForbiddenMethodCall")

package com.wasimaster.wmkeyboard.core.prediction.eval

import com.wasimaster.wmkeyboard.core.prediction.BeamWorkspace
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.FuzzyBeamSearch
import com.wasimaster.wmkeyboard.core.prediction.KeyProximity
import com.wasimaster.wmkeyboard.core.prediction.PackedTrie
import java.io.File
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Measures the headroom for injecting left-context into the fuzzy walk
 * itself, as opposed to the existing post-walk context boost.
 *
 * The post-walk boost can only promote candidates the walk already emitted:
 * the strip's walk runs at k = max(limit * 2, AUTOCORRECT_K) = 8. A word the
 * beam prunes before the top-8 is invisible to every downstream rescoring
 * step no matter how strong its bigram evidence. This harness counts how
 * often that happens to context-reachable targets, in two populations:
 *
 *  - "seed": the intended word is a real follower from the bundled
 *    seed-bigram list — the cold-user context the seed boost serves.
 *    Followers here skew common, so this bounds the everyday case.
 *  - "learned q1..q4": the intended word is drawn uniformly from each
 *    frequency quartile of the dictionary, standing in for personal bigrams
 *    (userLexicon.learnBigram accepts any pair). The low-unigram,
 *    high-bigram "New York" archetype lives in q3/q4.
 *
 * Each case corrupts the target with one realistic error and runs the raw
 * walk once at k=64. Because the walk's pruning is admissible, the first 8
 * of that list are exactly the production top-8. The rank bands then tell
 * four stories at once:
 *
 *   rank 1      — context unnecessary, unigram evidence already wins
 *   ranks 2-8   — the existing post-walk boost can rescue it
 *   ranks 9-64  — merely widening k would rescue it, no beam changes
 *   absent      — only in-beam context could help (or the word is
 *                 unreachable within the edit budget, and nothing can)
 *
 * Only the "absent" band justifies touching FuzzyBeamSearch.
 */
class ContextMissRateTest {

    private companion object {
        const val SEED = 42L
        const val SEED_CASES = 1000
        const val QUARTILE_CASES = 500

        /** limit=32 makes the walk return k = 64 candidates. */
        const val WIDE_LIMIT = 32
        const val WIDE_K = 64
        const val STRIP_K = FuzzyBeamSearch.AUTOCORRECT_K
    }

    private data class Case(val intended: String, val typed: String, val frequency: Int)

    private fun realEntries(): List<Pair<String, Int>> {
        val candidates = listOf(
            File("dictionaries-src/en.txt"),
            File("app/dictionaries-src/en.txt"),
        )
        return candidates.firstOrNull { it.exists() }?.inputStream()
            ?.use { DictionaryLoader.loadEntries(it) }
            ?: error("en.txt not found (cwd=${File(".").absolutePath})")
    }

    /** Every (prev, next) pair the bundled seed list knows. */
    private fun seedPairs(): List<Pair<String, String>> {
        val candidates = listOf(
            File("src/main/assets/dictionaries/en_bigrams.txt"),
            File("app/src/main/assets/dictionaries/en_bigrams.txt"),
        )
        val file = candidates.firstOrNull { it.exists() } ?: error("en_bigrams.txt not found")
        val out = ArrayList<Pair<String, String>>()
        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size >= 2) out.add(parts[0].lowercase() to parts[1].lowercase())
        }
        return out
    }

    private fun usable(word: String): Boolean = word.length >= 3 && word.all { it in 'a'..'z' }

    @Test
    fun measureWalkTop8MissRateForContextReachableTargets() {
        val entries = realEntries()
        val dictWords = entries.toMap()
        val dictionary = PackedTrie.of(entries)
        val beam = FuzzyBeamSearch()
        val workspace = BeamWorkspace()
        val sources = listOf(
            FuzzyBeamSearch.WalkSource(dictionary, 0.0, FuzzyBeamSearch.Tier.DICTIONARY)
        )
        val corpus = TypoCorpus(SEED)
        val random = Random(SEED)

        // The walk's rank of the intended word at k=64, or null when absent.
        fun rankOf(typed: String, intended: String): Int? {
            val ranked = beam.search(sources, typed, KeyProximity.QWERTY, WIDE_LIMIT, workspace)
            val idx = ranked.indexOfFirst { it.word == intended }
            return if (idx >= 0) idx + 1 else null
        }

        // A case only counts if the corruption produced a non-word: the
        // engine never autocorrects (and gates out edited candidates for) a
        // typed string the dictionary knows, so context injection could not
        // change those cases either.
        fun casesFrom(count: Int, pick: () -> String): List<Case> {
            val cases = ArrayList<Case>(count)
            var attempts = 0
            while (cases.size < count && attempts < count * 100) {
                attempts++
                val intended = pick()
                val typed = corpus.corrupt(intended) ?: continue
                if (dictWords.containsKey(typed)) continue
                cases.add(Case(intended, typed, dictWords.getValue(intended)))
            }
            check(cases.size == count) { "corpus generation starved after $attempts attempts" }
            return cases
        }

        data class SliceResult(
            val label: String,
            val n: Int,
            val top1: Int,
            val top8: Int,
            val top16: Int,
            val top32: Int,
            val top64: Int,
            val misses: List<Case>,
        )

        fun measure(label: String, cases: List<Case>): SliceResult {
            var top1 = 0
            var top8 = 0
            var top16 = 0
            var top32 = 0
            var top64 = 0
            val misses = ArrayList<Case>()
            for (case in cases) {
                when (val rank = rankOf(case.typed, case.intended)) {
                    null -> misses.add(case)
                    else -> {
                        top64++
                        if (rank <= 32) top32++
                        if (rank <= 16) top16++
                        if (rank <= STRIP_K) top8++
                        if (rank == 1) top1++
                    }
                }
            }
            return SliceResult(label, cases.size, top1, top8, top16, top32, top64, misses)
        }

        // Slice A: real bundled seed pairs, follower corrupted.
        val followers = seedPairs().map { it.second }.filter { usable(it) && it in dictWords }
        check(followers.isNotEmpty()) { "no usable seed followers" }
        val seedSlice = measure(
            "seed",
            casesFrom(SEED_CASES) { followers[random.nextInt(followers.size)] },
        )

        // Slice B: uniform draws from each frequency quartile of the usable
        // dictionary, most-frequent quartile first.
        val byFrequency = entries.filter { usable(it.first) }.sortedByDescending { it.second }
        val quartileSize = byFrequency.size / 4
        val quartileSlices = (0 until 4).map { q ->
            val lo = q * quartileSize
            val hi = if (q == 3) byFrequency.size else (q + 1) * quartileSize
            measure(
                "learned q${q + 1}",
                casesFrom(QUARTILE_CASES) { byFrequency[lo + random.nextInt(hi - lo)].first },
            )
        }

        val slices = listOf(seedSlice) + quartileSlices
        fun pct(v: Double) = String.format(Locale.ROOT, "%.4f", v)
        println("=== context miss rate (seed=$SEED, k=$WIDE_K, strip k=$STRIP_K) ===")
        println("slice        n     top1    top8    top16   top32   top64   top8Miss  absentAt64")
        for (s in slices) {
            println(
                String.format(
                    Locale.ROOT,
                    "%-11s %5d  %s  %s  %s  %s  %s  %s    %s",
                    s.label, s.n,
                    pct(s.top1 / s.n.toDouble()),
                    pct(s.top8 / s.n.toDouble()),
                    pct(s.top16 / s.n.toDouble()),
                    pct(s.top32 / s.n.toDouble()),
                    pct(s.top64 / s.n.toDouble()),
                    pct(1.0 - s.top8 / s.n.toDouble()),
                    pct(s.misses.size / s.n.toDouble()),
                )
            )
        }
        for (s in slices) {
            if (s.misses.isEmpty()) continue
            val sample = s.misses.take(10)
                .joinToString(", ") { "${it.typed}->${it.intended}(f=${it.frequency})" }
            println("${s.label} absent-at-64 examples: $sample")
        }

        val json = buildString {
            append("{\n")
            append("  \"seed\": $SEED,\n")
            append("  \"wideK\": $WIDE_K,\n")
            append("  \"stripK\": $STRIP_K,\n")
            append("  \"slices\": [\n")
            slices.forEachIndexed { i, s ->
                append("    {")
                append("\"label\": \"${s.label}\", \"n\": ${s.n}, ")
                append("\"top1\": ${pct(s.top1 / s.n.toDouble())}, ")
                append("\"top8\": ${pct(s.top8 / s.n.toDouble())}, ")
                append("\"top16\": ${pct(s.top16 / s.n.toDouble())}, ")
                append("\"top32\": ${pct(s.top32 / s.n.toDouble())}, ")
                append("\"top64\": ${pct(s.top64 / s.n.toDouble())}, ")
                append("\"top8MissRate\": ${pct(1.0 - s.top8 / s.n.toDouble())}, ")
                append("\"absentAt64\": ${pct(s.misses.size / s.n.toDouble())}")
                append("}")
                append(if (i < slices.size - 1) ",\n" else "\n")
            }
            append("  ]\n")
            append("}\n")
        }
        val out = File("build/reports/predeval/context_miss.json")
        out.parentFile?.mkdirs()
        out.writeText(json)
        println("metrics written to ${out.path}")

        // Sanity floors only — this is a measurement, not a regression gate.
        // Seed followers are common words: if most fall out of the top-8 the
        // harness itself is broken (wrong sources, wrong proximity, empty walk).
        assertTrue(
            "seed-slice top8 ${seedSlice.top8}/${seedSlice.n} implausibly low — harness broken?",
            seedSlice.top8 / seedSlice.n.toDouble() > 0.5,
        )
    }
}
