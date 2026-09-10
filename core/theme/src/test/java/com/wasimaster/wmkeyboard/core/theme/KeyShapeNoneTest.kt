package com.wasimaster.wmkeyboard.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The key shape that is no shape (issue #149): stored like any other key shape,
 * refused by every surface that names its shape as a string, and drawn as the
 * plain rounded outline wherever a shapeless key still has to light up.
 */
class KeyShapeNoneTest {

    @Test
    fun `a theme with no key shape survives a round trip`() {
        val bare = ThemeSpec(id = "custom_1", name = "Bare", keyShape = KeyShapeKind.NONE)
        assertEquals(KeyShapeKind.NONE, ThemeCodec.decode(ThemeCodec.encode(bare))?.keyShape)
    }

    /** The issue asked for None at the top of the list, and the picker lists the entries in order. */
    @Test
    fun `the picker lists no shape first`() {
        assertEquals(KeyShapeKind.NONE, KeyShapeKind.entries.first())
    }

    /**
     * Popups, menus, tools, chips and cards name their shape as a string. One
     * with no outline at all would be its text floating over the board, so the
     * name falls back the way a shape from a later build does.
     */
    @Test
    fun `no shape is for keys only`() {
        assertNull(keyShapeKindOrNull("NONE"))
        assertEquals(KeyShapeKind.ROUNDED, keyShapeKindOrNull("ROUNDED"))
        assertEquals(KeyShapeKind.ROUNDED, safeContainerKind(KeyShapeKind.NONE))
    }

    @Test
    fun `a key with no shape lights up in the rounded outline`() {
        assertEquals(RoundedCornerShape(8.dp), keyShapeFor(KeyShapeKind.NONE, 8))
        assertTrue(castsElevationShadow(KeyShapeKind.NONE))
    }
}
