package com.wasimaster.wmkeyboard.core.emoji

import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class EmojiShortcodesTest {

    companion object {
        private lateinit var codes: EmojiShortcodes
        private lateinit var catalog: Set<String>

        @BeforeClass
        @JvmStatic
        fun setUp() {
            // Unit tests run on the JVM, so read the assets straight from disk.
            codes = EmojiShortcodes.load(
                FileInputStream(File("src/main/assets/emoji/shortcodes.tsv")),
            )
            catalog = EmojiCatalog
                .load(FileInputStream(File("src/main/assets/emoji/catalog.tsv")))
                .mapTo(HashSet()) { it.emoji }
        }
    }

    @Test fun loadsTheWholeTable() {
        assertTrue("expected the full gemoji set", !codes.isEmpty)
        assertEquals("🎉", codes.exact("tada"))
    }

    @Test fun colonsAndCaseAreStripped() {
        assertEquals("😂", codes.exact(":joy:"))
        assertEquals("😂", codes.exact(":JOY"))
        assertEquals("😂", codes.exact("Joy:"))
    }

    @Test fun symbolShortcodesSurvive() {
        assertEquals("👍", codes.exact("+1"))
        assertEquals("👎", codes.exact("-1"))
        assertEquals("💯", codes.exact("100"))
        assertEquals("🎱", codes.exact("8ball"))
    }

    @Test fun unknownNameIsNotGuessed() {
        assertNull(codes.exact("definitely_not_an_emoji"))
        assertNull(codes.exact(""))
    }

    @Test fun prefixOffersShortestNamesFirst() {
        val matches = codes.prefix("tad", 5)
        assertEquals("🎉", matches.first())
    }

    @Test fun prefixDeduplicatesAliasesOfOneEmoji() {
        // hankey / poop / shit all name 💩 — the row should show it once.
        val matches = codes.prefix("", 5)
        assertTrue("empty prefix returns nothing", matches.isEmpty())
        assertEquals(1, codes.prefix("poo", 10).count { it == "💩" })
    }

    @Test fun prefixRespectsTheLimit() {
        assertTrue(codes.prefix("s", 3).size <= 3)
        assertTrue(codes.prefix("s", 0).isEmpty())
    }

    /**
     * The trigger table is generated beside the shortcodes, and two things
     * have to hold for it or the strip fills with near-duplicates: every emoji
     * it names is one the app carries, and none of them is a gender/role
     * variant the palette collapses under a base.
     */
    @Test fun everyTriggerNamesACatalogBaseEmoji() {
        val entries = EmojiCatalog
            .load(FileInputStream(File("src/main/assets/emoji/catalog.tsv")))
        val parents = entries.associate { it.emoji to it.parent }
        val triggers = EmojiTriggers.load(
            FileInputStream(File("src/main/assets/emoji/triggers.tsv")),
        )
        var checked = 0
        for (word in listOf("chef", "doctor", "cop", "party", "birthday", "tears", "hooray")) {
            for (emoji in triggers.of(word)) {
                assertTrue("$emoji not in catalog", emoji in parents)
                assertNull("$word offers the variant $emoji", parents[emoji])
                checked++
            }
        }
        assertTrue("expected the trigger table to be loaded", checked > 10)
    }

    /** A shortcode for an emoji the palette lacks would be a dead result. */
    @Test fun everyShortcodeResolvesToACatalogEmoji() {
        val missing = codes.prefix("", Int.MAX_VALUE)
        assertTrue(missing.isEmpty())
        for (letter in "abcdefghijklmnopqrstuvwxyz0123456789+-") {
            for (emoji in codes.prefix(letter.toString(), Int.MAX_VALUE)) {
                assertTrue("$emoji not in catalog", emoji in catalog)
            }
        }
    }
}
