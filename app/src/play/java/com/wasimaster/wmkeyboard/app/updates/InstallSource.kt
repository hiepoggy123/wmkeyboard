package com.wasimaster.wmkeyboard.app.updates

import android.content.Context
import android.os.Build

/**
 * Who installed this app, as far as Play's update API is concerned.
 *
 * Play In-App Updates only works for an install the Play Store made. Asked
 * anything else, it fails with `ERROR_APP_NOT_OWNED` on every single call, and
 * because the settings activity checks on every resume, that was one warning
 * in the log per return to the app, forever, on any build flagged for Play but
 * installed by hand: a developer's own device, an internal-sharing link, an
 * APK someone pulled off a build server.
 *
 * So the question is asked once, up front, and a build Play did not install
 * simply has no updater rather than a broken one.
 */
internal fun Context.installedByPlay(): Boolean = runCatching {
    val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        packageManager.getInstallSourceInfo(packageName).installingPackageName
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstallerPackageName(packageName)
    }
    installer == PLAY_STORE_PACKAGE
}.getOrDefault(false)

/**
 * The Play Store's package name. Named in `src/play/AndroidManifest.xml` under
 * `<queries>` as well: from API 30, package visibility hides it from
 * `getInstallSourceInfo` unless the app says it wants to see it.
 */
private const val PLAY_STORE_PACKAGE = "com.android.vending"
