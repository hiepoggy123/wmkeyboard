package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CONTENT_PAD = 10.dp
private val FIELD_PAD = 10.dp
private val CARET_PAD = 24.dp
private val NUMBER_PAD = 10.dp

/** At most this many squiggles are drawn in one frame, however many a broken file has. */
private const val MAX_SQUIGGLES = 200

/**
 * Everything drawn under or over the text without touching its colours.
 *
 * All of it is drawn on the underlay rather than spanned into the text, for the
 * reason the bracket boxes always were: a span means rebuilding the coloured
 * document, and a find that recoloured the whole file on every letter of the
 * query, or a diagnostics pass that did it after every pause in typing, would
 * make the field fall behind the keyboard. Drawing also leaves the identity
 * offset mapping alone, which is what puts the caret where a finger lands.
 */
@Immutable
internal data class CodeDecorations(
    val matches: List<TextRange> = emptyList(),
    /** The index in [matches] of the one find is sitting on, or -1. */
    val activeMatch: Int = -1,
    val squiggles: List<CodeDiagnostic> = emptyList(),
    /** Line index to the worst severity on it; the number is drawn in that colour. */
    val gutterMarks: Map<Int, CodeSeverity> = emptyMap(),
) {
    companion object {
        val None = CodeDecorations()
    }
}

/** A code field is not prose: no capitals at line starts and no corrections. */
private val CodeKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Text,
)

/**
 * The editor field on its own: a monospaced field on a code background, with
 * numbered lines, the caret's line lit, matching brackets boxed, and whatever
 * [decorations] asks for drawn underneath. No toolbar, no status line and no
 * height of its own: [CodeEditor] caps it inside a scrolling screen, and the
 * plugin editor gives it the whole window.
 *
 * Long lines run off to the right and scroll rather than wrap, which is what
 * keeps one number against one line. [wrap] turns that off for a narrow screen,
 * and the numbers stay right either way: they are drawn at the y each line
 * actually landed at, from the field's own layout, rather than at a multiple of
 * an assumed line height.
 */
@Composable
internal fun CodeSurface(
    state: CodeEditorState,
    language: CodeLanguage,
    modifier: Modifier = Modifier,
    colors: CodeColors = rememberCodeColors(),
    lineStarts: List<Int> = remember(state.text) { lineStartOffsets(state.text) },
    decorations: CodeDecorations = CodeDecorations.None,
    wrap: Boolean = false,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 20.sp,
    bottomPad: Dp = CONTENT_PAD,
    extraKeys: (KeyEvent) -> Boolean = { false },
) {
    val density = LocalDensity.current
    // Held as the state object rather than read through it: the two draw
    // blocks below read `.value` inside the draw phase, so a new text layout
    // repaints the margin and the underlay without recomposing the editor.
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()

    val style = remember(colors, fontSize, lineHeight) {
        TextStyle(fontFamily = CodeFontFamily, fontSize = fontSize, lineHeight = lineHeight, color = colors.text)
    }
    val text = state.text
    val caret = state.value.selection.end.coerceIn(0, text.length)
    val caretLine = lineOf(lineStarts, caret)
    // Everything below is keyed on the text alone. Only `match` follows the
    // caret, and it walks a list of offsets rather than the document.
    val brackets = remember(text, language) { language.brackets(text) }
    val match = remember(brackets, caret) { language.matchingBracket(text, brackets, caret) }
    val coloured = remember(text, colors, language) { language.highlight(text, colors) }
    val painter = remember(coloured) { CodeHighlight(coloured) }

    // Wrapped text has nothing to the right, so a stale sideways scroll would
    // only hide the left margin.
    LaunchedEffect(wrap) { if (wrap) horizontal.scrollTo(0) }

    val measurer = rememberTextMeasurer(cacheSize = 64)
    val digits = lineStarts.size.toString().length
    val gutterWidth = remember(digits, style, density) {
        with(density) { measurer.measure(AnnotatedString("0".repeat(digits)), style).size.width.toDp() + 20.dp }
    }
    val charWidth = remember(style, density) {
        with(density) { measurer.measure(AnnotatedString("0"), style).size.width.toDp() }
    }
    val longest = remember(text) { text.lineSequence().maxOf { it.length } }

    val shape = RoundedCornerShape(12.dp)
    // A plain Box with its size reported back, rather than
    // BoxWithConstraints: that one subcomposes its content on every measure
    // pass, and this content is a text field holding the whole document.
    var frame by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.border, shape)
            .onSizeChanged { frame = it },
    ) {
        val viewport = frame.height
        val room = with(density) { frame.width.toDp() } - gutterWidth - FIELD_PAD - FIELD_PAD
        val contentWidth = maxOf(room, charWidth * longest + CARET_PAD)

        Box(Modifier.width(gutterWidth).fillMaxHeight().background(colors.gutter))
        Box(Modifier.offset(x = gutterWidth).width(1.dp).fillMaxHeight().background(colors.border))
        EditorUnderlay(
            layout = layout,
            caret = caret,
            match = match,
            decorations = decorations,
            textLeft = gutterWidth + FIELD_PAD,
            vertical = vertical,
            horizontal = horizontal,
            colors = colors,
        )

        // The margin is painted behind the row rather than laid out beside
        // it, so the row's height is the field's own and nothing has to
        // read the text layout during composition.
        Row(
            Modifier
                .verticalScroll(vertical)
                .padding(top = CONTENT_PAD, bottom = bottomPad)
                .drawBehind {
                    drawLineNumbers(
                        layout = layout.value,
                        lineStarts = lineStarts,
                        activeLine = caretLine,
                        marks = decorations.gutterMarks,
                        right = gutterWidth.toPx() - NUMBER_PAD.toPx(),
                        colors = colors,
                        style = style,
                        measurer = measurer,
                        scroll = vertical.value,
                        viewport = viewport,
                    )
                },
            verticalAlignment = Alignment.Top,
        ) {
            Spacer(Modifier.width(gutterWidth))
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = FIELD_PAD)
                    .horizontalScroll(horizontal, enabled = !wrap),
            ) {
                // Wrapping is the width, not a flag: a field as wide as its
                // longest line has nothing to wrap, and one as wide as the
                // viewport wraps everything. The legacy field has no
                // softWrap of its own.
                CodeField(state, style, colors, painter, if (wrap) room else contentWidth, extraKeys) {
                    layout.value = it
                }
            }
        }
    }
}

/** Paints the document that was coloured once per change, character for character. */
private class CodeHighlight(private val coloured: AnnotatedString) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(coloured, OffsetMapping.Identity)
}

/** The field itself. Split out to keep the editor's own tree readable. */
@Composable
private fun CodeField(
    state: CodeEditorState,
    style: TextStyle,
    colors: CodeColors,
    painter: VisualTransformation,
    width: Dp,
    extraKeys: (KeyEvent) -> Boolean,
    onLayout: (TextLayoutResult) -> Unit,
) {
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.handle, backgroundColor = colors.selection)
    }
    CompositionLocalProvider(LocalTextSelectionColors provides selection) {
        BasicTextField(
            value = state.value,
            onValueChange = state::edit,
            textStyle = style,
            cursorBrush = SolidColor(colors.caret),
            visualTransformation = painter,
            onTextLayout = onLayout,
            keyboardOptions = CodeKeyboardOptions,
            modifier = Modifier
                .width(width)
                .onPreviewKeyEvent { event -> extraKeys(event) || handleEditorKey(event, state) },
        )
    }
}

/** Tab, Shift+Tab and the undo pair, for a device with a hardware keyboard. */
private fun handleEditorKey(event: KeyEvent, state: CodeEditorState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val command = event.isCtrlPressed || event.isMetaPressed
    return when {
        event.key == Key.Tab -> { state.shiftLines(if (event.isShiftPressed) -1 else 1); true }
        command && event.key == Key.Z && event.isShiftPressed -> { state.redo(); true }
        command && event.key == Key.Z -> { state.undo(); true }
        command && event.key == Key.Y -> { state.redo(); true }
        else -> false
    }
}

/**
 * What sits under the text: the caret's line lit across the whole editor,
 * gutter included, find matches, a box around each half of the bracket pair,
 * and a squiggle under each diagnostic.
 */
@Composable
private fun BoxScope.EditorUnderlay(
    layout: State<TextLayoutResult?>,
    caret: Int,
    match: Pair<Int, Int>?,
    decorations: CodeDecorations,
    textLeft: Dp,
    vertical: ScrollState,
    horizontal: ScrollState,
    colors: CodeColors,
) {
    val density = LocalDensity.current
    // The layout and the scroll positions are read here, inside the draw block,
    // so a drag or a fresh layout repaints without recomposing anything.
    Canvas(Modifier.matchParentSize()) {
        val result = layout.value ?: return@Canvas
        val top = with(density) { CONTENT_PAD.toPx() } - vertical.value
        val left = with(density) { textLeft.toPx() } - horizontal.value
        val length = result.layoutInput.text.length
        if (caret <= length) {
            val line = result.getLineForOffset(caret)
            val y = result.getLineTop(line) + top
            val height = result.getLineBottom(line) - result.getLineTop(line)
            if (y + height > 0f && y < size.height) {
                drawRect(color = colors.activeLine, topLeft = Offset(0f, y), size = Size(size.width, height))
            }
        }
        drawMatches(result, decorations, left, top, colors)
        if (match != null) {
            for (offset in listOf(match.first, match.second)) {
                if (offset >= length) continue
                val box = result.getBoundingBox(offset)
                drawRect(
                    color = colors.bracketMatch,
                    topLeft = Offset(box.left + left, box.top + top),
                    size = Size(box.width, box.height),
                )
            }
        }
        drawSquiggles(result, decorations, left, top, colors, with(density) { 1.5.dp.toPx() })
    }
}

/**
 * Every find match that could be on screen. getPathForRange gives one path for
 * a range however many lines and wraps it crosses, so a multi-line hit needs no
 * arithmetic here.
 */
private fun DrawScope.drawMatches(
    result: TextLayoutResult,
    decorations: CodeDecorations,
    left: Float,
    top: Float,
    colors: CodeColors,
) {
    val length = result.layoutInput.text.length
    for ((index, hit) in decorations.matches.withIndex()) {
        val start = hit.min.coerceIn(0, length)
        val end = hit.max.coerceIn(start, length)
        if (end <= start) continue
        val firstTop = result.getLineTop(result.getLineForOffset(start)) + top
        if (firstTop > size.height) break
        if (result.getLineBottom(result.getLineForOffset(end)) + top < 0f) continue
        translate(left, top) {
            drawPath(
                path = result.getPathForRange(start, end),
                color = if (index == decorations.activeMatch) colors.findActive else colors.findMatch,
            )
        }
    }
}

/** A zigzag under each diagnostic, one per visual line so a wrapped one does not jump the gap. */
@Suppress("LongParameterList")
private fun DrawScope.drawSquiggles(
    result: TextLayoutResult,
    decorations: CodeDecorations,
    left: Float,
    top: Float,
    colors: CodeColors,
    stroke: Float,
) {
    val length = result.layoutInput.text.length
    for (diagnostic in decorations.squiggles.take(MAX_SQUIGGLES)) {
        val start = diagnostic.range.min.coerceIn(0, length)
        val end = diagnostic.range.max.coerceIn(start, length)
        val colour = when (diagnostic.severity) {
            CodeSeverity.ERROR -> colors.problem
            CodeSeverity.WARNING -> colors.warning
            CodeSeverity.INFO -> colors.gutterText
        }
        val firstLine = result.getLineForOffset(start)
        val lastLine = result.getLineForOffset(if (end > start) end - 1 else start)
        for (line in firstLine..lastLine) {
            val y = result.getLineBottom(line) - stroke + top
            if (y < 0f) continue
            if (y > size.height) return
            val from = maxOf(start, result.getLineStart(line))
            val to = minOf(end, result.getLineEnd(line))
            val x0 = result.getHorizontalPosition(from, true) + left
            var x1 = result.getHorizontalPosition(to, true) + left
            // A diagnostic at one offset, or on an empty stretch, still gets a mark.
            if (x1 - x0 < stroke * 4) x1 = x0 + stroke * 6
            drawSquiggle(x0, x1, y, colour, stroke)
        }
    }
}

private fun DrawScope.drawSquiggle(x0: Float, x1: Float, y: Float, color: Color, stroke: Float) {
    val step = stroke * 2f
    val path = Path()
    path.moveTo(x0, y)
    var x = x0
    var up = true
    while (x < x1) {
        x = minOf(x + step, x1)
        path.lineTo(x, if (up) y - step else y)
        up = !up
    }
    drawPath(path, color, style = Stroke(width = stroke))
}

/**
 * The line numbers, right-aligned at [right]. Only the ones the viewport can
 * show are measured and drawn: a long document would otherwise pay for hundreds
 * nobody is looking at.
 */
@Suppress("LongParameterList")
private fun DrawScope.drawLineNumbers(
    layout: TextLayoutResult?,
    lineStarts: List<Int>,
    activeLine: Int,
    marks: Map<Int, CodeSeverity>,
    right: Float,
    colors: CodeColors,
    style: TextStyle,
    measurer: TextMeasurer,
    scroll: Int,
    viewport: Int,
) {
    val result = layout ?: return
    val limit = result.layoutInput.text.length
    for (index in lineStarts.indices) {
        val start = lineStarts[index]
        if (start > limit) break
        val line = result.getLineForOffset(start)
        val top = result.getLineTop(line)
        if (top - scroll > viewport) break
        if (result.getLineBottom(line) - scroll < 0f) continue
        val mark = marks[index]
        val active = index == activeLine
        val colour = when {
            mark == CodeSeverity.ERROR -> colors.problem
            mark == CodeSeverity.WARNING -> colors.warning
            active -> colors.gutterActiveText
            else -> colors.gutterText
        }
        val number = measurer.measure(
            AnnotatedString((index + 1).toString()),
            style.copy(color = colour, fontWeight = if (active || mark != null) FontWeight.Medium else FontWeight.Normal),
        )
        drawText(number, topLeft = Offset(right - number.size.width, top))
    }
}
