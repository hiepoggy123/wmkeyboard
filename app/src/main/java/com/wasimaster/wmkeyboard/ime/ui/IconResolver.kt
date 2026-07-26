package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.wasimaster.wmkeyboard.core.icons.IconOverrides
import com.wasimaster.wmkeyboard.core.icons.IconPackStore
import com.wasimaster.wmkeyboard.core.settings.IconSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A resolved icon: the vector to draw, and whether the caller's tint applies.
 *
 * A monochrome icon declared no colours of its own and is recoloured to match
 * the theme (and the per-tool accent colour). One that did is drawn as its
 * author painted it, which is why the tint has to be dropped for it rather
 * than flattening it to a single colour.
 */
@Immutable
data class ResolvedIcon(val vector: ImageVector, val monochrome: Boolean)

/**
 * Every icon the user has replaced, already parsed and ready to draw.
 *
 * Built off the settings and the installed packs on a background thread and
 * then held immutable, because the alternative — resolving on demand — would
 * put an SVG parse and a file read on the keyboard's render path. A slot that
 * is not in here draws its built-in glyph, so an empty set is exactly the
 * stock keyboard.
 */
@Immutable
class IconSet(private val icons: Map<String, ResolvedIcon>) {

    fun resolve(slot: String): ResolvedIcon? = icons[slot]

    val size: Int get() = icons.size

    companion object {
        /** Nothing replaced: every slot falls through to [IconDefaults]. */
        val Builtin = IconSet(emptyMap())
    }
}

/**
 * The icon set in force for this subtree.
 *
 * Defaults to [IconSet.Builtin] so a composable that is rendered outside a
 * provider — a preview, a dialog hosted elsewhere — still draws real icons
 * instead of blanks.
 */
val LocalIconSet = staticCompositionLocalOf { IconSet.Builtin }

/**
 * Draws the icon for [slot]: the user's replacement if they set one, otherwise
 * the built-in glyph.
 *
 * A drop-in replacement for `Icon(…)` at every customisable call site. Full
 * colour is handled by passing `Color.Unspecified` as the tint, which is
 * Material's own "no colour filter" signal — so one code path covers both
 * kinds of icon and the caller's sizing modifier keeps working unchanged.
 */
@Composable
fun SlotIcon(
    slot: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val resolved = LocalIconSet.current.resolve(slot)
    val vector = resolved?.vector ?: IconDefaults.forSlot(slot) ?: return
    Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = if (resolved == null || resolved.monochrome) tint else Color.Unspecified,
    )
}

/**
 * Resolves [settings] against the installed packs, off the main thread.
 *
 * Reading and parsing a full pack is tens of milliseconds — small, but not
 * something to do during composition — so the set starts as [IconSet.Builtin]
 * and swaps in when it is ready. The keyboard therefore draws stock icons for
 * the first frame or two after a cold start, which is the same trade the
 * dictionaries and the emoji catalog already make.
 */
@Composable
fun rememberIconSet(settings: IconSettings): State<IconSet> {
    val context = LocalContext.current
    val store = remember(context) { IconPackStore.get(context) }
    // The store's revision changes whenever a pack is added, edited or removed,
    // so an import shows up without anyone having to signal the keyboard. It is
    // an in-memory counter rather than the manifest's mtime on purpose: this
    // runs on every recomposition, and the keyboard recomposes on every
    // keystroke, so a file stat here would be a syscall per key.
    val revision by store.revision.collectAsState()
    return produceState(IconSet.Builtin, settings, revision) {
        value = withContext(Dispatchers.Default) { buildIconSet(settings, store) }
    }
}

/**
 * The blocking half of [rememberIconSet]: reads and parses whatever the
 * settings point at. Safe to call from any thread but the main one.
 */
fun buildIconSet(settings: IconSettings, store: IconPackStore): IconSet {
    // Before the early return, not after: a user with no icons customised is
    // exactly the one whose first frame would otherwise pay for building them.
    IconDefaults.warm()

    val activePackId = settings.activePackId
    if (activePackId.isEmpty() && settings.overrides.isEmpty()) return IconSet.Builtin

    val resolved = HashMap<String, ResolvedIcon>()
    // The active pack first, so a per-slot override written afterwards wins.
    if (activePackId.isNotEmpty()) {
        for ((slot, doc) in store.docs(activePackId)) {
            resolved[slot] = ResolvedIcon(doc.toImageVector(slot), doc.monochrome)
        }
    }
    for ((slot, source) in settings.overrides) {
        val icon = when {
            source.startsWith(IconOverrides.BUILTIN_PREFIX) ->
                BuiltinIcons.byName(source.removePrefix(IconOverrides.BUILTIN_PREFIX))
                ?.let { ResolvedIcon(it, monochrome = true) }
            source.startsWith(IconOverrides.PACK_PREFIX) ->
                store.docs(source.removePrefix(IconOverrides.PACK_PREFIX))[slot]
                ?.let { ResolvedIcon(it.toImageVector(slot), it.monochrome) }
            else -> null
        }
        // A source naming a pack that was uninstalled, or an icon renamed out
        // of the catalog, leaves the slot on whatever the pack or the built-in
        // gives it rather than drawing nothing.
        if (icon != null) resolved[slot] = icon else resolved.remove(slot)
    }
    return if (resolved.isEmpty()) IconSet.Builtin else IconSet(resolved)
}
