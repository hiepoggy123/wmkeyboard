package com.wasimaster.wmkeyboard.app

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * JSON, as [CodeEditor] needs it: coloured, tidied, paired up and parsed.
 *
 * The colouring is a hand-rolled tokeniser feeding an AnnotatedString, so both
 * build flavors get it with no extra dependency. It is purely a recolour: every
 * character stays in place and the length never changes, which is what lets the
 * editor keep the identity offset mapping and put the caret where the user
 * tapped. It works on whatever is typed, valid JSON or not, and never throws.
 */
internal object JsonCode : CodeLanguage {

    override fun highlight(source: String, colors: CodeColors, match: Pair<Int, Int>?): AnnotatedString =
        AnnotatedString.Builder(source.length).apply {
            var index = 0
            while (index < source.length) {
                val character = source[index]
                when {
                    character == '"' -> {
                        val end = stringEnd(source, index)
                        val isKey = colonFollows(source, end)
                        withStyle(SpanStyle(color = if (isKey) colors.key else colors.string)) {
                            append(source.substring(index, end))
                        }
                        index = end
                    }
                    character == '-' || character.isDigit() -> {
                        val end = numberEnd(source, index)
                        withStyle(SpanStyle(color = colors.number)) { append(source.substring(index, end)) }
                        index = end
                    }
                    atKeyword(source, index, "true") -> { keyword(colors, "true"); index += 4 }
                    atKeyword(source, index, "false") -> { keyword(colors, "false"); index += 5 }
                    atKeyword(source, index, "null") -> { keyword(colors, "null"); index += 4 }
                    character in STRUCTURAL -> {
                        val paired = match != null && (index == match.first || index == match.second)
                        withStyle(punctuationStyle(colors, paired)) { append(character) }
                        index++
                    }
                    else -> { append(character); index++ }
                }
            }
        }.toAnnotatedString()

    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    override fun format(source: String): String? = runCatching {
        pretty.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(source))
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

    override fun matchingBracket(source: String, caret: Int): Pair<Int, Int>? {
        val brackets = structuralBrackets(source)
        if (brackets.isEmpty()) return null
        // The bracket behind the caret wins, which is where a caret sits after
        // the user has typed one.
        val at = when {
            caret > 0 && brackets.contains(caret - 1) -> caret - 1
            brackets.contains(caret) -> caret
            else -> return null
        }
        val start = brackets.indexOf(at)
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

    /** Appends a literal keyword in the keyword colour. */
    private fun AnnotatedString.Builder.keyword(colors: CodeColors, word: String) {
        withStyle(SpanStyle(color = colors.keyword)) { append(word) }
    }

    private fun punctuationStyle(colors: CodeColors, paired: Boolean): SpanStyle = if (paired) {
        SpanStyle(color = colors.punctuation, background = colors.bracketMatch, fontWeight = FontWeight.Bold)
    } else {
        SpanStyle(color = colors.punctuation)
    }
}

private const val STRUCTURAL = "{}[]:,"
private val OFFSET = Regex("offset (\\d+)")

private fun opensAt(source: String, at: Int): Boolean = source[at] == '{' || source[at] == '['

/**
 * The offsets of the curly and square brackets that structure the document,
 * with the ones inside strings left out. One forward pass, so a bracket in a
 * key name never pairs with a real one.
 */
private fun structuralBrackets(source: String): List<Int> {
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
