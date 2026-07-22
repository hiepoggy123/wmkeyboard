package com.wasimaster.wmkeyboard.core.input.composer

import com.wasimaster.wmkeyboard.core.script.ComposerType
import com.wasimaster.wmkeyboard.core.script.ScriptDef
import com.wasimaster.wmkeyboard.core.script.ScriptId

/**
 * Turns keystrokes into committed text for scripts that need more than a 1:1
 * append. Generalises the gates that used to be hard-coded to Bengali
 * (`isPhonetic`, `isFixedBengali`) and the dead-key path behind one type, chosen
 * from a layout's script and optional composer override — so a new complex
 * script is a registry entry, not another branch in the service.
 */
interface Composer {

    /**
     * How many chars to remove from the end of [before] to delete one visual
     * unit: a whole grapheme cluster for complex scripts, a surrogate pair or a
     * single char otherwise.
     */
    fun deleteLength(before: CharSequence): Int = defaultDeleteLength(before)

    /**
     * A transliterator (Avro, Hangul): its composing buffer *is* the input
     * method, so it must run even in password fields and with the suggestion
     * strip off, where plain suggestion-composing does not.
     */
    val isTransliterating: Boolean get() = false

    /**
     * Specifically the Bengali phonetic transliterator (Avro): its commit and
     * suggestions route through the Bengali dictionary path. Other
     * transliterators (Hangul) compose but do not, so this stays false for them.
     */
    val isBengaliPhonetic: Boolean get() = false

    /**
     * A fixed complex-script layout (Probhat, and later Devanagari, Tamil …):
     * types script characters directly and shapes clusters / contextual vowel
     * forms. The registry-era replacement for `isFixedBengali`.
     */
    val isClusterShaping: Boolean get() = false

    /**
     * Whether digit keys feed the composing buffer instead of committing it and
     * typing the digit. Only Vietnamese VNI needs this — its tones and marks are
     * spelled with the digits 0–9, which the transducer consumes.
     */
    val bufferDigits: Boolean get() = false

    /**
     * A conversion IME (Chinese Pinyin, Japanese kana→kanji): the roman/kana
     * buffer maps to a *choice* of outputs shown in the suggestion strip, and the
     * user taps one to commit it — unlike a plain transliterator whose buffer has
     * a single deterministic rendering. When true, the strip shows [candidates]
     * instead of dictionary suggestions and a tap commits with no trailing space.
     */
    val isConversion: Boolean get() = false

    /**
     * The candidate conversions of [buffer] for a conversion IME, best first
     * (Pinyin → Hanzi words, kana → kanji). Empty for every non-conversion
     * composer. The composing region still shows [composeBuffer].
     */
    fun candidates(buffer: String): List<String> = emptyList()

    /**
     * How many chars of the input [buffer] the [chosen] candidate consumed —
     * the linchpin of prefix commit. Picking 你 for `nihao` consumes only the
     * `ni` (2), so a commit deletes those chars and re-converts the `hao` tail
     * instead of wiping the whole buffer. Whole-buffer composers (and the raw
     * fallback, where [chosen] is the reading itself) consume everything, so the
     * default returns [buffer]'s length.
     */
    fun consumedFor(buffer: String, chosen: String): Int = buffer.length

    /** A transliterator's buffer (roman, or jamo) rendered as script text. */
    fun composeBuffer(buffer: String): String = buffer

    /**
     * The form a just-typed character takes given the character before the
     * cursor — a Bengali vowel key becoming its kar / glide / independent form.
     * Identity for scripts without contextual forms.
     */
    fun contextualForm(text: String, before: Char?): String = text
}

/** One visual unit at the end of [before]: a surrogate pair, else one char. */
internal fun defaultDeleteLength(before: CharSequence): Int {
    if (before.isEmpty()) return 0
    val last = before.length - 1
    return if (last >= 1 && Character.isSurrogatePair(before[last - 1], before[last])) 2 else 1
}

/**
 * No special composing: Latin, Cyrillic, Greek. Dead-key accent fusion is a
 * separate service-level state machine over combining marks (see `DeadKeys`),
 * not a per-character transform, so `ComposerType.DEAD_KEY` maps here too.
 */
object NoComposer : Composer

/**
 * The [Composer] a layout uses, from its resolved [script] and [type]
 * (`LayoutSpec.composerType()`). Unknown/unbuilt composers degrade to
 * [NoComposer] so the layout still types.
 */
fun composerFor(script: ScriptDef, type: ComposerType): Composer = when (type) {
    ComposerType.NONE, ComposerType.DEAD_KEY -> NoComposer
    ComposerType.INDIC_CLUSTER -> IndicClusterComposer(script)
    ComposerType.TRANSLITERATE -> when (script.id) {
        ScriptId.BENGALI -> BengaliTransliterateComposer
        else -> NoComposer
    }
    ComposerType.HANGUL -> HangulComposer
    ComposerType.TELEX -> VietnameseTelexComposer
    ComposerType.VNI -> VietnameseVniComposer
    ComposerType.ROMAJI -> JapaneseComposer
    ComposerType.PINYIN -> PinyinComposer
    ComposerType.STROKE -> StrokeComposer
}
