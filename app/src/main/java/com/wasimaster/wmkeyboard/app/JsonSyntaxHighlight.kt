package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.wasimaster.wmkeyboard.core.util.JsonPretty
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * JSON, as [CodeEditor] needs it: coloured, tidied, paired up and parsed.
 *
 * The colouring is a hand-rolled tokeniser, so both build flavors get it with
 * no extra dependency. It is purely a recolour: every character stays in place
 * and the length never changes, which is what lets the editor keep the identity
 * offset mapping and put the caret where the user tapped. It works on whatever
 * is typed, valid JSON or not, and never throws.
 */
internal object JsonCode : CodeLanguage {

    /**
     * One pass, one allocation per token *range* rather than per token.
     *
     * The naive shape of this (an `AnnotatedString.Builder`, a `substring` per
     * token and a fresh `SpanStyle` per token) allocated four objects per token
     * and copied the whole document through the builder. On a real layout that
     * is thousands of allocations on every keystroke. Here the source string is
     * handed over whole and the styles are the same four instances throughout.
     *
     * Braces, colons and commas carry no span at all: they take the field's own
     * colour, which is what the palette's `text` is for. That halves the span
     * count, and the span count is what the text layout pays for.
     */
    override fun highlight(source: String, colors: CodeColors): AnnotatedString {
        val keyStyle = SpanStyle(color = colors.key)
        val stringStyle = SpanStyle(color = colors.string)
        val numberStyle = SpanStyle(color = colors.number)
        val keywordStyle = SpanStyle(color = colors.keyword)
        val spans = ArrayList<AnnotatedString.Range<SpanStyle>>()
        var index = 0
        while (index < source.length) {
            val character = source[index]
            when {
                character == '"' -> {
                    val end = stringEnd(source, index)
                    val style = if (colonFollows(source, end)) keyStyle else stringStyle
                    spans += AnnotatedString.Range(style, index, end)
                    index = end
                }
                character == '-' || character.isDigit() -> {
                    val end = numberEnd(source, index)
                    spans += AnnotatedString.Range(numberStyle, index, end)
                    index = end
                }
                atKeyword(source, index, "true") -> {
                    spans += AnnotatedString.Range(keywordStyle, index, index + 4)
                    index += 4
                }
                atKeyword(source, index, "false") -> {
                    spans += AnnotatedString.Range(keywordStyle, index, index + 5)
                    index += 5
                }
                atKeyword(source, index, "null") -> {
                    spans += AnnotatedString.Range(keywordStyle, index, index + 4)
                    index += 4
                }
                else -> index++
            }
        }
        return AnnotatedString(source, spans)
    }

    /**
     * Indents the document through the one printer everything a person reads
     * goes through, so pressing Format prints what reopening the screen prints.
     */
    override fun format(source: String): String? = JsonPretty.reprint(source)

    override fun problem(source: String): CodeProblem? {
        if (source.isBlank()) return null
        return try {
            Json.parseToJsonElement(source)
            null
        } catch (failure: SerializationException) {
            // The parser names the character it stopped on. That offset is what
            // the gutter marks and what the status line jumps to.
            CodeProblem(OFFSET.find(failure.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull())
        }
    }

    /**
     * The offsets of the curly and square brackets that structure the document,
     * with the ones inside strings left out. One forward pass, so a bracket in
     * a key name never pairs with a real one.
     */
    override fun brackets(source: String): List<Int> {
        val found = ArrayList<Int>()
        var index = 0
        while (index < source.length) {
            val character = source[index]
            when {
                character == '"' -> index = stringEnd(source, index)
                character == '{' || character == '}' || character == '[' || character == ']' -> {
                    found += index
                    index++
                }
                else -> index++
            }
        }
        return found
    }

    override fun matchingBracket(source: String, brackets: List<Int>, caret: Int): Pair<Int, Int>? {
        if (brackets.isEmpty()) return null
        // The bracket behind the caret wins, which is where a caret sits after
        // the user has typed one.
        val behind = if (caret > 0) brackets.binarySearch(caret - 1) else -1
        val start = when {
            behind >= 0 -> behind
            else -> brackets.binarySearch(caret).takeIf { it >= 0 } ?: return null
        }
        val at = brackets[start]
        if (at >= source.length) return null
        var depth = 0
        if (source[at] == '{' || source[at] == '[') {
            for (i in start until brackets.size) {
                depth += if (opensAt(source, brackets[i])) 1 else -1
                if (depth == 0) return at to brackets[i]
            }
        } else {
            for (i in start downTo 0) {
                depth += if (opensAt(source, brackets[i])) -1 else 1
                if (depth == 0) return brackets[i] to at
            }
        }
        return null
    }
}

private val OFFSET = Regex("offset (\\d+)")

private fun opensAt(source: String, at: Int): Boolean = source[at] == '{' || source[at] == '['

/**
 * Index one past the closing quote of the string starting at [start] (which is
 * the opening quote). Honours backslash escapes; an unterminated string runs to
 * end of input so a half-typed line still colours cleanly.
 */
private fun stringEnd(source: String, start: Int): Int {
    var index = start + 1
    while (index < source.length) {
        when (source[index]) {
            '\\' -> index += 2
            '"' -> return index + 1
            else -> index++
        }
    }
    return source.length
}

/** Index one past the last character of the number token starting at [start]. */
private fun numberEnd(source: String, start: Int): Int {
    var index = start
    if (index < source.length && source[index] == '-') index++
    while (index < source.length && (source[index].isDigit() || source[index] in ".eE+-")) index++
    return index
}

/** True if the next character that is not a space at or after [from] is a colon. */
private fun colonFollows(source: String, from: Int): Boolean {
    var index = from
    while (index < source.length && source[index].isWhitespace()) index++
    return index < source.length && source[index] == ':'
}

/** True if [word] sits at [at] as a whole token, not the start of a longer one. */
private fun atKeyword(source: String, at: Int, word: String): Boolean {
    if (!source.startsWith(word, at)) return false
    val after = at + word.length
    return after >= source.length || !source[after].let { it.isLetterOrDigit() || it == '_' }
}
