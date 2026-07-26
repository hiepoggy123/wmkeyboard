package com.wasimaster.wmkeyboard.core.icons

import com.wasimaster.wmkeyboard.core.settings.ToolbarTool

/** Which surface a slot belongs to, and the order the settings screen groups them in. */
enum class IconSlotGroup(val title: String) {
    TOOL("Tools"),
    KEY("Keys"),
    CHROME("Toolbar"),
    EMOJI_TAB("Emoji tabs"),
}

/**
 * One icon the user can replace.
 *
 * [id] is the stable name that travels in an icon pack and in the saved
 * overrides, so it must never change once shipped — renaming one silently
 * drops every user's customisation of it. Ids are lowercase `group.name`, and
 * deliberately contain no `,` or `=`: the overrides are persisted as a CSV of
 * `slot=source` pairs, the same shape as the per-tool colour overrides.
 *
 * [tool] is set for the [IconSlotGroup.TOOL] slots. The settings UI prefers
 * `toolTitle(tool)` over [label] for those, so a tool's icon row and its own
 * settings screen agree on what it is called.
 */
data class IconSlot(
    val id: String,
    val group: IconSlotGroup,
    val label: String,
    val tool: ToolbarTool? = null,
)

/**
 * Every icon a user can swap, as stable ids.
 *
 * This is the `core` half of the icon system: it names the slots but knows
 * nothing about vectors, so it stays Compose-free and JVM-testable. The
 * built-in glyph for each id lives in `ime/ui/IconDefaults.kt`, matching how
 * `Key.icon` stores a name that `KeyIcons` resolves at render time.
 *
 * The tool slots are *derived* from [ToolbarTool] rather than listed, so a new
 * tool becomes customisable the moment it is added to the enum — one less
 * exhaustive `when` to forget. The remaining groups are enumerated by hand
 * because they have no enum to hang off.
 *
 * Deliberately absent: the named layout glyphs in `KeyIcons`. Those are picked
 * per key by whoever authored the layout, so a pack that redefined them would
 * be changing that author's design rather than the app's chrome.
 */
object IconSlots {

    /** `tool.<enum name lowercased>` — the id for [tool]'s toolbar/toolbox icon. */
    fun forTool(tool: ToolbarTool): String = "tool.${tool.name.lowercase()}"

    // ---- key action slots ----

    const val KEY_SHIFT = "key.shift"
    const val KEY_SHIFT_ON = "key.shift_on"
    const val KEY_SHIFT_LOCK = "key.shift_lock"
    const val KEY_BACKSPACE = "key.backspace"
    const val KEY_GLOBE = "key.globe"
    const val KEY_EMOJI = "key.emoji"

    /**
     * One per enter action the field can ask for. `EnterAction.CUSTOM` has no
     * slot: the app supplied its own wording and the key draws that as text,
     * so there is no icon to replace.
     */
    const val KEY_ENTER = "key.enter"
    const val KEY_ENTER_SEARCH = "key.enter_search"
    const val KEY_ENTER_SEND = "key.enter_send"
    const val KEY_ENTER_GO = "key.enter_go"
    const val KEY_ENTER_NEXT = "key.enter_next"
    const val KEY_ENTER_PREVIOUS = "key.enter_previous"
    const val KEY_ENTER_DONE = "key.enter_done"

    // ---- toolbar chrome slots ----

    const val CHROME_TOOLBOX = "chrome.toolbox"
    const val CHROME_PANEL_BACK = "chrome.panel_back"
    const val CHROME_SUGGESTIONS_EXPAND = "chrome.suggestions_expand"
    const val CHROME_EMOJI_SHORTCUT = "chrome.emoji_shortcut"
    const val CHROME_SEARCH_CLOSE = "chrome.search_close"
    const val CHROME_INCOGNITO = "chrome.incognito"

    // ---- emoji tab slots ----

    const val EMOJI_TAB_SEARCH = "emoji_tab.search"
    const val EMOJI_TAB_RECENT = "emoji_tab.recent"
    const val EMOJI_TAB_MOST_USED = "emoji_tab.most_used"

    /**
     * `emoji_tab.<category>` for a catalog category name. Unknown categories
     * get an id too — the resolver falls back to the smiley for them, exactly
     * as the built-in map already did.
     */
    fun forEmojiCategory(category: String): String = "emoji_tab.$category"

    /** The catalog's Unicode categories, in the order the tab strip shows them. */
    val EMOJI_CATEGORIES: List<String> = listOf(
        "smileys", "people", "animals", "nature", "food",
        "travel", "activities", "objects", "symbols", "flags",
    )

    private val keySlots: List<IconSlot> = listOf(
        IconSlot(KEY_SHIFT, IconSlotGroup.KEY, "Shift"),
        IconSlot(KEY_SHIFT_ON, IconSlotGroup.KEY, "Shift (on)"),
        IconSlot(KEY_SHIFT_LOCK, IconSlotGroup.KEY, "Caps lock"),
        IconSlot(KEY_BACKSPACE, IconSlotGroup.KEY, "Backspace"),
        IconSlot(KEY_ENTER, IconSlotGroup.KEY, "Enter"),
        IconSlot(KEY_ENTER_SEARCH, IconSlotGroup.KEY, "Enter — search"),
        IconSlot(KEY_ENTER_SEND, IconSlotGroup.KEY, "Enter — send"),
        IconSlot(KEY_ENTER_GO, IconSlotGroup.KEY, "Enter — go"),
        IconSlot(KEY_ENTER_NEXT, IconSlotGroup.KEY, "Enter — next"),
        IconSlot(KEY_ENTER_PREVIOUS, IconSlotGroup.KEY, "Enter — previous"),
        IconSlot(KEY_ENTER_DONE, IconSlotGroup.KEY, "Enter — done"),
        IconSlot(KEY_GLOBE, IconSlotGroup.KEY, "Switch language"),
        IconSlot(KEY_EMOJI, IconSlotGroup.KEY, "Emoji key"),
    )

    private val chromeSlots: List<IconSlot> = listOf(
        IconSlot(CHROME_TOOLBOX, IconSlotGroup.CHROME, "Toolbox"),
        IconSlot(CHROME_PANEL_BACK, IconSlotGroup.CHROME, "Back to keys"),
        IconSlot(CHROME_SUGGESTIONS_EXPAND, IconSlotGroup.CHROME, "Show tools"),
        IconSlot(CHROME_EMOJI_SHORTCUT, IconSlotGroup.CHROME, "Emoji shortcut"),
        IconSlot(CHROME_SEARCH_CLOSE, IconSlotGroup.CHROME, "Close search"),
        IconSlot(CHROME_INCOGNITO, IconSlotGroup.CHROME, "Incognito badge"),
    )

    private val emojiTabSlots: List<IconSlot> = buildList {
        add(IconSlot(EMOJI_TAB_SEARCH, IconSlotGroup.EMOJI_TAB, "Search"))
        add(IconSlot(EMOJI_TAB_RECENT, IconSlotGroup.EMOJI_TAB, "Recent"))
        add(IconSlot(EMOJI_TAB_MOST_USED, IconSlotGroup.EMOJI_TAB, "Most used"))
        for (category in EMOJI_CATEGORIES) {
            add(
                IconSlot(
                    forEmojiCategory(category),
                    IconSlotGroup.EMOJI_TAB,
                    category.replaceFirstChar { it.uppercase() },
                )
            )
        }
    }

    /** Every slot, grouped in [IconSlotGroup] order. */
    val all: List<IconSlot> = buildList {
        for (tool in ToolbarTool.entries) {
            // The label is a readable fallback; the settings UI substitutes
            // toolTitle(tool), which is the wording the rest of the app uses.
            add(
                IconSlot(
                    forTool(tool),
                    IconSlotGroup.TOOL,
                    tool.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                    tool,
                )
            )
        }
        addAll(keySlots)
        addAll(chromeSlots)
        addAll(emojiTabSlots)
    }

    private val byId: Map<String, IconSlot> = all.associateBy { it.id }

    /** The slot named [id], or null — an unknown id is dropped, never fatal. */
    fun byId(id: String?): IconSlot? = id?.let { byId[it] }

    /** Slots in [group], in declaration order. */
    fun inGroup(group: IconSlotGroup): List<IconSlot> = all.filter { it.group == group }

    /**
     * Whether [id] is safe to use as a file name inside a pack.
     *
     * Slot ids are lowercase letters, digits, `.` and `_` by construction, and
     * always start with a letter. This is the belt-and-braces check on an id
     * arriving from a downloaded pack: it has to rule out `..` and anything
     * beginning with a dot as well as the obvious path separators, because the
     * id becomes a file name.
     */
    fun isWellFormed(id: String): Boolean =
        id.isNotEmpty() &&
            id.length <= 64 &&
            id.first() in 'a'..'z' &&
            ".." !in id &&
            id.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }
}
