package com.wasimaster.wmkeyboard.core.emoji

import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class EmojiSearchTest {

    companion object {
        private lateinit var search: EmojiSearch

        @BeforeClass
        @JvmStatic
        fun setUp() {
            // Unit tests run on the JVM, so read the asset straight from disk.
            val asset = File("src/main/assets/emoji/catalog.tsv")
            val entries = EmojiCatalog.load(FileInputStream(asset))
            assertTrue("catalog should have hundreds of entries", entries.size > 300)
            search = EmojiSearch(entries)
        }
    }

    private fun results(query: String) = search.search(query).map { it.emoji }

    @Test fun happyFindsSmileys() {
        val r = results("happy")
        assertTrue("😀" in r)
        assertTrue("😄" in r)
        assertTrue("😊" in r)
        assertTrue("🥳" in r)
    }

    @Test fun partyFindsCelebrationConcepts() {
        val r = results("party")
        assertTrue("🎉" in r)
        assertTrue("🥳" in r)
        assertTrue("🍾" in r)
        assertTrue("🎂" in r)
    }

    @Test fun catFindsCats() {
        val r = results("cat")
        assertTrue("🐱" in r)
        assertTrue("😺" in r)
        assertTrue("🐈" in r)
    }

    @Test fun loveFindsHeartsAndFaces() {
        val r = results("love")
        assertTrue("❤️" in r)
        assertTrue("🥰" in r)
        assertTrue("😍" in r)
        assertTrue("😘" in r)
        assertTrue("💕" in r)
    }

    @Test fun laughFindsLaughing() {
        val r = results("laugh")
        assertTrue("🤣" in r)
        assertTrue("😂" in r)
        assertTrue("😹" in r)
    }

    @Test fun fireFindsRelatedConcepts() {
        val r = results("fire")
        assertTrue("🔥" in r)
        assertTrue("💥" in r)
        assertTrue("☄️" in r)
        assertTrue("❤️‍🔥" in r)
    }

    @Test fun bengaliCatQuery() {
        val r = results("বিড়াল")
        assertTrue("🐱" in r)
        assertTrue("😺" in r)
    }

    @Test fun bengaliSmileQuery() {
        val r = results("হাসি")
        assertTrue(r.any { it in listOf("😀", "😄", "😊", "😂") })
    }

    @Test fun birthdayFindsPartySet() {
        val r = results("birthday")
        assertTrue("🎂" in r)
        assertTrue("🎉" in r)
        assertTrue("🥳" in r)
        assertTrue("🎁" in r)
    }

    @Test fun coffeeFindsCup() {
        assertTrue("☕" in results("coffee"))
    }

    @Test fun bangladeshFindsFlag() {
        assertTrue("🇧🇩" in results("bangladesh"))
        assertTrue("🇧🇩" in results("বাংলাদেশ"))
    }

    @Test fun fuzzyTypoStillMatches() {
        assertTrue("😀" in results("hapy"))
    }

    @Test fun prefixWhileTyping() {
        assertTrue(results("birt").contains("🎂"))
    }
}
