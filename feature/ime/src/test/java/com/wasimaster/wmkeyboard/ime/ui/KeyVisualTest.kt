package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.graphics.Color
import com.wasimaster.wmkeyboard.core.icons.IconSlots
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.compile
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.ime.EnterAction
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.LayoutSet
import com.wasimaster.wmkeyboard.ime.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The keys are handed a resolved [KeyVisual] instead of the whole
 * [KeyboardUiState] so that a keystroke — which publishes a fresh state carrying
 * new suggestions and a new composing preview — leaves every key's parameters
 * untouched, and all ~40 key bodies skip recomposition instead of re-running.
 *
 * That only holds while nothing per-keystroke leaks into the resolution, which
 * is a one-line mistake to make. These tests are the guard: the first one fails
 * the moment a key starts reading something a keypress changes.
 */
class KeyVisualTest {

    private val palette = KeyPalette(
        key = Color.White,
        keyText = Color.Black,
        modifierKey = Color.Gray,
        modifierKeyText = Color.DarkGray,
        enterKey = Color.Blue,
        enterKeyText = Color.Yellow,
        pressedKey = Color.LightGray,
        accent = Color.Magenta,
    )

    private fun state(settings: KeyboardSettings = KeyboardSettings()) = KeyboardUiState(
        settings = settings,
        layouts = LayoutSet(
            BuiltInLayouts.QWERTY.compile(LayoutLayer.LETTERS),
            BuiltInLayouts.QWERTY.compile(LayoutLayer.SYMBOLS),
            BuiltInLayouts.QWERTY.compile(LayoutLayer.SYMBOLS_SHIFTED),
        ),
    )

    /** Every key of the letters layer, resolved. */
    private fun visuals(state: KeyboardUiState) =
        currentLayout(state).rows.flatten().map { keyVisual(it, state, palette) }

    private fun keyLabelled(state: KeyboardUiState, label: String) =
        keyVisual(Key(label), state, palette)

    @Test
    fun `a keystroke changes nothing any key draws`() {
        val before = state()
        // What the service publishes on a keypress: a word committed to the
        // composing buffer, fresh suggestions, a fresh next-letter distribution.
        val after = before.copy(
            suggestions = listOf("hello", "help", "held"),
            composingPreview = "hel",
            nextLetterBias = mapOf('l' to 0.8f, 'p' to 0.3f),
            glideWord = "hello",
            expandedCandidates = listOf("hello"),
        )
        assertNotEquals(before, after)
        assertEquals(visuals(before), visuals(after))
    }

    /**
     * The counterweight to the test above: if resolution ignored the state
     * outright the first test would pass vacuously, so check that the things a
     * key *does* track still come through.
     */
    @Test
    fun `shift recases the letter labels`() {
        val off = state()
        val on = off.copy(shiftState = ShiftState.ON)
        assertEquals("q", keyLabelled(off, "q").label)
        assertEquals("Q", keyLabelled(on, "q").label)
        assertNotEquals(visuals(off), visuals(on))
    }

    @Test
    fun `the shift key's own icon and spoken name track the shift state`() {
        val shift = Key("⇧", action = KeyAction.Shift)
        val off = keyVisual(shift, state(), palette)
        assertEquals(IconSlots.KEY_SHIFT, off.iconSlot)
        assertEquals("Shift", off.spoken)
        assertEquals(false, off.iconActive)

        val locked = keyVisual(shift, state().copy(shiftState = ShiftState.CAPS_LOCK), palette)
        assertEquals(IconSlots.KEY_SHIFT_LOCK, locked.iconSlot)
        assertEquals("Caps lock on", locked.spoken)
        assertEquals(true, locked.iconActive)
    }

    /**
     * An app-supplied actionLabel is drawn as wording, not an icon — that is the
     * whole point of it, and no icon can stand in for what the app chose.
     */
    @Test
    fun `an app-supplied enter label wins over the enter icon`() {
        val enter = Key("⏎", action = KeyAction.Enter)
        val custom = keyVisual(
            enter,
            state().copy(enterAction = EnterAction.CUSTOM, enterActionLabel = "Post"),
            palette,
        )
        assertEquals("Post", custom.enterLabel)

        val search = keyVisual(enter, state().copy(enterAction = EnterAction.SEARCH), palette)
        assertNull(search.enterLabel)
        assertEquals(IconSlots.KEY_ENTER_SEARCH, search.iconSlot)
        assertEquals("Search", search.spoken)
    }

    /**
     * Enter is the one key whose label colour flips under the finger: its own
     * text colour is picked for its accented face, which the press paints over.
     */
    @Test
    fun `only the enter key recolours its label on press`() {
        val enter = keyVisual(Key("⏎", action = KeyAction.Enter), state(), palette)
        assertEquals(palette.enterKeyText, enter.contentColor)
        assertEquals(palette.modifierKeyText, enter.pressedContentColor)

        val letter = keyLabelled(state(), "q")
        assertEquals(letter.contentColor, letter.pressedContentColor)
    }
}
