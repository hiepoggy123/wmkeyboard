package com.wasimaster.wmkeyboard.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.core.icons.IconImportResult
import com.wasimaster.wmkeyboard.core.icons.IconOverrides
import com.wasimaster.wmkeyboard.core.icons.IconPack
import com.wasimaster.wmkeyboard.core.icons.IconPackFile
import com.wasimaster.wmkeyboard.core.icons.IconPackStore
import com.wasimaster.wmkeyboard.core.icons.IconSlot
import com.wasimaster.wmkeyboard.core.icons.IconSlotGroup
import com.wasimaster.wmkeyboard.core.icons.IconSlots
import com.wasimaster.wmkeyboard.core.icons.SvgParser
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.ime.ui.BuiltinIcons
import com.wasimaster.wmkeyboard.ime.ui.SlotIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Icon customisation: which pack is active, and per-slot replacements.
 *
 * The screen is deliberately one level deep. Picking a different glyph for one
 * button is the common case, and installing a whole pack is the rare one, so
 * the slot list is the body of the screen and the pack controls sit above it.
 *
 * Everything the user changes here — a single icon or a whole pack — ends up
 * expressible as a `.wmicons` file, because the single-icon path writes into
 * the reserved "My icons" pack that Export then writes out.
 */
@Composable
internal fun IconsScreen(repository: SettingsRepository, settings: KeyboardSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { IconPackStore.get(context) }
    // The store is a plain file-backed object with no flow of its own, so this
    // is what re-reads it after a mutation.
    var revision by remember { mutableIntStateOf(0) }
    val packs = remember(revision) { store.packs() }

    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<IconPack?>(null) }
    var picking by remember { mutableStateOf<IconSlot?>(null) }

    // CreateDocument cannot carry a payload, so the pack waiting to be written
    // is parked here between launching the picker and its result.
    var pendingExport by remember { mutableStateOf<IconPack?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(IconPackFile.MIME_TYPE),
    ) { uri ->
        val pack = pendingExport
        pendingExport = null
        if (uri == null || pack == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        IconPackFile.write(
                            out,
                            pack,
                            appVersion = BuildConfig.VERSION_CODE,
                            appVersionName = BuildConfig.VERSION_NAME,
                        ) { store.fileFor(pack.id, it) }
                    } ?: error("no stream")
                }.isSuccess
            }
            message = if (ok) "Saved ${pack.name}." else "Could not write that file."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!
                        .use { IconPackFile.import(it, store) }
                }.getOrDefault(IconImportResult.Failed)
            }
            revision++
            message = describeImport(result)
            // Switching to it is what the user came for; leaving it installed
            // but inactive would read as "nothing happened".
            (result as? IconImportResult.Imported)?.let { repository.setIconPack(it.pack.id) }
        }
    }

    // The one-slot SVG picker. The slot is parked the same way the export pack
    // is, because OpenDocument carries no payload either.
    var pendingSlot by remember { mutableStateOf<IconSlot?>(null) }
    val svgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val slot = pendingSlot
        pendingSlot = null
        if (uri == null || slot == null) return@rememberLauncherForActivityResult
        scope.launch {
            val mine = store.mine()
            val ok = mine != null && withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { input ->
                        // Reads one byte past the cap: an oversized file is
                        // then rejected by setIcon rather than silently
                        // truncated into something that happens to parse.
                        input.readBoundedBytes(SvgParser.MAX_SOURCE_BYTES + 1)
                    }
                    store.setIcon(mine.id, slot.id, bytes.decodeToString())
                }.getOrDefault(false)
            }
            revision++
            message = if (ok) {
                // Pinning the slot to "My icons" is what makes the choice
                // survive switching packs — it is an override, not a pack.
                repository.setIconOverride(slot.id, IconOverrides.packSource(mine!!.id))
                "Set a custom icon for ${slotLabel(slot)}."
            } else {
                "That file could not be used as an icon. " +
                    "It has to be an SVG under ${SvgParser.MAX_SOURCE_BYTES / 1024} KB."
            }
        }
    }

    CaptionText(
        "Replace any keyboard icon with another built-in one or your own SVG. " +
            "An icon that doesn't set its own colours follows the theme (and the " +
            "tool's accent colour); one that does keeps them.",
    )

    SettingsGroup("Icon pack") {
        item {
            PackRow(
                name = "Built-in icons",
                supporting = "The icons WM Keyboard ships with",
                selected = settings.icons.activePackId.isEmpty(),
                onClick = { scope.launch { repository.setIconPack("") } },
            )
        }
        for (pack in packs) {
            item {
                PackRow(
                    name = pack.name,
                    supporting = buildString {
                        append("${pack.slots.size} icon(s)")
                        if (pack.author.isNotBlank()) append(" · ${pack.author}")
                        if (pack.version.isNotBlank()) append(" · v${pack.version}")
                    },
                    selected = settings.icons.activePackId == pack.id,
                    onClick = { scope.launch { repository.setIconPack(pack.id) } },
                    trailing = {
                        Row {
                            IconButton(onClick = {
                                pendingExport = pack
                                exportLauncher.launch(IconPackFile.fileName(pack))
                            }) {
                                Icon(Icons.Outlined.FileUpload, contentDescription = "Export ${pack.name}")
                            }
                            IconButton(onClick = { confirmDelete = pack }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete ${pack.name}")
                            }
                        }
                    },
                )
            }
        }
    }

    SettingsGroup {
        item {
            ListItem(
                colors = transparentListColors(),
                modifier = Modifier.clickable {
                    importLauncher.launch(IconPackFile.IMPORT_MIME_TYPES)
                },
                leadingContent = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
                headlineContent = { Text("Import an icon pack") },
                supportingContent = { Text("Opens a .wmicons file someone shared") },
            )
        }
        item {
            ListItem(
                colors = transparentListColors(),
                modifier = Modifier.clickable {
                    scope.launch {
                        repository.clearIconOverrides()
                        message = "Every icon is back to the built-in one."
                    }
                },
                leadingContent = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                headlineContent = { Text("Reset all icons") },
                supportingContent = {
                    Text("Turns the pack off and drops every single-icon change. Installed packs are kept.")
                },
            )
        }
    }

    for (group in IconSlotGroup.entries) {
        SettingsGroup(group.title) {
            for (slot in IconSlots.inGroup(group)) {
                item {
                    ListItem(
                        colors = transparentListColors(),
                        modifier = Modifier.clickable { picking = slot },
                        leadingContent = {
                            SlotIcon(slot.id, contentDescription = null)
                        },
                        headlineContent = { Text(slotLabel(slot)) },
                        supportingContent = {
                            CaptionText(describeSource(settings, slot, store))
                        },
                    )
                }
            }
        }
    }

    picking?.let { slot ->
        IconPickerDialog(
            slot = slot,
            settings = settings,
            onPickBuiltin = { name ->
                scope.launch { repository.setIconOverride(slot.id, IconOverrides.builtinSource(name)) }
                picking = null
            },
            onImportSvg = {
                pendingSlot = slot
                picking = null
                svgLauncher.launch(arrayOf("image/svg+xml", "text/xml", "text/plain", "application/octet-stream"))
            },
            onReset = {
                // An SVG the user imported for this slot lives in "My icons".
                // Dropping the override alone would leave the file behind, so
                // it would keep turning up in exports of a pack they thought
                // they had reverted.
                if (settings.icons.overrides[slot.id] ==
                    IconOverrides.packSource(IconPackStore.MINE_ID)
                ) {
                    store.removeIcon(IconPackStore.MINE_ID, slot.id)
                    revision++
                }
                scope.launch { repository.setIconOverride(slot.id, null) }
                picking = null
            },
            onDismiss = { picking = null },
        )
    }

    confirmDelete?.let { pack ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${pack.name}?") },
            text = { Text("Its ${pack.slots.size} icon(s) are removed from this device.") },
            confirmButton = {
                Button(onClick = {
                    store.deletePack(pack.id)
                    scope.launch { repository.forgetIconPack(pack.id) }
                    confirmDelete = null
                    revision++
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

/** A tool slot uses the tool's own settings wording, so the two agree. */
private fun slotLabel(slot: IconSlot): String =
    slot.tool?.let { toolTitle(it) } ?: slot.label

/** What the row says under the name: where this icon currently comes from. */
private fun describeSource(
    settings: KeyboardSettings,
    slot: IconSlot,
    store: IconPackStore,
): String {
    val override = settings.icons.overrides[slot.id]
    if (override != null) {
        if (override.startsWith(IconOverrides.BUILTIN_PREFIX)) {
            val builtin = override.removePrefix(IconOverrides.BUILTIN_PREFIX)
            // A name that is gone (renamed between versions) means the slot is
            // drawing its default; say so rather than naming a ghost.
            return if (BuiltinIcons.byName(builtin) != null) BuiltinIcons.label(builtin) else "Default"
        }
        if (override.startsWith(IconOverrides.PACK_PREFIX)) {
            val packId = override.removePrefix(IconOverrides.PACK_PREFIX)
            val pack = store.pack(packId) ?: return "Default"
            return if (slot.id in pack.slots) "From ${pack.name}" else "Default"
        }
    }
    val active = store.pack(settings.icons.activePackId)
    if (active != null && slot.id in active.slots) return "From ${active.name}"
    return "Default"
}

/** Shared with the open-a-file dialog, which reports the same outcomes. */
internal fun describeImport(result: IconImportResult): String = when (result) {
    is IconImportResult.Imported -> buildString {
        append("Imported ${result.pack.name} — ${result.pack.slots.size} icon(s).")
        if (result.repairs.isNotEmpty()) {
            append("\n\nChanged on the way in:")
            for (line in result.repairs) append("\n• $line")
        }
    }
    IconImportResult.NotAnIconPack -> "That file is not a WM Keyboard icon pack."
    IconImportResult.TooManyPacks ->
        "You already have ${IconPackStore.MAX_PACKS} icon packs. Delete one first."
    IconImportResult.Failed -> "That file could not be read."
}

@Composable
private fun PackRow(
    name: String,
    supporting: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        colors = transparentListColors(),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = "Active")
            } else {
                Spacer(modifier = Modifier.width(24.dp))
            }
        },
        headlineContent = { Text(name) },
        supportingContent = { Text(supporting) },
        trailingContent = trailing,
    )
}

/**
 * Picks one slot's icon: a bundled glyph, a file, or back to the default.
 *
 * The grid is the bundled Material set rather than the whole
 * `material-icons-extended` library — see [BuiltinIcons] for why — with a
 * search box, because 180 icons is well past what anyone scans by eye.
 */
@Composable
private fun IconPickerDialog(
    slot: IconSlot,
    settings: KeyboardSettings,
    onPickBuiltin: (String) -> Unit,
    onImportSvg: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val selected = settings.icons.overrides[slot.id]?.removePrefix(IconOverrides.BUILTIN_PREFIX)
    val shown = remember(query) {
        val needle = query.trim()
        if (needle.isEmpty()) BuiltinIcons.names
        else BuiltinIcons.names.filter { BuiltinIcons.label(it).contains(needle, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(slotLabel(slot)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search icons") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (shown.isEmpty()) {
                    Text("No icon matches “${query.trim()}”.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(52.dp),
                        modifier = Modifier.heightIn(max = 280.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(shown, key = { it }) { name ->
                            val vector = BuiltinIcons.catalog.getValue(name)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (name == selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { onPickBuiltin(name) },
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        vector,
                                        contentDescription = BuiltinIcons.label(name),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onImportSvg, modifier = Modifier.fillMaxWidth()) {
                    Text("Use my own SVG…")
                }
            }
        },
        confirmButton = { TextButton(onClick = onReset) { Text("Use default") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Reads at most [limit] bytes. Android has no `InputStream.readNBytes` before
 * API 33 and this app runs back to 24, so the bound is applied by hand — an
 * unbounded read here would let a picked file of any size into memory.
 */
private fun java.io.InputStream.readBoundedBytes(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n <= 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
