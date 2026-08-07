package com.wasimaster.wmkeyboard.core.tools

import android.view.KeyEvent

/**
 * The physical keyboard's language switch: Ctrl+Space steps forward through the
 * enabled layouts, Ctrl+Shift+Space steps back, and holding Ctrl browses a list
 * that commits on release. Pure like the rest of the hardware engine — only
 * `KeyEvent` integer constants, so everything runs in a plain JVM test.
 *
 * Meta+Space is deliberately absent: that is the system's own IME-rotation
 * chord and must keep reaching the framework.
 */

val LanguageSwitchForward = KeyChord(KeyEvent.KEYCODE_SPACE, ctrl = true)
val LanguageSwitchBackward = KeyChord(KeyEvent.KEYCODE_SPACE, ctrl = true, shift = true)

/**
 * +1 forward, -1 backward, null when this key is not a language-switch stroke.
 * [KeyChord.matches] is exact, so AltGr (Ctrl+Alt) producing a character never
 * reads as a switch, and lock bits are already masked off.
 */
fun languageSwitchDelta(keyCode: Int, metaState: Int): Int? = when {
    LanguageSwitchForward.matches(keyCode, metaState) -> 1
    LanguageSwitchBackward.matches(keyCode, metaState) -> -1
    else -> null
}

/**
 * The first candidate of a browse session, or null when there is nothing to
 * cycle. A current layout missing from the cycle (a per-field override, a
 * freshly disabled layout) starts from whichever end the step points at.
 */
fun languageCycleStart(ids: List<String>, currentId: String, delta: Int): Int? {
    if (ids.size < 2) return null
    val at = ids.indexOf(currentId)
    if (at < 0) return if (delta > 0) 0 else ids.lastIndex
    return (at + delta).mod(ids.size)
}

/** The next candidate, wrapping at both ends. */
fun languageCycleStep(candidate: Int, delta: Int, count: Int): Int =
    (candidate + delta).mod(count)
