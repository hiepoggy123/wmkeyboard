package com.wasimaster.wmkeyboard.core.layout

import android.content.res.AssetManager

/**
 * The long tail of language layouts, shipped as JSON assets rather than Kotlin
 * [BuiltInLayouts].
 *
 * A boot-critical grid — the default-enabled layouts drawn on the first frame —
 * has to be a compiled `val`: it cannot wait for a parse, and a typo in it must
 * fail the build, not ship a blank keyboard. Every other layout can be data.
 * Shipping it as the same [LayoutFile] envelope the export path already writes
 * gets three things at once: it parses off the main thread, a mistake costs one
 * language rather than the build, and each file is user-importable and editable
 * for free.
 *
 * Loaded once by [load]; [all] returns the cache (empty until then) so
 * [resolveLayouts] can splice these in beside the built-ins with no `Context`.
 * Asset layouts are deliberately never in `defaultEnabledIds`, so an empty cache
 * on the first frame only means a not-yet-selected language is briefly absent —
 * never a keyboard with nothing to draw.
 */
object AssetLayouts {

    /** Folder under `assets/` holding the layout files. */
    private const val DIR = "layouts"

    /** Stable ids of the shipped asset layouts, for the [LanguageDef]s that group them. */
    const val PT_QWERTY_ID = "asset_pt_qwerty"
    const val UK_JCUKEN_ID = "asset_uk_jcuken"

    @Volatile private var cached: List<LayoutSpec> = emptyList()
    @Volatile private var loaded = false

    /** The parsed asset layouts, or empty before [load] has run. */
    val all: List<LayoutSpec> get() = cached

    /**
     * Reads and parses every `.wmlayout.json` under `assets/layouts`, caching the
     * result. Idempotent; the I/O runs on the calling thread, so call it off the
     * main thread the way the service loads its dictionaries. A file that fails
     * to parse is skipped, never fatal — one malformed asset cannot cost the
     * others.
     */
    fun load(assets: AssetManager) {
        if (loaded) return
        val names = runCatching { assets.list(DIR)?.asList() }.getOrNull().orEmpty()
        cached = names
            .filter { it.endsWith(SUFFIX) }
            .mapNotNull { name ->
                runCatching {
                    val text = assets.open("$DIR/$name").use { it.readBytes().decodeToString() }
                    LayoutFile.decode(text)?.layout
                }.getOrNull()
            }
        loaded = true
    }

    private val SUFFIX = ".${LayoutFile.FILE_EXTENSION}"
}
