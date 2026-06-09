package com.wasimaster.wmkeyboard.core.emoji

import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class EmojiSuggesterTest {

    companion object {
        private lateinit var suggester: EmojiSuggester

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val asset = File("src/main/assets/emoji/catalog.tsv")
            suggester = EmojiSuggester(EmojiCatalog.load(FileInputStream(asset)))
        }
    }

    @Test fun birthdaySuggestsThePartySet() {
        assertEquals(listOf("🎂", "🎉", "🥳", "🎁"), suggester.suggest("birthday"))
    }

    @Test fun coffeeSuggestsTheCup() {
        assertEquals("☕", suggester.suggest("coffee").first())
    }

    @Test fun bangladeshSuggestsTheFlag() {
        assertTrue("🇧🇩" in suggester.suggest("bangladesh"))
        assertTrue("🇧🇩" in suggester.suggest("Bangladesh"))
        assertTrue("🇧🇩" in suggester.suggest("বাংলাদেশ"))
    }

    @Test fun bengaliBirthdaySuggestsCake() {
        assertEquals(listOf("🎂", "🎉", "🥳", "🎁"), suggester.suggest("জন্মদিন"))
    }

    @Test fun exactKeywordHitsComeFromTheCatalog() {
        assertTrue("🍕" in suggester.suggest("pizza"))
        // Every "cat" hit within the default limit is a real cat emoji
        // (the cat faces outrank 🐱 in catalog order).
        assertTrue("🐱" in suggester.suggest("cat", limit = 10))
    }

    @Test fun simplePluralsStillMatch() {
        assertTrue(suggester.suggest("cakes").isNotEmpty())
    }

    @Test fun noMatchMeansNoSuggestions() {
        assertTrue(suggester.suggest("qzxqzx").isEmpty())
        assertTrue(suggester.suggest("a").isEmpty())
    }

    @Test fun genderVariantsDoNotFloodResults() {
        // "man" has dozens of gendered-variant entries; those collapse under
        // their base so the plain word yields at most `limit` results.
        assertTrue(suggester.suggest("man").size <= 4)
    }
}
