package com.wasimaster.wmkeyboard.ime.ui

/**
 * What a lifted glide asks the service to do with the stroke's last word.
 *
 * The ambiguity picker's answer, carried on the existing glide callbacks in
 * place of the old `chosen: String?` — a type change rather than a new
 * [KeyboardScreen] parameter, because `ServiceKeyboardContent` sits against
 * the JVM's 64K method ceiling (see `DeleteSwipeCallbacks` for the same
 * constraint). Three answers rather than a nullable word because the picker
 * has a third thing to say: the finger was dragged down out of it, and a
 * lift there should type nothing at all.
 *
 * [capitals] rides along here for the same reason: a stroke that drew through
 * the shift key is asking for a capital (#115), and that is one more thing the
 * lift has to say about the word rather than one more callback.
 */
sealed interface GlideVerdict {

    /**
     * How many times the stroke crossed the shift key: 0 for an ordinary
     * glide, 1 for a capitalized word, 2 or more for a shouted one — the same
     * ladder tapping shift walks up.
     */
    val capitals: Int get() = 0

    /**
     * No choice was made — the picker never opened, or the finger lifted off
     * it — so the decoder's own first word commits, exactly as a glide with no
     * picker would.
     */
    data class Leader(override val capitals: Int = 0) : GlideVerdict

    /**
     * The finger lifted on a picker target. Committed in place of the
     * decoder's first choice and otherwise treated identically: still learned,
     * still spaced, still revertible, still first on the strip.
     */
    data class Word(val word: String, override val capitals: Int = 0) : GlideVerdict

    /**
     * The finger lifted in the picker's cancel zone. Nothing commits, nothing
     * is learned; the service only retires the stroke's previews.
     */
    data object Cancel : GlideVerdict
}

/**
 * This verdict with [times] shift crossings recorded against it.
 *
 * A cancelled stroke keeps none: it types nothing, so there is nothing to
 * capitalize. Zero crossings return the verdict untouched, which is every
 * ordinary glide and the reason this allocates nothing in the common case.
 */
fun GlideVerdict.withCapitals(times: Int): GlideVerdict = when {
    times <= 0 -> this
    this is GlideVerdict.Leader -> GlideVerdict.Leader(times)
    this is GlideVerdict.Word -> GlideVerdict.Word(word, times)
    else -> this
}
