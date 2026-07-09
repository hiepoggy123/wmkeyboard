package com.wasimaster.wmkeyboard.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool

/**
 * A distinct accent colour per tool, used to tint the tool icons in the
 * Settings app and the keyboard's toolbox when
 * [com.wasimaster.wmkeyboard.core.settings.KeyboardSettings.coloredToolIcons]
 * is on. Related tools share a hue on purpose (all cursor keys are the same
 * neutral, all media tools are warm) so the grid reads as grouped rather than
 * a random confetti of colour, while still being scannable at a glance.
 *
 * Mid-tone values (~Material 400/500) that stay legible on both the light and
 * dark toolbar/circle backgrounds. Exhaustive so a new tool must pick a
 * colour, matching the icon/label maps.
 */
fun toolAccentColor(tool: ToolbarTool): Color = when (tool) {
    // Expressive / media — warm
    ToolbarTool.EMOJI -> Color(0xFFFFB300)
    ToolbarTool.GIF -> Color(0xFFFF7043)
    ToolbarTool.STICKER -> Color(0xFFFFA726)
    ToolbarTool.CAMERA -> Color(0xFFF06292)
    ToolbarTool.THEMES -> Color(0xFFEC407A)

    // Text & clipboard — blues/teals
    ToolbarTool.CLIPBOARD -> Color(0xFF42A5F5)
    ToolbarTool.SNIPPETS -> Color(0xFF26A69A)
    ToolbarTool.TEXT_EDIT -> Color(0xFF5C6BC0)
    ToolbarTool.DICTIONARY -> Color(0xFF26A69A)
    ToolbarTool.HANDWRITING -> Color(0xFF7E57C2)
    ToolbarTool.NUMPAD -> Color(0xFF42A5F5)
    ToolbarTool.SYMBOLS -> Color(0xFF7E57C2)

    // Language help — greens
    ToolbarTool.AUTOCORRECT -> Color(0xFF66BB6A)
    ToolbarTool.GRAMMAR -> Color(0xFF66BB6A)
    ToolbarTool.TRANSLATE -> Color(0xFF29B6F6)

    // Voice / sound — reds/purples
    ToolbarTool.VOICE -> Color(0xFFEF5350)
    ToolbarTool.SOUND_HAPTICS -> Color(0xFFAB47BC)

    // Online / search — blues/cyans
    ToolbarTool.WEB_SEARCH -> Color(0xFF42A5F5)
    ToolbarTool.IMAGE_SEARCH -> Color(0xFF26C6DA)
    ToolbarTool.WIKIPEDIA -> Color(0xFF78909C)
    ToolbarTool.AI -> Color(0xFFAB47BC)

    // Scanners — teals/greens
    ToolbarTool.OCR -> Color(0xFF66BB6A)
    ToolbarTool.QR_SCAN -> Color(0xFF26A69A)
    ToolbarTool.DOC_SCAN -> Color(0xFF29B6F6)

    // Create & convert
    ToolbarTool.CALCULATOR -> Color(0xFFFFA726)
    ToolbarTool.UNIT_CONVERT -> Color(0xFF26C6DA)
    ToolbarTool.CURRENCY -> Color(0xFF66BB6A)
    ToolbarTool.QR_GEN -> Color(0xFF26A69A)
    ToolbarTool.PASSWORD_GEN -> Color(0xFFEF5350)
    ToolbarTool.TYPING_TEST -> Color(0xFFFF7043)

    // Keyboard modes / layout — indigos & neutrals
    ToolbarTool.MODES -> Color(0xFF5C6BC0)
    ToolbarTool.ONE_HANDED -> Color(0xFF78909C)
    ToolbarTool.SPLIT -> Color(0xFF78909C)
    ToolbarTool.FLOATING -> Color(0xFF78909C)
    ToolbarTool.INCOGNITO -> Color(0xFF607D8B)
    ToolbarTool.SETTINGS -> Color(0xFF90A4AE)
    ToolbarTool.UNDO -> Color(0xFF78909C)
    ToolbarTool.REDO -> Color(0xFF78909C)

    // Sensors / utilities
    ToolbarTool.FLASHLIGHT -> Color(0xFFFFCA28)
    ToolbarTool.COMPASS -> Color(0xFFEF5350)
    ToolbarTool.LEVEL -> Color(0xFF26C6DA)
    ToolbarTool.CALENDAR -> Color(0xFFE57373)
    ToolbarTool.WEATHER -> Color(0xFFFFA726)
    ToolbarTool.MOON_PHASE -> Color(0xFF7986CB)

    // Cursor keys — one neutral so they read as a set
    ToolbarTool.CURSOR_LEFT,
    ToolbarTool.CURSOR_RIGHT,
    ToolbarTool.CURSOR_WORD_LEFT,
    ToolbarTool.CURSOR_WORD_RIGHT,
    ToolbarTool.CURSOR_UP,
    ToolbarTool.CURSOR_DOWN,
    ToolbarTool.CURSOR_HOME,
    ToolbarTool.CURSOR_END,
    ToolbarTool.PAGE_UP,
    ToolbarTool.PAGE_DOWN,
    ToolbarTool.SELECT_WORD -> Color(0xFF90A4AE)
}

/**
 * The accent colour to actually paint a tool with: the user's per-tool
 * override from [com.wasimaster.wmkeyboard.core.settings.KeyboardSettings.toolColorOverrides]
 * when they've set one, otherwise the built-in [toolAccentColor] default.
 */
fun toolAccentColor(tool: ToolbarTool, overrides: Map<ToolbarTool, Long>): Color =
    overrides[tool]?.let { Color(it.toInt()) } ?: toolAccentColor(tool)

/** The default accent colour as a stored ARGB long (for seeding the picker). */
fun toolAccentColorArgb(tool: ToolbarTool): Long =
    toolAccentColor(tool).toArgb().toLong() and 0xFFFFFFFFL
