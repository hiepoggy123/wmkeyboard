package com.wasimaster.wmkeyboard.core.plugins

/**
 * What a plugin may do beyond drawing inside its own panel.
 *
 * The list is deliberately, almost comically short, and the things that are
 * *not* on it matter more than the thing that is. A plugin can never read what
 * the user types, read the text field, read the clipboard, or reach the
 * network — not behind a prompt, not behind a grant, not at all. There is no
 * API for any of them, so there is no permission for any of them either.
 *
 * That is a design constraint rather than a starting point. A keyboard sees
 * everything its user types, and third-party code that could read that text and
 * also send it somewhere is the definitional keyboard-malware shape; Google
 * Play's Device and Network Abuse policy names Lua specifically and asks that
 * runtime-loaded interpreted code "must not allow potential violations" — a
 * question about capability, which a consent prompt does not answer. So text
 * reaches a plugin the way it reaches any app: the user types or pastes it into
 * a visible input inside the plugin's own panel, which needs no permission
 * because the user did it on purpose and can see exactly what they shared.
 *
 * What remains is local scratch space, which cannot leak anywhere because
 * nothing in the sandbox has anywhere to leak to.
 *
 * A permission string this build does not recognise is a **refused install**,
 * not an ignored line — see [PluginManifestCodec]. A capability that cannot be
 * explained to the user before they install is one that cannot be granted, and
 * refusing is also the forward-compatibility guard: if a later version adds a
 * capability, older builds decline those plugins outright rather than running
 * them with a declaration they silently dropped.
 */
sealed interface PluginPermission {

    /** The exact string a manifest declares. */
    val wire: String

    /** One line for the "This plugin can" list, shown before install and in settings. */
    val label: String

    /**
     * Per-plugin key/value storage under the plugin's own directory, exposed to
     * the script as `wm.storage`. Local only, quota'd, and deleted when the
     * plugin is uninstalled.
     *
     * Not gated at runtime. With no egress of any kind this is app-local
     * scratch space, and a prompt for it would be noise that teaches users to
     * dismiss prompts without reading them.
     */
    data object Storage : PluginPermission {
        override val wire: String = "storage"
        override val label: String = "Save data on this device"
    }

    companion object {
        /** Every permission this build understands. */
        val all: List<PluginPermission> = listOf(Storage)

        /** The permission [wire] names, or null if this build does not know it. */
        fun parse(wire: String): PluginPermission? {
            val trimmed = wire.trim()
            return all.firstOrNull { it.wire == trimmed }
        }
    }
}
