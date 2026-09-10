package com.wasimaster.wmkeyboard.core.plugins

/**
 * The `wm` members that must never exist: every way a plugin could read text,
 * the clipboard, files or the network. [PluginHostApiTest] proves the sandbox has
 * none of them, and `LuaApiDriftTest` proves the plugin editor names the same set.
 */
object PluginForbiddenNames {
    val WM: List<String> = listOf(
        "wm.text", "wm.clipboard", "wm.http", "wm.net", "wm.field", "wm.insert",
        "wm.keys", "wm.input", "wm.contacts", "wm.files", "wm.fs", "wm.exec",
    )
}
