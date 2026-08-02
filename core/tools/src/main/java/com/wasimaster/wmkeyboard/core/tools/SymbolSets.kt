package com.wasimaster.wmkeyboard.core.tools

import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.tools.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One set of characters for the symbol row: anything committable — single
 * characters ("§", "→") or whole snippets ("@gmail.com", "https://"). The
 * row shows one set at a time; its picker chip switches between the sets
 * the user has enabled (or the active keyboard mode prescribes).
 *
 * [name] stays a plain string because the user names their own sets and can
 * rename a shipped one. A screen draws [BuiltInSymbolSets.nameRes] where there
 * is one and falls back to [name].
 */
@Serializable
data class SymbolSet(
    val id: String,
    val name: String,
    val chars: List<String>,
)

private val symbolSetJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object SymbolSetCodec {
    fun encodeList(sets: List<SymbolSet>): String = symbolSetJson.encodeToString(sets)

    fun decodeList(json: String): List<SymbolSet> =
        runCatching { symbolSetJson.decodeFromString<List<SymbolSet>>(json) }
            .getOrDefault(emptyList())
}

/**
 * The sets that ship with the keyboard. Their ids stay stable so modes can
 * reference them; editing one stores a custom set under the *same* id that
 * shadows the shipped version (see [resolveSymbolSets]), so a mode pinned to
 * "Coding" keeps working and deleting the edit restores the original.
 */
object BuiltInSymbolSets {

    const val EMAIL_ID = "builtin_email"
    const val WEB_ID = "builtin_web"
    const val CODING_ID = "builtin_coding"
    const val MATH_ID = "builtin_math"
    const val PUNCTUATION_ID = "builtin_punctuation"

    val sets: List<SymbolSet> = listOf(
        SymbolSet(
            EMAIL_ID, "Email",
            listOf(
                "@", "@gmail.com", "@outlook.com", "@yahoo.com", "@icloud.com",
                "@hotmail.com", "@proton.me", ".com", "_", "-", ".",
            ),
        ),
        SymbolSet(
            WEB_ID, "Web",
            listOf(
                "https://", "www.", ".com", ".org", ".net", ".io", "/", ":",
                "?", "&", "=", "#", "-", "_", "~",
            ),
        ),
        SymbolSet(
            CODING_ID, "Coding",
            listOf(
                "{", "}", "(", ")", "[", "]", "<", ">", ";", ":", "=", "!",
                "&", "|", "*", "/", "\\", "\"", "'", "`", "->", "=>", "==",
                "!=", "#", "$", "%", "^", "_", "+", "-", "\t",
            ),
        ),
        SymbolSet(
            MATH_ID, "Math",
            listOf(
                "±", "×", "÷", "≠", "≈", "≤", "≥", "√", "π", "∞", "°", "²",
                "³", "½", "¼", "¾", "%", "‰", "∑", "∫", "Δ", "μ",
            ),
        ),
        SymbolSet(
            PUNCTUATION_ID, "Punctuation",
            listOf(
                "—", "–", "…", "•", "·", "‘", "’", "“", "”", "«", "»", "¡",
                "¿", "§", "¶", "†", "©", "®", "™", "№",
            ),
        ),
    )

    fun byId(id: String): SymbolSet? = sets.firstOrNull { it.id == id }

    /**
     * The translated name of a shipped set, or null when the screen has to fall
     * back to [SymbolSet.name].
     *
     * Only a set that still carries its shipped name gets one: a set the user
     * made, and a shipped set the user renamed, both keep the name the user
     * typed. The English name stays in [sets] so a stored copy round-trips and
     * so the editor has something to show before it resolves resources.
     */
    @StringRes
    fun nameRes(set: SymbolSet): Int? = when {
        byId(set.id)?.name != set.name -> null
        set.id == EMAIL_ID -> R.string.core_tools_symbol_set_email_label
        set.id == WEB_ID -> R.string.core_tools_symbol_set_web_label
        set.id == CODING_ID -> R.string.core_tools_symbol_set_coding_label
        set.id == MATH_ID -> R.string.core_tools_symbol_set_math_label
        set.id == PUNCTUATION_ID -> R.string.core_tools_symbol_set_punctuation_label
        else -> null
    }

    val defaultEnabledIds: List<String> = sets.map { it.id }
}

/**
 * Every set the user has, built-ins first in their shipped order, then the
 * user's own. A custom set whose id matches a built-in is an *edit* of that
 * built-in: it takes the built-in's slot rather than appearing twice.
 */
fun resolveSymbolSets(custom: List<SymbolSet>): List<SymbolSet> {
    val overrides = custom.associateBy { it.id }
    val builtIns = BuiltInSymbolSets.sets.map { overrides[it.id] ?: it }
    val builtInIds = BuiltInSymbolSets.sets.mapTo(HashSet()) { it.id }
    return builtIns + custom.filter { it.id !in builtInIds }
}

/** Display label for row chips that would otherwise render invisibly. */
fun symbolChipLabel(text: String): String = when (text) {
    "\t" -> "⇥"
    " " -> "␣"
    else -> SymbolCatalog.label(text)
}
