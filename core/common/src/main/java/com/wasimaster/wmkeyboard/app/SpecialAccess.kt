package com.wasimaster.wmkeyboard.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * The three grants Android does not hand out through a permission dialog:
 * notification access, usage access and the accessibility service. Each is a
 * toggle on a system Settings screen the app can only *navigate to*.
 *
 * Play treats these as more sensitive than ordinary permissions, not less —
 * notification access and the Accessibility API each have their own Console
 * declaration, and both are routinely refused when the reviewer cannot see why
 * the app needs them. So the same prominent-disclosure rule from
 * [PermissionDisclosure] applies here: say what is read and why *before*
 * sending anyone to the switch, and never bounce them there straight from a tap.
 *
 * The wording matters more than usual for these three, because what the system
 * screen says is alarming and generic ("full control of your device", "read all
 * notifications") while what this app actually does with each is narrow. The
 * copy below states the narrow truth without arguing with the system warning —
 * a reviewer reads both, and a disclosure that contradicts the platform's own
 * text reads as a dodge.
 */
enum class SpecialAccess {
    /** Notification listener, for the media-control tool's transport buttons. */
    NOTIFICATIONS,

    /** Usage access, for "show source app" on clipboard entries. */
    USAGE,

    /** The touch-passthrough accessibility service, for keyboard gestures under TalkBack. */
    ACCESSIBILITY,
    ;

    val title: String
        get() = when (this) {
            NOTIFICATIONS -> "Notification access for media controls"
            USAGE -> "Usage access for clipboard source apps"
            ACCESSIBILITY -> "Accessibility service for keyboard gestures"
        }

    val body: String
        get() = when (this) {
            NOTIFICATIONS ->
                "Android hands the play/pause/skip controls of whatever is playing only to " +
                    "apps that hold notification access, which is why the media tool needs " +
                    "it. The keyboard's listener is empty: it reads no notification, no " +
                    "title, no sender and no content, and exists only so the platform will " +
                    "let the tool see the current media session. Nothing is stored and " +
                    "nothing leaves the device. The system screen you are about to open " +
                    "describes the full access the permission can give — this app uses the " +
                    "media-session part of it and nothing else. The tool is optional."
            USAGE ->
                "With usage access, the keyboard can note which app was in front when you " +
                    "copied something, and show it when you press and hold that clip. It " +
                    "reads the foreground app at the moment of a copy — not your history, " +
                    "not how long you spend anywhere — and the answer is kept with the clip " +
                    "on this device only. Nothing about your app usage leaves the phone. " +
                    "The setting is optional and stays off until you turn it on."
            ACCESSIBILITY ->
                "This service exists so the keyboard's own gestures — the spacebar cursor " +
                    "slide, the backspace word swipe, glide typing, handwriting — keep " +
                    "working while a screen reader is running, which only an accessibility " +
                    "service is allowed to arrange. It subscribes to no accessibility " +
                    "events, cannot read window content, and marks nothing but the " +
                    "keyboard's own key grid. It collects no data at all. Android's " +
                    "accessibility screen warns about the full power the category can have; " +
                    "this service declares none of it."
        }

    /**
     * Where to send the user. First entry preferred, later ones are fallbacks for
     * devices where the deep link is missing — the per-app notification-listener
     * page only exists from API 30, and some OEMs ship neither.
     */
    internal fun intents(component: ComponentName?): List<Intent> = when (this) {
        // The version check is spelled out rather than folded into a takeIf so
        // that Lint can see it: both constants are Strings, so they inline into
        // the APK and would be used unguarded on API 24 otherwise.
        NOTIFICATIONS -> listOfNotNull(
            if (component != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        component.flattenToString(),
                    )
            } else {
                null
            },
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )
        USAGE -> listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        ACCESSIBILITY -> listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }.map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}

/** Opens the first Settings screen that this device actually has. */
internal fun SpecialAccess.openSettings(context: Context, component: ComponentName? = null) {
    intents(component).firstOrNull { intent ->
        runCatching { context.startActivity(intent) }.isSuccess
    }
}

/**
 * Settings-app entry point: returns a lambda that shows the disclosure and, if
 * the user continues, opens the system screen holding the switch.
 */
@Composable
fun rememberDisclosedSpecialAccess(access: SpecialAccess): () -> Unit {
    val context = LocalContext.current
    var showing by remember { mutableStateOf(false) }
    if (showing) {
        DisclosureDialog(
            title = access.title,
            body = access.body,
            continueLabel = "Open settings",
            onContinue = {
                showing = false
                access.openSettings(context)
            },
            onDismiss = { showing = false },
        )
    }
    return { showing = true }
}

/**
 * The IME's route to the same thing. An input method cannot host a dialog
 * outside its own window and must not hand out a "grant" button that jumps
 * straight to a system screen with no explanation, so it starts this invisible
 * activity, which shows the disclosure and then opens Settings.
 *
 * Build the intent with [intent]; the component extra is only read for
 * [SpecialAccess.NOTIFICATIONS], where it selects the app's own row on the
 * per-listener settings page.
 */
class SpecialAccessActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val access = intent?.getStringExtra(EXTRA_ACCESS)
            ?.let { name -> SpecialAccess.entries.firstOrNull { it.name == name } }
        if (access == null) {
            finish()
            return
        }
        val component = intent?.getStringExtra(EXTRA_COMPONENT)
            ?.let { ComponentName.unflattenFromString(it) }
        setContent {
            DisclosureTheme {
                DisclosureDialog(
                    title = access.title,
                    body = access.body,
                    continueLabel = "Open settings",
                    onContinue = {
                        access.openSettings(this, component)
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_ACCESS = "access"
        private const val EXTRA_COMPONENT = "component"

        /** Intent that shows [access]'s disclosure and then its Settings screen. */
        fun intent(
            context: Context,
            access: SpecialAccess,
            component: ComponentName? = null,
        ): Intent = Intent(context, SpecialAccessActivity::class.java)
            .putExtra(EXTRA_ACCESS, access.name)
            .putExtra(EXTRA_COMPONENT, component?.flattenToString())
    }
}
