package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.settings.sink.S3Listing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class S3ListingTest {

    private val page = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
          <Name>backups</Name>
          <IsTruncated>false</IsTruncated>
          <Contents>
            <Key>keyboard/wmkeyboard-auto-20250807-140233.wmconfig.json</Key>
            <LastModified>2025-08-07T14:02:33.000Z</LastModified>
            <Size>4096</Size>
          </Contents>
          <Contents>
            <Key>keyboard/wmkeyboard-auto-20250806-140233.wmconfig.enc</Key>
            <LastModified>2025-08-06T14:02:33.000Z</LastModified>
            <Size>4112</Size>
          </Contents>
        </ListBucketResult>
    """.trimIndent()

    @Test
    fun `a page yields its keys`() {
        val parsed = S3Listing.parse(page)
        assertEquals(2, parsed.keys.size)
        assertEquals("keyboard/wmkeyboard-auto-20250807-140233.wmconfig.json", parsed.keys[0].key)
        assertEquals(4096L, parsed.keys[0].sizeBytes)
        assertEquals(1_754_575_353_000L, parsed.keys[0].modifiedAtMs)
        assertNull(parsed.continuationToken)
    }

    @Test
    fun `a truncated page carries its continuation token`() {
        val truncated = page.replace(
            "<IsTruncated>false</IsTruncated>",
            "<IsTruncated>true</IsTruncated><NextContinuationToken>abc123</NextContinuationToken>",
        )
        assertEquals("abc123", S3Listing.parse(truncated).continuationToken)
    }

    @Test
    fun `an error document is an empty page rather than a crash`() {
        val error = """
            <?xml version="1.0"?>
            <Error><Code>AccessDenied</Code><Message>Access Denied</Message></Error>
        """.trimIndent()
        val parsed = S3Listing.parse(error)
        assertTrue(parsed.keys.isEmpty())
        assertNull(parsed.continuationToken)
    }

    @Test
    fun `rubbish is an empty page`() {
        assertTrue(S3Listing.parse("").keys.isEmpty())
        assertTrue(S3Listing.parse("not xml").keys.isEmpty())
    }

    @Test
    fun `a doctype is refused outright`() {
        val hostile = """
            <!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <ListBucketResult><Contents><Key>a</Key></Contents></ListBucketResult>
        """.trimIndent()
        assertTrue(S3Listing.parse(hostile).keys.isEmpty())
    }

    @Test
    fun `timestamps parse with and without fractional seconds`() {
        assertEquals(1_754_575_353_000L, S3Listing.parseIso8601("2025-08-07T14:02:33.000Z"))
        assertEquals(1_754_575_353_000L, S3Listing.parseIso8601("2025-08-07T14:02:33Z"))
        assertEquals(0L, S3Listing.parseIso8601("whenever"))
    }
}
