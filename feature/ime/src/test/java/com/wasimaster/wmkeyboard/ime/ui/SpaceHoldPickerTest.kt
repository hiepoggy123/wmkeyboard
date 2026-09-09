package com.wasimaster.wmkeyboard.ime.ui

import com.wasimaster.wmkeyboard.core.settings.SpaceSwipeAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who owns a hold on the spacebar (issue #122).
 *
 * The gesture itself needs a composition to drive, but the question that was
 * wrong does not: the hold-to-open language chooser used to arm itself whenever
 * more than one layout was enabled, which meant it fired first and ate the drag
 * belonging to the "press and hold, then swipe" slot. Every case below is one
 * answer to "may the chooser take this hold".
 */
class SpaceHoldPickerTest {

    @Test
    fun `a hold slot set to the cursor keeps the chooser off the hold`() {
        assertFalse(spaceHoldOpensPicker(3, holdOpensAlternates = false, SpaceSwipeAction.CURSOR))
    }

    @Test
    fun `a hold slot set to the numpad keeps the chooser off the hold`() {
        assertFalse(spaceHoldOpensPicker(3, holdOpensAlternates = false, SpaceSwipeAction.NUMPAD))
    }

    @Test
    fun `a hold slot that switches language leaves the chooser its hold`() {
        assertTrue(spaceHoldOpensPicker(3, holdOpensAlternates = false, SpaceSwipeAction.LANGUAGE))
    }

    @Test
    fun `a hold slot set to nothing leaves the chooser its hold`() {
        assertTrue(spaceHoldOpensPicker(3, holdOpensAlternates = false, SpaceSwipeAction.NONE))
    }

    @Test
    fun `authored spacebar keys still beat the chooser`() {
        assertFalse(spaceHoldOpensPicker(3, holdOpensAlternates = true, SpaceSwipeAction.LANGUAGE))
        assertFalse(spaceHoldOpensPicker(3, holdOpensAlternates = true, SpaceSwipeAction.NONE))
    }

    @Test
    fun `one enabled layout has nothing to choose between`() {
        assertFalse(spaceHoldOpensPicker(1, holdOpensAlternates = false, SpaceSwipeAction.LANGUAGE))
        assertFalse(spaceHoldOpensPicker(0, holdOpensAlternates = false, SpaceSwipeAction.NONE))
    }

    @Test
    fun `two layouts are enough`() {
        assertTrue(spaceHoldOpensPicker(2, holdOpensAlternates = false, SpaceSwipeAction.LANGUAGE))
    }
}
