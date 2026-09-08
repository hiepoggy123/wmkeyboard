package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.geometry.Offset
import com.wasimaster.wmkeyboard.core.prediction.OctopusWord

/**
 * Where each floating word ended up, for the pointer loop to hit-test against.
 *
 * Plain fields rather than snapshot state, for the reason [GlidePickerState]
 * gives about its own rectangles: this is written by the overlay's layout and
 * read from a pointer handler at touch-report rate, and a rectangle that lived
 * in snapshot state would recompose the keyboard every time a finger moved.
 *
 * The overlay is not itself touchable — a word that took presses would swallow
 * the key underneath — so both the tap and the flick come through here.
 */
internal class OctopusRects {

    private var slots: List<OctopusSlot> = emptyList()

    /**
     * Stamped with the layout the rectangles were measured against, so a
     * generation behind is read as nothing rather than as the old positions.
     * The trap [KeyRects] documents: a stale table hit-tests a finger against a
     * board that is no longer on the screen.
     */
    private var token: Int = -1

    fun publish(list: List<OctopusSlot>, gridToken: Int = 0) {
        slots = list
        token = gridToken
    }

    fun clear() {
        slots = emptyList()
    }

    /** Whether anything is floating at all — the gate the flick arms on. */
    fun isEmpty(): Boolean = slots.isEmpty()

    /**
     * The word drawn under [point], or null. Walks best-first, so where two
     * words overlap the likelier one wins — which is the right bias for a
     * target the user aimed at by eye.
     */
    fun wordAt(point: Offset, gridToken: Int = 0): OctopusWord? {
        if (token != gridToken) return null
        return slots.firstOrNull { it.hit.width > 0f && it.hit.contains(point) }?.word
    }

    /** The word floating over the key anchored at [keyCodePoint], or null. */
    fun wordFor(keyCodePoint: Int, gridToken: Int = 0): OctopusWord? {
        if (token != gridToken) return null
        return slots.firstOrNull { it.word.keyCodePoint == keyCodePoint }?.word
    }
}
