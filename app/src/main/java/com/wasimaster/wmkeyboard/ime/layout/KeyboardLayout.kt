package com.wasimaster.wmkeyboard.ime.layout

/**
 * Declarative keyboard layout model rendered by the Compose keyboard view.
 *
 * A [Key] either outputs text ([Key.output], falling back to [Key.label])
 * or triggers an [action]. [Key.longPress] holds the alternate characters
 * shown in the long-press popup. [width] is relative: 1.0 is a standard
 * key, the spacebar is wider, shift/delete slightly wider.
 */
data class Key(
    val label: String,
    val output: String? = null,
    val shiftLabel: String? = null,
    val action: KeyAction = KeyAction.Text,
    val width: Float = 1f,
    val longPress: List<String> = emptyList(),
)

enum class KeyAction { Text, Shift, Delete, Space, Enter, Symbols, Letters, LanguageSwitch, Emoji }

data class KeyboardLayout(
    val name: String,
    val rows: List<List<Key>>,
)

object Layouts {

    private fun letters(vararg chars: String) = chars.map { Key(it) }

    val QWERTY = KeyboardLayout(
        name = "qwerty",
        rows = listOf(
            listOf(
                Key("q", longPress = listOf("1")), Key("w", longPress = listOf("2")),
                Key("e", longPress = listOf("3", "è", "é", "ê", "ë")), Key("r", longPress = listOf("4")),
                Key("t", longPress = listOf("5")), Key("y", longPress = listOf("6")),
                Key("u", longPress = listOf("7", "ù", "ú", "û", "ü")),
                Key("i", longPress = listOf("8", "ì", "í", "î", "ï")),
                Key("o", longPress = listOf("9", "ò", "ó", "ô", "ö")),
                Key("p", longPress = listOf("0")),
            ),
            listOf(
                Key("a", longPress = listOf("à", "á", "â", "ä", "å")), Key("s", longPress = listOf("ß", "ś")),
                Key("d"), Key("f"), Key("g"), Key("h"), Key("j"), Key("k"), Key("l"),
            ),
            listOf(
                Key("⇧", action = KeyAction.Shift, width = 1.5f),
                Key("z", longPress = listOf("ż", "ź")), Key("x"), Key("c", longPress = listOf("ç", "ć")),
                Key("v"), Key("b"), Key("n", longPress = listOf("ñ", "ń")), Key("m"),
                Key("⌫", action = KeyAction.Delete, width = 1.5f),
            ),
            bottomRow(),
        ),
    )

    /**
     * Probhat: the fixed Bengali layout popular on Linux. Vowel signs on the
     * home row, consonants arranged by frequency; shift carries the
     * aspirates and less-common letters.
     */
    val PROBHAT = KeyboardLayout(
        name = "probhat",
        rows = listOf(
            listOf(
                Key("দ", shiftLabel = "ধ"), Key("ূ", shiftLabel = "ঊ"), Key("ী", shiftLabel = "ঈ"),
                Key("র", shiftLabel = "ড়", longPress = listOf("ড়", "ঢ়")), Key("ট", shiftLabel = "ঠ"),
                Key("এ", shiftLabel = "ঐ", longPress = listOf("ৃ")), Key("ু", shiftLabel = "উ"),
                Key("ি", shiftLabel = "ই"), Key("ও", shiftLabel = "ঔ"), Key("প", shiftLabel = "ফ"),
            ),
            listOf(
                Key("া", shiftLabel = "আ", longPress = listOf("অ")), Key("স", shiftLabel = "ষ", longPress = listOf("শ")),
                Key("ড", shiftLabel = "ঢ"), Key("ত", shiftLabel = "থ", longPress = listOf("ৎ")),
                Key("গ", shiftLabel = "ঘ"), Key("হ", shiftLabel = "ঃ"),
                Key("জ", shiftLabel = "ঝ", longPress = listOf("য")), Key("ক", shiftLabel = "খ"),
                Key("ল", shiftLabel = "ং"), Key("ে", shiftLabel = "ৈ"),
            ),
            listOf(
                Key("⇧", action = KeyAction.Shift, width = 1.2f),
                Key("য়", shiftLabel = "ঞ"), Key("ষ", shiftLabel = "ঁ"), Key("চ", shiftLabel = "ছ"),
                Key("ভ", shiftLabel = "ণ"), Key("ব", shiftLabel = "ন"), Key("ন", shiftLabel = "ম"),
                Key("ম", shiftLabel = "ঙ"), Key("্", shiftLabel = "।", longPress = listOf("।", "॥")),
                Key("⌫", action = KeyAction.Delete, width = 1.3f),
            ),
            bottomRow(),
        ),
    )

    val SYMBOLS = KeyboardLayout(
        name = "symbols",
        rows = listOf(
            listOf(
                Key("1", longPress = listOf("¹", "½", "⅓", "¼")), Key("2", longPress = listOf("²", "⅔")),
                Key("3", longPress = listOf("³", "¾")), Key("4", longPress = listOf("⁴")), Key("5"),
                Key("6"), Key("7"), Key("8"), Key("9"), Key("0", longPress = listOf("ⁿ", "∅")),
            ),
            listOf(
                Key("@"), Key("#", longPress = listOf("№")), Key("$", longPress = listOf("৳", "€", "£", "¥", "₹", "₿")),
                Key("_"), Key("&"), Key("-", longPress = listOf("–", "—", "·")),
                Key("+", longPress = listOf("±")), Key("(", longPress = listOf("[", "{", "<")),
                Key(")", longPress = listOf("]", "}", ">")), Key("/", longPress = listOf("\\", "÷")),
            ),
            listOf(
                Key("=\\<", action = KeyAction.Symbols, width = 1.5f),
                Key("*", longPress = listOf("†", "★", "×")), Key("\"", longPress = listOf("“", "”", "„", "«", "»")),
                Key("'", longPress = listOf("‘", "’", "‚", "‹", "›")), Key(":"), Key(";"),
                Key("!", longPress = listOf("¡")), Key("?", longPress = listOf("¿", "‽")),
                Key("⌫", action = KeyAction.Delete, width = 1.5f),
            ),
            bottomRow(symbols = true),
        ),
    )

    val SYMBOLS_SHIFTED = KeyboardLayout(
        name = "symbols2",
        rows = listOf(
            listOf(
                Key("~"), Key("`"), Key("|"), Key("•", longPress = listOf("◦", "‣")), Key("√"),
                Key("π", longPress = listOf("Π", "℮")), Key("÷"), Key("×"), Key("¶", longPress = listOf("§")), Key("∆"),
            ),
            listOf(
                Key("৳"), Key("€"), Key("£", longPress = listOf("₺")), Key("¥"), Key("₹"),
                Key("^", longPress = listOf("↑", "↓", "←", "→")), Key("°", longPress = listOf("′", "″")),
                Key("{"), Key("}"), Key("\\"),
            ),
            listOf(
                Key("?123", action = KeyAction.Symbols, width = 1.5f),
                Key("%", longPress = listOf("‰")), Key("©"), Key("®"), Key("™"),
                Key("✓", longPress = listOf("✔", "✗", "☆", "★")), Key("[", longPress = listOf("{")),
                Key("]", longPress = listOf("}")),
                Key("⌫", action = KeyAction.Delete, width = 1.5f),
            ),
            bottomRow(symbols = true),
        ),
    )

    private fun bottomRow(symbols: Boolean = false) = listOf(
        Key(
            if (symbols) "ABC" else "?123",
            action = if (symbols) KeyAction.Letters else KeyAction.Symbols,
            width = 1.5f,
        ),
        Key(",", longPress = listOf("!", "?")),
        Key("🌐", action = KeyAction.LanguageSwitch),
        Key(" ", action = KeyAction.Space, width = 3.5f),
        Key(".", longPress = listOf("…", ",", "?", "!", ":", ";", "।")),
        Key("⏎", action = KeyAction.Enter, width = 1.5f),
    )
}
