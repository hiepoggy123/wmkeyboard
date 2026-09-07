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
 */
sealed interface GlideVerdict {

    /**
     * No choice was made — the picker never opened, or the finger lifted off
     * it — so the decoder's own first word commits, exactly as a glide with no
     * picker would.
     */
    data object Leader : GlideVerdict

    /**
     * The finger lifted on a picker target. Committed in place of the
     * decoder's first choice and otherwise treated identically: still learned,
     * still spaced, still revertible, still first on the strip.
     */
    data class Word(val word: String) : GlideVerdict

    /**
     * The finger lifted in the picker's cancel zone. Nothing commits, nothing
     * is learned; the service only retires the stroke's previews.
     */
    data object Cancel : GlideVerdict
}
