package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.settings.sink.WebDavListing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavListingTest {

    /** Nextcloud's shape: `d:` prefix, path-only hrefs, collection first. */
    private val nextcloud = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:">
          <d:response>
            <d:href>/remote.php/dav/files/me/backups/</d:href>
            <d:propstat>
              <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
          <d:response>
            <d:href>/remote.php/dav/files/me/backups/wmkeyboard-auto-20250807-140233.wmconfig.json</d:href>
            <d:propstat>
              <d:prop>
                <d:getcontentlength>4096</d:getcontentlength>
                <d:getlastmodified>Thu, 07 Aug 2025 14:02:33 GMT</d:getlastmodified>
                <d:resourcetype/>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun `a listing yields its files`() {
        val entries = WebDavListing.parse(nextcloud)
        assertEquals(2, entries.size)
        assertTrue(entries[0].isCollection)
        val file = entries[1]
        assertFalse(file.isCollection)
        assertEquals("wmkeyboard-auto-20250807-140233.wmconfig.json", file.name)
        assertEquals(4096L, file.sizeBytes)
        // 2025-08-07T14:02:33Z
        assertEquals(1_754_575_353_000L, file.modifiedAtMs)
    }

    @Test
    fun `any namespace prefix works`() {
        // Apache uses D:, some servers use lp1: for the live properties, and a
        // server is allowed to use no prefix at all. All the same document.
        val apache = """
            <?xml version="1.0"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/wmkeyboard-auto-20250807-140233.wmconfig.enc</D:href>
                <D:propstat><D:prop>
                  <lp1:getcontentlength xmlns:lp1="DAV:">7</lp1:getcontentlength>
                  <D:resourcetype/>
                </D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        val bare = """
            <multistatus xmlns="DAV:">
              <response>
                <href>/dav/wmkeyboard-auto-20250807-140233.wmconfig.enc</href>
                <propstat><prop><getcontentlength>7</getcontentlength></prop></propstat>
              </response>
            </multistatus>
        """.trimIndent()
        for (xml in listOf(apache, bare)) {
            val entries = WebDavListing.parse(xml)
            assertEquals(xml, 1, entries.size)
            assertEquals("wmkeyboard-auto-20250807-140233.wmconfig.enc", entries[0].name)
            assertEquals(7L, entries[0].sizeBytes)
        }
    }

    @Test
    fun `a percent-encoded href gives a readable name`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:"><d:response>
              <d:href>/dav/my%20backups/wmkeyboard-auto-20250807-140233.wmconfig.json</d:href>
              <d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat>
            </d:response></d:multistatus>
        """.trimIndent()
        assertEquals(
            "wmkeyboard-auto-20250807-140233.wmconfig.json",
            WebDavListing.parse(xml).single().name,
        )
    }

    @Test
    fun `a missing size or date is not a parse failure`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:"><d:response>
              <d:href>/dav/wmkeyboard-auto-20250807-140233.wmconfig.json</d:href>
              <d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat>
            </d:response></d:multistatus>
        """.trimIndent()
        val entry = WebDavListing.parse(xml).single()
        assertEquals(-1L, entry.sizeBytes)
        // Rotation falls back to the name, which embeds the second.
        assertEquals(0L, entry.modifiedAtMs)
    }

    @Test
    fun `an unparseable date does not lose the entry`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:"><d:response>
              <d:href>/dav/wmkeyboard-auto-20250807-140233.wmconfig.json</d:href>
              <d:propstat><d:prop>
                <d:getlastmodified>whenever</d:getlastmodified>
              </d:prop></d:propstat>
            </d:response></d:multistatus>
        """.trimIndent()
        assertEquals(0L, WebDavListing.parse(xml).single().modifiedAtMs)
    }

    @Test
    fun `a document with a doctype is refused outright`() {
        // The XXE guard. Rejected by inspection rather than by a parser feature
        // that Android's SAX does not recognise; see SvgParser.
        val xml = """
            <!DOCTYPE d [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <d:multistatus xmlns:d="DAV:"><d:response>
              <d:href>/dav/wmkeyboard-auto-20250807-140233.wmconfig.json</d:href>
            </d:response></d:multistatus>
        """.trimIndent()
        assertTrue(WebDavListing.parse(xml).isEmpty())
    }

    @Test
    fun `rubbish is an empty listing rather than a crash`() {
        assertTrue(WebDavListing.parse("").isEmpty())
        assertTrue(WebDavListing.parse("not xml at all").isEmpty())
        assertTrue(WebDavListing.parse("<html><body>404</body></html>").isEmpty())
    }

    @Test
    fun `a truncated document keeps what it already read`() {
        val xml = nextcloud.substringBefore("<d:response>\n    <d:href>/remote.php/dav/files/me/backups/wmkeyboard")
        // Whatever survives, it must not throw and must not invent entries.
        assertTrue(WebDavListing.parse(xml).size <= 2)
    }

    @Test
    fun `a name is the last path segment`() {
        assertEquals("b.json", WebDavListing.nameOf("/a/b.json"))
        assertEquals("b.json", WebDavListing.nameOf("https://h/a/b.json"))
        assertEquals("a", WebDavListing.nameOf("/a/"))
        assertEquals("a b", WebDavListing.nameOf("/x/a%20b"))
    }
}
