package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.tools.BuiltInSymbolSets
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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
 */
enum class ModeField { PASSWORD, EMAIL, URL, NUMBER, PHONE }

/**
 * One keyboard mode: a named bundle of overrides applied while the mode is
 * active. Null fields inherit the global setting. A mode activates
 * automatically for its bound apps ([apps], package names) or field kinds
 * ([fieldKinds]), or manually from the Modes tool — a manual pick wins
 * until the user switches to another app.
 */
@Serializable
data class KeyboardMode(
    val id: String,
    val name: String,
    /** Emoji row presentation while active; null inherits the global choice. */
    val emojiBarMode: EmojiBarMode? = null,
    /** Pinned toolbar tools while active; null inherits the user's toolbar. */
    val toolbarTools: List<ToolbarTool>? = null,
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
)

private val modeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object KeyboardModeCodec {
    fun encodeList(modes: List<KeyboardMode>): String = modeJson.encodeToString(modes)

    fun decodeList(json: String): List<KeyboardMode> =
        runCatching { modeJson.decodeFromString<List<KeyboardMode>>(json) }
            .getOrDefault(emptyList())
}

/**
 * Starter modes seeded on first run (stored copies — the user can edit or
 * delete them freely). App lists cover the mainstream apps per category;
 * binding extra apps is one tap in the mode editor.
 */
val DefaultKeyboardModes: List<KeyboardMode> = listOf(
    KeyboardMode(
        id = "mode_password",
        name = "Passwords",
        emojiBarMode = EmojiBarMode.OFF,
        toolbarTools = listOf(
            ToolbarTool.PASSWORD_GEN, ToolbarTool.CLIPBOARD, ToolbarTool.SETTINGS,
        ),
        symbolRowEnabled = false,
        fieldKinds = listOf(ModeField.PASSWORD),
    ),
    KeyboardMode(
        id = "mode_email",
        name = "Email",
        symbolRowEnabled = true,
        symbolSetIds = listOf(BuiltInSymbolSets.EMAIL_ID, BuiltInSymbolSets.PUNCTUATION_ID),
        apps = listOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail",
            "ch.protonmail.android",
            "com.fsck.k9",
        ),
        fieldKinds = listOf(ModeField.EMAIL),
    ),
    KeyboardMode(
        id = "mode_browser",
        name = "Browser",
        symbolRowEnabled = true,
        symbolSetIds = listOf(BuiltInSymbolSets.WEB_ID),
        apps = listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.sec.android.app.sbrowser",
            "com.duckduckgo.mobile.android",
        ),
        fieldKinds = listOf(ModeField.URL),
    ),
    KeyboardMode(
        id = "mode_coding",
        name = "Coding",
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
 * Picks the active mode: a manual pick from the Modes tool wins, then the
 * focused field's kind, then the app the field belongs to. Field kind beats
 * app so a password box inside a browser still gets the password mode.
 */
fun resolveKeyboardMode(
    modes: List<KeyboardMode>,
    packageName: String?,
    fieldKinds: Set<ModeField>,
    manualModeId: String?,
): KeyboardMode? {
    manualModeId?.let { id -> modes.firstOrNull { it.id == id }?.let { return it } }
    modes.firstOrNull { mode -> mode.fieldKinds.any { it in fieldKinds } }?.let { return it }
    return modes.firstOrNull { packageName != null && packageName in it.apps }
}

/** The settings as they apply while [mode] is active (null = no mode). */
fun KeyboardSettings.applyMode(mode: KeyboardMode?): KeyboardSettings {
    if (mode == null) return this
    return copy(
        emojiBarMode = mode.emojiBarMode ?: emojiBarMode,
        toolbarTools = mode.toolbarTools ?: toolbarTools,
        symbolRowEnabled = mode.symbolRowEnabled ?: symbolRowEnabled,
        symbolRowSetIds = mode.symbolSetIds ?: symbolRowSetIds,
        symbolRowActiveSetId = mode.symbolSetIds?.firstOrNull() ?: symbolRowActiveSetId,
    )
}
