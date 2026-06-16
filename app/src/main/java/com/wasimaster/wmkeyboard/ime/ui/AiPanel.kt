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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.core.settings.AiAction
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.ime.AiUi
import com.wasimaster.wmkeyboard.ime.KeyboardUiState

/**
 * AI writing tool: one-tap actions (rewrite, summarize, translate …) that
 * run on the focused field's text through the provider configured in the
 * tool's settings (Claude, OpenAI, Gemini, Ollama, LM Studio — BYOK).
 * The result replaces the field or inserts at the cursor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiPanel(
    state: KeyboardUiState,
    onAction: (AiAction) -> Unit,
    onReplace: () -> Unit,
    onInsert: () -> Unit,
    onRetry: () -> Unit,
    onOpenToolSettings: (ToolbarTool) -> Unit,
) {
    val kb = LocalKbTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(keyRowsHeight(state.settings))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
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
                    CircularProgressIndicator(
                        color = kb.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(22.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${ai.action.label}…",
                        color = kb.secondaryText,
                        fontSize = 12.sp,
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
            is AiUi.Ready -> Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        ai.action.label,
                        color = kb.accent,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    ToolPanelChip("Replace", selected = true) { onReplace() }
                    ToolPanelChip("Insert") { onInsert() }
                    ToolPanelChip("↻") { onRetry() }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(kb.chip)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        ai.result,
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
