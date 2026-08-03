package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.theme.PhotoAttribution
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.theme.reseeded
import com.wasimaster.wmkeyboard.core.theme.alphaFraction
import com.wasimaster.wmkeyboard.core.theme.withAlphaFraction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Which photo one theme is showing right now, and when it started.
 *
 * Kept apart from the theme itself on purpose. A rotating background must not
 * rewrite a [ThemeSpec] the user made: doing so would churn the stored theme
 * list on a timer, and one bad write would take their colours with it. Instead
 * this is laid over the theme on the way to the screen, and the worst a lost or
 * corrupt entry can cost is the photo now showing.
 */
@Serializable
data class RotationState(
    val imagePath: String? = null,
    val credit: PhotoAttribution? = null,
    /** A colour drawn from the photo, computed when it was downloaded. 0 = none. */
    val seedColor: Long = 0L,
    /** The scrim the readability check asked for, computed then too. Below 0 = none. */
    val scrimAlpha: Float = -1f,
    /** Wall clock, which survives a reboot but jumps when the clock is set. */
    val rotatedAtEpochMs: Long = 0L,
    /** Monotonic clock, which cannot jump but restarts at every boot. */
    val rotatedAtElapsedMs: Long = 0L,
)

/** Stores the per-theme rotation map as one JSON string. */
object RotationStateCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(states: Map<String, RotationState>): String = json.encodeToString(states)

    /** A blob that will not parse reads as "no theme is rotating yet". */
    fun decode(raw: String): Map<String, RotationState> {
        // Blank until a rotating background has actually moved on once, which
        // for most installs is never. Parsing it threw on every read.
        if (raw.isBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, RotationState>>(raw) }
            .getOrDefault(emptyMap())
    }
}

/** A jump this large means the clock was set, not that time passed. */
private const val MAX_PLAUSIBLE_GAP_MS = 30L * 24 * 3_600_000

/**
 * Whether [state] is due to move on to another photo.
 *
 * Every reading of "now" is a parameter rather than a call, so a reboot, a
 * time-zone change and a user setting the date back a year are all one-line
 * cases in a test rather than things nobody can check.
 *
 * Both clocks are stored because neither is enough alone. `elapsedRealtime` is
 * monotonic — no time-zone change or clock correction can move it — but it
 * restarts at every boot. The wall clock survives a reboot but does move. So
 * the monotonic reading is preferred, and a reading *below* the stored one is
 * taken as the reboot it can only be, falling back to the wall clock.
 *
 * What this deliberately does not try to do is tell a reboot apart from a clock
 * change when the monotonic clock has not gone backwards: the two look
 * identical from here, and guessing wrong in the direction of the wall clock
 * would let a corrected clock decide the schedule. The cost of the choice made
 * instead is that a photo set shortly before a reboot may wait one extra
 * interval of uptime, which then corrects itself.
 */
fun isRotationDue(
    interval: RotationInterval,
    state: RotationState,
    nowEpochMs: Long,
    nowElapsedMs: Long,
    sessionStarted: Boolean,
): Boolean {
    if (interval == RotationInterval.MANUAL) return false
    if (interval == RotationInterval.EVERY_OPEN) return sessionStarted
    // Never rotated: the first photo is always due.
    if (state.rotatedAtEpochMs == 0L) return true

    val rebooted = nowElapsedMs < state.rotatedAtElapsedMs
    val elapsed = if (rebooted) {
        nowEpochMs - state.rotatedAtEpochMs
    } else {
        nowElapsedMs - state.rotatedAtElapsedMs
    }
    // A clock that moved backwards, or forwards by a month, says nothing about
    // how long the photo has been up. Rotating is the safe answer either way:
    // the alternative is a background frozen until a date in the future.
    if (elapsed < 0L || elapsed > MAX_PLAUSIBLE_GAP_MS) return true
    return elapsed >= interval.periodMs
}

/** Whether [themeId] is one of the themes the rotation takes over. */
fun PhotoBackgroundSettings.rotates(themeId: String, activeThemeId: String): Boolean {
    if (!rotateEnabled) return false
    return when (scope) {
        RotationScope.ALL_THEMES -> true
        RotationScope.CURRENT_THEME -> themeId == activeThemeId
        RotationScope.SELECTED_THEMES -> themeId in scopeThemeIds
    }
}

/**
 * The theme as it should be drawn with its rotating photo in place.
 *
 * Applied where a theme is resolved for the screen rather than where it is
 * stored, which is what lets a built-in theme rotate as well: a built-in has
 * nowhere to keep an image, but nothing here writes anything down.
 *
 * Returns the theme unchanged when there is no photo to show, so a pool that
 * has not filled yet, or a device that is still locked, simply draws the theme
 * the user configured.
 */
fun ThemeSpec.withRotation(state: RotationState?, settings: PhotoBackgroundSettings): ThemeSpec {
    val path = state?.imagePath?.takeIf { it.isNotBlank() } ?: return this
    var next = copy(
        backgroundImage = path,
        backgroundPhoto = state.credit,
        // One photo serves both orientations: the board is a wide strip either
        // way round, and the draw code already falls back to the portrait image
        // when there is no landscape one.
        backgroundImageLandscape = null,
        backgroundPhotoLandscape = null,
    )
    if (settings.seedPalette && state.seedColor != 0L) {
        // Reseeding first, because it rebuilds the board colour from the seed
        // and would otherwise throw the scrim away.
        next = next.reseeded(state.seedColor, next.dark)
    }
    if (settings.readabilityGuard && state.scrimAlpha >= 0f) {
        next = next.copy(boardBackground = next.boardBackground.withAlphaFraction(state.scrimAlpha))
    }
    return next
}

/**
 * Every custom theme with its rotating photo in place. Themes that are not
 * rotating come back untouched, so this is a no-op for anyone not using the
 * feature.
 */
fun List<ThemeSpec>.withRotatingBackgrounds(
    states: Map<String, RotationState>,
    settings: PhotoBackgroundSettings,
    activeThemeId: String,
): List<ThemeSpec> {
    if (!settings.rotateEnabled || states.isEmpty()) return this
    return map { theme ->
        if (settings.rotates(theme.id, activeThemeId)) {
            theme.withRotation(states[theme.id], settings)
        } else {
            theme
        }
    }
}

/**
 * A key colour made see-through enough that a background photo reads through
 * it, or the colour unchanged if it is already see-through.
 *
 * Applied when a photo becomes the background. A keyboard is mostly keys, so
 * leaving them opaque means the photo is only visible around the edges — which
 * makes choosing one feel like nothing happened. The value is a starting point:
 * the key colours stay editable, and the readability check runs over the result.
 */
fun Long.softenedForPhoto(alpha: Float = PHOTO_KEY_ALPHA): Long =
    if (alphaFraction() >= 1f) withAlphaFraction(alpha) else this

/** How see-through a key becomes when a photo is put behind it. */
const val PHOTO_KEY_ALPHA = 0.62f
