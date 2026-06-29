package com.wasimaster.wmkeyboard.core.layout

/**
 * The only trace the removed `InputMode` enum leaves behind: two lookup tables
 * keyed by its former value names, used once when reading data written before the
 * language registry existed.
 *
 * [LEGACY_MODE_LANG] converts a stored `LayoutSpec.baseMode` name (in a custom
 * layout's JSON) into a `langId`; [LEGACY_MODE_LAYOUT] converts a pre-registry
 * `input_mode`/`enabled_modes` preference into a built-in layout id. Both are
 * migration-only — nothing at runtime references the old mode names once a
 * layout has been migrated or a preference translated.
 *
 * Keyed by String rather than an enum on purpose: the enum is gone, and a name a
 * future build no longer maps simply falls through to a default instead of
 * failing to compile.
 */
internal val LEGACY_MODE_LANG: Map<String, String> = mapOf(
    "ENGLISH" to "en",
    "AZERTY" to "en",
    "DVORAK" to "en",
    "AVRO" to "bn",
    "PROBHAT" to "bn",
    "JATIYA" to "bn",
    "FRENCH" to "fr",
    "GERMAN" to "de",
    "SPANISH" to "es",
)

internal val LEGACY_MODE_LAYOUT: Map<String, String> = mapOf(
    "ENGLISH" to BuiltInLayouts.QWERTY_ID,
    "AZERTY" to BuiltInLayouts.AZERTY_ID,
    "DVORAK" to BuiltInLayouts.DVORAK_ID,
    "AVRO" to BuiltInLayouts.AVRO_ID,
    "PROBHAT" to BuiltInLayouts.PROBHAT_ID,
    "JATIYA" to BuiltInLayouts.JATIYA_ID,
    "FRENCH" to BuiltInLayouts.FRENCH_ID,
    "GERMAN" to BuiltInLayouts.GERMAN_ID,
    "SPANISH" to BuiltInLayouts.SPANISH_ID,
)
