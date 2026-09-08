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
 * Judged on **direction, not speed**. The first version asked the stroke to be
 * quick, to still be quick in its last few milliseconds, and to still point
 * upward across its final samples. All three reject real flicks: a flick ends
 * by decelerating as the finger leaves the glass, and its last samples cluster
 * and jitter. What is left is the test that actually separates a flick from a
 * swipe — did the finger go up, and did it go more or less straight there.
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
    // Upward, and inside the cone.
    if (dy >= 0f) return false
    val direct = hypot(dx, dy)
    if (direct < minTravelPx) return false
    if (direct > keyWidthPx * OCTOPUS_MAX_TRAVEL_WIDTHS) return false
    if (angleOffVertical(dx, dy) > sensitivity.coneDegrees) return false

    // One motion, not a path that happened to end higher than it started. This
    // is what keeps a real word whose stroke opens upward — s to w to e — with
    // the decoder, and it is now the only thing that does.
    var travelled = 0f
    for (i in 1 until points.size) {
        travelled += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
    }
    return travelled <= direct * sensitivity.maxDetour
}

/** Degrees between the stroke and straight up. */
private fun angleOffVertical(dx: Float, dy: Float): Float =
    Math.toDegrees(atan2(abs(dx).toDouble(), (-dy).toDouble())).toFloat()

/**
 * How far a flick may travel. Generous: the cone and the straightness are what
 * decide, and a deliberate flick across two rows is still a flick.
 */
private const val OCTOPUS_MAX_TRAVEL_WIDTHS = 3.5f

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
