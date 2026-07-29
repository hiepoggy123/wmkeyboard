package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import com.wasimaster.wmkeyboard.core.layout.KeyRole
import com.wasimaster.wmkeyboard.core.layout.LayerSpec
import com.wasimaster.wmkeyboard.core.util.requireInputStream
import com.wasimaster.wmkeyboard.core.util.runCancellable
import kotlin.math.roundToInt
import androidx.compose.material3.Button
import com.wasimaster.wmkeyboard.core.layout.LayoutCodec
import com.wasimaster.wmkeyboard.core.layout.repair
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.core.addons.AddonStore
import com.wasimaster.wmkeyboard.core.addons.AddonType
import com.wasimaster.wmkeyboard.core.layout.ImportedLayout
import com.wasimaster.wmkeyboard.core.layout.LayoutFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.wasimaster.wmkeyboard.core.layout.AssetLayouts
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.KeyboardLayout
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.ModifierKey
import com.wasimaster.wmkeyboard.core.layout.LayoutSeverity
import com.wasimaster.wmkeyboard.core.layout.compile
import com.wasimaster.wmkeyboard.ime.ui.KeyIcons
import com.wasimaster.wmkeyboard.core.layout.gridWeightOf
import com.wasimaster.wmkeyboard.core.layout.resolveLayouts
import com.wasimaster.wmkeyboard.core.layout.sidePadFor
import com.wasimaster.wmkeyboard.core.layout.validateLayout
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.ime.ui.KbTheme
import com.wasimaster.wmkeyboard.ime.ui.KeyboardThemeProvider
import com.wasimaster.wmkeyboard.ime.ui.LocalKbTheme
import com.wasimaster.wmkeyboard.ime.ui.keyShape
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Gallery
// ---------------------------------------------------------------------------

/**
 * Every layout the user has: the shipped ones, then their own.
 *
 * A list rather than the two-per-row card grid the themes gallery uses. A
 * theme's whole identity is a colour swatch and reads fine at 150dp; a layout's
 * identity is its key arrangement, and a ten-column grid in a half-width card
 * gives about 17dp per key. A full-width row with a one-line shape summary
 * carries more than a shrunken grid would.
 */
@Composable
internal fun KeyLayoutsScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf<LayoutSpec?>(null) }
    var confirmImport by remember { mutableStateOf<ImportedLayout?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    // CreateDocument cannot carry a payload, so the layout waiting to be written
    // is parked here between launching the picker and its result.
    var pendingExport by remember { mutableStateOf<LayoutSpec?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(LayoutFile.MIME_TYPE),
    ) { uri ->
        val layout = pendingExport
        pendingExport = null
        if (uri == null || layout == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCancellable {
                val text = LayoutFile.encode(
                    layout,
                    appVersion = BuildConfig.VERSION_CODE,
                    appVersionName = BuildConfig.VERSION_NAME,
                )
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray())
                    } ?: error("no stream")
                }
            }.isSuccess
            // Reported either way. The theme export swallows its failures, and
            // the exported file may be the only copy of an hour's work.
            message = if (ok) "Saved ${layout.name}." else "Could not write that file."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.requireInputStream(uri)
                        .use { it.readBytes().decodeToString() }
                }.getOrNull()
            }
            val parsed = text?.let { LayoutFile.decode(it) }
            if (parsed == null) {
                message = "That file is not a WMKeyboard layout."
                return@launch
            }
            // Read first, ask, then write. Importing a layout is not something
            // to discover you have done — and it never activates it either.
            confirmImport = parsed
        }
    }

    val layouts = resolveLayouts(settings.customLayouts)
    val customIds = settings.customLayouts.map { it.id }.toSet()
    // "Shipped" is both the compiled built-ins and the JSON asset layouts.
    // Testing only BuiltInLayouts put every asset layout in neither group — an
    // enabled Français BÉPO was invisible here — and made an *edit* of one look
    // like a layout of the user's own, offering Delete where it should offer
    // Reset. resolveLayouts already treats the two the same way.
    val shippedIds = remember(layouts) {
        (BuiltInLayouts.all + AssetLayouts.all).mapTo(HashSet()) { it.id }
    }
    // Layouts that arrived from an addon repository rather than from this
    // screen. They live under Languages → Your layouts, which is where the
    // switch that turns one on is, and are removed from the Addons screen.
    val addonStore = remember { AddonStore.get(context) }
    val addonRevision by addonStore.revision.collectAsStateWithLifecycle()
    val addonLayoutIds = remember(addonRevision) {
        addonStore.installed().values
            .filter { it.type == AddonType.Layout }
            .mapTo(HashSet()) { it.localRef }
    }

    CaptionText(
        "A layout is a key grid. It types the language of the layout it is based on, " +
            "so a rearranged Bengali grid still uses the Bengali dictionary.",
    )

    /**
     * Copies a layout and opens the copy.
     *
     * Deliberately does not activate it, unlike the themes gallery, which
     * applies a duplicate before navigating. A layout owns delete, enter and
     * space, and a half-built copy becoming the live keyboard mid-edit is how
     * someone ends up unable to type well enough to undo it. Custom layouts go
     * live only from their toggle under Languages, and only once they validate.
     */
    fun duplicateAndEdit(base: LayoutSpec) {
        scope.launch {
            val id = "custom_${System.currentTimeMillis()}"
            repository.upsertCustomLayout(base.copy(id = id, name = "${base.name} copy"))
            onNavigate("keymap_edit/$id")
        }
    }

    SettingsGroup("Your layouts") {
        // Only enabled layouts are listed: this page manages the grids you
        // actually type with, not the whole registry. Enable others under
        // Languages first and they appear here.
        //
        // Addon layouts are left out entirely. This group is "grids you made",
        // and an installed one is neither made here nor managed here — it is
        // switched on under Languages and removed from Addons, and editing it
        // would only produce changes the next update silently discards.
        val customs = layouts.filter {
            it.id in customIds &&
                it.id !in shippedIds &&
                it.id !in addonLayoutIds &&
                it.id in settings.enabledLayoutIds
        }
        if (customs.isEmpty()) {
            item {
                WmRow(
                    title = "No layouts of your own yet",
                    subtitle = "Copy one below to start from a grid that already works.",
                )
            }
        }
        for (layout in customs) {
            item {
                LayoutRow(
                    layout = layout,
                    enabled = layout.id in settings.enabledLayoutIds,
                    onEdit = { onNavigate("keymap_edit/${layout.id}") },
                    onExport = {
                        pendingExport = layout
                        exportLauncher.launch(LayoutFile.fileName(layout))
                    },
                    onDuplicate = { duplicateAndEdit(layout) },
                    onDelete = { confirmDelete = layout },
                    deleteIsReset = false,
                )
            }
        }
    }

    SettingsGroup {
        item {
            WmRow(
                title = "Import a layout",
                subtitle = "Opens a .wmlayout.json file someone shared",
                leading = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
                onClick = {
                    importLauncher.launch(LayoutFile.IMPORT_MIME_TYPES)
                },
            )
        }
    }

    val builtIns = layouts.filter {
        it.id in shippedIds && it.id in settings.enabledLayoutIds
    }
    if (builtIns.isNotEmpty()) {
        SettingsGroup("Built in") {
            for (layout in builtIns) {
                item {
                    LayoutRow(
                        layout = layout,
                        enabled = layout.id in settings.enabledLayoutIds,
                        onEdit = { onNavigate("keymap_edit/${layout.id}") },
                        onExport = {
                            pendingExport = layout
                            exportLauncher.launch(LayoutFile.fileName(layout))
                        },
                        onDuplicate = { duplicateAndEdit(layout) },
                        // An edited built-in is stored as an override under the same
                        // id, so removing it restores the shipped grid rather than
                        // deleting anything — hence Reset, not Delete.
                        onDelete = if (layout.id in customIds) {
                            { confirmDelete = layout }
                        } else {
                            null
                        },
                        deleteIsReset = true,
                    )
                }
            }
        }
    }

    confirmImport?.let { imported ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Import ${imported.layout.name}?") },
            text = {
                Column {
                    Text(
                        "It is added to your layouts but not switched on — turn it on " +
                            "under Languages when you are ready to type with it.",
                    )
                    if (imported.repairs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Changed on the way in:", fontWeight = FontWeight.Medium)
                        for (line in imported.repairs) Text("• $line")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = "custom_${System.currentTimeMillis()}"
                    scope.launch {
                        repository.upsertCustomLayout(imported.layout.copy(id = id))
                        message = "Imported ${imported.layout.name}."
                    }
                    confirmImport = null
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = null }) { Text("Cancel") }
            },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }

    confirmDelete?.let { layout ->
        val reset = layout.id in shippedIds
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (reset) "Reset ${layout.name}?" else "Delete ${layout.name}?") },
            text = {
                Text(
                    if (reset) {
                        "Your changes to this built-in layout are discarded and the " +
                            "original grid comes back. Nothing else changes."
                    } else {
                        "This layout is removed. If it is switched on under Languages " +
                            "it is switched off, and the keyboard falls back to QWERTY."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteCustomLayout(layout.id) }
                    confirmDelete = null
                }) { Text(if (reset) "Reset" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LayoutRow(
    layout: LayoutSpec,
    enabled: Boolean,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (() -> Unit)?,
    deleteIsReset: Boolean,
) {
    WmRow(
        title = layout.name,
        subtitle = layoutSummary(layout, enabled),
        trailing = {
            Row {
                IconButton(onClick = onExport) {
                    Icon(Icons.Outlined.Share, contentDescription = "Export ${layout.name}")
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate ${layout.name}")
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            if (deleteIsReset) Icons.Outlined.Refresh else Icons.Outlined.Delete,
                            contentDescription =
                                if (deleteIsReset) "Reset ${layout.name}" else "Delete ${layout.name}",
                        )
                    }
                }
            }
        },
        onClick = onEdit,
    )
}

/** One line describing a layout: its language, its shape, and whether it is on. */
internal fun layoutSummary(layout: LayoutSpec, enabled: Boolean): String {
    val letters = layout.compile(LayoutLayer.LETTERS).rows
    val shape = "${letters.size} rows · ${letters.sumOf { it.size }} keys"
    val extras = layout.layers.keys.count { it != LayoutLayer.LETTERS.key }
    val layers = if (extras > 0) " · $extras custom layer${if (extras == 1) "" else "s"}" else ""
    val state = if (enabled) "On" else "Off"
    return "$state · ${baseModeTitle(layout)} · $shape$layers"
}

/**
 * The catalog's name for a mode, so the subtitle tracks a renamed catalog entry
 * rather than duplicating it.
 */
internal fun baseModeTitle(layout: LayoutSpec): String =
    "${layout.language().displayName} · ${layout.name}"

// ---------------------------------------------------------------------------
// Editor
// ---------------------------------------------------------------------------

/** Row and column address of one key in the layer being edited. */
internal data class KeyRef(val row: Int, val col: Int)

/**
 * Undo holds whole layouts rather than diffs. A layout is a few kB of data
 * classes, thirty of them cost nothing, and a diff type would need an inverse
 * for every edit the sheet can make — which is exactly the list that keeps
 * growing as actions gain payloads.
 */
private const val UndoDepth = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeyLayoutEditorScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    layoutId: String,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val layout = resolveLayouts(settings.customLayouts).firstOrNull { it.id == layoutId }
    if (layout == null) {
        Text(
            "This layout no longer exists.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    var layer by rememberSaveable(layoutId) { mutableStateOf(LayoutLayer.LETTERS) }
    var selection by remember(layoutId, layer) { mutableStateOf<KeyRef?>(null) }
    var showShift by rememberSaveable(layoutId) { mutableStateOf(false) }
    var sheetOpen by remember(layoutId, layer) { mutableStateOf(false) }

    // Session-scoped on purpose. Persisting it would mean a second serialized
    // document per layout, for a benefit — "undo what I did last Tuesday" —
    // that nobody expects from an editor.
    var undo by remember(layoutId) { mutableStateOf(emptyList<LayoutSpec>()) }
    var redo by remember(layoutId) { mutableStateOf(emptyList<LayoutSpec>()) }
    var stepPushed by remember(layoutId) { mutableStateOf(false) }

    // Editing an inherited layer authors this layout's own copy of it. Until
    // then the grid shows the built-in, which is what makes "replaces
    // everything" survivable: moving one letter must not cost you a phone pad.
    fun withLayerRows(spec: LayoutSpec, rows: List<List<Key>>): LayoutSpec {
        val existing = spec.layer(layer) ?: LayerSpec(rows)
        return spec.copy(layers = spec.layers + (layer.key to existing.copy(rows = rows)))
    }

    fun push() {
        undo = (undo + layout).takeLast(UndoDepth)
        redo = emptyList()
    }

    /** Restores a whole layout, for undo and redo. */
    fun save(next: LayoutSpec) {
        scope.launch { repository.upsertCustomLayout(next) }
    }

    /**
     * Applies an edit through the repository rather than to the layout held
     * here. This screen saves on every keystroke and its copy of the layout
     * comes from the settings flow, which lags the write it just made — so two
     * edits landing within a frame would see the same stale copy and the second
     * would undo the first.
     */
    fun apply(transform: (LayoutSpec) -> LayoutSpec) {
        scope.launch { repository.updateCustomLayout(layoutId, transform) }
    }

    /** One discrete edit: one undo step. */
    fun edit(transform: (LayoutSpec) -> LayoutSpec) {
        push()
        stepPushed = true
        apply(transform)
    }

    /**
     * An edit inside one key-sheet session. Pushing per keystroke would flush
     * thirty slots typing "https://", and "undo my edit to that key" is the step
     * users actually have in mind.
     */
    fun editCoalesced(transform: (LayoutSpec) -> LayoutSpec) {
        if (!stepPushed) push()
        stepPushed = true
        apply(transform)
    }

    // Every transform below derives its rows from the spec it is handed, never
    // from the copy this composition is holding, for the staleness reason above.
    fun editRows(transform: (List<List<Key>>) -> List<List<Key>>) {
        edit { spec -> withLayerRows(spec, transform(spec.compile(layer).rows)) }
    }

    // Whole-layer edit, so a structural change to the rows can keep the parallel
    // per-row heights aligned. Authoring an inherited layer copies the built-in's
    // compiled grid (heights and all) first.
    fun editLayer(transform: (LayerSpec) -> LayerSpec) {
        edit { spec ->
            val compiled = spec.compile(layer)
            val base = spec.layer(layer)
                ?: LayerSpec(rows = compiled.rows, rowHeights = compiled.rowHeights)
            spec.copy(layers = spec.layers + (layer.key to transform(base)))
        }
    }

    // Reindexes per-row heights by source row index so they follow the rows
    // through a reorder/duplicate/delete. Stays null while every row is the
    // default height, so untouched layouts never grow the field.
    fun pickHeights(heights: List<Float>?, sourceIndices: List<Int>): List<Float>? =
        heights?.let { h -> sourceIndices.map { h.getOrNull(it) ?: 1f } }

    fun setRowHeight(rowIndex: Int, value: Float) {
        editLayer { ls ->
            val list = MutableList(ls.rows.size) { ls.rowHeights?.getOrNull(it) ?: 1f }
            if (rowIndex in list.indices) list[rowIndex] = value
            ls.copy(rowHeights = if (list.all { it == 1f }) null else list.toList())
        }
    }

    val rows = layout.compile(layer).rows
    val rowHeights = layout.compile(layer).rowHeights
    val selectedKey = selection?.let { rows.getOrNull(it.row)?.getOrNull(it.col) }

    SectionHeaderPublic(layout.name)

    LayerChips(layout, layer) { layer = it; selection = null }

    if (layout.layer(layer) == null) {
        if (layer == LayoutLayer.FN) {
            // Nothing ships an Fn layer, so there is no built-in to inherit and
            // the grid above is a stand-in. Offer the template instead.
            CaptionText(
                "This layout has no Fn layer yet. Add one and put an Fn key on " +
                    "another layer to reach it.",
            )
            SettingsGroup {
                item {
                    WmRow(
                        title = "Add an Fn layer",
                        subtitle = "Starts from Esc, F1–F12, Tab, the arrows and Home/End",
                        leading = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        onClick = {
                            edit { it.copy(layers = it.layers + (layer.key to BuiltInLayouts.FN_DEFAULT)) }
                        },
                    )
                }
            }
        } else {
            CaptionText(
                "Showing the built-in ${layerTitle(layer).lowercase()} grid. Changing " +
                    "anything here makes this layout's own copy of it.",
            )
        }
    }

    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            enabled = undo.isNotEmpty(),
            onClick = {
                val previous = undo.last()
                undo = undo.dropLast(1)
                redo = redo + layout
                stepPushed = false
                save(previous)
            },
        ) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo") }
        IconButton(
            enabled = redo.isNotEmpty(),
            onClick = {
                val next = redo.last()
                redo = redo.dropLast(1)
                undo = undo + layout
                stepPushed = false
                save(next)
            },
        ) { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo") }
        Spacer(Modifier.weight(1f))
        Text(
            "Saved automatically",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    EditorGrid(
        layout = layout.compile(layer),
        settings = settings,
        selection = selection,
        showShift = showShift,
        onSelect = { ref ->
            selection = ref
            stepPushed = false
            sheetOpen = true
        },
    )

    selection?.let { ref ->
        if (ref.row in rows.indices) {
            RowActionBar(
                rowIndex = ref.row,
                rowCount = rows.size,
                rowWidth = rows[ref.row].sumOf { it.width.toDouble() }.toFloat(),
                gridWeight = gridWeightOf(rows),
                onAddKey = {
                    editRows { r ->
                        r.mapIndexed { i, row ->
                            if (i == ref.row) row + Key("new") else row
                        }
                    }
                },
                onDuplicateRow = {
                    editLayer { ls ->
                        val src = (0..ref.row) + ref.row + (ref.row + 1 until ls.rows.size)
                        ls.copy(
                            rows = src.map { ls.rows[it] },
                            rowHeights = pickHeights(ls.rowHeights, src),
                        )
                    }
                },
                onDeleteRow = {
                    editLayer { ls ->
                        val src = ls.rows.indices.filter { it != ref.row }
                        ls.copy(
                            rows = src.map { ls.rows[it] },
                            rowHeights = pickHeights(ls.rowHeights, src),
                        )
                    }
                    selection = null
                },
            )
            RowHeightRow(
                height = rowHeights?.getOrNull(ref.row) ?: 1f,
            ) { setRowHeight(ref.row, it) }
        }
    }

    SettingsGroup {
        item {
            WmRow(
                title = "Add a row",
                subtitle = "Appends an empty row to this layer",
                leading = { Icon(Icons.Outlined.Add, contentDescription = null) },
                onClick = {
                    editLayer { ls ->
                        ls.copy(
                            rows = ls.rows + listOf(listOf(Key("new"))),
                            rowHeights = ls.rowHeights?.plus(1f),
                        )
                    }
                },
            )
        }
        item {
            ReorderSetting(
                title = "Reorder rows",
                dialogTitle = "Row order",
                items = rows.indices.toList(),
                label = { i -> "Row ${i + 1} · ${rows[i].size} keys" },
            ) { order ->
                editLayer { ls ->
                    ls.copy(
                        rows = order.map { ls.rows[it] },
                        rowHeights = pickHeights(ls.rowHeights, order),
                    )
                }
            }
        }
        selection?.let { ref ->
            if (ref.row in rows.indices && rows[ref.row].size > 1) {
                item {
                    ReorderSetting(
                        title = "Reorder keys in row ${ref.row + 1}",
                        dialogTitle = "Key order",
                        items = rows[ref.row],
                        label = { keyReorderLabel(it) },
                    ) { order ->
                        editRows { r -> r.mapIndexed { i, row -> if (i == ref.row) order else row } }
                        selection = null
                    }
                }
            }
        }
        item {
            ToggleSetting(
                "Show the shift plane",
                "Draw each key as it appears with shift held",
                showShift,
            ) { showShift = it }
        }
        item {
            NavRow(
                "Edit as JSON",
                subtitle = "Reach anything this screen has no control for",
            ) { onNavigate("keymap_json/$layoutId") }
        }
        if (layout.layer(layer) != null) {
            item {
                WmRow(
                    title = "Reset this layer",
                    subtitle = "Drops your ${layerTitle(layer).lowercase()} grid and uses the built-in one",
                    leading = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    onClick = {
                        edit { it.copy(layers = it.layers - layer.key) }
                        selection = null
                    },
                )
            }
        }
    }

    val findings = validateLayout(layout)
    if (findings.isNotEmpty()) {
        SettingsGroup("Problems") {
            for (finding in findings) {
                item {
                    WmRow(
                        title = finding.message,
                        subtitle = if (finding.severity == LayoutSeverity.BLOCKING) {
                                "Has to be fixed before this layout can be switched on."
                            } else {
                                "Worth a look, but the layout still works."
                            },
                    )
                }
            }
        }
    }

    CaptionText(
        "Nothing you change here affects typing until the layout is switched on " +
            "under Languages.",
    )

    val ref = selection
    if (sheetOpen && ref != null && selectedKey != null) {
        KeyEditSheet(
            key = selectedKey,
            ref = ref,
            rowSize = rows[ref.row].size,
            gridWeight = gridWeightOf(rows),
            otherWidthsInRow = rows[ref.row]
                .filterIndexed { i, _ -> i != ref.col }
                .sumOf { it.width.toDouble() }
                .toFloat(),
            onChange = { updated ->
                editCoalesced { spec ->
                    withLayerRows(
                        spec,
                        spec.compile(layer).rows.mapIndexed { r, row ->
                            if (r != ref.row) {
                                row
                            } else {
                                row.mapIndexed { c, k -> if (c == ref.col) updated else k }
                            }
                        },
                    )
                }
            },
            onMove = { delta ->
                val target = ref.col + delta
                if (target in rows[ref.row].indices) {
                    editRows { r ->
                        r.mapIndexed { i, row ->
                            if (i != ref.row) {
                                row
                            } else {
                                row.toMutableList().apply { add(target, removeAt(ref.col)) }
                            }
                        }
                    }
                    selection = ref.copy(col = target)
                }
            },
            onDuplicate = {
                editRows { r ->
                    r.mapIndexed { i, row ->
                        if (i != ref.row) {
                            row
                        } else {
                            row.subList(0, ref.col + 1) + row[ref.col] + row.drop(ref.col + 1)
                        }
                    }
                }
            },
            onDelete = {
                editRows { r ->
                    r.mapIndexed { i, row ->
                        if (i != ref.row) row else row.filterIndexed { c, _ -> c != ref.col }
                    }
                }
                selection = null
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

/** How a key reads in the reorder dialog, where there is no grid to look at. */
private fun keyReorderLabel(key: Key): String = when {
    key.label.isNotBlank() -> key.label
    else -> KeyActionCatalog.firstOrNull { it.matches(key.action) }?.title ?: "Key"
}

/** Contextual actions for the row the selected key sits in. */
@Composable
private fun RowActionBar(
    rowIndex: Int,
    rowCount: Int,
    rowWidth: Float,
    gridWeight: Float,
    onAddKey: () -> Unit,
    onDuplicateRow: () -> Unit,
    onDeleteRow: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Row ${rowIndex + 1}", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        // Only worth saying when it disagrees with the grid — the width the
        // keyboard measures every other row against. Printed on every row it
        // would be five numbers that are correct and identical almost always.
        if (kotlin.math.abs(rowWidth - gridWeight) > 0.01f) {
            Text(
                "%.2f wide, grid is %.2f".format(rowWidth, gridWeight),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onAddKey) {
            Icon(Icons.Outlined.Add, contentDescription = "Add key to row ${rowIndex + 1}")
        }
        IconButton(onClick = onDuplicateRow) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate row ${rowIndex + 1}")
        }
        IconButton(enabled = rowCount > 1, onClick = onDeleteRow) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete row ${rowIndex + 1}")
        }
    }
}

/** Layer tabs. The pencil marks a layer this layout has actually authored. */
@Composable
private fun LayerChips(
    layout: LayoutSpec,
    selected: LayoutLayer,
    onSelect: (LayoutLayer) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(LayoutLayer.entries) { layer ->
            val authored = layout.layer(layer) != null
            FilterChip(
                selected = layer == selected,
                onClick = { onSelect(layer) },
                label = { Text(layerTitle(layer), maxLines = 1) },
                leadingIcon = if (authored) {
                    {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Customised",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

internal fun layerTitle(layer: LayoutLayer): String = when (layer) {
    LayoutLayer.LETTERS -> "Letters"
    LayoutLayer.SYMBOLS -> "Symbols"
    LayoutLayer.SYMBOLS_SHIFTED -> "Symbols 2"
    LayoutLayer.NUMBER -> "Number"
    LayoutLayer.PHONE -> "Phone"
    LayoutLayer.DATE -> "Date"
    LayoutLayer.TIME -> "Time"
    LayoutLayer.DATETIME -> "Date & time"
    LayoutLayer.FN -> "Fn"
}

/**
 * The grid the user edits, drawn in the keyboard's own theme.
 *
 * [KeyboardThemeProvider] needs only [KeyboardSettings], so the editor gets the
 * real key colours, key shape, corner radius and font without touching the input
 * pipeline — `KeyboardScreen` itself wants a `StateFlow<KeyboardUiState>` and
 * some ninety callbacks, and a synthetic state that large is a maintenance
 * liability rather than a preview. Reusing the real `KeyCell`/`KeyButton` was the
 * other option and was rejected for the same reason, plus their whole job is the
 * press machinery — long-press popups, key repeat, the spacebar hold timer —
 * every one of which fights tap-to-select.
 *
 * The provider is scoped to this Box on purpose: it swaps MaterialTheme, and
 * hoisting it any higher would repaint every row and slider on the screen in the
 * keyboard's palette.
 */
@Composable
private fun EditorGrid(
    layout: KeyboardLayout,
    settings: KeyboardSettings,
    selection: KeyRef?,
    showShift: Boolean,
    onSelect: (KeyRef) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
    ) {
        KeyboardThemeProvider(settings) {
            val kb = LocalKbTheme.current
            // Forced LTR: a key's index in its row is its serialized order, so an
            // RTL locale mirroring the grid would make "move right" write
            // index - 1. Labels inside each cell still resolve their own bidi.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(kb.board)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // The most common row width sets the grid and every other
                    // row is centred against it, or squeezed if it is wider —
                    // the same rule the real keyboard lays rows out by, taken
                    // from the same helpers so the two can never disagree.
                    val gridWeight = gridWeightOf(layout.rows).takeIf { it > 0f } ?: 10f
                    if (layout.rows.isEmpty()) {
                        Text(
                            "This layer has no rows.",
                            modifier = Modifier.padding(12.dp),
                            color = kb.keyText.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                    }
                    layout.rows.forEachIndexed { r, row ->
                        val sidePad = sidePadFor(row, gridWeight)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (sidePad > 0.01f) Spacer(Modifier.weight(sidePad))
                            row.forEachIndexed { c, key ->
                                EditorKeyCell(
                                    key = key,
                                    kb = kb,
                                    heightDp = settings.keyHeightDp,
                                    selected = selection == KeyRef(r, c),
                                    showShift = showShift,
                                    modifier = Modifier.weight(key.width),
                                ) { onSelect(KeyRef(r, c)) }
                            }
                            if (sidePad > 0.01f) Spacer(Modifier.weight(sidePad))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.EditorKeyCell(
    key: Key,
    kb: KbTheme,
    heightDp: Int,
    selected: Boolean,
    showShift: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        key.action == KeyAction.Enter -> kb.enterKey
        key.action != KeyAction.Text -> kb.modifierKey
        else -> kb.key
    }
    val foreground = when {
        key.action == KeyAction.Enter -> kb.enterKeyText
        key.action != KeyAction.Text -> kb.modifierKeyText
        else -> kb.keyText
    }
    // The user's own key height keeps the preview honest, but a 100dp setting
    // would put two rows on screen — clamping is cheaper than a zoom control.
    val height = heightDp.dp.coerceIn(38.dp, 56.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(kb.keyShape())
            .background(background)
            .then(
                if (selected) {
                    Modifier.border(2.dp, kb.accent, kb.keyShape())
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Shown verbatim rather than uppercased the way the real keyboard does
        // under shift: the stored label is the thing being edited.
        val primary = if (showShift) key.shiftLabel ?: key.label.uppercase() else key.label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val cellIcon = KeyIcons.byName(key.icon)
            if (cellIcon != null) {
                Icon(
                    cellIcon,
                    contentDescription = primary.ifBlank { key.icon },
                    tint = foreground,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = primary.ifBlank { actionGlyph(key.action) },
                    color = foreground,
                    fontSize = labelSize(primary),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            // Probhat and Jatiya put half the alphabet on shiftLabel, and many
            // fonts render a bare matra (া, ি) as an orphaned mark. Showing the
            // pair identifies the key even when the top glyph is ambiguous. The
            // real keyboard swaps on shift instead, so this is editor-only.
            val shiftLabel = key.shiftLabel
            if (!showShift && shiftLabel != null) {
                Text(
                    text = shiftLabel,
                    color = foreground.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * ".com" and "https://" are legal labels and would blow a cell open at full
 * size, so the label steps down as it grows.
 */
private fun labelSize(label: String) = when {
    label.length <= 1 -> 16.sp
    label.length <= 3 -> 13.sp
    else -> 11.sp
}

/** What to draw for an action key whose label is blank, like a keypad spacebar. */
private fun actionGlyph(action: KeyAction): String = when (action) {
    KeyAction.Space -> "space"
    KeyAction.Enter -> "⏎"
    KeyAction.Delete -> "⌫"
    KeyAction.ForwardDelete -> "⌦"
    KeyAction.Shift -> "⇧"
    KeyAction.None -> ""
    else -> "·"
}


// ---------------------------------------------------------------------------
// Key edit sheet
// ---------------------------------------------------------------------------

/**
 * One pickable action, with whatever extra input it needs.
 *
 * The picker renders from this list rather than from `KeyAction`'s members, so
 * an action that ships later — a keycode sender, a tool launcher — is one entry
 * here plus its serialization, and no change to the editor at all. Building it
 * off an enum's entries would bake in "an action is a bare value" and break the
 * moment payloads land.
 */
internal data class KeyActionOption(
    val title: String,
    val group: String,
    val detail: String,
    val build: () -> KeyAction,
    val matches: (KeyAction) -> Boolean,
)

internal val KeyActionCatalog: List<KeyActionOption> = listOf(
    KeyActionOption(
        "Types text", "Typing", "The label, or the output if you set one",
        { KeyAction.Text }, { it == KeyAction.Text },
    ),
    KeyActionOption(
        "Shift", "Typing", "Tap for one capital, double-tap for caps lock",
        { KeyAction.Shift }, { it == KeyAction.Shift },
    ),
    KeyActionOption(
        "Delete", "Typing", "Backspace; repeats while held",
        { KeyAction.Delete }, { it == KeyAction.Delete },
    ),
    KeyActionOption(
        "Forward delete", "Typing", "⌦ — deletes ahead of the cursor; repeats while held",
        { KeyAction.ForwardDelete }, { it == KeyAction.ForwardDelete },
    ),
    KeyActionOption(
        "Space", "Typing", "Swipes on it move the cursor or switch layout",
        { KeyAction.Space }, { it == KeyAction.Space },
    ),
    KeyActionOption(
        "Enter", "Typing", "Takes the app's own action — send, search, done",
        { KeyAction.Enter }, { it == KeyAction.Enter },
    ),
    KeyActionOption(
        "Symbols page", "Layers", "Cycles ?123 and =\\<",
        { KeyAction.Symbols }, { it == KeyAction.Symbols },
    ),
    KeyActionOption(
        "Letters page", "Layers", "Back to the letter grid",
        { KeyAction.Letters }, { it == KeyAction.Letters },
    ),
    KeyActionOption(
        "Emoji panel", "Layers", "Opens the emoji picker",
        { KeyAction.Emoji }, { it == KeyAction.Emoji },
    ),
    KeyActionOption(
        "Switch layout", "Layers", "Cycles the layouts you have switched on",
        { KeyAction.LanguageSwitch }, { it == KeyAction.LanguageSwitch },
    ),
    KeyActionOption(
        "Ctrl", "Modifiers", "Held for the next key — tap twice to lock it",
        { KeyAction.Mod(ModifierKey.CTRL) },
        { it is KeyAction.Mod && it.key == ModifierKey.CTRL },
    ),
    KeyActionOption(
        "Alt", "Modifiers", "Held for the next key — tap twice to lock it",
        { KeyAction.Mod(ModifierKey.ALT) },
        { it is KeyAction.Mod && it.key == ModifierKey.ALT },
    ),
    KeyActionOption(
        "Meta", "Modifiers", "The ⌘ or Windows modifier",
        { KeyAction.Mod(ModifierKey.META) },
        { it is KeyAction.Mod && it.key == ModifierKey.META },
    ),
    KeyActionOption(
        "Fn layer", "Layers", "Switches to this layout's Fn grid for one key",
        { KeyAction.Fn }, { it == KeyAction.Fn },
    ),
    KeyActionOption(
        "Tab", "Keys apps understand", "Sends a real Tab key press",
        { KeyAction.SendKey(KEYCODE_TAB) },
        { it is KeyAction.SendKey && it.keyCode == KEYCODE_TAB },
    ),
    KeyActionOption(
        "Escape", "Keys apps understand", "Sends a real Esc key press",
        { KeyAction.SendKey(KEYCODE_ESCAPE) },
        { it is KeyAction.SendKey && it.keyCode == KEYCODE_ESCAPE },
    ),
    KeyActionOption(
        "Arrow up", "Keys apps understand", "Moves the cursor up a line",
        { KeyAction.SendKey(KEYCODE_DPAD_UP) },
        { it is KeyAction.SendKey && it.keyCode == KEYCODE_DPAD_UP },
    ),
    KeyActionOption(
        "Arrow down", "Keys apps understand", "Moves the cursor down a line",
        { KeyAction.SendKey(KEYCODE_DPAD_DOWN) },
        { it is KeyAction.SendKey && it.keyCode == KEYCODE_DPAD_DOWN },
    ),
    KeyActionOption(
        "Arrow left", "Keys apps understand", "Moves the cursor left",
        { KeyAction.SendKey(KEYCODE_DPAD_LEFT) },
        { it is KeyAction.SendKey && it.keyCode == KEYCODE_DPAD_LEFT },
    ),
    KeyActionOption(
        "Arrow right", "Keys apps understand", "Moves the cursor right",
        { KeyAction.SendKey(KEYCODE_DPAD_RIGHT) },
        { it is KeyAction.SendKey && it.keyCode == KEYCODE_DPAD_RIGHT },
    ),
    KeyActionOption(
        "Braille dot", "Chorded input",
        "One dot of a six-key braille chord; set the dot number below",
        { KeyAction.BrailleDot(1) }, { it is KeyAction.BrailleDot },
    ),
    KeyActionOption(
        "Morse dot", "Chorded input",
        "Adds a short signal; the letter commits after a pause",
        { KeyAction.MorseDot }, { it == KeyAction.MorseDot },
    ),
    KeyActionOption(
        "Morse dash", "Chorded input",
        "Adds a long signal; the letter commits after a pause",
        { KeyAction.MorseDash }, { it == KeyAction.MorseDash },
    ),
    KeyActionOption(
        "Broadcast intent", "Other",
        "Fires an Android broadcast for automation apps (Tasker); set the action below",
        { KeyAction.Broadcast("") }, { it is KeyAction.Broadcast },
    ),
    KeyActionOption(
        "Nothing", "Other", "A deliberate gap: drawn as empty space, swallows taps",
        { KeyAction.None }, { it == KeyAction.None },
    ),
)

// Written as numbers rather than KeyEvent.KEYCODE_* so this file, which is
// otherwise pure settings UI, needs no android.view import.
private const val KEYCODE_TAB = 61
private const val KEYCODE_ESCAPE = 111
private const val KEYCODE_DPAD_UP = 19
private const val KEYCODE_DPAD_DOWN = 20
private const val KEYCODE_DPAD_LEFT = 21
private const val KEYCODE_DPAD_RIGHT = 22

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyEditSheet(
    key: Key,
    ref: KeyRef,
    rowSize: Int,
    gridWeight: Float,
    otherWidthsInRow: Float,
    onChange: (Key) -> Unit,
    onMove: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingAction by remember { mutableStateOf(false) }
    // Held as text so a half-typed entry survives; parsed on every change.
    var alternates by remember(ref) { mutableStateOf(key.longPress.joinToString(" ")) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionHeaderPublic("Row ${ref.row + 1}, key ${ref.col + 1}")

            SheetField(
                label = "Label",
                value = key.label,
                supporting = "What is drawn on the key",
            ) { onChange(key.copy(label = it)) }

            SheetField(
                label = "Output",
                value = key.output.orEmpty(),
                supporting = "Blank — types the label",
            ) { onChange(key.copy(output = it.ifBlank { null })) }

            SheetField(
                label = "Shift label",
                value = key.shiftLabel.orEmpty(),
                supporting = "Blank — shift types the uppercase of the label",
            ) { onChange(key.copy(shiftLabel = it.ifBlank { null })) }

            val option = KeyActionCatalog.firstOrNull { it.matches(key.action) }
            NavRow(
                title = "Action",
                subtitle = option?.detail,
                value = option?.title ?: "Unknown",
            ) { pickingAction = true }

            // Broadcast keys carry a free-form action string the automation app
            // listens for; every other action is self-contained.
            (key.action as? KeyAction.Broadcast)?.let { broadcast ->
                SheetField(
                    label = "Broadcast action",
                    value = broadcast.action,
                    supporting = "The intent action an automation app listens for, " +
                        "e.g. com.example.MACRO. Left blank, the key does nothing.",
                ) { onChange(key.copy(action = KeyAction.Broadcast(it.trim()))) }
            }

            // Braille dot keys carry which of the six dots this key is.
            (key.action as? KeyAction.BrailleDot)?.let { brailleDot ->
                SheetField(
                    label = "Dot number",
                    value = brailleDot.dot.toString(),
                    supporting = "1–6: dots 1-2-3 down the left column, 4-5-6 down the right",
                ) { text ->
                    text.trim().toIntOrNull()?.takeIf { it in 1..6 }?.let {
                        onChange(key.copy(action = KeyAction.BrailleDot(it)))
                    }
                }
            }

            KeyWidthRow(
                width = key.width,
                gridWeight = gridWeight,
                otherWidthsInRow = otherWidthsInRow,
            ) { onChange(key.copy(width = it)) }

            if (key.action == KeyAction.Text) {
                SheetField(
                    label = "Icon",
                    value = key.icon.orEmpty(),
                    supporting = iconFieldSupport(key.icon),
                ) { onChange(key.copy(icon = it.ifBlank { null })) }

                SheetField(
                    label = "Icon hint",
                    value = key.iconHint.orEmpty(),
                    supporting = iconFieldSupport(key.iconHint),
                ) { onChange(key.copy(iconHint = it.ifBlank { null })) }

                SheetField(
                    label = "Long-press alternates",
                    value = alternates,
                    supporting = "Separate with spaces, so an alternate cannot itself be a " +
                        "space. Multi-character entries like .com are fine. The first is " +
                        "also printed in the key's corner.",
                ) { text ->
                    alternates = text
                    onChange(key.copy(longPress = parseAlternates(text)))
                }
                AlternatePreview(parseAlternates(alternates))

                RoleRow(key.role) { onChange(key.copy(role = it)) }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Delete key")
                }
                Spacer(Modifier.weight(1f))
                IconButton(enabled = ref.col > 0, onClick = { onMove(-1) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "Move left",
                    )
                }
                IconButton(enabled = ref.col < rowSize - 1, onClick = { onMove(+1) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Move right",
                    )
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate key")
                }
            }
        }
    }

    if (pickingAction) {
        KeyActionPickerDialog(
            current = key.action,
            onPick = {
                onChange(key.copy(action = it))
                pickingAction = false
            },
            onDismiss = { pickingAction = false },
        )
    }
}

/**
 * Space-separated, exactly as the symbol-set editor parses its characters, so a
 * user meets the convention once. A chip-per-entry editor was the alternative
 * and fails on ".com" and "https://" — multi-character alternates the built-ins
 * already ship.
 */
private fun parseAlternates(text: String): List<String> =
    text.split(Regex("\\s+")).filter { it.isNotEmpty() }

/** Inline validity feedback for the icon / icon-hint name fields. */
private fun iconFieldSupport(name: String?): String = when {
    name.isNullOrBlank() ->
        "Name of a built-in icon (search, mic, arrow_up, emoji, …); blank draws the label"
    KeyIcons.byName(name) != null -> "Shows the \"$name\" icon"
    else -> "No icon named \"$name\" — the label is drawn instead"
}

/** The first alternate is also the corner hint, and a flat string cannot say so. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlternatePreview(alternates: List<String>) {
    if (alternates.isEmpty()) return
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        alternates.forEachIndexed { index, alternate ->
            AssistChip(
                onClick = {},
                label = { Text(alternate) },
                leadingIcon = if (index == 0) {
                    { Text("hint", style = MaterialTheme.typography.labelSmall) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun SheetField(
    label: String,
    value: String,
    supporting: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(supporting) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Which slot this key fills for field adaptation, or none. */
@Composable
private fun RoleRow(role: KeyRole?, onChange: (KeyRole?) -> Unit) {
    ChoiceSetting(
        title = "Field adaptation",
        subtitle = "Email and web fields swap these two slots for @ and /",
        options = listOf(
            null to "None",
            KeyRole.Comma to "Comma slot",
            KeyRole.Period to "Period slot",
        ),
        selected = role,
        onChange = onChange,
    )
}

/**
 * Per-row height control for the layout editor: a multiplier on the standard
 * key height for this one row. 1.00 is the default (and collapses the stored
 * list back to nothing). Mirrors [KeyWidthRow]'s quarter-step slider + presets.
 */
@Composable
private fun RowHeightRow(
    height: Float,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("Row height  ×%.2f".format(height), style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = height.coerceIn(0.5f, 2f),
            onValueChange = { onChange((it * 4f).roundToInt() / 4f) },
            valueRange = 0.5f..2f,
            steps = 5,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (preset in listOf(0.75f, 1f, 1.25f, 1.5f, 2f)) {
                FilterChip(
                    selected = kotlin.math.abs(height - preset) < 0.01f,
                    onClick = { onChange(preset) },
                    label = { Text("×%.2f".format(preset).trimEnd('0').trimEnd('.')) },
                )
            }
        }
    }
}

@Composable
private fun KeyWidthRow(
    width: Float,
    gridWeight: Float,
    otherWidthsInRow: Float,
    onChange: (Float) -> Unit,
) {
    val remaining = gridWeight - otherWidthsInRow
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("Width  %.2f".format(width), style = MaterialTheme.typography.bodyLarge)
        // Quarter steps. Every built-in width (1, 1.2, 1.3, 1.5, 4) lands on or
        // beside a step, and a free slider would write 1.0374 into a file people
        // are invited to hand-edit.
        Slider(
            value = width.coerceIn(0.5f, 5f),
            onValueChange = { onChange((it * 4f).roundToInt() / 4f) },
            valueRange = 0.5f..5f,
            steps = 17,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (preset in listOf(1f, 1.25f, 1.5f, 2f, 4f)) {
                FilterChip(
                    selected = kotlin.math.abs(width - preset) < 0.01f,
                    onClick = { onChange(preset) },
                    label = { Text("%.2f".format(preset).trimEnd('0').trimEnd('.')) },
                )
            }
        }
        // The one-tap fix for a row left 0.75 short after an edit: hand this key
        // whatever row 1's width is not already spoken for.
        if (remaining >= 0.5f && kotlin.math.abs(remaining - width) > 0.01f) {
            OutlinedButton(
                onClick = { onChange((remaining * 4f).roundToInt() / 4f) },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Fill the row (%.2f)".format(remaining)) }
        }
    }
}

@Composable
private fun KeyActionPickerDialog(
    current: KeyAction,
    onPick: (KeyAction) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What this key does") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                var lastGroup: String? = null
                for (option in KeyActionCatalog) {
                    if (option.group != lastGroup) {
                        SectionHeaderPublic(option.group)
                        lastGroup = option.group
                    }
                    WmRow(
                        title = option.title,
                        subtitle = option.detail,
                        leading = {
                            RadioButton(
                                selected = option.matches(current),
                                onClick = { onPick(option.build()) },
                            )
                        },
                        onClick = { onPick(option.build()) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// ---------------------------------------------------------------------------
// Raw JSON
// ---------------------------------------------------------------------------

/**
 * The escape hatch: the layout as text, for pasting one in or fixing something
 * the grid editor has no control for.
 *
 * Draft plus an explicit Apply, unlike the grid editor's auto-save — half-typed
 * JSON is not a layout, so saving as you go is not merely undesirable, it is
 * impossible. Applying runs the same repair the import path does, and says what
 * it changed rather than rewriting the text silently.
 */
@Composable
internal fun KeyLayoutJsonScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    layoutId: String,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val layout = resolveLayouts(settings.customLayouts).firstOrNull { it.id == layoutId }
    if (layout == null) {
        Text(
            "This layout no longer exists.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    var text by rememberSaveable(layoutId) { mutableStateOf(LayoutCodec.encode(layout)) }
    var error by remember { mutableStateOf<String?>(null) }
    var repairs by remember { mutableStateOf<List<String>>(emptyList()) }

    CaptionText(
        "The layout as it is stored. Editing here is the way to reach anything the " +
            "grid editor has no control for. Re-opening this screen reformats what you " +
            "wrote — values that match the default are left out, and spacing is not kept.",
    )

    OutlinedTextField(
        value = text,
        onValueChange = { text = it; error = null },
        label = { Text("Layout JSON") },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = rememberJsonSyntaxHighlighter(),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )

    if (repairs.isNotEmpty()) {
        SettingsGroup("Applied, with changes") {
            for (line in repairs) {
                item {
                    WmRow(
                        title = line,
                    )
                }
            }
        }
    }

    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Spacer(Modifier.weight(1f))
        Button(
            enabled = text.isNotBlank(),
            onClick = {
                val parsed = LayoutCodec.decode(text)
                if (parsed == null) {
                    error = "That is not valid layout JSON."
                    return@Button
                }
                // The id in the text is ignored: this screen edits one layout,
                // and honouring a pasted id would silently overwrite a different
                // one — or create a second layout the user never asked for.
                val repaired = parsed.copy(id = layoutId).repair()
                repairs = repaired.repairs
                scope.launch {
                    repository.upsertCustomLayout(repaired.spec)
                    if (repaired.repairs.isEmpty()) onDone()
                }
            },
        ) { Text("Apply") }
    }
}
