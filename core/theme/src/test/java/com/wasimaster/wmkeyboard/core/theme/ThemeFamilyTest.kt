package com.wasimaster.wmkeyboard.core.theme

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The family helpers: one theme carrying its alternate looks as [ThemeSpec.variants].
 *
 * The forward-compat case is the load-bearing one — a family file must open on
 * a build that has never heard of variants, because the whole reason variants
 * nest inside the spec (instead of a new container format) is that older
 * builds' `ignoreUnknownKeys` drops them and keeps the base theme.
 */
class ThemeFamilyTest {

    private fun theme(id: String, name: String = id) = themeFromSeed(id, name, 0xFF3B82C4, dark = true)

    private fun family(parentId: String, vararg variantIds: String) =
        theme(parentId).copy(variants = variantIds.map { theme(it) })

    // ---- flattening and lookup ----

    @Test
    fun `flattening lays each family's variants after their parent`() {
        val list = listOf(family("a", "a1", "a2"), theme("b"))
        assertEquals(listOf("a", "a1", "a2", "b"), list.flattenedThemes().map { it.id })
    }

    @Test
    fun `findThemeFamily answers for the parent and for a variant`() {
        val list = listOf(family("a", "a1"), theme("b"))
        assertEquals("a", list.findThemeFamily("a")?.id)
        assertEquals("a", list.findThemeFamily("a1")?.id)
        assertEquals("b", list.findThemeFamily("b")?.id)
        assertNull(list.findThemeFamily("missing"))
    }

    @Test
    fun `findThemeSpec finds a built-in variant`() {
        assertEquals("builtin_glacier", findThemeSpec("builtin_glacier", emptyList())?.id)
        assertEquals("builtin_catppuccin_frappe", findThemeSpec("builtin_catppuccin_frappe", emptyList())?.id)
    }

    @Test
    fun `a custom shadows a built-in of the same id`() {
        // The same precedence activeThemeSpec has always had: customs first.
        val custom = theme("builtin_ocean", name = "mine")
        assertEquals("mine", findThemeSpec("builtin_ocean", listOf(custom))?.name)
    }

    // ---- reminting and member writes ----

    @Test
    fun `withFreshIds remints the parent and every variant`() {
        val fresh = family("old", "old_a", "old_b").withFreshIds("custom_1")
        assertEquals("custom_1", fresh.id)
        assertEquals(listOf("custom_1_v0", "custom_1_v1"), fresh.variants.map { it.id })
    }

    @Test
    fun `withFreshIds strips variants of variants`() {
        // Only a hand-edited file can carry them; the one-level contract wins.
        val nested = theme("p").copy(
            variants = listOf(theme("v").copy(variants = listOf(theme("vv")))),
        )
        val fresh = nested.withFreshIds("custom_1")
        assertTrue(fresh.variants.single().variants.isEmpty())
    }

    @Test
    fun `replacingMember edits the parent in place`() {
        val next = family("a", "a1").replacingMember("a") { it.copy(name = "renamed") }
        assertEquals("renamed", next.name)
        assertEquals(listOf("a1"), next.variants.map { it.id })
    }

    @Test
    fun `replacingMember edits one variant and leaves the rest`() {
        val next = family("a", "a1", "a2").replacingMember("a1") { it.copy(name = "renamed") }
        assertEquals("a", next.id)
        assertEquals(listOf("renamed", "a2"), next.variants.map { it.name })
    }

    // ---- grouping an import ----

    @Test
    fun `groupAsFamily heads the list with its first theme`() {
        val entry = groupAsFamily(listOf(theme("p"), theme("v1"), theme("v2")), "Dusk")
        assertEquals("p", entry.id)
        assertEquals("Dusk", entry.familyName)
        assertEquals(listOf("v1", "v2"), entry.variants.map { it.id })
    }

    @Test
    fun `groupAsFamily leaves a single theme unnamed`() {
        // One theme is not a family; a group label on it would draw a group
        // that does not exist.
        val entry = groupAsFamily(listOf(theme("p")), "Dusk")
        assertNull(entry.familyName)
        assertTrue(entry.variants.isEmpty())
    }

    // ---- codec ----

    @Test
    fun `a family round-trips through the codec`() {
        val entry = family("p", "v1").copy(familyName = "Mine")
        val decoded = ThemeCodec.decode(ThemeCodec.encode(entry))
        assertEquals(entry, decoded)
    }

    @Test
    fun `an old build's codec reads a family file as the base theme`() {
        // Stands in for a build shipped before variants existed: same Json
        // settings, no variants or familyName fields to decode into.
        @Serializable
        data class LegacyTheme(val id: String, val name: String, val dark: Boolean = true)

        val legacyJson = Json { ignoreUnknownKeys = true }
        val encoded = ThemeCodec.encode(family("p", "v1").copy(familyName = "Mine"))
        val legacy = legacyJson.decodeFromString<LegacyTheme>(encoded)
        assertEquals("p", legacy.id)
    }

    @Test
    fun `a single-theme file from an old build decodes with no variants`() {
        val decoded = ThemeCodec.decode("""{"id":"a","name":"Old","dark":true}""")
        assertEquals(emptyList<ThemeSpec>(), decoded?.variants)
        assertNull(decoded?.familyName)
    }
}
