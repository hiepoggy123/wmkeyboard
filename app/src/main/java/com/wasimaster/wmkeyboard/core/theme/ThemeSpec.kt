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
/** How a [GradientSpec] paints its color stops. */
enum class GradientType { LINEAR, RADIAL, SWEEP }

/**
 * A multi-stop gradient. Colors are ARGB longs like everything else in the
 * theme, so translucent stops work (a see-through gradient over a background
 * image acts as a tinted scrim). [angleDeg] rotates LINEAR and SWEEP
 * gradients; RADIAL ignores it.
 */
@Serializable
data class GradientSpec(
    val colors: List<Long>,
    val type: GradientType = GradientType.LINEAR,
    val angleDeg: Float = 45f,
)

/** Outline used for every key. ROUNDED follows the corner-radius sliders. */
enum class KeyShapeKind { ROUNDED, PILL, CUT, SQUIRCLE }

/**
 * Live theme animation. FLOW drifts the board gradient along its axis;
 * HUE_CYCLE slowly rotates the hue of the board gradient (or the solid board
 * color). Only runs while the keyboard is on screen.
 */
enum class ThemeAnimation { NONE, FLOW, HUE_CYCLE }

@Serializable
data class ThemeSpec(
    val id: String,
    val name: String,
    val dark: Boolean = true,
    // Board
    val boardBackground: Long = 0xFF17181C,
    /** Painted instead of [boardBackground] when set; stops may be translucent. */
    val boardGradient: GradientSpec? = null,
    /** Absolute path of a local background image; never set in exports. */
    val backgroundImage: String? = null,
    /**
     * Absolute path of a separate landscape background image; used only when
     * the screen is landscape, falling back to [backgroundImage] when null.
     * Lets a portrait photo and a wide crop each fit their orientation instead
     * of one image being letterboxed or over-cropped in the other. Never set in
     * exports (travels as [backgroundImageLandscapeBase64]).
     */
    val backgroundImageLandscape: String? = null,
    /** Alpha applied to the background image itself (shared by both orientations). */
    val backgroundImageOpacity: Float = 1f,
    /** Blur radius applied to the background image (0 = sharp, 25 = heavy). */
    val backgroundImageBlur: Float = 0f,
    /** Image bytes for export/import payloads only; stripped after import. */
    val backgroundImageBase64: String? = null,
    /** Landscape image bytes for export/import payloads only; stripped after import. */
    val backgroundImageLandscapeBase64: String? = null,
    // Keys
    val keyShape: KeyShapeKind = KeyShapeKind.ROUNDED,
    /** Painted over letter keys when set (subtle sheen); stops may be translucent. */
    val keyGradient: GradientSpec? = null,
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
    // Animation
    val animation: ThemeAnimation = ThemeAnimation.NONE,
    /** Multiplier on the animation cycle speed; 1 = one cycle every ~16 s. */
    val animationSpeed: Float = 1f,
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
    // Gradient themes. Keys are translucent so the gradient shows through;
    // their flattened (opaque) versions still contrast with the key text, which
    // matters because panel surfaces flatten alpha (see schemeFor).
    themeFromSeed("builtin_nebula", "Nebula", 0xFF8E5AC8, dark = true).copy(
        boardGradient = GradientSpec(
            listOf(0xFF2A1758, 0xFF15306B, 0xFF0B1B3A), GradientType.LINEAR, 135f,
        ),
        keyBackground = 0x59453076,
        modifierKeyBackground = 0x40311F5C,
        keyText = 0xFFF2ECFF,
        enterKeyBackground = 0xFF8E5AC8,
        enterKeyText = 0xFFF6F0FF,
        accent = 0xFFB79CFF,
        popupBackground = 0xFF352759,
        toolCircleBackground = 0x59453076,
        chipBackground = 0x40311F5C,
    ),
    themeFromSeed("builtin_sunset_drift", "Sunset drift", 0xFFE07B39, dark = true).copy(
        boardGradient = GradientSpec(
            listOf(0xFF3B1035, 0xFF7A1E3C, 0xFFC2542B), GradientType.LINEAR, 100f,
        ),
        keyBackground = 0x59561F33,
        modifierKeyBackground = 0x403D1428,
        keyText = 0xFFFFEFE4,
        accent = 0xFFFFB07C,
        popupBackground = 0xFF57203A,
        toolCircleBackground = 0x59561F33,
        chipBackground = 0x403D1428,
        animation = ThemeAnimation.FLOW,
        animationSpeed = 1f,
    ),
    themeFromSeed("builtin_aurora", "Aurora", 0xFF3E8E5A, dark = true).copy(
        boardGradient = GradientSpec(
            listOf(0xFF0E3B43, 0xFF14543B, 0xFF14324F), GradientType.SWEEP, 0f,
        ),
        keyBackground = 0x59204A44,
        modifierKeyBackground = 0x40163830,
        keyText = 0xFFE6FFF4,
        accent = 0xFF7CE8B5,
        popupBackground = 0xFF1C4A42,
        toolCircleBackground = 0x59204A44,
        chipBackground = 0x40163830,
        animation = ThemeAnimation.HUE_CYCLE,
        animationSpeed = 0.6f,
    ),
    themeFromSeed("builtin_deep_sea", "Deep sea", 0xFF3B82C4, dark = true).copy(
        boardGradient = GradientSpec(
            listOf(0xFF0E2A4A, 0xFF071523), GradientType.RADIAL, 0f,
        ),
        keyBackground = 0x591E4568,
        modifierKeyBackground = 0x4014304A,
        popupBackground = 0xFF1B3C5C,
        toolCircleBackground = 0x591E4568,
        chipBackground = 0x4014304A,
    ),
    themeFromSeed("builtin_glacier", "Glacier", 0xFF5B7DB1, dark = false).copy(
        boardGradient = GradientSpec(
            listOf(0xFFDDE9F7, 0xFFEAF3EE, 0xFFF6EEE7), GradientType.LINEAR, 20f,
        ),
    ),
    // Shape showcases: pill keys with a soft radial glow, cut-corner steel.
    themeFromSeed("builtin_bubble", "Bubble", 0xFF00897B, dark = true).copy(
        keyShape = KeyShapeKind.PILL,
        boardGradient = GradientSpec(
            listOf(0xFF16302E, 0xFF0D1517), GradientType.RADIAL, 0f,
        ),
        keyBackground = 0x5926504B,
        modifierKeyBackground = 0x401B3B37,
        toolCircleBackground = 0x5926504B,
        chipBackground = 0x401B3B37,
    ),
    themeFromSeed("builtin_facet", "Facet", 0xFF7A8699, dark = true).copy(
        keyShape = KeyShapeKind.CUT,
        boardGradient = GradientSpec(
            listOf(0xFF23262C, 0xFF14161A), GradientType.LINEAR, 90f,
        ),
        keyGradient = GradientSpec(
            listOf(0x14FFFFFF, 0x00FFFFFF), GradientType.LINEAR, 90f,
        ),
        keyBorderColor = 0x338A94A6,
        keyBorderWidthDp = 1f,
    ),
)

/** Seed swatches offered by the editor's "start from a color" row. */
val SeedSwatches: List<Long> = listOf(
    0xFF4C8DF6, 0xFF3B82C4, 0xFF00897B, 0xFF3E8E5A, 0xFF7CB342,
    0xFFF9A825, 0xFFE07B39, 0xFFCE4257, 0xFFB84A8E, 0xFF8E5AC8,
    0xFF5C6BC0, 0xFF7A8699, 0xFF6D4C41, 0xFF546E7A, 0xFF37393F,
)
