package com.wasimaster.wmkeyboard.core.voice

/**
 * Intelligent spacing rules for voice dictation.
 * Determines when leading or trailing spaces are needed based on the
 * character context immediately preceding and following the insertion point / selection.
 */
object VoiceSpacing {

    /**
     * Leading space is needed if text before cursor is non-whitespace (e.g. end of word)
     * AND text after cursor is NOT whitespace (e.g. end of line, or another word without a space between).
     * If text after cursor IS whitespace, the space after cursor already provides separation.
     */
    fun needsLeadingSpace(beforeChar: Char?, afterChar: Char?): Boolean =
        beforeChar != null && !beforeChar.isWhitespace() && (afterChar == null || !afterChar.isWhitespace())

    /**
     * Trailing space is needed if text after cursor is a letter or digit (e.g. start of word)
     * AND text before cursor is whitespace (or start of line).
     */
    fun needsTrailingSpace(beforeChar: Char?, afterChar: Char?): Boolean =
        afterChar != null && afterChar.isLetterOrDigit() && (beforeChar == null || beforeChar.isWhitespace())

    /**
     * Formats dictated [text] by prepending a leading space and/or appending a trailing space
     * according to [needsLeading] and [needsTrailing].
     */
    fun format(text: String, needsLeading: Boolean, needsTrailing: Boolean): String {
        if (text.isEmpty()) return text
        var result = text
        if (needsLeading && text.first().isLetterOrDigit()) {
            result = " $result"
        }
        if (needsTrailing && text.last().isLetterOrDigit()) {
            result = "$result "
        }
        return result
    }
}
