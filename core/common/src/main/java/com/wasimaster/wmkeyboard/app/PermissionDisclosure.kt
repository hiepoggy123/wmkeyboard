package com.wasimaster.wmkeyboard.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.core.net.toUri
import com.wasimaster.wmkeyboard.common.R

/**
 * One prominent disclosure: what the keyboard is about to ask Android for, what
 * it does with the answer, and where that data goes.
 *
 * Google Play's User Data policy requires this for every permission that
 * reaches personal or sensitive data, and it is stricter than it first looks —
 * the disclosure has to be *inside the app*, has to appear *before* the system
 * permission dialog, has to name the data and the use in the app's own words,
 * and has to be refusable without granting anything. A description in the
 * settings row, the store listing or the privacy policy does not satisfy any of
 * those on its own.
 *
 * An input method draws that scrutiny harder than most apps do: a keyboard that
 * asks for the microphone, the camera, contacts and the photo library is
 * shaped exactly like a keylogger from the outside, so every one of those
 * requests goes through [PermissionRequestActivity] (from the IME, which cannot
 * show a permission dialog itself) or [rememberDisclosedPermissionRequest]
 * (from the settings app) rather than calling `launch()` directly. There is no
 * fourth path, and adding one is how the next review gets failed.
 *
 * The copy lives in [PermissionDisclosures] next to the permission it belongs
 * to so the two cannot drift; keep it truthful rather than reassuring, since
 * this text is also what the Play Data safety form has to agree with.
 */
data class PermissionDisclosure(
    /** The Android permission this disclosure precedes. */
    val permission: String,
    /** Dialog heading: the permission and the feature that wants it. */
    @StringRes val titleRes: Int,
    /** What is read, why, where it goes, and that the feature is optional. */
    @StringRes val bodyRes: Int,
)

/** The disclosure copy for every runtime permission this app asks for. */
object PermissionDisclosures {

    val MICROPHONE = PermissionDisclosure(
        permission = Manifest.permission.RECORD_AUDIO,
        titleRes = R.string.common_permission_microphone_title,
        bodyRes = R.string.common_permission_microphone_body,
    )

    val CAMERA = PermissionDisclosure(
        permission = Manifest.permission.CAMERA,
        titleRes = R.string.common_permission_camera_title,
        bodyRes = R.string.common_permission_camera_body,
    )

    val CALENDAR = PermissionDisclosure(
        permission = Manifest.permission.READ_CALENDAR,
        titleRes = R.string.common_permission_calendar_title,
        bodyRes = R.string.common_permission_calendar_body,
    )

    val CONTACT_NAMES = PermissionDisclosure(
        permission = Manifest.permission.READ_CONTACTS,
        titleRes = R.string.common_permission_contact_names_title,
        bodyRes = R.string.common_permission_contact_names_body,
    )

    val CONTACT_EMAILS = PermissionDisclosure(
        permission = Manifest.permission.READ_CONTACTS,
        titleRes = R.string.common_permission_contact_emails_title,
        bodyRes = R.string.common_permission_contact_emails_body,
    )

    /**
     * Screenshots in the clipboard. Android has no screenshots-only permission —
     * granting this opens the whole photo library — so the disclosure says so
     * rather than implying a narrower grant than the user is actually giving.
     */
    val IMAGES = PermissionDisclosure(
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        },
        titleRes = R.string.common_permission_images_title,
        bodyRes = R.string.common_permission_images_body,
    )

    /**
     * Save-to-gallery below API 29. The old permission covers reading as well as
     * writing, which the copy admits rather than glossing over.
     */
    val STORAGE = PermissionDisclosure(
        permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
        titleRes = R.string.common_permission_storage_title,
        bodyRes = R.string.common_permission_storage_body,
    )
}

/**
 * Where a permission stands, from the point of view of asking for it again.
 *
 * The distinction that matters is [BLOCKED]: after two refusals Android stops
 * showing the dialog at all and `requestPermissions` returns "denied" without
 * anything appearing on screen. A button that silently does nothing is worse
 * than no button, so that case sends the user to the app's permission settings
 * instead of firing a request that cannot be seen.
 */
enum class PermissionState {
    /** Already held; nothing to ask for. */
    GRANTED,

    /** Never asked, or refused once — the system dialog will still appear. */
    ASKABLE,

    /** Refused for good (or restricted by policy): only Settings can grant it now. */
    BLOCKED,
}

/**
 * Remembers which permissions have been asked for at least once.
 *
 * `shouldShowRequestPermissionRationale` cannot tell "never asked" from
 * "permanently denied" — both answer false — so the ask has to be recorded
 * somewhere to distinguish them. Device-protected storage, so this works in
 * the same direct-boot window the keyboard itself runs in; the contents are one
 * boolean per permission and nothing private.
 */
private object PermissionAsks {

    private const val PREFS = "permission_asks"

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mark(context: Context, permission: String) {
        runCatching { prefs(context).edit { putBoolean(permission, true) } }
    }

    fun asked(context: Context, permission: String): Boolean =
        runCatching { prefs(context).getBoolean(permission, false) }.getOrDefault(false)
}

/**
 * Whether [permission] can still be asked for, or only granted from Settings.
 *
 * One known imprecision: revoking a granted permission from Settings (or
 * Android's auto-reset for unused apps) clears the platform's own "don't ask
 * again" flag while the ask is still recorded here, so the next attempt routes
 * to Settings even though the prompt would have worked. That errs toward a
 * screen that always works rather than one that may silently do nothing, which
 * is the right way round.
 */
fun permissionState(activity: Activity, permission: String): PermissionState = when {
    activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ->
        PermissionState.GRANTED
    // Refused once: Android still shows the dialog, and wants a reason first.
    activity.shouldShowRequestPermissionRationale(permission) -> PermissionState.ASKABLE
    // Never asked: the first request always reaches the user.
    !PermissionAsks.asked(activity, permission) -> PermissionState.ASKABLE
    else -> PermissionState.BLOCKED
}

/** The app's own page in system Settings, where a blocked permission can still be granted. */
private fun appSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData("package:${context.packageName}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * The disclosure dialog: heading, the explanation, and an explicit choice.
 *
 * Dismissing (the button, the scrim or Back) must leave without asking for
 * anything — Play requires the disclosure to be refusable, and the feature
 * behind it is expected to keep working in its permission-less state.
 *
 * When [blocked], the disclosure still has to be shown (the user is on their
 * way to granting the permission, so Play's requirement applies exactly as it
 * does to the prompt) but the button leads to Settings rather than to a dialog
 * Android will not draw.
 */
@Composable
internal fun DisclosureDialog(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    blocked: Boolean = false,
    @StringRes continueLabelRes: Int =
        if (blocked) R.string.common_open_system_settings else R.string.common_continue,
) {
    val body = stringResource(bodyRes)
    // Two resources rather than one: the note only applies when Android has
    // stopped drawing the system dialog, and every disclosure body shares it.
    val bodyText = if (blocked) {
        body + "\n\n" + stringResource(R.string.common_disclosure_blocked_body)
    } else {
        body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            // Long bodies on a short screen: scroll rather than clip, because a
            // clipped disclosure is not a disclosure.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(bodyText, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(continueLabelRes)) } },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_disclosure_not_now))
            }
        },
    )
}

/** Convenience wrapper for the runtime-permission case. */
@Composable
fun PermissionDisclosureDialog(
    disclosure: PermissionDisclosure,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    blocked: Boolean = false,
) = DisclosureDialog(
    titleRes = disclosure.titleRes,
    bodyRes = disclosure.bodyRes,
    onContinue = onContinue,
    onDismiss = onDismiss,
    blocked = blocked,
)

/**
 * Settings-app entry point: returns a lambda that shows the disclosure and, if
 * the user continues, asks Android for the permission. [onGranted] runs only on
 * a grant, so the caller can flip its setting on there and nowhere else.
 */
@Composable
fun rememberDisclosedPermissionRequest(
    disclosure: PermissionDisclosure,
    onGranted: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onGranted() }
    var showing by remember { mutableStateOf(false) }
    if (showing) {
        // Read at show time, not at composition time: the user may have changed
        // the permission in Settings since this screen was drawn.
        val activity = context.findActivity()
        val blocked = activity != null &&
            permissionState(activity, disclosure.permission) == PermissionState.BLOCKED
        PermissionDisclosureDialog(
            disclosure = disclosure,
            blocked = blocked,
            onContinue = {
                showing = false
                if (blocked) {
                    runCatching { context.startActivity(appSettingsIntent(context)) }
                } else {
                    PermissionAsks.mark(context, disclosure.permission)
                    launcher.launch(disclosure.permission)
                }
            },
            onDismiss = { showing = false },
        )
    }
    return { showing = true }
}

/** The Activity behind a Compose context, for the calls that only Activity has. */
internal fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Base class for the invisible trampolines the IME starts when a tool needs a
 * permission: an input method service cannot show a permission dialog itself,
 * so it starts one of these instead.
 *
 * The trampoline shows the disclosure, then the system dialog, then finishes.
 * Subclasses supply the disclosure and nothing else; a `null` disclosure (a
 * permission that does not apply on this Android version) finishes immediately.
 * The tools re-check the permission when the keyboard regains focus, which is
 * what actually drives the panel — these activities never report back.
 */
abstract class PermissionRequestActivity : ComponentActivity() {

    /** The permission to ask for, or null when this build/API needs none. */
    protected abstract val disclosure: PermissionDisclosure?

    private val request =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val disclosure = disclosure
        val state = disclosure?.let { permissionState(this, it.permission) }
        if (disclosure == null || state == PermissionState.GRANTED) {
            finish()
            return
        }
        val blocked = state == PermissionState.BLOCKED
        setContent {
            DisclosureTheme {
                PermissionDisclosureDialog(
                    disclosure = disclosure,
                    blocked = blocked,
                    onContinue = {
                        if (blocked) {
                            // The system dialog would never appear; the app's
                            // settings page is the only place left to grant it.
                            runCatching { startActivity(appSettingsIntent(this)) }
                            finish()
                        } else {
                            // Marked before the launch, not in the result callback:
                            // these trampolines are android:noHistory, so the
                            // callback may never arrive once the system dialog
                            // covers them.
                            PermissionAsks.mark(this, disclosure.permission)
                            request.launch(disclosure.permission)
                        }
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }
}

/**
 * Material You colours for a dialog that is hosted by a bare translucent
 * activity theme. The app's own theme lives in :core:theme, which depends on
 * this module — so this is a deliberately tiny stand-in rather than that.
 */
@Composable
internal fun DisclosureTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
