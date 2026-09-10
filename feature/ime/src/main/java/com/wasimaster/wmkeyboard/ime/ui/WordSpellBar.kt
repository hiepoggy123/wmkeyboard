package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.R
import com.wasimaster.wmkeyboard.ime.WordCardAction

/**
 * The word card's spelling editor (#138), drawn in the suggestion strip's own
 * row while the card is respelling a word.
 *
 * The card itself is a window over the whole keyboard, so it cannot be typed
 * into; it steps aside and this bar takes its place, exactly as the AI Custom
 * instruction collapses its panel to leave the key rows free. Every keystroke
 * goes into [KeyboardUiState.wordSpell], never into the app behind the
 * keyboard — see `keysTakenByKeyboard`.
 *
 * Tick applies the respelling and brings the card back on the new word; cross
 * leaves the spelling as it was. There is no caret to move: a word is short,
 * and backspace plus retyping is the whole editing model, which is what keeps
 * this a strip row rather than a text field the IME cannot host.
 */
@Composable
internal fun WordSpellBar(
    state: KeyboardUiState,
    onAction: (WordCardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spell = state.wordSpell ?: return
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight(state.settings).coerceAtLeast(SPELL_BAR_MIN_HEIGHT))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(kb.suggestionText.copy(alpha = 0.10f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (spell.draft.isEmpty()) {
                // An empty draft cannot be applied, so the bar says what the
                // keys are for rather than showing a blank box.
                Text(
                    text = stringResource(R.string.ime_word_spell_hint, spell.word),
                    style = MaterialTheme.typography.bodyMedium,
                    color = kb.suggestionText.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = spell.draft,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = kb.suggestionText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SpellBarButton(
            icon = Icons.Outlined.Close,
            description = stringResource(R.string.ime_word_spell_cancel_desc),
            enabled = true,
            tint = kb.suggestionText,
        ) {
            feedback()
            onAction(WordCardAction.CancelSpelling)
        }
        SpellBarButton(
            icon = Icons.Outlined.Check,
            description = stringResource(R.string.ime_word_spell_apply_desc),
            // A respelling to nothing would delete the word by the back door,
            // and one that changes nothing has nothing to apply.
            enabled = spell.draft.isNotBlank() && spell.draft != spell.word,
            tint = kb.accent,
        ) {
            feedback()
            onAction(WordCardAction.CommitSpelling)
        }
    }
}

/** One of the bar's two round buttons. */
@Composable
private fun SpellBarButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = tint.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The shortest the bar may be. The strip's own height is what it normally
 * takes, but that height is zero with the toolbar turned off — and the bar
 * has to be readable wherever the card can be opened from.
 */
private val SPELL_BAR_MIN_HEIGHT = 40.dp
