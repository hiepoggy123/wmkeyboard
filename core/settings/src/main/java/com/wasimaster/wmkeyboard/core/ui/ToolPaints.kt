package com.wasimaster.wmkeyboard.core.ui

import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool

/*
 * The one place that turns the tool-colour settings into something to paint
 * with. The colours themselves — the per-tool defaults, the derived gradient
 * end, the brush — are in `:core:common`'s ToolColors.kt, in this same package;
 * only the reading of `KeyboardSettings` has to live up here, because that
 * class is defined in this module.
 */

/**
 * The paint for [tool] under [settings], or null when "Colorful tool icons" is
 * off and the tool should wear whatever colour its surroundings use.
 */
fun toolAccentPaint(tool: ToolbarTool, settings: KeyboardSettings): ToolPaint? {
    if (!settings.coloredToolIcons) return null
    val start = toolAccentColor(tool, settings.toolColorOverrides)
    if (!settings.toolIconGradients) return ToolPaint(start, null)
    return ToolPaint(
        start,
        toolAccentEndColor(tool, settings.toolColorOverrides, settings.toolColorEndOverrides),
    )
}
