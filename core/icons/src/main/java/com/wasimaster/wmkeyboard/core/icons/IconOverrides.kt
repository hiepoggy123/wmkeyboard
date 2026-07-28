package com.wasimaster.wmkeyboard.core.icons

/**
 * How the per-slot icon overrides are stored: a CSV of `slot=source` pairs,
 * the same shape the per-tool colour overrides use.
 *
 * Safe as a flat CSV because neither half can contain a separator. Slot ids are
 * lowercase `a-z0-9._` (see [IconSlots.isWellFormed]) and a source is `b:<name>`
 * for a bundled icon or `p:<packId>` for one out of an installed pack — no `,`
 * and no `=` in either.
 *
 * Lives here rather than inside `SettingsRepository` so it is a plain pair of
 * functions a unit test can call, with no DataStore or Android context in the
 * way.
 */
object IconOverrides {

    /** `b:<BuiltinIcons name>` — one of the bundled Material glyphs. */
    const val BUILTIN_PREFIX = "b:"

    /** `p:<packId>` — this slot's icon out of a specific installed pack. */
    const val PACK_PREFIX = "p:"

    fun builtinSource(name: String): String = BUILTIN_PREFIX + name

    fun packSource(packId: String): String = PACK_PREFIX + packId

    /**
     * Parses [csv], dropping anything unusable.
     *
     * A slot this version doesn't have is dropped, so an override written by a
     * newer build simply doesn't apply here instead of corrupting the map. A
     * source naming a missing pack or icon is *kept*: the resolver falls back
     * for it, and dropping it would quietly lose the user's choice while a pack
     * was only temporarily unavailable.
     */
    fun decode(csv: String?): Map<String, String> =
        csv?.split(',')?.mapNotNull { entry ->
            val separator = entry.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val slot = entry.substring(0, separator)
            val source = entry.substring(separator + 1)
            if (source.isEmpty() || IconSlots.byId(slot) == null) return@mapNotNull null
            slot to source
        }?.toMap().orEmpty()

    fun encode(map: Map<String, String>): String =
        map.entries.joinToString(",") { (slot, source) -> "$slot=$source" }
}
