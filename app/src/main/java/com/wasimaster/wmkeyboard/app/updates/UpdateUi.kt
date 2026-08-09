package com.wasimaster.wmkeyboard.app.updates

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.app.NavRow
import com.wasimaster.wmkeyboard.app.SettingsGroup
import com.wasimaster.wmkeyboard.app.SettingsRowIcons
import com.wasimaster.wmkeyboard.app.ToggleSetting
import com.wasimaster.wmkeyboard.app.WmRow

/**
 * The update card on the settings home screen.
 *
 * Draws nothing unless there is something to act on, which is most of the
 * time: a build with no Play behind it sits on [UpdateState.Unsupported], and
 * an up-to-date install sits on [UpdateState.UpToDate]. A failed check draws
 * nothing either — the About row is where a retry belongs, not the front page.
 */
@Composable
internal fun UpdateCard(modifier: Modifier = Modifier) {
    val updater = LocalAppUpdater.current
    val state by updater.state.collectAsStateWithLifecycle()
    when (val current = state) {
        is UpdateState.Available ->
            if (current.dismissed) Unit else AvailableCard(current, updater, modifier)
        is UpdateState.Downloading -> DownloadingCard(current, modifier)
        UpdateState.Downloaded -> ReadyCard(updater, modifier)
        UpdateState.Installing -> InstallingCard(modifier)
        UpdateState.Unsupported,
        UpdateState.Idle,
        UpdateState.Checking,
        is UpdateState.UpToDate,
        is UpdateState.Failed,
        -> Unit
    }
}

@Composable
private fun AvailableCard(
    state: UpdateState.Available,
    updater: AppUpdater,
    modifier: Modifier = Modifier,
) {
    UpdateCardFrame(
        title = stringResource(R.string.update_card_available_title),
        body = stringResource(
            if (state.immediate) R.string.update_card_urgent_body
            else R.string.update_card_available_body,
        ),
        modifier = modifier,
    ) {
        Button(onClick = updater::start) {
            Text(
                stringResource(
                    if (state.immediate) R.string.update_action_update
                    else R.string.update_action_download,
                ),
            )
        }
        // An urgent release gets no "Not now": the button would write a snooze
        // that [UpdatePolicy] ignores for that release anyway, so it would be
        // a control that does nothing.
        if (!state.immediate) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = updater::dismiss) {
                Text(stringResource(R.string.update_action_later))
            }
        }
    }
}

@Composable
private fun DownloadingCard(state: UpdateState.Downloading, modifier: Modifier = Modifier) {
    val fraction = state.fraction
    UpdateCardFrame(
        title = stringResource(R.string.update_card_downloading_title),
        // Nothing to say yet while the download is only queued: Play reports no
        // byte counts until it starts, and "0 B of 0 B" is worse than silence.
        body = state.progressText(),
        modifier = modifier,
    ) {
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ReadyCard(updater: AppUpdater, modifier: Modifier = Modifier) {
    UpdateCardFrame(
        title = stringResource(R.string.update_card_ready_title),
        // The warning is the point of this card. Installing kills the process
        // the keyboard runs in, so the user has to be the one who picks the
        // moment; see [AppUpdater].
        body = stringResource(R.string.update_card_ready_body),
        modifier = modifier,
    ) {
        Button(onClick = updater::install) {
            Text(stringResource(R.string.update_action_install))
        }
    }
}

@Composable
private fun InstallingCard(modifier: Modifier = Modifier) {
    UpdateCardFrame(
        title = stringResource(R.string.update_card_installing_title),
        body = stringResource(R.string.update_card_installing_body),
        modifier = modifier,
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/** The shape every update card shares: a title, a line of prose, then controls. */
@Composable
private fun UpdateCardFrame(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    controls: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Row { controls() }
        }
    }
}

/** "12 MB of 30 MB", or null while Play has not said how big the download is. */
@Composable
private fun UpdateState.Downloading.progressText(): String? {
    if (totalBytes <= 0L) return null
    val context = LocalContext.current
    return stringResource(
        R.string.update_progress_body,
        Formatter.formatShortFileSize(context, bytesDownloaded),
        Formatter.formatShortFileSize(context, totalBytes),
    )
}

/**
 * The Updates group on the About screen: one row that always says where things
 * stand, and the setting that decides whether Play may interrupt.
 *
 * The row's action follows its state rather than always meaning "check". A row
 * that reads "Install the update" and then only re-checks for one would be
 * lying about what pressing it does.
 */
@Composable
internal fun UpdateSettings() {
    val updater = LocalAppUpdater.current
    val state by updater.state.collectAsStateWithLifecycle()
    if (state is UpdateState.Unsupported) return

    // The setting lives in SharedPreferences, not in the settings DataStore, so
    // it is not snapshot state and a write would not recompose anything on its
    // own. The switch keeps its own copy and writes through.
    val autoPrompt = remember { mutableStateOf(updater.autoPrompt) }

    SettingsGroup(stringResource(R.string.update_section_title)) {
        item { UpdateRow(state, updater) }
        item {
            ToggleSetting(
                R.string.update_row_prompts_title,
                stringResource(R.string.update_row_prompts_subtitle),
                checked = autoPrompt.value,
                default = UpdatePrefs.DEFAULT_AUTO_PROMPT,
                onChange = {
                    autoPrompt.value = it
                    updater.autoPrompt = it
                },
            )
        }
    }
}

@Composable
private fun UpdateRow(state: UpdateState, updater: AppUpdater) {
    val context = LocalContext.current
    when (state) {
        is UpdateState.Available -> {
            val size = Formatter.formatShortFileSize(context, state.sizeBytes)
            NavRow(
                R.string.update_row_available_title,
                subtitle = if (state.sizeBytes > 0L) {
                    stringResource(R.string.update_row_available_subtitle, state.versionCode, size)
                } else {
                    stringResource(R.string.update_row_available_subtitle_plain, state.versionCode)
                },
                onClick = updater::start,
            )
        }
        // Nothing to press while Play is working, so these two are rows rather
        // than [NavRow]s: a chevron would promise an action that is not there.
        is UpdateState.Downloading ->
            ProgressRow(R.string.update_row_downloading_title, state.progressText())
        UpdateState.Downloaded -> NavRow(
            R.string.update_row_install_title,
            subtitle = stringResource(R.string.update_row_install_subtitle),
            onClick = updater::install,
        )
        UpdateState.Installing -> ProgressRow(R.string.update_row_installing_title, null)
        // Every quiet state, where the row's job is to offer a check and to
        // report the last one.
        UpdateState.Unsupported,
        UpdateState.Idle,
        UpdateState.Checking,
        is UpdateState.UpToDate,
        is UpdateState.Failed,
        -> NavRow(
            R.string.update_row_check_title,
            subtitle = stringResource(state.checkSubtitle()),
            onClick = { updater.check(userAsked = true) },
        )
    }
}

/** A row that reports work in progress and cannot be pressed. */
@Composable
private fun ProgressRow(@StringRes title: Int, subtitle: String?) {
    WmRow(
        title = stringResource(title),
        subtitle = subtitle,
        icon = SettingsRowIcons[title],
        highlightKey = title,
    )
}

/** What the "Check for updates" row says under its name, for the quiet states. */
@StringRes
private fun UpdateState.checkSubtitle(): Int = when (this) {
    UpdateState.Checking -> R.string.update_row_checking_subtitle
    is UpdateState.UpToDate -> R.string.update_row_current_subtitle
    // A cancelled flow is the user's own answer, so the row goes back to its
    // resting words rather than reporting their decision back at them.
    is UpdateState.Failed -> if (cancelled) {
        R.string.update_row_check_subtitle
    } else {
        R.string.update_row_failed_subtitle
    }
    UpdateState.Unsupported,
    UpdateState.Idle,
    UpdateState.Downloaded,
    UpdateState.Installing,
    is UpdateState.Available,
    is UpdateState.Downloading,
    -> R.string.update_row_check_subtitle
}
