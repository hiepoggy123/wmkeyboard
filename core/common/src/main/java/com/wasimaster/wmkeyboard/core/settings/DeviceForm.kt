package com.wasimaster.wmkeyboard.core.settings

// Lives in :core:common rather than beside ScreenVariant in :core:settings
// because the tablet key expansion is in :core:layout, and :core:settings
// already depends on :core:language — putting this there would invert that edge
// into a cycle. Same package, different Gradle module; see the note at the top
// of ToolbarTool.kt, which is here for the same reason.

/**
 * How big the screen the keyboard is drawing on is, in the buckets a keyboard
 * actually has to make decisions about.
 *
 * Thresholds are AOSP LatinIME's, which is what Gboard inherited: a small tablet
 * starts at sw600dp and a large one at sw768dp. Matching them is the point —
 * users arrive with Gboard's key set in their fingers, and a keyboard that drew
 * its wide layout at a different screen size would be wrong on exactly the
 * devices where the difference is noticeable.
 *
 * Read from `Configuration.smallestScreenWidthDp` — the *smallest* dimension, so
 * a phone turned sideways is still a phone, while a tablet is a tablet either way
 * up. In multi-window the platform reports the app's own window, so a tablet
 * running the keyboard beside another app correctly drops to [PHONE].
 *
 * Plain Kotlin rather than `values-sw600dp` resource qualifiers (which is how
 * AOSP does it): this repo has no qualified resource directories at all, the one
 * existing screen-size constant is a plain Kotlin `const`, and a resource would
 * be unreadable from here without a Context and untestable without Robolectric.
 *
 * Distinct from [ScreenVariant]'s "unfolded", which asks about *posture* and
 * happens to use the same 600dp line. Two questions, two constants — a change to
 * one must not silently move the other.
 */
enum class DeviceForm {
    /** Phones, and foldables while folded. The shipped layouts as authored. */
    PHONE,

    /** ~7-inch tablets and unfolded foldables. Held, so still thumb-typed. */
    SMALL_TABLET,

    /** ~10-inch and up. Put down and typed with both hands. */
    LARGE_TABLET,
    ;

    val isTablet: Boolean get() = this != PHONE

    companion object {
        /** `Configuration.smallestScreenWidthDp` at or above which this is a tablet. */
        const val SMALL_TABLET_MIN_DP = 600

        /** …and above which it is a large one, which earns the full key set. */
        const val LARGE_TABLET_MIN_DP = 768

        fun of(smallestScreenWidthDp: Int): DeviceForm = when {
            smallestScreenWidthDp >= LARGE_TABLET_MIN_DP -> LARGE_TABLET
            smallestScreenWidthDp >= SMALL_TABLET_MIN_DP -> SMALL_TABLET
            else -> PHONE
        }
    }
}
