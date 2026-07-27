package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.tools.BuiltInSymbolSets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The rows stacked above the keys, in top-to-bottom order. [TOPBAR] is the
 * suggestion/toolbar strip and is always present; the emoji and symbol rows
 * only render when their settings turn them on, but keep their slot in the
 * order either way.
 */
enum class BarRow { TOPBAR, EMOJI, SYMBOL }

/** Ensures every row appears exactly once, preserving the stored order. */
fun sanitizeBarOrder(rows: List<BarRow>): List<BarRow> =
    rows.distinct() + BarRow.entries.filter { it !in rows }

/**
 * Kind of input field a keyboard mode can bind to, derived from
 * EditorInfo.inputType (named ModeField to stay distinct from the IME's
 * layout-level FieldKind). A field can match several bindings at once — a
 * password box in a browser matches the app binding and [PASSWORD].
 *
 * [TEXT] is the ordinary free-text box: everything that is not a password,
 * email, URL, number or phone field. It is what a chat composer or a
 * document body is, and it is deliberately the *narrow* option — binding a
 * mode to an app plus [TEXT] keeps that mode off the app's search boxes,
 * address fields and login forms.
 */
enum class ModeField { PASSWORD, EMAIL, URL, NUMBER, PHONE, TEXT }

/**
 * One keyboard mode: a named bundle of overrides applied while the mode is
 * active. Null fields inherit the global setting. A mode activates
 * automatically for its bound apps ([apps], package names) and/or field
 * kinds ([fieldKinds]), or manually from the Modes tool — a manual pick
 * wins until the user switches to another app.
 *
 * Binding both apps and field kinds means *both* have to match (see
 * [matchesField]): the "Chat" mode is bound to the messaging apps **and**
 * [ModeField.TEXT], so it switches on for the message composer but not for
 * the app's contact search.
 */
@Serializable
data class KeyboardMode(
    val id: String,
    val name: String,
    /**
     * Id of the icon shown next to the mode's name, from `ModeIcons.catalog`.
     * An id rather than a drawable so stored modes survive the catalog being
     * reshuffled; null and unknown ids fall back to the generic mode icon.
     */
    val icon: String? = null,
    /** Emoji row presentation while active; null inherits the global choice. */
    val emojiBarMode: EmojiBarMode? = null,
    /**
     * Pinned toolbar tools while active; null inherits the user's toolbar.
     * Replaces the toolbar unless [toolbarToolsAppend] is on.
     */
    val toolbarTools: List<ToolbarTool>? = null,
    /**
     * When true, [toolbarTools] are added after the user's own pinned tools
     * instead of replacing them — the mode contributes its specialty tools
     * without taking away the ones the user pinned by hand.
     */
    val toolbarToolsAppend: Boolean = false,
    /**
     * Tools to float to the front of the toolbox panel while active; null
     * inherits the user's order. A partial list is enough — anything not
     * named keeps its global rank behind these.
     */
    val toolboxOrder: List<ToolbarTool>? = null,
    /** Symbol row on/off while active; null inherits. */
    val symbolRowEnabled: Boolean? = null,
    /**
     * Symbol sets offered by the row's picker while active (first one is
     * the default); null inherits the globally enabled sets.
     */
    val symbolSetIds: List<String>? = null,
    /** Package names this mode activates for automatically. */
    val apps: List<String> = emptyList(),
    /** Input-field kinds this mode activates for automatically. */
    val fieldKinds: List<ModeField> = emptyList(),
) {
    /**
     * Whether this mode prescribes a tool arrangement of its own. When it
     * does, a drag made while it is active has to be written back into the
     * mode — stored globally it would be overwritten by the mode's list on
     * the very next composition and look like it never happened.
     */
    val ownsToolOrder: Boolean get() = toolbarTools != null || toolboxOrder != null
}

private val modeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object KeyboardModeCodec {
    fun encodeList(modes: List<KeyboardMode>): String = modeJson.encodeToString(modes)

    fun decodeList(json: String): List<KeyboardMode> =
        runCatching { modeJson.decodeFromString<List<KeyboardMode>>(json) }
            .getOrDefault(emptyList())
            .map(::migrateMode)

    /** App list the browser mode used to ship with (now field-bound only). */
    private val legacyBrowserApps = listOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android",
    )

    /** App list the email mode used to ship with (now field-bound only). */
    private val legacyEmailApps = listOf(
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.yahoo.mobile.client.android.mail",
        "ch.protonmail.android",
        "com.fsck.k9",
    )

    /**
     * Stored copies of a seeded mode keep their old app bindings forever;
     * drop them when they are exactly the old default so the mode becomes
     * field-bound only like a fresh install. A user-edited app list is left
     * alone.
     *
     * The email mode loses its app list because bindings are now an AND:
     * keeping the apps would have narrowed it to email boxes *inside* those
     * five apps, when an email box anywhere is what it is for. The mail
     * apps live on in the Writing mode instead, which handles their bodies.
     */
    private fun migrateMode(mode: KeyboardMode): KeyboardMode = when {
        mode.id == "mode_browser" && mode.apps == legacyBrowserApps ->
            mode.copy(apps = emptyList()).withSeededIcon()
        mode.id == "mode_email" && mode.apps == legacyEmailApps ->
            mode.copy(apps = emptyList()).withSeededIcon()
        else -> mode.withSeededIcon()
    }

    /**
     * Modes stored before icons existed have none, so a seeded mode would
     * show the generic fallback forever. Fill in the icon it ships with —
     * only when there is none, so a user's pick is never overwritten.
     */
    private fun KeyboardMode.withSeededIcon(): KeyboardMode =
        if (icon != null) {
            this
        } else {
            DefaultKeyboardModes.firstOrNull { it.id == id }?.icon
                ?.let { copy(icon = it) } ?: this
        }
}

/** Messaging apps the Chat mode ships bound to. */
private val ChatApps = listOf(
    "com.facebook.orca",
    "com.instagram.android",
    "org.telegram.messenger",
    "com.whatsapp",
    "org.thoughtcrime.securesms",
    "com.discord",
)

/**
 * Writing and note-taking apps the Writing mode ships bound to. The mail
 * apps belong here too: the mode only fires on plain text fields, and in a
 * mail app that is the body — the recipient boxes are email fields and go
 * to the Email mode.
 */
private val WritingApps = listOf(
    // Notes and documents
    "com.google.android.keep",
    "com.google.android.apps.docs.editors.docs",
    "com.samsung.android.app.notes",
    "com.microsoft.office.word",
    "com.microsoft.office.onenote",
    "md.obsidian",
    "notion.id",
    "com.evernote",
    "net.cozic.joplin",
    // Mail (bodies only — see above)
    "com.google.android.gm",
    "com.microsoft.office.outlook",
    "com.yahoo.mobile.client.android.mail",
    "ch.protonmail.android",
    "com.fsck.k9",
)

/**
 * Starter modes seeded on first run (stored copies — the user can edit or
 * delete them freely). App lists cover the mainstream apps per category;
 * binding extra apps is one tap in the mode editor.
 */
val DefaultKeyboardModes: List<KeyboardMode> = listOf(
    KeyboardMode(
        id = "mode_password",
        icon = "lock",
        name = "Passwords",
        emojiBarMode = EmojiBarMode.OFF,
        // The one mode that deliberately *replaces* the toolbar: nothing
        // that opens a network panel belongs next to a password box.
        toolbarTools = listOf(
            ToolbarTool.PASSWORD_GEN, ToolbarTool.CLIPBOARD, ToolbarTool.SETTINGS,
        ),
        toolboxOrder = listOf(
            ToolbarTool.PASSWORD_GEN, ToolbarTool.CLIPBOARD, ToolbarTool.SNIPPETS,
            ToolbarTool.NUMPAD, ToolbarTool.SYMBOLS, ToolbarTool.SETTINGS,
        ),
        symbolRowEnabled = false,
        fieldKinds = listOf(ModeField.PASSWORD),
    ),
    // Field-bound only: an email box is an email box in any app. The mail
    // apps are bound by the Writing mode, which covers their bodies.
    KeyboardMode(
        id = "mode_email",
        icon = "mail",
        name = "Email",
        toolboxOrder = listOf(
            ToolbarTool.CLIPBOARD, ToolbarTool.SNIPPETS, ToolbarTool.TEXT_EDIT,
            ToolbarTool.VOICE, ToolbarTool.SYMBOLS,
        ),
        symbolRowEnabled = true,
        symbolSetIds = listOf(BuiltInSymbolSets.EMAIL_ID, BuiltInSymbolSets.PUNCTUATION_ID),
        fieldKinds = listOf(ModeField.EMAIL),
    ),
    // Field-bound only: most typing in a browser is ordinary text (search
    // boxes, forms, web apps) — the web symbol set should kick in just for
    // the address bar, so no app bindings here.
    KeyboardMode(
        id = "mode_browser",
        icon = "web",
        name = "Browser",
        toolboxOrder = listOf(
            ToolbarTool.CLIPBOARD, ToolbarTool.WEB_SEARCH, ToolbarTool.SNIPPETS,
            ToolbarTool.QR_SCAN, ToolbarTool.VOICE, ToolbarTool.INCOGNITO,
        ),
        symbolRowEnabled = true,
        symbolSetIds = listOf(BuiltInSymbolSets.WEB_ID),
        fieldKinds = listOf(ModeField.URL),
    ),
    KeyboardMode(
        id = "mode_chat",
        icon = "chat",
        name = "Chat",
        // The emoji row earns its space in a chat: it is the whole point.
        emojiBarMode = EmojiBarMode.ALWAYS,
        // What you reach for mid-sentence. Grammar is a once-per-message
        // check, so it leads the toolbox instead of taking toolbar space.
        toolbarTools = listOf(
            ToolbarTool.EMOJI, ToolbarTool.GIF, ToolbarTool.STICKER, ToolbarTool.VOICE,
        ),
        toolbarToolsAppend = true,
        toolboxOrder = listOf(
            ToolbarTool.GRAMMAR, ToolbarTool.TRANSLATE, ToolbarTool.CAMERA,
            ToolbarTool.IMAGE_SEARCH, ToolbarTool.CLIPBOARD, ToolbarTool.SNIPPETS,
            // The pinned four, ranked in case the user unpins one — the
            // toolbox skips whatever is on the toolbar, so these sit idle
            // until then.
            ToolbarTool.EMOJI, ToolbarTool.GIF, ToolbarTool.STICKER, ToolbarTool.VOICE,
        ),
        apps = ChatApps,
        fieldKinds = listOf(ModeField.TEXT),
    ),
    KeyboardMode(
        id = "mode_writing",
        icon = "write",
        name = "Writing",
        // A self-contained toolbar — it replaces the user's pins rather than
        // appending, so the Settings shortcut the global pins carry never
        // rides along into a writing session. Writing helpers lead (compose
        // and edit with AI, the caret tools, dictation), then the everyday
        // paste-and-insert pins. AI drops out on its own in a build without
        // it (see isSupportedTool), leaving the rest of the bar untouched.
        toolbarTools = listOf(
            ToolbarTool.AI, ToolbarTool.TEXT_EDIT, ToolbarTool.VOICE,
            ToolbarTool.CLIPBOARD, ToolbarTool.EMOJI,
        ),
        toolbarToolsAppend = false,
        toolboxOrder = listOf(
            // Polish the draft, then pull material into it, then the
            // reference and history tools.
            ToolbarTool.GRAMMAR, ToolbarTool.DICTIONARY,
            ToolbarTool.OCR, ToolbarTool.DOC_SCAN, ToolbarTool.CALENDAR,
            ToolbarTool.CLIPBOARD, ToolbarTool.SNIPPETS, ToolbarTool.TRANSLATE,
            ToolbarTool.UNDO, ToolbarTool.REDO,
            // Ranked for whenever the user unpins one of the pinned tools above.
            ToolbarTool.AI, ToolbarTool.TEXT_EDIT, ToolbarTool.VOICE, ToolbarTool.EMOJI,
        ),
        symbolRowEnabled = true,
        symbolSetIds = listOf(BuiltInSymbolSets.PUNCTUATION_ID),
        apps = WritingApps,
        fieldKinds = listOf(ModeField.TEXT),
    ),
    KeyboardMode(
        id = "mode_coding",
        icon = "code",
        name = "Coding",
        toolboxOrder = listOf(
            ToolbarTool.SNIPPETS, ToolbarTool.CLIPBOARD, ToolbarTool.TEXT_EDIT,
            ToolbarTool.SYMBOLS, ToolbarTool.CALCULATOR, ToolbarTool.NUMPAD,
            ToolbarTool.UNDO, ToolbarTool.REDO,
            ToolbarTool.CURSOR_LEFT, ToolbarTool.CURSOR_RIGHT,
            ToolbarTool.CURSOR_HOME, ToolbarTool.CURSOR_END,
        ),
        symbolRowEnabled = true,
        symbolSetIds = listOf(BuiltInSymbolSets.CODING_ID, BuiltInSymbolSets.MATH_ID),
        apps = listOf(
            "com.termux",
            "com.foxdebug.acode",
            "com.rk.xededitor",
            "io.spck",
        ),
    ),
)

/**
 * How many rounds of default modes exist. Bump this and add the new ids to
 * a `ModesAddedInSeedVersionN` list whenever [DefaultKeyboardModes] grows,
 * so upgrades pick the new modes up — see
 * `SettingsRepository.seedNewDefaultModes`.
 */
const val CurrentModeSeedVersion: Int = 2

/** Default modes added in seed version 2 (chat and writing). */
val ModesAddedInSeedVersion2: Set<String> = setOf("mode_chat", "mode_writing")

/**
 * Whether the focused field satisfies this mode's automatic bindings.
 *
 * Apps and field kinds are an AND when both are set: binding the Chat mode
 * to WhatsApp *and* [ModeField.TEXT] keeps it on the message composer and
 * off the contact search. Setting only one of the two matches on that one
 * alone; setting neither makes the mode manual-only.
 */
private fun KeyboardMode.matchesField(packageName: String?, fields: Set<ModeField>): Boolean {
    val appMatch = packageName != null && packageName in apps
    val fieldMatch = fieldKinds.any { it in fields }
    return when {
        apps.isEmpty() && fieldKinds.isEmpty() -> false
        apps.isEmpty() -> fieldMatch
        fieldKinds.isEmpty() -> appMatch
        else -> appMatch && fieldMatch
    }
}

/**
 * Picks the active mode: a manual pick from the Modes tool wins, then the
 * most specific automatic match. A mode that names field kinds beats one
 * that only names apps, so a password box inside a code editor still gets
 * the password mode; ties break on the order of [modes].
 */
fun resolveKeyboardMode(
    modes: List<KeyboardMode>,
    packageName: String?,
    fieldKinds: Set<ModeField>,
    manualModeId: String?,
): KeyboardMode? {
    manualModeId?.let { id -> modes.firstOrNull { it.id == id }?.let { return it } }
    val matching = modes.filter { it.matchesField(packageName, fieldKinds) }
    return matching.firstOrNull { it.fieldKinds.isNotEmpty() } ?: matching.firstOrNull()
}

/**
 * The mode's pinned toolbar over [base] — replacing it, appending to it, or
 * inheriting it untouched. Appending keeps the user's own pins first and
 * drops duplicates so a tool never shows up twice.
 */
private fun KeyboardMode.pinnedOver(base: List<ToolbarTool>): List<ToolbarTool> {
    val tools = toolbarTools?.filter(::isSupportedTool) ?: return base
    return if (toolbarToolsAppend) base + (tools - base.toSet()) else tools
}

/** The settings as they apply while [mode] is active (null = no mode). */
fun KeyboardSettings.applyMode(mode: KeyboardMode?): KeyboardSettings {
    if (mode == null) return this
    val pinned = mode.pinnedOver(toolbarTools)
    return copy(
        emojiBarMode = mode.emojiBarMode ?: emojiBarMode,
        toolbarTools = pinned,
        // A tool the mode pins has to be visible, even when the user left it
        // switched off globally — otherwise the mode silently pins nothing.
        enabledTools = enabledTools + (pinned - enabledTools.toSet()),
        toolboxOrder = mode.toolboxOrder
            ?.let { front -> front + (toolboxOrder - front.toSet()) }
            ?: toolboxOrder,
        symbolRowEnabled = mode.symbolRowEnabled ?: symbolRowEnabled,
        symbolRowSetIds = mode.symbolSetIds ?: symbolRowSetIds,
        symbolRowActiveSetId = mode.symbolSetIds?.firstOrNull() ?: symbolRowActiveSetId,
    )
}
