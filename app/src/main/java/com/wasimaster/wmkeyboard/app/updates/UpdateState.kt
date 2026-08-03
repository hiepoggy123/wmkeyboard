package com.wasimaster.wmkeyboard.app.updates

/**
 * Where an update stands.
 *
 * One type for both channels: a non-Play build sits on [Unsupported] forever
 * and every piece of update UI draws nothing, so no screen needs to know which
 * build it is in.
 */
internal sealed interface UpdateState {

    /** Not a Play build. Nothing to show, ever. */
    data object Unsupported : UpdateState

    /** Nothing asked yet. */
    data object Idle : UpdateState

    /** Waiting on Play. */
    data object Checking : UpdateState

    /**
     * Play answered: this is the newest version.
     *
     * [userAsked] is true only when the user pressed "Check for updates", and
     * is what lets the row say so instead of staying silent — good news is
     * worth a line when someone asked for it, and noise when nobody did.
     */
    data class UpToDate(val userAsked: Boolean) : UpdateState

    /**
     * A newer version exists and the user has not started it.
     *
     * [immediate] says which flow starting it runs: Play's blocking
     * full-screen update, or a background download. [sizeBytes] is what Play
     * reports for the download, and is 0 when Play does not say.
     *
     * [dismissed] means the user has already waved this version away, whether
     * by pressing "Not now" or by backing out of Play's own dialog. The card
     * stops drawing; the About row does not, because the update is still there
     * and a row that then claimed the app was up to date would be lying.
     */
    data class Available(
        val versionCode: Int,
        val sizeBytes: Long,
        val immediate: Boolean,
        val dismissed: Boolean = false,
    ) : UpdateState

    /** A flexible update is downloading in the background. */
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : UpdateState

    /** Downloaded and waiting for the user to press install. */
    data object Downloaded : UpdateState

    /** [AppUpdater.install] was called; the process is about to be replaced. */
    data object Installing : UpdateState

    /**
     * The check or the flow failed.
     *
     * [cancelled] separates "the user said no" from a real error: the first is
     * an ordinary outcome that should leave no wreckage on screen, the second
     * is worth a retry affordance.
     */
    data class Failed(val cancelled: Boolean) : UpdateState
}

/** Fraction downloaded, or null while the total is still unknown. */
internal val UpdateState.Downloading.fraction: Float?
    get() = if (totalBytes <= 0L) null else (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
