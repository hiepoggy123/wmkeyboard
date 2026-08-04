package com.wasimaster.wmkeyboard.core.prediction.eval

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Reproducible synthetic typo corpus. The corpus is a pure function of
 * (seed, word list): substitutions come from a 2D Gaussian touch-noise model
 * over [QwertyGeometry] so they are adjacency-realistic rather than uniform,
 * and the remaining error kinds are injected at literature-typical rates
 * (sub/insert/delete/transpose ≈ 60/15/15/10).
 */
class TypoCorpus(seed: Long = 42L) {

    data class Case(val intended: String, val typed: String, val previous: String?)

    private val random = Random(seed)

    /** σ in key widths; x is looser than y on phone keyboards. */
    private val sigmaX = 0.3f
    private val sigmaY = 0.4f

    /** Every case has `typed != intended` and exactly one injected error. */
    fun generate(entries: List<Pair<String, Int>>, count: Int = 2000): List<Case> {
        val sampler = FrequencySampler(entries.filter(::usable))
        val cases = ArrayList<Case>(count)
        while (cases.size < count) {
            val intended = sampler.sample(random)
            val typed = injectError(intended) ?: continue
            if (typed == intended) continue
            val previous = if (random.nextFloat() < 0.5f) sampler.sample(random) else null
            cases.add(Case(intended, typed, previous))
        }
        return cases
    }

    /** Correctly-typed dictionary words, for measuring the false-correction rate. */
    fun cleanWords(entries: List<Pair<String, Int>>, count: Int = 2000): List<String> {
        val sampler = FrequencySampler(entries.filter(::usable))
        return List(count) { sampler.sample(random) }
    }

    private fun usable(entry: Pair<String, Int>): Boolean =
        entry.first.length >= 3 && entry.first.all { it in 'a'..'z' }

    private fun injectError(word: String): String? {
        val roll = random.nextFloat()
        return when {
            roll < 0.60f -> substituteByTouch(word)
            roll < 0.75f -> insertNeighbor(word)
            roll < 0.90f -> deleteChar(word)
            else -> transpose(word)
        }
    }

    /** Sample a tap around the intended key; the nearest key to the tap is what got "typed". */
    private fun substituteByTouch(word: String): String? {
        val at = random.nextInt(word.length)
        val key = QwertyGeometry.keyFor(word[at]) ?: return null
        // Retry a few times: most taps land on the intended key, which is not a typo.
        repeat(8) {
            val x = key.x + gaussian() * sigmaX * 2.2f
            val y = key.y + gaussian() * sigmaY * 2.2f
            val hit = QwertyGeometry.nearestKey(x, y)
            if (hit != word[at]) {
                return word.substring(0, at) + hit + word.substring(at + 1)
            }
        }
        return null
    }

    private fun insertNeighbor(word: String): String? {
        val at = random.nextInt(word.length)
        val neighbors = QwertyGeometry.neighborsOf(word[at])
        if (neighbors.isEmpty()) return null
        val extra = neighbors[random.nextInt(neighbors.size)]
        val before = random.nextBoolean()
        return if (before) {
            word.substring(0, at) + extra + word.substring(at)
        } else {
            word.substring(0, at + 1) + extra + word.substring(at + 1)
        }
    }

    private fun deleteChar(word: String): String? {
        if (word.length < 4) return null
        val at = random.nextInt(word.length)
        return word.substring(0, at) + word.substring(at + 1)
    }

    private fun transpose(word: String): String? {
        if (word.length < 4) return null
        val at = random.nextInt(word.length - 1)
        if (word[at] == word[at + 1]) return null
        val chars = word.toCharArray()
        val tmp = chars[at]
        chars[at] = chars[at + 1]
        chars[at + 1] = tmp
        return String(chars)
    }

    /** Box–Muller: kotlin.random.Random has no Gaussian sampler. */
    private fun gaussian(): Float {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return (sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)).toFloat()
    }

    /** Cumulative-sum frequency-weighted sampling with binary search. */
    private class FrequencySampler(entries: List<Pair<String, Int>>) {
        private val words = entries.map { it.first }
        private val cumulative = LongArray(entries.size)

        init {
            require(entries.isNotEmpty()) { "no usable words in the entry list" }
            var total = 0L
            for (i in entries.indices) {
                total += entries[i].second.coerceAtLeast(1)
                cumulative[i] = total
            }
        }

        fun sample(random: Random): String {
            val target = random.nextLong(cumulative.last())
            var lo = 0
            var hi = cumulative.size - 1
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (cumulative[mid] <= target) lo = mid + 1 else hi = mid
            }
            return words[lo]
        }
    }
}
