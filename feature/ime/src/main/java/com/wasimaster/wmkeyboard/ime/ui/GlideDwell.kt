package com.wasimaster.wmkeyboard.ime.ui

import kotlin.math.hypot

/**
 * The ambiguity picker's clock: where the finger last actually moved, when,
 * and how long it has to rest there before the stroke asks.
 *
 * Pulled out of the pointer loop for two reasons. The arithmetic has to be
 * right in a way a device cannot show — a finger that is genuinely still sends
 * no touch events at all, so the old "check the clock on every move" trigger
 * simply never ran for the people holding stillest (#96), and the timer that
 * replaces it has to guarantee progress or the loop spins. And the loop is
 * already at detekt's condition limit, so every operand this takes off it is
 * one it can spend on something else.
 *
 * Stillness is measured against where the finger *stopped*, not the last
 * sample, so a slow drift never accumulates into a hold — and a finger resting
 * on glass is never perfectly still, so [sample] tolerates a radius.
 *
 * Two deadlines. A close call asks after one [dwell][deadline]; a stroke the
 * decoder is sure of asks after two, when the user has that switched on, so
 * every stroke has a way to be second-guessed without lifting.
 */
internal class GlideDwell {

    var stillX = 0f
        private set

    var stillY = 0f
        private set

    /** Uptime at which the finger stopped where it is. */
    var stillSince = 0L
        private set

    /** The finger is (re)starting: the glide took over, or a move went past the still radius. */
    fun reset(x: Float, y: Float, t: Long) {
        stillX = x
        stillY = y
        stillSince = t
    }

    /** A sample; restarts the clock only if it travelled past [radiusPx] from where the finger stopped. */
    fun sample(x: Float, y: Float, t: Long, radiusPx: Float) {
        if (hypot(x - stillX, y - stillY) > radiusPx) reset(x, y, t)
    }

    /** Uptime at which to ask, or null when nothing would open. */
    fun deadline(closeCall: Boolean, holdToAsk: Boolean, dwellMs: Long): Long? = when {
        closeCall -> stillSince + dwellMs
        holdToAsk -> stillSince + HOLD_TO_ASK_DWELLS * dwellMs
        else -> null
    }

    /**
     * How long to wait for the next pointer event before asking, or null to
     * wait for the event alone. May be zero or negative when the deadline has
     * already passed; the caller's timeout treats that as "fire now".
     */
    fun timeout(now: Long, closeCall: Boolean, holdToAsk: Boolean, dwellMs: Long): Long? =
        deadline(closeCall, holdToAsk, dwellMs)?.let { it - now }

    /** What a timer that fired should do. */
    enum class Action {
        /** Ask: open the picker where the finger stopped. */
        OPEN,

        /** Nothing to ask yet; restart the clock so the next try is a full dwell away. */
        REARM,

        /** The deadline moved later since the timer was armed; keep waiting. */
        WAIT,
    }

    /**
     * Decides what a timer firing at [now] should do, given what the service
     * has published *by now*: [choices] is how many words the latest preview
     * offered. Fewer than two is nothing to choose between, so the clock is
     * restarted rather than the picker opened on a list of one.
     *
     * Progress guarantee: after [Action.OPEN] the caller stops arming; after
     * [Action.REARM] the caller resets the clock so the next timeout is at
     * least a dwell; after [Action.WAIT] the next timeout is the remaining
     * time, which is positive. So no answer can make the next timeout fire
     * immediately again.
     */
    fun onTimeout(
        now: Long,
        closeCall: Boolean,
        holdToAsk: Boolean,
        dwellMs: Long,
        choices: Int,
    ): Action {
        val due = deadline(closeCall, holdToAsk, dwellMs) ?: return Action.REARM
        return when {
            now < due -> Action.WAIT
            choices >= MIN_CHOICES -> Action.OPEN
            else -> Action.REARM
        }
    }

    companion object {
        /** A confident stroke asks after this many dwells, with hold-to-ask on. */
        const val HOLD_TO_ASK_DWELLS = 2

        /** Fewer words than this is nothing to choose between. */
        const val MIN_CHOICES = 2
    }
}
