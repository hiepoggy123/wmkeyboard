package com.wasimaster.wmkeyboard.ime.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.layout.ContentScale
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.wasimaster.wmkeyboard.core.settings.ColorVisionFilter
import com.wasimaster.wmkeyboard.core.script.FontHint
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.theme.BuiltInThemes
import com.wasimaster.wmkeyboard.core.theme.ColorVision
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import com.wasimaster.wmkeyboard.core.theme.GradientSpec
import com.wasimaster.wmkeyboard.core.theme.KeyShapeKind
import com.wasimaster.wmkeyboard.core.theme.ThemeAnimation
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.theme.blurredBy
import com.wasimaster.wmkeyboard.core.theme.brush
import com.wasimaster.wmkeyboard.core.theme.hueShift
import com.wasimaster.wmkeyboard.core.theme.keyShapeFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fully resolved keyboard theme: every color the keyboard UI draws with,
 * plus the effective radii. Nullable [ThemeSpec] fields have been collapsed
 * into derived values by [resolveKbTheme].
 */
data class KbTheme(
    val dark: Boolean,
    val board: Color,
    val boardGradient: GradientSpec?,
    val backgroundImage: String?,
    /** Landscape override for [backgroundImage]; null falls back to it. */
    val backgroundImageLandscape: String?,
    val backgroundImageOpacity: Float,
    val backgroundImageBlur: Float,
    val keyShapeKind: KeyShapeKind,
    val keyGradient: GradientSpec?,
    val key: Color,
    val keyText: Color,
    val modifierKey: Color,
    val modifierKeyText: Color,
    val enterKey: Color,
    val enterKeyText: Color,
    val pressedKey: Color,
    val keyBorder: Color?,
    val keyBorderWidthDp: Float,
    val accent: Color,
    /** Colour of the glide-typing trail; defaults to [accent] when a theme leaves it unset. */
    val gestureTrail: Color,
    val popup: Color,
    val popupText: Color,
    val toolbarIcon: Color,
    val toolCircle: Color,
    val toolCircleActive: Color,
    val toolCircleActiveIcon: Color,
    val chip: Color,
    val suggestionText: Color,
    val secondaryText: Color,
    val divider: Color,
    val keyRadiusDp: Int,
    val popupRadiusDp: Int,
    val toolRadiusDp: Int,
    val animation: ThemeAnimation,
    val animationSpeed: Float,
    /**
     * The accessibility setting, carried on the theme rather than threaded
     * through every composable that animates. It is not a colour, but neither
     * are the radii or [animation] beside it — this object is already the
     * "how the keyboard presents itself" bundle, and it is the one thing
     * every drawing composable can reach through [LocalKbTheme]. The blinking
     * carets in particular sit four call sites deep in panels that never
     * otherwise see settings.
     */
    val reduceMotion: Boolean,
)

/** The resolved outline every key draws with. */
fun KbTheme.keyShape() = keyShapeFor(keyShapeKind, keyRadiusDp)

val LocalKbTheme = staticCompositionLocalOf<KbTheme> {
    error("LocalKbTheme not provided")
}

/**
 * Emoji font for the keyboard's own emoji rendering (panel, row, strip);
 * null uses the system emoji font. Kept separate from the text font — a
 * display face has no emoji glyphs and vice versa.
 */
val LocalEmojiFontFamily = staticCompositionLocalOf<FontFamily?> { null }

/** Every Material text style re-based onto [family]; null keeps the defaults. */
private fun typographyWith(family: FontFamily?): Typography {
    val base = Typography()
    if (family == null) return base
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

private fun colorOf(argb: Long): Color = Color(argb.toInt())

/** Blend of [top] at [alpha] flattened over [base] — guaranteed-contrast trick. */
private fun blendOver(top: Color, base: Color, alpha: Float): Color =
    top.copy(alpha = alpha).compositeOver(base)

/** WCAG-style contrast ratio between two (assumed opaque) colors. */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return max(la, lb) / min(la, lb)
}

/**
 * First of [candidates] that actually reads on [background] (≥ 3:1, the
 * large-text/UI-component threshold), else plain black/white. Lets active
 * chips keep the theme's accent personality when it's legible and fall
 * back to guaranteed contrast when it isn't — accent text on an
 * accent-tinted chip was unreadable in most light themes.
 */
private fun legibleOn(background: Color, candidates: List<Color>): Color =
    candidates.firstOrNull { contrastRatio(it, background) >= 3f }
        ?: if (background.luminance() > 0.5f) Color.Black else Color.White

/**
 * The default (system) theme, derived from the Material scheme. In dark
 * mode the key/circle colors are blends of onSurface over a darkened board
 * instead of the scheme's surfaceContainer roles: dynamic dark palettes
 * often flatten those to near-identical tones, which is exactly the washed
 * out look this replaces.
 */
private fun defaultKbTheme(
    scheme: ColorScheme,
    dark: Boolean,
    amoled: Boolean,
    settings: KeyboardSettings,
): KbTheme {
    val board = when {
        amoled -> Color.Black
        dark -> lerp(scheme.surfaceContainerLowest, Color.Black, 0.35f)
        else -> scheme.surfaceContainerLow
    }
    val key = if (dark) blendOver(scheme.onSurface, board, 0.15f) else scheme.surfaceContainerHighest
    val modifier = if (dark) blendOver(scheme.onSurface, board, 0.08f) else scheme.surfaceContainerHigh
    val toolCircle = if (dark) blendOver(scheme.onSurface, board, 0.14f) else scheme.surfaceContainerHighest
    val popup = if (dark) blendOver(scheme.onSurface, board, 0.20f) else scheme.surfaceContainerHighest
    val chip = if (dark) blendOver(scheme.onSurface, board, 0.10f) else scheme.surfaceContainer
    // Pressed keys darken/lighten neutrally instead of flashing the accent
    // (primaryContainer) — a themed highlight on every keystroke reads as
    // noise, a grayscale shift reads as depression.
    val pressed = if (dark) blendOver(scheme.onSurface, board, 0.32f)
        else blendOver(scheme.onSurface, key, 0.14f)
    return KbTheme(
        dark = dark,
        board = board,
        boardGradient = null,
        backgroundImage = null,
        backgroundImageLandscape = null,
        backgroundImageOpacity = 1f,
        backgroundImageBlur = 0f,
        keyShapeKind = KeyShapeKind.ROUNDED,
        keyGradient = null,
        key = key,
        keyText = scheme.onSurface,
        modifierKey = modifier,
        modifierKeyText = scheme.onSurface,
        enterKey = scheme.primary,
        enterKeyText = scheme.onPrimary,
        pressedKey = pressed,
        keyBorder = null,
        keyBorderWidthDp = 0f,
        accent = scheme.primary,
        gestureTrail = scheme.primary,
        popup = popup,
        popupText = scheme.onSurface,
        toolbarIcon = scheme.onSurfaceVariant,
        toolCircle = toolCircle,
        toolCircleActive = scheme.primaryContainer,
        // onPrimaryContainer, not primary: primary-on-primaryContainer is
        // tone-on-tone (blue text on light blue) and fails contrast in
        // most light palettes.
        toolCircleActiveIcon = legibleOn(
            scheme.primaryContainer,
            listOf(scheme.onPrimaryContainer, scheme.primary),
        ),
        chip = chip,
        suggestionText = scheme.onSurface,
        secondaryText = scheme.onSurfaceVariant,
        divider = scheme.outlineVariant,
        keyRadiusDp = settings.keyCornerRadiusDp,
        popupRadiusDp = 12,
        toolRadiusDp = settings.toolCircleRadiusDp,
        animation = ThemeAnimation.NONE,
        animationSpeed = 1f,
        reduceMotion = settings.reduceMotion,
    )
}

/** Resolves a stored [ThemeSpec] into a [KbTheme], deriving nullable fields. */
private fun specKbTheme(spec: ThemeSpec, settings: KeyboardSettings): KbTheme {
    val board = colorOf(spec.boardBackground)
    val key = colorOf(spec.keyBackground)
    val keyText = colorOf(spec.keyText)
    val accent = colorOf(spec.accent)
    // Derived pressed state shifts toward the text color (a neutral
    // darken/lighten of the key), not the accent — same reasoning as the
    // default theme. Themes that want a colored press set it explicitly.
    val pressed = spec.pressedKeyBackground?.let(::colorOf) ?: lerp(key, keyText, 0.25f)
    val secondary = keyText.copy(alpha = 0.65f)
    return KbTheme(
        dark = spec.dark,
        board = board,
        boardGradient = spec.boardGradient,
        backgroundImage = spec.backgroundImage,
        backgroundImageLandscape = spec.backgroundImageLandscape,
        backgroundImageOpacity = spec.backgroundImageOpacity,
        backgroundImageBlur = spec.backgroundImageBlur,
        keyShapeKind = spec.keyShape,
        keyGradient = spec.keyGradient,
        key = key,
        keyText = keyText,
        modifierKey = colorOf(spec.modifierKeyBackground),
        modifierKeyText = spec.modifierKeyText?.let(::colorOf) ?: keyText,
        enterKey = colorOf(spec.enterKeyBackground),
        enterKeyText = colorOf(spec.enterKeyText),
        pressedKey = pressed,
        keyBorder = spec.keyBorderColor?.let(::colorOf),
        keyBorderWidthDp = spec.keyBorderWidthDp,
        accent = accent,
        gestureTrail = spec.gestureTrailColor?.let(::colorOf) ?: accent,
        // A light theme's keyText is dark, so a heavy blend produced a dark
        // popup — and popupText also falls back to keyText (dark), giving an
        // unreadable dark-on-dark key preview. A light theme needs a subtle
        // lift (like the dark branch) so the dark popupText stays legible.
        popup = spec.popupBackground?.let(::colorOf)
            ?: blendOver(keyText, board, if (spec.dark) 0.20f else 0.06f),
        popupText = spec.popupText?.let(::colorOf) ?: keyText,
        toolbarIcon = spec.toolbarIcon?.let(::colorOf) ?: secondary,
        toolCircle = spec.toolCircleBackground?.let(::colorOf)
            ?: blendOver(keyText, board, 0.14f),
        toolCircleActive = spec.toolCircleActiveBackground?.let(::colorOf) ?: pressed,
        toolCircleActiveIcon = legibleOn(
            spec.toolCircleActiveBackground?.let(::colorOf) ?: pressed,
            listOf(accent, keyText),
        ),
        chip = spec.chipBackground?.let(::colorOf) ?: colorOf(spec.modifierKeyBackground),
        suggestionText = spec.suggestionText?.let(::colorOf) ?: keyText,
        secondaryText = secondary,
        divider = keyText.copy(alpha = 0.25f),
        keyRadiusDp = spec.keyCornerRadiusDp ?: settings.keyCornerRadiusDp,
        popupRadiusDp = spec.popupCornerRadiusDp ?: 12,
        toolRadiusDp = spec.toolCircleRadiusDp ?: settings.toolCircleRadiusDp,
        animation = spec.animation,
        animationSpeed = spec.animationSpeed,
        reduceMotion = settings.reduceMotion,
    )
}

/**
 * Material scheme for M3 components inside the keyboard (tabs, buttons,
 * list bits in the panels), mapped from the resolved theme so everything
 * follows the theme's colors. Surfaces are flattened to opaque so text on
 * them stays readable even when the board itself is translucent.
 */
private fun schemeFor(kb: KbTheme): ColorScheme {
    val base = if (kb.dark) darkColorScheme() else lightColorScheme()
    val opaqueBoard = kb.board.copy(alpha = 1f)
    return base.copy(
        primary = kb.accent,
        onPrimary = kb.enterKeyText,
        primaryContainer = kb.pressedKey.copy(alpha = 1f),
        onPrimaryContainer = kb.keyText,
        background = opaqueBoard,
        onBackground = kb.keyText,
        surface = opaqueBoard,
        onSurface = kb.keyText,
        surfaceContainerLowest = opaqueBoard,
        surfaceContainerLow = opaqueBoard,
        surfaceContainer = kb.chip.copy(alpha = 1f),
        surfaceContainerHigh = kb.modifierKey.copy(alpha = 1f),
        surfaceContainerHighest = kb.key.copy(alpha = 1f),
        onSurfaceVariant = kb.secondaryText,
        outlineVariant = kb.divider,
    )
}

private fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun GradientSpec.mapColors(f: (Color) -> Color): GradientSpec =
    copy(colors = colors.map { f(Color(it.toInt())).argbLong() })

/** Every colour in the theme put through [f]; non-colour fields untouched. */
private fun KbTheme.mapColors(f: (Color) -> Color): KbTheme = copy(
    board = f(board),
    boardGradient = boardGradient?.mapColors(f),
    keyGradient = keyGradient?.mapColors(f),
    key = f(key),
    keyText = f(keyText),
    modifierKey = f(modifierKey),
    modifierKeyText = f(modifierKeyText),
    enterKey = f(enterKey),
    enterKeyText = f(enterKeyText),
    pressedKey = f(pressedKey),
    keyBorder = keyBorder?.let(f),
    accent = f(accent),
    gestureTrail = f(gestureTrail),
    popup = f(popup),
    popupText = f(popupText),
    toolbarIcon = f(toolbarIcon),
    toolCircle = f(toolCircle),
    toolCircleActive = f(toolCircleActive),
    toolCircleActiveIcon = f(toolCircleActiveIcon),
    chip = f(chip),
    suggestionText = f(suggestionText),
    secondaryText = f(secondaryText),
    divider = f(divider),
)

/** Near-black or near-white, whichever reads on [background]. */
private fun maxContrastOn(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xFF000000) else Color(0xFFFFFFFF)

/**
 * Applies the accessibility settings that are palette-level: colour-vision
 * correction, forced contrast and key outlines. Doing it here — after the
 * theme (default or stored) has fully resolved — means every drawing site
 * downstream reads the adjusted values through [LocalKbTheme] without
 * knowing accessibility exists, and it works identically for built-in,
 * custom and dynamic-colour themes.
 */
private fun KbTheme.accessibilityAdjusted(settings: KeyboardSettings): KbTheme {
    // Correction runs first so the contrast pass gets the last word: after
    // daltonization shifts hues, the text/background relationship is what
    // matters and it must not be re-broken.
    var kb = if (settings.colorVisionFilter == ColorVisionFilter.NONE) {
        this
    } else {
        mapColors { ColorVision.correct(it, settings.colorVisionFilter) }
    }

    if (settings.highContrastKeys) {
        // Push the board away from the keys as well as fixing the text: a
        // high-contrast palette that only touches labels still leaves the
        // key *shapes* indistinct, which is the harder problem for low vision.
        val board = if (kb.dark) Color(0xFF000000) else Color(0xFFFFFFFF)
        val key = if (kb.dark) Color(0xFF2A2A2A) else Color(0xFFEDEDED)
        val modifier = if (kb.dark) Color(0xFF141414) else Color(0xFFD2D2D2)
        kb = kb.copy(
            board = board,
            // Gradients, images and motion all fight legibility — drop them.
            boardGradient = null,
            keyGradient = null,
            backgroundImage = null,
            backgroundImageLandscape = null,
            animation = ThemeAnimation.NONE,
            key = key,
            modifierKey = modifier,
            keyText = maxContrastOn(key),
            modifierKeyText = maxContrastOn(modifier),
            enterKeyText = maxContrastOn(kb.enterKey),
            popupText = maxContrastOn(kb.popup),
            suggestionText = maxContrastOn(board),
            toolbarIcon = maxContrastOn(board),
            secondaryText = maxContrastOn(board).copy(alpha = 0.75f),
            divider = maxContrastOn(board).copy(alpha = 0.4f),
        )
    }

    if (settings.keyOutlines && kb.keyBorderWidthDp <= 0f) {
        kb = kb.copy(
            keyBorder = maxContrastOn(kb.key).copy(alpha = if (settings.highContrastKeys) 0.9f else 0.45f),
            keyBorderWidthDp = 1.5f,
        )
    }
    return kb
}

/**
 * Resolves the selected theme and provides [LocalKbTheme] plus a matching
 * MaterialTheme to [content]. The default theme follows the theme mode
 * (system/light/dark/AMOLED) and dynamic color like before; stored themes
 * are fixed palettes.
 */
@Composable
fun KeyboardThemeProvider(settings: KeyboardSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    // Auto-theme, when on, picks the id from the system light/dark setting and
    // ignores the manually-selected keyboardThemeId (the theme tool is read-only
    // while it's active). Off, the selected id wins as before.
    val auto = settings.autoTheme
    val effectiveId = if (auto.enabled) {
        if (systemDark) auto.darkThemeId else auto.lightThemeId
    } else {
        settings.keyboardThemeId
    }
    val spec = if (effectiveId == DEFAULT_THEME_ID) {
        null
    } else {
        settings.customThemes.find { it.id == effectiveId }
            ?: BuiltInThemes.find { it.id == effectiveId }
    }
    val resolved = if (spec == null) {
        // Under auto-theme the chosen slot decides light vs dark directly;
        // otherwise the theme mode does.
        val dark = if (auto.enabled) systemDark else when (settings.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK, ThemeMode.AMOLED -> true
        }
        // AMOLED is a dark-only variant; gating on `dark` keeps a light slot
        // (or Light mode) from turning the board black.
        val amoled = dark && settings.themeMode == ThemeMode.AMOLED
        val supportsDynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val scheme = when {
            supportsDynamic && dark -> dynamicDarkColorScheme(context)
            supportsDynamic -> dynamicLightColorScheme(context)
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
        defaultKbTheme(scheme, dark, amoled = amoled, settings)
    } else {
        specKbTheme(spec, settings)
    }
    val kb = animatedKbTheme(resolved.accessibilityAdjusted(settings))
    // The chosen font rides in through the Material typography, so every
    // Text on the keyboard — key labels, suggestions, panels — follows it
    // without per-call plumbing. Emojis get their own family via
    // LocalEmojiFontFamily at the few places emojis are drawn.
    // Latin-script and Bengali modes each have their own font choice. The
    // Bengali faces all carry Latin glyphs too, so Avro's romanized keys
    // and mixed suggestion strips stay in one face while Bengali is active.
    // (Phase 5 fans this out to a per-script font map; for now the two shipped
    // scripts keep their existing choices.)
    val fontId = when (settings.script.fontHint) {
        FontHint.BENGALI -> settings.bengaliFontId
        // Latin and — until a full per-script font map lands — every other
        // script use the main font; Android falls back to a system face for
        // glyphs it lacks (Korean, etc.).
        else -> settings.keyFontId
    }
    val keyFontFamily = remember(fontId, settings.customFontName, settings.customBengaliFontName) {
        KeyboardFonts.family(context, fontId)
    }
    val emojiFontFamily = remember(settings.emojiFont) {
        KeyboardFonts.emojiFamily(context, settings.emojiFont)
    }
    MaterialTheme(
        colorScheme = schemeFor(kb),
        typography = remember(keyFontFamily) { typographyWith(keyFontFamily) },
    ) {
        CompositionLocalProvider(
            LocalKbTheme provides kb,
            LocalEmojiFontFamily provides emojiFontFamily,
            content = content,
        )
    }
}

/** How long a theme switch eases from the old palette to the new one. */
private const val THEME_TRANSITION_MS = 320

/**
 * Crossfades whole themes: whenever the resolved [target] changes while the
 * keyboard is composed — the user tapping a theme, or the AI theme tool
 * swapping one in — every colour, gradient and radius eases from what was on
 * screen to the new values instead of snapping. The first composition and
 * reduce-motion return [target] outright, so opening the keyboard and
 * accessibility users see no fade.
 *
 * An interrupted switch (tapping a second theme mid-fade) restarts from the
 * currently blended colours, so rapid taps stay smooth instead of jumping
 * back to a stale endpoint.
 */
@Composable
fun animatedKbTheme(target: KbTheme): KbTheme {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        if (target.reduceMotion) {
            // Accessibility: jump straight to the new theme, no crossfade.
            from = target
            to = target
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        // Snapshot what is on screen now so an interrupted fade continues from
        // the blend rather than from the previous target.
        from = lerpKbTheme(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(THEME_TRANSITION_MS, easing = FastOutSlowInEasing))
    }
    val p = progress.value
    return if (p >= 1f || from == to) to else lerpKbTheme(from, to, p)
}

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun lerpI(a: Int, b: Int, t: Float): Int = Math.round(a + (b - a) * t)

/** Blend two ARGB longs (theme's storage form) through [Color] space. */
private fun lerpLong(a: Long, b: Long, t: Float): Long =
    lerp(Color(a.toInt()), Color(b.toInt()), t).toArgb().toLong() and 0xFFFFFFFFL

private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun lerpColorOrNull(a: Color?, b: Color?, t: Float): Color? {
    if (a == null && b == null) return null
    // A null border means "no outline"; fade it in/out via a transparent stand-in.
    val from = a ?: b!!.copy(alpha = 0f)
    val to = b ?: a!!.copy(alpha = 0f)
    return lerp(from, to, t)
}

/**
 * Interpolate two gradients. Same type and stop count blend stop-for-stop; a
 * missing gradient is promoted to a flat gradient of its side's solid colour
 * ([aSolid]/[bSolid]) so a solid⇆gradient switch still eases. Mismatched
 * shapes (different type or stop count) can't blend, so they flip at the
 * midpoint.
 */
private fun lerpGradient(
    a: GradientSpec?,
    b: GradientSpec?,
    t: Float,
    aSolid: Color,
    bSolid: Color,
): GradientSpec? {
    if (a == null && b == null) return null
    if (a != null && b != null && (a.type != b.type || a.colors.size != b.colors.size)) {
        return if (t >= 0.5f) b else a
    }
    val shape = a ?: b!!
    val flatSolid = (if (a == null) aSolid else bSolid).toArgbLong()
    val flat = List(shape.colors.size) { flatSolid }
    val fromColors = a?.colors ?: flat
    val toColors = b?.colors ?: flat
    return GradientSpec(
        colors = fromColors.indices.map { lerpLong(fromColors[it], toColors[it], t) },
        type = shape.type,
        angleDeg = lerpF(a?.angleDeg ?: shape.angleDeg, b?.angleDeg ?: shape.angleDeg, t),
    )
}

/**
 * Every drawable field of two themes blended at [t] (0 = [a], 1 = [b]).
 * Colours, gradients, radii and widths interpolate; discrete fields (shape
 * kind, animation, background image, dark flag) flip at the midpoint since
 * they can't be tweened.
 */
private fun lerpKbTheme(a: KbTheme, b: KbTheme, t: Float): KbTheme {
    if (t <= 0f) return a
    if (t >= 1f) return b
    val past = t >= 0.5f
    return KbTheme(
        dark = if (past) b.dark else a.dark,
        board = lerp(a.board, b.board, t),
        boardGradient = lerpGradient(a.boardGradient, b.boardGradient, t, a.board, b.board),
        backgroundImage = if (past) b.backgroundImage else a.backgroundImage,
        backgroundImageLandscape = if (past) b.backgroundImageLandscape else a.backgroundImageLandscape,
        backgroundImageOpacity = lerpF(a.backgroundImageOpacity, b.backgroundImageOpacity, t),
        backgroundImageBlur = lerpF(a.backgroundImageBlur, b.backgroundImageBlur, t),
        keyShapeKind = if (past) b.keyShapeKind else a.keyShapeKind,
        keyGradient = lerpGradient(a.keyGradient, b.keyGradient, t, a.key, b.key),
        key = lerp(a.key, b.key, t),
        keyText = lerp(a.keyText, b.keyText, t),
        modifierKey = lerp(a.modifierKey, b.modifierKey, t),
        modifierKeyText = lerp(a.modifierKeyText, b.modifierKeyText, t),
        enterKey = lerp(a.enterKey, b.enterKey, t),
        enterKeyText = lerp(a.enterKeyText, b.enterKeyText, t),
        pressedKey = lerp(a.pressedKey, b.pressedKey, t),
        keyBorder = lerpColorOrNull(a.keyBorder, b.keyBorder, t),
        keyBorderWidthDp = lerpF(a.keyBorderWidthDp, b.keyBorderWidthDp, t),
        accent = lerp(a.accent, b.accent, t),
        gestureTrail = lerp(a.gestureTrail, b.gestureTrail, t),
        popup = lerp(a.popup, b.popup, t),
        popupText = lerp(a.popupText, b.popupText, t),
        toolbarIcon = lerp(a.toolbarIcon, b.toolbarIcon, t),
        toolCircle = lerp(a.toolCircle, b.toolCircle, t),
        toolCircleActive = lerp(a.toolCircleActive, b.toolCircleActive, t),
        toolCircleActiveIcon = lerp(a.toolCircleActiveIcon, b.toolCircleActiveIcon, t),
        chip = lerp(a.chip, b.chip, t),
        suggestionText = lerp(a.suggestionText, b.suggestionText, t),
        secondaryText = lerp(a.secondaryText, b.secondaryText, t),
        divider = lerp(a.divider, b.divider, t),
        keyRadiusDp = lerpI(a.keyRadiusDp, b.keyRadiusDp, t),
        popupRadiusDp = lerpI(a.popupRadiusDp, b.popupRadiusDp, t),
        toolRadiusDp = lerpI(a.toolRadiusDp, b.toolRadiusDp, t),
        animation = if (past) b.animation else a.animation,
        animationSpeed = lerpF(a.animationSpeed, b.animationSpeed, t),
        reduceMotion = b.reduceMotion,
    )
}

/**
 * The theme the default ("Auto") id resolves to right now — dynamic device
 * colors in the current light/dark mode. The themes panel uses this for the
 * Auto swatch, so it previews the device palette instead of parroting
 * whatever theme happens to be active.
 */
@Composable
fun autoKbTheme(settings: KeyboardSettings): KbTheme {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val supportsDynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        supportsDynamic && dark -> dynamicDarkColorScheme(context)
        supportsDynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    return defaultKbTheme(scheme, dark, amoled = settings.themeMode == ThemeMode.AMOLED, settings)
}

/**
 * The animation clock: 0..1 phase looping while the keyboard is composed.
 * Returns a static 0 for NONE, and for reduce-motion, so no infinite
 * transition runs at all — this one loops for as long as the keyboard is on
 * screen, so leaving it running was the single largest thing the setting
 * failed to stop.
 */
@Composable
fun themeAnimationPhase(animation: ThemeAnimation, speed: Float, reduceMotion: Boolean): Float {
    if (animation == ThemeAnimation.NONE || reduceMotion) return 0f
    val transition = rememberInfiniteTransition(label = "themeAnim")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (16000f / speed.coerceIn(0.25f, 3f)).toInt(),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    return phase
}

/**
 * Board background: optional image (center-cropped, blurred at decode time,
 * with its own opacity) under the board layer. The board layer is the
 * gradient when one is set, else the solid board color; either can be
 * translucent to act as a scrim over the image (or, with no image, to let
 * the app behind shine through). FLOW/HUE_CYCLE animate the board layer.
 */
@Composable
fun BoxScope.BoardBackground(kb: KbTheme) {
    // In landscape prefer the theme's landscape image, falling back to the
    // portrait one when it has none — so a theme with a single image still
    // shows it in both orientations, exactly as before this split existed.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val imagePath = if (landscape) kb.backgroundImageLandscape ?: kb.backgroundImage else kb.backgroundImage
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null, imagePath, kb.backgroundImageBlur,
    ) {
        value = withContext(Dispatchers.IO) {
            imagePath?.let { path ->
                runCatching {
                    BitmapFactory.decodeFile(path)
                        ?.blurredBy(kb.backgroundImageBlur)
                        ?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .alpha(kb.backgroundImageOpacity),
            contentScale = ContentScale.Crop,
        )
    }
    val phase = themeAnimationPhase(kb.animation, kb.animationSpeed, kb.reduceMotion)
    val gradient = kb.boardGradient
    if (gradient != null) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(gradient.brush(kb.animation, phase)),
        )
    } else {
        val board = if (kb.animation == ThemeAnimation.HUE_CYCLE) {
            hueShift(kb.board, phase * 360f)
        } else {
            kb.board
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(board),
        )
    }
}
