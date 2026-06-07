package com.wasimaster.wmkeyboard.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A complete keyboard theme. Colors are ARGB longs (0xAARRGGBB) so alpha is
 * first-class — a translucent board over a background image, ghosted keys,
 * and so on. Nullable fields fall back to a value derived from the required
 * ones (see KbTheme resolution), so a minimal theme only needs the core
 * colors; nullable radii fall back to the global appearance sliders.
 */
@Serializable
data class ThemeSpec(
    val id: String,
    val name: String,
    val dark: Boolean = true,
    // Board
    val boardBackground: Long = 0xFF17181C,
    /** Absolute path of a local background image; never set in exports. */
    val backgroundImage: String? = null,
    /** Alpha applied to the background image itself. */
    val backgroundImageOpacity: Float = 1f,
    /** Image bytes for export/import payloads only; stripped after import. */
    val backgroundImageBase64: String? = null,
    // Keys
    val keyBackground: Long = 0xFF303338,
    val keyText: Long = 0xFFE9E9EE,
    val modifierKeyBackground: Long = 0xFF222428,
    val modifierKeyText: Long? = null,
    val enterKeyBackground: Long = 0xFF4C8DF6,
    val enterKeyText: Long = 0xFF0B1220,
    val pressedKeyBackground: Long? = null,
    val keyBorderColor: Long? = null,
    val keyBorderWidthDp: Float = 0f,
    // Accent (shift-on tint, gesture trail, active tools, links/buttons in panels)
    val accent: Long = 0xFF8AB4F8,
    // Popups (key preview bubble + long-press alternates)
    val popupBackground: Long? = null,
    val popupText: Long? = null,
    // Toolbar
    val toolbarIcon: Long? = null,
    val toolCircleBackground: Long? = null,
    val toolCircleActiveBackground: Long? = null,
    // Panels (clipboard/snippet cards, emoji search bar)
    val chipBackground: Long? = null,
    val suggestionText: Long? = null,
    // Radii overrides; null = follow the global appearance sliders
    val keyCornerRadiusDp: Int? = null,
    val popupCornerRadiusDp: Int? = null,
    val toolCircleRadiusDp: Int? = null,
)

/** Follows system light/dark + Material You; not a stored [ThemeSpec]. */
const val DEFAULT_THEME_ID = "default"

private val themeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object ThemeCodec {
    fun encodeList(themes: List<ThemeSpec>): String = themeJson.encodeToString(themes)

    fun decodeList(json: String): List<ThemeSpec> =
        runCatching { themeJson.decodeFromString<List<ThemeSpec>>(json) }.getOrDefault(emptyList())

    fun encode(theme: ThemeSpec): String = themeJson.encodeToString(theme)

    fun decode(json: String): ThemeSpec? =
        runCatching { themeJson.decodeFromString<ThemeSpec>(json) }.getOrNull()
}

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** Readable text color (near-black or near-white) for a solid background. */
fun onColorFor(background: Long): Long =
    if (Color(background).luminance() > 0.5f) 0xFF15161A else 0xFFF4F4F8

/**
 * Builds a full theme from one seed color — the "pick a color, get a
 * sensible theme, then tweak" flow. All colors are opaque; the editor can
 * add alpha afterwards.
 */
fun themeFromSeed(id: String, name: String, seed: Long, dark: Boolean): ThemeSpec {
    val seedColor = Color(seed)
    return if (dark) {
        val board = lerp(Color(0xFF121318), seedColor, 0.08f)
        val key = lerp(Color(0xFF2B2D34), seedColor, 0.12f)
        ThemeSpec(
            id = id,
            name = name,
            dark = true,
            boardBackground = board.argb(),
            keyBackground = key.argb(),
            keyText = 0xFFE9E9EE,
            modifierKeyBackground = lerp(Color(0xFF1E2026), seedColor, 0.12f).argb(),
            enterKeyBackground = seed,
            enterKeyText = onColorFor(seed),
            pressedKeyBackground = lerp(key, seedColor, 0.45f).argb(),
            accent = lerp(seedColor, Color.White, 0.30f).argb(),
            popupBackground = lerp(Color(0xFF34363E), seedColor, 0.12f).argb(),
            toolCircleBackground = lerp(Color(0xFF2A2C33), seedColor, 0.14f).argb(),
            chipBackground = lerp(Color(0xFF24262C), seedColor, 0.10f).argb(),
        )
    } else {
        val board = lerp(Color(0xFFE8EAF0), seedColor, 0.06f)
        ThemeSpec(
            id = id,
            name = name,
            dark = false,
            boardBackground = board.argb(),
            keyBackground = 0xFFFFFFFF,
            keyText = 0xFF1B1C20,
            modifierKeyBackground = lerp(Color(0xFFD7DAE2), seedColor, 0.10f).argb(),
            enterKeyBackground = seed,
            enterKeyText = onColorFor(seed),
            pressedKeyBackground = lerp(Color.White, seedColor, 0.30f).argb(),
            accent = lerp(seedColor, Color.Black, 0.12f).argb(),
            popupBackground = 0xFFFFFFFF,
            toolCircleBackground = 0xFFFFFFFF,
            chipBackground = lerp(Color(0xFFDDE0E7), seedColor, 0.08f).argb(),
        )
    }
}

/**
 * Built-in gallery themes. Users can't edit these in place — the editor
 * duplicates one into a custom theme instead — so ids stay stable.
 */
val BuiltInThemes: List<ThemeSpec> = listOf(
    themeFromSeed("builtin_ocean", "Ocean", 0xFF3B82C4, dark = true),
    themeFromSeed("builtin_forest", "Forest", 0xFF3E8E5A, dark = true),
    themeFromSeed("builtin_sunset", "Sunset", 0xFFE07B39, dark = true),
    themeFromSeed("builtin_berry", "Berry", 0xFFB84A8E, dark = true),
    themeFromSeed("builtin_crimson", "Crimson", 0xFFCE4257, dark = true),
    themeFromSeed("builtin_slate", "Slate", 0xFF7A8699, dark = true),
    // Pitch black: AMOLED-friendly, near-black keys on true black.
    themeFromSeed("builtin_pitch", "Pitch black", 0xFF4C8DF6, dark = true).copy(
        boardBackground = 0xFF000000,
        keyBackground = 0xFF1A1C21,
        modifierKeyBackground = 0xFF101216,
        toolCircleBackground = 0xFF1E2025,
        chipBackground = 0xFF15171B,
        popupBackground = 0xFF24262C,
    ),
    themeFromSeed("builtin_snow", "Snow", 0xFF5B7DB1, dark = false),
    themeFromSeed("builtin_mint", "Mint", 0xFF4FA98F, dark = false),
    themeFromSeed("builtin_rose", "Rose", 0xFFC96A85, dark = false),
    themeFromSeed("builtin_sand", "Sand", 0xFFA98052, dark = false),
)

/** Seed swatches offered by the editor's "start from a color" row. */
val SeedSwatches: List<Long> = listOf(
    0xFF4C8DF6, 0xFF3B82C4, 0xFF00897B, 0xFF3E8E5A, 0xFF7CB342,
    0xFFF9A825, 0xFFE07B39, 0xFFCE4257, 0xFFB84A8E, 0xFF8E5AC8,
    0xFF5C6BC0, 0xFF7A8699, 0xFF6D4C41, 0xFF546E7A, 0xFF37393F,
)
