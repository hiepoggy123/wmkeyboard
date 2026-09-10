package com.wasimaster.wmkeyboard.app

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextRange

/**
 * One change to a document: [range] replaced by [text], then [selection] set,
 * in the offsets of the document after the change.
 *
 * Every line command below returns one of these rather than a new document, and
 * [CodeEditorState.applyEdit] applies it as exactly one step of history. They are
 * plain functions of a string and a range, so a JVM test drives each directly.
 */
@Immutable
internal data class CodeTextEdit(val range: TextRange, val text: String, val selection: TextRange)

/** The document after this edit. */
internal fun CodeTextEdit.applyTo(document: String): String =
    document.substring(0, range.min) + text + document.substring(range.max)

/** The line holding [offset], newline excluded. */
internal fun lineRangeAt(text: String, offset: Int): TextRange {
    val at = offset.coerceIn(0, text.length)
    val start = if (at == 0) 0 else text.lastIndexOf('\n', at - 1) + 1
    val end = text.indexOf('\n', at).let { if (it < 0) text.length else it }
    return TextRange(start, end)
}

/** The offset line [line] starts at, clamped to the lines there are. */
internal fun offsetOfLine(text: String, line: Int): Int {
    val starts = lineStartOffsets(text)
    return starts[line.coerceIn(0, starts.size - 1)]
}

/**
 * The word at [offset], or the run of spaces there, or the one character there.
 * A caret just after a word selects that word, which is where a caret sits when a
 * word has just been typed.
 */
internal fun wordRangeAt(text: String, offset: Int): TextRange {
    val at = offset.coerceIn(0, text.length)
    val here = text.getOrNull(at)
    val before = text.getOrNull(at - 1)
    val test: ((Char) -> Boolean)? = when {
        here != null && isWordChar(here) -> ::isWordChar
        before != null && isWordChar(before) -> ::isWordChar
        here != null && isBlank(here) -> ::isBlank
        before != null && isBlank(before) -> ::isBlank
        else -> null
    }
    if (test == null) return if (here != null) TextRange(at, at + 1) else TextRange(at)
    var start = at
    while (start > 0 && test(text[start - 1])) start--
    var end = at
    while (end < text.length && test(text[end])) end++
    return TextRange(start, end)
}

/**
 * The caret moved by [delta] characters, or by [delta] words. A word step goes to
 * the far edge of the next word in that direction, the way every editor does.
 */
internal fun caretStep(text: String, offset: Int, delta: Int, byWord: Boolean): Int {
    if (!byWord) return (offset + delta).coerceIn(0, text.length)
    var at = offset.coerceIn(0, text.length)
    repeat(kotlin.math.abs(delta)) {
        if (delta > 0) {
            while (at < text.length && !isWordChar(text[at])) at++
            while (at < text.length && isWordChar(text[at])) at++
        } else {
            while (at > 0 && !isWordChar(text[at - 1])) at--
            while (at > 0 && isWordChar(text[at - 1])) at--
        }
    }
    return at
}

/**
 * Comments out every line [selection] touches with [marker], or comments them
 * back in when every one of them already starts with it. The marker goes in at
 * the block's shallowest indent, so a commented block stays lined up, and blank
 * lines are left alone either way. The selection keeps covering the same text.
 */
internal fun toggleLineComment(text: String, selection: TextRange, marker: String): CodeTextEdit {
    val block = blockOf(text, selection)
    val lines = text.substring(block.min, block.max).split('\n')
    val written = lines.filter { it.isNotBlank() }
    val uncomment = written.isNotEmpty() && written.all { it.substring(leading(it)).startsWith(marker) }
    val indent = if (written.isEmpty()) lines.minOf { leading(it) } else written.minOf { leading(it) }
    val changes = lines.map { line ->
        when {
            line.isBlank() && written.isNotEmpty() -> LineChange(line, 0, 0)
            uncomment -> {
                val at = leading(line)
                val after = at + marker.length
                val removed = marker.length + if (line.getOrNull(after) == ' ') 1 else 0
                LineChange(line.substring(0, at) + line.substring(at + removed), at, -removed)
            }
            else -> LineChange(line.substring(0, indent) + marker + " " + line.substring(indent), indent, marker.length + 1)
        }
    }
    val replaced = changes.joinToString("\n") { it.text }
    fun map(offset: Int): Int {
        if (offset > block.max) return offset + replaced.length - (block.max - block.min)
        var oldStart = block.min
        var newStart = block.min
        for ((index, change) in changes.withIndex()) {
            val oldLength = lines[index].length
            if (offset <= oldStart + oldLength || index == changes.lastIndex) {
                val column = offset - oldStart
                val moved = when {
                    change.delta >= 0 -> if (column >= change.at) column + change.delta else column
                    column <= change.at -> column
                    else -> maxOf(change.at, column + change.delta)
                }
                return newStart + moved
            }
            oldStart += oldLength + 1
            newStart += change.text.length + 1
        }
        return offset
    }
    return CodeTextEdit(block, replaced, TextRange(map(selection.start), map(selection.end)))
}

/** Copies every line [selection] touches in below itself. The caret lands on the copy, at the same column. */
internal fun duplicateLines(text: String, selection: TextRange): CodeTextEdit {
    val block = blockOf(text, selection)
    val inserted = "\n" + text.substring(block.min, block.max)
    val shift = inserted.length
    return CodeTextEdit(TextRange(block.max), inserted, TextRange(selection.start + shift, selection.end + shift))
}

/**
 * Swaps the lines [selection] touches with the line above ([delta] < 0) or below.
 * Null at the top or bottom of the document, so the command can grey out rather
 * than do nothing.
 */
internal fun moveLines(text: String, selection: TextRange, delta: Int): CodeTextEdit? {
    val block = blockOf(text, selection)
    val moving = text.substring(block.min, block.max)
    return if (delta < 0) {
        if (block.min == 0) return null
        val above = lineRangeAt(text, block.min - 1)
        val shift = -(above.length + 1)
        CodeTextEdit(
            TextRange(above.min, block.max),
            moving + "\n" + text.substring(above.min, above.max),
            TextRange(selection.start + shift, selection.end + shift),
        )
    } else {
        if (block.max >= text.length) return null
        val below = lineRangeAt(text, block.max + 1)
        val shift = below.length + 1
        CodeTextEdit(
            TextRange(block.min, below.max),
            text.substring(below.min, below.max) + "\n" + moving,
            TextRange(selection.start + shift, selection.end + shift),
        )
    }
}

/**
 * Several non-overlapping replacements as one edit spanning all of them, so a
 * rename or a replace-all is one step of history. Null when [changes] is empty or
 * two of them overlap.
 */
internal fun mergeEdits(text: String, changes: List<Pair<TextRange, String>>, selection: TextRange): CodeTextEdit? {
    if (changes.isEmpty()) return null
    val sorted = changes.sortedBy { it.first.min }
    val start = sorted.first().first.min
    val end = sorted.last().first.max
    val middle = StringBuilder()
    var cursor = start
    for ((range, replacement) in sorted) {
        if (range.min < cursor) return null
        middle.append(text, cursor, range.min).append(replacement)
        cursor = range.max
    }
    return CodeTextEdit(TextRange(start, end), middle.toString(), selection)
}

/** What happened to one line: its new text, the column the change is at, and how much longer it got. */
private class LineChange(val text: String, val at: Int, val delta: Int)

/**
 * The whole lines [selection] touches, newline excluded. A selection that ends
 * at the very start of a line, as dragging down to the next line does, leaves
 * that line out.
 */
private fun blockOf(text: String, selection: TextRange): TextRange {
    val first = lineRangeAt(text, selection.min).min
    val lastAnchor = if (selection.max > selection.min && text.getOrNull(selection.max - 1) == '\n') {
        selection.max - 1
    } else {
        selection.max
    }
    return TextRange(first, lineRangeAt(text, lastAnchor).max)
}

private fun leading(line: String): Int {
    var index = 0
    while (index < line.length && (line[index] == ' ' || line[index] == '\t')) index++
    return index
}

private fun isWordChar(character: Char): Boolean = character.isLetterOrDigit() || character == '_'

private fun isBlank(character: Char): Boolean = character == ' ' || character == '\t'
