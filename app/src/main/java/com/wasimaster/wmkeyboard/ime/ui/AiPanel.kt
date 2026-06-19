package com.wasimaster.wmkeyboard.ime.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmCatalog
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmStore
import com.wasimaster.wmkeyboard.core.settings.AiAction
import com.wasimaster.wmkeyboard.core.settings.AiProvider
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.tools.AiClient
import com.wasimaster.wmkeyboard.ime.AiUi
import com.wasimaster.wmkeyboard.ime.KeyboardUiState

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
    onReplace: () -> Unit,
    onInsert: () -> Unit,
    onRetry: () -> Unit,
    onPickModel: (AiProvider, String?) -> Unit,
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
                Text(
                    "Pick a provider and add its API key (or your Ollama / LM Studio " +
                        "server address) in the tool's settings.",
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
                    "AI runs entirely on this device — download a model in the " +
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
                ActionChips(onAction)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "Actions run on the selected text, or the whole field — " +
                            "via ${state.settings.aiProvider.label}.",
                        color = kb.secondaryText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }
            }
            is AiUi.Loading -> Column(Modifier.fillMaxSize()) {
                ActionChips(onAction, running = ai.action)
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (ai.thinking) {
                        LinearProgressIndicator(
                            color = kb.accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                        )
                    } else {
                        CircularProgressIndicator(
                            color = kb.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(22.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            ai.thinking ->
                                "Reasoning… the answer streams in once it's done thinking."
                            state.settings.aiProvider == AiProvider.ON_DEVICE ->
                                "${ai.action.label} on-device… the first run also loads the model."
                            else -> "${ai.action.label}…"
                        },
                        color = kb.secondaryText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            is AiUi.Error -> Column(Modifier.fillMaxSize()) {
                ActionChips(onAction)
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
                        grayThinking(ai.result, kb.modifierKeyText.copy(alpha = 0.45f)),
                        color = kb.modifierKeyText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    for (action in AiAction.entries) {
                        ToolPanelChip(action.label, selected = action == ai.action) {
                            onAction(action)
                        }
                    }
                }
            }
        }
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
    val close = text.indexOf("</think>")
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

/**
 * One-tap switcher across everything usable right now: each configured
 * cloud/server provider, plus every downloaded on-device model. Hidden via
 * the "model picker on the panel" setting, or when there's nothing to
 * switch between.
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Model:", color = kb.secondaryText, fontSize = 11.sp)
        for ((id, name) in localIds) {
            ToolPanelChip(
                name,
                selected = settings.aiProvider == AiProvider.ON_DEVICE && id == selectedLocalId,
            ) { onPickModel(AiProvider.ON_DEVICE, id) }
        }
        for (provider in remote) {
            ToolPanelChip(
                provider.label,
                selected = settings.aiProvider == provider,
            ) { onPickModel(provider, null) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionChips(onAction: (AiAction) -> Unit, running: AiAction? = null) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (action in AiAction.entries) {
            ToolPanelChip(action.label, selected = action == running) {
                if (running == null) onAction(action)
            }
        }
    }
}
