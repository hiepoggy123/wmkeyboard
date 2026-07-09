package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactEmailsTest {

    @Test fun completesWholeAddressFromPrefix() {
        val emails = ContactEmails.fromAddresses(listOf("john.doe@gmail.com"))
        assertEquals(listOf("john.doe@gmail.com"), emails.complete("john", limit = 5))
        // Completes past the local part too.
        assertEquals(listOf("john.doe@gmail.com"), emails.complete("john.doe@gm", limit = 5))
    }

    @Test fun lowercasesAndDedupes() {
        val emails = ContactEmails.fromAddresses(
            listOf("John.Doe@Gmail.com", "  john.doe@gmail.com  ")
        )
        assertEquals(1, emails.size)
        assertTrue(emails.contains("john.doe@gmail.com"))
        assertEquals(listOf("john.doe@gmail.com"), emails.complete("john", limit = 5))
    }

    @Test fun dropsImplausibleAddresses() {
        val emails = ContactEmails.fromAddresses(
            listOf("not-an-email", "@nolocalpart.com", "trailingat@", "has space@x.com", "")
        )
        assertTrue(emails.isEmpty)
    }

    @Test fun emptyPrefixOffersNothing() {
        val emails = ContactEmails.fromAddresses(listOf("a@b.com"))
        assertFalse(emails.isEmpty)
        assertTrue(emails.complete("", limit = 5).isEmpty())
    }
}
