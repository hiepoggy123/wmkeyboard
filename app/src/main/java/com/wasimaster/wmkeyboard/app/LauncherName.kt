package com.wasimaster.wmkeyboard.app

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.PackageManager.DONT_KILL_APP

/**
 * Which name the launcher shows under the app icon: "WM Keyboard", or "WMK"
 * (#140).
 *
 * A launcher shows the label of the activity it starts, and a manifest label
 * is fixed at build time. So the launcher entry is not MainActivity but one of
 * two `<activity-alias>`es of it, one per name, and exactly one of them is
 * enabled. The application label, which Android's app settings, the keyboard
 * list and the stores read, is never touched.
 *
 * The component state *is* the setting. PackageManager keeps it across updates
 * and reboots, so there is no preference behind it that could disagree with
 * it, and for the same reason it does not travel in a settings backup.
 *
 * Turning the short name off returns both aliases to
 * `COMPONENT_ENABLED_STATE_DEFAULT` rather than to explicit values, so the full
 * name is exactly a fresh install's state, not a copy of it.
 */
internal object LauncherName {
    /** Enabled by the manifest. Stored state: read the manifest comment before renaming. */
    const val FULL_ALIAS = "com.wasimaster.wmkeyboard.app.LauncherFullName"

    /** Disabled by the manifest. Stored state: read the manifest comment before renaming. */
    const val SHORT_ALIAS = "com.wasimaster.wmkeyboard.app.LauncherShortName"

    /** What a fresh install shows, and what the About row's reset returns to. */
    const val DEFAULT_SHORT = false

    /** The About row's title resource name, so a restart lands on the row. */
    private const val ROW = "about_launcher_name_title"

    fun isShort(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(ComponentName(context, SHORT_ALIAS)) ==
            COMPONENT_ENABLED_STATE_ENABLED

    fun setShort(context: Context, short: Boolean) {
        if (short == isShort(context)) return
        val packageManager = context.packageManager
        val shown = ComponentName(context, if (short) SHORT_ALIAS else FULL_ALIAS)
        val hidden = ComponentName(context, if (short) FULL_ALIAS else SHORT_ALIAS)

        // The new entry first. With neither enabled, even for the length of
        // one broadcast, a launcher sees an app with nothing to launch and can
        // delete its home-screen icon instead of moving it to the other name.
        packageManager.setComponentEnabledSetting(
            shown,
            if (short) COMPONENT_ENABLED_STATE_ENABLED else COMPONENT_ENABLED_STATE_DEFAULT,
            DONT_KILL_APP,
        )

        // DONT_KILL_APP spares the process, not the task. When a component is
        // disabled, Android removes every task whose root intent names it
        // (RecentTasks.cleanupDisabledPackageTasksLocked, "disabled-package"),
        // and a settings app opened from the launcher is rooted at exactly the
        // alias about to go: it would vanish under the user's finger. A
        // CLEAR_TASK start re-roots the task at MainActivity itself, which no
        // switch ever disables, and lands back on this row. It has to happen
        // before the call below, whose broadcast is what triggers the removal.
        if (rootedAt(context, hidden.className)) {
            context.startActivity(
                MainActivityContract.intent(context, route = "about", setting = ROW)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }

        packageManager.setComponentEnabledSetting(
            hidden,
            if (short) COMPONENT_ENABLED_STATE_DISABLED else COMPONENT_ENABLED_STATE_DEFAULT,
            DONT_KILL_APP,
        )
    }

    /** Whether any of this app's tasks was started through [alias]. */
    private fun rootedAt(context: Context, alias: String): Boolean =
        context.getSystemService(ActivityManager::class.java)?.appTasks.orEmpty().any { task ->
            // A task that finishes between the listing and the read throws.
            runCatching { task.taskInfo.baseIntent.component?.className == alias }.getOrDefault(false)
        }
}
