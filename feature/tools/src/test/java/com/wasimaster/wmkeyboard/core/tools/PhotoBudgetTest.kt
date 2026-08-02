package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The request budget and the page cache: the two things standing between a
 * 50-an-hour key and a picker that stops working an hour into the day.
 */
class PhotoBudgetTest {

    private val start = 1_700_000_000_000L
    private val hour = 3_600_000L

    @Before
    fun clearState() {
        PhotoRateLimit.reset()
        PhotoCache.clear()
    }

    // ---- header parsing -----------------------------------------------

    @Test
    fun `an unsplash budget comes from its two headers`() {
        val budget = PhotoRateLimit.parse(
            PhotoSource.UNSPLASH,
            mapOf("X-Ratelimit-Limit" to "50", "X-Ratelimit-Remaining" to "37"),
            start,
        )
        assertEquals(50, budget?.limit)
        assertEquals(37, budget?.remaining)
        // Unsplash sends no reset header, so the hour it documents is assumed.
        assertEquals(start + hour, budget?.resetAtMs)
    }

    @Test
    fun `a header in another case is still read`() {
        val budget = PhotoRateLimit.parse(
            PhotoSource.UNSPLASH,
            mapOf("x-ratelimit-remaining" to "5"),
            start,
        )
        assertEquals(5, budget?.remaining)
    }

    @Test
    fun `an unreadable header means unknown and never means spent`() {
        // A proxy that strips or mangles headers must not be able to shut the
        // feature down: 0 would read as "no requests left".
        assertNull(PhotoRateLimit.parse(PhotoSource.UNSPLASH, mapOf("X-Ratelimit-Remaining" to "abc"), start))
        assertNull(PhotoRateLimit.parse(PhotoSource.UNSPLASH, emptyMap(), start))
    }

    @Test
    fun `a pexels reset is capped at an hour out`() {
        // Their reset points at the monthly rollover, which is no use for
        // gating the hourly cap.
        val monthAway = (start / 1000) + 30 * 24 * 3600
        val budget = PhotoRateLimit.parse(
            PhotoSource.PEXELS,
            mapOf("X-Ratelimit-Remaining" to "19847", "X-Ratelimit-Reset" to monthAway.toString()),
            start,
        )
        assertEquals(start + hour, budget?.resetAtMs)
    }

    // ---- what the budget gates ----------------------------------------

    @Test
    fun `an unknown budget blocks nothing`() {
        assertFalse(PhotoRateLimit.isSpent(PhotoSource.UNSPLASH, start))
        assertTrue(PhotoRateLimit.canPrefetch(PhotoSource.UNSPLASH, start))
    }

    @Test
    fun `the prefetcher stops before the picker does`() {
        PhotoRateLimit.recordHeaders(PhotoSource.UNSPLASH, mapOf("X-Ratelimit-Remaining" to "8"), start)
        // Eight left: a user scrolling still gets served, an unattended top-up
        // does not get to spend the last of it.
        assertFalse(PhotoRateLimit.canPrefetch(PhotoSource.UNSPLASH, start))
        assertFalse(PhotoRateLimit.isSpent(PhotoSource.UNSPLASH, start))
        assertFalse(PhotoRateLimit.isLow(PhotoSource.UNSPLASH, start))
    }

    @Test
    fun `the picker stops short of the wall`() {
        PhotoRateLimit.recordHeaders(PhotoSource.UNSPLASH, mapOf("X-Ratelimit-Remaining" to "2"), start)
        assertTrue(PhotoRateLimit.isLow(PhotoSource.UNSPLASH, start))
        assertFalse(PhotoRateLimit.isSpent(PhotoSource.UNSPLASH, start))
    }

    @Test
    fun `a budget is forgotten once its window has passed`() {
        PhotoRateLimit.recordHeaders(PhotoSource.UNSPLASH, mapOf("X-Ratelimit-Remaining" to "0"), start)
        assertTrue(PhotoRateLimit.isSpent(PhotoSource.UNSPLASH, start))
        assertFalse(PhotoRateLimit.isSpent(PhotoSource.UNSPLASH, start + hour + 1))
    }

    @Test
    fun `a refusal with no headers is assumed to mean the budget is gone`() {
        // Pexels answers an error with no budget information at all, so the
        // pessimistic reading is the only safe one.
        PhotoRateLimit.recordDenial(PhotoSource.PEXELS, start)
        assertTrue(PhotoRateLimit.isSpent(PhotoSource.PEXELS, start))
        assertFalse(PhotoRateLimit.isSpent(PhotoSource.PEXELS, start + hour + 1))
    }

    @Test
    fun `the pexels hourly cap is counted locally because no header reports it`() {
        // Their header would still be reading 19,847 of the monthly quota.
        PhotoRateLimit.recordHeaders(PhotoSource.PEXELS, mapOf("X-Ratelimit-Remaining" to "19847"), start)
        repeat(200) { PhotoRateLimit.recordRequest(PhotoSource.PEXELS, start + it) }
        assertEquals(0, PhotoRateLimit.remaining(PhotoSource.PEXELS, start + 200))
        assertTrue(PhotoRateLimit.isSpent(PhotoSource.PEXELS, start + 200))
        // The oldest calls fall out of the window an hour later.
        assertFalse(PhotoRateLimit.isSpent(PhotoSource.PEXELS, start + hour + 1000))
    }

    @Test
    fun `an unsplash request is not counted against the pexels hour`() {
        repeat(200) { PhotoRateLimit.recordRequest(PhotoSource.UNSPLASH, start + it) }
        assertNull(PhotoRateLimit.remaining(PhotoSource.UNSPLASH, start + 200))
    }

    // ---- telling quota from a bad key ---------------------------------

    @Test
    fun `unsplash 403 with an empty budget is a quota failure not a bad key`() {
        val spent = ToolHttpException(
            messageRes = 0,
            status = 403,
            headers = mapOf("X-Ratelimit-Limit" to "50", "X-Ratelimit-Remaining" to "0"),
        )
        assertTrue(PhotoRateLimit.isQuotaFailure(PhotoSource.UNSPLASH, spent))
        assertTrue(photoFailureOf(PhotoSource.UNSPLASH, spent, start) is PhotoFailure.QuotaSpent)
    }

    @Test
    fun `unsplash 403 with requests still left really is a bad key`() {
        val rejected = ToolHttpException(
            messageRes = 0,
            status = 403,
            headers = mapOf("X-Ratelimit-Remaining" to "42"),
        )
        assertFalse(PhotoRateLimit.isQuotaFailure(PhotoSource.UNSPLASH, rejected))
        assertTrue(photoFailureOf(PhotoSource.UNSPLASH, rejected, start) is PhotoFailure.KeyRejected)
    }

    @Test
    fun `a 403 with no headers at all is read as a bad key`() {
        val bare = ToolHttpException(messageRes = 0, status = 403)
        assertFalse(PhotoRateLimit.isQuotaFailure(PhotoSource.UNSPLASH, bare))
        assertTrue(photoFailureOf(PhotoSource.UNSPLASH, bare, start) is PhotoFailure.KeyRejected)
    }

    @Test
    fun `a 401 is always a bad key`() {
        val unauthorized = ToolHttpException(messageRes = 0, status = 401)
        assertTrue(photoFailureOf(PhotoSource.PEXELS, unauthorized, start) is PhotoFailure.KeyRejected)
    }

    @Test
    fun `pexels signals a spent budget with 429 and no headers`() {
        val throttled = ToolHttpException(messageRes = 0, status = 429)
        assertTrue(PhotoRateLimit.isQuotaFailure(PhotoSource.PEXELS, throttled))
        assertTrue(photoFailureOf(PhotoSource.PEXELS, throttled, start) is PhotoFailure.QuotaSpent)
    }

    @Test
    fun `being offline is reported as being offline`() {
        val failure = photoFailureOf(PhotoSource.UNSPLASH, java.net.UnknownHostException("api"), start)
        assertTrue(failure is PhotoFailure.Offline)
    }

    @Test
    fun `the provider's own words win over ours`() {
        val withMessage = ToolHttpException(messageRes = 0, status = 500, apiMessage = "Server on fire")
        val failure = photoFailureOf(PhotoSource.UNSPLASH, withMessage, start) as PhotoFailure.Other
        assertEquals(PhotoText.Literal("Server on fire"), failure.text)
    }

    // ---- the page cache -----------------------------------------------

    @Test
    fun `a query differing only in case or spacing is the same request`() {
        val a = PhotoCache.keyOf(PhotoSource.UNSPLASH, PhotoQuery(text = "Cats"))
        val b = PhotoCache.keyOf(PhotoSource.UNSPLASH, PhotoQuery(text = "cats "))
        assertEquals(a, b)
    }

    @Test
    fun `the same query to a different provider is a different request`() {
        val query = PhotoQuery(text = "cats")
        assertFalse(
            PhotoCache.keyOf(PhotoSource.UNSPLASH, query) == PhotoCache.keyOf(PhotoSource.PEXELS, query),
        )
    }

    @Test
    fun `each filter and page is its own entry`() {
        val base = PhotoQuery(text = "cats")
        val keys = setOf(
            PhotoCache.keyOf(PhotoSource.UNSPLASH, base),
            PhotoCache.keyOf(PhotoSource.UNSPLASH, base.copy(page = 2)),
            PhotoCache.keyOf(PhotoSource.UNSPLASH, base.copy(color = PhotoColor.BLUE)),
            PhotoCache.keyOf(PhotoSource.UNSPLASH, base.copy(orientation = PhotoOrientation.PORTRAIT)),
            PhotoCache.keyOf(PhotoSource.UNSPLASH, base.copy(safe = false)),
            PhotoCache.keyOf(PhotoSource.UNSPLASH, base.copy(topicId = "nature")),
        )
        assertEquals(6, keys.size)
    }

    @Test
    fun `a cached search page expires`() {
        val query = PhotoQuery(text = "cats")
        PhotoCache.put(PhotoSource.UNSPLASH, query, emptyPage(), start)
        assertTrue(PhotoCache.get(PhotoSource.UNSPLASH, query, start + 60_000) != null)
        assertNull(PhotoCache.get(PhotoSource.UNSPLASH, query, start + 16 * 60_000))
    }

    @Test
    fun `a curated feed is held longer than a search`() {
        val feed = PhotoQuery(text = "")
        PhotoCache.put(PhotoSource.UNSPLASH, feed, emptyPage(), start)
        assertTrue(PhotoCache.get(PhotoSource.UNSPLASH, feed, start + 30 * 60_000) != null)
        assertNull(PhotoCache.get(PhotoSource.UNSPLASH, feed, start + 61 * 60_000))
    }

    @Test
    fun `the cache stops growing`() {
        repeat(100) { page ->
            PhotoCache.put(PhotoSource.UNSPLASH, PhotoQuery(text = "cats", page = page), emptyPage(), start)
        }
        // The first pages were evicted; the last ones are still there.
        assertNull(PhotoCache.get(PhotoSource.UNSPLASH, PhotoQuery(text = "cats", page = 0), start))
        assertTrue(PhotoCache.get(PhotoSource.UNSPLASH, PhotoQuery(text = "cats", page = 99), start) != null)
    }

    private fun emptyPage() = PhotoPage(
        items = emptyList(),
        page = 1,
        totalResults = 0,
        hasMore = false,
        source = PhotoSource.UNSPLASH,
    )
}
