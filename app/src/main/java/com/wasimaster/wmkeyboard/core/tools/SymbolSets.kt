package com.wasimaster.wmkeyboard.core.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One set of characters for the symbol row: anything committable — single
 * characters ("§", "→") or whole snippets ("@gmail.com", "https://"). The
 * row shows one set at a time; its picker chip switches between the sets
 * the user has enabled (or the active keyboard mode prescribes).
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
 * The sets that ship with the keyboard. Users can't edit these in place
 * (ids stay stable so modes can reference them); the editor duplicates one
 * into a custom set instead.
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

    val defaultEnabledIds: List<String> = sets.map { it.id }
}

/** Display label for row chips that would otherwise render invisibly. */
fun symbolChipLabel(text: String): String = when (text) {
    "\t" -> "⇥"
    " " -> "␣"
    else -> SymbolCatalog.label(text)
}
