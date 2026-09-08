package com.wasimaster.wmkeyboard.app.updates

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.core.debug.DebugLog
import com.wasimaster.wmkeyboard.core.tools.ToolHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The updater of an F-Droid install: it looks, and it points.
 *
 * F-Droid signs every build with its own key, so an APK from this project's
 * GitHub release could never install over an F-Droid install however carefully
 * it was downloaded. Anything past "there is a new version, here is where it
 * is" would end at Android refusing the file. So this channel checks and links
 * out, and downloads nothing.
 *
 * It asks **F-Droid**, not GitHub, and that is the whole reason it is a
 * separate driver rather than the GitHub one with its buttons hidden. F-Droid
 * builds from source on its own schedule, so a tag that exists here is not a
 * version F-Droid has yet. A card that named a version and then sent the user
 * to a page still showing the one they have would be worse than no card.
 */
@Composable
internal fun rememberAppUpdater(): AppUpdater {
    val context = LocalContext.current.applicationContext
    val updater = remember(context) { FdroidAppUpdater(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(updater, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) updater.check()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return updater
}

/** Asks F-Droid what F-Droid has, and opens F-Droid. */
internal class FdroidAppUpdater(
    private val context: Context,
    private val prefs: UpdatePrefs = UpdatePrefs(context),
    private val now: () -> Long = System::currentTimeMillis,
) : AppUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var running: Job? = null

    init {
        // What was known last time, before anything is asked. Without this a
        // rotation would empty the card and then decline to refill it, because
        // the check that would have refilled it is inside its own interval.
        restore()
    }

    override val sourceNameRes: Int = R.string.update_source_fdroid

    /** Pressing the button leaves the app. There is no download to own. */
    override val startsExternally: Boolean = true

    override var includePrereleases: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) = Unit

    override var autoPrompt: Boolean
        get() = prefs.autoPrompt
        set(value) {
            prefs.autoPrompt = value
        }

    override fun check(userAsked: Boolean) {
        if (running?.isActive == true) return
        if (!userAsked &&
            !UpdateCheckGate.shouldAutoCheck(
                now = now(),
                lastCheckAt = prefs.lastCheckAt,
                rateLimitedUntil = 0L,
                state = _state.value,
            )
        ) {
            return
        }
        if (userAsked) _state.value = UpdateState.Checking
        running = scope.launch { fetch(userAsked) }
    }

    override fun start() {
        val candidate = GithubUpdateChecker.decodeCandidate(prefs.knownCandidate) ?: return
        // An https link rather than a package-specific scheme: the F-Droid
        // client claims f-droid.org links, so it opens if it is installed, and
        // a browser opens the same page if it is not. Nothing to declare under
        // <queries> either, since web links are always visible.
        val intent = Intent(Intent.ACTION_VIEW, candidate.releaseUrl.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { DebugLog.w(TAG, "could not open F-Droid: ${it.message.orEmpty()}") }
    }

    /** Nothing to install: F-Droid's own client does that part. */
    override fun install() = Unit

    override fun dismiss() {
        val available = _state.value as? UpdateState.Available ?: return
        prefs.snooze(available.versionCode, now())
        _state.value = available.copy(dismissed = true, promptOpen = false)
    }

    private fun restore() {
        val candidate = GithubUpdateChecker.decodeCandidate(prefs.knownCandidate) ?: return
        if (candidate.versionCode <= BuildConfig.VERSION_CODE) {
            prefs.clearCandidate()
            return
        }
        _state.value = offerOf(candidate, userAsked = false, allowPrompt = false)
    }

    private fun fetch(userAsked: Boolean) {
        val url = FdroidIndex.apiUrl(context.packageName)
        val body = runCatching { ToolHttp.get(url) }.getOrElse { error ->
            // A 404 is the ordinary answer while the app is not in F-Droid's
            // index yet, and is not something to report as a failure.
            DebugLog.d(TAG, "F-Droid index unavailable: ${error.message.orEmpty()}")
            if (userAsked) _state.value = UpdateState.UpToDate(userAsked = true)
            return
        }
        prefs.lastCheckAt = now()
        val index = FdroidIndex.decode(body)
        val newer = index?.let { FdroidIndex.newerThan(it, BuildConfig.VERSION_CODE) }
        if (newer == null) {
            prefs.clearCandidate()
            _state.value = UpdateState.UpToDate(userAsked)
            return
        }
        val candidate = UpdateCandidate(
            versionCode = newer.versionCode,
            versionName = newer.versionName,
            tag = "",
            assetName = "",
            url = "",
            sizeBytes = 0L,
            sha256 = null,
            publishedAtMillis = null,
            releaseUrl = FdroidIndex.packagePage(context.packageName),
        )
        prefs.knownCandidate = GithubUpdateChecker.encode(candidate)
        _state.value = offerOf(candidate, userAsked, allowPrompt = true)
    }

    private fun offerOf(
        candidate: UpdateCandidate,
        userAsked: Boolean,
        allowPrompt: Boolean,
    ): UpdateState = GithubUpdateChecker.offer(
        candidate = candidate,
        snoozed = prefs.isSnoozed(candidate.versionCode, now()),
        autoPrompt = prefs.autoPrompt,
        allowPrompt = allowPrompt,
        userAsked = userAsked,
        now = now(),
        fileVerified = false,
    )

    private companion object {
        const val TAG = "AppUpdates"
    }
}
