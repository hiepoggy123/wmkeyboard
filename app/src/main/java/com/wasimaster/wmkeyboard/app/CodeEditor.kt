package com.wasimaster.wmkeyboard.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// A small code editor
// ---------------------------------------------------------------------------

/**
 * The parts of a source language this editor needs: how to colour it, how to
 * tidy it, where its brackets pair up, and what is wrong with it right now.
 *
 * One implementation ships today ([JsonCode]), but the editor itself knows
 * nothing about JSON, so the Lua plugin screen or a pattern field can adopt it
 * later without a change here.
 */
@Immutable
internal interface CodeLanguage {
    /**
     * The document, coloured. Called once per change of the text, so it must
     * not depend on where the caret is: the caret moves far more often than
     * the text changes, and re-colouring a long document on every move is what
     * makes an editor feel slow.
     */
    fun highlight(source: String, colors: CodeColors): AnnotatedString

    /** The document, indented again, or null when it does not parse. */
    fun format(source: String): String?

    /** What is wrong with the document, or null when nothing is. */
    fun problem(source: String): CodeProblem?

    /** The offsets of the brackets that structure [source], in order. */
    fun brackets(source: String): List<Int>

    /** The bracket at or before [caret] and the one that pairs with it. */
    fun matchingBracket(source: String, brackets: List<Int>, caret: Int): Pair<Int, Int>?
}

/** A parse failure, at a character offset when the parser reports one. */
@Immutable
internal data class CodeProblem(val offset: Int?)

/**
 * The editor's own colours. Fixed, rather than taken from the app theme: code
 * reads as code, and a green string on one theme and a pink one on the next
 * helps nobody. The two sets are the light and dark palettes that every editor
 * has trained people on.
 */
@Immutable
internal data class CodeColors(
    val background: Color,
    val border: Color,
    val gutter: Color,
    val gutterText: Color,
    val gutterActiveText: Color,
    val activeLine: Color,
    val caret: Color,
    val selection: Color,
    val bracketMatch: Color,
    val text: Color,
    val key: Color,
    val string: Color,
    val number: Color,
    val keyword: Color,
    val problem: Color,
)

private val LightCode = CodeColors(
    background = Color(0xFFFFFFFF),
    border = Color(0xFFD8DEE4),
    gutter = Color(0xFFF5F6F8),
    gutterText = Color(0xFFA0A6AD),
    gutterActiveText = Color(0xFF24292F),
    activeLine = Color(0xFFF2F4F7),
    caret = Color(0xFF0550AE),
    selection = Color(0xFFADD6FF),
    bracketMatch = Color(0xFFD3E3F7),
    text = Color(0xFF24292F),
    key = Color(0xFF0451A5),
    string = Color(0xFFA31515),
    number = Color(0xFF098658),
    keyword = Color(0xFF0000FF),
    problem = Color(0xFFD1242F),
)

private val DarkCode = CodeColors(
    background = Color(0xFF1E1E1E),
    border = Color(0xFF33383D),
    gutter = Color(0xFF232527),
    gutterText = Color(0xFF858585),
    gutterActiveText = Color(0xFFC6C6C6),
    activeLine = Color(0xFF2A2D2E),
    caret = Color(0xFFAEAFAD),
    selection = Color(0xFF264F78),
    bracketMatch = Color(0xFF3A5070),
    text = Color(0xFFD4D4D4),
    key = Color(0xFF9CDCFE),
    string = Color(0xFFCE9178),
    number = Color(0xFFB5CEA8),
    keyword = Color(0xFF569CD6),
    problem = Color(0xFFF14C4C),
)

/**
 * The files a real monospaced font is likely to sit in. AOSP ships the second
 * one, and every skin builds on AOSP.
 */
private val MONO_FONT_FILES = listOf(
    "/system/fonts/RobotoMono-Regular.ttf",
    "/system/fonts/DroidSansMono.ttf",
    "/system/fonts/CutiveMono.ttf",
)

/**
 * A monospaced family, found by file rather than by name.
 *
 * `FontFamily.Monospace` asks the platform for its "monospace" alias, and some
 * skins point that alias at their own proportional face. ColorOS 15 does, so
 * the JSON on an OPPO device came out in the system sans with every column
 * ragged. The font file behind the alias does not go through the alias, so it
 * is loaded straight from disk when one of the usual ones is there, and the
 * alias stays as the fallback for a device that has none.
 *
 * Resolved once per process: it touches the file system, and the answer cannot
 * change while the app runs.
 */
private val CodeFontFamily: FontFamily by lazy {
    val file = MONO_FONT_FILES
        .asSequence()
        .map(::File)
        .filter { it.canRead() }
        .firstOrNull { runCatching { Typeface.createFromFile(it) }.getOrNull() != null }
    if (file == null) FontFamily.Monospace else FontFamily(Font(file))
}

/** The palette that suits the theme the app is drawn in. */
@Composable
internal fun rememberCodeColors(): CodeColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(dark) { if (dark) DarkCode else LightCode }
}

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

private const val UNDO_DEPTH = 100
private const val COALESCE_MS = 700L
private const val INDENT = "  "

/**
 * What the editor holds: the text, where the caret is, and enough history to
 * step back out of a mistake.
 *
 * The history lives here rather than in the field, because a text field keeps
 * none of its own past one focus session, and because the two toolbar buttons
 * have to know whether there is anything to step to.
 */
@Stable
internal class CodeEditorState(initial: TextFieldValue) {

    var value by mutableStateOf(initial)
        private set

    private val past = mutableStateListOf<TextFieldValue>()
    private val future = mutableStateListOf<TextFieldValue>()
    private var lastEditAt = 0L

    val text: String get() = value.text
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /** The field reports a change. Smart editing runs here, before the record. */
    fun edit(new: TextFieldValue) {
        val smart = smartEdit(value, new)
        record(smart)
        value = smart
    }

    /** Replaces the whole document, as Format and Paste do. One step of history. */
    fun replace(newText: String) {
        if (newText == value.text) return
        push()
        value = TextFieldValue(newText, TextRange(value.selection.end.coerceIn(0, newText.length)))
    }

    /** Puts the caret at [offset]. The text does not change. */
    fun moveTo(offset: Int) {
        val at = offset.coerceIn(0, value.text.length)
        value = TextFieldValue(value.text, TextRange(at))
    }

    fun undo() {
        val previous = past.removeLastOrNull() ?: return
        future += value
        value = previous
        lastEditAt = 0L
    }

    fun redo() {
        val next = future.removeLastOrNull() ?: return
        past += value
        value = next
        lastEditAt = 0L
    }

    /**
     * Tab and Shift+Tab. With no selection, Tab is two spaces. With one, it
     * moves every line the selection touches in or out by one step.
     */
    fun shiftLines(levels: Int) {
        val current = value
        val selection = current.selection
        if (levels > 0 && selection.collapsed) {
            edit(
                TextFieldValue(
                    text = current.text.substring(0, selection.end) + INDENT + current.text.substring(selection.end),
                    selection = TextRange(selection.end + INDENT.length),
                ),
            )
            return
        }
        val first = lineStartAt(current.text, selection.min)
        val last = current.text.indexOf('\n', selection.max).let { if (it < 0) current.text.length else it }
        val block = current.text.substring(first, last)
        val shifted = block.split('\n').joinToString("\n") { line -> shiftOne(line, levels) }
        if (shifted == block) return
        val grown = shifted.length - block.length
        push()
        value = TextFieldValue(
            text = current.text.substring(0, first) + shifted + current.text.substring(last),
            selection = TextRange(
                selection.min.coerceAtLeast(first),
                (selection.max + grown).coerceIn(first, first + shifted.length),
            ),
        )
    }

    /**
     * One history entry per burst of typing. A run of plain characters inside
     * [COALESCE_MS] is one step, so undo does not walk back letter by letter,
     * while a newline, a delete or a paste always starts a fresh one.
     */
    private fun record(new: TextFieldValue) {
        val old = value
        if (old.text == new.text) return
        val now = System.currentTimeMillis()
        val typed = new.text.length == old.text.length + 1 &&
            new.text.getOrNull(new.selection.end - 1)?.isWhitespace() == false
        if (!(typed && past.isNotEmpty() && now - lastEditAt < COALESCE_MS)) {
            past += old
            while (past.size > UNDO_DEPTH) past.removeAt(0)
        }
        lastEditAt = now
        future.clear()
    }

    /** Starts a history entry for a change that is never coalesced. */
    private fun push() {
        past += value
        while (past.size > UNDO_DEPTH) past.removeAt(0)
        future.clear()
        lastEditAt = 0L
    }

    companion object {
        /** Rotation keeps the text and the caret. The history is not worth the bundle. */
        val Saver = listSaver<CodeEditorState, Any>(
            save = { listOf(it.value.text, it.value.selection.start, it.value.selection.end) },
            restore = { CodeEditorState(TextFieldValue(it[0] as String, TextRange(it[1] as Int, it[2] as Int))) },
        )
    }
}

/** Remembers the editor's state for one document. A new [key] starts a new one. */
@Composable
internal fun rememberCodeEditorState(key: Any?, initial: () -> String): CodeEditorState =
    rememberSaveable(key, saver = CodeEditorState.Saver) { CodeEditorState(TextFieldValue(initial())) }

// ---------------------------------------------------------------------------
// The editor
// ---------------------------------------------------------------------------

private const val PROBLEM_DELAY_MS = 250L
private val CONTENT_PAD = 10.dp
private val FIELD_PAD = 10.dp
private val CARET_PAD = 24.dp
private val NUMBER_PAD = 10.dp

/**
 * A monospaced field on a code background, with numbered lines, the caret's
 * line lit, matching brackets boxed, a toolbar above and a status line below.
 *
 * Long lines run off to the right and scroll rather than wrap, which is what
 * keeps one number against one line. The Wrap button turns that off for a
 * narrow screen, and the numbers stay right either way: they are drawn at the
 * y each line actually landed at, from the field's own layout, rather than at
 * a multiple of an assumed line height.
 */
@Composable
internal fun CodeEditor(
    state: CodeEditorState,
    language: CodeLanguage,
    modifier: Modifier = Modifier,
    title: String? = null,
    minHeight: Dp = 240.dp,
    maxHeight: Dp = 420.dp,
) {
    val colors = rememberCodeColors()
    val density = LocalDensity.current
    var wrap by rememberSaveable { mutableStateOf(false) }
    // Held as the state object rather than read through it: the two draw
    // blocks below read `.value` inside the draw phase, so a new text layout
    // repaints the margin and the underlay without recomposing the editor.
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()

    val style = remember(colors) {
        TextStyle(fontFamily = CodeFontFamily, fontSize = 13.sp, lineHeight = 20.sp, color = colors.text)
    }
    val text = state.text
    val caret = state.value.selection.end.coerceIn(0, text.length)
    val lineStarts = remember(text) { lineStartOffsets(text) }
    val caretLine = lineOf(lineStarts, caret)
    // Everything below is keyed on the text alone. Only `match` follows the
    // caret, and it walks a list of offsets rather than the document.
    val brackets = remember(text, language) { language.brackets(text) }
    val match = remember(brackets, caret) { language.matchingBracket(text, brackets, caret) }
    val coloured = remember(text, colors, language) { language.highlight(text, colors) }
    val painter = remember(coloured) { CodeHighlight(coloured) }

    // A parse on every keystroke is wasted work while the user is mid-word, so
    // the status line waits for a pause, and then parses off the main thread.
    // Nothing else waits on it.
    val problem by produceState<CodeProblem?>(null, text, language) {
        delay(PROBLEM_DELAY_MS)
        value = withContext(Dispatchers.Default) { language.problem(text) }
    }
    val problemLine = problem?.offset?.let { lineOf(lineStarts, it.coerceIn(0, text.length)) }

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

    Column(modifier) {
        CodeToolbar(state, language, colors, title, wrap) { wrap = it }

        val shape = RoundedCornerShape(12.dp)
        // A plain Box with its size reported back, rather than
        // BoxWithConstraints: that one subcomposes its content on every measure
        // pass, and this content is a text field holding the whole document.
        var frame by remember { mutableStateOf(IntSize.Zero) }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight, max = maxHeight)
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
                    .padding(vertical = CONTENT_PAD)
                    .drawBehind {
                        drawLineNumbers(
                            layout = layout.value,
                            lineStarts = lineStarts,
                            activeLine = caretLine,
                            problemLine = problemLine,
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
                    CodeField(state, style, colors, painter, if (wrap) room else contentWidth) { layout.value = it }
                }
            }
        }

        CodeStatus(caretLine, caret - lineStarts[caretLine], text.length, problemLine, colors) {
            problem?.offset?.let { state.moveTo(it) }
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
    onLayout: (TextLayoutResult) -> Unit,
) {
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.caret, backgroundColor = colors.selection)
    }
    CompositionLocalProvider(LocalTextSelectionColors provides selection) {
        BasicTextField(
            value = state.value,
            onValueChange = state::edit,
            textStyle = style,
            cursorBrush = SolidColor(colors.caret),
            visualTransformation = painter,
            onTextLayout = onLayout,
            modifier = Modifier
                .width(width)
                .onPreviewKeyEvent { event -> handleEditorKey(event, state) },
        )
    }
}

/** Tab, Shift+Tab and the undo pair, for a device with a hardware keyboard. */
private fun handleEditorKey(event: androidx.compose.ui.input.key.KeyEvent, state: CodeEditorState): Boolean {
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
 * gutter included, and a box around each half of the bracket pair.
 *
 * The bracket boxes are drawn rather than spanned. A span would mean rebuilding
 * the coloured document on every caret move, which on a long layout is the
 * difference between a field that keeps up with typing and one that does not.
 */
@Composable
private fun BoxScope.EditorUnderlay(
    layout: State<TextLayoutResult?>,
    caret: Int,
    match: Pair<Int, Int>?,
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
        val length = result.layoutInput.text.length
        if (caret <= length) {
            val line = result.getLineForOffset(caret)
            val y = result.getLineTop(line) + top
            val height = result.getLineBottom(line) - result.getLineTop(line)
            if (y + height > 0f && y < size.height) {
                drawRect(color = colors.activeLine, topLeft = Offset(0f, y), size = Size(size.width, height))
            }
        }
        if (match == null) return@Canvas
        val left = with(density) { textLeft.toPx() } - horizontal.value
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
    problemLine: Int?,
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
        val bad = problemLine == index
        val active = index == activeLine
        val colour = when {
            bad -> colors.problem
            active -> colors.gutterActiveText
            else -> colors.gutterText
        }
        val number = measurer.measure(
            AnnotatedString((index + 1).toString()),
            style.copy(color = colour, fontWeight = if (active || bad) FontWeight.Medium else FontWeight.Normal),
        )
        drawText(number, topLeft = Offset(right - number.size.width, top))
    }
}

/** Undo, redo, format, wrap, copy and paste. */
@Composable
private fun CodeToolbar(
    state: CodeEditorState,
    language: CodeLanguage,
    colors: CodeColors,
    title: String?,
    wrap: Boolean,
    onWrapChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // The title takes whatever the six buttons leave, and cuts itself
        // short rather than pushing one of them off a narrow screen.
        Text(
            text = title.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        CodeAction(Icons.AutoMirrored.Outlined.Undo, stringResource(CommonR.string.common_undo), state.canUndo) {
            state.undo()
        }
        CodeAction(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.code_redo_desc), state.canRedo) {
            state.redo()
        }
        CodeAction(Icons.Outlined.AutoFixHigh, stringResource(R.string.code_format_desc), true) {
            language.format(state.text)?.let { state.replace(it) }
        }
        CodeAction(
            icon = Icons.AutoMirrored.Outlined.WrapText,
            description = stringResource(if (wrap) R.string.code_wrap_off_desc else R.string.code_wrap_on_desc),
            enabled = true,
            tint = if (wrap) colors.caret else null,
        ) { onWrapChange(!wrap) }
        CodeAction(Icons.Outlined.ContentCopy, stringResource(CommonR.string.common_copy), true) {
            copyCode(context, state.text)
        }
        CodeAction(Icons.Outlined.ContentPaste, stringResource(CommonR.string.common_paste), true) {
            pasteCode(context)?.let { state.replace(it) }
        }
    }
}

@Composable
private fun CodeAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(19.dp), tint = tint ?: LocalContentColor.current)
    }
}

/** Where the caret is, how long the document is, and whether it parses. */
@Composable
private fun CodeStatus(
    line: Int,
    column: Int,
    characters: Int,
    problemLine: Int?,
    colors: CodeColors,
    onProblemClick: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.labelSmall
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.code_position_label, line + 1, column + 1), style = small, color = muted)
        Spacer(Modifier.width(12.dp))
        Text(
            pluralStringResource(R.plurals.code_character_count, characters, characters),
            style = small,
            color = muted,
        )
        Spacer(Modifier.weight(1f))
        if (problemLine == null) {
            Text(stringResource(R.string.code_status_ok), style = small, color = muted)
        } else {
            Text(
                stringResource(R.string.code_status_problem, problemLine + 1),
                style = small,
                color = colors.problem,
                modifier = Modifier.clickable(onClick = onProblemClick).padding(2.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Smart editing
// ---------------------------------------------------------------------------

/**
 * Turns one raw field change into the one an editor would make: a newline
 * carries the indentation on, an opening bracket brings its closer, a typed
 * closer steps over the one already there, and a backspace over an empty pair
 * takes both halves.
 *
 * It reads the difference between the old value and the new one rather than
 * key events, because on a phone the characters arrive from an input method
 * and there are no key events to read.
 *
 * Internal rather than private so the unit tests can drive it directly: it is
 * the one part of the editor with rules of its own.
 */
internal fun smartEdit(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    singleInsert(old, new)?.let { (at, character) ->
        val closer = closerFor(character)
        return when {
            character == '\n' -> autoIndent(new, at)
            // The closer is already there: step over it instead of doubling it.
            (character.isCloser() || character == '"') && new.text.getOrNull(at + 1) == character ->
                TextFieldValue(old.text, TextRange(at + 1))
            closer != null && shouldClose(new.text, at, character) -> TextFieldValue(
                text = new.text.substring(0, at + 1) + closer + new.text.substring(at + 1),
                selection = TextRange(at + 1),
            )
            else -> new
        }
    }
    singleDelete(old, new)?.let { (at, character) ->
        val emptyPair = closerFor(character) != null && old.text.getOrNull(at + 1) == closerFor(character)
        return if (emptyPair) {
            TextFieldValue(new.text.removeRange(at, at + 1), TextRange(at))
        } else {
            new
        }
    }
    return new
}

private fun Char.isCloser() = this == '}' || this == ']'

private fun closerFor(character: Char): Char? = when (character) {
    '{' -> '}'
    '[' -> ']'
    '"' -> '"'
    else -> null
}

/**
 * True when an opener typed at [at] should bring its closer. Not inside a
 * string, and not in front of a word: a closer there would split what the user
 * is about to wrap.
 */
private fun shouldClose(text: String, at: Int, character: Char): Boolean {
    if (closerFor(character) == null) return false
    if (insideString(text, at)) return false
    val next = text.getOrNull(at + 1) ?: return true
    return next.isWhitespace() || next == ',' || next.isCloser()
}

/** True when offset [at] falls inside a string. */
private fun insideString(text: String, at: Int): Boolean {
    var index = 0
    var open = false
    while (index < at && index < text.length) {
        when (text[index]) {
            '\\' -> if (open) index++
            '"' -> open = !open
        }
        index++
    }
    return open
}

/** Carries the line's indentation onto the new one, a step deeper after a bracket. */
private fun autoIndent(new: TextFieldValue, at: Int): TextFieldValue {
    val text = new.text
    val prefix = text.substring(lineStartAt(text, at), at)
    val indent = prefix.takeWhile { it == ' ' || it == '\t' }
    val opens = prefix.trimEnd().lastOrNull().let { it == '{' || it == '[' }
    val body = if (opens) indent + INDENT else indent
    val closesNext = text.getOrNull(at + 1).let { it == '}' || it == ']' }
    return if (opens && closesNext) {
        // The closer takes a line of its own at the outer level, and the caret
        // lands on the empty line between the two.
        val inserted = "\n" + body + "\n" + indent
        TextFieldValue(
            text = text.substring(0, at) + inserted + text.substring(at + 1),
            selection = TextRange(at + 1 + body.length),
        )
    } else {
        TextFieldValue(
            text = text.substring(0, at + 1) + body + text.substring(at + 1),
            selection = TextRange(at + 1 + body.length),
        )
    }
}

/** The offset and character of a one character insertion, or null for anything else. */
private fun singleInsert(old: TextFieldValue, new: TextFieldValue): Pair<Int, Char>? {
    if (new.text.length != old.text.length + 1) return null
    if (!old.selection.collapsed || !new.selection.collapsed) return null
    val at = new.selection.end - 1
    if (at < 0 || at != old.selection.end) return null
    // Compared by region rather than by rebuilding the document: this runs on
    // every keystroke, and the document can be tens of kilobytes.
    val same = old.text.regionMatches(0, new.text, 0, at) &&
        old.text.regionMatches(at, new.text, at + 1, old.text.length - at)
    return if (same) at to new.text[at] else null
}

/** The offset and character of a one character deletion, or null for anything else. */
private fun singleDelete(old: TextFieldValue, new: TextFieldValue): Pair<Int, Char>? {
    if (new.text.length != old.text.length - 1) return null
    if (!old.selection.collapsed || !new.selection.collapsed) return null
    val at = new.selection.end
    if (at < 0 || at >= old.text.length || at != old.selection.end - 1) return null
    val same = old.text.regionMatches(0, new.text, 0, at) &&
        old.text.regionMatches(at + 1, new.text, at, new.text.length - at)
    return if (same) at to old.text[at] else null
}

/** One line in or out by a single step. Out stops at the left margin. */
private fun shiftOne(line: String, levels: Int): String = if (levels > 0) {
    INDENT + line
} else {
    line.removePrefix(INDENT).let { if (it == line) line.trimStart(' ') else it }
}

// ---------------------------------------------------------------------------
// Lines and the clipboard
// ---------------------------------------------------------------------------

/** The offset each line starts at. The first is always 0. */
internal fun lineStartOffsets(text: String): List<Int> {
    val starts = ArrayList<Int>(text.count { it == '\n' } + 1)
    starts += 0
    text.forEachIndexed { index, character -> if (character == '\n') starts += index + 1 }
    return starts
}

/** The index of the line that [offset] falls on. */
internal fun lineOf(starts: List<Int>, offset: Int): Int {
    var low = 0
    var high = starts.size - 1
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (starts[middle] <= offset) low = middle else high = middle - 1
    }
    return low
}

/** The offset the line holding [at] starts at. */
private fun lineStartAt(text: String, at: Int): Int =
    text.lastIndexOf('\n', (at - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }

private fun copyCode(context: Context, text: String) {
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("code", text))
    }
}

private fun pasteCode(context: Context): String? = runCatching {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = manager.primaryClip ?: return null
    (0 until clip.itemCount)
        .asSequence()
        .mapNotNull { clip.getItemAt(it).coerceToText(context)?.toString() }
        .firstOrNull { it.isNotBlank() }
}.getOrNull()
