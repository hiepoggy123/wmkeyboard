package com.wasimaster.wmkeyboard.core.settings.sink

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4, which is what every S3-compatible service checks.
 *
 * Pure, and apart from the sink, because it is the part that is exactly right
 * or completely broken with nothing in between: a signature is either accepted
 * or the request is a 403, and no amount of looking at the code tells you
 * which. Kept here so it can be run against Amazon's own published test
 * vectors.
 *
 * Hand-written rather than taken from the AWS SDK, which is tens of megabytes
 * and brings a dependency tree of its own. The algorithm is four hashes and a
 * canonical string; the SDK's value is everything else it does, none of which
 * this app wants.
 */
object AwsSigV4 {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"
    private const val TERMINATOR = "aws4_request"

    /** The hash of an empty body, which S3 wants spelled out rather than omitted. */
    const val EMPTY_BODY_SHA256 =
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    /** What the signer needs to know about one request. */
    data class Request(
        val method: String,
        /** Already percent-encoded, and starting with a slash. */
        val path: String,
        /** Canonical form is sorted by key; the signer does the sorting. */
        val query: Map<String, String> = emptyMap(),
        /** Lower-cased header names to values. `host` must be among them. */
        val headers: Map<String, String>,
        /** Hex SHA-256 of the body, or [EMPTY_BODY_SHA256]. */
        val bodySha256: String,
    )

    /**
     * The headers to add to [request] so a service will accept it.
     *
     * Returns `Authorization` plus the two S3 always wants alongside it, ready
     * to be merged into the request being built.
     */
    fun sign(
        request: Request,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        timestampMs: Long,
    ): Map<String, String> {
        val amzDate = format("yyyyMMdd'T'HHmmss'Z'", timestampMs)
        val dateStamp = format("yyyyMMdd", timestampMs)

        // x-amz-date and x-amz-content-sha256 are signed, so they have to be in
        // the map before the canonical headers are built rather than added to
        // the request afterwards.
        val headers = buildMap {
            putAll(request.headers.mapKeys { it.key.lowercase(Locale.US) })
            put("x-amz-date", amzDate)
            put("x-amz-content-sha256", request.bodySha256)
        }.toSortedMap()

        val signedHeaders = headers.keys.joinToString(";")
        val canonicalHeaders = headers.entries.joinToString("") { (name, value) ->
            "$name:${value.trim()}\n"
        }
        val canonicalQuery = request.query.toSortedMap().entries.joinToString("&") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }
        val canonicalRequest = listOf(
            request.method,
            request.path,
            canonicalQuery,
            canonicalHeaders,
            signedHeaders,
            request.bodySha256,
        ).joinToString("\n")

        val scope = "$dateStamp/$region/$SERVICE/$TERMINATOR"
        val stringToSign = listOf(
            ALGORITHM,
            amzDate,
            scope,
            hex(sha256(canonicalRequest.toByteArray(Charsets.UTF_8))),
        ).joinToString("\n")

        var key = "AWS4$secretAccessKey".toByteArray(Charsets.UTF_8)
        for (part in listOf(dateStamp, region, SERVICE, TERMINATOR)) {
            key = hmac(key, part)
        }
        val signature = hex(hmac(key, stringToSign))

        return mapOf(
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to request.bodySha256,
            "Authorization" to "$ALGORITHM Credential=$accessKeyId/$scope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature",
        )
    }

    /**
     * Percent-encoding as the canonical request defines it, which is not what
     * `URLEncoder` does: a space is `%20` rather than `+`, and the four
     * unreserved marks are left alone. Getting this wrong is a 403 with no
     * explanation.
     */
    fun encode(value: String, encodeSlash: Boolean = true): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val ch = byte.toInt().toChar()
            when {
                ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' -> append(ch)
                ch == '_' || ch == '-' || ch == '~' || ch == '.' -> append(ch)
                ch == '/' && !encodeSlash -> append(ch)
                else -> append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }

    fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (byte in bytes) append("%02x".format(byte.toInt() and 0xFF))
    }

    private fun hmac(key: ByteArray, data: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key, "HmacSHA256"))
        }.doFinal(data.toByteArray(Charsets.UTF_8))

    /** [Locale.US] and UTC: the format has fixed digits and a literal `Z`. */
    private fun format(pattern: String, timestampMs: Long): String =
        SimpleDateFormat(pattern, Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(timestampMs))
}
