package com.wasimaster.wmkeyboard.app.updates

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Play In-App Updates, as the settings app sees them.
 *
 * The interface is here, in `main`, so every screen compiles in every build
 * channel. The implementation is not: `src/play/java` drives Google's
 * `AppUpdateManager`, and `src/noplay/java` supplies a version of
 * [rememberAppUpdater] that returns [NoAppUpdater]. Only one of those two
 * directories is on the compile path (see `app/build.gradle.kts`), so an
 * F-Droid or direct-download build contains no Play Core code at all — which
 * is both what F-Droid requires and honest about what those installs can do:
 * in-app updates only work when Play itself installed the app.
 *
 * ## Why this app drives the flow by hand
 *
 * The keyboard shares a process with this activity. Finishing an update
 * restarts that process, so [install] is never called on the app's own
 * initiative — a keyboard that vanishes mid-sentence is a much worse bug than
 * a version that is a day old. Every path that ends in a restart starts with
 * the user pressing something.
 */
internal interface AppUpdater {

    /** What the updater knows right now. Drives every piece of update UI. */
    val state: StateFlow<UpdateState>

    /**
     * Whether the app may open Play's own update dialog by itself, or only
     * draw the card and wait to be pressed. The user's setting; see
     * [UpdatePrefs.autoPrompt] for why it defaults to on.
     */
    var autoPrompt: Boolean

    /**
     * Asks Play what is available.
     *
     * [userAsked] separates the check the About screen's row runs from the one
     * the activity runs when it resumes: only the latter is allowed to open a
     * Play dialog on its own, and only the former reports "you are up to date"
     * as a result worth showing.
     */
    fun check(userAsked: Boolean = false)

    /**
     * Starts the update the user just agreed to: Play's own confirmation
     * dialog, then a background download for a flexible update, or the
     * full-screen blocking flow for an immediate one.
     */
    fun start()

    /**
     * Installs a finished flexible download and restarts the app. Call this
     * only from a control the user pressed — see the class note.
     */
    fun install()

    /**
     * "Not now": stops this version from prompting again for
     * [UpdatePolicy.SNOOZE_DAYS]. It never hides a high-priority update, which
     * is the whole point of that priority.
     */
    fun dismiss()
}

/** The updater for builds that have no Play Store behind them. */
internal object NoAppUpdater : AppUpdater {
    override val state: StateFlow<UpdateState> = MutableStateFlow(UpdateState.Unsupported)
    override var autoPrompt: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) = Unit
    override fun check(userAsked: Boolean) = Unit
    override fun start() = Unit
    override fun install() = Unit
    override fun dismiss() = Unit
}

/**
 * The updater every settings screen reads.
 *
 * Static rather than threaded through the navigation graph: the update card on
 * the home screen and the rows on the About screen are far apart in the tree
 * and must never disagree about the same download. Defaults to [NoAppUpdater]
 * so a preview or a screen composed outside [com.wasimaster.wmkeyboard.app.MainActivity]
 * simply shows nothing.
 */
internal val LocalAppUpdater = staticCompositionLocalOf<AppUpdater> { NoAppUpdater }
