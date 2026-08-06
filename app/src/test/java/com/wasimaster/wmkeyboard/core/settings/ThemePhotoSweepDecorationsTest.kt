package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.theme.DecalSpec
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sweep counts key textures and decal images as referenced. Before it
 * did, a texture theme lost its textures a day after it was made — the sweep
 * saw unreferenced files in the theme-images folder and took them.
 */
class ThemePhotoSweepDecorationsTest {

    private val now = 1_700_000_000_000L
    private val old = now - 2 * SWEEP_MIN_AGE_MS

    @Test
    fun `texture and decal files are never swept while a theme refers to them`() {
        val theme = ThemeSpec(
            id = "a",
            name = "a",
            keyTexture = "/data/files/theme_images/a_tex.img",
            keyTextureEnter = "/data/files/theme_images/a_tex_enter.img",
            decals = listOf(
                DecalSpec(id = "d1", image = "/data/files/theme_images/a_decal_d1.img"),
            ),
        )
        val plan = themePhotoSweepPlan(
            themes = listOf(theme),
            rotationStates = emptyMap(),
            imagesOnDisk = listOf(
                SweptFile("a_tex.img", old),
                SweptFile("a_tex_enter.img", old),
                SweptFile("a_decal_d1.img", old),
                SweptFile("orphan_tex.img", old),
            ),
            poolFileNames = emptySet(),
            nowMs = now,
        )
        assertEquals(listOf("orphan_tex.img"), plan.deleteImages)
    }

    @Test
    fun `deleting the theme releases its texture files to the sweep`() {
        val plan = themePhotoSweepPlan(
            themes = emptyList(),
            rotationStates = emptyMap(),
            imagesOnDisk = listOf(SweptFile("a_tex.img", old)),
            poolFileNames = emptySet(),
            nowMs = now,
        )
        assertTrue("a_tex.img" in plan.deleteImages)
    }
}
