package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.layout.secondaryLayouts

import com.wasimaster.wmkeyboard.config.BuildConfig

/**
 * Whether the two search tools have a key to search with: the one the user
 * pasted into their settings, or the one baked into this build.
 *
 * Brave is the only provider behind both of them and it has no anonymous tier,
 * so with no key there is nothing for the panel to do but apologise. The tools
 * module resolves the same pair for the request itself
 * (`ToolApiKeys.hasSearchProvider`); this lives down here because the toolbar,
 * the toolbox and the settings screens all have to ask the question long before
 * anything reaches a network client.
 */
fun hasSearchKey(settings: KeyboardSettings): Boolean =
    settings.braveApiKey.isNotBlank() || BuildConfig.BRAVE_API_KEY.isNotBlank()

/**
 * Whether a tool can do its job right now — as opposed to [isSupportedTool],
 * which asks whether this build ships it at all.
 *
 * The difference is that this one can change while the app is running: paste a
 * key and the tool appears, clear it and it goes. Everything the keyboard draws
 * or dispatches filters on both, so a tool that cannot work is never on the
 * bar, in the toolbox, or behind a hardware shortcut.
 */
fun isUsableTool(tool: ToolbarTool, settings: KeyboardSettings): Boolean =
    toolBlocker(tool, settings) == null

/**
 * What stands between a tool and doing its job. The settings app draws the
 * reason on the tool's row and beside its switch, so the reason is typed
 * rather than implied: for a long time the search tools were the only
 * unusable ones, every screen spelled `!isUsableTool` as "needs an API key",
 * and the first tool gated on something else was offered a key field for a
 * problem a key could not fix. A new blocker added here fails to compile at
 * every screen until it has words of its own.
 */
enum class ToolBlocker {
    /** Web and image search: no search API key is configured. */
    NEEDS_SEARCH_KEY,

    /** The Custom layout tool: the user has not made a secondary layout yet. */
    NEEDS_SECONDARY_LAYOUT,
}

/** Why [tool] cannot run right now, or null when it can; see [ToolBlocker]. */
fun toolBlocker(tool: ToolbarTool, settings: KeyboardSettings): ToolBlocker? = when (tool) {
    ToolbarTool.WEB_SEARCH, ToolbarTool.IMAGE_SEARCH ->
        if (hasSearchKey(settings)) null else ToolBlocker.NEEDS_SEARCH_KEY
    // Nothing to show until the user has built a secondary layout; a button
    // that does nothing is worse than no button.
    ToolbarTool.CUSTOM_LAYOUT ->
        if (secondaryLayouts(settings.customLayouts).isNotEmpty()) null else ToolBlocker.NEEDS_SECONDARY_LAYOUT
    else -> null
}

/**
 * The tools the user has switched on that can actually run, for the callers
 * that take a whole set (the hardware shortcuts, the smart suggestions, the
 * tool picker) rather than filtering tool by tool.
 */
fun usableTools(settings: KeyboardSettings): Set<ToolbarTool> =
    settings.enabledTools.filterTo(mutableSetOf()) { isUsableTool(it, settings) }
