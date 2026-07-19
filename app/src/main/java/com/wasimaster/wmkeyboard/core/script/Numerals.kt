package com.wasimaster.wmkeyboard.core.script

/**
 * The digit glyphs a language draws and types. Layout data always stores ASCII
 * `0`–`9`; the chosen system is applied late — at key-label draw time and at
 * commit time — so the layouts, the number-row auto-detection and the digit
 * long-press data all keep seeing plain ASCII.
 *
 * [digits] is the ten glyphs for `0`..`9` in order, or null for a passthrough
 * (Latin, and [AUTO] before it is resolved against a language). [AUTO] means
 * "follow the active language's own [LanguageDef.numeralSystem]"; every other
 * value forces that system regardless of language.
 */
enum class NumeralSystem(val digits: String?, val label: String) {
    // Labels stay short — they render as segmented buttons in settings.
    AUTO(null, "Auto"),
    LATIN(null, "0-9"),
    ARABIC_INDIC("٠١٢٣٤٥٦٧٨٩", "٠-٩"),
    PERSIAN("۰۱۲۳۴۵۶۷۸۹", "۰-۹"),
    BENGALI("০১২৩৪৫৬৭৮৯", "০-৯"),
    DEVANAGARI("०१२३४५६७८९", "०-९"),
}

/**
 * Where a non-Latin numeral system rewrites the digits that get committed.
 * Drawing is unaffected — the chosen glyphs always show on the keys; this only
 * governs what text is inserted.
 *
 * [TEXT_ONLY] (default) keeps ASCII in numeric/phone/date/time fields, which the
 * OS and apps expect to be machine-parseable, and types native digits elsewhere.
 * [EVERYWHERE] types native digits in those fields too. [DISPLAY_ONLY] always
 * commits ASCII, so the feature is purely cosmetic.
 */
enum class NumeralCommitScope(val label: String) {
    TEXT_ONLY("Text fields"),
    EVERYWHERE("Everywhere"),
    DISPLAY_ONLY("Display only"),
}

/**
 * The glyph set to apply for [setting] under [language], or null when no remap
 * is needed (Latin / an Auto language that stays Latin). [AUTO] defers to the
 * language's own default; anything else forces its own digits.
 */
fun resolveNumeralDigits(setting: NumeralSystem, language: LanguageDef): String? =
    if (setting == NumeralSystem.AUTO) language.numeralSystem.digits else setting.digits

/**
 * Rewrites the ASCII digits in [s] to [digits] (indexed `0`..`9`), leaving every
 * other character untouched. A null [digits] — the common Latin case — returns
 * [s] unchanged, so this is safe to call unconditionally on any label or output.
 */
fun mapDigits(s: String, digits: String?): String {
    if (digits == null || s.isEmpty()) return s
    var i = 0
    while (i < s.length) {
        if (s[i] in '0'..'9') {
            return buildString(s.length) {
                append(s, 0, i)
                for (j in i until s.length) {
                    val c = s[j]
                    append(if (c in '0'..'9') digits[c - '0'] else c)
                }
            }
        }
        i++
    }
    return s
}
