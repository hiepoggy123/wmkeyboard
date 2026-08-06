package com.wasimaster.wmkeyboard.core.prediction.eval

import com.wasimaster.wmkeyboard.core.prediction.MappedNgramPack
import com.wasimaster.wmkeyboard.core.prediction.NgramPack
import com.wasimaster.wmkeyboard.core.prediction.NgramPackBuilder
import com.wasimaster.wmkeyboard.core.prediction.NgramPackCodec
import java.io.File
import kotlin.random.Random

/**
 * A corpus n-gram pack of realistic shape, compiled through the production
 * writer and read back through the production reader.
 *
 * Latency work needs this because the benchmarks otherwise leave
 * [SuggestionEngine.ngramPack][com.wasimaster.wmkeyboard.core.prediction.SuggestionEngine.ngramPack]
 * at [NgramPack.EMPTY], where `bigramCount` returns on its first branch — so
 * every recorded number describes a keyboard with no pack installed, which is
 * not the case this code is tuned for.
 *
 * Shape, not content, is what matters: heads and followers are drawn from the
 * real dictionary's frequency distribution, so a handful of common words own
 * thousands of followers while the tail owns one or two. That skew is what
 * decides whether a follower lookup is cheap.
 */
object SyntheticNgramPack {

    /** The caps [com.wasimaster.wmkeyboard.core.dictionaries.NgramPackDownloadManager] compiles to. */
    const val BIGRAMS = 150_000
    const val TRIGRAMS = 75_000

    fun of(
        dictionary: List<Pair<String, Int>>,
        dir: File,
        bigrams: Int = BIGRAMS,
        trigrams: Int = TRIGRAMS,
        seed: Long = 7L,
    ): NgramPack = NgramPack.of(mapped(dictionary, dir, bigrams, trigrams, seed))

    fun mapped(
        dictionary: List<Pair<String, Int>>,
        dir: File,
        bigrams: Int = BIGRAMS,
        trigrams: Int = TRIGRAMS,
        seed: Long = 7L,
    ): MappedNgramPack {
        val sampler = Sampler(dictionary, Random(seed))
        val builder = NgramPackBuilder()
        for ((words, count) in sampler.pairs(bigrams, parts = 2)) {
            builder.addBigram(words[0], words[1], count)
        }
        for ((words, count) in sampler.pairs(trigrams, parts = 3)) {
            builder.addTrigram(words[0], words[1], words[2], count)
        }
        dir.mkdirs()
        val file = File(dir, "ngrams.wmng")
        file.outputStream().use { NgramPackCodec.write(builder.build(), it) }
        return MappedNgramPack.open(file) ?: error("failed to map ${file.name}")
    }

    /** Draws words in proportion to their dictionary frequency. */
    private class Sampler(dictionary: List<Pair<String, Int>>, private val random: Random) {

        private val words = dictionary.map { it.first }
        private val frequency = dictionary.map { it.second }
        private val cumulative = IntArray(dictionary.size).also { out ->
            var running = 0
            for (i in dictionary.indices) {
                running += frequency[i]
                out[i] = running
            }
        }
        private val total = cumulative.lastOrNull() ?: 1

        /** Index of the first cumulative bucket past a uniform draw. */
        private fun pick(): Int {
            val target = random.nextInt(total)
            var lo = 0
            var hi = cumulative.size - 1
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (cumulative[mid] <= target) lo = mid + 1 else hi = mid
            }
            return lo
        }

        /** [count] distinct n-grams of [parts] words, with plausible counts. */
        fun pairs(count: Int, parts: Int): List<Pair<List<String>, Int>> {
            val seen = HashSet<List<Int>>(count * 2)
            val out = ArrayList<Pair<List<String>, Int>>(count)
            while (out.size < count) {
                val picked = IntArray(parts) { pick() }
                if (!seen.add(picked.toList())) continue
                // A pair is about as common as its words co-occurring by
                // chance would be, which reproduces the long tail the real
                // lists have without needing them.
                var joint = frequency[picked[0]].toLong()
                for (i in 1 until parts) joint = joint * frequency[picked[i]] / total
                out.add(picked.map { words[it] } to joint.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt())
            }
            return out
        }
    }
}
