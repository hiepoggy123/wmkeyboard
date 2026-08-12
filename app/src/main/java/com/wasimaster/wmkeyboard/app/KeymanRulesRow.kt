package com.wasimaster.wmkeyboard.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
 * Nothing here downloads on its own. It is one row, one tap, and it names the
 * source, because a keyboard that reaches the network unasked is not something
 * to do quietly.
 */
@Composable
internal fun KeymanRulesRow(binding: KeymanBinding) {
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
    LaunchedEffect(binding.keyboardId, busy) {
        if (busy) return@LaunchedEffect
        installedVersion = withContext(Dispatchers.IO) {
            if (store.hasRules(binding.keyboardId)) {
                store.installedVersion(binding.keyboardId) ?: UNKNOWN_VERSION
            } else {
                null
            }
        }
    }

    val subtitle = when {
        busy && progress > 0 -> stringResource(R.string.languages_keyman_rules_downloading, progress)
        busy -> stringResource(R.string.languages_keyman_rules_checking)
        failed -> failure ?: stringResource(R.string.languages_keyman_rules_failed)
        installedVersion == UNKNOWN_VERSION -> stringResource(R.string.languages_keyman_rules_installed_unknown)
        installedVersion != null ->
            stringResource(R.string.languages_keyman_rules_installed, installedVersion.orEmpty())
        else -> stringResource(R.string.languages_keyman_rules_missing)
    }

    NavRow(
        title = stringResource(R.string.languages_keyman_rules_title),
        subtitle = subtitle,
        icon = null,
    ) {
        if (busy) return@NavRow
        // A second tap on an installed row removes the rules rather than
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
 * Rules are present but their version is not recorded, which is what a restored
 * backup or a hand-placed file looks like. Distinct from "installed", so the row
 * does not claim to know something it does not.
 */
private const val UNKNOWN_VERSION = "?"
