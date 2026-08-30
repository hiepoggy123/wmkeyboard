package com.wasimaster.wmkeyboard.ime.ui

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.wasimaster.wmkeyboard.core.grammar.GrammarFix
import com.wasimaster.wmkeyboard.core.grammar.GrammarLint
import com.wasimaster.wmkeyboard.core.settings.GrammarDialect
import com.wasimaster.wmkeyboard.ime.FocusRegion
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.PanelMode
import com.wasimaster.wmkeyboard.ime.R

/**
 * Grammarly-style category for a lint: Harper's fine-grained kinds collapse
 * into four color-coded buckets so the card header reads at a glance.
 */
private data class LintCategory(
    @param:StringRes val labelRes: Int,
    val light: Color,
    val dark: Color,
) {
    fun color(isDark: Boolean): Color = if (isDark) dark else light
}

private val CORRECTNESS = LintCategory(
    R.string.ime_grammar_category_correctness_label, Color(0xFFD64545), Color(0xFFEF7070),
)
private val CLARITY = LintCategory(
    R.string.ime_grammar_category_clarity_label, Color(0xFF2D7FD6), Color(0xFF6AAFF5),
)
private val ENGAGEMENT = LintCategory(
    R.string.ime_grammar_category_engagement_label, Color(0xFF15845D), Color(0xFF43C593),
)
private val DELIVERY = LintCategory(
    R.string.ime_grammar_category_delivery_label, Color(0xFF8A56C9), Color(0xFFB68AF0),
)

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
    onFocus: (GrammarLint) -> Unit,
) {
    val kb = LocalKbTheme.current
    val grammar = state.grammar
    var pickerOpen by remember { mutableStateOf(false) }
    val fixable = grammar.lints.count { it.suggestions.isNotEmpty() }
    // The ring's regions. Seed-only (panelFocusSeedOnly): while the panel is
    // open the user is editing the field, so no arrow ever summons the ring —
    // only opening the tool from the leader does. CHIPS is the header pair;
    // RESULTS activates a card the way "Fix all" would fix it (its top
    // suggestion), or jumps the caret to it when it has none.
    PanelFocusTarget(
        panel = PanelMode.GRAMMAR,
        region = FocusRegion.CHIPS,
        count = if (fixable > 0) 2 else 1,
        columns = 2,
    ) { index ->
        if (index == 0) pickerOpen = true else onFixAll()
    }
    PanelFocusTarget(
        panel = PanelMode.GRAMMAR,
        region = FocusRegion.RESULTS,
        count = grammar.lints.size,
        columns = 1,
    ) { index ->
        grammar.lints.getOrNull(index)?.let { lint ->
            val top = lint.suggestions.firstOrNull()
            if (top != null) onFix(lint, top) else onFocus(lint)
        }
    }
    val focusedChip = state.focusedIndex(FocusRegion.CHIPS)
    val focusedLint = state.focusedIndex(FocusRegion.RESULTS)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ime_grammar_title),
                color = kb.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Box {
                Row(
                    modifier = Modifier
                        .clip(kb.chipShape())
                        .background(kb.chip)
                        .chipBorder(kb, kb.chipShape())
                        .focusRing(focusedChip == 0, kb.chipShape())
                        .clickable { pickerOpen = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(state.settings.grammarDialect.labelRes),
                        color = kb.chipText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Icon(
                        Icons.Outlined.ArrowDropDown,
                        contentDescription = stringResource(R.string.ime_grammar_dialect_desc),
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
                    pluralStringResource(
                        R.plurals.ime_grammar_issue_count,
                        grammar.lints.size,
                        grammar.lints.size,
                    ),
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
                        .clip(kb.chipShape())
                        .background(kb.chipActive)
                        .chipBorder(kb, kb.chipShape())
                        .focusRing(focusedChip == 1, kb.chipShape())
                        .clickable { onFixAll() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Done,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = kb.chipActiveText,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.ime_grammar_fix_all_action, fixable),
                        color = kb.chipActiveText,
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
                stringResource(R.string.ime_grammar_unavailable_info),
            )
            grammar.sourceText.isEmpty() && !grammar.checking -> GrammarHint(
                stringResource(R.string.ime_grammar_empty_hint),
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
                Text(
                    stringResource(R.string.ime_grammar_no_issues_empty),
                    color = kb.secondaryText,
                    fontSize = 13.sp,
                )
            }
            else -> {
                val listState = rememberLazyListState()
                ScrollFocusIntoView(focusedLint) { listState.animateScrollToItem(it) }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(grammar.lints) { index, lint ->
                        GrammarLintCard(
                            lint, onFix, onDismiss, onFocus,
                            focused = index == focusedLint,
                        )
                    }
                }
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
    onFocus: (GrammarLint) -> Unit,
    focused: Boolean = false,
) {
    val kb = LocalKbTheme.current
    val category = categoryFor(lint.kind)
    val catColor = category.color(kb.dark)
    val cardShape = kb.cardShape()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(kb.chip)
            .chipBorder(kb, cardShape)
            .focusRing(focused, cardShape)
            // Tapping the card jumps the cursor to the issue in the field —
            // selecting the word for a swap, or parking at its end for a small
            // fix. The X and fix chips consume their own taps first.
            .clickable { onFocus(lint) }
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
            val categoryLabel = stringResource(category.labelRes)
            Text(
                if (kind.isEmpty() || kind == categoryLabel) {
                    categoryLabel
                } else {
                    stringResource(R.string.ime_grammar_category_kind_label, categoryLabel, kind)
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
                contentDescription = stringResource(R.string.ime_grammar_dismiss_desc),
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
                    lint.suggestions
                        .distinctBy { it.labelText?.trim() ?: it.labelRes }
                        .take(5)
                        .forEach { fix ->
                            // A fix with no text of its own (the delete fix)
                            // names itself with a resource instead.
                            val labelText = fix.labelText
                            val labelRes = fix.labelRes
                            val fixLabel = when {
                                !labelText.isNullOrBlank() -> labelText
                                labelRes != null -> stringResource(labelRes)
                                else -> stringResource(R.string.ime_grammar_fix_action)
                            }
                            Text(
                                fixLabel,
                                color = kb.chipActiveText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(kb.chipShape())
                                    .background(kb.chipActive)
                                    .chipBorder(kb, kb.chipShape())
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
                .clip(kb.menuShape())
                .background(kb.popup)
                .popupBorder(kb, kb.menuShape())
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
                        stringResource(dialect.labelRes),
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
