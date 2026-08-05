package com.wasimaster.wmkeyboard.ime

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app-launcher tool's view of the package manager: the launchable apps,
 * and for one app at a time, the activities inside it.
 *
 * Visibility comes entirely from the manifest's MAIN/LAUNCHER `<queries>`
 * entry — the same one the suggestion engine's app-name index uses. That
 * entry makes every launcher-listed package fully visible, which is what lets
 * [loadActivities] enumerate a package's whole activity table without
 * QUERY_ALL_PACKAGES (which Play restricts and this app must never request).
 * Work-profile apps live behind LauncherApps and are deliberately out of
 * scope here.
 */
data class LauncherApp(
    val packageName: String,
    val label: String,
    /** Class name of the MAIN/LAUNCHER entry point. */
    val activityName: String,
) {
    /** The entry point, for the icon and the plain launch. Plain strings are
     * stored instead so the JVM unit tests can build catalog entries. */
    val component: ComponentName get() = ComponentName(packageName, activityName)
}

data class LauncherActivity(
    val packageName: String,
    val className: String,
    /** The activity's own label when it has one, else its short class name. */
    val label: String,
    /**
     * Whether another app may start it. A non-exported activity throws
     * SecurityException on launch, so the panel dims these and only shows
     * them behind a setting.
     */
    val exported: Boolean,
    val enabled: Boolean,
) {
    val component: ComponentName get() = ComponentName(packageName, className)
}

object AppCatalog {

    /** All launchable apps, one entry per package, sorted by label. */
    suspend fun loadApps(pm: PackageManager): List<LauncherApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                LauncherApp(
                    packageName = activity.packageName,
                    label = info.loadLabel(pm)?.toString() ?: activity.packageName,
                    activityName = activity.name,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Every activity declared by [packageName], exported-and-enabled first.
     * Empty when the package is not visible or declares none.
     */
    suspend fun loadActivities(
        pm: PackageManager,
        packageName: String,
    ): List<LauncherActivity> = withContext(Dispatchers.IO) {
        val activities = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES).activities
        }.getOrNull() ?: return@withContext emptyList()
        activities
            .map { info ->
                LauncherActivity(
                    packageName = info.packageName,
                    className = info.name,
                    label = info.loadLabel(pm).toString()
                        .ifBlank { info.name.substringAfterLast('.') },
                    exported = info.exported,
                    enabled = info.enabled,
                )
            }
            .sortedWith(
                compareByDescending<LauncherActivity> { it.exported && it.enabled }
                    .thenBy { it.label.lowercase() },
            )
    }

    /** Case-insensitive filter over label and package name. */
    fun filterApps(apps: List<LauncherApp>, query: String): List<LauncherApp> {
        val q = query.trim()
        if (q.isEmpty()) return apps
        return apps.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
    }

    /**
     * The grid order: pinned apps lead in the user's pin order, then the rest
     * alphabetically or by recency, per the sort setting. [recents] is
     * most-recent-first package names.
     */
    fun sortApps(
        apps: List<LauncherApp>,
        recentFirst: Boolean,
        pinned: List<String>,
        recents: List<String>,
    ): List<LauncherApp> {
        val byPackage = apps.associateBy { it.packageName }
        val lead = pinned.mapNotNull { byPackage[it] }
        val rest = apps.filter { it.packageName !in pinned.toSet() }
        val ordered = if (recentFirst) {
            val rank = recents.withIndex().associate { (i, pkg) -> pkg to i }
            rest.sortedWith(
                compareBy<LauncherApp> { rank[it.packageName] ?: Int.MAX_VALUE }
                    .thenBy { it.label.lowercase() },
            )
        } else {
            rest
        }
        return lead + ordered
    }
}
