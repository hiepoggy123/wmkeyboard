package com.wasimaster.wmkeyboard.app

import com.wasimaster.wmkeyboard.core.settings.InputMode

/**
 * One selectable layout of a language, as presented by the settings app
 * and onboarding. [info] is the longer explanation behind the ⓘ button on
 * the settings screen.
 */
data class LayoutOption(
    val mode: InputMode,
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
                InputMode.ENGLISH, "QWERTY", "Standard layout with suggestions",
                "Standard QWERTY layout with autocorrect, predictions and gesture typing.",
            ),
            LayoutOption(
                InputMode.AZERTY, "AZERTY", "French/Belgian layout",
                "The AZERTY arrangement used in France and Belgium: A/Q and Z/W " +
                    "swapped, M on the home row, French accents on long-press. Same " +
                    "English suggestions, autocorrect and gesture typing.",
            ),
            LayoutOption(
                InputMode.DVORAK, "Dvorak", "Vowels on the left home row",
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
                InputMode.AVRO, "Avro phonetic", "Type \"ami valo achi\", get আমি ভালো আছি",
                "Type Bengali phonetically with Latin letters; the transliteration happens " +
                    "live as you type, and the suggestion bar offers dictionary spellings.",
            ),
            LayoutOption(
                InputMode.PROBHAT, "প্রভাত (Probhat)", "Fixed layout, Linux style",
                "The fixed Probhat layout familiar from Linux: vowel signs on the home row, " +
                    "consonants by frequency, aspirates on shift.",
            ),
            LayoutOption(
                InputMode.JATIYA, "জাতীয় (National)", "Bangladesh standard fixed layout",
                "The National (Jatiya) fixed layout standardized in Bangladesh; the same " +
                    "arrangement used by Bijoy-style keyboards, with aspirates on shift and " +
                    "independent vowels on long-press.",
            ),
        ),
    ),
)
