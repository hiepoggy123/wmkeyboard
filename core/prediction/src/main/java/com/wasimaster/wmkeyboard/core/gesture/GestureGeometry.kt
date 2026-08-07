package com.wasimaster.wmkeyboard.core.gesture

/**
 * A sampled touch point of a gesture, in keyboard-view pixel coordinates.
 *
 * [t] is the sample's event time in uptime milliseconds. Defaults to 0 for
 * callers that have no clock, which is every synthetic path in a test that does
 * not care about timing.
 */
data class GesturePoint(val x: Float, val y: Float, val t: Long = 0L)

/**
 * One character the letter grid can produce, at the centre of the key that
 * produces it, in the same coordinate space as [GesturePoint].
 *
 * Several entries may share a centre, and normally do: a key carries its base
 * character, its shifted one and whatever its long press holds, and a finger
 * crossing it cannot say which was meant. [GlideKeyMap] is what collapses them.
 */
data class KeyCenter(val char: Char, val x: Float, val y: Float)
