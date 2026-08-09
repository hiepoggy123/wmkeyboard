package com.wasimaster.wmkeyboard.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decoration batch on [ThemeSpec] — per-theme font and sound, animated
 * background, key textures, single-key overrides, decals, press effects and
 * the generic assets map. All of it must survive a round trip, decode to its
 * defaults from a theme written before it existed, and — the forward-compat
 * contract — never cost more than itself when a name is unknown.
 */
class ThemeSpecDecorationsTest {

    private fun decorated() = ThemeSpec(
        id = "custom_1",
        name = "Decorated",
        fontId = "google:Pacifico",
        scriptFontIds = mapOf("BENGALI" to "installed:Sohanur Minecraft", "ARABIC" to "google:Cairo"),
        soundStyle = "THOCK",
        soundCustomId = "snd1",
        backgroundAnimated = true,
        keyTexture = "/data/theme_images/t.img",
        keyTextureSpace = "/data/theme_images/s.img",
        keyTextureScale = "TILE",
        keyTextureOpacity = 0.8f,
        keyOverrides = mapOf(
            "a" to KeyOverride(background = 0xFFFF0000, popupText = 0xFF00FF00),
            "ENTER" to KeyOverride(text = 0xFF0000FF),
        ),
        decals = listOf(
            DecalSpec(id = "d1", image = "/data/theme_images/d1.img", x = 0.2f, y = 0.8f),
        ),
        keyEffect = "EMOJI",
        keyEffectParam = "🎉🔥",
        keyEffectIntensity = 1.5f,
        keyEffectImages = listOf("/data/theme_images/fx0.img", "/data/theme_images/fx1.img"),
        popupPlacement = "float",
        popupBorderColor = 0xFFB3282D,
        popupBorderWidthDp = 1.5f,
        popupTexture = "/data/theme_images/p.img",
        chipText = 0xFFE8DFC8,
        chipActiveBackground = 0xFFB3282D,
        chipActiveText = 0xFFFFFFFF,
        chipBorderColor = 0xFF241F19,
        chipBorderWidthDp = 1f,
        chipShape = "SHARP",
        chipCornerRadiusDp = 0,
        menuShape = "CUT",
        cardShape = "SQUIRCLE",
        assets = mapOf("keyTexture" to "aGVsbG8="),
    )

    @Test
    fun `decorations survive a round trip`() {
        val decoded = ThemeCodec.decode(ThemeCodec.encode(decorated()))
        assertNotNull(decoded)
        assertEquals(decorated(), decoded)
    }

    @Test
    fun `a theme written before this feature decodes with no decorations`() {
        val old = """
            {"id":"custom_9","name":"Old","dark":true,"boardBackground":-15329508}
        """.trimIndent()
        val decoded = ThemeCodec.decode(old)
        assertNotNull(decoded)
        decoded!!
        assertNull(decoded.fontId)
        assertTrue(decoded.scriptFontIds.isEmpty())
        assertNull(decoded.soundStyle)
        assertEquals(false, decoded.backgroundAnimated)
        assertNull(decoded.keyTexture)
        assertTrue(decoded.keyOverrides.isEmpty())
        assertTrue(decoded.decals.isEmpty())
        assertNull(decoded.keyEffect)
        assertNull(decoded.popupPlacement)
        assertNull(decoded.popupTexture)
        assertNull(decoded.chipShape)
        assertTrue(decoded.assets.isEmpty())
    }

    /** What this build's decode of a *later* build's theme looks like. */
    @Test
    fun `unknown fields cost themselves and not the theme`() {
        val futuristic = ThemeCodec.encode(decorated())
            .removeSuffix("}") +
            ""","someFutureField":123,"assetsV2":{"x":"y"}}"""
        val decoded = ThemeCodec.decode(futuristic)
        assertNotNull(decoded)
        assertEquals("google:Pacifico", decoded?.fontId)
        assertEquals(2, decoded?.keyOverrides?.size)
    }

    /**
     * A theme names one font per script it has an answer for, so a script this
     * build has never heard of has to cost its own entry and nothing else — the
     * same contract every other name in the spec keeps.
     */
    @Test
    fun `a script font for an unknown script costs its entry and not the theme`() {
        val json = ThemeCodec.encode(decorated())
            .replace(""""ARABIC":"google:Cairo"""", """"LINEAR_B":"google:Cairo"""")
        val decoded = ThemeCodec.decode(json)
        assertNotNull(decoded)
        assertEquals(
            "installed:Sohanur Minecraft",
            decoded?.scriptFontIds?.get("BENGALI"),
        )
        assertNull(decoded?.scriptFontIds?.get("ARABIC"))
    }

    @Test
    fun `unknown texture scale falls back to crop`() {
        assertEquals(KeyTextureScale.CROP, keyTextureScaleOrDefault(null))
        assertEquals(KeyTextureScale.CROP, keyTextureScaleOrDefault("HOLOGRAM"))
        assertEquals(KeyTextureScale.TILE, keyTextureScaleOrDefault("tile"))
    }

    @Test
    fun `popup placement parses the two names and nothing else`() {
        assertEquals(true, popupOnKeyOrNull("key"))
        assertEquals(true, popupOnKeyOrNull("KEY"))
        assertEquals(false, popupOnKeyOrNull("float"))
        assertNull(popupOnKeyOrNull(null))
        assertNull(popupOnKeyOrNull("orbiting"))
    }

    @Test
    fun `circle is a known key shape and unknown chip shapes cost the field`() {
        assertEquals(KeyShapeKind.CIRCLE, keyShapeKindOrNull("CIRCLE"))
        assertNull(keyShapeKindOrNull("DODECAHEDRON"))
    }

    @Test
    fun `menus and cards only inherit the plain outlines`() {
        // Plain outlines pass through …
        assertEquals(KeyShapeKind.SHARP, safeContainerKind(KeyShapeKind.SHARP))
        assertEquals(KeyShapeKind.CUT, safeContainerKind(KeyShapeKind.CUT))
        assertEquals(KeyShapeKind.SQUIRCLE, safeContainerKind(KeyShapeKind.SQUIRCLE))
        // … the decorative ones fall back to rounded rather than clipping
        // a menu's own rows.
        assertEquals(KeyShapeKind.ROUNDED, safeContainerKind(KeyShapeKind.SLANT))
        assertEquals(KeyShapeKind.ROUNDED, safeContainerKind(KeyShapeKind.CIRCLE))
        assertEquals(KeyShapeKind.ROUNDED, safeContainerKind(KeyShapeKind.SCALLOP))
        assertEquals(KeyShapeKind.ROUNDED, safeContainerKind(KeyShapeKind.HEXAGON))
    }

    @Test
    fun `the circle shape only inscribes a circle in a roughly square box`() {
        // A key is square enough to take the circle it is named for …
        assertFalse(circleBecomesStadium(width = 40f, height = 48f))
        // … a spacebar is not, and neither is the on-key preview bubble, which
        // is one key wide and more than twice as tall. Inscribing there would
        // centre the circle and clip the label drawn near the top.
        assertTrue(circleBecomesStadium(width = 320f, height = 48f))
        assertTrue(circleBecomesStadium(width = 44f, height = 110f))
    }

    @Test
    fun `unknown effect name resolves to no effect`() {
        assertNull(keyEffectKindOrNull(null))
        assertNull(keyEffectKindOrNull("FIREWORKS_3D"))
        assertEquals(KeyEffectKind.HEARTS, keyEffectKindOrNull("hearts"))
        assertEquals(KeyEffectKind.CUSTOM_IMAGE, keyEffectKindOrNull("custom_image"))
    }

    @Test
    fun `a reseed keeps the decorations`() {
        val reseeded = decorated().reseeded(0xFF3366AA, dark = false)
        assertEquals(decorated().keyOverrides, reseeded.keyOverrides)
        assertEquals(decorated().decals, reseeded.decals)
        assertEquals(decorated().keyTexture, reseeded.keyTexture)
        assertEquals(decorated().fontId, reseeded.fontId)
        assertEquals(decorated().keyEffect, reseeded.keyEffect)
    }
}
