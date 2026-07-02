package com.wasimaster.wmkeyboard.core.script

import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts

/**
 * A language the keyboard offers, and everything language-level that used to hang
 * off the `InputMode`/`KeyboardLanguage` enums: the dictionary folder, the
 * dictation/hint locale, which layouts belong to it, and which script it writes.
 *
 * [id] is a stable BCP-47-ish key ("en", "bn", "fr", "sr-Cyrl" …). It doubles as
 * the on-disk name for the language's imported-dictionary folder, so it must not
 * change once shipped. [layoutIds] are the built-in (or asset) layouts grouped
 * under this language on the Languages screen.
 *
 * [bundledDictionary] is true only where the APK actually ships a word list
 * (English, Bengali). [gestureLexicon] is true only for English, whose glide
 * decoder is bound to the English lexicon; other languages type on the grid but
 * do not gesture until they gain a lexicon.
 */
data class LanguageDef(
    val id: String,
    val displayName: String,
    val englishName: String,
    val script: ScriptId,
    val localeTag: String,
    val layoutIds: List<String>,
    val bundledDictionary: Boolean = false,
    val gestureLexicon: Boolean = false,
) {
    /** English-language convenience, preserving the old `InputMode.isEnglish` reads. */
    val isEnglish: Boolean get() = id == "en"
}

/**
 * Every language the keyboard offers. Seeded with the current five; Phase 5
 * expands it toward ~60. Lookups are plain map reads — the exhaustive `when`s the
 * old enums forced (`InputMode.language`, `forMode`, `hintedMode`) collapse to
 * these.
 *
 * [byId] never throws: an id this build does not know (an asset or custom layout
 * from a newer version) resolves to a generic Latin language so the keyboard
 * still draws and types, mirroring how an unknown key action degrades.
 */
object LanguageRegistry {

    val all: List<LanguageDef> = listOf(
        LanguageDef(
            id = "en",
            displayName = "English",
            englishName = "English",
            script = ScriptId.LATIN,
            localeTag = "en-US",
            layoutIds = listOf(BuiltInLayouts.QWERTY_ID, BuiltInLayouts.AZERTY_ID, BuiltInLayouts.DVORAK_ID),
            bundledDictionary = true,
            gestureLexicon = true,
        ),
        LanguageDef(
            id = "bn",
            displayName = "বাংলা · Bangla",
            englishName = "Bangla",
            script = ScriptId.BENGALI,
            localeTag = "bn-BD",
            layoutIds = listOf(BuiltInLayouts.AVRO_ID, BuiltInLayouts.PROBHAT_ID, BuiltInLayouts.JATIYA_ID),
            bundledDictionary = true,
        ),
        LanguageDef(
            id = "fr",
            displayName = "Français · French",
            englishName = "French",
            script = ScriptId.LATIN,
            localeTag = "fr-FR",
            layoutIds = listOf(BuiltInLayouts.FRENCH_ID),
        ),
        LanguageDef(
            id = "de",
            displayName = "Deutsch · German",
            englishName = "German",
            script = ScriptId.LATIN,
            localeTag = "de-DE",
            layoutIds = listOf(BuiltInLayouts.GERMAN_ID),
        ),
        LanguageDef(
            id = "es",
            displayName = "Español · Spanish",
            englishName = "Spanish",
            script = ScriptId.LATIN,
            localeTag = "es-ES",
            layoutIds = listOf(BuiltInLayouts.SPANISH_ID),
        ),
        LanguageDef(
            id = "ko",
            displayName = "한국어 · Korean",
            englishName = "Korean",
            script = ScriptId.HANGUL,
            localeTag = "ko-KR",
            layoutIds = listOf(BuiltInLayouts.KOREAN_ID),
        ),
        LanguageDef(
            id = "ru",
            displayName = "Русский · Russian",
            englishName = "Russian",
            script = ScriptId.CYRILLIC,
            localeTag = "ru-RU",
            layoutIds = listOf(BuiltInLayouts.RUSSIAN_ID),
        ),
        LanguageDef(
            id = "ar",
            displayName = "العربية · Arabic",
            englishName = "Arabic",
            script = ScriptId.ARABIC,
            localeTag = "ar-SA",
            layoutIds = listOf(BuiltInLayouts.ARABIC_ID),
        ),
        LanguageDef(
            id = "el",
            displayName = "Ελληνικά · Greek",
            englishName = "Greek",
            script = ScriptId.GREEK,
            localeTag = "el-GR",
            layoutIds = listOf(BuiltInLayouts.GREEK_ID),
        ),
        LanguageDef(
            id = "he",
            displayName = "עברית · Hebrew",
            englishName = "Hebrew",
            script = ScriptId.HEBREW,
            localeTag = "he-IL",
            layoutIds = listOf(BuiltInLayouts.HEBREW_ID),
        ),
        LanguageDef(
            id = "hi",
            displayName = "हिन्दी · Hindi",
            englishName = "Hindi",
            script = ScriptId.DEVANAGARI,
            localeTag = "hi-IN",
            layoutIds = listOf(BuiltInLayouts.HINDI_ID),
        ),
    )

    /** The stand-in for an id this build does not recognise. Never surfaced in UI. */
    val GENERIC: LanguageDef = LanguageDef(
        id = "und",
        displayName = "Unknown",
        englishName = "Unknown",
        script = ScriptId.LATIN,
        localeTag = "und",
        layoutIds = emptyList(),
    )

    private val index: Map<String, LanguageDef> = all.associateBy { it.id }

    private val byLayout: Map<String, LanguageDef> = buildMap {
        for (lang in all) for (layoutId in lang.layoutIds) put(layoutId, lang)
    }

    fun byId(id: String): LanguageDef = index[id] ?: GENERIC

    /**
     * The language whose primary subtag matches a BCP-47 tag ("fr-FR" → French,
     * "en" → English), for `hintLocales` and OS subtype locales. Null when no
     * offered language matches — the caller decides the fallback.
     */
    fun byLocale(tag: String): LanguageDef? {
        val primary = tag.replace('_', '-').substringBefore('-').lowercase()
        if (primary.isEmpty()) return null
        return all.firstOrNull { it.id == primary || it.localeTag.substringBefore('-').lowercase() == primary }
    }

    /**
     * The language a built-in layout id belongs to. Custom/asset layouts carry
     * their own `langId` on the spec and should resolve through that instead;
     * this covers the built-in ids the UI lists.
     */
    fun languageOf(layoutId: String): LanguageDef = byLayout[layoutId] ?: GENERIC
}
