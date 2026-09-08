package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.gesture.GlideShapeSample
import kotlin.math.abs

/**
 * How a committed word got into the field.
 *
 * [OCTOPUS] behaves exactly like [PICK] everywhere learning reads this — it is
 * a word the user chose off a surface, not one they spelled — and is kept apart
 * only so the question the feature has to answer can be answered: whether the
 * words floating over the keys are actually used, or are decoration. Folded
 * into [PICK] that number is inside the suggestion strip's and can never be
 * recovered.
 */
enum class WordOrigin { TYPED, GLIDE, PICK, OCTOPUS }

/**
 * Words committed into the field that have not settled yet.
 *
 * A word is not evidence of anything the moment it lands: half of them are
 * about to be backspaced, re-picked from the strip, or edited when the user
 * reads back what they wrote. So a committed word goes here first and only
 * counts towards learning once the text around it has stopped moving —
 * "cached for proofreading", which is what the request that prompted this
 * asked for.
 *
 * Both kinds of word wait here, and [Entry.known] says which is which. A word
 * nothing recognises has to be sighted several times before it joins the
 * lexicon at all ([PendingLearn]); one the keyboard already knows only has to
 * settle once, because there was never any doubt about its spelling — the
 * doubt is whether the user meant *that* word, which is exactly what a glide
 * gets wrong and what reading the text back fixes (#101).
 *
 * Settling is decided by two signals, both cheap enough to run on the typing
 * path because neither reads the field:
 *
 * * **The caret going back.** Each entry remembers where the caret came to
 *   rest after its commit ([Entry.anchor], filled in from the commit's own
 *   selection echo). A later caret anywhere in front of that anchor means the
 *   user has gone back into that text, so the entry is dropped — whatever they
 *   are doing there, the word is no longer something they typed and left
 *   alone.
 * * **Leaving the text.** The keyboard closing, the field being sent or
 *   cleared, or moving to another field all mean the words that survived are
 *   the user's final answer. The caller [drain]s at those points.
 *
 * A dropped word is not quite forgotten. Going back into a word is, more often
 * than not, the start of fixing it, and the fix is the one thing about the
 * word worth learning. So the last few dropped words are kept a while, and
 * when the word committed *in their place* is anchored ([onCaret]) a caret
 * within a slip's distance of the old anchor pairs the two: the new entry
 * carries the old spelling in [Entry.replaces], marked [Entry.suspect]
 * because position alone cannot tell a replacement from a short word typed
 * in front of another — the caller confirms a suspect against the field once,
 * at the flush, before teaching anything from it. A tracker that *knows* the
 * fix (the service's `WordRevision`) passes `replaces` at push instead, and
 * that exact knowledge always wins over a positional guess.
 *
 * Purely in-memory and per-field: nothing here survives the keyboard going
 * away, because the whole question it answers ("did this text settle?") is
 * answered by then. The counts it feeds live in [PendingLearn].
 */
class LearningBuffer(private val capacity: Int = DEFAULT_CAPACITY) {

    /**
     * One committed word waiting to settle.
     *
     * [anchor] is -1 until the commit's selection echo arrives; an entry with
     * no anchor yet can never be invalidated by a caret move, which is
     * correct — the caret has not been reported since the word landed.
     */
    class Entry internal constructor(
        val word: String,
        val langId: String,
        /** How deliberate the commit was; see `WMKeyboardService.learn`. */
        val weight: Int,
        /**
         * Whether [word]'s capitals are the user's own rather than ones the
         * keyboard put there (auto-capitalize, caps lock, an all-caps field).
         * Only the commit site can tell, and by the time the word settles that
         * moment is long gone — so the answer rides along with it (#44).
         */
        val caseTrusted: Boolean = false,
        /**
         * Whether the keyboard already recognised [word] when it was
         * committed. A known word settles straight into the lexicon; an
         * unknown one still has to earn its place through [PendingLearn].
         */
        val known: Boolean = false,
        val origin: WordOrigin = WordOrigin.TYPED,
        /**
         * The spelling this word replaced by hand, to be taught as a fix when
         * this entry settles. Exact when the caller knew it; a positional
         * guess ([suspect]) when [onCaret] paired it with a dropped word.
         */
        replaces: String? = null,
        /**
         * What the composing buffer held when this word committed — the
         * letters the taps in [taps] were aimed at. Equal to [word] for a
         * plain commit; the typed prefix for a strip pick.
         */
        val typed: String = word,
        /** Tap position per character of [typed], null where unknown. */
        val taps: List<TouchPoint?>? = null,
        /** The key-centre model those taps were scored against. */
        val keys: KeyTouchModel? = null,
        /** How the word in [replaces] got into the field, when the caller knows. */
        replacesOrigin: WordOrigin = WordOrigin.TYPED,
        revised: String? = null,
        internal val pushIndex: Long = 0L,
    ) {
        var anchor: Int = UNANCHORED
            internal set

        var replaces: String? = replaces
            internal set

        var replacesOrigin: WordOrigin = replacesOrigin
            internal set

        /** True when [replaces] came from position rather than knowledge. */
        var suspect: Boolean = false
            internal set

        /**
         * What [replaces] became when that is more than this one word: a
         * two-word repair of a fat-fingered space ("thisbis" → "this is").
         * Null means the fix is [word] itself.
         */
        var revised: String? = revised
            internal set

        /**
         * The glide that committed the word, when one did. Rides with the
         * word and lands in the shape store as it settles — never before,
         * because a glide the user takes back teaches nothing about how they
         * draw the word it was read as.
         */
        var glideShape: GlideShapeSample? = null
            internal set
    }

    /** A word the caret went back into, kept for pairing with its replacement. */
    class Dropped internal constructor(
        val word: String,
        val anchor: Int,
        val origin: WordOrigin,
        val replaces: String?,
        internal val droppedAt: Long,
    )

    private val entries = ArrayDeque<Entry>()
    private val recent = ArrayDeque<Dropped>()
    private var pushes = 0L

    val size: Int get() = entries.size

    fun isEmpty(): Boolean = entries.isEmpty()

    /**
     * Queues a freshly committed [word].
     *
     * Returns whatever had to be pushed out to stay inside [capacity] —
     * already-settled by sheer distance, since the user has typed a hundred
     * words since without going back to them. The caller counts those the same
     * way it counts a drain.
     *
     * [anchor] may be given by a caller that knows where the word ends and
     * whose next caret echo will land somewhere else entirely — a word fixed
     * in place and then left by a tap elsewhere. Left at [UNANCHORED] the
     * first echo anchors it, which is right for every ordinary commit.
     */
    fun push(
        word: String,
        langId: String,
        weight: Int,
        caseTrusted: Boolean = false,
        known: Boolean = false,
        origin: WordOrigin = WordOrigin.TYPED,
        replaces: String? = null,
        anchor: Int = UNANCHORED,
        typed: String = word,
        taps: List<TouchPoint?>? = null,
        keys: KeyTouchModel? = null,
        replacesOrigin: WordOrigin = WordOrigin.TYPED,
        /** What [replaces] became when that is more than [word]: a two-word repair. */
        revised: String? = null,
    ): List<Entry> {
        val entry = Entry(
            word, langId, weight, caseTrusted, known, origin, replaces, typed, taps, keys,
            replacesOrigin, revised, pushIndex = ++pushes,
        )
        if (anchor >= 0) entry.anchor = anchor
        entries.addLast(entry)
        if (entries.size <= capacity) return emptyList()
        val overflow = ArrayList<Entry>(entries.size - capacity)
        while (entries.size > capacity) overflow.add(entries.removeFirst())
        return overflow
    }

    /**
     * The caret was reported at [caret]. Returns the entries it dropped.
     *
     * Anchors every entry still waiting for its commit echo, then drops the
     * ones the caret has moved in front of. A collapsed caret is the only kind
     * that anchors: a range selection is a selection, not a resting place, and
     * anchoring to it would leave entries pointing at text about to be
     * replaced.
     */
    fun onCaret(caret: Int): List<Dropped> {
        if (caret < 0 || entries.isEmpty()) return emptyList()
        // Anchors only ever grow, so the newest one answers for all of them:
        // a caret at or past it is ordinary forward typing, which is every
        // keystroke. This runs on the typing path, so that case does no work.
        val newest = entries.last().anchor
        if (newest != UNANCHORED && caret >= newest) return emptyList()
        // Drop first, anchor second: an entry pushed but not yet anchored is
        // the word this very update belongs to, and it must not be dropped by
        // its own echo.
        val dropped = ArrayList<Dropped>()
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.anchor != UNANCHORED && entry.anchor > caret) {
                val d = Dropped(entry.word, entry.anchor, entry.origin, entry.replaces, pushes)
                dropped.add(d)
                remember(d)
                iterator.remove()
            }
        }
        for (entry in entries) {
            if (entry.anchor == UNANCHORED) {
                entry.anchor = caret
                pairWithDropped(entry)
            }
        }
        return dropped
    }

    /**
     * Ties [sample], the glide that committed [word], to the newest copy of
     * the word waiting here, so it settles with the word and never without
     * it. False when nothing is waiting: learning off, or a word the commit
     * did not queue.
     */
    fun attachGlide(word: String, sample: GlideShapeSample): Boolean {
        val key = WordKey.of(word)
        val entry = entries.lastOrNull { WordKey.of(it.word) == key } ?: return false
        entry.glideShape = sample
        return true
    }

    /**
     * Drops [word] outright — the user took the commit back by hand (undoing
     * an autocorrect, re-picking from the strip), which is a statement about
     * the word rather than about the text around it.
     */
    fun drop(word: String) {
        val key = WordKey.of(word)
        entries.removeAll { WordKey.of(it.word) == key }
    }

    /** Everything still queued, emptying the buffer. */
    fun drain(): List<Entry> {
        recent.clear()
        if (entries.isEmpty()) return emptyList()
        val all = entries.toList()
        entries.clear()
        return all
    }

    /** Throws the queue away unsettled — nothing in it counts. */
    fun clear() {
        entries.clear()
        recent.clear()
    }

    private fun remember(d: Dropped) {
        recent.addLast(d)
        while (recent.size > RECENT_CAPACITY) recent.removeFirst()
    }

    /**
     * [entry] has just been anchored: if a recently dropped word ended about
     * where this one does and this one is a slip away from it, this is that
     * word fixed. A caller that already knows what the entry replaced has
     * said so at push, and that knowledge is not overwritten by a guess.
     */
    private fun pairWithDropped(entry: Entry) {
        if (recent.isEmpty() || entry.replaces != null) return
        val iterator = recent.iterator()
        while (iterator.hasNext()) {
            val d = iterator.next()
            if (WordKey.of(d.word) == WordKey.of(entry.word)) continue
            val limit = if (d.word.length >= CorrectionMemory.LONG_WORD_LENGTH) {
                CorrectionMemory.MAX_EDITS_LONG
            } else {
                CorrectionMemory.MAX_EDITS
            }
            if (near(entry.anchor, d.anchor, entry.word.length - d.word.length) &&
                EditOps.distance(d.word, entry.word) <= limit
            ) {
                entry.replaces = d.word
                entry.replacesOrigin = d.origin
                entry.suspect = true
                iterator.remove()
                return
            }
            // A fat-fingered space put back: the word before this one was
            // committed after the drop, and the two together are what the old
            // word was meant to be ("thisbis" → "this" + "is").
            val previous = previousOf(entry) ?: continue
            if (previous.pushIndex <= d.droppedAt || previous.replaces != null) continue
            val joined = previous.word + " " + entry.word
            if (near(entry.anchor, d.anchor, joined.length - d.word.length) &&
                EditOps.distance(d.word, joined) <= limit
            ) {
                entry.replaces = d.word
                entry.replacesOrigin = d.origin
                entry.revised = joined
                entry.suspect = true
                iterator.remove()
                return
            }
        }
    }

    private fun previousOf(entry: Entry): Entry? {
        var previous: Entry? = null
        for (e in entries) {
            if (e === entry) return previous
            previous = e
        }
        return null
    }

    private fun near(anchor: Int, oldAnchor: Int, lengthDelta: Int): Boolean =
        abs(anchor - oldAnchor) <= abs(lengthDelta) + ANCHOR_SLACK

    companion object {
        internal const val UNANCHORED = -1

        /**
         * Words held before the oldest is settled by distance. Long enough to
         * cover a paragraph the user may still scroll back through, short
         * enough that someone writing an essay in one field still teaches the
         * keyboard their vocabulary before they finish.
         *
         * Every committed word waits here since #101, not only the ones no
         * dictionary knows, so this is a whole message's worth rather than a
         * handful of new words — the figure the request asked for by name.
         */
        const val DEFAULT_CAPACITY = 500

        /** Dropped words kept for pairing with whatever replaces them. */
        const val RECENT_CAPACITY = 8

        /**
         * How far a replacement's anchor may sit from the dropped word's,
         * beyond the difference in their lengths. Editors anchor a commit
         * before or after its trailing space depending on how they coalesce
         * echoes, so one character either way is the same place.
         */
        const val ANCHOR_SLACK = 2
    }
}
