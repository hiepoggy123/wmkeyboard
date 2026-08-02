package com.wasimaster.wmkeyboard.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Colour picking and the readability rules, as plain arithmetic over pixels.
 * No Bitmap and no Context, which is what lets any of this be checked at all.
 */
class PhotoPaletteTest {

    private val black = 0xFF000000L
    private val white = 0xFFFFFFFFL

    private fun solid(argb: Int, width: Int = 8, height: Int = 8) =
        PixelGrid(IntArray(width * height) { argb }, width, height)

    /** Top half [top], bottom half [bottom]. */
    private fun split(top: Int, bottom: Int, size: Int = 8) = PixelGrid(
        IntArray(size * size) { if (it < size * size / 2) top else bottom },
        size,
        size,
    )

    // ---- seed colours -------------------------------------------------

    @Test
    fun `a solid photo gives back its own colour`() {
        val seeds = seedsFrom(solid(0xFFE53935.toInt()))
        assertEquals(1, seeds.size)
        // Quantised to 5 bits a channel, so near rather than exact.
        val seed = seeds.single()
        assertTrue(seed.toString(16), ((seed ushr 16) and 0xFF) > 0xD0)
        assertTrue(seed.toString(16), ((seed ushr 8) and 0xFF) < 0x50)
    }

    @Test
    fun `a colourful subject beats a large flat surround`() {
        // Seven eighths dull grey, one eighth vivid blue.
        val pixels = IntArray(64) { if (it < 56) 0xFF808080.toInt() else 0xFF1E88E5.toInt() }
        val seeds = seedsFrom(PixelGrid(pixels, 8, 8))
        val first = seeds.first()
        assertTrue(first.toString(16), (first and 0xFFL) > ((first ushr 16) and 0xFFL))
    }

    @Test
    fun `a grey photo still gives something rather than nothing`() {
        // Fog, snow and night skies are real photos; returning no colours at
        // all would leave the palette dialog empty for them.
        val seeds = seedsFrom(split(0xFF9E9E9E.toInt(), 0xFF6E6E6E.toInt()))
        assertTrue(seeds.isNotEmpty())
    }

    @Test
    fun `swatches are not six shades of one colour`() {
        val pixels = IntArray(64) { index ->
            when (index % 4) {
                0 -> 0xFFE53935.toInt()
                1 -> 0xFFE64A3E.toInt() // almost the first
                2 -> 0xFF1E88E5.toInt()
                else -> 0xFF43A047.toInt()
            }
        }
        val seeds = seedsFrom(PixelGrid(pixels, 8, 8))
        // The near-duplicate red is dropped; three distinct colours remain.
        assertEquals(3, seeds.size)
    }

    @Test
    fun `a fully transparent photo yields no colours`() {
        assertTrue(seedsFrom(solid(0x00FFFFFF)).isEmpty())
    }

    @Test
    fun `the same photo always gives the same colours`() {
        val grid = split(0xFFE53935.toInt(), 0xFF1E88E5.toInt())
        assertEquals(seedsFrom(grid), seedsFrom(grid))
    }

    // ---- luminance and the key band -----------------------------------

    @Test
    fun `luminance runs from black to white`() {
        assertEquals(0f, luminanceOf(black), 0.001f)
        assertEquals(1f, luminanceOf(white), 0.001f)
        // Green carries most of the perceived brightness.
        assertTrue(luminanceOf(0xFF00FF00) > luminanceOf(0xFFFF0000))
        assertTrue(luminanceOf(0xFFFF0000) > luminanceOf(0xFF0000FF))
    }

    @Test
    fun `the band under the keys ignores the strip above them`() {
        // Bright top, dark bottom. The keys sit in the dark half, and reading
        // the whole image would call this photo mid-bright.
        val grid = split(0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        assertTrue(regionLuminance(grid, top = 0.5f) < 0.05f)
        assertTrue(regionLuminance(grid, top = 0f) > 0.4f)
    }

    @Test
    fun `variation separates a flat photo from a busy one`() {
        assertEquals(0f, regionVariation(solid(0xFF808080.toInt()), top = 0f), 0.001f)
        val busy = PixelGrid(
            IntArray(64) { if (it % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() },
            8,
            8,
        )
        assertTrue(regionVariation(busy, top = 0f) > 0.4f)
    }

    // ---- contrast -----------------------------------------------------

    @Test
    fun `contrast is the WCAG ratio`() {
        assertEquals(21f, contrastRatio(1f, 0f), 0.01f)
        assertEquals(1f, contrastRatio(0.5f, 0.5f), 0.01f)
        // Order does not matter.
        assertEquals(contrastRatio(0.8f, 0.1f), contrastRatio(0.1f, 0.8f), 0.001f)
    }

    @Test
    fun `white text over a white photo reads as unreadable`() {
        val verdict = readabilityOver(
            photoLuminance = 1f,
            photoVariation = 0f,
            imageOpacity = 1f,
            boardArgb = 0x00000000,
            keyBackgroundArgb = 0x00000000,
            keyTextArgb = white,
            blur = 0f,
        )
        assertTrue(verdict.poor)
        assertFalse(verdict.good)
    }

    @Test
    fun `white text over a dark photo reads clearly`() {
        val verdict = readabilityOver(
            photoLuminance = 0.02f,
            photoVariation = 0.05f,
            imageOpacity = 1f,
            boardArgb = 0x00000000,
            keyBackgroundArgb = 0x00000000,
            keyTextArgb = white,
            blur = 0f,
        )
        assertTrue(verdict.good)
    }

    @Test
    fun `an opaque board hides the photo entirely`() {
        // Nothing about the photo can matter once the board is solid over it.
        val verdict = readabilityOver(
            photoLuminance = 1f,
            photoVariation = 1f,
            imageOpacity = 1f,
            boardArgb = 0xFF000000,
            keyBackgroundArgb = 0x00000000,
            keyTextArgb = white,
            blur = 0f,
        )
        assertTrue(verdict.good)
        assertEquals(0f, verdict.variation, 0.001f)
    }

    @Test
    fun `blur settles a busy photo`() {
        fun variationAt(blur: Float) = readabilityOver(
            photoLuminance = 0.2f,
            photoVariation = 0.9f,
            imageOpacity = 1f,
            boardArgb = 0x00000000,
            keyBackgroundArgb = 0x00000000,
            keyTextArgb = white,
            blur = blur,
        ).variation
        assertTrue(variationAt(0f) > variationAt(8f))
        assertTrue(variationAt(8f) > variationAt(25f))
    }

    @Test
    fun `a detailed photo behind the keys is called busy`() {
        val verdict = readabilityOver(
            photoLuminance = 0.02f,
            photoVariation = 0.9f,
            imageOpacity = 1f,
            boardArgb = 0x00000000,
            keyBackgroundArgb = 0x00000000,
            keyTextArgb = white,
            blur = 0f,
        )
        assertTrue(verdict.busy)
        assertFalse(verdict.good)
    }

    // ---- the scrim ----------------------------------------------------

    @Test
    fun `no scrim is asked for when the keys already read`() {
        val alpha = scrimAlphaFor(
            photoLuminance = 0.02f,
            imageOpacity = 1f,
            boardArgb = 0x00000000,
            keyBackgroundArgb = 0x00000000,
            keyTextArgb = white,
        )
        assertEquals(0f, alpha, 0.001f)
    }

    @Test
    fun `a bright photo under white text asks for a dark scrim`() {
        val alpha = scrimAlphaFor(
            photoLuminance = 1f,
            imageOpacity = 1f,
            boardArgb = 0x00000000,
            keyBackgroundArgb = 0x00000000,
            keyTextArgb = white,
        )
        assertTrue(alpha.toString(), alpha > 0f)
        // Never opaque: the user did ask for a photo.
        assertTrue(alpha.toString(), alpha <= Readability.MAX_SCRIM_ALPHA)
    }

    @Test
    fun `a brighter photo needs at least as much scrim`() {
        fun alphaAt(luminance: Float) = scrimAlphaFor(
            photoLuminance = luminance,
            imageOpacity = 1f,
            boardArgb = 0x00000000,
            keyBackgroundArgb = 0x00000000,
            keyTextArgb = white,
        )
        assertTrue(alphaAt(0.9f) >= alphaAt(0.5f))
        assertTrue(alphaAt(0.5f) >= alphaAt(0.05f))
    }

    @Test
    fun `the scrim it asks for actually fixes the contrast`() {
        val board = 0x00000000L
        val alpha = scrimAlphaFor(
            photoLuminance = 0.8f,
            imageOpacity = 1f,
            boardArgb = board,
            keyBackgroundArgb = 0x00000000,
            keyTextArgb = white,
        )
        val after = readabilityOver(
            photoLuminance = 0.8f,
            photoVariation = 0f,
            imageOpacity = 1f,
            boardArgb = board.withAlphaFraction(alpha),
            keyBackgroundArgb = 0x00000000,
            keyTextArgb = white,
            blur = 0f,
        )
        assertFalse(after.poor)
        assertFalse(after.marginal)
    }

    @Test
    fun `a light theme scrims toward its own board colour`() {
        // A white board over a dark photo, with dark text: the fix is to
        // lighten, and the same search finds it.
        val alpha = scrimAlphaFor(
            photoLuminance = 0.02f,
            imageOpacity = 1f,
            boardArgb = 0x00FFFFFF,
            keyBackgroundArgb = 0x00000000,
            keyTextArgb = 0xFF1B1C20,
        )
        assertTrue(alpha.toString(), alpha > 0f)
    }

    // ---- sampling arithmetic ------------------------------------------

    @Test
    fun `sampling never drops below the target size`() {
        // 6000x4000 down to a 1080x560 strip: 4 leaves 1500x1000, 8 would
        // leave 750x500, which is under the target and visibly soft once
        // ContentScale.Crop upscales it.
        assertEquals(4, BackgroundBitmapCache.sampleSizeFor(6000, 4000, 1080, 560))
        assertEquals(1, BackgroundBitmapCache.sampleSizeFor(800, 400, 1080, 560))
        assertEquals(1, BackgroundBitmapCache.sampleSizeFor(0, 0, 1080, 560))
    }

    @Test
    fun `target sizes are bucketed so small changes do not evict everything`() {
        // A font-scale nudge or a floating-resize drag moves the target by a
        // pixel or two; the decode should survive it.
        assertEquals(BackgroundBitmapCache.bucket(1080), BackgroundBitmapCache.bucket(1085))
        assertEquals(128, BackgroundBitmapCache.bucket(1))
        assertTrue(BackgroundBitmapCache.bucket(1080) >= 1080)
    }
}
