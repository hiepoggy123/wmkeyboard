package com.wasimaster.wmkeyboard.app

import android.net.Uri
import java.net.URI

/**
 * Launcher shortcuts: long-pressing the app icon drops straight into a settings
 * screen instead of the home page.
 *
 * The entries themselves are static, declared in `res/xml/shortcuts.xml` and
 * hung off the launcher activity with a `android.app.shortcuts` meta-data tag,
 * so the platform publishes them at install time with no runtime code. What
 * *is* code is the other half: each shortcut carries a
 * `wmkeyboard://settings/<route>` URI, and this maps it back to the in-app
 * route [SettingsNavHost] knows.
 *
 * A URI rather than an intent extra because the shortcut XML's `android:data`
 * is unambiguously supported, and because it costs nothing: a shortcut intent
 * is explicit (it names the package and the class), so unlike the addon links
 * in [AddonDeepLink] this scheme is *not* reachable from a web page — the
 * manifest declares no browsable filter for it.
 *
 * [Routes] is the allowlist, and is the reason this parses rather than trusting
 * the string: a route that is not one of ours navigates nowhere.
 */
object SettingsShortcuts {

    const val SCHEME = "wmkeyboard"

    /** The host that marks a settings link, as written in `shortcuts.xml`. */
    const val HOST = "settings"

    /**
     * Routes a shortcut may open. Kept in step with the `composable(...)`
     * destinations in `SettingsNavHost` by [SettingsShortcutsTest], which reads
     * both and fails when a shortcut names a screen that does not exist.
     */
    val Routes: Set<String> = setOf("typing", "appearance", "themes", "languages", "tools", "search")

    /** Convenience for the intent's data. */
    fun routeFor(uri: Uri?): String? = routeFor(uri?.toString())

    /**
     * The settings route [link] names, or null when it isn't a shortcut link or
     * names a screen that is not in [Routes].
     */
    fun routeFor(link: String?): String? {
        val text = link?.trim().orEmpty()
        if (text.isEmpty()) return null
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        // "wmkeyboard://settings/themes" puts the host and path apart; the
        // opaque "wmkeyboard:settings/themes" form carries both in one string.
        // Accept either rather than making the XML care which it wrote.
        val body = if (uri.isOpaque) {
            uri.schemeSpecificPart.orEmpty()
        } else {
            if (!uri.host.equals(HOST, ignoreCase = true)) return null
            uri.path.orEmpty()
        }
        val route = body.removePrefix(HOST).trim('/').substringBefore('?').lowercase()
        return route.takeIf { it in Routes }
    }
}
