package com.wasimaster.wmkeyboard.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fetcher that answers from a script, and counts what it was asked. */
private class FakeFetcher(private vararg val answers: FetchResult) : ReleaseFetcher {
    val etagsSent = mutableListOf<String?>()
    var calls = 0
    override fun fetch(etag: String?): FetchResult {
        etagsSent += etag
        return answers[minOf(calls++, answers.lastIndex)]
    }
}

/** The release cache, in memory. */
private class FakeCache(private var body: String? = null) : ReleaseCache {
    var writes = 0
    override fun read(): String? = body
    override fun write(body: String) {
        this.body = body
        writes++
    }
    override fun exists(): Boolean = body != null
    override fun clear() {
        body = null
    }
}

class GithubUpdateCheckerTest {

    private fun body(code: Int = 14, version: String = "0.5.3"): String = """
        [{
          "tag_name": "v$version",
          "draft": false,
          "prerelease": false,
          "published_at": "2026-09-07T07:40:12Z",
          "html_url": "https://github.com/wasi-master/WMKeyboard/releases/tag/v$version",
          "assets": [{
            "name": "wmkeyboard-$version-vc$code-full-arm64-v8a.apk",
            "size": 100,
            "digest": "sha256:${"a".repeat(64)}",
            "browser_download_url": "https://github.com/x/y/releases/download/v$version/wmkeyboard-$version-vc$code-full-arm64-v8a.apk"
          }]
        }]
    """.trimIndent()

    private fun check(
        checker: GithubUpdateChecker,
        installed: Int = 13,
        storedEtag: String? = null,
        includePrereleases: Boolean = false,
        onEtag: (String?) -> Unit = {},
    ) = checker.check(
        installedVersionCode = installed,
        flavor = "full",
        supportedAbis = listOf("arm64-v8a"),
        includePrereleases = includePrereleases,
        storedEtag = storedEtag,
        onEtag = onEtag,
    )

    @Test
    fun `a fresh list is cached and offered`() {
        val cache = FakeCache()
        var etag: String? = null
        val outcome = check(
            GithubUpdateChecker(FakeFetcher(FetchResult.Fresh(body(), "W/abc")), cache),
            onEtag = { etag = it },
        )
        assertEquals(14, (outcome as CheckOutcome.Available).candidate.versionCode)
        assertEquals(1, cache.writes)
        assertEquals("W/abc", etag)
    }

    @Test
    fun `an unchanged list is answered from the cache without a second request`() {
        val fetcher = FakeFetcher(FetchResult.NotModified)
        val outcome = check(
            GithubUpdateChecker(fetcher, FakeCache(body())),
            storedEtag = "W/abc",
        )
        assertEquals(14, (outcome as CheckOutcome.Available).candidate.versionCode)
        assertEquals(1, fetcher.calls)
        assertEquals(listOf<String?>("W/abc"), fetcher.etagsSent)
    }

    @Test
    fun `an ETag is never sent without the body it describes`() {
        val fetcher = FakeFetcher(FetchResult.Fresh(body(), null))
        check(GithubUpdateChecker(fetcher, FakeCache()), storedEtag = "W/abc")
        assertNull(fetcher.etagsSent.single())
    }

    @Test
    fun `an unchanged list with nothing cached forgets the ETag instead of looping`() {
        var etag: String? = "W/abc"
        val outcome = check(
            GithubUpdateChecker(FakeFetcher(FetchResult.NotModified), FakeCache()),
            storedEtag = "W/abc",
            onEtag = { etag = it },
        )
        assertEquals(CheckOutcome.Failed, outcome)
        assertNull(etag)
    }

    @Test
    fun `being rate limited carries the time to wait`() {
        val outcome = check(
            GithubUpdateChecker(FakeFetcher(FetchResult.RateLimited(9_999L)), FakeCache()),
        )
        assertEquals(9_999L, (outcome as CheckOutcome.RateLimited).untilMillis)
    }

    @Test
    fun `a failure is a failure, and does not empty a good cache`() {
        val cache = FakeCache(body())
        val outcome = check(GithubUpdateChecker(FakeFetcher(FetchResult.Failed), cache))
        assertEquals(CheckOutcome.Failed, outcome)
        assertTrue(cache.exists())
    }

    @Test
    fun `a body that will not decode is dropped rather than answered from forever`() {
        val cache = FakeCache()
        val outcome = check(
            GithubUpdateChecker(FakeFetcher(FetchResult.Fresh("not json", null)), cache),
        )
        assertEquals(CheckOutcome.Failed, outcome)
        assertFalse(cache.exists())
    }

    @Test
    fun `the current version reads as up to date`() {
        val outcome = check(
            GithubUpdateChecker(FakeFetcher(FetchResult.Fresh(body(), null)), FakeCache()),
            installed = 14,
        )
        assertEquals(CheckOutcome.UpToDate, outcome)
    }

    @Test
    fun `the cache answers on its own, with no request at all`() {
        val fetcher = FakeFetcher(FetchResult.Failed)
        val outcome = GithubUpdateChecker(fetcher, FakeCache(body())).fromCache(
            installedVersionCode = 13,
            flavor = "full",
            supportedAbis = listOf("arm64-v8a"),
            includePrereleases = false,
        )
        assertEquals(14, (outcome as CheckOutcome.Available).candidate.versionCode)
        assertEquals(0, fetcher.calls)
    }

    // ---- what an offer becomes on screen ----

    private val candidate = UpdateCandidate(
        versionCode = 14,
        versionName = "0.5.3",
        tag = "v0.5.3",
        assetName = "wmkeyboard-0.5.3-vc14-full-arm64-v8a.apk",
        url = "https://example.test/a.apk",
        sizeBytes = 100,
        sha256 = "a".repeat(64),
        publishedAtMillis = 0L,
        releaseUrl = "https://example.test/release",
    )

    private fun offer(
        snoozed: Boolean = false,
        autoPrompt: Boolean = true,
        allowPrompt: Boolean = true,
        userAsked: Boolean = false,
        publishedAtMillis: Long? = 0L,
        now: Long = 0L,
        fileVerified: Boolean = false,
    ) = GithubUpdateChecker.offer(
        candidate = candidate.copy(publishedAtMillis = publishedAtMillis),
        snoozed = snoozed,
        autoPrompt = autoPrompt,
        allowPrompt = allowPrompt,
        userAsked = userAsked,
        now = now,
        fileVerified = fileVerified,
    )

    @Test
    fun `a fresh release is offered quietly, on a card`() {
        val state = offer() as UpdateState.Available
        assertFalse(state.promptOpen)
        assertFalse(state.dismissed)
        assertFalse(state.immediate)
        assertEquals("0.5.3", state.versionName)
        assertEquals("https://example.test/release", state.releaseUrl)
    }

    @Test
    fun `a release a week old earns a dialog`() {
        val week = 7L * 24 * 60 * 60 * 1000
        assertTrue((offer(now = week) as UpdateState.Available).promptOpen)
    }

    @Test
    fun `no dialog when the user turned prompts off, or already said not now`() {
        val week = 7L * 24 * 60 * 60 * 1000
        assertFalse((offer(now = week, autoPrompt = false) as UpdateState.Available).promptOpen)
        assertFalse((offer(now = week, snoozed = true) as UpdateState.Available).promptOpen)
    }

    @Test
    fun `a check the user asked for never answers with a dialog`() {
        val week = 7L * 24 * 60 * 60 * 1000
        // They are looking at the row that is about to answer them.
        assertFalse((offer(now = week, userAsked = true) as UpdateState.Available).promptOpen)
    }

    @Test
    fun `a dialog already shown this session is not shown again`() {
        val week = 7L * 24 * 60 * 60 * 1000
        assertFalse((offer(now = week, allowPrompt = false) as UpdateState.Available).promptOpen)
    }

    @Test
    fun `not now hides the card but leaves the offer`() {
        val state = offer(snoozed = true) as UpdateState.Available
        assertTrue(state.dismissed)
        assertEquals(14, state.versionCode)
    }

    @Test
    fun `a release with no date is never loud about it`() {
        assertFalse(
            (offer(publishedAtMillis = null, now = Long.MAX_VALUE / 2) as UpdateState.Available)
                .promptOpen,
        )
    }

    @Test
    fun `a download that already finished skips all of it`() {
        assertEquals(UpdateState.Downloaded, offer(fileVerified = true))
    }

    @Test
    fun `a candidate survives a round trip through the preferences`() {
        val restored = GithubUpdateChecker.decodeCandidate(
            GithubUpdateChecker.encode(candidate),
        )
        assertEquals(candidate, restored)
    }

    @Test
    fun `a preference written by some other version decodes to nothing`() {
        assertNull(GithubUpdateChecker.decodeCandidate(null))
        assertNull(GithubUpdateChecker.decodeCandidate("not json"))
    }
}
