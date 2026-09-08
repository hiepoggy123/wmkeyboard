package com.wasimaster.wmkeyboard.app.updates

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.notify.NotificationIds
import com.wasimaster.wmkeyboard.core.notify.NotificationKind
import com.wasimaster.wmkeyboard.core.notify.WmNotifications

/**
 * The update, in the notification shade.
 *
 * GitHub-channel only, and for the same reason the channel owns everything
 * else about its download: it is the only one where *this app* is doing the
 * fetching, so it is the only one where nothing else would say anything. Play
 * posts its own download notification, and F-Droid's client posts its own.
 *
 * The one thing it deliberately does not carry is an Install button. Finishing
 * an update restarts the process the keyboard runs in, and a button in the
 * shade is pressable while the user is typing in another app — the exact
 * moment the keyboard must not disappear. Tapping opens the settings app,
 * where the Install control comes with the sentence explaining what it costs.
 * See [AppUpdater] for the rule this follows.
 */
internal object UpdateNotifications {

    /** Redraws the shade for one state. Called for every state the manager reaches. */
    fun render(context: Context, state: UpdateState) {
        when (state) {
            is UpdateState.Downloading -> downloading(context, state)
            UpdateState.Downloaded -> ready(context)
            is UpdateState.Failed ->
                if (state.cancelled) clear(context) else failed(context, state)
            // Nothing in flight and nothing waiting: an offer nobody has
            // accepted belongs on the card, not in the shade, and an install
            // that is running is about to take the process with it anyway.
            UpdateState.Unsupported,
            UpdateState.Idle,
            UpdateState.Checking,
            UpdateState.Installing,
            is UpdateState.UpToDate,
            is UpdateState.Available,
            -> clear(context)
        }
    }

    fun clear(context: Context) {
        WmNotifications.cancel(context, NotificationIds.UPDATE)
    }

    private fun downloading(context: Context, state: UpdateState.Downloading) {
        val fraction = state.fraction
        WmNotifications.post(context, NotificationKind.UPDATES, NotificationIds.UPDATE) {
            setContentTitle(context.getString(R.string.update_notify_downloading_title))
            if (state.totalBytes > 0L) {
                setContentText(
                    context.getString(
                        CommonR.string.common_notify_download_progress,
                        Formatter.formatShortFileSize(context, state.bytesDownloaded),
                        Formatter.formatShortFileSize(context, state.totalBytes),
                    ),
                )
            }
            setProgress(
                PERCENT,
                ((fraction ?: 0f) * PERCENT).toInt(),
                fraction == null,
            )
            setOngoing(true)
            setSilent(true)
            setOnlyAlertOnce(true)
            // This app does no background work: if the process holding the
            // download dies, the shade must not keep claiming it is running.
            setTimeoutAfter(WmNotifications.PROGRESS_TIMEOUT_MS)
            addAction(
                0,
                context.getString(CommonR.string.common_cancel),
                broadcast(context, UpdateActionReceiver.ACTION_CANCEL),
            )
            openApp(context)
        }
    }

    private fun ready(context: Context) {
        WmNotifications.post(context, NotificationKind.UPDATES, NotificationIds.UPDATE) {
            setContentTitle(context.getString(R.string.update_notify_ready_title))
            setContentText(context.getString(R.string.update_notify_ready_body))
            setAutoCancel(true)
            openApp(context)
        }
    }

    private fun failed(context: Context, state: UpdateState.Failed) {
        val reason = context.getString(state.reason.subtitle())
        WmNotifications.post(context, NotificationKind.UPDATES, NotificationIds.UPDATE) {
            setContentTitle(context.getString(R.string.update_notify_failed_title))
            setContentText(reason)
            setAutoCancel(true)
            openApp(context)
        }
    }

    private fun NotificationCompat.Builder.openApp(context: Context) {
        val intent = WmNotifications.launchIntent(context) ?: return
        setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    private fun broadcast(context: Context, action: String): PendingIntent {
        val intent = Intent(context, UpdateActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The scale Android draws a notification's progress bar on. */
    private const val PERCENT = 100
}

/**
 * The one button the update notification carries.
 *
 * Cancelling is the only update action safe to take from the shade: it stops
 * something, keeps the partial file for a later resume, and cannot restart the
 * process under a user who is mid-sentence.
 */
class UpdateActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL) return
        GithubUpdateManager.cancel()
        UpdateNotifications.clear(context)
    }

    companion object {
        const val ACTION_CANCEL = "com.wasimaster.wmkeyboard.action.CANCEL_UPDATE"
    }
}
