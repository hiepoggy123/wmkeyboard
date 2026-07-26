package com.wasimaster.wmkeyboard.core.input.composer

/**
 * Runtime CJK input options the composers read at call time. Mirrors
 * [CjkDictionaries]: the composers stay parameter-less singletons (so
 * `composerFor` needs no new arguments and the `_uiState.copy` sites are
 * untouched), and the service pushes the user's settings in here from the same
 * `settings.collect` block that already feeds the suggestion engine.
 *
 * Every option here changes what the composers rank, so each one invalidates the
 * cached rankings on assignment — see [CjkDictionaries.generation].
 */
object CjkConfig {

    /**
     * Fuzzy Pinyin: treat commonly-confused initials/finals as equivalent
     * (zh↔z, an↔ang, …) so an imprecise spelling still finds its characters.
     */
    @Volatile
    var fuzzyPinyin: Boolean = false
        set(value) { field = value; CjkDictionaries.invalidate() }

    /**
     * The Double Pinyin scheme in use, or [DoublePinyinScheme.OFF] for full
     * Pinyin. When set, each syllable is two keys that the composer expands to
     * full Pinyin before the normal segmentation runs.
     */
    @Volatile
    var doublePinyin: DoublePinyinScheme = DoublePinyinScheme.OFF
        set(value) { field = value; CjkDictionaries.invalidate() }

    /**
     * Convert candidate output to Traditional characters. Read by [HanVariant]
     * inside each composer's ranking, never applied to a finished candidate list —
     * see [HanVariant] for why that distinction is load-bearing.
     */
    @Volatile
    var traditionalOutput: Boolean = false
        set(value) { field = value; CjkDictionaries.invalidate() }
}

/**
 * Double Pinyin layouts: every syllable is exactly two keystrokes — an initial
 * key and a final key. [OFF] is ordinary full Pinyin. Ordinals are append-only
 * (the setting persists by [name], but keep them stable regardless).
 */
enum class DoublePinyinScheme(val displayName: String) {
    OFF("Off (full Pinyin)"),
    MICROSOFT("Microsoft"),
    SOGOU("Sogou"),
    XIAOHE("Xiaohe (小鹤)"),
    ZIRANMA("Ziranma (自然码)"),
    PINYINPP("Pinyin++ (拼音加加)"),
}
