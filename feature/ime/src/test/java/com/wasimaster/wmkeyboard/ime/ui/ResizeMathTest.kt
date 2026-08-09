package com.wasimaster.wmkeyboard.ime.ui

import com.wasimaster.wmkeyboard.core.settings.ScreenVariant
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.ime.SizingAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inline resize tool's drag math and, above all, its Done semantics: a
 * field the user did not drag must reach the repository as null, because a
 * written key height permanently disables the tablet default heights
 * (`keyHeightUntouched`). These pin that contract.
 */
class ResizeMathTest {

    private val entry = ResizeValues(keyHeightDp = 48, numberRowHeightDp = 42, bottomPaddingDp = 8)

    // ---- height drag ----

    @Test
    fun `unit frac returns the start values`() {
        assertEquals(entry, resizeScaledHeights(entry, 1f))
    }

    @Test
    fun `frac scales both heights and keeps their ratio`() {
        val half = resizeScaledHeights(entry, 0.75f)
        assertEquals(36, half.keyHeightDp)
        assertEquals(32, half.numberRowHeightDp) // round(42 * .75)
        // The untouched axis rides along unchanged.
        assertEquals(entry.bottomPaddingDp, half.bottomPaddingDp)
    }

    @Test
    fun `heights clamp to the slider range`() {
        val floor = resizeScaledHeights(entry, 0.1f)
        assertEquals(SettingsRepository.KEY_HEIGHT_MIN_DP, floor.keyHeightDp)
        assertEquals(SettingsRepository.KEY_HEIGHT_MIN_DP, floor.numberRowHeightDp)
        val ceiling = resizeScaledHeights(entry, 10f)
        assertEquals(SettingsRepository.KEY_HEIGHT_MAX_DP, ceiling.keyHeightDp)
        assertEquals(SettingsRepository.KEY_HEIGHT_MAX_DP, ceiling.numberRowHeightDp)
    }

    @Test
    fun `height limit flag matches the clamp`() {
        assertFalse(resizeHeightAtLimit(entry))
        assertTrue(resizeHeightAtLimit(resizeScaledHeights(entry, 0.1f)))
        assertTrue(resizeHeightAtLimit(resizeScaledHeights(entry, 10f)))
    }

    // ---- padding drag ----

    @Test
    fun `finger down sinks the keyboard and finger up lifts it`() {
        val max = SettingsRepository.MAX_BOTTOM_PADDING_DP
        assertEquals(0, resizePaddedBy(entry, dyDp = 20, maxPad = max).bottomPaddingDp)
        assertEquals(28, resizePaddedBy(entry, dyDp = -20, maxPad = max).bottomPaddingDp)
    }

    @Test
    fun `padding clamps to its bounds and flags the limit`() {
        val max = SettingsRepository.MAX_BOTTOM_PADDING_DP
        val floor = resizePaddedBy(entry, dyDp = 999, maxPad = max)
        assertEquals(0, floor.bottomPaddingDp)
        assertTrue(resizePadAtLimit(floor, max))
        val ceiling = resizePaddedBy(entry, dyDp = -999, maxPad = max)
        assertEquals(max, ceiling.bottomPaddingDp)
        assertTrue(resizePadAtLimit(ceiling, max))
        assertFalse(resizePadAtLimit(entry, max))
    }

    // ---- Done semantics ----

    @Test
    fun `no drag at all commits as a cancel`() {
        assertEquals(
            SizingAction.ResizeCancel,
            resizeCommitAction(ScreenVariant.PORTRAIT, entry, result = null),
        )
    }

    @Test
    fun `padding-only session leaves both heights null`() {
        val action = resizeCommitAction(
            ScreenVariant.PORTRAIT,
            entry,
            entry.copy(bottomPaddingDp = 40),
        ) as SizingAction.ResizeCommit
        assertNull(action.keyHeightDp)
        assertNull(action.numberRowHeightDp)
        assertEquals(40, action.bottomPaddingDp)
    }

    @Test
    fun `height-only session leaves the padding null`() {
        val action = resizeCommitAction(
            ScreenVariant.LANDSCAPE,
            entry,
            resizeScaledHeights(entry, 0.75f),
        ) as SizingAction.ResizeCommit
        assertEquals(ScreenVariant.LANDSCAPE, action.variant)
        assertEquals(36, action.keyHeightDp)
        assertEquals(32, action.numberRowHeightDp)
        assertNull(action.bottomPaddingDp)
    }

    @Test
    fun `dragged away and back commits nothing`() {
        val action = resizeCommitAction(ScreenVariant.PORTRAIT, entry, entry)
            as SizingAction.ResizeCommit
        assertNull(action.keyHeightDp)
        assertNull(action.numberRowHeightDp)
        assertNull(action.bottomPaddingDp)
    }
}
