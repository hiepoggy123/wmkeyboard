package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmCatalog
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmStore
import com.wasimaster.wmkeyboard.config.BuildConfig
import com.wasimaster.wmkeyboard.core.settings.AiAction
import com.wasimaster.wmkeyboard.core.settings.AiProvider
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.tools.AiClient
import com.wasimaster.wmkeyboard.core.tools.AiMarkdown
import com.wasimaster.wmkeyboard.core.tools.AiPhase
import com.wasimaster.wmkeyboard.ime.AiUi
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import kotlinx.coroutines.delay

/**
 * AI writing tool: one-tap actions (rewrite, summarize, translate …) that
 * run on the focused field's text through the provider configured in the
 * tool's settings (Claude, OpenAI, Gemini, Ollama, LM Studio — BYOK — or a
 * downloaded model running on-device). The result replaces the field or
 * inserts at the cursor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiPanel(
    state: KeyboardUiState,
    onAction: (AiAction) -> Unit,
    onRetry: () -> Unit,
    onRunCustom: () -> Unit,
    onPickModel: (AiProvider, String?) -> Unit,
    onToggleStripMarkdown: () -> Unit,
    onReport: () -> Unit,
    onOpenToolSettings: (ToolbarTool) -> Unit,
) {
    val kb = LocalKbTheme.current

    // Height comes from the FullBleedTool wrapper — the panel replaces the
    // toolbar too, so it gets the key rows plus every hidden bar's height.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val ai0 = state.ai
        if (state.settings.aiPanelModelPicker &&
            (ai0 is AiUi.Idle || ai0 is AiUi.NeedSetup || ai0 is AiUi.NeedModel)
        ) {
            ModelPickerRow(state, onPickModel)
        }
        when (val ai = state.ai) {
            AiUi.NeedSetup -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val setupText = if (BuildConfig.ENABLE_LOCAL_LLM) {
                    "Set up an AI provider or download a local model in the tool's settings to use AI."
                } else {
                    "Set up an AI provider in the tool's settings to use AI."
                }
                Text(
                    setupText,
                    color = kb.secondaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
                ToolPanelChip("Open settings") { onOpenToolSettings(ToolbarTool.AI) }
            }
            AiUi.NeedModel -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "To use on-device AI, download a model in the " +
                        "tool's settings first.",
                    color = kb.secondaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
                ToolPanelChip("Download a model") { onOpenToolSettings(ToolbarTool.AI) }
            }
            AiUi.Idle -> Column(Modifier.fillMaxSize()) {
                ActionChips(onAction, enabled = state.aiHasText)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.aiHasText) {
                            "Actions run on the selected text, or the whole field — " +
                                "via ${state.settings.aiProvider.label}."
                        } else {
                            // An empty field has nothing to act on, so the chips
                            // are greyed out — say why rather than leaving the
                            // user tapping a dead row, and point at Custom,
                            // which still works because it can write from
                            // nothing.
                            "Type or select some text for these — or tap Custom " +
                                "to write something from scratch."
                        },
                        color = kb.secondaryText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }
            }
            // The Custom action's own prompt: typed on the key rows below (the
            // panel is compact here), run on the field text via Run or Enter.
            // No chip grid — like the dictionary/translate search modes, the
            // header chevron is the way back out.
            is AiUi.CustomInput -> Column(Modifier.fillMaxSize()) {
                val blank = ai.instruction.isEmpty()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(kb.chip)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        when {
                            !blank -> ai.instruction
                            // The example has to match what the instruction
                            // will actually do: with an empty field there is
                            // no "this" to make a haiku out of.
                            state.aiHasText -> "Type an instruction — e.g. “make this a haiku”"
                            else -> "Type an instruction — e.g. “write a haiku about rain”"
                        },
                        color = if (blank) kb.secondaryText else kb.modifierKeyText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Only the instruction is required: with an empty field
                    // Custom writes from scratch rather than transforming.
                    ToolPanelChip("Run", selected = !blank, enabled = !blank) { onRunCustom() }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.aiHasText) {
                            "Runs on the selected text, or the whole field — " +
                                "via ${state.settings.aiProvider.label}."
                        } else {
                            "Field is empty — this writes new text, " +
                                "via ${state.settings.aiProvider.label}."
                        },
                        color = kb.secondaryText,
                        fontSize = 11.sp,
                    )
                }
            }
            is AiUi.Loading -> Column(Modifier.fillMaxSize()) {
                ActionChips(onAction, running = ai.action)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AiProgress(ai, state.settings)
                }
            }
            is AiUi.Error -> Column(Modifier.fillMaxSize()) {
                ActionChips(onAction, enabled = state.aiHasText)
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        ai.message,
                        color = kb.secondaryText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    ToolPanelChip("Retry") { onRetry() }
                }
            }
            // Replace/Insert/retry live in the full-bleed header row (the
            // space reclaimed from the toolbar) — the panel body is all
            // result.
            is AiUi.Ready -> Column(Modifier.fillMaxSize()) {
                val resultScroll = rememberScrollState()
                // What the panel shows is what Replace/Insert will commit —
                // so the checkbox reformats the preview, not only the output.
                val hasMarkdown = remember(ai.result) { AiMarkdown.hasMarkdown(ai.result) }
                val shown = remember(ai.result, ai.stripMarkdown, hasMarkdown) {
                    if (hasMarkdown && ai.stripMarkdown) AiMarkdown.strip(ai.result) else ai.result
                }
                LaunchedEffect(ai.result, ai.generating) {
                    // Follow the streaming text like a terminal tail.
                    if (ai.generating) resultScroll.scrollTo(resultScroll.maxValue)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(kb.chip)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .verticalScroll(resultScroll),
                ) {
                    Text(
                        // Dim via alpha, not the theme's secondary color —
                        // some themes draw secondary text in the same white.
                        grayThinking(shown, kb.modifierKeyText.copy(alpha = 0.45f)),
                        color = kb.modifierKeyText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasMarkdown) {
                        PanelCheckbox(
                            label = "Strip markdown",
                            checked = ai.stripMarkdown,
                            onToggle = onToggleStripMarkdown,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Anything a model writes needs a way to be reported —
                    // Play asks for one, and a keyboard that can produce
                    // offensive text at the tap of a chip should own that. It
                    // opens a mail draft the user reads before sending; there
                    // is no silent upload. Hidden while still streaming: half
                    // a result is not the thing being complained about.
                    if (!ai.generating) {
                        ToolPanelChip("Report") { onReport() }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    for (action in AiAction.entries) {
                        ToolPanelChip(
                            action.label,
                            selected = action == ai.action,
                            // These re-run on the field, so they need field
                            // text — unlike Replace/Insert, which only need
                            // the result already on screen. Custom is the
                            // exception: it can write without any input.
                            enabled = state.aiHasText || action.worksWithoutText,
                        ) { onAction(action) }
                    }
                }
            }
        }
    }
}

/**
 * Small themed checkbox for a panel — Material3's would ignore the keyboard
 * theme and eat far more vertical room than this row has to give.
 */
@Composable
private fun PanelCheckbox(label: String, checked: Boolean, onToggle: () -> Unit) {
    val kb = LocalKbTheme.current
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) kb.toolCircleActive else kb.chip),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = kb.toolCircleActiveIcon,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        Text(
            label,
            color = kb.secondaryText,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * With verbose thinking on, a reasoning model's stream includes its think
 * block — tint everything from `<think>` through `</think>` (tags included)
 * gray so the reasoning reads as an aside and the answer stands out. Handles
 * the streaming cases: an unclosed block grays to the end, and a close tag
 * with the opener still in the prompt (Qwen 3 style) grays from the start.
 */
private fun grayThinking(text: String, gray: Color): AnnotatedString {
    val open = text.indexOf("<think>")
    // Search for the closer only after the opener so grayEnd can never precede
    // grayStart (which would crash substring). Still handles the opener-in-
    // prompt case (open == -1) by searching from 0.
    val close = text.indexOf("</think>", startIndex = if (open != -1) open else 0)
    val grayStart = if (open != -1) open else 0
    val grayEnd = when {
        close != -1 -> close + "</think>".length
        open != -1 -> text.length
        else -> -1
    }
    return buildAnnotatedString {
        if (grayEnd == -1) {
            append(text)
        } else {
            append(text.substring(0, grayStart))
            withStyle(SpanStyle(color = gray)) { append(text.substring(grayStart, grayEnd)) }
            append(text.substring(grayEnd))
        }
    }
}

/** One entry in the panel's model picker. */
private class ModelPick(
    // Stable identity across reorders so the LazyRow can animate the selected
    // chip sliding to the front instead of snapping.
    val key: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * One-tap switcher across everything usable right now: each configured
 * cloud/server provider, plus every downloaded on-device model. Hidden via
 * the "model picker on the panel" setting, or when there's nothing to
 * switch between.
 *
 * The row scrolls horizontally, so the selected entry is pulled to the front
 * — otherwise the one thing the user needs to see is the one thing off the
 * right edge.
 */
@Composable
private fun ModelPickerRow(
    state: KeyboardUiState,
    onPickModel: (AiProvider, String?) -> Unit,
) {
    val kb = LocalKbTheme.current
    val filesDir = LocalContext.current.filesDir
    val settings = state.settings
    // Cheap file stats; keyed on ai state so re-opening after a download or
    // delete re-scans.
    val localIds = remember(state.ai, settings.aiLocalModelId) {
        LocalLlmCatalog.models
            .filter { LocalLlmStore.isDownloaded(filesDir, it) }
            .map { it.id to it.displayName } +
            LocalLlmStore.customModels(filesDir).map {
                (LocalLlmStore.CUSTOM_PREFIX + it.name) to it.name.substringBeforeLast('.')
            }
    }
    val remote = AiClient.configuredRemoteProviders(settings)
    if (remote.size + localIds.size < 2) return

    val selectedLocalId = settings.aiLocalModelId
        .takeIf { id -> localIds.any { it.first == id } }
        ?: localIds.singleOrNull()?.first

    val picks = localIds.map { (id, name) ->
        ModelPick(
            key = "local:$id",
            label = name,
            selected = settings.aiProvider == AiProvider.ON_DEVICE && id == selectedLocalId,
            onClick = { onPickModel(AiProvider.ON_DEVICE, id) },
        )
    } + remote.map { provider ->
        ModelPick(
            key = "remote:${provider.name}",
            label = provider.label,
            selected = settings.aiProvider == provider,
            onClick = { onPickModel(provider, null) },
        )
    }
    // Stable sort: the selected entry moves to the front, everything else
    // keeps catalog order.
    val ordered = picks.sortedByDescending { it.selected }

    // A LazyRow keyed by model identity so the just-picked chip animates to the
    // front instead of jumping there.
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "label") {
            Text(
                "Model:",
                color = kb.secondaryText,
                fontSize = 11.sp,
                modifier = Modifier.animateItem(),
            )
        }
        items(ordered, key = { it.key }) { pick ->
            ToolPanelChip(
                pick.label,
                selected = pick.selected,
                modifier = Modifier.animateItem(),
                onClick = pick.onClick,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionChips(
    onAction: (AiAction) -> Unit,
    running: AiAction? = null,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (action in AiAction.entries) {
            ToolPanelChip(
                action.label,
                selected = action == running,
                // The running chip stays lit rather than dimming — it is the
                // label of what is in flight, not an offer to tap.
                enabled = (enabled || action.worksWithoutText) &&
                    (running == null || action == running),
            ) {
                if (running == null) onAction(action)
            }
        }
    }
}

/**
 * What a request is actually doing, in place of the old anonymous spinner.
 * Three things a bare indeterminate bar cannot say: which step it is on, how
 * long it has been there, and — for a reasoning model that emits nothing
 * visible for entire seconds — that anything is arriving at all.
 */
@Composable
private fun AiProgress(ai: AiUi.Loading, settings: KeyboardSettings) {
    val kb = LocalKbTheme.current
    val onDevice = settings.aiProvider == AiProvider.ON_DEVICE
    val steps = buildList {
        add(AiPhase.PREPARING)
        // On-device has no connection to make, so those steps would sit
        // unreachable for the whole run.
        if (!onDevice) {
            add(AiPhase.CONNECTING)
            add(AiPhase.WAITING)
        }
        // Only promise a reasoning step for a model that reasons — otherwise
        // every plain request ends on a step it never reaches. The id sniff can
        // be wrong, so a run that *is* reasoning shows it regardless.
        if (AiClient.expectsReasoning(settings) || ai.phase == AiPhase.THINKING) {
            add(AiPhase.THINKING)
        }
    }

    // Ticks once a second purely to redraw the elapsed readout. Keyed on the
    // run's start so a retry restarts the clock instead of continuing it.
    var elapsedSeconds by remember(ai.startedAtMs) { mutableIntStateOf(0) }
    LaunchedEffect(ai.startedAtMs) {
        if (ai.startedAtMs == 0L) return@LaunchedEffect
        while (true) {
            elapsedSeconds = ((SystemClock.uptimeMillis() - ai.startedAtMs) / 1000).toInt()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            headline(ai, onDevice),
            color = kb.modifierKeyText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        // Determinate: the bar's position means the step, so it stops implying
        // a completion estimate nobody can give.
        LinearProgressIndicator(
            progress = { ai.phase.fraction },
            color = kb.accent,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            for ((index, step) in steps.withIndex()) {
                if (index > 0) {
                    Text("›", color = kb.secondaryText.copy(alpha = 0.5f), fontSize = 11.sp)
                }
                StepLabel(
                    label = step.label,
                    done = step.fraction < ai.phase.fraction,
                    current = step == ai.phase,
                )
            }
        }
        val detail = detail(ai, elapsedSeconds)
        if (detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(detail, color = kb.secondaryText, fontSize = 11.sp)
        }
    }
}

/** One step in [AiProgress]'s breadcrumb: done, in progress, or still ahead. */
@Composable
private fun StepLabel(label: String, done: Boolean, current: Boolean) {
    val kb = LocalKbTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (done) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = kb.accent,
                modifier = Modifier.size(10.dp),
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            label,
            color = when {
                current -> kb.accent
                done -> kb.secondaryText
                // Steps not reached yet are dimmer than finished ones, so the
                // row reads left-to-right as progress.
                else -> kb.secondaryText.copy(alpha = 0.45f)
            },
            fontSize = 11.sp,
            fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** The one-line "what's happening" above [AiProgress]'s bar. */
private fun headline(ai: AiUi.Loading, onDevice: Boolean): String = when {
    ai.phase == AiPhase.THINKING ->
        "Reasoning… the answer streams in once it's done thinking."
    onDevice -> "${ai.action.label} on-device… the first run also loads the model."
    ai.phase == AiPhase.WAITING -> "${ai.action.label}… waiting for the first words."
    else -> "${ai.action.label}…"
}

/**
 * The sub-line under the steps: elapsed time, plus the reasoning size while
 * thinking. Suppressed for the first second, where a timer reading "0s" is
 * noise on a request that is about to come straight back.
 */
private fun detail(ai: AiUi.Loading, elapsedSeconds: Int): String? {
    val time = if (elapsedSeconds >= 1) "${elapsedSeconds}s elapsed" else null
    if (ai.phase != AiPhase.THINKING || ai.thinkingChars <= 0) return time
    val reasoning = "${ai.thinkingChars} characters of reasoning"
    return if (time == null) reasoning else "$reasoning · $time"
}
