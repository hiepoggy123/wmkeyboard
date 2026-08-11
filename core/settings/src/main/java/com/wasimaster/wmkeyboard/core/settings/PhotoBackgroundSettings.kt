package com.wasimaster.wmkeyboard.core.settings

import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.settings.R

/** How often a rotating background changes. */
enum class RotationInterval {
    /** Every time the keyboard opens, which is as often as it can be. */
    EVERY_OPEN,
    HOURLY,
    SIX_HOURLY,
    DAILY,
    WEEKLY,

    /** Only when the user presses Shuffle. */
    MANUAL,
    ;

    /**
     * The window in milliseconds, or 0 for the two that are not a duration.
     * A day here means "24 hours since the last change" rather than local
     * midnight: [AutoThemeSettings] already owns the idea of a time of day, and
     * a theme with two clock models in it is a source of bugs rather than of
     * clarity.
     */
    val periodMs: Long
        get() = when (this) {
            EVERY_OPEN, MANUAL -> 0L
            HOURLY -> 3_600_000L
            SIX_HOURLY -> 6 * 3_600_000L
            DAILY -> 24 * 3_600_000L
            WEEKLY -> 7 * 24 * 3_600_000L
        }

    /** Caption for this choice; resolve it where it is drawn. */
    @get:StringRes
    val labelRes: Int
        get() = when (this) {
            EVERY_OPEN -> R.string.core_settings_photo_interval_every_open_label
            HOURLY -> R.string.core_settings_photo_interval_hourly_label
            SIX_HOURLY -> R.string.core_settings_photo_interval_six_hourly_label
            DAILY -> R.string.core_settings_photo_interval_daily_label
            WEEKLY -> R.string.core_settings_photo_interval_weekly_label
            MANUAL -> R.string.core_settings_photo_interval_manual_label
        }
}

/** Which themes a rotating background takes over. */
enum class RotationScope {
    /** Only whichever theme is showing. */
    CURRENT_THEME,
    ALL_THEMES,
    SELECTED_THEMES,
    ;

    @get:StringRes
    val labelRes: Int
        get() = when (this) {
            CURRENT_THEME -> R.string.core_settings_photo_scope_current_label
            ALL_THEMES -> R.string.core_settings_photo_scope_all_label
            SELECTED_THEMES -> R.string.core_settings_photo_scope_selected_label
        }
}

/**
 * Where the photos in the rotation pool come from.
 *
 * A photo the user added from the device and a photo they kept from a service
 * are both [SAVED]: the user chose each of them deliberately, both live in the
 * collection, and neither is ever thrown away to make room. Only [ONLINE] --
 * photos the app fetched on its own -- is a cache.
 */
enum class RotationSourceKind {
    /** Photos in the collection, whether kept from a service or from the device. */
    SAVED,

    /** Fresh photos the app fetched by topic and search word. */
    ONLINE,
}

/**
 * Online photo backgrounds: the provider keys and everything the rotating
 * background needs to know.
 *
 * A nested class rather than sixteen more fields on [KeyboardSettings], for the
 * reason spelled out there: the top-level count is two slots short of the JVM's
 * `copy` ceiling, and this feature would take it past. The DataStore keys stay
 * flat, so this grouping exists only in memory.
 */
data class PhotoBackgroundSettings(
    /** User-supplied keys, overriding any key baked into the build. */
    val unsplashApiKey: String = "",
    val pexelsApiKey: String = "",
    val rotateEnabled: Boolean = false,
    val interval: RotationInterval = RotationInterval.DAILY,
    val scope: RotationScope = RotationScope.CURRENT_THEME,
    /** Which themes rotate under [RotationScope.SELECTED_THEMES]. */
    val scopeThemeIds: Set<String> = emptySet(),
    /**
     * Both on by default. The collection costs nothing while it is empty, and
     * once it is not, a photo the user kept on purpose is the last thing a
     * rotation should skip in favour of one the app picked for them.
     */
    val sources: Set<RotationSourceKind> =
        setOf(RotationSourceKind.SAVED, RotationSourceKind.ONLINE),
    /** Provider topic slugs, never the translated labels shown on the chips. */
    val topics: List<String> = emptyList(),
    /** Search terms the pool is topped up from, alongside [topics]. */
    val queries: List<String> = emptyList(),
    /**
     * Which way round to ask for. Landscape by default: the board is a wide,
     * short strip whichever way the device is held.
     */
    val landscapeOnly: Boolean = true,
    val safeSearch: Boolean = true,
    /** Off by default; a top-up nobody asked for should not cost mobile data. */
    val fetchOnMetered: Boolean = false,
    /** How many photos to keep ready, so a change is never a wait. */
    val poolTarget: Int = DEFAULT_POOL_TARGET,
    /** Rebuild the theme's colours from each new photo. */
    val seedPalette: Boolean = false,
    /** Add a scrim on its own when a photo would make the keys unreadable. */
    val readabilityGuard: Boolean = true,
    /**
     * How opaque a key stays once a photo is behind it, 0-1.
     *
     * 62% was fixed, so anyone who disliked it retuned two or three colour
     * rows by hand after every photo. Only applied to keys that were fully
     * opaque to begin with; a theme that already sets its own transparency
     * keeps it.
     */
    val keyOpacity: Float = PHOTO_KEY_ALPHA,
    /**
     * How much disk the ready-to-use photo pool may take, in megabytes.
     *
     * The library screen showed the size and offered no way to change the
     * ceiling, so a pool that kept evicting had no remedy and one that was
     * too generous could not be reined in.
     */
    val poolBudgetMb: Int = DEFAULT_POOL_BUDGET_MB,
) {
    companion object {
        const val DEFAULT_POOL_TARGET = 8
        const val MIN_POOL_TARGET = 3
        const val DEFAULT_POOL_BUDGET_MB = 24
        val POOL_BUDGET_MB_RANGE = 8..128
        const val MAX_POOL_TARGET = 20

        /**
         * The floor on how often a rotation may go looking for photos, applied
         * whatever [interval] says. Worst case is one request an hour against a
         * budget of fifty.
         */
        const val MIN_FETCH_PERIOD_MS = 3_600_000L
    }

    /** Whether any source at all is turned on. An empty pool cannot fill itself. */
    val hasSource: Boolean get() = sources.isNotEmpty()

    /** Whether the pool may be topped up from the network. */
    val usesNetwork: Boolean get() = RotationSourceKind.ONLINE in sources
}
