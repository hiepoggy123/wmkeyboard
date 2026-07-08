package com.wasimaster.wmkeyboard.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle

/**
 * Colours the JSON edited on-screen. Purely a recolour: every character is kept
 * in place and the length never changes, so the mapping stays the identity and
 * the text cursor lands where the user tapped. Works on whatever is typed,
 * valid JSON or not — it tokenises leniently and never throws.
 *
 * No extra dependency: it is a hand-rolled tokeniser feeding an AnnotatedString,
 * so both build flavors get it.
 */

/** The four span colours the highlighter paints, derived from the app theme. */
private data class JsonPalette(
    val key: Color,
    val string: Color,
    val number: Color,
    val keyword: Color,
    val punctuation: Color,
)

/** A JSON syntax highlighter bound to the current theme's colours. */
@Composable
internal fun rememberJsonSyntaxHighlighter(): VisualTransformation {
    val scheme = MaterialTheme.colorScheme
    val palette = JsonPalette(
        key = scheme.primary,
        string = scheme.tertiary,
        number = scheme.secondary,
        keyword = scheme.error,
        punctuation = scheme.onSurfaceVariant,
    )
    return remember(palette) { JsonSyntaxTransformation(palette) }
}

private class JsonSyntaxTransformation(private val palette: JsonPalette) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlightJson(text.text, palette), OffsetMapping.Identity)
}

/**
 * Walks [source] once, emitting a coloured run per token. Strings are told apart
 * from the keys that name them by peeking past trailing whitespace for a colon —
 * that is the only place a string acts as a key in JSON.
 */
private fun highlightJson(source: String, palette: JsonPalette): AnnotatedString =
    AnnotatedString.Builder(source.length).apply {
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '"' -> {
                    val end = stringEnd(source, i)
                    val isKey = colonFollows(source, end)
                    withStyle(SpanStyle(color = if (isKey) palette.key else palette.string)) {
                        append(source.substring(i, end))
                    }
                    i = end
                }
                c == '-' || c.isDigit() -> {
                    val end = numberEnd(source, i)
                    withStyle(SpanStyle(color = palette.number)) { append(source.substring(i, end)) }
                    i = end
                }
                atKeyword(source, i, "true") -> { keyword(palette, "true"); i += 4 }
                atKeyword(source, i, "false") -> { keyword(palette, "false"); i += 5 }
                atKeyword(source, i, "null") -> { keyword(palette, "null"); i += 4 }
                c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',' -> {
                    withStyle(SpanStyle(color = palette.punctuation)) { append(c) }
                    i++
                }
                else -> { append(c); i++ }
            }
        }
    }.toAnnotatedString()

/** Appends a literal keyword in the keyword colour. */
private fun AnnotatedString.Builder.keyword(palette: JsonPalette, word: String) {
    withStyle(SpanStyle(color = palette.keyword)) { append(word) }
}

/** True if [word] sits at [at] as a whole token, not the prefix of a longer one. */
private fun atKeyword(source: String, at: Int, word: String): Boolean {
    if (!source.startsWith(word, at)) return false
    val after = at + word.length
    return after >= source.length || !source[after].let { it.isLetterOrDigit() || it == '_' }
}

/**
 * Index one past the closing quote of the string starting at [start] (which is
 * the opening quote). Honours backslash escapes; an unterminated string runs to
 * end of input so a half-typed line still colours cleanly.
 */
private fun stringEnd(source: String, start: Int): Int {
    var i = start + 1
    while (i < source.length) {
        when (source[i]) {
            '\\' -> i += 2
            '"' -> return i + 1
            else -> i++
        }
    }
    return source.length
}

/** Index one past the last character of the number token starting at [start]. */
private fun numberEnd(source: String, start: Int): Int {
    var i = start
    if (i < source.length && source[i] == '-') i++
    while (i < source.length && (source[i].isDigit() || source[i] in ".eE+-")) i++
    return i
}

/** True if the next non-whitespace character at or after [from] is a colon. */
private fun colonFollows(source: String, from: Int): Boolean {
    var i = from
    while (i < source.length && source[i].isWhitespace()) i++
    return i < source.length && source[i] == ':'
}
