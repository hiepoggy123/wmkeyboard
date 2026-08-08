package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.input.composer.Composer

/**
 * The input-method half of the gate on re-arming the word a caret landed on as
 * the composing region, pulled out of `WMKeyboardService` so it can be checked
 * without an `InputConnection`. The service still owns the other half — what
 * else is on screen, which field this is, whether a panel or a dictation is
 * mid-commit.
 */

/**
 * Whether the language and layout now on screen can have a completed word
 * re-armed as the composing region at all.
 *
 * Two things have to hold. The buffer has to be able to say something: a
 * language with no bundled list, no imported one and nothing learned yet has no
 * completion or correction to offer, so the underline would only be in the way
 * ([hasWordSources] is `SuggestionEngine.hasWordSources`). And the buffer has to
 * *be* the text in the field, because that is the one thing a resume assumes —
 * it reads a word out of the editor and hands it to the composing machinery as
 * if it had been typed there:
 *
 *  - A transliterator's buffer is its input spelling, not its output. Avro's is
 *    the roman source of Bengali text that cannot be reversed back into it, and
 *    Hangul's is jamo, and a conversion IME's is a reading with a whole choice
 *    of outputs behind it. Handing any of them Bengali or Hangul or Hanzi read
 *    back out of the field would compose gibberish over the user's own words.
 *  - A cluster-shaping layout (Probhat, and the fixed Devanagari/Tamil layouts)
 *    never composes at all — it types its script straight into the field, so a
 *    region armed behind it is extended by nothing, and backspace would take
 *    the composing path and shed one code unit where a whole conjunct should
 *    go.
 *
 * Everything else — Latin, Cyrillic, Greek, Arabic, Hebrew — composes its own
 * script, so whatever the caret landed on is exactly what the buffer can hold.
 *
 * Deliberately not a language check. This was `language.isEnglish` for one
 * release, inherited from a glide flag that happened to be set on English and
 * nothing else; French, Russian and every language with a downloaded word list
 * complete from the same sources English does.
 */
internal fun composingResumable(composer: Composer, hasWordSources: Boolean): Boolean =
    hasWordSources && !composer.isTransliterating && !composer.isClusterShaping
