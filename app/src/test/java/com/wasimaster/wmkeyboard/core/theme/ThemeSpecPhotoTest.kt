package com.wasimaster.wmkeyboard.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Photo attribution on a theme, and the reseed that has to stop dropping it.
 *
 * `withEmbeddedImages` and `withExtractedImages` use `android.util.Base64` and
 * cannot run on the JVM, so the coverage here is of [ThemeCodec], which is pure
 * kotlinx-serialization.
 */
class ThemeSpecPhotoTest {

    private val credit = PhotoAttribution(
        provider = "unsplash",
        photoId = "abc123",
        photographer = "Ana Silva",
        photographerUrl = "https://unsplash.com/@ana",
        photoUrl = "https://unsplash.com/photos/abc123",
        altText = "a misty forest",
        avgColor = "#0c1014",
    )

    private fun theme() = ThemeSpec(
        id = "custom_1",
        name = "Forest",
        backgroundImage = "/data/user/0/app/files/theme_images/custom_1.img",
        backgroundPhoto = credit,
    )

    // ---- serialization ------------------------------------------------

    @Test
    fun `a credit survives a round trip`() {
        val decoded = ThemeCodec.decode(ThemeCodec.encode(theme()))
        assertEquals(credit, decoded?.backgroundPhoto)
    }

    @Test
    fun `a theme written before this feature still decodes`() {
        val old = """
            {"id":"custom_9","name":"Old","dark":true,"boardBackground":-15329508}
        """.trimIndent()
        val decoded = ThemeCodec.decode(old)
        assertNotNull(decoded)
        assertNull(decoded?.backgroundPhoto)
        assertNull(decoded?.backgroundPhotoLandscape)
    }

    @Test
    fun `a provider this build has never heard of does not lose the theme`() {
        // The reason `provider` is a String and not an enum: kotlinx throws on
        // an unknown enum constant, ThemeCodec.decode swallows that, and the
        // user would lose their whole theme over an unfamiliar credit line.
        val future = ThemeCodec.encode(
            theme().copy(backgroundPhoto = credit.copy(provider = "some_new_service")),
        )
        val decoded = ThemeCodec.decode(future)
        assertNotNull(decoded)
        assertEquals("Forest", decoded?.name)
        assertEquals("some_new_service", decoded?.backgroundPhoto?.provider)
    }

    @Test
    fun `a theme list round-trips with credits`() {
        val list = listOf(theme(), theme().copy(id = "custom_2", backgroundPhoto = null))
        val decoded = ThemeCodec.decodeList(ThemeCodec.encodeList(list))
        assertEquals(2, decoded.size)
        assertEquals(credit, decoded[0].backgroundPhoto)
        assertNull(decoded[1].backgroundPhoto)
    }

    // ---- reseeded -----------------------------------------------------

    @Test
    fun `reseeding keeps the photo and its credit`() {
        // The whole point of inverting this function: the previous version
        // listed what to carry over, so any field added later was dropped.
        val reseeded = theme().reseeded(0xFF4C8DF6, dark = true)
        assertEquals("/data/user/0/app/files/theme_images/custom_1.img", reseeded.backgroundImage)
        assertEquals(credit, reseeded.backgroundPhoto)
    }

    @Test
    fun `reseeding keeps everything that is not a generated colour`() {
        val decorated = theme().copy(
            backgroundImageOpacity = 0.6f,
            backgroundImageBlur = 8f,
            boardGradient = GradientSpec(colors = listOf(0xFF000000, 0xFFFFFFFF)),
            keyGradient = GradientSpec(colors = listOf(0xFF111111, 0xFF222222)),
            keyShape = KeyShapeKind.PILL,
            keyBorderColor = 0xFF00FF00,
            keyBorderWidthDp = 2f,
            gestureTrailColor = 0xFFFF0000,
            keyCornerRadiusDp = 12,
            popupCornerRadiusDp = 16,
            toolCircleRadiusDp = 20,
            animation = ThemeAnimation.FLOW,
            animationSpeed = 2f,
            backgroundImageLandscape = "/files/theme_images/custom_1_land.img",
            backgroundPhotoLandscape = credit.copy(photoId = "land"),
        )
        val reseeded = decorated.reseeded(0xFF4C8DF6, dark = true)

        assertEquals(0.6f, reseeded.backgroundImageOpacity, 0f)
        assertEquals(8f, reseeded.backgroundImageBlur, 0f)
        assertEquals(decorated.boardGradient, reseeded.boardGradient)
        assertEquals(decorated.keyGradient, reseeded.keyGradient)
        assertEquals(KeyShapeKind.PILL, reseeded.keyShape)
        assertEquals(0xFF00FF00, reseeded.keyBorderColor)
        assertEquals(2f, reseeded.keyBorderWidthDp, 0f)
        assertEquals(0xFFFF0000, reseeded.gestureTrailColor)
        assertEquals(12, reseeded.keyCornerRadiusDp)
        assertEquals(16, reseeded.popupCornerRadiusDp)
        assertEquals(20, reseeded.toolCircleRadiusDp)
        assertEquals(ThemeAnimation.FLOW, reseeded.animation)
        assertEquals(2f, reseeded.animationSpeed, 0f)
        assertEquals("/files/theme_images/custom_1_land.img", reseeded.backgroundImageLandscape)
        assertEquals("land", reseeded.backgroundPhotoLandscape?.photoId)
        assertEquals("custom_1", reseeded.id)
        assertEquals("Forest", reseeded.name)
    }

    @Test
    fun `reseeding does rebuild the generated colours`() {
        val seed = 0xFFE53935
        val reseeded = theme().reseeded(seed, dark = true)
        val generated = themeFromSeed("custom_1", "Forest", seed, dark = true)
        assertEquals(generated.boardBackground, reseeded.boardBackground)
        assertEquals(generated.keyBackground, reseeded.keyBackground)
        assertEquals(generated.accent, reseeded.accent)
        assertEquals(generated.enterKeyBackground, reseeded.enterKeyBackground)
        assertEquals(generated.chipBackground, reseeded.chipBackground)
    }

    @Test
    fun `reseeding clears the per-theme colour exceptions`() {
        // These are overrides of a palette that no longer exists, so they go
        // back to following the new colours. This matches what the previous
        // implementation did by starting from a freshly generated theme.
        val overridden = theme().copy(
            modifierKeyText = 0xFF123456,
            popupText = 0xFF123456,
            toolbarIcon = 0xFF123456,
            toolCircleActiveBackground = 0xFF123456,
            suggestionText = 0xFF123456,
        )
        val reseeded = overridden.reseeded(0xFF4C8DF6, dark = true)
        assertNull(reseeded.modifierKeyText)
        assertNull(reseeded.popupText)
        assertNull(reseeded.toolbarIcon)
        assertNull(reseeded.toolCircleActiveBackground)
        assertNull(reseeded.suggestionText)
    }

    @Test
    fun `reseeding switches the theme between light and dark`() {
        assertTrue(theme().reseeded(0xFF4C8DF6, dark = true).dark)
        assertTrue(!theme().reseeded(0xFF4C8DF6, dark = false).dark)
    }

    // ---- the scrim helper ---------------------------------------------

    // The `L` suffixes are load-bearing: a hex literal with a zero alpha byte
    // fits in an Int, and these helpers take a Long.
    @Test
    fun `the alpha helper replaces opacity and keeps the colour`() {
        assertEquals(0x80112233L, 0xFF112233L.withAlphaFraction(0.5f))
        assertEquals(0x00112233L, 0xFF112233L.withAlphaFraction(0f))
        assertEquals(0xFF112233L, 0x00112233L.withAlphaFraction(1f))
    }

    @Test
    fun `the alpha helper clamps rather than wrapping`() {
        assertEquals(0xFF112233L, 0x00112233L.withAlphaFraction(4f))
        assertEquals(0x00112233L, 0xFF112233L.withAlphaFraction(-1f))
    }

    @Test
    fun `alpha reads back as it was written`() {
        assertEquals(1f, 0xFF112233L.alphaFraction(), 0.001f)
        assertEquals(0f, 0x00112233L.alphaFraction(), 0.001f)
        assertEquals(0.5f, 0xFF112233L.withAlphaFraction(0.5f).alphaFraction(), 0.01f)
    }
}
