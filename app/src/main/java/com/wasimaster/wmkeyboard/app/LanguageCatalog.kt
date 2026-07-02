package com.wasimaster.wmkeyboard.app

import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts

/**
 * One selectable layout of a language, as presented by the settings app
 * and onboarding. [info] is the longer explanation behind the ⓘ button on
 * the settings screen.
 *
 * Identified by layout id rather than by [com.wasimaster.wmkeyboard.core.settings.InputMode]:
 * a mode is a language behaviour and several layouts can share one, so the
 * mode cannot name which grid the user picked.
 */
data class LayoutOption(
    val layoutId: String,
    val title: String,
    val subtitle: String,
    val info: String,
)

/** A language section on the Languages screen, with its layout choices. */
data class LanguageEntry(
    val name: String,
    val layouts: List<LayoutOption>,
)

/**
 * Every language the keyboard offers, in display order. The Languages
 * settings screen and the onboarding page both render from this list, so
 * adding a language (or another layout to one) is a single entry here plus
 * its [InputMode] and layout definition.
 */
val LanguageCatalog: List<LanguageEntry> = listOf(
    LanguageEntry(
        name = "English",
        layouts = listOf(
            LayoutOption(
                BuiltInLayouts.QWERTY_ID, "QWERTY", "Standard layout with suggestions",
                "Standard QWERTY layout with autocorrect, predictions and gesture typing.",
            ),
            LayoutOption(
                BuiltInLayouts.AZERTY_ID, "AZERTY", "French/Belgian layout",
                "The AZERTY arrangement used in France and Belgium: A/Q and Z/W " +
                    "swapped, M on the home row, French accents on long-press. Same " +
                    "English suggestions, autocorrect and gesture typing.",
            ),
            LayoutOption(
                BuiltInLayouts.DVORAK_ID, "Dvorak", "Vowels on the left home row",
                "The simplified Dvorak layout: vowels under the left hand, common " +
                    "consonants under the right. Same English suggestions, autocorrect " +
                    "and gesture typing.",
            ),
        ),
    ),
    LanguageEntry(
        name = "বাংলা · Bangla",
        layouts = listOf(
            LayoutOption(
                BuiltInLayouts.AVRO_ID, "Avro phonetic", "Type \"ami valo achi\", get আমি ভালো আছি",
                "Type Bengali phonetically with Latin letters; the transliteration happens " +
                    "live as you type, and the suggestion bar offers dictionary spellings.",
            ),
            LayoutOption(
                BuiltInLayouts.PROBHAT_ID, "প্রভাত (Probhat)", "Fixed layout, Linux style",
                "The fixed Probhat layout familiar from Linux: vowel signs on the home row, " +
                    "consonants by frequency, aspirates on shift.",
            ),
            LayoutOption(
                BuiltInLayouts.JATIYA_ID, "জাতীয় (National)", "Bangladesh standard fixed layout",
                "The National (Jatiya) fixed layout standardized in Bangladesh; the same " +
                    "arrangement used by Bijoy-style keyboards, with aspirates on shift and " +
                    "independent vowels on long-press.",
            ),
        ),
    ),
    LanguageEntry(
        name = "Français · French",
        layouts = listOf(
            LayoutOption(
                BuiltInLayouts.FRENCH_ID, "AZERTY", "French layout with accents",
                "The standard French AZERTY arrangement with é è ç à and other accents " +
                    "on long-press. No French dictionary is bundled yet, so the keyboard " +
                    "learns your words as you type instead of offering English corrections.",
            ),
        ),
    ),
    LanguageEntry(
        name = "Deutsch · German",
        layouts = listOf(
            LayoutOption(
                BuiltInLayouts.GERMAN_ID, "QWERTZ", "German layout with umlauts",
                "The standard German QWERTZ arrangement (Y and Z swapped) with ä ö ü ß " +
                    "on long-press. No German dictionary is bundled yet, so the keyboard " +
                    "learns your words as you type instead of offering English corrections.",
            ),
        ),
    ),
    LanguageEntry(
        name = "Español · Spanish",
        layouts = listOf(
            LayoutOption(
                BuiltInLayouts.SPANISH_ID, "QWERTY + Ñ", "Spanish layout with Ñ key",
                "QWERTY with the Ñ key on the home row, acute accents on long-press and " +
                    "¿ ¡ behind the ? and ! alternates. No Spanish dictionary is bundled " +
                    "yet, so the keyboard learns your words as you type instead of " +
                    "offering English corrections.",
            ),
        ),
    ),
    LanguageEntry(
        name = "한국어 · Korean",
        layouts = listOf(
            LayoutOption(
                BuiltInLayouts.KOREAN_ID, "2-beolsik (두벌식)", "Standard Korean layout",
                "The standard 2-set Korean layout; jamo compose into syllable blocks as " +
                    "you type (ㅎ + ㅏ + ㄴ → 한). Shift gives the tense consonants. No " +
                    "Korean dictionary is bundled.",
            ),
        ),
    ),
    LanguageEntry(
        name = "Русский · Russian",
        layouts = listOf(
            LayoutOption(
                BuiltInLayouts.RUSSIAN_ID, "ЙЦУКЕН", "Standard Russian layout",
                "The standard ЙЦУКЕН Cyrillic arrangement; shift uppercases, ё and ъ on " +
                    "long-press. No Russian dictionary is bundled yet.",
            ),
        ),
    ),
    LanguageEntry(
        name = "العربية · Arabic",
        layouts = listOf(
            LayoutOption(
                BuiltInLayouts.ARABIC_ID, "العربية", "Standard Arabic layout",
                "The standard Arabic letter arrangement; text is entered right-to-left. " +
                    "Hamza and alef forms on long-press. No Arabic dictionary is bundled yet.",
            ),
        ),
    ),
    LanguageEntry(
        name = "Ελληνικά · Greek",
        layouts = listOf(
            LayoutOption(
                BuiltInLayouts.GREEK_ID, "Ελληνικά", "Standard Greek layout",
                "The Greek national layout; shift uppercases, and accented vowels " +
                    "(ά έ ή ί ό ύ ώ) sit on long-press. No Greek dictionary is bundled yet.",
            ),
        ),
    ),
    LanguageEntry(
        name = "עברית · Hebrew",
        layouts = listOf(
            LayoutOption(
                BuiltInLayouts.HEBREW_ID, "עברית", "Standard Hebrew layout",
                "The standard Hebrew letter arrangement; text is entered right-to-left, " +
                    "with the final forms (ן ם ך ף ץ) on their own keys. No Hebrew " +
                    "dictionary is bundled yet.",
            ),
        ),
    ),
    LanguageEntry(
        name = "हिन्दी · Hindi",
        layouts = listOf(
            LayoutOption(
                BuiltInLayouts.HINDI_ID, "InScript", "Standard Devanagari layout",
                "The standard InScript Devanagari layout: vowel signs and consonants on " +
                    "the base keys, independent vowels and aspirates on shift. Backspace " +
                    "deletes a full cluster. No Hindi dictionary is bundled yet.",
            ),
        ),
    ),
)
