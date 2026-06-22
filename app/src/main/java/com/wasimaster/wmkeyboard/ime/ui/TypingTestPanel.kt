package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.tools.CharState
import com.wasimaster.wmkeyboard.core.tools.TypingBests
import com.wasimaster.wmkeyboard.core.tools.TypingHistory
import com.wasimaster.wmkeyboard.core.tools.TypingResult
import com.wasimaster.wmkeyboard.core.tools.TypingTestDurations
import com.wasimaster.wmkeyboard.core.tools.TypingTestMode
import com.wasimaster.wmkeyboard.core.tools.TypingTestWordCounts
import com.wasimaster.wmkeyboard.core.tools.compareWord
import com.wasimaster.wmkeyboard.core.tools.typingConfigKey
import com.wasimaster.wmkeyboard.core.tools.typingConfigLabel
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.TypingTestAction
import com.wasimaster.wmkeyboard.ime.TypingTestUi
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The typing-speed tool. Two faces sharing one panel: the run itself —
 * which sits above the live key rows, since the user is typing on them —
 * and the results screen, which takes the whole panel once the clock stops.
 *
 * Nothing here owns any test state. The service holds the prompt, the
 * typed words and the clock (see TypingTestUi) so that scoring can never
 * drift from what a recomposition happens to render.
 */
@Composable
internal fun TypingTestPanel(
    state: KeyboardUiState,
    onAction: (TypingTestAction) -> Unit,
) {
    val result = state.typingTest.result
    if (result != null) {
        TypingResultView(state, result, onAction)
    } else {
        TypingRunView(state, onAction)
    }
}

// ---- the run ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypingRunView(state: KeyboardUiState, onAction: (TypingTestAction) -> Unit) {
    val kb = LocalKbTheme.current
    val test = state.typingTest
    val settings = state.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        TypingRunStats(state)
        Spacer(Modifier.height(4.dp))

        // The prompt scrolls a page at a time rather than a word at a time:
        // re-anchoring on every space would slide the whole paragraph under
        // the reader's eye and make the text impossible to follow.
        var windowStart by remember(test.words) { mutableIntStateOf(0) }
        if (test.wordIndex - windowStart > WordsPerPage) {
            windowStart = max(0, test.wordIndex - 4)
        }

        // Reduce motion holds the caret solid: no infinite transition is
        // started at all, rather than one running against a zero duration.
        val blink = if (kb.reduceMotion) {
            1f
        } else {
            val animated by rememberInfiniteTransition(label = "caret").animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(620, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "caretAlpha",
            )
            animated
        }

        Box(modifier = Modifier.weight(1f)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                val end = minOf(test.words.size, windowStart + WordsPerPage + 8)
                for (index in windowStart until end) {
                    Text(
                        promptWord(test, index, kb, blink),
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        // Untyped text is the resting colour; the spans in
                        // the annotated string override it as the user goes.
                        color = kb.secondaryText.copy(alpha = 0.45f),
                    )
                }
            }
        }

        TypingConfigRow(settings, onAction, compact = true)
    }
}

/** How many words are on screen before the prompt turns the page. */
private const val WordsPerPage = 22

/**
 * Mistake red. Matches the grammar panel's "correctness" colour rather
 * than inventing a second wrong-looking red, and lightens on dark themes
 * so it stays legible against a dark board.
 */
private fun errorColor(kb: KbTheme): Color =
    if (kb.dark) Color(0xFFEF7070) else Color(0xFFD64545)

/**
 * One prompt word, coloured character by character against what the user
 * typed for it. The word holding the caret also carries the caret itself,
 * drawn as a background block on the character about to be typed.
 *
 * [blink] is the caret's alpha, driven by a single animation in the parent
 * — one transition for the panel rather than one per word on screen.
 */
private fun promptWord(
    test: TypingTestUi,
    index: Int,
    kb: KbTheme,
    blink: Float,
): AnnotatedString {
    val expected = test.words[index]
    val live = index == test.wordIndex
    val typed = when {
        live -> test.current
        index < test.typedWords.size -> test.typedWords[index].typed
        else -> null
    }

    // Untouched words are plain text — no spans, no per-character work for
    // the hundred-odd words still ahead of the caret.
    if (typed == null) return AnnotatedString(expected)

    val states = compareWord(expected, typed, live = live)
    val caretAt = if (live) typed.length else -1
    val wrong = errorColor(kb)
    return buildAnnotatedString {
        val chars = expected + typed.drop(expected.length)
        for ((i, state) in states.withIndex()) {
            val style = when (state) {
                CharState.CORRECT -> SpanStyle(color = kb.keyText)
                CharState.WRONG -> SpanStyle(
                    color = wrong,
                    textDecoration = TextDecoration.Underline,
                )
                CharState.EXTRA -> SpanStyle(color = wrong.copy(alpha = 0.7f))
                CharState.MISSING -> SpanStyle(
                    color = wrong.copy(alpha = 0.5f),
                    textDecoration = TextDecoration.LineThrough,
                )
                CharState.PENDING -> SpanStyle(color = kb.secondaryText.copy(alpha = 0.45f))
            }
            val caret = if (i == caretAt) {
                style.copy(background = kb.accent.copy(alpha = 0.55f * blink))
            } else {
                style
            }
            withStyleSafe(caret) { append(chars.getOrElse(i) { ' ' }) }
        }
        // Caret parked past the last letter — the word is fully typed and
        // the next key is either the space or an overshoot.
        if (caretAt >= states.size) {
            withStyleSafe(SpanStyle(background = kb.accent.copy(alpha = 0.55f * blink))) {
                append(" ")
            }
        }
    }
}

/** [AnnotatedString.Builder.withStyle] without the experimental opt-in noise. */
private inline fun AnnotatedString.Builder.withStyleSafe(
    style: SpanStyle,
    block: AnnotatedString.Builder.() -> Unit,
) {
    val index = pushStyle(style)
    block()
    pop(index)
}

/** Live speed, progress and accuracy across the top of a running test. */
@Composable
private fun TypingRunStats(state: KeyboardUiState) {
    val kb = LocalKbTheme.current
    val test = state.typingTest
    val settings = state.settings

    val elapsedSeconds = test.elapsedMs / 1000.0
    val liveWpm = test.samples.lastOrNull()?.wpm ?: 0.0
    val accuracy = if (test.totalKeystrokes > 0) {
        test.correctKeystrokes * 100.0 / test.totalKeystrokes
    } else {
        100.0
    }

    // The headline number: seconds left when a clock is running the test,
    // words remaining when a word count is.
    val headline: String
    val headlineLabel: String
    val progress: Float
    when (settings.typingTestMode) {
        TypingTestMode.TIME -> {
            val left = (settings.typingTestDuration - elapsedSeconds).coerceAtLeast(0.0)
            headline = left.roundToInt().toString()
            headlineLabel = "left"
            progress = (elapsedSeconds / settings.typingTestDuration).coerceIn(0.0, 1.0).toFloat()
        }
        else -> {
            val total = test.words.size.coerceAtLeast(1)
            headline = "${test.wordIndex}/$total"
            headlineLabel = "words"
            progress = (test.wordIndex.toFloat() / total).coerceIn(0f, 1f)
        }
    }

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                headline,
                color = kb.accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                " $headlineLabel",
                color = kb.secondaryText,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            InlineStat("wpm", liveWpm.roundToInt().toString())
            Spacer(Modifier.width(14.dp))
            InlineStat("acc", "${accuracy.roundToInt()}%")
        }
        Spacer(Modifier.height(4.dp))
        ProgressBar(progress)
    }
}

@Composable
private fun InlineStat(label: String, value: String) {
    val kb = LocalKbTheme.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = kb.keyText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            " $label",
            color = kb.secondaryText,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val kb = LocalKbTheme.current
    // Animated so the time bar glides instead of stepping with the ticker.
    // Reduce motion takes the stepping: a progress bar advancing in ticks is
    // still legible, and the glide is the part that is motion.
    val width by animateFloatAsState(
        progress,
        animationSpec = if (kb.reduceMotion) snap() else spring(),
        label = "typingProgress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(kb.divider),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(width.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(kb.accent),
        )
    }
}

// ---- results ----

@Composable
private fun TypingResultView(
    state: KeyboardUiState,
    result: TypingResult,
    onAction: (TypingTestAction) -> Unit,
) {
    val kb = LocalKbTheme.current
    val settings = state.settings
    val best = remember(settings.typingTestBests, result.configKey) {
        TypingBests.decode(settings.typingTestBests)[result.configKey]
    }
    val history = remember(settings.typingTestHistory) {
        TypingHistory.decode(settings.typingTestHistory)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                result.wpm.roundToInt().toString(),
                color = kb.accent,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                " wpm",
                color = kb.secondaryText,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            if (state.typingTest.personalBest) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(kb.accent.copy(alpha = 0.18f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        "New best",
                        color = kb.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else if (best != null) {
                Text(
                    "best ${best.roundToInt()}",
                    color = kb.secondaryText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatTile("accuracy", "${result.accuracy.roundToInt()}%", Modifier.weight(1f))
            StatTile("raw", result.raw.roundToInt().toString(), Modifier.weight(1f))
            StatTile("consistency", "${result.consistency.roundToInt()}%", Modifier.weight(1f))
            StatTile("time", "${result.seconds.roundToInt()}s", Modifier.weight(1f))
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "${result.correctChars} correct · ${result.incorrectChars} wrong · " +
                "${result.extraChars} extra · ${result.missedChars} missed",
            color = kb.secondaryText,
            fontSize = 11.sp,
        )

        // Two samples is the minimum that makes a line rather than a dot.
        if (result.samples.size >= 2) {
            Spacer(Modifier.height(8.dp))
            WpmGraph(result, modifier = Modifier.fillMaxWidth().height(74.dp))
            Row {
                LegendDot("wpm", kb.accent)
                Spacer(Modifier.width(10.dp))
                LegendDot("raw", kb.secondaryText)
                Spacer(Modifier.weight(1f))
                Text(
                    typingConfigLabel(
                        result.mode, settings.typingTestDuration, settings.typingTestWordCount,
                    ),
                    color = kb.secondaryText,
                    fontSize = 10.sp,
                )
            }
        }

        if (history.size >= 2) {
            Spacer(Modifier.height(8.dp))
            Text("Last ${history.size} runs", color = kb.secondaryText, fontSize = 10.sp)
            Spacer(Modifier.height(3.dp))
            HistoryBars(history, modifier = Modifier.fillMaxWidth().height(26.dp))
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolPanelChip("Again", selected = true) { onAction(TypingTestAction.Restart) }
            ToolPanelChip("Insert score") { onAction(TypingTestAction.InsertResult) }
        }
        Spacer(Modifier.height(6.dp))
        TypingConfigRow(settings, onAction, compact = false)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val kb = LocalKbTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(kb.chip)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(value, color = kb.keyText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(label, color = kb.secondaryText, fontSize = 9.sp)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    val kb = LocalKbTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Text(" $label", color = kb.secondaryText, fontSize = 10.sp)
    }
}

/**
 * Speed over the course of the run: corrected WPM as a filled line, raw
 * WPM behind it, and a mark on every second that contained a mistake.
 */
@Composable
private fun WpmGraph(result: TypingResult, modifier: Modifier = Modifier) {
    val kb = LocalKbTheme.current
    val samples = result.samples
    val peak = samples.maxOf { maxOf(it.wpm, it.raw) }.coerceAtLeast(10.0)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun x(index: Int) = w * index / (samples.size - 1).coerceAtLeast(1)
        fun y(value: Double) = h - (h * (value / peak)).toFloat()

        // Three faint gridlines to give the curve a sense of scale.
        for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
            drawLine(
                color = kb.divider,
                start = Offset(0f, h * fraction),
                end = Offset(w, h * fraction),
                strokeWidth = 1f,
            )
        }

        fun path(values: List<Double>): Path = Path().apply {
            values.forEachIndexed { index, value ->
                val point = Offset(x(index), y(value))
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }

        drawPath(
            path(samples.map { it.raw }),
            color = kb.secondaryText.copy(alpha = 0.45f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round),
        )
        drawPath(
            path(samples.map { it.wpm }),
            color = kb.accent,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round),
        )

        // Error markers: the second a mistake landed in, not the running
        // total, so a clean stretch after a bad word shows as clean.
        var previousErrors = 0
        samples.forEachIndexed { index, sample ->
            if (sample.errors > previousErrors) {
                drawCircle(
                    color = errorColor(kb),
                    radius = 2.5f,
                    center = Offset(x(index), y(sample.wpm)),
                )
            }
            previousErrors = sample.errors
        }
    }
}

/** Recent scores as bars, newest on the right, tallest = fastest. */
@Composable
private fun HistoryBars(history: List<Double>, modifier: Modifier = Modifier) {
    val kb = LocalKbTheme.current
    val peak = history.max().coerceAtLeast(1.0)
    Canvas(modifier = modifier) {
        val gap = 2f
        val barWidth = ((size.width - gap * (history.size - 1)) / history.size).coerceAtLeast(1f)
        history.forEachIndexed { index, value ->
            val height = (size.height * (value / peak)).toFloat().coerceAtLeast(1f)
            // The latest run is the one being read about; the rest are context.
            val color = if (index == history.lastIndex) kb.accent else kb.accent.copy(alpha = 0.3f)
            drawRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), size.height - height),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
            )
        }
    }
}

// ---- shared controls ----

/**
 * Mode, length and the punctuation/numbers switches. Changing any of them
 * deals a new prompt, so the row is deliberately small and out of the way
 * during a run — it is a settings strip, not a scoreboard.
 */
@Composable
private fun TypingConfigRow(
    settings: KeyboardSettings,
    onAction: (TypingTestAction) -> Unit,
    compact: Boolean,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(vertical = if (compact) 1.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (mode in TypingTestMode.entries) {
            ToolPanelChip(
                label = when (mode) {
                    TypingTestMode.TIME -> "time"
                    TypingTestMode.WORDS -> "words"
                    TypingTestMode.QUOTE -> "quote"
                },
                selected = settings.typingTestMode == mode,
            ) { onAction(TypingTestAction.Mode(mode)) }
        }
        Spacer(Modifier.width(3.dp))
        when (settings.typingTestMode) {
            TypingTestMode.TIME -> for (seconds in TypingTestDurations) {
                ToolPanelChip(
                    label = "${seconds}s",
                    selected = settings.typingTestDuration == seconds,
                ) { onAction(TypingTestAction.Duration(seconds)) }
            }
            TypingTestMode.WORDS -> for (count in TypingTestWordCounts) {
                ToolPanelChip(
                    label = "$count",
                    selected = settings.typingTestWordCount == count,
                ) { onAction(TypingTestAction.WordCount(count)) }
            }
            // A quote is whatever length it is; the punctuation and number
            // switches do not apply to it either.
            TypingTestMode.QUOTE -> Unit
        }
        if (settings.typingTestMode != TypingTestMode.QUOTE) {
            Spacer(Modifier.width(3.dp))
            ToolPanelChip(label = "punct", selected = settings.typingTestPunctuation) {
                onAction(TypingTestAction.Punctuation(!settings.typingTestPunctuation))
            }
            ToolPanelChip(label = "123", selected = settings.typingTestNumbers) {
                onAction(TypingTestAction.Numbers(!settings.typingTestNumbers))
            }
        }
    }
}

/**
 * The best score for the settings currently selected — shown in the panel
 * header so there is a target in view before the run starts.
 */
internal fun typingHeaderBest(settings: KeyboardSettings): String? {
    val key = typingConfigKey(
        settings.typingTestMode, settings.typingTestDuration, settings.typingTestWordCount,
    )
    val best = TypingBests.decode(settings.typingTestBests)[key] ?: return null
    return "best ${best.roundToInt()}"
}
