package com.wasimaster.wmkeyboard.core.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiKeywordPackTest {

    private fun pack(text: String, langId: String? = null) =
        EmojiKeywordPack.load(text.byteInputStream(), langId)

    private val catalog = listOf(
        EmojiEntry("💰", "objects", listOf("money", "bag"), name = "money bag"),
        EmojiEntry("🎂", "food", listOf("cake", "birthday"), name = "birthday cake"),
    )

    @Test fun parsesKeywordsAndNames() {
        val p = pack("💰\tটাকা, দাম\tটাকার ব্যাগ\n🎂\tকেক\n")
        assertEquals(listOf("টাকা", "দাম"), p.keywords["💰"])
        assertEquals("টাকার ব্যাগ", p.names["💰"])
        assertEquals(listOf("কেক"), p.keywords["🎂"])
        assertEquals(null, p.names["🎂"])
        assertEquals(2, p.size)
    }

    @Test fun skipsCommentsAndBlanksButNotTheKeycapHash() {
        val p = pack("# word\tkeywords\n\n#️⃣\thash,keycap\n")
        assertEquals(listOf("hash", "keycap"), p.keywords["#️⃣"])
        assertEquals(1, p.size)
    }

    @Test fun keywordsAreLowercasedAndDeduplicated() {
        val p = pack("💰\tDinero, dinero , PLATA\n")
        assertEquals(listOf("dinero", "plata"), p.keywords["💰"])
    }

    @Test fun junkFileParsesToNothing() {
        assertTrue(pack("%PDF-1.4 garbage\n").isEmpty)
        assertTrue(pack("").isEmpty)
    }

    @Test fun mergeAddsKeywordsAndLeavesNamesAlone() {
        val merged = EmojiKeywordPack.merge(catalog, listOf(pack("💰\tটাকা\tটাকার ব্যাগ\n", "bn")))
        val money = merged.first { it.emoji == "💰" }
        assertEquals(listOf("money", "bag", "টাকা"), money.keywords)
        // Names are resolved per typed language, not baked into the catalog:
        // every enabled language gets a pack, and only one name can be shown.
        assertEquals("money bag", money.name)
        assertEquals("birthday cake", merged.first { it.emoji == "🎂" }.name)
    }

    @Test fun namesAreGroupedByLanguage() {
        val names = EmojiKeywordPack.namesByLanguage(
            listOf(
                pack("💰\tটাকা\tটাকার ব্যাগ\n", "bn"),
                pack("💰\tपैसा\tपैसे का थैला\n", "hi"),
            ),
        )
        assertEquals("টাকার ব্যাগ", names["bn"]?.get("💰"))
        assertEquals("पैसे का थैला", names["hi"]?.get("💰"))
    }

    /** Within one language the later pack (the user's import) wins. */
    @Test fun laterPackWinsWithinALanguage() {
        val names = EmojiKeywordPack.namesByLanguage(
            listOf(pack("💰\tটাকা\tডাউনলোড\n", "bn"), pack("💰\tটাকা\tআমার নাম\n", "bn")),
        )
        assertEquals("আমার নাম", names["bn"]?.get("💰"))
    }

    /** A pack with no language names nothing — there is nothing to show it under. */
    @Test fun packsWithoutALanguageNameNothing() {
        assertTrue(EmojiKeywordPack.namesByLanguage(listOf(pack("💰\tটাকা\tব্যাগ\n"))).isEmpty())
    }

    @Test fun packsStackAcrossLanguages() {
        val merged = EmojiKeywordPack.merge(
            catalog,
            listOf(pack("💰\tটাকা\n"), pack("💰\tdinero\n")),
        )
        val money = merged.first { it.emoji == "💰" }
        assertTrue("টাকা" in money.keywords)
        assertTrue("dinero" in money.keywords)
        assertTrue("money" in money.keywords)
    }

    /** The no-packs case must not churn the list EmojiNames caches against. */
    @Test fun mergeWithoutPacksReturnsTheSameList() {
        assertSame(catalog, EmojiKeywordPack.merge(catalog, emptyList()))
        assertSame(catalog, EmojiKeywordPack.merge(catalog, listOf(EmojiKeywordPack.EMPTY)))
        assertNotSame(catalog, EmojiKeywordPack.merge(catalog, listOf(pack("💰\tটাকা\n"))))
    }

    /** A pack naming emoji this build has never heard of must not break merge. */
    @Test fun unknownEmojiInAPackAreIgnored() {
        val merged = EmojiKeywordPack.merge(catalog, listOf(pack("🫩\tnew\n💰\tটাকা\n")))
        assertEquals(catalog.size, merged.size)
        assertTrue("টাকা" in merged.first { it.emoji == "💰" }.keywords)
    }

    /** The merged catalog is what search runs on, so the packs must reach it. */
    @Test fun mergedKeywordsAreSearchable() {
        val merged = EmojiKeywordPack.merge(catalog, listOf(pack("💰\tটাকা\n")))
        val results = EmojiSearch(merged).search("টাকা").map { it.emoji }
        assertEquals(listOf("💰"), results)
    }
}
