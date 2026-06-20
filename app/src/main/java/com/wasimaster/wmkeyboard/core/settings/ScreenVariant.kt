package com.wasimaster.wmkeyboard.core.settings

/**
 * The screen shapes a keyboard has to fit, each of which can carry its own
 * sizing.
 *
 * A key height that feels right on a folded phone is cramped on a tablet
 * and far too tall in landscape, where vertical space is the scarce thing.
 * Rather than one compromise set of numbers, each shape gets its own.
 *
 * [PORTRAIT] is the base: its values are the plain fields on
 * [KeyboardSettings], which is what the keyboard used before this existed.
 * The other three are *overrides* — each value is optional, and anything
 * left unset falls back to portrait. That keeps existing installs behaving
 * exactly as they did and means a user who only cares about landscape
 * height sets one number, not a whole profile.
 *
 * "Unfolded" is decided by the smallest screen dimension rather than the
 * current width: a phone in landscape is wide but still a phone, while an
 * opened foldable or a tablet is wide in both orientations.
 */
enum class ScreenVariant(val suffix: String, val label: String) {
    PORTRAIT("", "Portrait"),
    LANDSCAPE("landscape", "Landscape"),
    PORTRAIT_UNFOLDED("unfolded", "Portrait, unfolded"),
    LANDSCAPE_UNFOLDED("landscape_unfolded", "Landscape, unfolded"),
    ;

    /** Everything except [PORTRAIT], which is stored as the base values. */
    val isOverride: Boolean get() = this != PORTRAIT

    companion object {
        /** Width in dp at or above which a device counts as unfolded. */
        const val UNFOLDED_MIN_DP = 600

        fun of(landscape: Boolean, unfolded: Boolean): ScreenVariant = when {
            landscape && unfolded -> LANDSCAPE_UNFOLDED
            landscape -> LANDSCAPE
            unfolded -> PORTRAIT_UNFOLDED
            else -> PORTRAIT
        }
    }
}

/**
 * Per-variant sizing. Every field is optional: null means "use the
 * portrait value", so a variant only stores what the user actually changed.
 */
data class SizingOverride(
    val keyHeightDp: Int? = null,
    val numberRowHeightDp: Int? = null,
    val bottomPaddingDp: Int? = null,
    val keyboardWidthPercent: Int? = null,
    val fontScale: Float? = null,
    val keyboardAlignment: KeyboardAlignment? = null,
) {
    val isEmpty: Boolean
        get() = keyHeightDp == null && numberRowHeightDp == null &&
            bottomPaddingDp == null && keyboardWidthPercent == null &&
            fontScale == null && keyboardAlignment == null
}

/**
 * The settings as they apply on [variant]: the base values with that
 * variant's overrides layered on top.
 *
 * Returning a whole [KeyboardSettings] rather than a separate sizing object
 * is deliberate — the keyboard resolves once where it knows the screen
 * shape, and every `settings.keyHeightDp` downstream keeps working without
 * knowing variants exist at all.
 */
fun KeyboardSettings.resolvedFor(variant: ScreenVariant): KeyboardSettings {
    val override = sizingOverrides[variant] ?: return this
    if (override.isEmpty) return this
    return copy(
        keyHeightDp = override.keyHeightDp ?: keyHeightDp,
        numberRowHeightDp = override.numberRowHeightDp ?: numberRowHeightDp,
        bottomPaddingDp = override.bottomPaddingDp ?: bottomPaddingDp,
        keyboardWidthPercent = override.keyboardWidthPercent ?: keyboardWidthPercent,
        fontScale = override.fontScale ?: fontScale,
        keyboardAlignment = override.keyboardAlignment ?: keyboardAlignment,
    )
}

/** The values [variant] currently resolves to, for showing in its editor. */
fun KeyboardSettings.sizingValuesFor(variant: ScreenVariant): SizingOverride {
    val resolved = resolvedFor(variant)
    return SizingOverride(
        keyHeightDp = resolved.keyHeightDp,
        numberRowHeightDp = resolved.numberRowHeightDp,
        bottomPaddingDp = resolved.bottomPaddingDp,
        keyboardWidthPercent = resolved.keyboardWidthPercent,
        fontScale = resolved.fontScale,
        keyboardAlignment = resolved.keyboardAlignment,
    )
}
