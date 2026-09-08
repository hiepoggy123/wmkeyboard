package com.wasimaster.wmkeyboard.app.updates

/**
 * When an automatic check is allowed to reach the network.
 *
 * The settings activity checks on every resume, and a resume happens every
 * time the user comes back from a system screen, another app, or the update
 * flow itself. Without a gate that would be several requests a minute, against
 * an endpoint that allows sixty an hour for every device behind one address.
 *
 * A check the user asked for never comes through here. Pressing "Check for
 * updates" and being told to wait six hours would be a row that does nothing.
 */
internal object UpdateCheckGate {

    /**
     * How long an automatic check waits after the last one.
     *
     * Six hours rather than a day because the check is nearly free once the
     * ETag is in hand, and rather than an hour because nothing about this app
     * is urgent: releases are weeks apart and the card can wait for the next
     * visit.
     */
    const val AUTO_CHECK_INTERVAL_MILLIS = 6L * 60 * 60 * 1000

    /**
     * How long after pressing Install the trip to the unknown-sources switch
     * still counts as the same decision. Long enough to find a toggle two
     * screens deep, short enough that it is not a licence to install later.
     */
    const val PENDING_INSTALL_MILLIS = 2L * 60 * 1000

    /**
     * Whether a resume may check.
     *
     * Refuses while something is already in flight, because a check would move
     * the state out from under a download the user is watching. Treats a
     * timestamp in the future as stale rather than as a window that has not
     * opened: a restored backup or a moved clock should not be able to stop
     * this app checking for six hours in the wrong direction, or forever.
     */
    fun shouldAutoCheck(
        now: Long,
        lastCheckAt: Long,
        rateLimitedUntil: Long,
        state: UpdateState,
    ): Boolean {
        if (!state.isQuiet()) return false
        if (rateLimitedUntil > now && rateLimitedUntil - now <= MAX_RATE_LIMIT_WAIT) return false
        if (now < lastCheckAt) return true
        return now - lastCheckAt >= AUTO_CHECK_INTERVAL_MILLIS
    }

    /**
     * The ETag to send, which is none unless the body it described is still
     * cached. A 304 carries no body, so answering one with nothing to show
     * would leave the card empty and the check apparently broken.
     */
    fun etagToSend(etag: String?, cacheExists: Boolean): String? =
        etag?.takeIf { cacheExists && it.isNotBlank() }

    /** Whether an Install press is recent enough to pick back up. */
    fun installStillPending(elapsedNow: Long, pressedAt: Long): Boolean {
        if (pressedAt <= 0L) return false
        if (elapsedNow < pressedAt) return false
        return elapsedNow - pressedAt < PENDING_INSTALL_MILLIS
    }

    /**
     * Whether this state is one an automatic check may replace. A download,
     * a finished download and an install in progress are all things the user
     * started, and none of them wants a check landing on top of it.
     */
    private fun UpdateState.isQuiet(): Boolean = when (this) {
        UpdateState.Idle,
        UpdateState.Checking,
        is UpdateState.UpToDate,
        is UpdateState.Available,
        is UpdateState.Failed,
        -> true
        UpdateState.Unsupported,
        UpdateState.Downloaded,
        UpdateState.Installing,
        is UpdateState.Downloading,
        -> false
    }

    /**
     * The longest a stored rate limit may hold a check back. GitHub's window
     * is an hour; anything claiming to be longer came from a clock that
     * disagrees with this one, and is ignored.
     */
    private const val MAX_RATE_LIMIT_WAIT = 2L * 60 * 60 * 1000
}
