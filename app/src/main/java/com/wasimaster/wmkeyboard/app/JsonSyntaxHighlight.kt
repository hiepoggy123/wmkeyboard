package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
     * Indents the document, but keeps an object or an array on one line while
     * it fits inside [LINE_BUDGET].
     *
     * A printer that always breaks turns a three-item list of alternates into
     * five lines and a layout into thousands, which is harder to read rather
     * than easier. This one breaks a container only when its own single line
     * would be too long, so the shape of the document survives.
     */
    override fun format(source: String): String? = runCatching {
        buildString { render(Json.parseToJsonElement(source), 0, 0, this) }
    }.getOrNull()

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

/** How wide a line may get before its container is broken over several. */
private const val LINE_BUDGET = 100
private const val FORMAT_INDENT = "  "
private val OFFSET = Regex("offset (\\d+)")

private fun opensAt(source: String, at: Int): Boolean = source[at] == '{' || source[at] == '['

/**
 * Writes [element] into [out], on one line when it fits and over several when
 * it does not. [column] is how much of the line is already spoken for, which is
 * the indent plus, for a value in an object, the key in front of it.
 */
private fun render(element: JsonElement, depth: Int, column: Int, out: StringBuilder) {
    val flat = StringBuilder().also { compact(element, it) }
    if (column + flat.length <= LINE_BUDGET) {
        out.append(flat)
        return
    }
    when (element) {
        is JsonObject -> {
            out.append("{\n")
            element.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) out.append(",\n")
                val name = JsonPrimitive(key).toString()
                indent(out, depth + 1)
                out.append(name).append(": ")
                render(value, depth + 1, (depth + 1) * FORMAT_INDENT.length + name.length + 2, out)
            }
            out.append('\n')
            indent(out, depth)
            out.append('}')
        }
        is JsonArray -> {
            out.append("[\n")
            element.forEachIndexed { index, value ->
                if (index > 0) out.append(",\n")
                indent(out, depth + 1)
                render(value, depth + 1, (depth + 1) * FORMAT_INDENT.length, out)
            }
            out.append('\n')
            indent(out, depth)
            out.append(']')
        }
        // A single string longer than the budget has nowhere to break.
        else -> out.append(flat)
    }
}

private fun indent(out: StringBuilder, depth: Int) {
    repeat(depth) { out.append(FORMAT_INDENT) }
}

/**
 * [element] on one line. `JsonElement.toString()` would nearly do, but it packs
 * everything tight; a space after each colon and comma is what makes the short
 * line worth keeping.
 */
private fun compact(element: JsonElement, out: StringBuilder) {
    when (element) {
        is JsonObject -> {
            out.append('{')
            element.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) out.append(", ")
                out.append(JsonPrimitive(key).toString()).append(": ")
                compact(value, out)
            }
            out.append('}')
        }
        is JsonArray -> {
            out.append('[')
            element.forEachIndexed { index, value ->
                if (index > 0) out.append(", ")
                compact(value, out)
            }
            out.append(']')
        }
        else -> out.append(element.toString())
    }
}

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
