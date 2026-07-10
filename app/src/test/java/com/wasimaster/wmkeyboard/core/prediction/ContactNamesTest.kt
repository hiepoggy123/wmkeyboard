package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactNamesTest {

    @Test fun exposesWordKeysAndDisplayCase() {
        val contacts = ContactNames.fromNames(listOf("Wasi Mollik"))
        assertEquals(setOf("wasi", "mollik"), contacts.wordKeys)
        assertEquals("Wasi", contacts.displayOf("wasi"))
        assertEquals("Mollik", contacts.displayOf("mollik"))
        assertNull(contacts.displayOf("unknown"))
    }

    @Test fun splitsNamesAndCountsRepeats() {
        val contacts = ContactNames.fromNames(
            listOf("Md. Rakib Islam", "Md. Karim Islam", "রাহিম আহমেদ")
        )
        assertTrue(contacts.contains("rakib"))
        assertTrue(contacts.contains("islam"))
        assertTrue(contacts.contains("রাহিম"))
        assertTrue(contacts.contains("md")) // "Md." loses the dot, keeps the word
        // "Islam" appears twice, so it outranks single-occurrence words.
        assertEquals("Islam", contacts.complete("is", limit = 3).first().word)
    }

    @Test fun keepsDisplayCapitalization() {
        val contacts = ContactNames.fromNames(listOf("Wasi Mollik"))
        assertEquals("Wasi", contacts.complete("wa", limit = 1).first().word)
        assertEquals(listOf("Mollik"), contacts.nextWords("wasi"))
    }

    @Test fun ignoresShortAndEmptyParts() {
        val contacts = ContactNames.fromNames(listOf("A B", "  ", "X (work)"))
        assertTrue(contacts.complete("a", limit = 5).isEmpty())
        assertTrue(contacts.contains("work"))
    }
}
