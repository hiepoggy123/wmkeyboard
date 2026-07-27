package com.wasimaster.wmkeyboard.core.script

/**
 * A writing system, named by its Unicode script. This is the axis the keyboard's
 * script-level behaviour keys off — text direction, whether the script has an
 * upper/lower case, which [ComposerType] runs, and which font it wants — so that
 * "type Serbian" or "type Persian" is data in [ScriptRegistry] rather than a new
 * branch in the service.
 *
 * The enum lists every script the keyboard intends to support so ids are stable
 * from the start; [ScriptRegistry] only carries a [ScriptDef] for the ones a
 * shipped language actually uses, and grows with the language set. Chinese and
 * Japanese are deliberately absent — they need conversion IMEs that are out of
 * scope here.
 */
enum class ScriptId {
    LATIN, CYRILLIC, GREEK, ARMENIAN, GEORGIAN,
    ARABIC, HEBREW, SYRIAC,
    DEVANAGARI, BENGALI, GURMUKHI, GUJARATI, ORIYA,
    TAMIL, TELUGU, KANNADA, MALAYALAM, SINHALA,
    THAI, LAO, KHMER, MYANMAR,
    HANGUL, ETHIOPIC, THAANA,
    JAPANESE, HAN,

    /**
     * The International Phonetic Alphabet. Not a language's writing system but a
     * transcription notation: Latin-derived glyphs plus the IPA Extensions block
     * and spacing/combining modifiers. Uncased, no dictionary, no composer — every
     * key commits its symbol as-is.
     */
    IPA,
    TIFINAGH, CHEROKEE,
    NKO, CANADIAN_ABORIGINAL_SYLLABICS,
    TIBETAN,
    OL_CHIKI, MEETEI_MAYEK,
}

/** Which way the script runs. Drives the suggestion strip's layout direction. */
enum class TextDirection { LTR, RTL }

/**
 * How keystrokes turn into committed text beyond a straight 1:1 append.
 *
 *  - [NONE]           — the key's output is committed as-is.
 *  - [DEAD_KEY]       — combining-accent composition (Latin/Greek/Cyrillic …),
 *                       handled by the script-agnostic `DeadKeys` NFC path.
 *  - [TRANSLITERATE]  — roman letters transliterated to another script as a unit
 *                       (Avro → Bengali); the composing buffer is the input method.
 *  - [INDIC_CLUSTER]  — Brahmic/SEA scripts whose grapheme clusters (conjuncts,
 *                       vowel signs) must be deleted and shaped as a unit.
 *  - [HANGUL]         — Korean jamo composed into syllable blocks.
 *
 * A single script can host more than one: Bengali defaults to [INDIC_CLUSTER]
 * (Probhat/Jatiya) but the Avro layout overrides to [TRANSLITERATE]. The default
 * lives here; the per-layout override lands in Phase 1 on `LayoutSpec`.
 */
enum class ComposerType {
    NONE, DEAD_KEY, TRANSLITERATE, INDIC_CLUSTER, HANGUL,

    /** Vietnamese Telex: roman keystrokes fold into toned Vietnamese letters. */
    TELEX,

    /** Vietnamese VNI: digit keys apply tones and letter marks. */
    VNI,

    /** Japanese: romaji composes to kana, with kana→kanji candidates. */
    ROMAJI,

    /** Chinese: pinyin buffer with Hanzi candidates chosen from the strip. */
    PINYIN,

    /** Chinese: 笔画 stroke-class buffer (一丨丿丶乙) with Hanzi candidates. */
    STROKE,

    /** Chinese: 九宫格 pinyin typed as ambiguous 9-key digit runs (64 → ni). */
    T9_PINYIN,

    /** Chinese: 注音 bopomofo symbols with tone marks, Taiwan's standard method. */
    ZHUYIN,

    /** Chinese: 倉頡 Cangjie radical decomposition, typed as the letters a-y. */
    CANGJIE,

    /** Chinese: 速成 Quick — a character's first and last Cangjie radical only. */
    CANGJIE_QUICK,

    /** Cantonese: 粵拼 Jyutping romanisation with optional tone digits 1-6. */
    JYUTPING,
}

/**
 * Which font family a script wants, so [com.wasimaster.wmkeyboard.ime.ui.KbTheme]
 * and the downloadable-font fallback can pick per script rather than the old
 * Latin-or-Bengali binary. [GENERIC] takes the system default.
 */
enum class FontHint { LATIN, BENGALI, DEVANAGARI, ARABIC, HEBREW, TAMIL, THAI, HANGUL, GENERIC }

/**
 * The behaviour of one [ScriptId]. Everything script-shaped the runtime needs is
 * an attribute here, so adding a language is a [LanguageDef] plus (if its script
 * is new) one row in [ScriptRegistry].
 *
 * [hasLetterCase] gates auto-capitalisation and shift-uppercasing — the thing the
 * old `isLatinScript`/`isFixedBengali` booleans conflated with "is Bengali".
 * [unicodeRange] is the script's main block, used to test whether a character
 * belongs to the script and to bound grapheme-cluster deletion.
 */
data class ScriptDef(
    val id: ScriptId,
    val direction: TextDirection = TextDirection.LTR,
    val hasLetterCase: Boolean = false,
    val composer: ComposerType = ComposerType.NONE,
    val fontHint: FontHint = FontHint.GENERIC,
    val unicodeRange: IntRange = IntRange.EMPTY,
)

/**
 * The scripts a shipped language uses. Seeded with the two the current five
 * languages need (Latin, Bengali); Phase 5 fills in the rest alongside the
 * ~60-language expansion. [get] never throws — an unregistered script falls back
 * to Latin so a language referencing a script this build lacks still renders.
 */
object ScriptRegistry {
    private val defs: Map<ScriptId, ScriptDef> = listOf(
        ScriptDef(
            id = ScriptId.LATIN,
            direction = TextDirection.LTR,
            hasLetterCase = true,
            composer = ComposerType.DEAD_KEY,
            fontHint = FontHint.LATIN,
            // Basic Latin letters through Latin Extended-B — covers the accented
            // letters the European layouts reach on long-press.
            unicodeRange = 0x0041..0x024F,
        ),
        ScriptDef(
            id = ScriptId.BENGALI,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.BENGALI,
            unicodeRange = 0x0980..0x09FF,
        ),
        ScriptDef(
            id = ScriptId.HANGUL,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.HANGUL,
            fontHint = FontHint.HANGUL,
            unicodeRange = 0xAC00..0xD7A3,
        ),
        ScriptDef(
            id = ScriptId.CYRILLIC,
            direction = TextDirection.LTR,
            hasLetterCase = true,
            composer = ComposerType.DEAD_KEY,
            fontHint = FontHint.LATIN,
            unicodeRange = 0x0400..0x04FF,
        ),
        ScriptDef(
            id = ScriptId.ARABIC,
            direction = TextDirection.RTL,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.ARABIC,
            unicodeRange = 0x0600..0x06FF,
        ),
        ScriptDef(
            id = ScriptId.GREEK,
            direction = TextDirection.LTR,
            hasLetterCase = true,
            composer = ComposerType.DEAD_KEY,
            // Greek rides the Latin faces, which carry the Greek block — as
            // Cyrillic does — until the per-script font map lands.
            fontHint = FontHint.LATIN,
            unicodeRange = 0x0370..0x03FF,
        ),
        ScriptDef(
            id = ScriptId.HEBREW,
            direction = TextDirection.RTL,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.HEBREW,
            unicodeRange = 0x0590..0x05FF,
        ),
        ScriptDef(
            id = ScriptId.SYRIAC,
            direction = TextDirection.RTL,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x0700..0x074F,
        ),
        ScriptDef(
            id = ScriptId.DEVANAGARI,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.DEVANAGARI,
            unicodeRange = 0x0900..0x097F,
        ),
        ScriptDef(
            id = ScriptId.GEORGIAN,
            direction = TextDirection.LTR,
            // Mkhedruli is unicameral; extra letters ride shiftLabel, not case.
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x10A0..0x10FF,
        ),
        // Armenian is bicameral (has upper/lower case), like Greek and Cyrillic,
        // but rides its own Unicode block; the system face supplies the glyphs.
        ScriptDef(
            id = ScriptId.ARMENIAN,
            direction = TextDirection.LTR,
            hasLetterCase = true,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x0530..0x058F,
        ),
        // Brahmic scripts: all uncased, all cluster-shaping (conjuncts joined by a
        // virama, vowel signs deleted with their base), so they share the generic
        // IndicClusterComposer keyed by their unicodeRange + virama (see viramaFor).
        ScriptDef(
            id = ScriptId.GURMUKHI,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x0A00..0x0A7F,
        ),
        ScriptDef(
            id = ScriptId.GUJARATI,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x0A80..0x0AFF,
        ),
        ScriptDef(
            id = ScriptId.ORIYA,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x0B00..0x0B7F,
        ),
        ScriptDef(
            id = ScriptId.TAMIL,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.TAMIL,
            unicodeRange = 0x0B80..0x0BFF,
        ),
        ScriptDef(
            id = ScriptId.TELUGU,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x0C00..0x0C7F,
        ),
        ScriptDef(
            id = ScriptId.KANNADA,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x0C80..0x0CFF,
        ),
        ScriptDef(
            id = ScriptId.MALAYALAM,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x0D00..0x0D7F,
        ),
        ScriptDef(
            id = ScriptId.SINHALA,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x0D80..0x0DFF,
        ),
        // Thai and Lao are alphasyllabaries but stack no conjuncts (no virama), so
        // they compose 1:1 and delete one code unit at a time — ComposerType.NONE.
        ScriptDef(
            id = ScriptId.THAI,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.THAI,
            unicodeRange = 0x0E00..0x0E7F,
        ),
        ScriptDef(
            id = ScriptId.LAO,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x0E80..0x0EFF,
        ),
        // Khmer (coeng) and Myanmar (virama/asat) DO stack, so they use the cluster
        // composer with their respective viramas (see viramaFor).
        ScriptDef(
            id = ScriptId.KHMER,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x1780..0x17FF,
        ),
        ScriptDef(
            id = ScriptId.MYANMAR,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x1000..0x109F,
        ),
        // Ethiopic (Amharic, Tigrinya …) is an abugida written left-to-right and
        // uncased. Its ~34 base consonants each have seven vowel orders; the
        // layout puts the base order on the key and the other six on long-press,
        // so it composes 1:1 with no special composer (system Noto face).
        ScriptDef(
            id = ScriptId.ETHIOPIC,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x1200..0x137F,
        ),
        // Thaana (Dhivehi) is written right-to-left; consonants carry vowel
        // diacritics (fili) typed after them, so like Arabic it composes 1:1 and
        // the field renders RTL from the visual-order grid.
        ScriptDef(
            id = ScriptId.THAANA,
            direction = TextDirection.RTL,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x0780..0x07BF,
        ),
        // Japanese and Chinese: the script default is a plain 1:1 append, and the
        // layouts override it (romaji→kana for ja, pinyin for zh). Their fonts are
        // resolved per script id (Noto Sans JP / SC), not the fontHint.
        ScriptDef(
            id = ScriptId.JAPANESE,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x3040..0x30FF,
        ),
        ScriptDef(
            id = ScriptId.HAN,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x4E00..0x9FFF,
        ),
        // IPA is uncased (a shift key would be inert) and composes 1:1 — each key
        // commits its phonetic symbol directly, combining diacritics included. It
        // rides the Latin faces, which carry the IPA Extensions block and the
        // spacing/combining modifiers, so it needs no dedicated font. The declared
        // range is the IPA Extensions block; the layout also reaches Latin,
        // spacing-modifier and combining-diacritic characters outside it.
        ScriptDef(
            id = ScriptId.IPA,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.LATIN,
            unicodeRange = 0x0250..0x02AF,
        ),
        ScriptDef(
            id = ScriptId.TIFINAGH,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x2D30..0x2D7F,
        ),
        ScriptDef(
            id = ScriptId.CHEROKEE,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x13A0..0x13FF,
        ),
        ScriptDef(
            id = ScriptId.NKO,
            direction = TextDirection.RTL,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x07C0..0x07FF,
        ),
        ScriptDef(
            id = ScriptId.CANADIAN_ABORIGINAL_SYLLABICS,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x1400..0x167F,
        ),
        ScriptDef(
            id = ScriptId.TIBETAN,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x0F00..0x0FFF,
        ),
        // Ol Chiki (Santali) is a true alphabet, not an abugida — no virama or
        // conjunct stacking, so it composes 1:1 like Thai/Lao rather than using
        // the cluster composer.
        ScriptDef(
            id = ScriptId.OL_CHIKI,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.NONE,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0x1C50..0x1C7F,
        ),
        // Meetei Mayek (Manipuri) is an abugida with a virama-like killer stroke
        // (Apun Iyek), so it rides the generic cluster composer like the other
        // Brahmic-family scripts. Its extension block (vowel signs, U+AAE0..AAF6)
        // falls outside unicodeRange's single contiguous span; that range is used
        // for cluster-deletion bounds checks and the common case (consonants,
        // U+ABC0..ABFF) is what matters there.
        ScriptDef(
            id = ScriptId.MEETEI_MAYEK,
            direction = TextDirection.LTR,
            hasLetterCase = false,
            composer = ComposerType.INDIC_CLUSTER,
            fontHint = FontHint.GENERIC,
            unicodeRange = 0xABC0..0xABFF,
        ),
    ).associateBy { it.id }

    val all: List<ScriptDef> get() = defs.values.toList()

    /** Whether a [ScriptDef] is actually registered (not the Latin fallback). */
    fun isRegistered(id: ScriptId): Boolean = id in defs

    operator fun get(id: ScriptId): ScriptDef = defs[id] ?: defs.getValue(ScriptId.LATIN)
}
