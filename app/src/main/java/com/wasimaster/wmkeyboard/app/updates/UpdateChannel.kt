package com.wasimaster.wmkeyboard.app.updates

import com.wasimaster.wmkeyboard.BuildConfig

/**
 * Which update channel this build was compiled for.
 *
 * The drivers live in source directories only one of which is on the compile
 * path, so code in `src/main` cannot ask them anything: it has to read the
 * build flags. Those flags are read here and nowhere else, because the update
 * card, the About rows, the permissions screen and the settings search index
 * all have to agree about which of them exist, and four copies of the same
 * boolean algebra would eventually disagree.
 */
internal object UpdateChannel {

    // Plain vals rather than consts: these BuildConfig fields are declared as
    // boxed `Boolean`, so they are not compile-time constants. R8 still folds
    // the branches away in a release build.

    /** Downloads and installs the APK itself. Everything in this file follows from it. */
    val GITHUB = BuildConfig.ENABLE_GITHUB_UPDATES

    /** Checks F-Droid's index and opens F-Droid. Never downloads. */
    val FDROID = BuildConfig.ENABLE_FDROID

    /** Whether this build has an Updates group at all. Currently every build does. */
    val ANY = GITHUB || FDROID || BuildConfig.ENABLE_PLAY_STORE
}
