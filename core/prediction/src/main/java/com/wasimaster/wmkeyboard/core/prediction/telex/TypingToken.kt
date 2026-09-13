package com.wasimaster.wmkeyboard.core.prediction.telex

/**
 * Represents a single typing token in the input buffer,
 * explicitly distinguishing between tap and flick gestures.
 */
data class TypingToken(
    val char: Char,
    val isFlick: Boolean = false,
    val baseKey: Char? = null,
    val flickOutput: String? = null
)
