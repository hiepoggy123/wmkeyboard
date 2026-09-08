package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.settings.OctopusFlickSensitivity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Whether a stroke is the quick upward flick that takes the word floating over
 * the key it started on (discussion #102), rather than the beginning of a
 * glide.
 *
 * Pulled out of the service for the reason [possessiveFlick]'s file gives: a
 * shape test should be checkable without an InputConnection or a live keyboard.
 * Both callers — the glide path, which asks at the lift, and the loop that runs
 * when glide typing is off — ask this one function, so there is one answer to
 * argue with rather than two that drift.
 *
 * Judged at the lift rather than the down. A flick is over in a tenth of a
 * second, so there is no latency to win by claiming the pointer early, and
 * claiming it early would mean fighting the glide loop for every upward stroke
 * instead of letting it decode the ones that turn out to be glides.
 *
 * @param points the stroke, oldest first, stamped with their arrival times
 * @param startX the key centre's x, in the same space as [points]
 * @param startY the key centre's y
 * @param startReachPx how near the key's centre the stroke must begin
 * @param minTravelPx the shortest stroke that counts — the caller clamps this
 *        against the glide loop's own start slop, which grows for a moment
 *        after each keystroke; below that slop the two would disagree about
 *        what has even begun
 * @param keyWidthPx the unit everything else here is measured in
 * @param sensitivity the user's tier, which sets the cone and the least speed
 */
internal fun octopusFlick(
    points: List<GesturePoint>,
    startX: Float,
    startY: Float,
    keyWidthPx: Float,
    startReachPx: Float,
    minTravelPx: Float,
    sensitivity: OctopusFlickSensitivity,
): Boolean {
    if (keyWidthPx <= 0f || points.size < 2) return false
    val first = points.first()
    val last = points.last()
    // It has to have started on the key whose word it claims. Anything else is
    // a stroke that happened to pass overhead.
    if (hypot(first.x - startX, first.y - startY) > startReachPx) return false

    val dx = last.x - first.x
    val dy = last.y - first.y
    // Upward, and inside the cone. The glide picker's near slots sit about 45
    // degrees off vertical and its far slot is dead centre of the balanced
    // cone, so both of the keyboard's up-flicks agree on what straight up is.
    if (dy >= 0f) return false
    val direct = hypot(dx, dy)
    if (direct < minTravelPx) return false
    if (direct > keyWidthPx * OCTOPUS_MAX_TRAVEL_WIDTHS) return false
    if (angleOffVertical(dx, dy) > sensitivity.coneDegrees) return false

    // One motion, not a path that happened to end higher than it started.
    var travelled = 0f
    for (i in 1 until points.size) {
        travelled += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
    }
    if (travelled > direct * OCTOPUS_MAX_DETOUR) return false

    val elapsed = last.t - first.t
    if (elapsed > OCTOPUS_MAX_DURATION_MS) return false
    // Zero-length timing means the caller did not stamp its points; the shape
    // has already been agreed, so the speed tests sit out rather than reject.
    if (elapsed > 0L) {
        val speed = direct / keyWidthPx / elapsed
        if (speed < sensitivity.minSpeedWidthsPerMs) return false
        // A glide that opened upward and then slowed to choose is the failure
        // this guards: the end of a flick is its fastest part, not its
        // slowest.
        if (endSpeedRatio(points, direct, elapsed) < OCTOPUS_MIN_END_SPEED_RATIO) return false
    }

    // And it must still be going up at the end, which is what separates a flick
    // from a stroke that went up and hooked away.
    return terminalIsUpward(points)
}

/** Degrees between the stroke and straight up. */
private fun angleOffVertical(dx: Float, dy: Float): Float =
    Math.toDegrees(atan2(abs(dx).toDouble(), (-dy).toDouble())).toFloat()

/** Speed over the last few milliseconds against the speed over the whole. */
private fun endSpeedRatio(points: List<GesturePoint>, direct: Float, elapsed: Long): Float {
    val last = points.last()
    val cutoff = last.t - OCTOPUS_END_WINDOW_MS
    var index = points.size - 1
    while (index > 0 && points[index - 1].t >= cutoff) index--
    if (index >= points.size - 1) return Float.MAX_VALUE
    val from = points[index]
    val window = last.t - from.t
    if (window <= 0L) return Float.MAX_VALUE
    val tail = hypot(last.x - from.x, last.y - from.y) / window
    val mean = direct / elapsed
    if (mean <= 0f) return Float.MAX_VALUE
    return tail / mean
}

/** Whether the last stretch of the stroke still points up, in a wider cone. */
private fun terminalIsUpward(points: List<GesturePoint>): Boolean {
    val from = points[maxOf(0, points.size - OCTOPUS_TERMINAL_POINTS)]
    val last = points.last()
    val dy = last.y - from.y
    if (dy >= 0f) return false
    return angleOffVertical(last.x - from.x, dy) <= OCTOPUS_TERMINAL_CONE_DEG
}

/** Past two key heights the user drew a path, not a flick. */
private const val OCTOPUS_MAX_TRAVEL_WIDTHS = 3f

/**
 * How much longer than the straight line the stroke may be. Tighter than the
 * possessive flick's 1.6, because that one reaches across the board to another
 * key and this one is a single motion off the key under the finger.
 */
private const val OCTOPUS_MAX_DETOUR = 1.25f

/** "Fast", in the only terms a user would state it. */
private const val OCTOPUS_MAX_DURATION_MS = 200L

/** The window the end-of-stroke speed is measured over. */
private const val OCTOPUS_END_WINDOW_MS = 40L

/** Slower than half the stroke's own average at the end is a stroke parking. */
private const val OCTOPUS_MIN_END_SPEED_RATIO = 0.5f

/** How many trailing samples say which way the stroke was still going. */
private const val OCTOPUS_TERMINAL_POINTS = 3

/** Widened, because the tail of a flick wobbles and the whole of it does not. */
private const val OCTOPUS_TERMINAL_CONE_DEG = 45f

/** How near the key's centre a flick must begin to claim that key's word. */
internal const val OCTOPUS_START_REACH_WIDTHS = 0.75f

/** The shortest stroke that counts, before the glide slop clamp. */
internal const val OCTOPUS_MIN_TRAVEL_WIDTHS = 1f

/**
 * How much clear of the glide loop's own start slop the minimum travel must
 * sit. Below the slop the glide loop has not decided a stroke has begun, and
 * the two would be answering about different gestures.
 */
internal const val OCTOPUS_SLOP_CLEARANCE = 1.05f

/**
 * Which gesture took a floating word. Statistics only: the two are the same
 * commit, and they are counted apart so their settings can be judged apart.
 */
enum class OctopusSource { FLICK, TAP, A11Y }
