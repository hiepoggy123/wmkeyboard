package com.wasimaster.wmkeyboard.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The on-key bubble's floor (#87): a height that would leave the bubble under
 * the key it grows from is raised to the key plus the label's lane, and a
 * height already above that is drawn as set.
 */
class KeyPreviewGeometryTest {

    @Test
    fun `a tall setting is drawn as set`() {
        assertEquals(330, onKeyBubbleHeightPx(settingPx = 330, keyHeightPx = 144, labelLanePx = 160))
    }

    @Test
    fun `a setting under the key rises to the key plus the label lane`() {
        // The floating default (65 dp) handed to a 48 dp key at 3x: hidden
        // under the finger before, a readable bubble now.
        assertEquals(304, onKeyBubbleHeightPx(settingPx = 195, keyHeightPx = 144, labelLanePx = 160))
    }

    @Test
    fun `a setting exactly at the floor is left alone`() {
        assertEquals(304, onKeyBubbleHeightPx(settingPx = 304, keyHeightPx = 144, labelLanePx = 160))
    }

    @Test
    fun `a taller key raises the floor with it`() {
        assertEquals(460, onKeyBubbleHeightPx(settingPx = 330, keyHeightPx = 300, labelLanePx = 160))
    }
}
