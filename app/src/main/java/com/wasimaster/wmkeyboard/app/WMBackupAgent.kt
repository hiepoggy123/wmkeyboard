package com.wasimaster.wmkeyboard.app

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import com.wasimaster.wmkeyboard.core.settings.CloudBackup

/**
 * Makes Android's backup opt-in.
 *
 * The manifest's `allowBackup` is read by the platform before any of this app's
 * code runs, so it can only ever be a build-time yes/no. Declaring an agent
 * turns that into a runtime decision: the platform still schedules a backup,
 * but it asks this class to produce the data, and with the switch off this
 * class produces none. Nothing is written, so nothing leaves the device — the
 * same outcome as `allowBackup="false"`, reached per user rather than per
 * build.
 *
 * `android:fullBackupOnly="true"` in the manifest keeps this on the Auto Backup
 * path, so the `super` call below does the ordinary whole-directory backup
 * filtered by `data_extraction_rules.xml` / `backup_rules.xml` — which is also
 * where the size limit is respected: Auto Backup's quota is a few tens of
 * megabytes, and those rules exclude everything bulky that can simply be
 * fetched again (models, downloaded dictionaries, scanner scratch files).
 * The key/value methods exist only because [BackupAgent] declares them
 * abstract; this app has no key/value dataset.
 *
 * Restore is deliberately not gated. A restore can only replay data that some
 * earlier install was allowed to back up, and it lands on a fresh install whose
 * own switch has not been set yet — gating it there would discard a backup the
 * user opted into making.
 */
class WMBackupAgent : BackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        if (!CloudBackup.isEnabled(this)) return
        super.onFullBackup(data)
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit
}
