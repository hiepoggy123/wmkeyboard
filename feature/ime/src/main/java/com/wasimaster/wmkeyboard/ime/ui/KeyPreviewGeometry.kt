package com.wasimaster.wmkeyboard.ime.ui

/**
 * The height an on-key preview bubble is drawn at, in px.
 *
 * An on-key bubble is measured from the bottom of the key it covers, so the
 * first key-height of it is spent under the finger and only the rest rises into
 * view. A height at or under the key's own is therefore a bubble nobody sees,
 * which is what the global slider's floor (32 dp) and a theme's single popup
 * height, seeded while the bubble was floating (65 dp), both asked for (#87).
 * The bubble is floored at the key plus [labelLanePx], the room its label needs
 * above the finger, so the setting still sizes it and can no longer hide it.
 */
internal fun onKeyBubbleHeightPx(settingPx: Int, keyHeightPx: Int, labelLanePx: Int): Int =
    maxOf(settingPx, keyHeightPx + labelLanePx)
