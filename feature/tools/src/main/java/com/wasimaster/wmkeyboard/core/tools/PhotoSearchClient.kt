package com.wasimaster.wmkeyboard.core.tools

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * The one place the two photo providers are told apart.
 *
 * A `when` over an enum rather than an interface with two implementations,
 * following [GifSource]: the set is closed at two, every other network client
 * in this app is a top-level `object`, and there is no container to hold
 * instances in. The one thing done differently from the GIF pipeline is that
 * this dispatch lives here instead of inside the keyboard service, because both
 * the settings picker and the background rotation need it.
 *
 * Everything that protects the request budget sits in front of the providers
 * here — cache, in-flight coalescing and the rate-limit gate — so no caller can
 * skip it by accident.
 */
object PhotoSearchClient {

    /** Keys the effective API key for each provider off the caller's settings. */
    fun interface Keys {
        fun keyFor(source: PhotoSource): String
    }

    private val inFlight = HashMap<String, CompletableDeferred<PhotoPage>>()

    /**
     * One page from one provider, served from cache when it can be, and shared
     * with any caller already waiting on the same page.
     *
     * Throws [ToolHttpException] like the clients it wraps; callers turn that
     * into a [PhotoFailure] with [photoFailureOf].
     */
    suspend fun page(
        source: PhotoSource,
        query: PhotoQuery,
        apiKey: String,
        nowMs: Long = System.currentTimeMillis(),
    ): PhotoPage {
        PhotoCache.get(source, query, nowMs)?.let { return it }

        val key = PhotoCache.keyOf(source, query)
        // A second caller for a page already being fetched waits for the first
        // rather than spending another request. Typing, backspacing and
        // retyping one character is otherwise two requests out of fifty.
        //
        // The bookkeeping is guarded by a plain lock rather than a Mutex on
        // purpose: releasing it has to happen in a `finally`, and a `finally`
        // that suspends is skipped when the caller is cancelled — which would
        // strand the entry and leave every later caller awaiting a result that
        // can never arrive.
        var owned = false
        val deferred = synchronized(inFlight) {
            inFlight.getOrPut(key) {
                owned = true
                CompletableDeferred()
            }
        }
        if (!owned) return deferred.await()

        try {
            val fetched = withContext(Dispatchers.IO) { fetch(source, query, apiKey, nowMs) }
            PhotoCache.put(source, query, fetched, nowMs)
            deferred.complete(fetched)
            return fetched
        } catch (e: Throwable) {
            // Cancellation included: waiters must be woken either way, or they
            // hang on a request that stopped existing.
            deferred.completeExceptionally(e)
            throw e
        } finally {
            synchronized(inFlight) { inFlight.remove(key) }
        }
    }

    /**
     * The same page from every provider in [sources], interleaved.
     *
     * Partial success is kept: one provider being out of requests, or simply
     * having nothing for this query, must not empty a grid the other one could
     * fill. The whole call fails only when every provider did.
     */
    suspend fun mixedPage(
        sources: List<PhotoSource>,
        query: PhotoQuery,
        keys: Keys,
        nowMs: Long = System.currentTimeMillis(),
    ): MixedPage = coroutineScope {
        val usable = sources.filterNot { PhotoRateLimit.isSpent(it, nowMs) }
        if (usable.isEmpty()) {
            val spent = sources.map { PhotoFailure.QuotaSpent(it, PhotoRateLimit.resetAt(it, nowMs)) }
            return@coroutineScope MixedPage(emptyList(), hasMore = false, failures = spent)
        }
        val results = usable.map { source ->
            source to async {
                try {
                    Result.success(page(source, query, keys.keyFor(source), nowMs))
                } catch (cancelled: CancellationException) {
                    // Never folded into a Result: the caller navigating away
                    // has to actually cancel, not come back as a failed
                    // provider the picker would then draw a retry button for.
                    throw cancelled
                } catch (failed: IOException) {
                    Result.failure(failed)
                } catch (malformed: SerializationException) {
                    Result.failure(malformed)
                } catch (malformed: IllegalArgumentException) {
                    // `jsonObject` and `jsonArray` throw this when a provider
                    // answers 200 with something that is not the shape it
                    // documents. One provider's bad day must not empty a grid
                    // the other one could fill.
                    Result.failure(malformed)
                }
            }
        }.map { (source, job) -> source to job.await() }

        val pages = results.mapNotNull { (_, result) -> result.getOrNull() }
        val failures = results.mapNotNull { (source, result) ->
            result.exceptionOrNull()?.let { photoFailureOf(source, it, nowMs) }
        }
        MixedPage(
            items = interleavePhotos(pages.map { it.items }),
            hasMore = pages.any { it.hasMore },
            failures = failures,
        )
    }

    /**
     * Photos for an unattended top-up, taken in as few requests as possible:
     * Unsplash hands back up to 30 for one request, which is what makes a
     * rotating background affordable on a free key.
     *
     * Returns an empty list rather than throwing. Nothing is on screen waiting
     * for this, and a background top-up that fails should simply be tried again
     * later.
     */
    suspend fun prefetch(
        sources: List<PhotoSource>,
        query: PhotoQuery,
        keys: Keys,
        count: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): List<PhotoItem> = withContext(Dispatchers.IO) {
        val source = sources.firstOrNull { PhotoRateLimit.canPrefetch(it, nowMs) }
            ?: return@withContext emptyList()
        runCatching {
            when (source) {
                PhotoSource.UNSPLASH -> {
                    val response = timed(source, nowMs) {
                        UnsplashClient.random(query, keys.keyFor(source), count)
                    }
                    UnsplashClient.parseList(response.body, page = 1, perPage = count).items
                }
                PhotoSource.PEXELS -> {
                    // Pexels has no random endpoint, so a page of the curated
                    // feed stands in. Paging by the query's own page keeps
                    // successive top-ups off the same twenty photos.
                    val response = timed(source, nowMs) {
                        PexelsClient.curated(query.copy(perPage = count), keys.keyFor(source))
                    }
                    PexelsClient.parsePage(response.body, query.page, count).items
                }
            }
        }.getOrElse { emptyList() }
    }

    /** Blocking; the single dispatch point over the providers. */
    private fun fetch(source: PhotoSource, query: PhotoQuery, apiKey: String, nowMs: Long): PhotoPage {
        // A colour has to go through search: the feed endpoints take no
        // `color`, so a colour picked on its own used to be dropped and the
        // swatch looked broken. Search needs *something* to search for, so a
        // blank query borrows the topic, and failing that the colour's name.
        val searching = query.text.isNotBlank() || query.color != PhotoColor.ANY
        val effective = if (!searching || query.text.isNotBlank()) {
            query
        } else {
            query.copy(
                text = query.topicId.replace('-', ' ').trim()
                    .ifBlank { query.color.searchWord() },
            )
        }
        val response = timed(source, nowMs) {
            when (source) {
                PhotoSource.UNSPLASH ->
                    if (searching) {
                        UnsplashClient.search(effective, apiKey)
                    } else {
                        UnsplashClient.feed(effective, apiKey)
                    }
                PhotoSource.PEXELS ->
                    if (searching) {
                        PexelsClient.search(effective, apiKey)
                    } else {
                        PexelsClient.curated(effective, apiKey)
                    }
            }
        }
        return when (source) {
            PhotoSource.UNSPLASH ->
                if (searching) {
                    UnsplashClient.parseSearch(response.body, effective.page)
                } else {
                    UnsplashClient.parseList(response.body, effective.page, effective.perPage)
                }
            PhotoSource.PEXELS -> PexelsClient.parsePage(response.body, effective.page, effective.perPage)
        }
    }

    /**
     * Counts the request and folds the answer's budget headers in, on the way
     * out as well as the way through — a refusal is exactly the response whose
     * budget is worth knowing, and Pexels sends none at all with an error, so
     * that case falls back to assuming the worst.
     */
    private inline fun timed(source: PhotoSource, nowMs: Long, request: () -> HttpResponse): HttpResponse {
        PhotoRateLimit.recordRequest(source, nowMs)
        try {
            return request().also {
                // A response that arrived is proof the budget is not spent,
                // even when the service sends no headers to say so.
                PhotoRateLimit.recordSuccess(source)
                PhotoRateLimit.recordHeaders(source, it.headers, nowMs)
            }
        } catch (e: ToolHttpException) {
            when {
                e.headers.isNotEmpty() -> PhotoRateLimit.recordHeaders(source, e.headers, nowMs)
                // Only a refusal that names throttling is read as an empty
                // budget. Treating a 404 or a 500 that way would shut the
                // picker down for an hour over a transient server fault.
                e.status == HTTP_TOO_MANY_REQUESTS -> PhotoRateLimit.recordDenial(source, nowMs)
            }
            throw e
        }
    }

    private const val HTTP_TOO_MANY_REQUESTS = 429
}

/**
 * One page of a mixed-provider grid. [failures] is what went wrong for the
 * providers that did fail, which the picker shows as a note beside results the
 * others returned rather than in place of them.
 */
data class MixedPage(
    val items: List<PhotoItem>,
    val hasMore: Boolean,
    val failures: List<PhotoFailure> = emptyList(),
) {
    /** Nothing came back and something went wrong: an error, not an empty result. */
    val failedOutright: Boolean get() = items.isEmpty() && failures.isNotEmpty()
}
