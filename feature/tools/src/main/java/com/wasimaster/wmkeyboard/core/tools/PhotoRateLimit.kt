package com.wasimaster.wmkeyboard.core.tools

/**
 * What is left of each provider's request budget.
 *
 * This matters more here than for the other network tools because the photo
 * providers are stingy: Unsplash allows 50 requests an hour on a free key, and
 * one key shipped in a build is 50 an hour for everyone using it. Spending a
 * request to find out there are none left is the worst possible use of the last
 * one, so the picker stops short and says so instead.
 *
 * Every entry point takes `nowMs` rather than reading the clock, so the whole
 * object is testable without waiting for an hour to pass.
 */
object PhotoRateLimit {

    /**
     * Requests the unattended prefetcher will not touch. Somebody scrolling the
     * picker should never lose to a background top-up.
     */
    const val INTERACTIVE_RESERVE = 10

    /** Stop issuing at this many left, rather than discovering the wall. */
    const val LOW_WATER_MARK = 3

    /**
     * Pexels allows 200 requests an hour, and says so nowhere in its headers —
     * `X-Ratelimit-Limit` reports the 20000-a-month quota instead. Sailing past
     * the hourly cap while the header still reads 19,847 is the failure this
     * constant exists to stop, so the hour is counted here instead.
     */
    private const val PEXELS_HOURLY_CAP = 200

    private const val HOUR_MS = 3_600_000L

    data class Budget(
        val limit: Int,
        val remaining: Int,
        val resetAtMs: Long,
        val stampedAtMs: Long,
    )

    private val budgets = HashMap<PhotoSource, Budget>()

    /** Request times inside the last hour, for the cap Pexels does not report. */
    private val pexelsCalls = ArrayDeque<Long>()

    /** Call once per request that reached the provider, whatever it answered. */
    @Synchronized
    fun recordRequest(source: PhotoSource, nowMs: Long) {
        if (source != PhotoSource.PEXELS) return
        pexelsCalls.addLast(nowMs)
        pruneLocked(nowMs)
    }

    /** Folds a successful response's headers into the known budget. */
    @Synchronized
    fun recordHeaders(source: PhotoSource, headers: Map<String, String>, nowMs: Long) {
        parse(source, headers, nowMs)?.let { budgets[source] = it }
    }

    /**
     * Records a refusal we could not read a budget from. Pexels answers an
     * error with no headers at all, so the only safe reading is "assume spent
     * until the hour is out" — an optimistic guess here means hammering a
     * provider that is already saying no.
     */
    @Synchronized
    fun recordDenial(source: PhotoSource, nowMs: Long) {
        val known = budgets[source]
        budgets[source] = Budget(
            limit = known?.limit ?: 0,
            remaining = 0,
            resetAtMs = nowMs + HOUR_MS,
            stampedAtMs = nowMs,
        )
    }

    /** The budget as last seen, or null while it is still unknown. */
    @Synchronized
    fun budget(source: PhotoSource, nowMs: Long): Budget? =
        budgets[source]?.takeIf { nowMs < it.resetAtMs }

    /**
     * Requests believed left, or null when nothing is known yet. For Pexels the
     * locally counted hour wins whenever it is the tighter of the two.
     */
    @Synchronized
    fun remaining(source: PhotoSource, nowMs: Long): Int? {
        val reported = budget(source, nowMs)?.remaining
        if (source != PhotoSource.PEXELS) return reported
        pruneLocked(nowMs)
        val local = (PEXELS_HOURLY_CAP - pexelsCalls.size).coerceAtLeast(0)
        return if (reported == null) local else minOf(reported, local)
    }

    /** Whether an interactive request should be attempted at all. */
    fun isSpent(source: PhotoSource, nowMs: Long): Boolean =
        (remaining(source, nowMs) ?: Int.MAX_VALUE) <= 0

    /** Whether the picker should stop short rather than hit the wall. */
    fun isLow(source: PhotoSource, nowMs: Long): Boolean =
        (remaining(source, nowMs) ?: Int.MAX_VALUE) <= LOW_WATER_MARK

    /** Whether an unattended top-up may spend a request right now. */
    fun canPrefetch(source: PhotoSource, nowMs: Long): Boolean =
        (remaining(source, nowMs) ?: Int.MAX_VALUE) > INTERACTIVE_RESERVE

    /** When the budget is expected back, or 0 when that is not known. */
    fun resetAt(source: PhotoSource, nowMs: Long): Long = budget(source, nowMs)?.resetAtMs ?: 0L

    /**
     * Whether a failure means "you are out of requests" rather than "your key is
     * wrong". The distinction is not cosmetic: **Unsplash answers 403 for both**,
     * and [ToolHttp.statusMessageRes] maps 401 and 403 alike to "the API key was
     * rejected". A user who has merely used up the hour would be told their key
     * is bad and would go and replace a key that was fine.
     */
    fun isQuotaFailure(source: PhotoSource, failure: ToolHttpException): Boolean = when (source) {
        PhotoSource.UNSPLASH ->
            failure.status == HTTP_FORBIDDEN && headerInt(failure.headers, "X-Ratelimit-Remaining") == 0
        PhotoSource.PEXELS -> failure.status == HTTP_TOO_MANY_REQUESTS
    }

    /** Test seam; also the right thing to do when a user pastes a new key. */
    @Synchronized
    fun reset() {
        budgets.clear()
        pexelsCalls.clear()
    }

    /**
     * Pure, so the header shapes can be pinned in a test. A value that will not
     * parse yields null (unknown) and never 0 (spent) — a proxy that strips or
     * mangles headers must not be able to lock the feature out.
     */
    internal fun parse(source: PhotoSource, headers: Map<String, String>, nowMs: Long): Budget? {
        val remaining = headerInt(headers, "X-Ratelimit-Remaining") ?: return null
        val limit = headerInt(headers, "X-Ratelimit-Limit") ?: 0
        // Unsplash sends no reset header, so the window is assumed to be the
        // hour it documents. Pexels' reset is UNIX seconds and points at the
        // monthly rollover, which is too far out to gate the hourly cap on.
        val reset = headerLong(headers, "X-Ratelimit-Reset")
        val resetAtMs = when {
            source == PhotoSource.PEXELS && reset != null -> minOf(reset * 1000L, nowMs + HOUR_MS)
            else -> nowMs + HOUR_MS
        }
        return Budget(limit = limit, remaining = remaining, resetAtMs = resetAtMs, stampedAtMs = nowMs)
    }

    private fun pruneLocked(nowMs: Long) {
        while (pexelsCalls.isNotEmpty() && nowMs - pexelsCalls.first() >= HOUR_MS) {
            pexelsCalls.removeFirst()
        }
    }

    private fun headerInt(headers: Map<String, String>, name: String): Int? =
        headerValue(headers, name)?.trim()?.toIntOrNull()

    private fun headerLong(headers: Map<String, String>, name: String): Long? =
        headerValue(headers, name)?.trim()?.toLongOrNull()

    /** Header names are case-insensitive, and a proxy may well change the case. */
    private fun headerValue(headers: Map<String, String>, name: String): String? =
        headers[name] ?: headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_TOO_MANY_REQUESTS = 429
}
