package com.wasimaster.wmkeyboard.core.emoji

import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertEquals
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
            val codes = EmojiShortcodes.load(
                FileInputStream(File("src/main/assets/emoji/shortcodes.tsv")),
            )
            search = EmojiSearch(entries, codes)
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

    /** The names people actually type, which the Unicode names do not match. */
    @Test fun githubShortcodesWinOutright() {
        assertEquals("😂", results("joy").first())
        assertEquals("🎉", results("tada").first())
        assertEquals("💯", results("100").first())
        assertEquals("👍", results("+1").first())
        assertEquals("👎", results("-1").first())
        assertEquals("🤷", results("shrug").first())
        assertEquals("💩", results("poop").first())
        assertEquals("😭", results("sob").first())
        assertEquals("🙏", results("pray").first())
        assertEquals("#️⃣", results("hash").first())
    }

    /** Discord/Slack spellings gemoji itself does not carry. */
    @Test fun discordSpellingsResolve() {
        assertEquals("🙂", results("slight_smile").first())
        assertEquals("🤔", results("thinking").first())
        assertEquals("🙄", results("rolling_eyes").first())
        assertEquals("🤦", results("person_facepalming").first())
    }

    @Test fun colonsAroundShortcodeAreIgnored() {
        assertEquals("🎉", results(":tada:").first())
        assertEquals("🎉", results(":tada").first())
    }

    /** Underscores read as spaces, so snake_case Unicode names hit keywords. */
    @Test fun underscoredNameFindsCatalogEntry() {
        assertTrue("🧐" in results("face_with_monocle"))
        assertTrue("🚀" in results("rocket"))
    }

    @Test fun partialShortcodeOffersTheShortestFirst() {
        val r = results("tad")
        assertEquals("🎉", r.first())
    }

    /** A shortcode must not drown the keyword layers it sits on top of. */
    @Test fun keywordSearchStillWorksAlongsideShortcodes() {
        val r = results("cat")
        assertEquals("🐱", r.first())
        assertTrue("😺" in r)
        assertTrue("🐈" in r)
    }
}
