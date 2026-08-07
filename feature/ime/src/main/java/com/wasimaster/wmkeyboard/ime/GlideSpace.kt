package com.wasimaster.wmkeyboard.ime

/**
 * The two text decisions behind the space a glided word is followed by, pulled
 * out of `WMKeyboardService` so they can be checked without an
 * `InputConnection`. The service does the field reads and the edits; these two
 * only say what the read means.
 */

/**
 * Marks that take back the space a glide typed, so they hug the word they
 * belong to: "hello." and "(hello)", never "hello .".
 *
 * The auto-space marks plus the closers, which is the one place the two sets
 * differ. A closing bracket or quote comes *after* the space in the
 * auto-space-after-punctuation rule's job (spacing what follows a mark) and
 * *before* it in this one (removing what precedes a mark). Openers are absent
 * on purpose — "hello (world)" wants its space kept. So are the CJK wide forms,
 * which no glide produces: the decoder is Latin-only.
 */
private val GLIDE_SPACE_SWALLOWERS =
    charArrayOf('.', '!', '?', '।', ',', ';', ':', ')', ']', '}', '"', '…', '%')

/** Whether typing [text] right after a glided word should take its space back. */
internal fun swallowsGlideSpace(text: String): Boolean =
    text.length == 1 && text[0] in GLIDE_SPACE_SWALLOWERS

/**
 * How many characters the glided [word] occupies at the end of [textBefore] —
 * the word, plus the space the commit typed after it when one is there. 0 when
 * the text does not end in that word at all, which is the signal to leave the
 * field alone.
 *
 * Backspace-undo and the strip's tap-to-replace both have to take the space
 * along with the word: without it an undo leaves a stray gap, and a replacement
 * lands on the far side of it ("hello world" out of one swipe). Measured off
 * the text rather than off a flag, so a handwritten word — which arms the same
 * undo but types no trailing space — comes out right too.
 *
 * [textBefore] is the last `word.length + 1` characters before the caret, which
 * is all either caller needs to read.
 */
internal fun glideCommitLength(textBefore: String, word: String): Int = when {
    word.isEmpty() -> 0
    textBefore.endsWith("$word ") -> word.length + 1
    textBefore.endsWith(word) -> word.length
    else -> 0
}
