package com.wasimaster.wmkeyboard.core.notify

import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import com.wasimaster.wmkeyboard.common.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * How far along one download is, in the only terms the shade cares about.
 *
 * Every download in this app has its own status type, with its own stages —
 * verifying a checksum, packing a trie, unzipping a pack. None of that belongs
 * in a notification, so each caller flattens its own status into this before
 * handing it over.
 */
sealed interface DownloadProgress {

    /** Bytes so far, and the total when the source reported one. */
    data class Running(val bytes: Long, val total: Long) : DownloadProgress

    /** Finished, and whatever the file was for is ready to use. */
    data object Done : DownloadProgress

    /** Stopped on an error. [reason] is a sentence the caller already resolved. */
    data class Failed(val reason: String?) : DownloadProgress

    /**
     * Not downloading, and nothing to say: cancelled, deleted, or never
     * started. Clears the notification rather than replacing it.
     */
    data object Gone : DownloadProgress
}

/**
 * The shade's view of a download.
 *
 * A download in this app belongs to a process-wide manager, not to the screen
 * that started it, and the screen is usually gone long before the download is.
 * That is the whole case for notifying: without it, a 400 MB voice model
 * finishes with nobody told, and a failure halfway is discovered days later by
 * someone wondering why their dictionary never appeared.
 *
 * Callers hand over a flow rather than calling progress in themselves, because
 * the managers already publish one and the alternative is a posting site inside
 * every one of them. [watch] is idempotent per [key]: starting the same
 * download twice replaces the collector instead of stacking two.
 *
 * Nothing here keeps the process alive — this app does no background work — so
 * a download whose process dies takes its notification with it, via the timeout
 * every progress post carries. See [WmNotifications.PROGRESS_TIMEOUT_MS].
 */
object DownloadNotifications {

    /**
     * How often a running download may redraw its notification.
     *
     * The managers report bytes about four times a second, which is right for
     * a progress bar on screen and far too often for a row in the shade.
     */
    private const val POST_INTERVAL_MS = 900L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val watching = mutableMapOf<String, Job>()

    /**
     * Follows one download to its end and keeps the shade in step.
     *
     * [key] identifies the download (the same string the manager keys its own
     * state map by is ideal). [title] is what the user calls the thing being
     * downloaded — "Bangla dictionary", "Whisper small" — resolved by the
     * caller, since the words live in the caller's module.
     */
    fun watch(context: Context, key: String, title: String, flow: Flow<DownloadProgress>) {
        val app = context.applicationContext
        val id = NotificationIds.forDownload(key)
        synchronized(watching) {
            watching.remove(key)?.cancel()
            val job = scope.launch {
                var lastPostAt = 0L
                flow.collect { progress ->
                    when (progress) {
                        is DownloadProgress.Running -> {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastPostAt < POST_INTERVAL_MS) return@collect
                            lastPostAt = now
                            postRunning(app, id, title, progress)
                        }
                        DownloadProgress.Done -> {
                            postDone(app, id, title)
                            stop(key)
                        }
                        is DownloadProgress.Failed -> {
                            postFailed(app, id, title, progress.reason)
                            stop(key)
                        }
                        DownloadProgress.Gone -> {
                            WmNotifications.cancel(app, id)
                            stop(key)
                        }
                    }
                }
            }
            watching[key] = job
        }
    }

    /** Drops a download's notification and stops following it. */
    fun clear(context: Context, key: String) {
        stop(key)
        WmNotifications.cancel(context, NotificationIds.forDownload(key))
    }

    private fun stop(key: String) {
        synchronized(watching) { watching.remove(key) }?.cancel()
    }

    private fun postRunning(
        context: Context,
        id: Int,
        title: String,
        progress: DownloadProgress.Running,
    ) {
        val known = progress.total > 0L
        val percent = if (known) {
            (progress.bytes * PERCENT / progress.total).toInt().coerceIn(0, PERCENT.toInt())
        } else {
            0
        }
        WmNotifications.post(context, NotificationKind.DOWNLOADS, id) {
            setContentTitle(title)
            setContentText(
                if (known) {
                    context.getString(
                        R.string.common_notify_download_progress,
                        Formatter.formatShortFileSize(context, progress.bytes),
                        Formatter.formatShortFileSize(context, progress.total),
                    )
                } else {
                    Formatter.formatShortFileSize(context, progress.bytes)
                },
            )
            setProgress(PERCENT.toInt(), percent, !known)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setSilent(true)
            setTimeoutAfter(WmNotifications.PROGRESS_TIMEOUT_MS)
            openApp(context)
        }
    }

    private fun postDone(context: Context, id: Int, title: String) {
        WmNotifications.post(context, NotificationKind.DOWNLOADS, id) {
            setContentTitle(title)
            setContentText(context.getString(R.string.common_notify_download_done))
            setAutoCancel(true)
            setSilent(true)
            openApp(context)
        }
    }

    private fun postFailed(context: Context, id: Int, title: String, reason: String?) {
        WmNotifications.post(context, NotificationKind.DOWNLOADS, id) {
            setContentTitle(context.getString(R.string.common_notify_download_failed, title))
            reason?.let {
                setContentText(it)
                setStyle(NotificationCompat.BigTextStyle().bigText(it))
            }
            setAutoCancel(true)
            openApp(context)
        }
    }

    /** Tapping any of these opens the app, which is where the download lives. */
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

    /** The scale a notification's progress bar is drawn on. */
    private const val PERCENT = 100L
}
