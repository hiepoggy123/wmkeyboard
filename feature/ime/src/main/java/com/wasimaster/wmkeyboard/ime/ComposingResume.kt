package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.input.composer.Composer
import com.wasimaster.wmkeyboard.core.prediction.WordContext

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
 * if it had been typed there.
 *
 * A transliterator's buffer is its input spelling, not its output. Avro's is the
 * roman source of Bengali text that cannot be reversed back into it, and
 * Hangul's is jamo, and a conversion IME's is a reading with a whole choice of
 * outputs behind it. Handing any of them Bengali or Hangul or Hanzi read back
 * out of the field would compose gibberish over the user's own words. Every
 * conversion composer sets [Composer.isTransliterating] too, so the one term
 * covers both.
 *
 * Everything that types its own script qualifies — Latin, Cyrillic, Greek,
 * Arabic, Hebrew, and the cluster-shaping layouts (Probhat, Jatiya, the fixed
 * Devanagari/Tamil/… ones). Bengali typed on Probhat is Bengali in the field and
 * Bengali in the buffer, which is the whole test; the shaping is a keypress
 * transform on the way in, not a different alphabet. A cluster-shaping layout
 * still commits every ordinary keystroke straight to the field — it composes
 * only while a resume has put a word in the buffer, which is what
 * `processTypedText`'s `composingMode` says — so this widens what a caret
 * landing on a word can do without changing what typing does.
 *
 * Deliberately not a language check. This was `language.isEnglish` for one
 * release, inherited from a glide flag that happened to be set on English and
 * nothing else; French, Russian and every language with a downloaded word list
 * complete from the same sources English does.
 */
internal fun composingResumable(composer: Composer, hasWordSources: Boolean): Boolean =
    hasWordSources && !composer.isTransliterating

/**
 * Whether [c] is part of the word the composing buffer holds.
 *
 * `Char.isLetter()` answers this only for the scripts that spell a word out of
 * letters alone. বাংলা ends in U+09BE VOWEL SIGN AA and হয়েছে in U+09C7 VOWEL
 * SIGN E — both combining marks — so a letters-only test says the caret sitting
 * after either of them is not at a word end at all, and on the words it does
 * accept it hands back only the tail past the last mark. Devanagari matras,
 * Tamil and Thai vowel signs, Arabic harakat and Hebrew niqqud spell words the
 * same way. Same rule the prediction stores already use for the same reason
 * ([WordContext.isWordChar]), plus the apostrophe that lives inside English
 * contractions.
 *
 * Digits are deliberately absent. They can *enter* a buffer as a number-row slip
 * ("as3" → "ase"), but a word span read back out of the field must not swallow
 * the "2" of "level 2".
 */
internal fun isComposingWordChar(c: Char): Boolean = WordContext.isWordChar(c) || c == '\''

/**
 * Whether the character *after* the caret continues the word behind it — the
 * test that keeps a resume off a word the caret is sitting in the middle of.
 *
 * Letters and digits, as before, and now combining marks: a caret between the
 * ল and the া of বাংলা has a word char on both sides of it, and resuming বাংল
 * there would arm a region over four fifths of a word and leave its vowel sign
 * stranded outside. The apostrophe is deliberately *not* here, so "don|'t" keeps
 * resuming "don" exactly as it does today.
 */
internal fun continuesWordAhead(c: Char): Boolean = c.isDigit() || WordContext.isWordChar(c)

/**
 * The word a caret sitting between [before] and [after] may re-arm as the
 * composing region, or null when there is none to take.
 *
 * A caret is at a word's end when a word character lies behind it and nothing
 * that continues a word lies ahead — the end of the text, or a separator, but
 * not the middle of a token. The word is then everything behind the caret that
 * belongs to it, which for বাংলা is all five characters and not the ল the last
 * kar hangs off.
 */
internal fun resumableWordAt(before: CharSequence?, after: CharSequence?): String? {
    if (before.isNullOrEmpty() || !isComposingWordChar(before.last())) return null
    if (!after.isNullOrEmpty() && continuesWordAhead(after[0])) return null
    return before.toString().takeLastWhile { isComposingWordChar(it) }.ifEmpty { null }
}
