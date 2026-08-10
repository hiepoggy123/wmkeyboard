package com.wasimaster.wmkeyboard.core.settings

import android.content.Context
import android.provider.Settings

/**
 * Android's own "Remove animations" switch, and how the keyboard obeys it.
 *
 * Nothing here is stored. A user who turns animations off for the whole device
 * has already answered the question the Accessibility screen asks, and until
 * this existed the keyboard was the one surface that ignored them: every panel
 * transition, key popup and toolbar reveal still animated.
 *
 * Deliberately not a setting of its own. It can only ever switch motion *off*,
 * exactly like power saving, so there is no state to keep and nothing for the
 * user to reconcile: their own switch and the system's both mean "less motion",
 * and either one being on is enough.
 */
object SystemMotion {

    /**
     * Whether the device is set to draw no animations.
     *
     * `ANIMATOR_DURATION_SCALE` is the one of the three developer-options
     * scales that governs property animations, which is what Compose and every
     * transition in this app are built on. Absent means never set, which is the
     * default of 1.0 rather than off, so a missing value must not read as
     * "animations are off".
     */
    fun animationsOff(context: Context): Boolean =
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
}

/**
 * Folds [animationsOff] into [KeyboardSettings.reduceMotion].
 *
 * A view applied on the way out, the same shape as
 * [KeyboardSettings.underPowerSaving]: never persisted, so turning the system
 * switch back on restores the user's own choice without anything having been
 * rewritten. One flag rather than two also means no renderer has to learn that
 * the system has an opinion — they all read `reduceMotion` already.
 */
fun KeyboardSettings.withSystemMotion(animationsOff: Boolean): KeyboardSettings =
    if (!animationsOff || reduceMotion) this else copy(reduceMotion = true)
