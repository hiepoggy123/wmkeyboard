package com.wasimaster.wmkeyboard.ime.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.theme.BuiltInThemes
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
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
    val backgroundImage: String?,
    val backgroundImageOpacity: Float,
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
)

val LocalKbTheme = staticCompositionLocalOf<KbTheme> {
    error("LocalKbTheme not provided")
}

private fun colorOf(argb: Long): Color = Color(argb.toInt())

/** Blend of [top] at [alpha] flattened over [base] — guaranteed-contrast trick. */
private fun blendOver(top: Color, base: Color, alpha: Float): Color =
    top.copy(alpha = alpha).compositeOver(base)

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
    return KbTheme(
        dark = dark,
        board = board,
        backgroundImage = null,
        backgroundImageOpacity = 1f,
        key = key,
        keyText = scheme.onSurface,
        modifierKey = modifier,
        modifierKeyText = scheme.onSurface,
        enterKey = scheme.primary,
        enterKeyText = scheme.onPrimary,
        pressedKey = scheme.primaryContainer,
        keyBorder = null,
        keyBorderWidthDp = 0f,
        accent = scheme.primary,
        popup = popup,
        popupText = scheme.onSurface,
        toolbarIcon = scheme.onSurfaceVariant,
        toolCircle = toolCircle,
        toolCircleActive = scheme.primaryContainer,
        toolCircleActiveIcon = scheme.primary,
        chip = chip,
        suggestionText = scheme.onSurface,
        secondaryText = scheme.onSurfaceVariant,
        divider = scheme.outlineVariant,
        keyRadiusDp = settings.keyCornerRadiusDp,
        popupRadiusDp = 12,
        toolRadiusDp = settings.toolCircleRadiusDp,
    )
}

/** Resolves a stored [ThemeSpec] into a [KbTheme], deriving nullable fields. */
private fun specKbTheme(spec: ThemeSpec, settings: KeyboardSettings): KbTheme {
    val board = colorOf(spec.boardBackground)
    val key = colorOf(spec.keyBackground)
    val keyText = colorOf(spec.keyText)
    val accent = colorOf(spec.accent)
    val pressed = spec.pressedKeyBackground?.let(::colorOf) ?: lerp(key, accent, 0.40f)
    val secondary = keyText.copy(alpha = 0.65f)
    return KbTheme(
        dark = spec.dark,
        board = board,
        backgroundImage = spec.backgroundImage,
        backgroundImageOpacity = spec.backgroundImageOpacity,
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
        popup = spec.popupBackground?.let(::colorOf)
            ?: blendOver(keyText, board, if (spec.dark) 0.20f else 0.9f),
        popupText = spec.popupText?.let(::colorOf) ?: keyText,
        toolbarIcon = spec.toolbarIcon?.let(::colorOf) ?: secondary,
        toolCircle = spec.toolCircleBackground?.let(::colorOf)
            ?: blendOver(keyText, board, 0.14f),
        toolCircleActive = spec.toolCircleActiveBackground?.let(::colorOf) ?: pressed,
        toolCircleActiveIcon = accent,
        chip = spec.chipBackground?.let(::colorOf) ?: colorOf(spec.modifierKeyBackground),
        suggestionText = spec.suggestionText?.let(::colorOf) ?: keyText,
        secondaryText = secondary,
        divider = keyText.copy(alpha = 0.25f),
        keyRadiusDp = spec.keyCornerRadiusDp ?: settings.keyCornerRadiusDp,
        popupRadiusDp = spec.popupCornerRadiusDp ?: 12,
        toolRadiusDp = spec.toolCircleRadiusDp ?: settings.toolCircleRadiusDp,
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
    val spec = if (settings.keyboardThemeId == DEFAULT_THEME_ID) {
        null
    } else {
        settings.customThemes.find { it.id == settings.keyboardThemeId }
            ?: BuiltInThemes.find { it.id == settings.keyboardThemeId }
    }
    val kb = if (spec == null) {
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
        defaultKbTheme(scheme, dark, amoled = settings.themeMode == ThemeMode.AMOLED, settings)
    } else {
        specKbTheme(spec, settings)
    }
    MaterialTheme(colorScheme = schemeFor(kb)) {
        CompositionLocalProvider(LocalKbTheme provides kb, content = content)
    }
}

/**
 * Board background: optional image (center-cropped, with its own opacity)
 * under the board color. A translucent board color acts as a scrim over
 * the image; with no image it lets the app behind shine through.
 */
@Composable
fun BoxScope.BoardBackground(kb: KbTheme) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, kb.backgroundImage) {
        value = withContext(Dispatchers.IO) {
            kb.backgroundImage?.let { path ->
                runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
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
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(kb.board),
    )
}
