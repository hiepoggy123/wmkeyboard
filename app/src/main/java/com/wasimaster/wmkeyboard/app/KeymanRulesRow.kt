package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.core.addons.KeymanRuleDownloader
import com.wasimaster.wmkeyboard.core.keyman.KeymanRuleStore
import com.wasimaster.wmkeyboard.core.layout.KeymanBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The row under a converted Keyman layout that fetches its typing rules.
 *
 * The grid ships in the app; the rules do not. Without them each key types the
 * character printed on it, which is right for a positional keyboard and wrong
 * for a mnemonic one, where the caps and the output deliberately differ. The
 * subtitle says which state the layout is in rather than making the user work it
 * out from what their typing looks like.
 *
 * **The title names its layout.** A language can offer several Keyman keyboards,
 * and their rows stack one after another under a single Layouts heading, where
 * a bare "Typing rules" belongs to whichever toggle happens to sit above it.
 * Reading the name out is what makes that unambiguous, and it is also the only
 * version that works for someone using TalkBack, who does not get "above" at
 * all.
 *
 * Nothing here downloads on its own. It is one row, one tap, and it names the
 * source, because a keyboard that reaches the network unasked is not something
 * to do quietly.
 *
 * [refreshKey] lets the screen tell the row that something else installed these
 * rules — the enable prompt does, and without it the row would still be
 * offering to download what is already there.
 */
@Composable
internal fun KeymanRulesRow(binding: KeymanBinding, layoutName: String, refreshKey: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { KeymanRuleStore(context) }

    var installedVersion by remember(binding.keyboardId) { mutableStateOf<String?>(null) }
    var busy by remember(binding.keyboardId) { mutableStateOf(false) }
    var progress by remember(binding.keyboardId) { mutableStateOf(0) }
    var failure by remember(binding.keyboardId) { mutableStateOf<String?>(null) }
    var failed by remember(binding.keyboardId) { mutableStateOf(false) }

    // Off the main thread: this stats a file, and the settings list builds one
    // of these rows per Keyman layout the language offers.
    LaunchedEffect(binding.keyboardId, busy, refreshKey) {
        if (busy) return@LaunchedEffect
        installedVersion = withContext(Dispatchers.IO) { store.installedLabel(binding.keyboardId) }
    }

    val subtitle = when {
        busy && progress > 0 -> stringResource(R.string.languages_keyman_rules_downloading, progress)
        busy -> stringResource(R.string.languages_keyman_rules_checking)
        failed -> failure ?: stringResource(R.string.languages_keyman_rules_failed)
        installedVersion == UNKNOWN_VERSION ->
            stringResource(R.string.languages_keyman_rules_installed_unknown)
        installedVersion != null ->
            stringResource(R.string.languages_keyman_rules_installed, installedVersion.orEmpty())
        else -> stringResource(R.string.languages_keyman_rules_missing)
    }

    NavRow(
        title = stringResource(R.string.languages_keyman_rules_title_for, layoutName),
        subtitle = subtitle,
        icon = null,
    ) {
        if (busy) return@NavRow
        // A second press on an installed row removes the rules rather than
        // re-fetching them, so the row is its own undo.
        if (installedVersion != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    KeymanRuleDownloader.remove(context, binding.keyboardId)
                }
                installedVersion = null
                failed = false
                failure = null
            }
            return@NavRow
        }
        busy = true
        failed = false
        failure = null
        progress = 0
        scope.launch {
            val outcome = KeymanRuleDownloader.fetch(
                context = context,
                keyboardId = binding.keyboardId,
            ) { read, total ->
                if (total > 0) progress = ((read * 100) / total).toInt().coerceIn(0, 100)
            }
            when (outcome) {
                is KeymanRuleDownloader.Outcome.Installed -> installedVersion = outcome.version
                is KeymanRuleDownloader.Outcome.AlreadyCurrent -> installedVersion = outcome.version
                is KeymanRuleDownloader.Outcome.NotAvailable -> {
                    failed = true
                    failure = null
                }
                is KeymanRuleDownloader.Outcome.Failed -> {
                    failed = true
                    failure = outcome.message
                }
            }
            busy = false
        }
    }
}

/**
 * Offers to fetch a layout's typing rules at the moment it is switched on.
 *
 * Mirrors `rememberLayoutEnableGate`: a function the caller invokes after
 * enabling, which owns the dialog it needs. Asking here rather than leaving the
 * row to be noticed is the difference between a keyboard that works and one that
 * types Latin letters at someone who selected Khmer — the row explains that, but
 * only to a user who already suspected something was wrong.
 *
 * The check runs before the dialog, so a layout whose rules are already present
 * enables silently. [onInstalled] fires only on a real install, and is how the
 * row behind the dialog learns to stop offering.
 */
@Composable
internal fun rememberKeymanRulesPrompt(
    onInstalled: () -> Unit,
): (KeymanBinding, String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { KeymanRuleStore(context) }

    var asking by remember { mutableStateOf<Pair<KeymanBinding, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }

    asking?.let { (binding, layoutName) ->
        fun close() {
            asking = null
            busy = false
            progress = 0
            failure = null
        }
        AlertDialog(
            // A download in flight must not be dismissed out from under itself;
            // the buttons are the way out until it settles.
            onDismissRequest = { if (!busy) close() },
            title = { Text(stringResource(R.string.languages_keyman_prompt_title, layoutName)) },
            text = {
                Column {
                    Text(stringResource(R.string.languages_keyman_prompt_body))
                    failure?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it)
                    }
                    if (busy) {
                        Spacer(Modifier.height(12.dp))
                        if (progress > 0) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        failure = null
                        progress = 0
                        scope.launch {
                            val outcome = KeymanRuleDownloader.fetch(
                                context = context,
                                keyboardId = binding.keyboardId,
                            ) { read, total ->
                                if (total > 0) {
                                    progress = ((read * 100) / total).toInt().coerceIn(0, 100)
                                }
                            }
                            when (outcome) {
                                is KeymanRuleDownloader.Outcome.Installed,
                                is KeymanRuleDownloader.Outcome.AlreadyCurrent,
                                -> {
                                    onInstalled()
                                    close()
                                }
                                is KeymanRuleDownloader.Outcome.NotAvailable -> {
                                    busy = false
                                    failure =
                                        context.getString(R.string.languages_keyman_rules_failed)
                                }
                                is KeymanRuleDownloader.Outcome.Failed -> {
                                    busy = false
                                    failure = outcome.message
                                        ?: context.getString(R.string.languages_keyman_rules_failed)
                                }
                            }
                        }
                    },
                ) { Text(stringResource(R.string.languages_keyman_prompt_download)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { close() }) {
                    Text(stringResource(R.string.languages_keyman_prompt_later))
                }
            },
        )
    }

    return remember(store) {
        { binding, layoutName ->
            scope.launch {
                // The file check is a disk hit, so it happens off the main
                // thread and only decides whether to ask at all. A layout whose
                // rules are already here enables without a word.
                val missing = withContext(Dispatchers.IO) {
                    store.installedLabel(binding.keyboardId) == null
                }
                if (missing) asking = binding to layoutName
            }
        }
    }
}

/**
 * What the rules directory says about a keyboard: its version, [UNKNOWN_VERSION]
 * when the file is there without one, or null when it is not installed.
 *
 * One call rather than `hasRules` then `installedVersion`, because those two
 * hit the disk separately and can disagree if a download lands between them.
 */
private fun KeymanRuleStore.installedLabel(keyboardId: String): String? =
    if (hasRules(keyboardId)) installedVersion(keyboardId) ?: UNKNOWN_VERSION else null

/**
 * Rules are present but their version is not recorded, which is what a restored
 * backup or a hand-placed file looks like. Distinct from "installed", so the row
 * does not claim to know something it does not.
 */
private const val UNKNOWN_VERSION = "?"
