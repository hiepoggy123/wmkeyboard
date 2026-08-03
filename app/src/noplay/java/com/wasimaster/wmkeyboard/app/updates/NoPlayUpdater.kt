package com.wasimaster.wmkeyboard.app.updates

import androidx.compose.runtime.Composable

/**
 * The [rememberAppUpdater] of every channel that is not Play.
 *
 * This directory replaces `src/play/java` when `wmkb.enablePlayStore` is off,
 * which is what keeps Google's Play Core binary out of the F-Droid and
 * direct-download APKs entirely. The stub is not a fallback — it is the
 * correct answer for those builds: Play's update API refuses to work for an
 * install that Play did not make, so there is nothing here to fall back to.
 *
 * [NoAppUpdater] holds [UpdateState.Unsupported] forever, and every piece of
 * update UI draws nothing for that state, so no screen needs a build check.
 */
@Composable
internal fun rememberAppUpdater(): AppUpdater = NoAppUpdater
