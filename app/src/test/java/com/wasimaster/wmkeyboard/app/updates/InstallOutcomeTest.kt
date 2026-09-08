package com.wasimaster.wmkeyboard.app.updates

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every way Android can answer an install, and what the user is told about it.
 *
 * The statuses are compile-time constants, so this never touches the framework.
 */
class InstallOutcomeTest {

    @Test
    fun `success cleans up and says nothing`() {
        val result = installOutcome(PackageInstaller.STATUS_SUCCESS)
        assertEquals(UpdateState.Idle, result.state)
        assertEquals(FileDisposition.DELETE, result.disposition)
    }

    @Test
    fun `backing out of Android's screen is an answer, not an error`() {
        val result = installOutcome(PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(UpdateState.Downloaded, result.state)
        // Keeping the file is the point: pressing Install again must not cost
        // the download a second time.
        assertEquals(FileDisposition.KEEP, result.disposition)
    }

    @Test
    fun `a device that forbids installs says so, and keeps the file`() {
        val result = installOutcome(PackageInstaller.STATUS_FAILURE_BLOCKED)
        assertEquals(
            UpdateState.Failed(cancelled = false, reason = UpdateFailure.INSTALL_BLOCKED),
            result.state,
        )
        assertEquals(FileDisposition.KEEP, result.disposition)
    }

    @Test
    fun `a signing conflict is named as one, and the file goes`() {
        val result = installOutcome(PackageInstaller.STATUS_FAILURE_CONFLICT)
        assertEquals(
            UpdateState.Failed(cancelled = false, reason = UpdateFailure.SIGNATURE_MISMATCH),
            result.state,
        )
        assertEquals(FileDisposition.DELETE, result.disposition)
    }

    @Test
    fun `an APK this device cannot take is not kept`() {
        val result = installOutcome(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
        assertEquals(
            UpdateState.Failed(cancelled = false, reason = UpdateFailure.INSTALL_FAILED),
            result.state,
        )
        assertEquals(FileDisposition.DELETE, result.disposition)
    }

    @Test
    fun `no room to install keeps the file, because the file is not the problem`() {
        val result = installOutcome(PackageInstaller.STATUS_FAILURE_STORAGE)
        assertEquals(
            UpdateState.Failed(cancelled = false, reason = UpdateFailure.NO_SPACE),
            result.state,
        )
        assertEquals(FileDisposition.KEEP, result.disposition)
    }

    @Test
    fun `an APK Android cannot parse is thrown away`() {
        val result = installOutcome(PackageInstaller.STATUS_FAILURE_INVALID)
        assertEquals(
            UpdateState.Failed(cancelled = false, reason = UpdateFailure.CORRUPT),
            result.state,
        )
        assertEquals(FileDisposition.DELETE, result.disposition)
    }

    @Test
    fun `an unrecognised status still produces something to say`() {
        val result = installOutcome(PackageInstaller.STATUS_FAILURE)
        assertEquals(
            UpdateState.Failed(cancelled = false, reason = UpdateFailure.INSTALL_FAILED),
            result.state,
        )
        assertEquals(FileDisposition.KEEP, result.disposition)
    }
}
