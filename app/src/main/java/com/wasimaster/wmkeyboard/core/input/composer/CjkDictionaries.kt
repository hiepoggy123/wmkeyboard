package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Holds the conversion tables the Chinese and Japanese composers read. They are
 * loaded from shipped TSV assets off the main thread by the service (the same
 * place it loads its word lists) and swapped in here; until then both are
 * [ConversionDictionary.EMPTY], so the composers still type — Pinyin commits the
 * raw pinyin and Japanese commits the kana — they just offer no character
 * candidates.
 */
object CjkDictionaries {
    @Volatile var pinyin: ConversionDictionary = ConversionDictionary.EMPTY
    @Volatile var japanese: ConversionDictionary = ConversionDictionary.EMPTY

    /** Stroke-order → Hanzi table for the 笔画 input method (downloadable pack). */
    @Volatile var stroke: StrokeDictionary = StrokeDictionary.EMPTY
}
