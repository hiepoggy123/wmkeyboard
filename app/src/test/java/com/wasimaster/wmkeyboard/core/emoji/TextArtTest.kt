package com.wasimaster.wmkeyboard.core.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextArtTest {

    private val all = TextArt.kaomoji + TextArt.emoticons

    @Test
    fun `every group has entries`() {
        for (group in all) {
            assertTrue("${group.name} is empty", group.items.isNotEmpty())
        }
    }

    @Test
    fun `entries are unique within each catalog`() {
        for (catalog in listOf(TextArt.kaomoji, TextArt.emoticons)) {
            val items = catalog.flatMap { it.items }
            assertEquals(
                "duplicate entries: ${items.groupBy { it }.filterValues { it.size > 1 }.keys}",
                items.size,
                items.distinct().size,
            )
        }
    }

    @Test
    fun `group names are unique within each catalog`() {
        for (catalog in listOf(TextArt.kaomoji, TextArt.emoticons)) {
            val names = catalog.map { it.name }
            assertEquals(names.size, names.distinct().size)
        }
    }

    /** A stray edge space would be committed into the user's text as-is. */
    @Test
    fun `entries are trimmed and non-blank`() {
        for (group in all) {
            for (item in group.items) {
                assertTrue("blank entry in ${group.name}", item.isNotBlank())
                assertEquals("untrimmed \"$item\" in ${group.name}", item.trim(), item)
            }
        }
    }

    /** Newlines and tabs would break the single-line cells they render in. */
    @Test
    fun `entries are single-line`() {
        for (group in all) {
            for (item in group.items) {
                assertTrue(
                    "line break in \"$item\" (${group.name})",
                    item.none { it == '\n' || it == '\r' || it == '\t' },
                )
            }
        }
    }

    @Test
    fun `catalogs are big enough to be worth a tab`() {
        assertTrue(TextArt.kaomoji.sumOf { it.items.size } >= 150)
        assertTrue(TextArt.emoticons.sumOf { it.items.size } >= 80)
    }
}
