package com.wasimaster.wmkeyboard.core.keyman

/**
 * A parsed, validated `.kmx` keyboard: immutable, shareable across threads, and
 * safe for the interpreter's hot loop to index without bounds checks because
 * [KmxParser] has already proved every offset and index resolves.
 *
 * Strings keep Keyman's own representation — UTF-16 units with `0xFFFF`
 * sentinels and their operand runs left in place. Decoding them into some
 * friendlier tree would mean re-encoding to compare against a live context
 * buffer that is itself in that representation, so the format is the model.
 */
class KeymanKeyboard internal constructor(
    /** `dwFileVersion`, e.g. 0x1100 for Keyman 17. */
    val version: Int,
    val stores: List<KmxStore>,
    val groups: List<KmxGroup>,
    /** Index into [groups] where processing starts, or -1 for a keyboard we cannot run. */
    val startGroup: Int,
    /** `begin NewContext` target, or -1. */
    val newContextGroup: Int,
    /** `begin PostKeystroke` target, or -1. */
    val postKeystrokeGroup: Int,
    /**
     * `&MNEMONICLAYOUT`. A mnemonic keyboard matches on the character the US
     * layout would have produced rather than on the physical key, which is why
     * the touch layout's key caps and the rules can disagree about what a key
     * "is".
     */
    val mnemonic: Boolean,
) {
    /** The value of a system store, or null. */
    fun systemStore(id: Int): String? = stores.firstOrNull { it.systemId == id }?.value

    /** `&NAME`, when the keyboard declares one. */
    val name: String? get() = systemStore(KmxFormat.TSS_NAME)

    /** `&KEYBOARDVERSION`, when the keyboard declares one. */
    val keyboardVersion: String? get() = systemStore(KmxFormat.TSS_KEYBOARDVERSION)

    /** Total rules across every group — the figure the load-time cap applies to. */
    val ruleCount: Int get() = groups.sumOf { it.rules.size }
}

/**
 * One `store()`. [systemId] is [KmxFormat.TSS_NONE] for an ordinary named store
 * and one of the `TSS_*` ids for a system store.
 */
class KmxStore internal constructor(
    val systemId: Int,
    val name: String,
    val value: String,
)

/**
 * One `group()`.
 *
 * [usingKeys] distinguishes `group(x) using keys`, which matches a keystroke,
 * from a context-only group reached by `use()`. The difference is not cosmetic:
 * a context-only group matches the first rule whose context matches and stops,
 * where a `using keys` group also has to agree about the key.
 */
class KmxGroup internal constructor(
    val name: String,
    val usingKeys: Boolean,
    /**
     * In compiled order, which is longest-context-first within a key. The
     * compiler sorted these and the runtime scans linearly and takes the first
     * hit, so **this list must never be re-sorted**.
     */
    val rules: List<KmxRule>,
    /** `match` rule output, empty when the group declares none. */
    val match: String,
    /** `nomatch` rule output, empty when the group declares none. */
    val noMatch: String,
)

/** One rule: `context + key > output`. */
class KmxRule internal constructor(
    /**
     * A US virtual key, a key-cap character, or a keyboard-allocated code above
     * [KmxFormat.VK_MAX] — which of the three is decided by
     * [shiftFlags] and [KmxFormat.KEY_KIND_MASK].
     */
    val key: Int,
    /** Source line, for diagnostics only. */
    val line: Int,
    val shiftFlags: Int,
    /** May be empty; never null, even for a rule that outputs nothing. */
    val output: String,
    /** May be empty, meaning the rule matches regardless of what precedes it. */
    val context: String,
) {
    /** How [key] should be compared against a keystroke. */
    val kind: KmxKeyKind
        get() = when (shiftFlags and KmxFormat.KEY_KIND_MASK) {
            KmxFormat.ISVIRTUALKEY -> KmxKeyKind.VIRTUAL_KEY
            KmxFormat.VIRTUALCHARKEY -> KmxKeyKind.VIRTUAL_CHAR_KEY
            else -> KmxKeyKind.CHARACTER
        }
}

enum class KmxKeyKind {
    /** A literal key-cap character. Every other shift flag is ignored. */
    CHARACTER,

    /** A US virtual key code, compared together with the modifiers. */
    VIRTUAL_KEY,

    /** A key cap combined with the modifiers. */
    VIRTUAL_CHAR_KEY,
}
