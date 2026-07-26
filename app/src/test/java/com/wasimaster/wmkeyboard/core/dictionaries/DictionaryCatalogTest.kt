package com.wasimaster.wmkeyboard.core.dictionaries

import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sanity checks over the hardcoded wordlist catalog: every entry must point at
 * a language the registry actually has (else its download row could never be
 * shown, or worse, its dictionary would load for nothing), and ids/urls must
 * be well-formed since the store keys directories by them.
 */
class DictionaryCatalogTest {

    @Test
    fun everyEntryTargetsARegisteredLanguage() {
        val known = LanguageRegistry.all.mapTo(HashSet()) { it.id }
        for (entry in DictionaryCatalog.entries) {
            assertTrue(
                "catalog entry ${entry.id} targets unknown language ${entry.languageId}",
                entry.languageId in known,
            )
        }
    }

    @Test
    fun idsAreUniqueAndFilesystemSafe() {
        assertEquals(
            DictionaryCatalog.entries.size,
            DictionaryCatalog.entries.map { it.id }.toSet().size,
        )
        for (entry in DictionaryCatalog.entries) {
            assertTrue(
                "id ${entry.id} is not filesystem/url safe",
                entry.id.matches(Regex("[a-z][a-z0-9_]*")),
            )
            assertTrue(entry.repoCode.matches(Regex("[a-z][a-z0-9_]*")))
        }
    }

    @Test
    fun urlsPointAtTheDataRepo() {
        for (entry in DictionaryCatalog.entries) {
            assertEquals(
                "https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/" +
                    "HEAD/data/${entry.repoCode}/${entry.repoCode}_full.txt.gz",
                entry.url,
            )
        }
    }

    @Test
    fun sizesAndCountsArePlausible() {
        for (entry in DictionaryCatalog.entries) {
            assertTrue("${entry.id} word count", entry.totalWordCount >= 1000)
            assertTrue("${entry.id} gz size", entry.approxGzBytes > 0)
        }
        // The pt special case: two entries, one language, distinct variants.
        val pt = DictionaryCatalog.forLanguage("pt")
        assertEquals(2, pt.size)
        assertEquals(setOf("Europe", "Brazil"), pt.mapNotNull { it.variant }.toSet())
    }
}
