package com.wasimaster.wmkeyboard.app

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.settings.DataSaverStatus
import com.wasimaster.wmkeyboard.core.settings.DeviceNetworkState
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.MeteredDecision
import com.wasimaster.wmkeyboard.core.settings.MeteredFeature

/**
 * What data saving allows a download to do right now, in the settings app.
 *
 * The keyboard service keeps a `NetworkWatcher` running because it makes this
 * decision on every panel open; a settings screen asks once, when a button is
 * pressed, so it reads the connection there and then instead. The answer is
 * the same either way — [DataSaverSettings.appliesTo] against the network as
 * it stands.
 *
 * Session grants are deliberately not shared with the keyboard's: the two
 * processes ask separately, and a yes given to a 300 MB model is not a yes
 * given to a GIF grid.
 */
internal fun downloadDecisionNow(
    context: Context,
    settings: KeyboardSettings,
): MeteredDecision {
    val network = DeviceNetworkState(metered = isMeteredNow(context))
    val status = DataSaverStatus(
        active = settings.dataSaver.appliesTo(network),
        settings = settings.dataSaver,
    )
    return status.decide(MeteredFeature.DOWNLOADS)
}

/** [downloadDecisionNow] as a callback, for screens that ask on a button press. */
@Composable
internal fun rememberDownloadDecision(settings: KeyboardSettings): () -> MeteredDecision {
    val context = LocalContext.current
    return remember(settings.dataSaver) { { downloadDecisionNow(context, settings) } }
}

/**
 * The download is off while the connection is metered, and the user said so.
 *
 * A dialog rather than a silent no-op: the button they pressed has to answer
 * for itself, and the answer names the setting so it can be found. There is no
 * "download anyway" here on purpose — that is what "ask each time" is for, and
 * an override on a refusal would make the two settings the same one.
 */
@Composable
internal fun MeteredBlockedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.datasaver_blocked_title)) },
        text = { Text(stringResource(R.string.datasaver_blocked_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_ok)) }
        },
    )
}
