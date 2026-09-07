package com.wasimaster.wmkeyboard.core.prediction

/**
 * One word the user has gone back into, followed until they leave it, so the
 * keyboard knows what it was and what it became.
 *
 * A manual fix is invisible to every store the keyboard keeps. The word was
 * committed long ago; the caret comes back; letters go and letters come; the
 * caret leaves. Nothing in that sequence is a "commit" of the fixed word, and
 * the one thing worth learning — that "teh" became "the" — is exactly the
 * pair of spellings no single event carries. So the service keeps one of
 * these from the moment the caret lands in a word until it leaves, and asks
 * it at the end.
 *
 * Two modes, one tracker:
 *
 * * [Mode.COMPOSING] — the caret landed at the word's end and the service
 *   re-armed the word as its composing buffer. The buffer *is* the word, so
 *   nothing is mirrored here; whoever ends the composition hands the buffer
 *   to [finish]. Caret echoes are ignored: the composition's own rules decide
 *   when it is over.
 * * [Mode.FIELD] — the caret landed *inside* the word, which no composing
 *   region can hold (see the service's `caretWord`). The service then edits
 *   the field directly, and every edit it issues is mirrored here before the
 *   editor echoes it back ([expectInsert], [expectDelete],
 *   [expectReplaceBefore]). [onCaret] checks each echo against the mirror: a
 *   caret where the mirror said it would be is confirmation
 *   ([Outcome.MATCH]); any other caret is the user's — moving inside the word
 *   ([Outcome.MOVED], the cursor follows and the mirror carries on) or
 *   leaving it ([Outcome.LEFT]). An echo the mirror did not predict is not
 *   fatal, because the mirror is never the last word: the caller verifies it
 *   against the field when the word is left, and falls back to what the field
 *   holds at [start] when they disagree. That read is what makes editors that
 *   swallow echoes (web views) and edits that bypass the keyboard (a hardware
 *   arrow key) safe rather than blinding.
 *
 * Pure and JVM-testable; the service owns at most one at a time.
 */
class WordRevision(
    val original: String,
    /** Offset of the word's first character in the field. */
    val start: Int,
    cursor: Int,
    mode: Mode,
) {

    enum class Mode { COMPOSING, FIELD }

    enum class Outcome { MATCH, MOVED, LEFT }

    var mode: Mode = mode
        private set

    private val buffer = StringBuilder(original)

    /** The caret's offset inside the word, 0..[length]. */
    var cursor: Int = cursor.coerceIn(0, original.length)
        private set

    /** Where the next echo must land, or -1 with no edit outstanding. */
    private var expectedCaret = -1

    val length: Int get() = buffer.length

    /** Offset just past the word's last character, as the mirror has it. */
    val end: Int get() = start + buffer.length

    /** Whether an edit of ours is still waiting for its echo. */
    val pending: Boolean get() = expectedCaret >= 0

    fun current(): String = buffer.toString()

    /** Whether this tracker is the word standing at [start]..[start]+[word].length. */
    fun covers(wordStart: Int, word: String): Boolean =
        wordStart == start && buffer.length == word.length && buffer.toString() == word

    /**
     * The word was re-armed as the composing buffer while this tracker was
     * following it in the field: keep the original, hand over to the buffer.
     */
    fun toComposing() {
        mode = Mode.COMPOSING
        expectedCaret = -1
        cursor = buffer.length
    }

    /** We inserted [text] at the cursor (commitText, or a fragment's first char). */
    fun expectInsert(text: String) {
        if (mode != Mode.FIELD) return
        buffer.insert(cursor, text)
        cursor += text.length
        expectedCaret = start + cursor
    }

    /** We deleted [before] characters behind the cursor and [after] ahead of it. */
    fun expectDelete(before: Int, after: Int) {
        if (mode != Mode.FIELD) return
        val b = before.coerceIn(0, cursor)
        val a = after.coerceIn(0, buffer.length - cursor)
        buffer.delete(cursor - b, cursor + a)
        cursor -= b
        expectedCaret = start + cursor
    }

    /**
     * We replaced the [oldLength] characters behind the cursor with [text] —
     * a composing fragment growing or shrinking by `setComposingText`, which
     * rewrites the whole fragment each time.
     */
    fun expectReplaceBefore(oldLength: Int, text: String) {
        if (mode != Mode.FIELD) return
        val old = oldLength.coerceIn(0, cursor)
        buffer.replace(cursor - old, cursor, text)
        cursor = cursor - old + text.length
        expectedCaret = start + cursor
    }

    /**
     * The editor reported a collapsed caret at [caret].
     *
     * A caret past the mirror's end by up to [LEAVE_SLACK] still counts as
     * inside: a fragment commit and its trailing space can echo as one, and
     * the separator the caller finalises on has its own hook.
     */
    fun onCaret(caret: Int): Outcome {
        if (mode == Mode.COMPOSING) return Outcome.MATCH
        if (expectedCaret >= 0 && caret == expectedCaret) {
            expectedCaret = -1
            return Outcome.MATCH
        }
        expectedCaret = -1
        if (caret < start || caret > end + LEAVE_SLACK) return Outcome.LEFT
        cursor = (caret - start).coerceIn(0, buffer.length)
        return Outcome.MOVED
    }

    /**
     * The user is done with the word. [text] is the composing buffer in
     * [Mode.COMPOSING] (the mirror is not kept there); the mirror otherwise.
     * Null when nothing changed, when the word was deleted outright, or when
     * only its case did — the case memory owns capitals.
     */
    fun finish(text: String? = null): Revision? {
        val revised = text ?: buffer.toString()
        if (revised.isEmpty() || revised.isBlank()) return null
        if (WordKey.of(revised) == WordKey.of(original)) return null
        return Revision(original, revised, start + revised.length)
    }

    companion object {
        /** Characters past the mirrored end a caret may sit and still be "in" the word. */
        const val LEAVE_SLACK = 1
    }
}

/** What a [WordRevision] saw: [original] became [revised], ending at [anchor]. */
class Revision(val original: String, val revised: String, val anchor: Int)
