package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.script.ComposerType

/**
 * The layouts that ship with the keyboard.
 *
 * Their ids stay stable so stored references survive: editing one stores a
 * custom layout under the *same* id that shadows the shipped version (see
 * [resolveLayouts]), so a 🌐 cycle pinned to "builtin_probhat" keeps working
 * and deleting the edit restores the original grid.
 *
 * Kotlin rather than JSON in assets, unlike the dictionaries: the letter grid
 * is needed synchronously on the first frame, so it cannot be parsed off the
 * main thread the way the word lists are, and a typo in a val fails the build
 * where a typo in an asset ships a blank keyboard. The KDoc explaining *why*
 * Probhat's tenth home-row key carries ে/ো is also the most valuable thing in
 * this file, and JSON has no comments.
 *
 * Only [QWERTY] defines every layer. The rest define [LayoutLayer.LETTERS] and
 * inherit the symbol layers and the five numeric keypads, so those grids exist
 * exactly once.
 */
object BuiltInLayouts {

    const val QWERTY_ID = "builtin_qwerty"
    const val AZERTY_ID = "builtin_azerty"
    const val DVORAK_ID = "builtin_dvorak"
    const val COLEMAK_ID = "builtin_colemak"
    const val WORKMAN_ID = "builtin_workman"
    const val HALMAK_ID = "builtin_halmak"
    const val AVRO_ID = "builtin_avro"
    const val PROBHAT_ID = "builtin_probhat"
    const val JATIYA_ID = "builtin_jatiya"
    const val FRENCH_ID = "builtin_french"
    const val GERMAN_ID = "builtin_qwertz"
    const val SPANISH_ID = "builtin_spanish"
    const val KOREAN_ID = "builtin_korean"
    const val RUSSIAN_ID = "builtin_russian"
    const val ARABIC_ID = "builtin_arabic"
    const val GREEK_ID = "builtin_greek"
    const val HEBREW_ID = "builtin_hebrew"
    const val HINDI_ID = "builtin_hindi"

    const val DEFAULT_ID = QWERTY_ID

    /**
     * The shared layers every built-in falls back to: the two symbol grids and
     * the five field-driven keypads. Held on [QWERTY] because it is the default
     * layout, and [LayoutSpec.compile] resolves a missing layer against it.
     */
    private val sharedLayers: Map<String, LayerSpec> = mapOf(
        LayoutLayer.SYMBOLS.key to LayerSpec(symbolRows),
        LayoutLayer.SYMBOLS_SHIFTED.key to LayerSpec(symbolsShiftedRows),
        LayoutLayer.NUMBER.key to LayerSpec(numberRows),
        LayoutLayer.PHONE.key to LayerSpec(phoneRows),
        LayoutLayer.DATE.key to LayerSpec(dateRows),
        LayoutLayer.TIME.key to LayerSpec(timeRows),
        LayoutLayer.DATETIME.key to LayerSpec(dateTimeRows),
    )

    val QWERTY = LayoutSpec(
        id = QWERTY_ID,
        name = "QWERTY",
        langId = "en",
        layers = sharedLayers + (LayoutLayer.LETTERS.key to LayerSpec(qwertyRows)),
    )

    /**
     * AZERTY: the French/Belgian Latin layout. Letter positions follow the
     * standard AZERTY keymap (A/Q and Z/W swapped, M on the home row); the
     * apostrophe gets its own key where QWERTY's M sits. Long-press keeps the
     * Samsung-style symbol-first alternates, with French accents (é è ç à …) on
     * their base vowels.
     */
    val AZERTY = LayoutSpec(
        id = AZERTY_ID,
        name = "AZERTY",
        langId = "en",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(azertyRows)),
    )

    /**
     * Dvorak: the simplified layout with vowels on the left home row. Follows
     * the standard Dvorak keymap adapted to the 10-column phone grid: ' , .
     * start the top row (with " < > on shift, ANSI style) and the desktop ; key's
     * spot is dropped — ; lives on long-press instead.
     */
    val DVORAK = LayoutSpec(
        id = DVORAK_ID,
        name = "Dvorak",
        langId = "en",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(dvorakRows)),
    )

    /**
     * Colemak: the modern ergonomic layout that keeps Z X C V in place. Follows
     * the standard Colemak keymap adapted to the 10-column phone grid — the top
     * row drops the desktop ; key (all 26 letters still fit as 9 / 10 / 7), with
     * Samsung-style symbol-first alternates and accents on the base vowels.
     */
    val COLEMAK = LayoutSpec(
        id = COLEMAK_ID,
        name = "Colemak",
        langId = "en",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(colemakRows)),
    )

    /**
     * Workman: the layout tuned to English digraph frequency. Follows the
     * standard Workman keymap adapted to the 10-column phone grid (9 / 10 / 7,
     * the desktop ; key dropped from the top row), same symbol-first alternates.
     */
    val WORKMAN = LayoutSpec(
        id = WORKMAN_ID,
        name = "Workman",
        langId = "en",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(workmanRows)),
    )

    /**
     * Halmak: the AI-designed layout tuned to English typing efficiency and hand
     * alternation. Follows the standard Halmak keymap adapted to the 10-column
     * phone grid (9 / 10 / 7, with desktop punctuation dropped to long-press),
     * same symbol-first alternates and vowel accents.
     */
    val HALMAK = LayoutSpec(
        id = HALMAK_ID,
        name = "Halmak",
        langId = "en",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(halmakRows)),
    )

    /** Avro types Bengali phonetically, so it wears the QWERTY grid unchanged. */
    val AVRO = LayoutSpec(
        id = AVRO_ID,
        name = "Avro phonetic",
        langId = "bn",
        composer = ComposerType.TRANSLITERATE,
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(qwertyRows)),
    )

    /**
     * Probhat: the fixed Bengali layout popular on Linux, following the standard
     * keymap (as shipped by Avro/OpenBangla). Letter keys q–m match the desktop
     * layout exactly; characters that live on desktop punctuation keys are
     * adapted for the 10-column phone grid:
     *  - the 10th home-row key carries ে/ো (desktop `[`/`]`) with ৈ/ৌ on
     *    long-press (desktop Shift+`[`/Shift+`]`);
     *  - ৎ ঞ ঁ ৃ ৗ ঽ ় ॥ move to long-press on their phonetic neighbours.
     */
    val PROBHAT = LayoutSpec(
        id = PROBHAT_ID,
        name = "প্রভাত (Probhat)",
        langId = "bn",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(probhatRows)),
    )

    /**
     * National (Jatiya): the Bangladesh national standard fixed layout,
     * following the standard keymap (as shipped by Avro/OpenBangla). Letter keys
     * match the desktop layout; independent vowels sit on desktop AltGr and are
     * adapted here as long-press on their kar keys (ই on ি, উ on ু, …), matching
     * how fixed-layout phone keyboards ship Jatiya.
     */
    val JATIYA = LayoutSpec(
        id = JATIYA_ID,
        name = "জাতীয় (National)",
        langId = "bn",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(jatiyaRows)),
    )

    /** French shares AZERTY's key grid; the modes differ in language, not keys. */
    val FRENCH = LayoutSpec(
        id = FRENCH_ID,
        name = "AZERTY (French)",
        langId = "fr",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(azertyRows)),
    )

    /**
     * QWERTZ: the German Latin layout — QWERTY with Y and Z swapped. The umlauts
     * and ß don't get dedicated keys on the 10-column phone grid (desktop QWERTZ
     * puts them on ö/ä/ü/ß keys that don't exist here); they lead the long-press
     * alternates on their base letters instead, Samsung-style behind the symbol
     * hint.
     */
    val GERMAN = LayoutSpec(
        id = GERMAN_ID,
        name = "QWERTZ",
        langId = "de",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(qwertzRows)),
    )

    /**
     * Spanish: QWERTY with the Ñ key appended to the home row, as on Spanish
     * desktop keyboards and every Spanish phone layout. Acute accents (á é í ó ú)
     * and ü lead their vowels' long-press alternates; inverted punctuation (¿ ¡)
     * rides on the ? and ! alternates.
     */
    val SPANISH = LayoutSpec(
        id = SPANISH_ID,
        name = "QWERTY + Ñ",
        langId = "es",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(spanishRows)),
    )

    /**
     * Korean (2-set / 두벌식): the standard phone layout. Keys emit compatibility
     * jamo that [com.wasimaster.wmkeyboard.core.input.composer.HangulComposer]
     * assembles into syllable blocks; shift gives the tense consonants (ㅃ ㅉ …)
     * and ㅒ/ㅖ.
     */
    val KOREAN = LayoutSpec(
        id = KOREAN_ID,
        name = "한국어 (2-beolsik)",
        langId = "ko",
        composer = ComposerType.HANGUL,
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(koreanRows)),
    )

    /** Russian ЙЦУКЕН: the standard Cyrillic phone layout; shift uppercases. */
    val RUSSIAN = LayoutSpec(
        id = RUSSIAN_ID,
        name = "ЙЦУКЕН",
        langId = "ru",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(russianRows)),
    )

    /**
     * Arabic: the standard letter arrangement, keys authored in visual (left to
     * right) order — the committed text is logical and the field renders it
     * right to left, so the grid itself needs no mirroring.
     */
    val ARABIC = LayoutSpec(
        id = ARABIC_ID,
        name = "العربية",
        langId = "ar",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(arabicRows)),
    )

    /** Greek: the national layout; shift uppercases, tonos vowels on long-press. */
    val GREEK = LayoutSpec(
        id = GREEK_ID,
        name = "Ελληνικά",
        langId = "el",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(greekRows)),
    )

    /**
     * Hebrew: the standard letter arrangement, keys authored in visual (left to
     * right) order like Arabic — the committed text is logical and the field
     * renders it right to left, so the grid itself needs no mirroring.
     */
    val HEBREW = LayoutSpec(
        id = HEBREW_ID,
        name = "עברית",
        langId = "he",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(hebrewRows)),
    )

    /**
     * Hindi (Devanagari), the standard InScript keymap. Inherits the script's
     * [ComposerType.INDIC_CLUSTER] (no per-layout override needed), so backspace
     * removes a whole consonant-sign cluster.
     */
    val HINDI = LayoutSpec(
        id = HINDI_ID,
        name = "हिन्दी",
        langId = "hi",
        layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(hindiRows)),
    )

    /**
     * Every compiled-in layout, in shipped order — even where two share a grid
     * (AZERTY and French are the same keys under different languages). This is
     * the boot-critical set drawn on the first frame; the JSON [AssetLayouts]
     * add the language tail at runtime, and both feed `resolveLayouts`.
     */
    val all: List<LayoutSpec> = listOf(
        QWERTY, AZERTY, DVORAK, COLEMAK, WORKMAN, HALMAK, AVRO, PROBHAT, JATIYA, FRENCH,
        GERMAN, SPANISH, KOREAN, RUSSIAN, ARABIC, GREEK, HEBREW, HINDI,
    )

    fun byId(id: String): LayoutSpec? = all.firstOrNull { it.id == id }

    val default: LayoutSpec get() = byId(DEFAULT_ID) ?: error("Built-in layout $DEFAULT_ID is missing")

    /**
     * A starting Fn layer, for a layout that wants one.
     *
     * Not attached to any built-in — nothing ships with an Fn key — but offered
     * by the editor as a template, and it doubles as the proof that
     * [KeyAction.SendKey] works end to end. Deliberately four rows, so enabling
     * it never inflates the row budget the panels are sized against.
     */
    val FN_DEFAULT: LayerSpec = LayerSpec(
        rows = listOf(
            listOf(
                fnKey("Esc", 111), fnKey("F1", 131), fnKey("F2", 132), fnKey("F3", 133),
                fnKey("F4", 134), fnKey("F5", 135), fnKey("F6", 136),
            ),
            listOf(
                fnKey("Tab", 61), fnKey("F7", 137), fnKey("F8", 138), fnKey("F9", 139),
                fnKey("F10", 140), fnKey("F11", 141), fnKey("F12", 142),
            ),
            listOf(
                fnKey("Home", 122), fnKey("↑", 19), fnKey("End", 123),
                fnKey("PgUp", 92), fnKey("Ins", 124), fnKey("Del", 112),
                Key("⌫", action = KeyAction.Delete),
            ),
            listOf(
                fnKey("←", 21), fnKey("↓", 20), fnKey("→", 22),
                fnKey("PgDn", 93),
                Key("ABC", action = KeyAction.Letters, width = 1.5f),
                Key(" ", action = KeyAction.Space, width = 1.5f),
                Key("⏎", action = KeyAction.Enter),
            ),
        ),
    )

    /**
     * What a fresh install starts with before onboarding or the language screen
     * has said otherwise: one layout per language, English and Bengali.
     *
     * It used to be four — Probhat and Jatiya alongside Avro — because this list
     * was inherited from the old `InputMode` enum, where a "mode" was a layout
     * and every Bengali layout was therefore its own entry. That reads as three
     * Bengali keyboards to switch between when the user only ever wanted one:
     * whoever types Probhat picks it from the language screen, and Avro is what
     * the rest of Bangladesh types on.
     */
    val defaultEnabledIds: List<String> = listOf(QWERTY_ID, AVRO_ID)
}

// ---------------------------------------------------------------------------
// The grids themselves, as private top-level vals so the object above reads as
// a catalog rather than a wall of keys. Their KDoc lives on the LayoutSpec that
// exposes each one.
// ---------------------------------------------------------------------------

private fun bottomRow(symbols: Boolean = false) = listOf(
    Key(
        if (symbols) "ABC" else "?123",
        action = if (symbols) KeyAction.Letters else KeyAction.Symbols,
        width = 1.5f,
    ),
    Key(",", role = KeyRole.Comma, longPress = listOf("!", "?")),
    Key("🌐", action = KeyAction.LanguageSwitch),
    Key(" ", action = KeyAction.Space, width = 4f),
    Key(".", role = KeyRole.Period, longPress = listOf("…", ",", "?", "!", ":", ";", "।")),
    Key("⏎", action = KeyAction.Enter, width = 1.5f),
)

private val qwertyRows = listOf(
    listOf(
        Key("q", longPress = listOf("1")), Key("w", longPress = listOf("2")),
        Key("e", longPress = listOf("3", "è", "é", "ê", "ë")), Key("r", longPress = listOf("4")),
        Key("t", longPress = listOf("5")), Key("y", longPress = listOf("6")),
        Key("u", longPress = listOf("7", "ù", "ú", "û", "ü")),
        Key("i", longPress = listOf("8", "ì", "í", "î", "ï")),
        Key("o", longPress = listOf("9", "ò", "ó", "ô", "ö")),
        Key("p", longPress = listOf("0")),
    ),
    // Every letter carries a symbol as its first alternate (the long-press
    // hint), Samsung-style; accents follow the symbol.
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
)

private val azertyRows = listOf(
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
)

private val qwertzRows = listOf(
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
)

private val spanishRows = listOf(
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
)

private val dvorakRows = listOf(
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
)

// Colemak: Z X C V stay put; top row drops the desktop ; key so all 26 letters
// fit the phone grid as 9 / 10 / 7. Number hints ride the top row, accents on
// the base vowels, symbol-first alternates elsewhere — Samsung-style like QWERTY.
private val colemakRows = listOf(
    listOf(
        Key("q", longPress = listOf("1")), Key("w", longPress = listOf("2")),
        Key("f", longPress = listOf("3")), Key("p", longPress = listOf("4")),
        Key("g", longPress = listOf("5")), Key("j", longPress = listOf("6")),
        Key("l", longPress = listOf("7")),
        Key("u", longPress = listOf("8", "ù", "ú", "û", "ü")),
        Key("y", longPress = listOf("9", "ÿ")),
    ),
    listOf(
        Key("a", longPress = listOf("@", "à", "á", "â", "ä", "å")),
        Key("r", longPress = listOf("#")), Key("s", longPress = listOf("$", "ß", "ś")),
        Key("t", longPress = listOf("_")), Key("d", longPress = listOf("&")),
        Key("h", longPress = listOf("-")), Key("n", longPress = listOf("+", "ñ", "ń")),
        Key("e", longPress = listOf("(", "è", "é", "ê", "ë")),
        Key("i", longPress = listOf(")", "ì", "í", "î", "ï")),
        Key("o", longPress = listOf("/", "ò", "ó", "ô", "ö")),
    ),
    listOf(
        Key("⇧", action = KeyAction.Shift, width = 1.5f),
        Key("z", longPress = listOf("*", "ż", "ź")), Key("x", longPress = listOf("\"")),
        Key("c", longPress = listOf("'", "ç", "ć")), Key("v", longPress = listOf(":")),
        Key("b", longPress = listOf(";")), Key("k", longPress = listOf("!")),
        Key("m", longPress = listOf("?")),
        Key("⌫", action = KeyAction.Delete, width = 1.5f),
    ),
    bottomRow(),
)

// Workman: tuned to English digraph frequency. Same 9 / 10 / 7 phone adaptation
// as Colemak (desktop ; dropped from the top row), same alternate scheme.
private val workmanRows = listOf(
    listOf(
        Key("q", longPress = listOf("1")), Key("d", longPress = listOf("2")),
        Key("r", longPress = listOf("3")), Key("w", longPress = listOf("4")),
        Key("b", longPress = listOf("5")), Key("j", longPress = listOf("6")),
        Key("f", longPress = listOf("7")),
        Key("u", longPress = listOf("8", "ù", "ú", "û", "ü")),
        Key("p", longPress = listOf("9")),
    ),
    listOf(
        Key("a", longPress = listOf("@", "à", "á", "â", "ä", "å")),
        Key("s", longPress = listOf("#", "ß", "ś")), Key("h", longPress = listOf("$")),
        Key("t", longPress = listOf("_")), Key("g", longPress = listOf("&")),
        Key("y", longPress = listOf("-", "ÿ")), Key("n", longPress = listOf("+", "ñ", "ń")),
        Key("e", longPress = listOf("(", "è", "é", "ê", "ë")),
        Key("o", longPress = listOf(")", "ò", "ó", "ô", "ö")),
        Key("i", longPress = listOf("/", "ì", "í", "î", "ï")),
    ),
    listOf(
        Key("⇧", action = KeyAction.Shift, width = 1.5f),
        Key("z", longPress = listOf("*", "ż", "ź")), Key("x", longPress = listOf("\"")),
        Key("m", longPress = listOf("'")), Key("c", longPress = listOf(":", "ç", "ć")),
        Key("v", longPress = listOf(";")), Key("k", longPress = listOf("!")),
        Key("l", longPress = listOf("?")),
        Key("⌫", action = KeyAction.Delete, width = 1.5f),
    ),
    bottomRow(),
)

// Halmak: tuned to English letter frequency & hand alternation. Same 9 / 10 / 7
// phone adaptation as Colemak and Workman (desktop punctuation dropped), same alternate scheme.
private val halmakRows = listOf(
    listOf(
        Key("w", longPress = listOf("1")), Key("l", longPress = listOf("2")),
        Key("r", longPress = listOf("3")), Key("b", longPress = listOf("4")),
        Key("z", longPress = listOf("5", "ż", "ź")), Key("q", longPress = listOf("6")),
        Key("u", longPress = listOf("7", "ù", "ú", "û", "ü")),
        Key("d", longPress = listOf("8")), Key("j", longPress = listOf("9")),
    ),
    listOf(
        Key("s", longPress = listOf("@", "ß", "ś")), Key("h", longPress = listOf("#")),
        Key("n", longPress = listOf("$", "ñ", "ń")), Key("t", longPress = listOf("_")),
        Key("f", longPress = listOf("&")), Key("g", longPress = listOf("-")),
        Key("a", longPress = listOf("+", "à", "á", "â", "ä", "å")),
        Key("e", longPress = listOf("(", "è", "é", "ê", "ë")),
        Key("o", longPress = listOf(")", "ò", "ó", "ô", "ö")),
        Key("i", longPress = listOf("/", "ì", "í", "î", "ï")),
    ),
    listOf(
        Key("⇧", action = KeyAction.Shift, width = 1.5f),
        Key("m", longPress = listOf("*")), Key("v", longPress = listOf("\"")),
        Key("c", longPress = listOf("'", "ç", "ć")), Key("p", longPress = listOf(":")),
        Key("x", longPress = listOf(";")), Key("k", longPress = listOf("!")),
        Key("y", longPress = listOf("?", "ÿ")),
        Key("⌫", action = KeyAction.Delete, width = 1.5f),
    ),
    bottomRow(),
)

private val probhatRows = listOf(
    listOf(
        Key("দ", shiftLabel = "ধ"), Key("ূ", shiftLabel = "ঊ"), Key("ী", shiftLabel = "ঈ"),
        Key("র", shiftLabel = "ড়"), Key("ট", shiftLabel = "ঠ"),
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
        Key("য়", shiftLabel = "য"), Key("শ", shiftLabel = "ঢ়"), Key("চ", shiftLabel = "ছ"),
        Key("আ", shiftLabel = "ঋ", longPress = listOf("ৃ")), Key("ব", shiftLabel = "ভ"),
        Key("ন", shiftLabel = "ণ"), Key("ম", shiftLabel = "ঙ"),
        Key("্", shiftLabel = "।", longPress = listOf("ঁ", "়", "॥")),
        Key("⌫", action = KeyAction.Delete, width = 1.3f),
    ),
    bottomRow(),
)

private val jatiyaRows = listOf(
    listOf(
        Key("ঙ", shiftLabel = "ং"), Key("য", shiftLabel = "য়"), Key("ড", shiftLabel = "ঢ"),
        Key("প", shiftLabel = "ফ"), Key("ট", shiftLabel = "ঠ"), Key("চ", shiftLabel = "ছ"),
        Key("জ", shiftLabel = "ঝ"), Key("হ", shiftLabel = "ঞ", longPress = listOf("ঽ")),
        Key("গ", shiftLabel = "ঘ"), Key("ড়", shiftLabel = "ঢ়"),
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
)

// Korean 2-beolsik: compatibility jamo in the standard phone arrangement, tense
// consonants and ㅒ/ㅖ on shift. HangulComposer assembles them into syllables.
private val koreanRows = listOf(
    listOf(
        Key("ㅂ", shiftLabel = "ㅃ"), Key("ㅈ", shiftLabel = "ㅉ"),
        Key("ㄷ", shiftLabel = "ㄸ"), Key("ㄱ", shiftLabel = "ㄲ"),
        Key("ㅅ", shiftLabel = "ㅆ"), Key("ㅛ"), Key("ㅕ"), Key("ㅑ"),
        Key("ㅐ", shiftLabel = "ㅒ"), Key("ㅔ", shiftLabel = "ㅖ"),
    ),
    listOf(
        Key("ㅁ"), Key("ㄴ"), Key("ㅇ"), Key("ㄹ"), Key("ㅎ"),
        Key("ㅗ"), Key("ㅓ"), Key("ㅏ"), Key("ㅣ"),
    ),
    listOf(
        Key("⇧", action = KeyAction.Shift, width = 1.5f),
        Key("ㅋ"), Key("ㅌ"), Key("ㅊ"), Key("ㅍ"), Key("ㅠ"), Key("ㅜ"), Key("ㅡ"),
        Key("⌫", action = KeyAction.Delete, width = 1.5f),
    ),
    bottomRow(),
)

// Russian ЙЦУКЕН. Cyrillic has case, so shift uppercases the labels (no
// per-key shiftLabel needed); ё and ъ ride long-press.
private val russianRows = listOf(
    listOf(
        Key("й"), Key("ц"), Key("у"), Key("к"), Key("е", longPress = listOf("ё")),
        Key("н"), Key("г"), Key("ш"), Key("щ"), Key("з"), Key("х"),
    ),
    listOf(
        Key("ф"), Key("ы"), Key("в"), Key("а"), Key("п"), Key("р"),
        Key("о"), Key("л"), Key("д"), Key("ж"), Key("э"),
    ),
    listOf(
        Key("⇧", action = KeyAction.Shift, width = 1.5f),
        Key("я"), Key("ч"), Key("с"), Key("м"), Key("и"), Key("т"),
        Key("ь", longPress = listOf("ъ")), Key("б"), Key("ю"),
        Key("⌫", action = KeyAction.Delete, width = 1.5f),
    ),
    bottomRow(),
)

// Arabic letters in visual (left-to-right) key order; the field renders the
// committed text right-to-left, so the grid itself is not mirrored.
private val arabicRows = listOf(
    listOf(
        Key("ض"), Key("ص"), Key("ث"), Key("ق"), Key("ف"), Key("غ"),
        Key("ع"), Key("ه"), Key("خ"), Key("ح"), Key("ج"),
    ),
    listOf(
        Key("ش"), Key("س"), Key("ي", longPress = listOf("ئ")), Key("ب"),
        Key("ل"), Key("ا", longPress = listOf("أ", "إ", "آ")), Key("ت"),
        Key("ن"), Key("م"), Key("ك"), Key("ط", longPress = listOf("ظ")),
    ),
    listOf(
        Key("⇧", action = KeyAction.Shift, width = 1.5f),
        Key("ذ"), Key("د"), Key("ز"), Key("ر"), Key("و", longPress = listOf("ؤ")),
        Key("ة"), Key("ى"), Key("ء"),
        Key("⌫", action = KeyAction.Delete, width = 1.5f),
    ),
    bottomRow(),
)

// Greek national layout. Greek has case, so shift uppercases the labels
// (ς→Σ, α→Α …) — no per-key shiftLabel — and the accented (tonos/dialytika)
// vowels ride long-press.
private val greekRows = listOf(
    listOf(
        Key("ς"), Key("ε", longPress = listOf("έ")), Key("ρ"), Key("τ"),
        Key("υ", longPress = listOf("ύ", "ϋ", "ΰ")), Key("θ"),
        Key("ι", longPress = listOf("ί", "ϊ", "ΐ")), Key("ο", longPress = listOf("ό")),
        Key("π"),
    ),
    listOf(
        Key("α", longPress = listOf("ά")), Key("σ"), Key("δ"), Key("φ"), Key("γ"),
        Key("η", longPress = listOf("ή")), Key("ξ"), Key("κ"), Key("λ"),
    ),
    listOf(
        Key("⇧", action = KeyAction.Shift, width = 1.5f),
        Key("ζ"), Key("χ"), Key("ψ"), Key("ω", longPress = listOf("ώ")),
        Key("β"), Key("ν"), Key("μ"),
        Key("⌫", action = KeyAction.Delete, width = 1.5f),
    ),
    bottomRow(),
)

// Hebrew letters in visual (left-to-right) key order; the field renders the
// committed text right-to-left, so the grid itself is not mirrored (as Arabic).
// The five final forms (ן ם ך ף ץ) sit where a Hebrew keyboard places them.
private val hebrewRows = listOf(
    listOf(
        Key("ק"), Key("ר"), Key("א"), Key("ט"), Key("ו"),
        Key("ן"), Key("ם"), Key("פ"),
    ),
    listOf(
        Key("ש"), Key("ד"), Key("ג"), Key("כ"), Key("ע"), Key("י"),
        Key("ח"), Key("ל"), Key("ך"), Key("ף"),
    ),
    listOf(
        Key("⇧", action = KeyAction.Shift, width = 1.5f),
        Key("ז"), Key("ס"), Key("ב"), Key("ה"), Key("נ"), Key("מ"),
        Key("צ"), Key("ת"), Key("ץ"),
        Key("⌫", action = KeyAction.Delete, width = 1.5f),
    ),
    bottomRow(),
)

// Hindi (Devanagari), the standard InScript keymap adapted to the 10-column
// phone grid: vowel signs (मात्रा) and consonants on the base plane, independent
// vowels and aspirates on shift — the arrangement InScript typists know. Rare
// desktop-number-row items move to long-press (ञ on ज, the nukta forms on ड).
// The Indic-cluster composer deletes a full consonant+sign cluster per delete.
private val hindiRows = listOf(
    listOf(
        Key("ौ", shiftLabel = "औ"), Key("ै", shiftLabel = "ऐ"), Key("ा", shiftLabel = "आ"),
        Key("ी", shiftLabel = "ई"), Key("ू", shiftLabel = "ऊ"), Key("ब", shiftLabel = "भ"),
        Key("ह", shiftLabel = "ङ"), Key("ग", shiftLabel = "घ"), Key("द", shiftLabel = "ध"),
        Key("ज", shiftLabel = "झ", longPress = listOf("ञ")),
        Key("ड", shiftLabel = "ढ", longPress = listOf("ड़", "ढ़", "़")),
    ),
    listOf(
        Key("ो", shiftLabel = "ओ"), Key("े", shiftLabel = "ए"), Key("्", shiftLabel = "अ"),
        Key("ि", shiftLabel = "इ"), Key("ु", shiftLabel = "उ"), Key("प", shiftLabel = "फ"),
        Key("र", shiftLabel = "ऱ"), Key("क", shiftLabel = "ख"), Key("त", shiftLabel = "थ"),
        Key("च", shiftLabel = "छ"), Key("ट", shiftLabel = "ठ"),
    ),
    listOf(
        Key("⇧", action = KeyAction.Shift, width = 1.2f),
        Key("ृ", shiftLabel = "ऋ"), Key("ं", shiftLabel = "ँ", longPress = listOf("ः", "ॐ")),
        Key("म", shiftLabel = "ण"), Key("न", shiftLabel = "ऩ"), Key("व"),
        Key("ल", shiftLabel = "ळ"), Key("स", shiftLabel = "श"), Key("ष"), Key("य"),
        Key("⌫", action = KeyAction.Delete, width = 1.3f),
    ),
    bottomRow(),
)

private val symbolRows = listOf(
    // This row is also what the optional number row shows over the letter
    // layer, so its long presses are the only symbols reachable there without
    // a layer switch. They follow one rule per key rather than a hand-picked
    // pile, so nothing has to be learnt digit by digit:
    //
    //   1. the character a physical US keyboard puts on Shift+digit — ! @ # $
    //      % ^ & * ( ) — which is where the fingers already expect them, and
    //      is how percent became typable from the number row at all;
    //   2. the superscript and subscript of that digit, all ten of each;
    //   3. every vulgar fraction with that digit as the *denominator*, so
    //      "hold 4 for quarters, hold 8 for eighths" holds for the whole row.
    //
    // What was here before came from AOSP: superscripts on 1-4 only, no
    // subscripts, fractions sorted by numerator so 1 carried ½ ⅓ ¼ and 5-9
    // carried nothing, and a superscript n on 0 that answers no question
    // anyone asks of a number row.
    listOf(
        Key("1", longPress = listOf("!", "¹", "₁")),
        Key("2", longPress = listOf("@", "²", "₂", "½")),
        Key("3", longPress = listOf("#", "³", "₃", "⅓", "⅔")),
        Key("4", longPress = listOf("$", "⁴", "₄", "¼", "¾")),
        Key("5", longPress = listOf("%", "⁵", "₅", "⅕", "⅖", "⅗", "⅘")),
        Key("6", longPress = listOf("^", "⁶", "₆", "⅙", "⅚")),
        Key("7", longPress = listOf("&", "⁷", "₇", "⅐")),
        Key("8", longPress = listOf("*", "⁸", "₈", "⅛", "⅜", "⅝", "⅞")),
        Key("9", longPress = listOf("(", "⁹", "₉", "⅑")),
        Key("0", longPress = listOf(")", "⁰", "₀", "⅒", "∅")),
    ),
    listOf(
        Key("@"), Key("#", longPress = listOf("№")),
        Key("$", longPress = listOf("৳", "€", "£", "¥", "₹", "₿")),
        // Percent sat two layers deep (?123 → =\< → %), which is one switch
        // too many for a character that belongs next to the digits. It takes
        // the underscore's slot here — the arrangement Gboard ships — and the
        // underscore drops onto the dash, where the other dash-shaped
        // characters already live.
        Key("%", longPress = listOf("‰")), Key("&"),
        Key("-", longPress = listOf("_", "–", "—", "·")),
        Key("+", longPress = listOf("±")), Key("(", longPress = listOf("[", "{", "<")),
        Key(")", longPress = listOf("]", "}", ">")), Key("/", longPress = listOf("\\", "÷")),
    ),
    listOf(
        Key("=\\<", action = KeyAction.Symbols, width = 1.5f),
        Key("*", longPress = listOf("†", "★", "×")),
        Key("\"", longPress = listOf("“", "”", "„", "«", "»")),
        Key("'", longPress = listOf("‘", "’", "‚", "‹", "›")), Key(":"), Key(";"),
        Key("!", longPress = listOf("¡")), Key("?", longPress = listOf("¿", "‽")),
        Key("⌫", action = KeyAction.Delete, width = 1.5f),
    ),
    bottomRow(symbols = true),
)

private val symbolsShiftedRows = listOf(
    listOf(
        Key("~"), Key("`"), Key("|"), Key("•", longPress = listOf("◦", "‣")), Key("√"),
        Key("π", longPress = listOf("Π", "℮")), Key("÷"), Key("×"),
        Key("¶", longPress = listOf("§")),
        Key("=", longPress = listOf("≠", "≈", "≤", "≥", "∆")),
    ),
    listOf(
        Key("৳"), Key("€"), Key("£", longPress = listOf("₺")), Key("¥"), Key("₹"),
        Key("^", longPress = listOf("↑", "↓", "←", "→")), Key("°", longPress = listOf("′", "″")),
        Key("{"), Key("}"), Key("\\"),
        // Dead-key accents: tap one, then a letter, and the pair fuses (´ then
        // e → é). The label shows the accent on a dotted circle; the output is
        // the bare combining mark, which is what marks a key as a dead key.
        Key(
            label = "\u25CC\u0301",
            output = "\u0301",
            longPress = listOf(
                "\u0300", "\u0302", "\u0308", "\u0303", "\u030C",
                "\u0304", "\u030A", "\u0327", "\u0328", "\u030B",
            ),
),
    ),
    listOf(
        Key("?123", action = KeyAction.Symbols, width = 1.5f),
        // Percent moved up to the first symbol layer; the underscore it
        // displaced takes the slot it left behind.
        Key("_"), Key("©"), Key("®"), Key("™"),
        Key("✓", longPress = listOf("✔", "✗", "☆", "★")), Key("[", longPress = listOf("{")),
        Key("]", longPress = listOf("}")),
        Key("⌫", action = KeyAction.Delete, width = 1.5f),
    ),
    bottomRow(symbols = true),
)

/**
 * 4-column keypad grid shared by the numeric field kinds: digits on the left 3
 * columns (0 under 8, phone-pad style), delete/enter anchoring the right column,
 * and the field-specific characters filling the rest.
 */
private fun numpad(
    rightMid: Key,
    rightLow: Key,
    bottomLeft: Key,
    bottomRight: Key,
    zero: Key = Key("0"),
    rightTop: Key = Key("⌫", action = KeyAction.Delete),
) = listOf(
    listOf(Key("1"), Key("2"), Key("3"), rightTop),
    listOf(Key("4"), Key("5"), Key("6"), rightMid),
    listOf(Key("7"), Key("8"), Key("9"), rightLow),
    listOf(bottomLeft, zero, bottomRight, Key("⏎", action = KeyAction.Enter)),
)

/** A keypad space key: blank label so no language name is drawn on it. */
private fun padSpace() = Key("", action = KeyAction.Space)

/**
 * TYPE_CLASS_NUMBER. The minus and decimal keys are always present — fields that
 * lack the signed/decimal flags filter them out themselves, and showing them
 * keeps the pad stable across the many apps that forget to set the flags.
 */
private val numberRows = numpad(
    // Space and backspace trade places relative to the other keypads:
    // deleting is the frequent correction while typing a number, so it sits
    // on the low-right slot under the thumb, and space takes the top.
    rightTop = padSpace(),
    rightMid = Key("-", longPress = listOf("+", "%", "*", "/")),
    rightLow = Key("⌫", action = KeyAction.Delete),
    bottomLeft = Key(".", longPress = listOf(":")),
    bottomRight = Key(","),
)

/** TYPE_CLASS_PHONE: dial pad with pause (,) and wait (;) on long press. */
private val phoneRows = numpad(
    rightMid = Key("-", longPress = listOf("(", ")", "/", ".")),
    rightLow = padSpace(),
    bottomLeft = Key("*", longPress = listOf(",")),
    bottomRight = Key("#", longPress = listOf(";")),
    zero = Key("0", longPress = listOf("+")),
)

/** TYPE_DATETIME_VARIATION_DATE: separators for 20/07/2026, 2026-07-20, 20.7.26. */
private val dateRows = numpad(
    rightMid = Key("/", longPress = listOf(":")),
    rightLow = Key("-"),
    bottomLeft = Key("."),
    bottomRight = Key(","),
)

/** TYPE_DATETIME_VARIATION_TIME: colon-led (17:45), space for AM/PM-ish suffixes. */
private val timeRows = numpad(
    rightMid = Key(":", longPress = listOf(".")),
    rightLow = padSpace(),
    bottomLeft = Key("."),
    bottomRight = Key("-"),
)

/** TYPE_DATETIME_VARIATION_NORMAL: date and time separators together. */
private val dateTimeRows = numpad(
    rightMid = Key("/", longPress = listOf(".")),
    rightLow = Key(":"),
    bottomLeft = Key("-"),
    bottomRight = padSpace(),
)

/**
 * A key that injects a raw key code. Written with the numeric code rather than
 * `KeyEvent.KEYCODE_*` so this file stays free of an Android import — it is
 * pure data, and the unit tests read it without a device.
 */
private fun fnKey(label: String, code: Int) = Key(label, action = KeyAction.SendKey(code))
