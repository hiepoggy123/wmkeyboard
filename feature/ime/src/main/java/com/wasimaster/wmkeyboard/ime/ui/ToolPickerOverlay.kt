package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.core.tools.CheatGroup
import com.wasimaster.wmkeyboard.core.tools.CheatRow
import com.wasimaster.wmkeyboard.core.tools.CheatSheetLetter
import com.wasimaster.wmkeyboard.core.tools.DefaultLeader
import com.wasimaster.wmkeyboard.core.tools.ToolboxLetter
import com.wasimaster.wmkeyboard.core.tools.cheatSheetRows
import com.wasimaster.wmkeyboard.core.tools.leaderLabel
import com.wasimaster.wmkeyboard.core.tools.parseLeader
import com.wasimaster.wmkeyboard.core.tools.resolvedToolLetters
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.R

/**
 * What the physical keyboard's leader key put on screen: a one-line hint of the
 * letters on offer, or — after `?` — the full legend.
 *
 * Drawn over the top strip rather than as a [com.wasimaster.wmkeyboard.ime.PanelMode]
 * of its own. A panel replaces the keys, which would hide the very keyboard the
 * legend is documenting, and opening one resets twenty fields of panel state that
 * a read-only list has no business touching.
 */
@Composable
internal fun ToolPickerOverlay(state: KeyboardUiState, modifier: Modifier = Modifier) {
    val picker = state.toolPicker
    AnimatedVisibility(visible = picker != null, modifier = modifier) {
        val kb = LocalKbTheme.current
        val config = state.settings.hardwareKeyboard
        val letters = resolvedToolLetters(config.toolByLetter, state.settings.enabledTools)
        // A chord spells itself, so it arrives with no template around it.
        val leaderParts = leaderLabel(parseLeader(config.leader) ?: DefaultLeader)
        val leader = if (leaderParts.templateRes == 0) {
            leaderParts.text
        } else {
            stringResource(leaderParts.templateRes, leaderParts.text)
        }
        Box(
            modifier = Modifier
                .padding(6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(kb.popup)
                .border(1.dp, kb.accent, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (picker?.cheatSheet == true) {
                CheatSheet(
                    rows = cheatSheetRows(config.toolByLetter, state.settings.enabledTools),
                    leader = leader,
                )
            } else {
                PickerHint(letters = letters.keys.toList(), leader = leader)
            }
        }
    }
}

/**
 * The compact form: enough to jog the memory without covering the screen. The
 * letters are the user's own bindings, in order, truncated to one line — `?`
 * opens the full list, which is the only thing anyone needs to remember.
 */
@Composable
private fun PickerHint(letters: List<Char>, leader: String) {
    val kb = LocalKbTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            leader,
            color = kb.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (letters.isEmpty()) {
                stringResource(R.string.ime_tool_picker_empty, ToolboxLetter)
            } else {
                stringResource(
                    R.string.ime_tool_picker_hint,
                    letters.joinToString(" "),
                    ToolboxLetter,
                    CheatSheetLetter,
                )
            },
            color = kb.popupText,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Every binding, laid out two columns wide so it fits the keyboard's height. */
@Composable
private fun CheatSheet(rows: List<CheatRow>, leader: String) {
    val kb = LocalKbTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.ime_tool_picker_leader, leader),
            color = kb.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rows) { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Text(
                        row.trigger,
                        color = kb.toolCircleActiveIcon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(kb.toolCircleActive)
                            .widthIn(min = 18.dp)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                    Text(
                        row.tool?.let { toolLabel(it) }
                            ?: row.labelRes.takeIf { it != 0 }?.let { stringResource(it) }.orEmpty(),
                        // The built-in actions are dimmer than the tools: they
                        // are the same four lines every time, and the tools are
                        // what the reader is scanning for.
                        color = if (row.group == CheatGroup.ACTIONS) kb.secondaryText else kb.popupText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
