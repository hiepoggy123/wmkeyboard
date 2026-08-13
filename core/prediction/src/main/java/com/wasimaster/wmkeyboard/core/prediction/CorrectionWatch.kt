package com.wasimaster.wmkeyboard.core.prediction

/**
 * Autocorrects that have fired but whose verdict is not in yet.
 *
 * Autocorrect used to learn from exactly one gesture: backspace, pressed
 * immediately, before the caret moved or another key landed. Every other way a
 * correction gets rejected was invisible to it. Noticing three words later and
 * tapping back, selecting the word and retyping, fixing the sentence before
 * sending, giving up and rewording. Worse, all of those still counted as
 * successes, so the adaptive gate read a better hit rate than the keyboard was
 * actually achieving and loosened when it should have tightened.
 *
 * This holds each correction until the text around it stops moving, and then
 * the caller asks the field which word won.
 *
 * The settle rule is [LearningBuffer]'s, with one deliberate difference. There,
 * a caret that goes back in front of an entry *drops* it, because a word the
 * user returned to is no longer evidence of anything. Here it only *marks* it
 * ([Entry.disturbed]), because going back is the interesting case rather than
 * the disqualifying one. Marking is generous on purpose: adding a comma three
 * words earlier disturbs everything after it, and the caller resolves that by
 * reading the field once at the flush, which is rare enough to afford.
 */
class CorrectionWatch(private val capacity: Int = DEFAULT_CAPACITY) {

    /** One fired correction: [typed] is what the user wrote, [corrected] what
     * the keyboard put in its place. */
    class Entry internal constructor(
        val typed: String,
        val corrected: String,
    ) {
        internal var anchor: Int = UNANCHORED

        /**
         * Whether the caret has been in front of this correction since it
         * landed. False means the user typed straight past it and never looked
         * back, which is an accept with no field read needed.
         */
        var disturbed: Boolean = false
            internal set
    }

    private val entries = ArrayDeque<Entry>()

    val size: Int get() = entries.size

    fun isEmpty(): Boolean = entries.isEmpty()

    /**
     * Queues a correction that has just landed in the field. Returns whatever
     * fell out of the far end of the window, which has settled by distance.
     */
    fun push(typed: String, corrected: String): List<Entry> {
        entries.addLast(Entry(typed, corrected))
        if (entries.size <= capacity) return emptyList()
        val overflow = ArrayList<Entry>(entries.size - capacity)
        while (entries.size > capacity) overflow.add(entries.removeFirst())
        return overflow
    }

    /**
     * The caret was reported at [caret]: anchor whatever is waiting for its
     * commit echo, and mark anything the caret has moved in front of.
     */
    fun onCaret(caret: Int) {
        if (caret < 0 || entries.isEmpty()) return
        // Anchors only ever grow, so the newest answers for all of them. This
        // runs on the typing path, and ordinary forward typing does no work.
        val newest = entries.last().anchor
        if (newest != UNANCHORED && caret >= newest) return
        for (entry in entries) {
            when {
                entry.anchor == UNANCHORED -> entry.anchor = caret
                entry.anchor > caret -> entry.disturbed = true
            }
        }
    }

    /**
     * Forgets the correction of [typed] into [corrected]: the immediate
     * backspace undo caught it, and that path records the rejection itself.
     * Counting it again at the flush would double it.
     */
    fun drop(typed: String, corrected: String) {
        val t = WordKey.of(typed)
        val c = WordKey.of(corrected)
        entries.removeAll { WordKey.of(it.typed) == t && WordKey.of(it.corrected) == c }
    }

    /** Everything still queued, emptying the watch. */
    fun drain(): List<Entry> {
        if (entries.isEmpty()) return emptyList()
        val all = entries.toList()
        entries.clear()
        return all
    }

    /** Throws the queue away unjudged. */
    fun clear() {
        entries.clear()
    }

    companion object {
        internal const val UNANCHORED = -1

        /**
         * Corrections held before the oldest is judged by distance. Far
         * shorter than [LearningBuffer]'s window: a disturbed entry is checked
         * against the text the caller can still read back, and there is no
         * point holding corrections that have scrolled out of reach of that
         * read.
         */
        const val DEFAULT_CAPACITY = 24
    }
}
