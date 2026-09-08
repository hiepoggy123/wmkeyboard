package com.wasimaster.wmkeyboard.app.updates

import android.content.pm.PackageInstaller

/** What to do with the downloaded file once Android has reported an install. */
internal enum class FileDisposition {
    /** Leave it: the same file can be offered again without downloading it twice. */
    KEEP,

    /** Delete it: it will never install on this device, so it is only taking room. */
    DELETE,
}

/** An install status, turned into a state and a decision about the file. */
internal data class InstallResult(
    val state: UpdateState,
    val disposition: FileDisposition,
)

/**
 * What each of Android's install statuses means to this app.
 *
 * Deliberately a pure function over the integer, so every branch is testable
 * without an installer, a session, or a device. The constants are compile-time
 * ints, so they inline and the test never touches the Android framework.
 *
 * Nothing here handles `STATUS_PENDING_USER_ACTION`: that one is not an
 * outcome, it is Android asking for a screen to be shown, and it is answered
 * by launching the intent that came with it rather than by moving the state.
 */
internal fun installOutcome(status: Int, cancelled: Boolean = false): InstallResult = when (status) {
    PackageInstaller.STATUS_SUCCESS ->
        // The process is normally dead before this arrives, and the receiver
        // that gets it is running in the new version. It has one job: clean up.
        InstallResult(UpdateState.Idle, FileDisposition.DELETE)

    // The user backed out of Android's own screen. Their answer, not an error,
    // and the file is still good for when they change their mind.
    PackageInstaller.STATUS_FAILURE_ABORTED ->
        InstallResult(UpdateState.Downloaded, FileDisposition.KEEP)

    // The device policy, or the user, does not let this app install packages.
    PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallResult(
        UpdateState.Failed(cancelled = false, reason = UpdateFailure.INSTALL_BLOCKED),
        FileDisposition.KEEP,
    )

    // Signed by a different key, or otherwise not a version of this app. The
    // pre-install check should have caught it, so reaching here means the two
    // disagreed and the file is worthless either way.
    PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallResult(
        UpdateState.Failed(cancelled = false, reason = UpdateFailure.SIGNATURE_MISMATCH),
        FileDisposition.DELETE,
    )

    // Wrong ABI, too old an Android, a manifest this device will not take.
    // Another release will not be built for it either, so the file goes.
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallResult(
        UpdateState.Failed(cancelled = false, reason = UpdateFailure.INSTALL_FAILED),
        FileDisposition.DELETE,
    )

    // Not enough room to stage the install. The file is fine; the device is full.
    PackageInstaller.STATUS_FAILURE_STORAGE -> InstallResult(
        UpdateState.Failed(cancelled = false, reason = UpdateFailure.NO_SPACE),
        FileDisposition.KEEP,
    )

    // Android could not parse it. The checksum passed, so this is not a broken
    // download; it is a file that is not the APK it claimed to be.
    PackageInstaller.STATUS_FAILURE_INVALID -> InstallResult(
        UpdateState.Failed(cancelled = false, reason = UpdateFailure.CORRUPT),
        FileDisposition.DELETE,
    )

    else -> InstallResult(
        UpdateState.Failed(cancelled = cancelled, reason = UpdateFailure.INSTALL_FAILED),
        FileDisposition.KEEP,
    )
}
