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
    /** Clipboard shortcut fired on long press instead of the alternates popup. */
    val clipboardAction: ClipboardKeyAction? = null,
)

/** Clipboard shortcut a letter key can perform on long press (A/C/V/X). */
enum class ClipboardKeyAction { SELECT_ALL, COPY, PASTE, CUT }

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
            // Every letter carries a symbol as its first alternate (the
            // long-press hint), Samsung-style; accents follow the symbol.
            listOf(
                Key("a", longPress = listOf("@", "à", "á", "â", "ä", "å")),
                Key("s", longPress = listOf("#", "ß", "ś")),
                Key("d", longPress = listOf("$")), Key("f", longPress = listOf("_")),
                Key("g", longPress = listOf("&")), Key("h", longPress = listOf("-")),
                Key("j", longPress = listOf("+")), Key("k", longPress = listOf("(")),
                Key("l", longPress = listOf(")")),
            ),
            listOf(
                Key("⇧", action = KeyAction.Shift, width = 1.5f),
                Key("z", longPress = listOf("*", "ż", "ź")), Key("x", longPress = listOf("\"")),
                Key("c", longPress = listOf("'", "ç", "ć")), Key("v", longPress = listOf(":")),
                Key("b", longPress = listOf(";")), Key("n", longPress = listOf("!", "ñ", "ń")),
                Key("m", longPress = listOf("?")),
                Key("⌫", action = KeyAction.Delete, width = 1.5f),
            ),
            bottomRow(),
        ),
    )

    /**
     * AZERTY: the French/Belgian Latin layout. Letter positions follow the
     * standard AZERTY keymap (A/Q and Z/W swapped, M on the home row); the
     * apostrophe gets its own key where QWERTY's M sits. Long-press keeps
     * the Samsung-style symbol-first alternates, with French accents
     * (é è ç à …) on their base vowels.
     */
    val AZERTY = KeyboardLayout(
        name = "azerty",
        rows = listOf(
            listOf(
                Key("a", longPress = listOf("1", "à", "â", "æ", "á", "ä")),
                Key("z", longPress = listOf("2")),
                Key("e", longPress = listOf("3", "é", "è", "ê", "ë", "€")),
                Key("r", longPress = listOf("4")), Key("t", longPress = listOf("5")),
                Key("y", longPress = listOf("6", "ÿ")),
                Key("u", longPress = listOf("7", "ù", "û", "ü", "ú")),
                Key("i", longPress = listOf("8", "î", "ï", "í", "ì")),
                Key("o", longPress = listOf("9", "ô", "ö", "œ", "ó", "ò")),
                Key("p", longPress = listOf("0")),
            ),
            listOf(
                Key("q", longPress = listOf("@")), Key("s", longPress = listOf("#", "ß")),
                Key("d", longPress = listOf("$")), Key("f", longPress = listOf("_")),
                Key("g", longPress = listOf("&")), Key("h", longPress = listOf("-")),
                Key("j", longPress = listOf("+")), Key("k", longPress = listOf("(")),
                Key("l", longPress = listOf(")")), Key("m", longPress = listOf("?")),
            ),
            listOf(
                Key("⇧", action = KeyAction.Shift, width = 1.5f),
                Key("w", longPress = listOf("*")), Key("x", longPress = listOf("\"")),
                Key("c", longPress = listOf(";", "ç", "ć")), Key("v", longPress = listOf(":")),
                Key("b", longPress = listOf("!")), Key("n", longPress = listOf("?", "ñ")),
                Key("'", shiftLabel = "\"", longPress = listOf("‘", "’", "„")),
                Key("⌫", action = KeyAction.Delete, width = 1.5f),
            ),
            bottomRow(),
        ),
    )

    /**
     * QWERTZ: the German Latin layout — QWERTY with Y and Z swapped. The
     * umlauts and ß don't get dedicated keys on the 10-column phone grid
     * (desktop QWERTZ puts them on ö/ä/ü/ß keys that don't exist here);
     * they lead the long-press alternates on their base letters instead,
     * Samsung-style behind the symbol hint.
     */
    val QWERTZ = KeyboardLayout(
        name = "qwertz",
        rows = listOf(
            listOf(
                Key("q", longPress = listOf("1")), Key("w", longPress = listOf("2")),
                Key("e", longPress = listOf("3", "é", "è", "ê", "ë")), Key("r", longPress = listOf("4")),
                Key("t", longPress = listOf("5")), Key("z", longPress = listOf("6", "ż", "ź")),
                Key("u", longPress = listOf("7", "ü", "ù", "ú", "û")),
                Key("i", longPress = listOf("8", "ì", "í", "î", "ï")),
                Key("o", longPress = listOf("9", "ö", "ò", "ó", "ô")),
                Key("p", longPress = listOf("0")),
            ),
            listOf(
                Key("a", longPress = listOf("@", "ä", "à", "á", "â")),
                Key("s", longPress = listOf("#", "ß", "ś")),
                Key("d", longPress = listOf("$")), Key("f", longPress = listOf("_")),
                Key("g", longPress = listOf("&")), Key("h", longPress = listOf("-")),
                Key("j", longPress = listOf("+")), Key("k", longPress = listOf("(")),
                Key("l", longPress = listOf(")")),
            ),
            listOf(
                Key("⇧", action = KeyAction.Shift, width = 1.5f),
                Key("y", longPress = listOf("*", "ÿ")), Key("x", longPress = listOf("\"")),
                Key("c", longPress = listOf("'", "ç", "ć")), Key("v", longPress = listOf(":")),
                Key("b", longPress = listOf(";")), Key("n", longPress = listOf("!", "ñ", "ń")),
                Key("m", longPress = listOf("?")),
                Key("⌫", action = KeyAction.Delete, width = 1.5f),
            ),
            bottomRow(),
        ),
    )

    /**
     * Spanish: QWERTY with the Ñ key appended to the home row, as on
     * Spanish desktop keyboards and every Spanish phone layout. Acute
     * accents (á é í ó ú) and ü lead their vowels' long-press alternates;
     * inverted punctuation (¿ ¡) rides on the ? and ! alternates.
     */
    val SPANISH_QWERTY = KeyboardLayout(
        name = "spanish",
        rows = listOf(
            listOf(
                Key("q", longPress = listOf("1")), Key("w", longPress = listOf("2")),
                Key("e", longPress = listOf("3", "é", "è", "ê", "ë")), Key("r", longPress = listOf("4")),
                Key("t", longPress = listOf("5")), Key("y", longPress = listOf("6")),
                Key("u", longPress = listOf("7", "ú", "ü", "ù", "û")),
                Key("i", longPress = listOf("8", "í", "ì", "î", "ï")),
                Key("o", longPress = listOf("9", "ó", "ò", "ô", "ö")),
                Key("p", longPress = listOf("0")),
            ),
            listOf(
                Key("a", longPress = listOf("@", "á", "à", "â", "ä")),
                Key("s", longPress = listOf("#", "ß", "ś")),
                Key("d", longPress = listOf("$")), Key("f", longPress = listOf("_")),
                Key("g", longPress = listOf("&")), Key("h", longPress = listOf("-")),
                Key("j", longPress = listOf("+")), Key("k", longPress = listOf("(")),
                Key("l", longPress = listOf(")")), Key("ñ"),
            ),
            listOf(
                Key("⇧", action = KeyAction.Shift, width = 1.5f),
                Key("z", longPress = listOf("*", "ż", "ź")), Key("x", longPress = listOf("\"")),
                Key("c", longPress = listOf("'", "ç", "ć")), Key("v", longPress = listOf(":")),
                Key("b", longPress = listOf(";")), Key("n", longPress = listOf("!", "¡")),
                Key("m", longPress = listOf("?", "¿")),
                Key("⌫", action = KeyAction.Delete, width = 1.5f),
            ),
            bottomRow(),
        ),
    )

    /**
     * Dvorak: the simplified layout with vowels on the left home row.
     * Follows the standard Dvorak keymap adapted to the 10-column phone
     * grid: ' , . start the top row (with " < > on shift, ANSI style) and
     * the desktop ; key's spot is dropped — ; lives on long-press instead.
     */
    val DVORAK = KeyboardLayout(
        name = "dvorak",
        rows = listOf(
            listOf(
                Key("'", shiftLabel = "\"", longPress = listOf("1", "‘", "’")),
                Key(",", shiftLabel = "<", longPress = listOf("2")),
                Key(".", shiftLabel = ">", longPress = listOf("3", "…")),
                Key("p", longPress = listOf("4")), Key("y", longPress = listOf("5")),
                Key("f", longPress = listOf("6")), Key("g", longPress = listOf("7")),
                Key("c", longPress = listOf("8", "ç", "ć")),
                Key("r", longPress = listOf("9")), Key("l", longPress = listOf("0")),
            ),
            listOf(
                Key("a", longPress = listOf("@", "à", "á", "â", "ä", "å")),
                Key("o", longPress = listOf("#", "ò", "ó", "ô", "ö")),
                Key("e", longPress = listOf("$", "è", "é", "ê", "ë")),
                Key("u", longPress = listOf("_", "ù", "ú", "û", "ü")),
                Key("i", longPress = listOf("&", "ì", "í", "î", "ï")),
                Key("d", longPress = listOf("-")), Key("h", longPress = listOf("+")),
                Key("t", longPress = listOf("(")), Key("n", longPress = listOf(")", "ñ", "ń")),
                Key("s", longPress = listOf("/", "ß", "ś")),
            ),
            listOf(
                Key("⇧", action = KeyAction.Shift),
                Key("q", longPress = listOf("*")), Key("j", longPress = listOf("\"")),
                Key("k", longPress = listOf("'")), Key("x", longPress = listOf(":")),
                Key("b", longPress = listOf(";")), Key("m", longPress = listOf("!")),
                Key("w", longPress = listOf("?")), Key("v", longPress = listOf("=")),
                Key("z", longPress = listOf("%", "ż", "ź")),
                Key("⌫", action = KeyAction.Delete),
            ),
            bottomRow(),
        ),
    )

    /**
     * Probhat: the fixed Bengali layout popular on Linux, following the
     * standard keymap (as shipped by Avro/OpenBangla). Letter keys q–m match
     * the desktop layout exactly; characters that live on desktop punctuation
     * keys are adapted for the 10-column phone grid:
     *  - the 10th home-row key carries ে/ো (desktop `[`/`]`) with ৈ/ৌ on
     *    long-press (desktop Shift+`[`/Shift+`]`);
     *  - ৎ ঞ ঁ ৃ ৗ ঽ ় ॥ move to long-press on their phonetic neighbours.
     */
    val PROBHAT = KeyboardLayout(
        name = "probhat",
        rows = listOf(
            listOf(
                Key("দ", shiftLabel = "ধ"), Key("ূ", shiftLabel = "ঊ"), Key("ী", shiftLabel = "ঈ"),
                Key("র", shiftLabel = "ড়"), Key("ট", shiftLabel = "ঠ"),
                Key("এ", shiftLabel = "ঐ"), Key("ু", shiftLabel = "উ"),
                Key("ি", shiftLabel = "ই"), Key("ও", shiftLabel = "ঔ", longPress = listOf("ৗ")),
                Key("প", shiftLabel = "ফ"),
            ),
            listOf(
                Key("া", shiftLabel = "অ"), Key("স", shiftLabel = "ষ"),
                Key("ড", shiftLabel = "ঢ"), Key("ত", shiftLabel = "থ", longPress = listOf("ৎ")),
                Key("গ", shiftLabel = "ঘ"), Key("হ", shiftLabel = "ঃ", longPress = listOf("ঽ")),
                Key("জ", shiftLabel = "ঝ", longPress = listOf("ঞ")), Key("ক", shiftLabel = "খ"),
                Key("ল", shiftLabel = "ং"), Key("ে", shiftLabel = "ো", longPress = listOf("ৈ", "ৌ")),
            ),
            listOf(
                Key("⇧", action = KeyAction.Shift, width = 1.2f),
                Key("য়", shiftLabel = "য"), Key("শ", shiftLabel = "ঢ়"), Key("চ", shiftLabel = "ছ"),
                Key("আ", shiftLabel = "ঋ", longPress = listOf("ৃ")), Key("ব", shiftLabel = "ভ"),
                Key("ন", shiftLabel = "ণ"), Key("ম", shiftLabel = "ঙ"),
                Key("্", shiftLabel = "।", longPress = listOf("ঁ", "়", "॥")),
                Key("⌫", action = KeyAction.Delete, width = 1.3f),
            ),
            bottomRow(),
        ),
    )

    /**
     * National (Jatiya): the Bangladesh national standard fixed layout,
     * following the standard keymap (as shipped by Avro/OpenBangla). Letter
     * keys match the desktop layout; independent vowels sit on desktop AltGr
     * and are adapted here as long-press on their kar keys (ই on ি, উ on ু,
     * …), matching how fixed-layout phone keyboards ship Jatiya.
     */
    val JATIYA = KeyboardLayout(
        name = "jatiya",
        rows = listOf(
            listOf(
                Key("ঙ", shiftLabel = "ং"), Key("য", shiftLabel = "য়"), Key("ড", shiftLabel = "ঢ"),
                Key("প", shiftLabel = "ফ"), Key("ট", shiftLabel = "ঠ"), Key("চ", shiftLabel = "ছ"),
                Key("জ", shiftLabel = "ঝ"), Key("হ", shiftLabel = "ঞ", longPress = listOf("ঽ")),
                Key("গ", shiftLabel = "ঘ"), Key("ড়", shiftLabel = "ঢ়"),
            ),
            listOf(
                Key("ৃ", shiftLabel = "ৗ", longPress = listOf("ঋ")),
                Key("ু", shiftLabel = "ূ", longPress = listOf("উ", "ঊ")),
                Key("ি", shiftLabel = "ী", longPress = listOf("ই", "ঈ")),
                Key("ব", shiftLabel = "ভ"),
                Key("্", shiftLabel = "।", longPress = listOf("॥", "ৎ")),
                Key("া", shiftLabel = "অ", longPress = listOf("আ")),
                Key("ক", shiftLabel = "খ"), Key("ত", shiftLabel = "থ"),
                Key("দ", shiftLabel = "ধ"),
            ),
            listOf(
                Key("⇧", action = KeyAction.Shift, width = 1.2f),
                Key("ঁ", shiftLabel = "ঃ"),
                Key("ো", shiftLabel = "ৌ", longPress = listOf("ও", "ঔ")),
                Key("ে", shiftLabel = "ৈ", longPress = listOf("এ", "ঐ")),
                Key("র", shiftLabel = "ল"), Key("ন", shiftLabel = "ণ"),
                Key("স", shiftLabel = "ষ"), Key("ম", shiftLabel = "শ"),
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
        Key(" ", action = KeyAction.Space, width = 4f),
        Key(".", longPress = listOf("…", ",", "?", "!", ":", ";", "।")),
        Key("⏎", action = KeyAction.Enter, width = 1.5f),
    )
}
