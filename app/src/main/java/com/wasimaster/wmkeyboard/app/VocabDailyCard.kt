package com.wasimaster.wmkeyboard.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.ui.toolAccentColor
import com.wasimaster.wmkeyboard.core.vocab.VocabIndex
import com.wasimaster.wmkeyboard.core.vocab.VocabPacks
import com.wasimaster.wmkeyboard.core.vocab.VocabProgress
import com.wasimaster.wmkeyboard.core.vocab.VocabWord
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class DailyPick(
    val word: VocabWord?,
    val packId: String?,
    val hasPacks: Boolean,
    val dismissed: Boolean = false,
) {
    /** Whether this draw has anything to put on the screen at all. */
    val draws: Boolean get() = !dismissed && (!hasPacks || word != null)
}

/**
 * The draw survives leaving the home screen and coming back, so the card is
 * on the first frame rather than a beat later. Keyed by the day and by
 * [VocabPacks.stateToken] so installing or deleting a pack redraws it, and
 * held for the process only — the pinned word itself lives in the record.
 */
private object VocabDailyCache {
    private var day = Int.MIN_VALUE
    private var token = 0
    private var pick: DailyPick? = null

    /** The last draw of [today] whatever the packs were, for painting straight away. */
    @Synchronized
    fun peek(today: Int): DailyPick? = if (day == today) pick else null

    /** The draw of [today] made from exactly these packs, or null to make it again. */
    @Synchronized
    fun get(today: Int, stateToken: Int): DailyPick? =
        if (day == today && token == stateToken) pick else null

    @Synchronized
    fun put(today: Int, stateToken: Int, value: DailyPick) {
        day = today
        token = stateToken
        pick = value
    }

    /** Keeps a dismissal without re-reading the packs it was drawn from. */
    @Synchronized
    fun markDismissed(today: Int) {
        if (day == today) pick = pick?.copy(dismissed = true)
    }
}

/**
 * The word-of-the-day card on the settings home. The same draw the keyboard
 * makes (pinned per day in the learning record), so both show one word.
 * Draws a nudge to install a pack when there is none, and nothing at all
 * once every word is learnt, once the card is put away for the day, or when
 * the switch in the tool's settings is off — the caller checks that one.
 *
 * Owns the gap above itself so that a day with nothing to say costs no space.
 */
@Composable
internal fun VocabDailyCard(settings: KeyboardSettings, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val today = remember { vocabToday() }
    val cached = remember(today) { VocabDailyCache.peek(today) }
    val pick by produceState<DailyPick?>(initialValue = cached, key1 = today) {
        value = withContext(Dispatchers.IO) {
            val filesDir = context.filesDir
            val token = VocabPacks.stateToken(filesDir)
            VocabDailyCache.get(today, token)
                ?: drawWordOfTheDay(filesDir, today).also { VocabDailyCache.put(today, token, it) }
        }
    }
    val accent = toolAccentColor(ToolbarTool.VOCABULARY, settings.toolColorOverrides)
    // Nothing is known yet on the very first opening of the process: hold the
    // card's space with a placeholder rather than letting the rows below jump
    // down when the packs finish loading.
    val current = pick ?: run {
        VocabDailySkeleton()
        return
    }
    var dismissed by remember(today) { mutableStateOf(false) }
    if (dismissed || !current.draws) return
    val progress = rememberVocabProgress()
    val putAway: () -> Unit = {
        dismissed = true
        // The record is written by the keyboard too, and this copy was read
        // when the screen opened; re-read before adding to it.
        progress.reloadIfChanged()
        progress.dismissWordOfTheDay(today)
        progress.save()
        VocabDailyCache.markDismissed(today)
    }
    VocabCardFrame {
        if (!current.hasPacks) {
            Row(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_vocab_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_vocab_install_body), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { onNavigate(VOCAB_PACKS_ROUTE) }) { Text(stringResource(R.string.home_vocab_install_action)) }
                VocabDismissButton(putAway)
            }
            return@VocabCardFrame
        }
        val word = current.word ?: return@VocabCardFrame
        val speaker = rememberVocabSpeaker()
        var learnt by remember(word.word) { mutableIntStateOf(if (progress.isLearnt(word.word)) 1 else 0) }
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = accent)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.home_vocab_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { speakVocabWord(context, settings, speaker, word) }) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = stringResource(R.string.vocab_word_speak_desc))
                }
                VocabDismissButton(putAway)
            }
            Text(word.word, style = MaterialTheme.typography.headlineSmall)
            val line = listOfNotNull(word.pos.firstOrNull(), word.ipaFor(settings.vocabulary.accent), word.respelling).joinToString("  ·  ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(word.definition, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = learnt == 0,
                    onClick = {
                        progress.reloadIfChanged()
                        progress.markLearnt(word.word, true, today)
                        progress.save()
                        learnt = 1
                    },
                ) { Text(stringResource(if (learnt == 1) R.string.home_vocab_learnt_label else R.string.home_vocab_learnt_action)) }
                TextButton(onClick = { onNavigate(vocabWordRoute(current.packId ?: "all", word.word)) }) {
                    Text(stringResource(R.string.home_vocab_open_action))
                }
            }
        }
    }
}

/** Reads the packs and the record; off the main thread, and cached by the caller. */
private fun drawWordOfTheDay(filesDir: File, today: Int): DailyPick {
    val progress = VocabProgress(File(filesDir, VocabProgress.FILE_PATH))
    if (progress.isWordOfTheDayDismissed(today)) {
        return DailyPick(null, null, hasPacks = true, dismissed = true)
    }
    val packs = VocabPacks.languages(filesDir).flatMap { VocabPacks.load(filesDir, it) }
    if (packs.none { it.words.isNotEmpty() }) return DailyPick(null, null, hasPacks = false)
    val index = VocabIndex.build(packs)
    val candidates = index.lemmas.filter { !progress.isLearnt(it) }
    val lemma = progress.wordOfTheDay(today, candidates)
    progress.save()
    return DailyPick(lemma?.let { index.lookup(it) }, lemma?.let { index.packOf(it)?.id }, hasPacks = true)
}

/** The gap above the card, spent only when there is a card under it. */
@Composable
private fun VocabCardFrame(content: @Composable () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun VocabDismissButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.home_vocab_dismiss_desc))
    }
}

/**
 * The card's own height, held while the packs load so that arriving costs no
 * jump. A shape rather than a spinner: the card it stands in for is a block
 * of text, and this is on the screen for a few hundred milliseconds at most.
 */
@Composable
private fun VocabDailySkeleton() {
    val pulse = rememberInfiniteTransition(label = "vocab-card")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "vocab-card-alpha",
    )
    VocabCardFrame {
        Column(modifier = Modifier.padding(16.dp)) {
            SkeletonBar(0.3f, 14.dp, alpha)
            Spacer(Modifier.height(12.dp))
            SkeletonBar(0.5f, 24.dp, alpha)
            Spacer(Modifier.height(10.dp))
            SkeletonBar(0.9f, 14.dp, alpha)
            Spacer(Modifier.height(6.dp))
            SkeletonBar(0.7f, 14.dp, alpha)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float, height: Dp, alpha: Float) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(6.dp)),
    )
}
