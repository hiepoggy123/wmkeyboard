package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.wasimaster.wmkeyboard.core.grammar.GrammarFix
import com.wasimaster.wmkeyboard.core.grammar.GrammarLint
import com.wasimaster.wmkeyboard.core.settings.GrammarDialect
import com.wasimaster.wmkeyboard.ime.KeyboardUiState

/**
 * Offline grammar strip (Harper engine): sits above the key rows, which stay
 * visible so issues can be fixed by typing too. Lists every issue in the
 * focused field with tappable fix chips; "Fix all" applies each issue's top
 * suggestion. The dialect chip switches the English variant Harper checks.
 */
@Composable
internal fun GrammarPanel(
    state: KeyboardUiState,
    onFix: (GrammarLint, GrammarFix) -> Unit,
    onFixAll: () -> Unit,
    onDialect: (GrammarDialect) -> Unit,
) {
    val kb = LocalKbTheme.current
    val grammar = state.grammar
    var pickerOpen by remember { mutableStateOf(false) }
    val fixable = grammar.lints.count { it.suggestions.isNotEmpty() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Grammar", color = kb.secondaryText, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Box {
                Row(
                    modifier = Modifier
                        .background(kb.chip, RoundedCornerShape(14.dp))
                        .clickable { pickerOpen = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.settings.grammarDialect.label,
                        color = kb.suggestionText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Icon(
                        Icons.Outlined.ArrowDropDown,
                        contentDescription = "Choose dialect",
                        modifier = Modifier.size(18.dp),
                        tint = kb.toolbarIcon,
                    )
                }
                if (pickerOpen) {
                    GrammarDialectPicker(
                        current = state.settings.grammarDialect,
                        onPick = {
                            pickerOpen = false
                            onDialect(it)
                        },
                        onDismiss = { pickerOpen = false },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (grammar.checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = kb.accent,
                )
                Spacer(Modifier.width(8.dp))
            }
            if (fixable > 0) {
                Row(
                    modifier = Modifier
                        .background(kb.toolCircleActive, RoundedCornerShape(14.dp))
                        .clickable { onFixAll() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Done,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = kb.toolCircleActiveIcon,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Fix all ($fixable)",
                        color = kb.toolCircleActiveIcon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
        when {
            !grammar.available -> GrammarHint(
                "Grammar engine isn't in this build (libharper_jni.so missing).",
            )
            grammar.sourceText.isEmpty() && !grammar.checking -> GrammarHint(
                "Type, or open a field with text — issues show here. Checks run fully offline.",
            )
            grammar.lints.isEmpty() && grammar.checkedOnce && !grammar.checking -> Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = kb.accent,
                )
                Spacer(Modifier.width(6.dp))
                Text("No issues found", color = kb.secondaryText, fontSize = 13.sp)
            }
            else -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(grammar.lints) { lint -> GrammarLintRow(lint, onFix) }
            }
        }
    }
}

@Composable
private fun GrammarHint(text: String) {
    val kb = LocalKbTheme.current
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = kb.secondaryText,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
        )
    }
}

@Composable
private fun GrammarLintRow(lint: GrammarLint, onFix: (GrammarLint, GrammarFix) -> Unit) {
    val kb = LocalKbTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (lint.original.isNotBlank()) {
            Text(
                lint.original,
                color = kb.accent,
                fontSize = 13.sp,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
            )
        }
        Text(
            lint.message,
            color = kb.secondaryText,
            fontSize = 11.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        lint.suggestions.take(3).forEach { fix ->
            Text(
                fix.label.ifBlank { "Fix" },
                color = kb.suggestionText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .background(kb.chip, RoundedCornerShape(12.dp))
                    .clickable { onFix(lint, fix) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun GrammarDialectPicker(
    current: GrammarDialect,
    onPick: (GrammarDialect) -> Unit,
    onDismiss: () -> Unit,
) {
    val kb = LocalKbTheme.current
    Popup(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 220.dp)
                .heightIn(max = 240.dp)
                .background(kb.popup, RoundedCornerShape(12.dp))
                .padding(vertical = 4.dp),
        ) {
            GrammarDialect.entries.forEach { dialect ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(dialect) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        dialect.label,
                        color = kb.suggestionText,
                        fontSize = 13.sp,
                        fontWeight = if (dialect == current) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (dialect == current) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = kb.accent,
                        )
                    }
                }
            }
        }
    }
}
