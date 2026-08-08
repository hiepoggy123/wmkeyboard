package com.wasimaster.wmkeyboard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The file associations live in the manifest as `pathPattern` strings and the
 * importer's understanding of them lives in [WMFileTypes.EXTENSIONS]. Nothing
 * at compile time ties the two together, and the failure mode is silent: a new
 * export format gets a Kotlin constant, nobody adds the patterns, and the file
 * simply never opens from a file manager.
 *
 * These read the real manifest and check the two still agree.
 */
class FileAssociationTest {

    /** Unit tests run with the module directory as the working directory. */
    private val manifest = File("src/main/AndroidManifest.xml").readText()
        // Comments in this manifest quote the patterns they explain.
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    private val patterns: List<String> =
        Regex("""android:pathPattern="([^"]+)"""")
            .findAll(manifest).map { it.groupValues[1] }.toList()

    /**
     * `.*\\..*\\.wmtheme\\.json` -> `wmtheme.json`: drop the leading wildcards,
     * then undo the backslash escaping aapt2 resolves at build time.
     */
    private fun extensionOf(pattern: String): String =
        pattern.substringAfterLast(""".*\\.""").replace("""\\.""", ".")

    @Test
    fun `every declared extension is matched by the manifest`() {
        assertEquals(WMFileTypes.EXTENSIONS.toSet(), patterns.map(::extensionOf).toSet())
    }

    @Test
    fun `each extension has the dotted-name variants pathPattern needs`() {
        // PatternMatcher's simple glob does not backtrack, so ".*\.wmtheme\.json"
        // matches "theme.wmtheme.json" but not "my.theme.wmtheme.json". Each
        // extra leading ".*\." buys one more dot in the name; four variants per
        // filter, across the two filters, is eight patterns per extension.
        val byExtension = patterns.groupBy(::extensionOf)
        for (extension in WMFileTypes.EXTENSIONS) {
            assertEquals(
                "wrong number of pathPattern variants for .$extension",
                8,
                byExtension[extension]?.size,
            )
        }
    }

    @Test
    fun `no pattern claims a general file extension`() {
        // The whole point of the compound extensions: opening someone else's
        // .json must not offer this app.
        //
        // One extension here is deliberately not ours. A `.flex` is a
        // FlorisBoard theme, which this app converts, and claiming it is how
        // someone moving over opens the file they already have. It is safe to
        // claim for the same reason the others are: it names one format
        // precisely, rather than a container half the device writes.
        // FlorisBoard's own filter still matches, so with both installed the
        // user is asked which app should open it.
        for (pattern in patterns) {
            val extension = extensionOf(pattern)
            assertTrue(
                "pattern $pattern claims files that are not WMKeyboard's",
                extension.startsWith("wm") || extension in FOREIGN_EXTENSIONS,
            )
        }
    }

    private companion object {
        /** Other apps' formats this one deliberately reads. */
        val FOREIGN_EXTENSIONS = setOf("flex")
    }

    @Test
    fun `both a typed and an untyped filter are declared`() {
        // An intent carrying no MIME type only ever matches a filter that
        // declares none, so the associations are duplicated with and without
        // the wildcard type. Losing one silently halves the coverage.
        val filters = manifest.split("<intent-filter").filter { it.contains("pathPattern") }
        assertEquals(2, filters.size)
        assertEquals(1, filters.count { it.contains("""android:mimeType="*/*"""") })
    }

    @Test
    fun `path matching is only enforced when an authority is declared`() {
        // IntentFilter checks paths inside its authority branch: drop host="*"
        // and every pattern above stops being applied, which turns these filters
        // into "any content URI of any type".
        for (filter in manifest.split("<intent-filter").filter { it.contains("pathPattern") }) {
            assertTrue(
                "a path-matching filter is missing android:host",
                filter.contains("""android:host="*""""),
            )
        }
    }
}
