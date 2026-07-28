package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.script.LanguageDef
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry

/**
 * Which layout is active and which are enabled, resolved from what is actually
 * on disk. [enabledLanguages] is the enabled set deduped by language; both it
 * and [active] derive from [enabledLayoutIds].
 */
data class LayoutSelection(
    val active: LayoutSpec,
    val enabledLayoutIds: List<String>,
    val enabledLanguages: List<LanguageDef>,
)

/**
 * Translates the stored preferences into a layout selection.
 *
 * Installs from before the layout registry have no layout id at all — their
 * stored [InputMode] names the built-in they were already rendering, so it is
 * translated here rather than migrated in place. Nothing is rewritten, which
 * means a downgrade to an older build still finds `input_mode` exactly where it
 * left it.
 *
 * Pure, and separate from the settings flow, so the migration can be tested
 * without standing up a DataStore.
 */
fun resolveLayoutSelection(
    storedLayoutId: String?,
    storedInputMode: String?,
    storedEnabledLayoutIds: String?,
    storedEnabledModes: String?,
    customLayouts: List<LayoutSpec>,
    defaultActiveId: String = BuiltInLayouts.DEFAULT_ID,
    defaultEnabledIds: List<String> = BuiltInLayouts.defaultEnabledIds,
): LayoutSelection {
    val activeId = storedLayoutId
        ?: storedInputMode?.let { LEGACY_MODE_LAYOUT[it] }
        ?: defaultActiveId

    val enabledIds = storedEnabledLayoutIds
        ?.split(',')?.filter { it.isNotEmpty() }?.ifEmpty { null }
        ?: storedEnabledModes?.split(',')
            ?.mapNotNull { LEGACY_MODE_LAYOUT[it] }
            ?.ifEmpty { null }
        ?: defaultEnabledIds

    return LayoutSelection(
        // Resolved rather than raw, so an id whose layout was deleted out from
        // under it heals to the default here instead of at every reader.
        active = resolveLayout(customLayouts, activeId),
        enabledLayoutIds = enabledIds,
        // switchLanguage already guards with ifEmpty, but hintedLanguage and the
        // FORCE_ASCII fallback do not — an empty list there would leave a field
        // with no language to fall back to.
        enabledLanguages = enabledIds
            .map { resolveLayout(customLayouts, it).language() }
            .distinctBy { it.id }
            .ifEmpty { listOf(LanguageRegistry.byId("en")) },
    )
}
