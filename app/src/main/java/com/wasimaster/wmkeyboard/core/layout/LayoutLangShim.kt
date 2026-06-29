package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.script.ComposerType
import com.wasimaster.wmkeyboard.core.settings.InputMode

/**
 * TEMPORARY bridge from the new [LayoutSpec.langId] back to the old [InputMode].
 *
 * Phase 1 replaced the stored `baseMode` with a language id, but the ~40 readers
 * that ask a spec for its `InputMode` (and the four `isEnglish`/`isLatinScript`/
 * `isFixedBengali`/`isPhonetic` helpers) still exist. This extension keeps them
 * compiling and behaving identically until Phase 3 flips every one of them to
 * read [LayoutSpec.language]/[LayoutSpec.script] directly — at which point this
 * whole file, [InputMode] and [com.wasimaster.wmkeyboard.core.settings.KeyboardLanguage]
 * are deleted together.
 *
 * Lossless for built-ins: their id maps 1:1 to the mode they used to store, so
 * `enabledModes` and the per-layout labels are byte-for-byte what they were. A
 * custom layout (no built-in id) is reconstructed from its language + composer,
 * which is all the four boolean readers ever look at.
 */
private val builtInIdToMode: Map<String, InputMode> = mapOf(
    BuiltInLayouts.QWERTY_ID to InputMode.ENGLISH,
    BuiltInLayouts.AZERTY_ID to InputMode.AZERTY,
    BuiltInLayouts.DVORAK_ID to InputMode.DVORAK,
    BuiltInLayouts.AVRO_ID to InputMode.AVRO,
    BuiltInLayouts.PROBHAT_ID to InputMode.PROBHAT,
    BuiltInLayouts.JATIYA_ID to InputMode.JATIYA,
    BuiltInLayouts.FRENCH_ID to InputMode.FRENCH,
    BuiltInLayouts.GERMAN_ID to InputMode.GERMAN,
    BuiltInLayouts.SPANISH_ID to InputMode.SPANISH,
)

val LayoutSpec.baseMode: InputMode
    get() = builtInIdToMode[id] ?: langComposerToMode(langId, composerType())

private fun langComposerToMode(langId: String, composer: ComposerType): InputMode = when (langId) {
    "en" -> InputMode.ENGLISH
    "fr" -> InputMode.FRENCH
    "de" -> InputMode.GERMAN
    "es" -> InputMode.SPANISH
    "bn" -> if (composer == ComposerType.TRANSLITERATE) InputMode.AVRO else InputMode.PROBHAT
    else -> InputMode.ENGLISH
}
