package com.wasimaster.wmkeyboard.app.updates

/**
 * Where an update stands.
 *
 * One type for every channel. A build with no updater behind it sits on
 * [Unsupported] forever and every piece of update UI draws nothing, so no
 * screen needs to know which build it is in. The channels differ in how far
 * along this list they can get: Play and GitHub run the whole thing, while
 * F-Droid stops at [Available] and sends the user to its own client.
 */
internal sealed interface UpdateState {

    /** No updater in this build, or the one there is cannot work here. */
    data object Unsupported : UpdateState

    /** Nothing asked yet. */
    data object Idle : UpdateState

    /** Waiting on the store or on GitHub. */
    data object Checking : UpdateState

    /**
     * This is the newest version.
     *
     * [userAsked] is true only when the user pressed "Check for updates", and
     * is what lets the row say so instead of staying silent. Good news is
     * worth a line when someone asked for it, and noise when nobody did.
     */
    data class UpToDate(val userAsked: Boolean) : UpdateState

    /**
     * A newer version exists and the user has not started it.
     *
     * [immediate] says which flow starting it runs: a blocking full-screen
     * update, or a background download. [sizeBytes] is the download size, and
     * is 0 when the source does not report one.
     *
     * [versionName] and [releaseUrl] are what a release has and a Play update
     * does not: a name to put in front of the user, and a page to read. Both
     * are null on Play, where the card names no version at all.
     *
     * [dismissed] means the user has already waved this version away, whether
     * by pressing "Not now" or by backing out of the store's own dialog. The
     * card stops drawing; the About row does not, because the update is still
     * there and a row that then claimed the app was up to date would be lying.
     *
     * [promptOpen] asks the settings activity to put the offer in front of the
     * user once, as a dialog. It is what [UpdatePolicy] decides and the user's
     * "Ask me about updates" switch governs.
     */
    data class Available(
        val versionCode: Int,
        val sizeBytes: Long,
        val immediate: Boolean,
        val dismissed: Boolean = false,
        val versionName: String? = null,
        val releaseUrl: String? = null,
        val promptOpen: Boolean = false,
    ) : UpdateState

    /** The update is downloading. */
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : UpdateState

    /** Downloaded and waiting for the user to press install. */
    data object Downloaded : UpdateState

    /** [AppUpdater.install] was called; the process is about to be replaced. */
    data object Installing : UpdateState

    /**
     * The check, the download or the install failed.
     *
     * [cancelled] separates "the user said no" from a real error: the first is
     * an ordinary outcome that should leave no wreckage on screen, the second
     * is worth a retry affordance. [reason] is what the row says under its
     * name, and is null where the source gave nothing to say.
     */
    data class Failed(val cancelled: Boolean, val reason: UpdateFailure? = null) : UpdateState
}

/**
 * Why an update did not happen, in the terms the user can act on.
 *
 * Deliberately coarser than the errors underneath it. The point of each case
 * is that it earns a different sentence: one tells the user to wait, one to
 * free space, one that this install and that release do not belong to each
 * other. Anything that would earn the same sentence shares a case.
 */
internal enum class UpdateFailure {
    /** No connection, a timeout, or an HTTP status with nothing to say. */
    NETWORK,

    /** GitHub is rate limiting this address. Comes with a time to wait. */
    RATE_LIMITED,

    /** The release exists but carries no file for this device or edition. */
    NO_ASSET,

    /** Not enough room to download the file, or to install it once downloaded. */
    NO_SPACE,

    /** The download does not match the checksum the release published. */
    CORRUPT,

    /**
     * The release is signed with a different key than this install, so Android
     * would refuse it. The honest end of a sideloaded debug build's update
     * path, and the one failure where the answer is to go and download it by
     * hand rather than to try again.
     */
    SIGNATURE_MISMATCH,

    /** The device does not let this app install packages. */
    INSTALL_BLOCKED,

    /** Android took the file and could not install it. */
    INSTALL_FAILED,
}

/** Fraction downloaded, or null while the total is still unknown. */
internal val UpdateState.Downloading.fraction: Float?
    get() = if (totalBytes <= 0L) null else (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
