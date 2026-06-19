package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.wasimaster.wmkeyboard.core.grammar.GrammarFix
import com.wasimaster.wmkeyboard.core.grammar.GrammarLint
import com.wasimaster.wmkeyboard.core.settings.GrammarDialect
import com.wasimaster.wmkeyboard.ime.KeyboardUiState

/**
 * Grammarly-style category for a lint: Harper's fine-grained kinds collapse
 * into four color-coded buckets so the card header reads at a glance.
 */
private data class LintCategory(val label: String, val light: Color, val dark: Color) {
    fun color(isDark: Boolean): Color = if (isDark) dark else light
}

private val CORRECTNESS = LintCategory("Correctness", Color(0xFFD64545), Color(0xFFEF7070))
private val CLARITY = LintCategory("Clarity", Color(0xFF2D7FD6), Color(0xFF6AAFF5))
private val ENGAGEMENT = LintCategory("Engagement", Color(0xFF15845D), Color(0xFF43C593))
private val DELIVERY = LintCategory("Delivery", Color(0xFF8A56C9), Color(0xFFB68AF0))

private fun categoryFor(kind: String): LintCategory = when (kind.lowercase()) {
    "spelling", "typo", "grammar", "agreement", "capitalization", "punctuation",
    "boundaryerror", "malapropism", "eggcorn", "usage",
    -> CORRECTNESS
    "readability", "redundancy", "repetition", "wordchoice",
    -> CLARITY
    "enhancement", "style", "miscellaneous", "",
    -> ENGAGEMENT
    "formatting", "regionalism", "nonstandard",
    -> DELIVERY
    else -> ENGAGEMENT
}

/** "WordChoice" -> "Word choice"; keeps Harper's kind readable in the header. */
private fun kindLabel(kind: String): String {
    if (kind.isBlank()) return ""
    return kind.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        .lowercase()
        .replaceFirstChar { it.uppercase() }
}

/**
 * Offline grammar strip (Harper engine): sits above the key rows, which stay
 * visible so issues can be fixed by typing too. Each issue is a Grammarly-style
 * card — category header, struck-through original, tappable fix chips and an
 * explanation — in a scrollable list. "Fix all" applies each issue's top
 * suggestion; the X on a card hides that issue until the text changes. The
 * dialect chip switches the English variant Harper checks.
 */
@Composable
internal fun GrammarPanel(
    state: KeyboardUiState,
    onFix: (GrammarLint, GrammarFix) -> Unit,
    onFixAll: () -> Unit,
    onDismiss: (GrammarLint) -> Unit,
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
            if (grammar.lints.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    if (grammar.lints.size == 1) "1 issue" else "${grammar.lints.size} issues",
                    color = kb.secondaryText,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
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
        Spacer(Modifier.height(4.dp))
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(grammar.lints) { lint -> GrammarLintCard(lint, onFix, onDismiss) }
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
private fun GrammarLintCard(
    lint: GrammarLint,
    onFix: (GrammarLint, GrammarFix) -> Unit,
    onDismiss: (GrammarLint) -> Unit,
) {
    val kb = LocalKbTheme.current
    val category = categoryFor(lint.kind)
    val catColor = category.color(kb.dark)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(kb.chip, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(catColor, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            val kind = kindLabel(lint.kind)
            Text(
                if (kind.isEmpty() || kind == category.label) {
                    category.label
                } else {
                    "${category.label} • $kind"
                },
                color = kb.secondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Dismiss issue",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onDismiss(lint) },
                tint = kb.secondaryText,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (lint.original.isNotBlank()) {
                Text(
                    lint.original,
                    color = catColor,
                    fontSize = 13.sp,
                    textDecoration = TextDecoration.LineThrough,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                if (lint.suggestions.isNotEmpty()) {
                    Text("→", color = kb.secondaryText, fontSize = 13.sp, maxLines = 1)
                }
            }
            if (lint.suggestions.isEmpty()) {
                Text(
                    lint.message,
                    color = kb.secondaryText,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    lint.suggestions.distinctBy { it.label.trim() }.take(5).forEach { fix ->
                        Text(
                            fix.label.ifBlank { "Fix" },
                            color = kb.toolCircleActiveIcon,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .background(kb.toolCircleActive, RoundedCornerShape(12.dp))
                                .clickable { onFix(lint, fix) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        if (lint.suggestions.isNotEmpty() && lint.message.isNotBlank()) {
            Text(
                lint.message,
                color = kb.secondaryText,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
