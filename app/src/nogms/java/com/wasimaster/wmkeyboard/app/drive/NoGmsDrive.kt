package com.wasimaster.wmkeyboard.app.drive

import android.content.Context
import com.wasimaster.wmkeyboard.core.settings.sink.DriveTokenProvider

/**
 * The Drive side of a build with no Google Play services.
 *
 * Same package and same signatures as `src/gms/java`, exactly one of which is
 * on the source path. This is what F-Droid builds, and what makes the Drive
 * destination report itself unavailable rather than fail at run time: there is
 * no token provider, so `AutoBackupRunner.sinkFor` answers null for it and the
 * settings screen leaves the choice out.
 */
fun driveAuthorizer(): DriveAuthorizer = NoDriveAuthorizer

/**
 * No provider, which is what makes the Drive destination unusable here.
 *
 * It returns a constant and takes a parameter it ignores, both on purpose: the
 * signature has to match `src/gms/java` exactly, because which of the two is
 * compiled is a build-time choice and every caller is written against one shape.
 */
@Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
fun driveTokenProvider(context: Context): DriveTokenProvider? = null
