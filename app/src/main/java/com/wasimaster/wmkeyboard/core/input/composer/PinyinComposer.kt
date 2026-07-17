package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Chinese Pinyin input: the roman buffer is toneless pinyin, shown as-is in the
 * composing region, and the strip offers Hanzi/word candidates from the shipped
 * pinyin→Hanzi table ([isConversion] + [candidates]). Tapping a candidate commits
 * the characters; with no match the raw pinyin commits, so the buffer never
 * traps the user.
 */
object PinyinComposer : Composer {

    override val isTransliterating: Boolean get() = true
    override val isConversion: Boolean get() = true

    /** The composing region shows the pinyin the user typed. */
    override fun composeBuffer(buffer: String): String = buffer.lowercase()

    override fun candidates(buffer: String): List<String> =
        CjkDictionaries.pinyin.candidates(buffer.lowercase())
}
