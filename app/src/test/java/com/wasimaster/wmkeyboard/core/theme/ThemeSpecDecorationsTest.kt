package com.wasimaster.wmkeyboard.core.theme

import org.junit.Assert.assertEquals
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
        assertNull(decoded.soundStyle)
        assertEquals(false, decoded.backgroundAnimated)
        assertNull(decoded.keyTexture)
        assertTrue(decoded.keyOverrides.isEmpty())
        assertTrue(decoded.decals.isEmpty())
        assertNull(decoded.keyEffect)
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

    @Test
    fun `unknown texture scale falls back to crop`() {
        assertEquals(KeyTextureScale.CROP, keyTextureScaleOrDefault(null))
        assertEquals(KeyTextureScale.CROP, keyTextureScaleOrDefault("HOLOGRAM"))
        assertEquals(KeyTextureScale.TILE, keyTextureScaleOrDefault("tile"))
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
