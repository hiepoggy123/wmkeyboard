package com.wasimaster.wmkeyboard.core.prediction

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What autocorrect has learned about its own mistakes: per-pair revert
 * penalties and a global fired/reverted ratio that adapts the confidence gate.
 *
 * Deliberately its own file (`learning/correction_stats.json`), not part of
 * the [UserLexicon] snapshot: a rejection must persist even when "learn from
 * typing" is off or incognito is on — undoing a correction is the user
 * telling the keyboard it was wrong, which is not optional telemetry — and
 * the lexicon file gets rewritten wholesale by the settings app.
 *
 * Storage follows the personal-store contract: nullable file (direct boot →
 * memory only), dirty-flag save on dismissal, [reload] for external edits.
 */
class CorrectionStats(private val storageFile: File?) {

    enum class Penalty { NONE, PENALIZED, BLOCKED }

    /**
     * [count] is how many times this exact correction was rejected. [kept] is
     * progress towards forgiving one of those: a pair the user has since let
     * stand [KEEPS_TO_FORGIVE] times loses a rejection, because a single bad
     * fix in a hurry should not sentence the pair for the six months the
     * expiry clock takes.
     */
    @Serializable
    private data class PairStat(val count: Int, val gen: Long, val kept: Int = 0)

    @Serializable
    private data class Snapshot(
        val pairs: Map<String, PairStat> = emptyMap(),
        val fired: Int = 0,
        val reverted: Int = 0,
        val generation: Long = 0L,
    )

    private val pairs = HashMap<String, PairStat>()
    private var fired = 0
    private var reverted = 0
    private var generation = 0L
    private var dirty = false

    /** Pairs reverted THIS process: the very next space must not re-correct,
     * regardless of persisted counts. */
    private val sessionRejected = HashSet<String>()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        load()
    }

    /**
     * One correction reached its verdict.
     *
     * Deliberately not counted when the correction *fires*, which is what this
     * used to do. At that moment the user has not seen it, so a correction they
     * went back and fixed by hand counted as a success and quietly told the
     * adaptive gate that autocorrect was doing better than it was. A verdict
     * arrives once the text has settled around the correction — see
     * [CorrectionWatch] — or immediately when the user backspaces it away.
     */
    private fun bumpFired() {
        fired++
        // Halving counters: an exponential moving window with no timestamps.
        if (fired >= WINDOW) {
            fired /= 2
            reverted /= 2
        }
        dirty = true
    }

    /** The user undid the correction of [typed] into [corrected]. */
    @Synchronized
    fun recordRevert(typed: String, corrected: String) {
        val key = key(typed, corrected)
        sessionRejected.add(key)
        val existing = pairs[key]
        // The accept progress goes with it: a pair being rejected again is not
        // most of the way to being forgiven.
        pairs[key] = PairStat((existing?.count ?: 0) + 1, generation)
        reverted++
        bumpFired()
        if (pairs.size > MAX_PAIRS) {
            pairs.remove(pairs.entries.minByOrNull { it.value.gen }?.key)
        }
        dirty = true
    }

    /**
     * The user let the correction of [typed] into [corrected] stand.
     *
     * Counts towards the global ratio, and towards forgiving this pair if it
     * carries a rejection. Only pairs already on record are touched: a
     * correction nobody ever objected to needs no entry, and giving every
     * accepted correction a slot would evict the rejections this store exists
     * to remember.
     */
    @Synchronized
    fun recordKept(typed: String, corrected: String) {
        bumpFired()
        val key = key(typed, corrected)
        // A pair rejected in this session stays rejected for it, whatever the
        // user does afterwards: the block is the promise that the very next
        // space will not re-correct.
        if (key in sessionRejected) return
        val existing = pairs[key] ?: return
        val kept = existing.kept + 1
        when {
            // Not enough accepts yet to buy back a rejection.
            kept < KEEPS_TO_FORGIVE -> pairs[key] = existing.copy(kept = kept, gen = generation)
            // Its last rejection is forgiven, so the pair is ordinary again.
            existing.count <= 1 -> pairs.remove(key)
            else -> pairs[key] = PairStat(existing.count - 1, generation, kept = 0)
        }
    }

    /** How strongly this exact correction is disfavored. A different
     * correction of the same typed word is untouched — one bad fix must not
     * take every other fix down with it. */
    @Synchronized
    fun penalty(typed: String, corrected: String): Penalty {
        val key = key(typed, corrected)
        if (key in sessionRejected) return Penalty.BLOCKED
        val count = pairs[key]?.count ?: 0
        return when {
            count >= BLOCK_AT -> Penalty.BLOCKED
            count >= 1 -> Penalty.PENALIZED
            else -> Penalty.NONE
        }
    }

    /**
     * Multiplier on the user's confidence setting, from the recent
     * revert rate: a keyboard being corrected-then-undone often should
     * demand more confidence before forcing anything; a clean record may
     * loosen very slightly. 1.0 until there is a real sample.
     */
    @Synchronized
    fun confidenceMultiplier(): Double {
        if (fired < MIN_SAMPLE) return 1.0
        val revertRate = reverted.toDouble() / fired
        return (1.0 + RATE_GAIN * (revertRate - TARGET_RATE))
            .coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
    }

    @Synchronized
    fun save() {
        val file = storageFile ?: return
        if (!dirty) return
        generation++
        expireStalePairs()
        val snapshot = Snapshot(
            pairs = pairs.toMap(),
            fired = fired,
            reverted = reverted,
            generation = generation,
        )
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(snapshot))
        }.onSuccess { dirty = false }
    }

    @Synchronized
    fun reload() {
        pairs.clear()
        fired = 0
        reverted = 0
        load()
        dirty = false
    }

    @Synchronized
    fun clear() {
        pairs.clear()
        sessionRejected.clear()
        fired = 0
        reverted = 0
        dirty = storageFile?.delete() == false
    }

    /** Penalties age out: one revert from months of sessions ago should not
     * dampen a correction forever. Each pair untouched for
     * [EXPIRE_GENERATIONS] saves loses one count (and is refreshed, so the
     * next decrement is another full period away). */
    private fun expireStalePairs() {
        val stale = pairs.filterValues { generation - it.gen > EXPIRE_GENERATIONS }
        for ((key, stat) in stale) {
            if (stat.count <= 1) {
                pairs.remove(key)
            } else {
                pairs[key] = PairStat(stat.count - 1, generation)
            }
        }
    }

    private fun load() {
        val file = storageFile ?: return
        if (!file.exists()) return
        runCatching {
            val snapshot = json.decodeFromString<Snapshot>(file.readText())
            pairs.putAll(snapshot.pairs)
            fired = snapshot.fired
            reverted = snapshot.reverted
            generation = snapshot.generation
        }
    }

    private companion object {
        /** NUL separator, built rather than written literally. */
        val SEPARATOR: Char = 0.toChar()

        fun key(typed: String, corrected: String): String =
            typed.lowercase() + SEPARATOR + corrected.lowercase()

        /** Second persisted revert of the same pair blocks it outright. */
        const val BLOCK_AT = 2

        /**
         * Accepted uses of a rejected pair that buy back one rejection.
         *
         * Three rather than one, because letting a correction stand is much
         * weaker evidence than reaching for backspace to undo one: people
         * often simply do not notice. It only reaches BLOCKED pairs through
         * [expireStalePairs], since a blocked pair never fires and so can
         * never be accepted.
         */
        const val KEEPS_TO_FORGIVE = 3
        const val MAX_PAIRS = 500
        const val EXPIRE_GENERATIONS = 180L

        /** Halving window for the fired/reverted counters. */
        const val WINDOW = 200
        const val MIN_SAMPLE = 20
        const val TARGET_RATE = 0.03
        const val RATE_GAIN = 20.0
        const val MIN_MULTIPLIER = 0.85
        const val MAX_MULTIPLIER = 2.5
    }
}
