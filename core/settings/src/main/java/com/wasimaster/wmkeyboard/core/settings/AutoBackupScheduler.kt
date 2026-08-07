package com.wasimaster.wmkeyboard.core.settings

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

/**
 * Keeps the platform's idea of when to back up in step with the user's.
 *
 * `JobScheduler` rather than WorkManager: this is one periodic job with no
 * chaining, no observers and no pre-API-23 story to tell, so WorkManager's
 * whole offer is unused, and it would cost a dependency that drags in Room and
 * an `androidx.startup` provider running at every process start. In a keyboard
 * that is cold-start latency on the path the user feels.
 *
 * Not `setPersisted`, which would need `RECEIVE_BOOT_COMPLETED`. The job is
 * instead re-established by whoever starts first after a reboot, and in a
 * keyboard that is the keyboard — within seconds of anyone typing.
 */
object AutoBackupScheduler {

    /** Arbitrary and permanent. Changing it orphans whatever is scheduled. */
    private const val JOB_ID = 20260807

    /**
     * The job runs in `:app`, which a library module cannot name in code. Same
     * string-component approach `AppCatalog` uses for launching activities.
     * `AutoBackupSchedulerTest` reads the manifest and checks this still names
     * a service that exists, since the compiler cannot.
     */
    const val SERVICE_CLASS = "com.wasimaster.wmkeyboard.app.AutoBackupJobService"

    private const val HOUR_MS = 60L * 60L * 1000L

    /** Retry delay after a failure the job asked to have retried. */
    private const val BACKOFF_MS = 30L * 60L * 1000L

    /**
     * Schedules, reschedules or cancels to match [settings].
     *
     * Safe to call as often as you like, and it is called from process start,
     * which matters more than it looks: handing `JobScheduler` a periodic job
     * restarts its period. Rescheduling unconditionally from the keyboard's
     * `onCreate` would mean a keyboard used more often than once a day never
     * reaches the end of a single period, and the backup never runs at all.
     * So an already-correct job is left exactly where it is.
     */
    fun sync(context: Context, settings: AutoBackupSettings) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val pending = runCatching { scheduler.getPendingJob(JOB_ID) }.getOrNull()

        if (!settings.enabled || !settings.destinationConfigured) {
            if (pending != null) runCatching { scheduler.cancel(JOB_ID) }
            return
        }

        val intervalMs = settings.intervalHours.coerceAtLeast(1) * HOUR_MS
        if (pending != null && pending.intervalMillis == intervalMs) return

        val job = JobInfo.Builder(JOB_ID, ComponentName(context.packageName, SERVICE_CLASS))
            .setPeriodic(intervalMs)
            // Charging only, deliberately without `setRequiresDeviceIdle`.
            // Both together is what makes the platform's own backups so hard to
            // predict, and the work here is a few seconds of file copying.
            .setRequiresCharging(true)
            .setBackoffCriteria(BACKOFF_MS, JobInfo.BACKOFF_POLICY_LINEAR)
            .build()
        runCatching { scheduler.schedule(job) }
    }
}
