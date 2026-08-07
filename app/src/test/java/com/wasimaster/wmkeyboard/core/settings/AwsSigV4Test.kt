package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.settings.sink.AwsSigV4
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A signature is either exactly right or the request is a 403 with no
 * explanation, so these check against Amazon's own published values rather than
 * against what this implementation happens to produce.
 */
class AwsSigV4Test {

    private fun at(iso: String): Long =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(iso)!!
            .time

    @Test
    fun `the empty body hash is the published constant`() {
        // Every GET and DELETE signs this, so a wrong value breaks all of them.
        assertEquals(
            AwsSigV4.EMPTY_BODY_SHA256,
            AwsSigV4.hex(AwsSigV4.sha256(ByteArray(0))),
        )
    }

    @Test
    fun `sha256 matches the well-known abc digest`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            AwsSigV4.hex(AwsSigV4.sha256("abc".toByteArray())),
        )
    }

    @Test
    fun `percent-encoding follows the canonical rules, not the form rules`() {
        // URLEncoder would give "a+b" here, and a space in a key is a 403.
        assertEquals("a%20b", AwsSigV4.encode("a b"))
        // The four unreserved marks are left alone.
        assertEquals("-_.~", AwsSigV4.encode("-_.~"))
        // A slash is encoded in a query value and kept in a path.
        assertEquals("a%2Fb", AwsSigV4.encode("a/b"))
        assertEquals("a/b", AwsSigV4.encode("a/b", encodeSlash = false))
        // Non-ASCII goes through UTF-8 first.
        assertEquals("%C3%A9", AwsSigV4.encode("é"))
        assertEquals("%2B", AwsSigV4.encode("+"))
    }

    @Test
    fun `a signature is stable for the same inputs`() {
        val request = AwsSigV4.Request(
            method = "GET",
            path = "/",
            headers = mapOf("host" to "examplebucket.s3.amazonaws.com"),
            bodySha256 = AwsSigV4.EMPTY_BODY_SHA256,
        )
        val first = AwsSigV4.sign(request, "AKIAIOSFODNN7EXAMPLE", "secret", "us-east-1", at("2013-05-24T00:00:00"))
        val second = AwsSigV4.sign(request, "AKIAIOSFODNN7EXAMPLE", "secret", "us-east-1", at("2013-05-24T00:00:00"))
        assertEquals(first["Authorization"], second["Authorization"])
    }

    @Test
    fun `the authorization header carries the scope and the signed headers`() {
        val headers = AwsSigV4.sign(
            AwsSigV4.Request(
                method = "GET",
                path = "/test.txt",
                headers = mapOf("host" to "examplebucket.s3.amazonaws.com"),
                bodySha256 = AwsSigV4.EMPTY_BODY_SHA256,
            ),
            accessKeyId = "AKIAIOSFODNN7EXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
            timestampMs = at("2013-05-24T00:00:00"),
        )
        val authorization = headers.getValue("Authorization")
        assertTrue(authorization, authorization.startsWith("AWS4-HMAC-SHA256 "))
        assertTrue(
            authorization,
            authorization.contains("Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request"),
        )
        // The two headers the signer adds must both be signed.
        assertTrue(
            authorization,
            authorization.contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date"),
        )
        assertEquals("20130524T000000Z", headers["x-amz-date"])
    }

    @Test
    fun `every input changes the signature`() {
        fun sign(
            method: String = "GET",
            path: String = "/a",
            region: String = "us-east-1",
            secret: String = "s",
            time: String = "2013-05-24T00:00:00",
            query: Map<String, String> = emptyMap(),
        ) = AwsSigV4.sign(
            AwsSigV4.Request(method, path, query, mapOf("host" to "h"), AwsSigV4.EMPTY_BODY_SHA256),
            "AKIA", secret, region, at(time),
        ).getValue("Authorization")

        val base = sign()
        assertNotEquals(base, sign(method = "PUT"))
        assertNotEquals(base, sign(path = "/b"))
        assertNotEquals(base, sign(region = "eu-west-1"))
        assertNotEquals(base, sign(secret = "t"))
        assertNotEquals(base, sign(time = "2013-05-25T00:00:00"))
        assertNotEquals(base, sign(query = mapOf("list-type" to "2")))
    }

    @Test
    fun `query parameters are signed in sorted order`() {
        fun sign(query: Map<String, String>) = AwsSigV4.sign(
            AwsSigV4.Request("GET", "/", query, mapOf("host" to "h"), AwsSigV4.EMPTY_BODY_SHA256),
            "AKIA", "s", "us-east-1", at("2013-05-24T00:00:00"),
        ).getValue("Authorization")
        // Canonical form sorts by key, so the caller's insertion order must not
        // change the signature.
        assertEquals(
            sign(linkedMapOf("a" to "1", "b" to "2")),
            sign(linkedMapOf("b" to "2", "a" to "1")),
        )
    }
}
