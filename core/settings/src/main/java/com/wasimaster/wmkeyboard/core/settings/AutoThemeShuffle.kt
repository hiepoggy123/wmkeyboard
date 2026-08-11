package com.wasimaster.wmkeyboard.core.settings

import kotlin.random.Random

/**
 * The random half of the auto theme: which theme a slot is showing, and when it
 * should select another.
 *
 * Kept apart from the pair itself ([AutoThemeSettings]) the way a rotating
 * photo is kept apart from the theme it sits on: the selection is bookkeeping
 * that changes on a timer, and the worst a lost or stale entry can cost is the
 * theme showing now.
 */

/** Whether either half of the pair selects at random. */
val AutoThemeSettings.usesRandomSlot: Boolean get() = lightRandom || darkRandom

/** Whether the half named by [darkSlot] selects at random. */
fun AutoThemeSettings.slotRandom(darkSlot: Boolean): Boolean =
    if (darkSlot) darkRandom else lightRandom

/** The theme ids the half named by [darkSlot] selects from. */
fun AutoThemeSettings.slotPool(darkSlot: Boolean): Set<String> =
    if (darkSlot) darkPoolIds else lightPoolIds

/** The one theme the half named by [darkSlot] shows when it is not random. */
fun AutoThemeSettings.slotFixedId(darkSlot: Boolean): String =
    if (darkSlot) darkThemeId else lightThemeId

/** The theme the half named by [darkSlot] selected last, or blank before the first. */
fun AutoThemeSettings.slotShuffledId(darkSlot: Boolean): String =
    if (darkSlot) shuffleDarkId else shuffleLightId

/**
 * The theme id the half named by [darkSlot] is showing.
 *
 * Three fallbacks, each covering a state the user can reach:
 * a random half with an empty pool shows the one theme it had before, a stored
 * selection that has left the pool is replaced by the pool's lowest id, and a
 * pool is ordered before that read because a `Set` from DataStore has no order
 * of its own and the board must not depend on one.
 *
 * Deliberately does not check the id against the stored themes. This runs
 * wherever the keyboard resolves its theme, and walking every custom theme
 * there would cost more than the case is worth: a deleted id is taken out of
 * the pools in the same write that deletes it (`cleanupThemeIdRefs`), and one
 * that survives that resolves to the default theme, which is what an unknown id
 * has always done.
 */
fun AutoThemeSettings.slotThemeId(darkSlot: Boolean): String {
    val fixed = slotFixedId(darkSlot)
    if (!slotRandom(darkSlot)) return fixed
    val pool = slotPool(darkSlot)
    if (pool.isEmpty()) return fixed
    val current = slotShuffledId(darkSlot)
    return if (current in pool) current else pool.min()
}

/**
 * The next theme for a random half: one of [pool], never [current] again while
 * the pool holds more than one.
 *
 * Excluding the current theme is what makes the change visible. True random
 * over a pool of three repeats about a third of the time, and a change nobody
 * can see reads as the feature being broken.
 *
 * [random] is a parameter so the tests are not a coin toss.
 */
fun nextShuffledId(pool: Set<String>, current: String, random: Random): String {
    if (pool.isEmpty()) return ""
    // Ordered before the draw: a Set out of DataStore has no order of its own,
    // so drawing straight from it would make the result unreproducible.
    val ordered = pool.sorted()
    val others = ordered.filter { it != current }
    val from = others.ifEmpty { ordered }
    return from[random.nextInt(from.size)]
}

/**
 * Whether the random halves are due to select again.
 *
 * The same cadence model as the rotating photo background, down to the reboot
 * and clock-change handling: see [isIntervalDue].
 */
fun isThemeShuffleDue(
    auto: AutoThemeSettings,
    nowEpochMs: Long,
    nowElapsedMs: Long,
    sessionStarted: Boolean,
): Boolean {
    if (!auto.enabled || !auto.usesRandomSlot) return false
    return isIntervalDue(
        interval = auto.shuffleInterval,
        atEpochMs = auto.shuffledAtEpochMs,
        atElapsedMs = auto.shuffledAtElapsedMs,
        nowEpochMs = nowEpochMs,
        nowElapsedMs = nowElapsedMs,
        sessionStarted = sessionStarted,
    )
}
