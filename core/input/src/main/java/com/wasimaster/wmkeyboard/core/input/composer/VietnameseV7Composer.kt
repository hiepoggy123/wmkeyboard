package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Vietnamese V7 shorthand input method:
 * Users type initial consonants + optional rhymes + tone numbers (e.g. "x0ch2" -> "xin chào").
 * Uses V7 GPT on-device model to offer full multi-word phrase candidates in the suggestion strip,
 * which can be committed as a whole unit like Pinyin.
 */
object VietnameseV7Composer : Composer {
    override val isTransliterating: Boolean get() = true
    override val isConversion: Boolean get() = true
    override val bufferDigits: Boolean get() = true
    override val digitsStartBuffer: Boolean get() = false
    override val readsLatinLetters: Boolean get() = true

    override fun composeBuffer(buffer: String): String = buffer

    override fun candidates(reading: String): List<String> {
        val prov = V7Dictionaries.provider ?: return emptyList()
        return prov.predict(reading)
    }

    override fun consumedFor(candidate: String, reading: String): Int = reading.length
}
