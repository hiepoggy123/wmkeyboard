package com.wasimaster.wmkeyboard.core.settings

import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.core.prediction.OctopusKind
import com.wasimaster.wmkeyboard.settings.R

/**
 * Where a floating word is drawn relative to the key it belongs to.
 *
 * The BlackBerry Z10 offered "In-Letter", "In-Column" and off; this is the
 * same choice made about geometry rather than about surfaces, because the
 * suggestion strip stays either way.
 */
enum class OctopusPlacement(@StringRes val labelRes: Int) {
    /**
     * Floating in the gap above the key, straddling its top edge — what both
     * the Z10 and the Octopus tweak did. The keyboard does not get any taller;
     * the words live in the space the rows already leave between them.
     */
    FLOAT(R.string.core_settings_octopus_placement_float_label),

    /**
     * Each row reserves a lane of its own, so nothing ever overlaps a key or a
     * neighbouring word. Honest and calm, at the cost of a taller keyboard.
     */
    STRIP(R.string.core_settings_octopus_placement_strip_label),

    /**
     * Inside the key, above its letter. Nothing can collide with anything, but
     * the word and the letter share a very small box.
     */
    IN_KEY(R.string.core_settings_octopus_placement_in_key_label),
}

/**
 * How readily a flick up off a key is taken as picking its word rather than as
 * the start of a glide stroke.
 *
 * Two numbers move together, because they are two ways of saying the same
 * thing: how far off vertical the stroke may point, and how fast it has to be
 * going. A glide that happens to open upward is the failure this guards
 * against, and a heavy glider wants a narrower cone than someone who has
 * gesture typing switched off entirely.
 *
 * [BALANCED]'s 30° is chosen against the glide picker rather than by feel: its
 * near slots sit about 45° off vertical and its far slot is dead centre of a
 * 30° cone, so both of the keyboard's up-flicks agree on what straight up
 * means. A user who conflates the two gestures is then right to.
 */
enum class OctopusFlickSensitivity(
    @StringRes val labelRes: Int,
    /** Half-width of the upward cone, in degrees. */
    val coneDegrees: Float,
    /** Minimum mean speed, in key widths per millisecond. */
    val minSpeedWidthsPerMs: Float,
) {
    /** For heavy gliders: only an unmistakably vertical, quick flick. */
    STRICT(R.string.core_settings_octopus_flick_strict_label, 22f, 0.016f),

    /** The default. */
    BALANCED(R.string.core_settings_octopus_flick_balanced_label, 30f, 0.012f),

    /** For boards with glide typing off, where almost nothing else competes. */
    RELAXED(R.string.core_settings_octopus_flick_relaxed_label, 40f, 0.008f),
}

/**
 * The octopus: a predicted word drawn over the key you would press next to
 * reach it, picked by flicking up on that key (discussion #102).
 *
 * Its own object rather than ten more fields on [KeyboardSettings], which is
 * near the JVM's limit on constructor parameters — and because these only ever
 * make sense together.
 *
 * Off by default. It is a second prediction surface on a keyboard that already
 * has one, and it wants to be found rather than met.
 */
data class OctopusSettings(
    val enabled: Boolean = false,
    val placement: OctopusPlacement = OctopusPlacement.FLOAT,
    /**
     * How many words may float at once: [SPARSE_DENSITY] is the real Z10, a
     * quiet board with three or four offers, and [MAX_DENSITY] is the Octopus
     * tweak's one-per-key. Above [DENSE_FROM] the trie is fanned to fill keys
     * the ranked candidates left empty, and the quietening score floor is
     * lifted, since filling the board is the point of asking for it.
     */
    val density: Int = SPARSE_DENSITY,
    /** Which kinds of prediction the user allows onto the keys. */
    val kinds: Set<OctopusKind> = OctopusKind.entries.toSet(),
    /** A flick up off a key commits its word. */
    val flickCommits: Boolean = true,
    /** Tapping the floating word itself commits it. */
    val tapCommits: Boolean = true,
    val flickSensitivity: OctopusFlickSensitivity = OctopusFlickSensitivity.BALANCED,
    /** Word size, as a multiple of the corner hint's 10sp base. */
    val fontScale: Float = 1.0f,
    /**
     * Hide a key's corner hint while it is carrying a word. Per key, not per
     * board: a key with nothing to say keeps its hint.
     *
     * Off by default: on a real board the word sits clear of the corner, so
     * hiding the hint gave up something for a collision that was not happening.
     */
    val suppressHints: Boolean = false,
    /**
     * Also float words above characters only a long press reaches. Off by
     * default, because a press of that key would not produce the word, and a
     * key that promises something a press does not deliver is worse than a key
     * that promises nothing.
     */
    val longPressKeys: Boolean = false,
) {
    /** Whether this board should fan the tries to fill the keys it can. */
    val dense: Boolean get() = density >= DENSE_FROM

    companion object {
        const val SPARSE_DENSITY = 6
        const val MIN_DENSITY = 3

        /** One per letter key on a Latin board. */
        const val MAX_DENSITY = 26

        /**
         * Where "a few good words" turns into "fill the board". Below this the
         * ranked candidates alone are enough and the fan-out is not worth its
         * trie walks.
         */
        const val DENSE_FROM = 9

        val FONT_SCALE_RANGE = 0.6f..1.6f
    }
}
