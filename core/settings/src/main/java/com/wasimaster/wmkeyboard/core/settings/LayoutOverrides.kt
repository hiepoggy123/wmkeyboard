package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.layout.LayoutAppearance
import com.wasimaster.wmkeyboard.core.layout.drawnFontScale

/**
 * Lays the active layout's own label size over the settings the rest of the
 * board is already using, the way [applyThemeOverrides] lays the theme's over
 * them.
 *
 * Ordering contract: this runs **last**, after [resolvedFor], and it is the one
 * step in the chain that multiplies rather than replaces. Every other overlay is
 * a more specific *answer* to the same question — this key height, on this
 * screen, in this theme — and the last one to speak wins. A layout's label size
 * is a different kind of statement: "whatever size you settled on, this grid
 * wants a fifth less of it". Replacing would throw away an accessibility
 * setting the moment the user imported a layout, which is exactly the direction
 * that must not fail silently.
 *
 * Only [KeyboardSettings.fontScale] moves, and only the key labels read it —
 * the suggestion strip and the panels have sizes of their own — so this cannot
 * resize anything that is not part of the grid the layout describes.
 *
 * The layout's font is *not* handled here. It resolves against the per-script
 * face and the theme's, which is a chain `KeyboardFonts` owns and one that a
 * single settings field cannot express; the keyboard theme takes it as its own
 * parameter instead.
 */
fun KeyboardSettings.applyLayoutAppearance(appearance: LayoutAppearance?): KeyboardSettings {
    val scale = appearance.drawnFontScale()
    // Instance-stable for every layout that asks for nothing, which is all of
    // the shipped ones: the caller remembers on the result.
    if (scale == 1f) return this
    return copy(fontScale = fontScale * scale)
}
