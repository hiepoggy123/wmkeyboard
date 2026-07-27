package com.wasimaster.wmkeyboard.core.emoji

import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class EmojiCatalogTest {

    companion object {
        private lateinit var entries: List<EmojiEntry>
        private lateinit var byEmoji: Map<String, EmojiEntry>

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val asset = File("src/main/assets/emoji/catalog.tsv")
            entries = EmojiCatalog.load(FileInputStream(asset))
            byEmoji = entries.associateBy { it.emoji }
        }
    }

    @Test fun catalogCoversTheFullUnicodeSet() {
        assertTrue("expected the full Emoji 17.0 set, got ${entries.size}", entries.size > 1800)
    }

    @Test fun emoji16And17AdditionsPresent() {
        assertTrue("🫩" in byEmoji) // face with bags under eyes (16.0)
        assertTrue("🫆" in byEmoji) // fingerprint (16.0)
        assertTrue("🫯" in byEmoji) // fight cloud (17.0)
        assertTrue("🦣" in byEmoji) // mammoth (13.0, missing from the old subset)
        assertTrue("🫠" in byEmoji) // melting face (14.0)
        assertTrue("🩷" in byEmoji) // pink heart (15.0)
        assertTrue("🙂‍↔️" in byEmoji) // head shaking horizontally (15.1)
    }

    @Test fun genderVariantsCollapseUnderTheirBase() {
        assertEquals("🏃", byEmoji.getValue("🏃‍♂️").parent)
        assertEquals("🏃", byEmoji.getValue("🏃‍♀️").parent)
        assertNull(byEmoji.getValue("🏃").parent)
        // Professions collapse under the person-led form.
        assertEquals("🧑‍⚕️", byEmoji.getValue("👨‍⚕️").parent)
        assertEquals("🧑‍⚕️", byEmoji.getValue("👩‍⚕️").parent)
        assertNull(byEmoji.getValue("🧑‍⚕️").parent)
    }

    @Test fun everyParentIsItselfACatalogEntry() {
        for (entry in entries) {
            val parent = entry.parent ?: continue
            assertTrue("parent $parent of ${entry.emoji} missing", parent in byEmoji)
            assertNull("parent $parent must be a base", byEmoji.getValue(parent).parent)
        }
    }

    @Test fun keywordsCarryBothLanguages() {
        val flag = byEmoji.getValue("🇧🇩")
        assertTrue("bangladesh" in flag.keywords)
        assertTrue("বাংলাদেশ" in flag.keywords)
    }

    @Test fun categoriesMatchTheExistingTabSet() {
        val expected = setOf(
            "smileys", "people", "animals", "nature", "food",
            "travel", "activities", "objects", "symbols", "flags",
        )
        assertEquals(expected, entries.map { it.category }.toSet())
    }
}
