package com.wasimaster.wmkeyboard.core.vocab

import java.io.File
import java.util.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How a flashcard went. [quality] is the SM-2 grade the answer maps to. */
enum class ReviewGrade(val quality: Int) {
    AGAIN(1),
    HARD(3),
    GOOD(4),
    EASY(5),
}

/** One review, kept so the app can draw a history. */
@Serializable
data class ReviewEvent(
    val day: Int,
    val grade: Int,
    val scheme: String = "",
)

/**
 * Where one word stands. Both schedulers keep their state here so a user
 * who switches scheme mid-way keeps what the other one learned about the
 * word: [box] is Leitner's, [ease]/[intervalDays]/[reps] are SM-2's, and
 * [dueDay]/[learnt] are what everybody reads.
 */
@Serializable
data class WordProgress(
    val box: Int = 0,
    val ease: Double = Sm2Scheduler.START_EASE,
    val intervalDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    /** Local epoch day the next review is due; 0 means never scheduled. */
    val dueDay: Int = 0,
    val firstDay: Int = 0,
    val lastDay: Int = 0,
    val learnt: Boolean = false,
    val history: List<ReviewEvent> = emptyList(),
) {
    val seen: Boolean get() = firstDay > 0 || learnt
}

/** The numbers a progress screen shows for one pack or for everything. */
data class ProgressStats(
    val seen: Int,
    val learnt: Int,
    val due: Int,
    val reviewedToday: Int,
)

/**
 * The user's learning record: one [WordProgress] per word, keyed by lemma
 * rather than by pack so it survives packs being deleted and downloaded
 * again, plus which word each day drew as its word of the day.
 *
 * Same personal-store contract as `TypingStats`: a nullable file (direct
 * boot → memory only), a dirty flag with an explicit [save], and [reload]
 * for edits by the other process. The keyboard and the settings app both
 * write this file; each saves promptly after a change and re-reads on its
 * next opening, which is enough because a review is a tap, not a stream.
 */
class VocabProgress(private var storageFile: File?) {

    @Serializable
    private data class Snapshot(
        val version: Int = 1,
        val words: Map<String, WordProgress> = emptyMap(),
        /** Word-of-the-day slot ([WordOfDay.slot]) → the lemma drawn for it. */
        val daily: Map<Int, String> = emptyMap(),
        /** The slot whose word-of-the-day card was put away; 0 means never. */
        val dismissedSlot: Int = 0,
    )

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val words = HashMap<String, WordProgress>()
    private val daily = HashMap<Int, String>()
    private var dismissedSlot = 0
    private var dirty = false
    private var loadedLength = -1L
    private var loadedModified = -1L

    init {
        load()
    }

    /** Points a store created blind (direct boot) at its file once the user unlocks. */
    @Synchronized
    fun attach(file: File) {
        if (storageFile == file) return
        storageFile = file
        load()
    }

    @Synchronized
    fun stateOf(word: String): WordProgress = words[word] ?: WordProgress()

    @Synchronized
    fun isLearnt(word: String): Boolean = words[word]?.learnt == true

    @Synchronized
    fun isSeen(word: String): Boolean = words[word]?.seen == true

    /** Applies one flashcard answer and returns the word's new state. */
    @Synchronized
    fun review(word: String, grade: ReviewGrade, nowDay: Int, scheme: VocabScheduler): WordProgress {
        val next = preview(word, grade, nowDay, scheme)
        words[word] = next
        dirty = true
        return next
    }

    /** What [review] would produce, for interval previews on the grade buttons. */
    @Synchronized
    fun preview(word: String, grade: ReviewGrade, nowDay: Int, scheme: VocabScheduler): WordProgress {
        val current = stateOf(word)
        val scheduled = when (scheme) {
            VocabScheduler.LEITNER -> LeitnerScheduler.next(current, grade, nowDay)
            VocabScheduler.SM2 -> Sm2Scheduler.next(current, grade, nowDay)
        }
        val history = (current.history + ReviewEvent(nowDay, grade.quality, scheme.name)).takeLast(MAX_HISTORY)
        return scheduled.copy(
            firstDay = if (current.firstDay == 0) nowDay else current.firstDay,
            lastDay = nowDay,
            history = history,
        )
    }

    /** Marks a word known (or unknown again) by hand; a known word stops being due. */
    @Synchronized
    fun markLearnt(word: String, learnt: Boolean, nowDay: Int) {
        val current = stateOf(word)
        words[word] = if (learnt) {
            current.copy(
                learnt = true,
                box = LeitnerScheduler.TOP,
                firstDay = if (current.firstDay == 0) nowDay else current.firstDay,
                lastDay = nowDay,
            )
        } else {
            current.copy(learnt = false, box = 0, intervalDays = 0, reps = 0, dueDay = nowDay)
        }
        dirty = true
    }

    /** Forgets everything about [word]. */
    @Synchronized
    fun reset(word: String) {
        if (words.remove(word) != null) dirty = true
    }

    /**
     * Words due on or before [day]: seen, not learnt, and scheduled. Sorted
     * most-overdue first. [among] narrows to one pack's words.
     */
    @Synchronized
    fun dueWords(day: Int, among: Collection<String>? = null): List<String> {
        val candidates = among ?: words.keys
        return candidates
            .filter { lemma ->
                val state = words[lemma] ?: return@filter false
                state.seen && !state.learnt && state.dueDay in 1..day
            }
            .sortedWith(compareBy({ words[it]?.dueDay ?: 0 }, { it }))
    }

    /** Words never reviewed and not marked learnt, in the given order. */
    @Synchronized
    fun unseen(among: Collection<String>): List<String> =
        among.filter { words[it]?.seen != true }

    @Synchronized
    fun stats(day: Int, among: Collection<String>? = null): ProgressStats {
        val candidates = among ?: words.keys
        var seen = 0
        var learnt = 0
        var due = 0
        var today = 0
        for (lemma in candidates) {
            val state = words[lemma] ?: continue
            if (state.seen) seen++
            if (state.learnt) learnt++
            if (state.seen && !state.learnt && state.dueDay in 1..day) due++
            if (state.lastDay == day && state.history.isNotEmpty()) today++
        }
        return ProgressStats(seen = seen, learnt = learnt, due = due, reviewedToday = today)
    }

    /**
     * The word drawn for [slot] (a day, or a fraction of one — see
     * [WordOfDay.slot]), pinned once drawn so a word marked learnt in the
     * afternoon does not change the morning's card. [lemmas] are every word
     * of the enabled packs; the draw itself is the same for everyone who has
     * those packs, and only then are the words this user has learnt skipped.
     * Returns null when there is nothing to draw from.
     */
    @Synchronized
    fun wordOfTheDay(slot: Int, lemmas: List<String>): String? {
        if (lemmas.isEmpty()) return null
        daily[slot]?.let { pinned -> if (pinned in lemmas) return pinned }
        val learnt = lemmas.filterTo(HashSet()) { words[it]?.learnt == true }
        val picked = WordOfDay.pick(slot, lemmas, exclude = learnt) ?: return null
        daily[slot] = picked
        if (daily.size > KEEP_SLOTS) {
            daily.keys.sorted().take(daily.size - KEEP_SLOTS).forEach { daily.remove(it) }
        }
        dirty = true
        return picked
    }

    /** The word already drawn for [slot], without drawing one. */
    @Synchronized
    fun pinnedWordOfTheDay(slot: Int): String? = daily[slot]

    /**
     * Whether the word-of-the-day card was put away for [slot]. Dismissing is
     * for one slot rather than for good: the next one draws a different word,
     * and the switch in the tool's settings is the way to stop the card.
     */
    @Synchronized
    fun isWordOfTheDayDismissed(slot: Int): Boolean = dismissedSlot == slot

    @Synchronized
    fun dismissWordOfTheDay(slot: Int) {
        if (dismissedSlot == slot) return
        dismissedSlot = slot
        dirty = true
    }

    @Synchronized
    fun save() {
        val file = storageFile ?: return
        if (!dirty) return
        runCatching {
            file.parentFile?.mkdirs()
            val part = File(file.parentFile, file.name + ".part")
            part.writeText(
                json.encodeToString(
                    Snapshot(
                        words = words.toSortedMap(),
                        daily = daily.toSortedMap(),
                        dismissedSlot = dismissedSlot,
                    ),
                ),
            )
            file.delete()
            part.renameTo(file)
            loadedLength = file.length()
            loadedModified = file.lastModified()
            dirty = false
        }
    }

    /** Re-reads the file, dropping unsaved changes; for an edit by the other process. */
    @Synchronized
    fun reload() {
        load()
    }

    /** [reload]s only when the file changed since it was last read; true when it did. */
    @Synchronized
    fun reloadIfChanged(): Boolean {
        val file = storageFile ?: return false
        if (file.length() == loadedLength && file.lastModified() == loadedModified) return false
        load()
        return true
    }

    private fun load() {
        words.clear()
        daily.clear()
        dismissedSlot = 0
        dirty = false
        val file = storageFile ?: return
        loadedLength = file.length()
        loadedModified = file.lastModified()
        if (!file.isFile) return
        val snapshot = runCatching { json.decodeFromString<Snapshot>(file.readText()) }.getOrNull() ?: return
        words.putAll(snapshot.words)
        daily.putAll(snapshot.daily)
        dismissedSlot = snapshot.dismissedSlot
    }

    companion object {
        const val FILE_PATH = "vocab/progress.json"
        const val MAX_HISTORY = 50

        /** Pins kept: a week of hourly slots, so a re-opened card finds its word. */
        private const val KEEP_SLOTS = 7 * 24
    }
}

/**
 * Five boxes with growing gaps. A right answer moves the card up a box, a
 * wrong one sends it back to the first; a card that leaves the top box is
 * learnt. Simple enough to draw as a ladder on a keyboard panel.
 */
object LeitnerScheduler {
    val INTERVALS = intArrayOf(1, 3, 7, 14, 30)
    val TOP = INTERVALS.size - 1

    fun next(state: WordProgress, grade: ReviewGrade, nowDay: Int): WordProgress {
        val box = when (grade) {
            ReviewGrade.AGAIN -> 0
            ReviewGrade.HARD -> state.box.coerceIn(0, TOP)
            ReviewGrade.GOOD -> (state.box + 1).coerceAtMost(TOP)
            ReviewGrade.EASY -> (state.box + 2).coerceAtMost(TOP)
        }
        val learnt = grade != ReviewGrade.AGAIN && grade != ReviewGrade.HARD && state.box >= TOP
        return state.copy(
            box = box,
            lapses = if (grade == ReviewGrade.AGAIN) state.lapses + 1 else state.lapses,
            dueDay = nowDay + INTERVALS[box],
            learnt = learnt,
        )
    }
}

/**
 * SuperMemo 2: an ease factor per card that a good answer nudges up and a
 * bad one down, with intervals of 1 day, 6 days, then the last interval
 * times the ease. A card whose interval passes two months is learnt.
 */
object Sm2Scheduler {
    const val START_EASE = 2.5
    const val MIN_EASE = 1.3
    const val LEARNT_INTERVAL_DAYS = 60

    fun next(state: WordProgress, grade: ReviewGrade, nowDay: Int): WordProgress {
        val q = grade.quality
        val ease = (state.ease + 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)).coerceAtLeast(MIN_EASE)
        if (q < 3) {
            return state.copy(
                ease = ease,
                reps = 0,
                intervalDays = 1,
                lapses = state.lapses + 1,
                dueDay = nowDay + 1,
                learnt = false,
            )
        }
        val interval = when (state.reps) {
            0 -> 1
            1 -> 6
            else -> Math.round(state.intervalDays * ease).toInt().coerceAtLeast(state.intervalDays + 1)
        }
        val boosted = if (grade == ReviewGrade.EASY && state.reps >= 2) Math.round(interval * 1.3).toInt() else interval
        return state.copy(
            ease = ease,
            reps = state.reps + 1,
            intervalDays = boosted,
            dueDay = nowDay + boosted,
            learnt = boosted >= LEARNT_INTERVAL_DAYS,
        )
    }
}

/**
 * The word-of-the-day draw. Stateless and the same everywhere: a slot number
 * and the pack's word list give the same word on every device, in both
 * processes, with nothing stored.
 *
 * The words are laid out in a fixed pseudo-random order (a hash of each
 * word, so the order does not depend on which packs came first), and slot
 * `s` takes the word at `s mod n`. Consecutive slots therefore walk the whole
 * list before any word comes round again, and two people with the same packs
 * see the same word at the same hour. Words the user has learnt are skipped
 * by walking on to the next unlearnt one, which is the only place one
 * device's draw can differ from another's.
 */
object WordOfDay {

    private const val DAY_MILLIS = 86_400_000L

    /**
     * The slot [nowMillis] falls in: the local day count times the slots per
     * day, plus which of the day's slots it is. [VocabWordInterval.DAILY]
     * makes a slot equal to the local epoch day, so a daily pin is a day.
     */
    fun slot(nowMillis: Long, zone: TimeZone, interval: VocabWordInterval): Int {
        val local = nowMillis + zone.getOffset(nowMillis)
        val day = Math.floorDiv(local, DAY_MILLIS)
        val hour = Math.floorMod(local, DAY_MILLIS) / 3_600_000L
        return (day * interval.perDay + hour / interval.hours).toInt()
    }

    /** The local epoch day a slot belongs to. */
    fun dayOf(slot: Int, interval: VocabWordInterval): Int = Math.floorDiv(slot, interval.perDay)

    /** When the slot after [slot] begins, in epoch millis, for the "next word in" line. */
    fun nextSlotStart(slot: Int, zone: TimeZone, interval: VocabWordInterval): Long {
        val nextSlot = slot + 1L
        val day = Math.floorDiv(nextSlot, interval.perDay.toLong())
        val hour = Math.floorMod(nextSlot, interval.perDay.toLong()) * interval.hours
        val localMillis = day * DAY_MILLIS + hour * 3_600_000L
        // The offset at roughly that instant; a DST edge inside the slot is a minute's error, not a wrong day.
        return localMillis - zone.getOffset(localMillis)
    }

    fun pick(slot: Int, candidates: List<String>, exclude: Set<String> = emptySet()): String? {
        if (candidates.isEmpty()) return null
        val order = shuffled(candidates)
        val start = Math.floorMod(slot, order.size)
        for (offset in order.indices) {
            val candidate = order[(start + offset) % order.size]
            if (candidate !in exclude) return candidate
        }
        return null
    }

    /** [candidates] in their fixed draw order: the same whatever order they arrived in. */
    fun shuffled(candidates: List<String>): List<String> =
        candidates.distinct().sortedWith(compareBy({ splitMix64(stableHash(it)) }, { it }))

    /** `String.hashCode` is specified, but 32 bits collide; this is the same idea with more room. */
    private fun stableHash(word: String): Long {
        var h = 1125899906842597L
        for (ch in word) h = 31 * h + ch.code
        return h
    }

    private fun splitMix64(seed: Long): Long {
        var z = seed + -7046029254386353131L
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }
}
