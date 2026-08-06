package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.core.settings.ToolbarTool

/**
 * Which key opens what, and which label goes under which button.
 *
 * The old physical-keyboard hint was one strip of bare letters floated over the
 * top bar: it named every binding and pointed at none of them. This file
 * replaces it with a plan that pairs each on-screen thing with the key that
 * fires it, so the hint can be drawn *under the button it belongs to*.
 *
 * The plan is built once from the visible state and read by two callers — the
 * renderer, for the labels, and the service, for the dispatch. One function, so
 * the badge under a key can never disagree with what the key does.
 *
 * Pure, like [HardwareShortcuts]: no Android types at all, so it runs in a plain
 * JVM test. The settings enums live in `:core:settings`, which depends on this
 * module, so the two mode questions arrive as booleans instead.
 */

// ------------------------------------------------------------------ keys ----

/**
 * One physical keypress in picker mode, after the layout has been decoded.
 *
 * [key] is already uppercased and comes from `pickerLetter`, which reads the
 * character printed on the keycap rather than the keycode. [shift] is read
 * separately off the event: `pickerLetter` decodes with no meta state (so a
 * binding can never depend on Shift or AltGr), which is exactly what makes
 * Shift usable as a tier of its own here.
 */
data class HintStroke(val key: Char, val shift: Boolean = false)

/** What a stroke does. */
sealed interface HintAction {
    data class OpenTool(val tool: ToolbarTool) : HintAction

    /** The toolbox launcher, which is a fixed button rather than one of the tools. */
    data object OpenToolbox : HintAction

    /** Index into the symbol row's characters. */
    data class InsertSymbol(val index: Int) : HintAction

    /** Index into the emoji row's emoji. */
    data class InsertEmoji(val index: Int) : HintAction

    data class PickSuggestion(val index: Int) : HintAction
}

/**
 * The on-screen rows a hint can be drawn under.
 *
 * [TOOLBAR] indexes the *buttons as drawn*, so its slot 0 is the fixed toolbox
 * launcher and slot 1 onward are the pinned tools. Numbering the tools alone
 * would put a 1 under the second button on the bar, which is exactly the kind of
 * off-by-one a badge is supposed to remove. The back chevron is skipped: it only
 * exists while a panel is open, so a digit on it would renumber the whole bar
 * every time one opened, and Escape already closes panels.
 */
enum class HintSurface { TOOLBAR, TOOLBOX, SYMBOL_ROW, EMOJI_ROW, SUGGESTION }

/** One drawable button: which row it is in, and where along that row. */
data class HintTarget(val surface: HintSurface, val index: Int)

/**
 * Modifier glyphs for the badges. Written as escapes rather than literals: the
 * badge is two or three characters wide, and a mangled encoding somewhere in
 * the build would be invisible until it reached a device.
 */
const val ShiftHintPrefix: String = "\u21E7"

const val CtrlHintPrefix: String = "\u2303"

const val AltHintPrefix: String = "\u2325"

/**
 * The second tier: everything with no bare key of its own answers to Shift plus
 * one of these.
 *
 * All 25 non-reserved letters are already spoken for by [DefaultToolLetters],
 * and there are far more tools than letters, so a second tier is not a luxury —
 * without it, "give every tool a key" is arithmetically impossible.
 *
 * Ordered the way the keys sit under the hands (top row, home row, bottom row,
 * then the digits) so a row of badges reads left to right across the keyboard.
 */
val ExtendedHintKeys: List<Char> =
    ("QWERTYUIOP" + "ASDFGHJKL" + "ZXCVBNM" + "1234567890").toList()

/**
 * Where the emoji row starts drawing from [ExtendedHintKeys], leaving the keys
 * before it to the symbol row.
 *
 * Fixed slices rather than one running counter: with a shared drain, adding a
 * character to a symbol set would silently renumber every emoji beside it, and
 * the whole point of these keys is that they stay where the user learned them.
 */
const val EmojiHintKeyOffset: Int = 20

/** How many symbol cells can be reached before the emoji row's slice starts. */
const val MaxSymbolHintKeys: Int = EmojiHintKeyOffset

/** How many emoji cells can be reached; the rest of the pool, after the symbols. */
const val MaxEmojiHintKeys: Int = 16

/** Toolbar positions get a digit each: 1-9, then 0 for the tenth. */
val ToolbarHintDigits: List<Char> = "1234567890".toList()

/**
 * The bar's buttons in drawn order, as far as the digits reach: the toolbox
 * launcher, then the pinned tools.
 *
 * Its own function because two callers need the same answer and they arrive by
 * different routes — [buildHintPlan] for the badges and for the leader's bare
 * digits, and the `Ctrl`+digit chord, which fires even in the mode where the
 * bare digits have been given to the suggestions.
 */
fun toolbarHintButtons(toolbarTools: List<ToolbarTool>): List<HintAction> =
    (listOf(HintAction.OpenToolbox) + toolbarTools.map(HintAction::OpenTool))
        .take(ToolbarHintDigits.size)

// ------------------------------------------------------------------ plan ----

/**
 * The labels to draw and the strokes to act on.
 *
 * [labels] is keyed by what is on screen; a target missing from it has no key
 * right now and simply draws no badge. [strokes] is keyed by what the user
 * presses, and holds more than [labels] does: every bound tool letter works
 * whether or not that tool happens to be visible.
 */
data class HintPlan(
    val labels: Map<HintTarget, String> = emptyMap(),
    val strokes: Map<HintStroke, HintAction> = emptyMap(),
) {
    fun label(surface: HintSurface, index: Int): String? = labels[HintTarget(surface, index)]

    fun action(key: Char, shift: Boolean): HintAction? = strokes[HintStroke(key, shift)]
}

/**
 * Works out every hint for the keyboard as it currently stands.
 *
 * The rules, in the order they claim keys:
 *
 * - **Toolbar** takes the digits, in the order the icons are drawn, starting
 *   with the toolbox launcher. Whether a bare digit fires them depends on
 *   [leaderDigitsPickSuggestions] — one of the two has to own the digits, and
 *   the suggestion setting is the user's own answer to which.
 * - **Tool letters** are the user's own map and never move. They fire from the
 *   picker whether or not the tool is on screen.
 * - **The toolbox**, when open, labels each tool with its letter, and hands the
 *   ones with no letter a key from [ExtendedHintKeys]. That is the "generate one
 *   for the tools that have none" rule.
 * - **The symbol and emoji rows** take fixed slices of [ExtendedHintKeys] — but
 *   only while the toolbox is closed. An open toolbox is the thing the user is
 *   looking at, and letting it share the tier would move the rows' keys around
 *   under them every time a panel opened.
 *
 * @param toolbarTools the pinned tools, in the order they are drawn (already
 *   reversed for an RTL bar, so the digits follow the eye). The toolbox launcher
 *   is prepended here, not by the caller.
 * @param toolboxTools the tools the toolbox is showing, or empty when it is shut.
 * @param toolLetters the resolved letter map (see `resolvedToolLetters`).
 * @param digitChord whether `Ctrl`+digit is on, which decides only how the
 *   toolbar badge is *spelled* — the chord itself is dispatched outside the picker.
 * @param leaderDigitsPickSuggestions true in the leader-digit suggestion mode.
 * @param suggestionAltDigits true in the Alt-digit suggestion mode.
 */
@Suppress("LongParameterList")
fun buildHintPlan(
    toolbarTools: List<ToolbarTool> = emptyList(),
    toolboxTools: List<ToolbarTool> = emptyList(),
    toolLetters: Map<Char, ToolbarTool> = emptyMap(),
    symbolCells: Int = 0,
    emojiCells: Int = 0,
    suggestions: Int = 0,
    digitChord: Boolean = false,
    leaderDigitsPickSuggestions: Boolean = false,
    suggestionAltDigits: Boolean = false,
): HintPlan {
    val labels = mutableMapOf<HintTarget, String>()
    val strokes = mutableMapOf<HintStroke, HintAction>()

    // -- toolbar: the digits, launcher first ----------------------------
    toolbarHintButtons(toolbarTools).forEachIndexed { index, action ->
        val digit = ToolbarHintDigits[index]
        if (!leaderDigitsPickSuggestions) strokes[HintStroke(digit)] = action
        // Spelled as the chord when the chord is on: that is the shortcut the
        // user can press at any time, and the bare digit only exists for the
        // three seconds after the leader. A button with no route at all (chord
        // off *and* the digits given to suggestions) gets no badge.
        val label = when {
            digitChord -> CtrlHintPrefix + digit
            !leaderDigitsPickSuggestions -> digit.toString()
            else -> null
        }
        if (label != null) labels[HintTarget(HintSurface.TOOLBAR, index)] = label
    }

    // -- tool letters: bound whether or not the tool is on screen --------
    for ((letter, tool) in toolLetters) {
        if (letter in ReservedLetters) continue
        strokes[HintStroke(letter)] = HintAction.OpenTool(tool)
    }

    // -- the second tier -------------------------------------------------
    val letterOf = toolLetters.entries.associate { (letter, tool) -> tool to letter }
    if (toolboxTools.isNotEmpty()) {
        var next = 0
        toolboxTools.forEachIndexed { index, tool ->
            val bound = letterOf[tool]
            val label = if (bound != null) {
                bound.toString()
            } else {
                val key = ExtendedHintKeys.getOrNull(next++) ?: return@forEachIndexed
                strokes[HintStroke(key, shift = true)] = HintAction.OpenTool(tool)
                ShiftHintPrefix + key
            }
            labels[HintTarget(HintSurface.TOOLBOX, index)] = label
        }
    } else {
        for (index in 0 until minOf(symbolCells, MaxSymbolHintKeys)) {
            val key = ExtendedHintKeys[index]
            strokes[HintStroke(key, shift = true)] = HintAction.InsertSymbol(index)
            labels[HintTarget(HintSurface.SYMBOL_ROW, index)] = ShiftHintPrefix + key
        }
        for (index in 0 until minOf(emojiCells, MaxEmojiHintKeys)) {
            val key = ExtendedHintKeys[EmojiHintKeyOffset + index]
            strokes[HintStroke(key, shift = true)] = HintAction.InsertEmoji(index)
            labels[HintTarget(HintSurface.EMOJI_ROW, index)] = ShiftHintPrefix + key
        }
    }

    // -- suggestions -----------------------------------------------------
    // Alt+digit needs no leader, so its badge is a standing label rather than
    // something the picker reveals; the leader-digit mode is the opposite, and
    // registers a stroke instead.
    if (suggestionAltDigits || leaderDigitsPickSuggestions) {
        for (index in 0 until minOf(suggestions, ToolbarHintDigits.size)) {
            val digit = ToolbarHintDigits[index]
            if (leaderDigitsPickSuggestions) {
                strokes[HintStroke(digit)] = HintAction.PickSuggestion(index)
            }
            labels[HintTarget(HintSurface.SUGGESTION, index)] =
                if (suggestionAltDigits) AltHintPrefix + digit else digit.toString()
        }
    }

    return HintPlan(labels = labels, strokes = strokes)
}
