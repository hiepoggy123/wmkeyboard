package com.wasimaster.wmkeyboard.app

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
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
import com.wasimaster.wmkeyboard.core.layout.tabletGridWidth
import com.wasimaster.wmkeyboard.core.settings.DeviceForm
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.addons.AddonStore
import com.wasimaster.wmkeyboard.core.addons.AddonType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import com.wasimaster.wmkeyboard.core.layout.ConvertedLayout
import com.wasimaster.wmkeyboard.core.layout.ForeignLayouts
import com.wasimaster.wmkeyboard.core.layout.ForeignSource
import com.wasimaster.wmkeyboard.core.layout.ImportedLayout
import com.wasimaster.wmkeyboard.core.layout.LayoutFile
import com.wasimaster.wmkeyboard.core.layout.LayoutMessage
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
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
import com.wasimaster.wmkeyboard.core.layout.fallbackLabel
import com.wasimaster.wmkeyboard.core.layout.resolveLayout
import com.wasimaster.wmkeyboard.core.layout.resolveLayouts
import com.wasimaster.wmkeyboard.core.layout.sidePadFor
import com.wasimaster.wmkeyboard.core.layout.validateLayout
import com.wasimaster.wmkeyboard.core.script.ComposerType
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
/** [ReturnAnchor] key for the key-layouts list. */
private const val KEYMAPS_ANCHOR = "keymaps"

/**
 * What the "import from another keyboard" picker offers.
 *
 * Wider than the native layout picker's list because these files are somebody
 * else's: HeliBoard writes `.txt` and `.json`, and providers report both as
 * anything from `text/plain` to `application/octet-stream`. Nothing is decided
 * from the MIME type — the converter reads the file and says whether it is one.
 */
private val FOREIGN_LAYOUT_MIME_TYPES =
    arrayOf("application/json", "text/plain", "application/octet-stream")

/** Largest foreign layout worth reading. The whole file is decoded as one string. */
private const val MAX_FOREIGN_LAYOUT_BYTES = 4 * 1024 * 1024

/**
 * Reads at most [max] bytes. A declared length is never trusted; the count comes
 * from what was actually read, the same way the archive importers do it.
 */
private fun java.io.InputStream.readBytes(max: Int): ByteArray {
    val out = ByteArray(max)
    var filled = 0
    val buffer = ByteArray(8 * 1024)
    while (filled < max) {
        val n = read(buffer, 0, minOf(buffer.size, max - filled))
        if (n <= 0) break
        System.arraycopy(buffer, 0, out, filled, n)
        filled += n
    }
    return out.copyOf(filled)
}

/**
 * Picks the language a converted layout types in.
 *
 * Its own dialog rather than a list inside the confirmation: the registry holds
 * over three hundred languages, so this needs a search field, and the search
 * field needs the room. Seeded from the character-set guess, which the caller
 * has already put in front of the user as a guess.
 */
/** Renames a layout. Blank is rejected rather than saved as an unnamed row. */
@Composable
private fun LayoutNameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    val trimmed = text.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layout_editor_name_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.layout_editor_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = trimmed.isNotEmpty(),
                onClick = { onConfirm(trimmed) },
            ) { Text(stringResource(CommonR.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

/**
 * A composer's name for the override row. Deliberately descriptive rather than
 * the enum name: "PINYIN" says nothing to someone choosing between a phonetic
 * and a direct grid.
 */
@Composable
private fun composerLabel(type: ComposerType): String = stringResource(
    when (type) {
        ComposerType.NONE -> R.string.layout_editor_composer_none
        ComposerType.DEAD_KEY -> R.string.layout_editor_composer_dead_key
        ComposerType.TRANSLITERATE -> R.string.layout_editor_composer_transliterate
        ComposerType.INDIC_CLUSTER -> R.string.layout_editor_composer_indic
        ComposerType.HANGUL -> R.string.layout_editor_composer_hangul
        ComposerType.TELEX -> R.string.layout_editor_composer_telex
        ComposerType.VNI -> R.string.layout_editor_composer_vni
        ComposerType.ROMAJI -> R.string.layout_editor_composer_romaji
        ComposerType.PINYIN -> R.string.layout_editor_composer_pinyin
        ComposerType.STROKE -> R.string.layout_editor_composer_stroke
        ComposerType.T9_PINYIN -> R.string.layout_editor_composer_t9_pinyin
        ComposerType.ZHUYIN -> R.string.layout_editor_composer_zhuyin
        ComposerType.CANGJIE -> R.string.layout_editor_composer_cangjie
        ComposerType.CANGJIE_QUICK -> R.string.layout_editor_composer_cangjie_quick
        ComposerType.JYUTPING -> R.string.layout_editor_composer_jyutping
    },
)

@Composable
private fun ForeignLanguageDialog(
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Remembered on the query: this recomposes on every letter typed, and
    // re-running the filter over the whole registry per keystroke is what makes
    // a search field feel heavy.
    val results = remember(query) { searchLanguages(query.trim().lowercase()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layout_editor_foreign_language_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.layout_editor_foreign_language_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(results, key = { it.id }) { language ->
                        WmRow(
                            title = language.displayName,
                            trailing = if (language.id == selected) {
                                { Icon(Icons.Outlined.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = { onPick(language.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

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

    // A layout converted from another keyboard, waiting on a language. Neither
    // FlorisBoard nor HeliBoard states one in a layout file, and a layout stored
    // without one is silently read back as English — with an English dictionary
    // and Latin shift behaviour on, say, a Georgian grid.
    var confirmForeign by remember { mutableStateOf<ConvertedLayout?>(null) }
    var foreignLangId by remember { mutableStateOf("") }
    var pickingLanguage by remember { mutableStateOf(false) }

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
            message = if (ok) {
                context.getString(R.string.layout_editor_export_done_message, layout.name)
            } else {
                context.getString(R.string.layout_editor_export_error)
            }
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
                message = context.getString(R.string.layout_editor_import_wrong_file_error)
                return@launch
            }
            // Read first, ask, then write. Importing a layout is not something
            // to discover you have done — and it never activates it either.
            confirmImport = parsed
        }
    }

    val foreignLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = WMFileTypes.displayName(context, uri)
            val converted = withContext(Dispatchers.IO) {
                runCatching {
                    // Capped, unlike the native import: this file was written by
                    // another app and picked by extension, so it can be anything
                    // the picker let through. The cap matches what an add-on
                    // repository will download for a layout, and no real grid is
                    // within two orders of magnitude of it.
                    val bytes = context.contentResolver.requireInputStream(uri).use { input ->
                        input.readBytes(MAX_FOREIGN_LAYOUT_BYTES + 1)
                    }
                    if (bytes.size > MAX_FOREIGN_LAYOUT_BYTES) {
                        null
                    } else {
                        ForeignLayouts.convert(bytes.decodeToString(), name)
                    }
                }.getOrNull()
            }
            if (converted == null) {
                message = context.getString(R.string.layout_editor_foreign_wrong_file_error)
                return@launch
            }
            foreignLangId = converted.guessedLangId
            confirmForeign = converted
        }
    }

    // The layout the editor was last opened on. A grid takes a while to build,
    // and coming out of one to hunt for it again in a list of every layout you
    // have made and every shipped one you have on is the sort of small tax that
    // makes an editor tiring to use.
    val returnTo = remember { ReturnAnchor.take(KEYMAPS_ANCHOR) }
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

    CaptionText(stringResource(R.string.layout_editor_gallery_caption))

    /**
     * Copies a layout and opens the copy.
     *
     * Deliberately does not activate it, unlike the themes gallery, which
     * applies a duplicate before navigating. A layout owns delete, enter and
     * space, and a half-built copy becoming the live keyboard mid-edit is how
     * someone ends up unable to type well enough to undo it. Custom layouts go
     * live only from their toggle under Languages, and only once they validate.
     */
    /** Opens the editor, and remembers the row to come back to. */
    fun openEditor(id: String) {
        ReturnAnchor.arm(KEYMAPS_ANCHOR, id)
        onNavigate("keymap_edit/$id")
    }

    fun duplicateAndEdit(base: LayoutSpec) {
        scope.launch {
            val id = "custom_${System.currentTimeMillis()}"
            val name = context.getString(R.string.layout_editor_duplicate_name_format, base.name)
            repository.upsertCustomLayout(base.copy(id = id, name = name))
            openEditor(id)
        }
    }

    /**
     * A new layout from a four-row skeleton rather than from a copy.
     *
     * The empty state said "Copy a layout below", which is fine advice and was
     * also the only route: building something that is not a rearranged QWERTY
     * meant duplicating one and deleting thirty keys first. The skeleton is the
     * default letters grid's shape with blank keys, so the row structure and
     * the bottom row are already right.
     */
    fun createBlankAndEdit() {
        scope.launch {
            val id = "custom_${System.currentTimeMillis()}"
            val name = context.getString(R.string.layout_editor_new_layout_name)
            val letters = BuiltInLayouts.default.compile(LayoutLayer.LETTERS)
            val blankRows = letters.rows.map { row ->
                row.map { key ->
                    // Only the plain character keys are emptied. Enter, shift,
                    // space and delete are what make the grid usable while it
                    // is being filled in, and nobody wants to re-add them.
                    if (key.action == KeyAction.Text) key.copy(label = "", output = "") else key
                }
            }
            repository.upsertCustomLayout(
                LayoutSpec(
                    id = id,
                    name = name,
                    layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(rows = blankRows)),
                ),
            )
            openEditor(id)
        }
    }

    SettingsGroup(stringResource(R.string.layout_editor_your_layouts_title)) {
        item {
            WmRow(
                title = stringResource(R.string.layout_editor_new_layout_title),
                subtitle = stringResource(R.string.layout_editor_new_layout_subtitle),
                leading = { Icon(Icons.Outlined.Add, contentDescription = null) },
                onClick = { createBlankAndEdit() },
            )
        }
        // Every grid the user made, on or off. Filtering to the enabled ones
        // made the two buttons on this very screen — Duplicate and Import —
        // produce a layout that then vanished from it: neither turns its result
        // on, so a copy opened the editor, and coming back said "No layouts of
        // your own yet". Whether a layout is on is a word in its subtitle, not
        // a reason to hide the thing you just made.
        //
        // Addon layouts are left out entirely. This group is "grids you made",
        // and an installed one is neither made here nor managed here — it is
        // switched on under Languages and removed from Addons, and editing it
        // would only produce changes the next update silently discards.
        val customs = layouts.filter {
            it.id in customIds &&
                it.id !in shippedIds &&
                it.id !in addonLayoutIds
        }
        if (customs.isEmpty()) {
            item {
                WmRow(
                    title = stringResource(R.string.layout_editor_empty_title),
                    subtitle = stringResource(R.string.layout_editor_empty_subtitle),
                )
            }
        }
        for (layout in customs) {
            item {
                ScrollAnchor(layout.id == returnTo) {
                    LayoutRow(
                        layout = layout,
                        enabled = layout.id in settings.enabledLayoutIds,
                        onEdit = { openEditor(layout.id) },
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
    }

    SettingsGroup {
        item {
            WmRow(
                title = stringResource(R.string.layout_editor_import_title),
                subtitle = stringResource(R.string.layout_editor_import_subtitle),
                leading = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
                onClick = {
                    importLauncher.launch(LayoutFile.IMPORT_MIME_TYPES)
                },
            )
        }
        item {
            WmRow(
                title = stringResource(R.string.layout_editor_foreign_title),
                subtitle = stringResource(R.string.layout_editor_foreign_subtitle),
                leading = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                onClick = { foreignLauncher.launch(FOREIGN_LAYOUT_MIME_TYPES) },
            )
        }
    }

    val builtIns = layouts.filter {
        it.id in shippedIds && it.id in settings.enabledLayoutIds
    }
    if (builtIns.isNotEmpty()) {
        SettingsGroup(stringResource(R.string.layout_editor_built_in_title)) {
            for (layout in builtIns) {
                item {
                    ScrollAnchor(layout.id == returnTo) {
                        LayoutRow(
                            layout = layout,
                            enabled = layout.id in settings.enabledLayoutIds,
                            onEdit = { openEditor(layout.id) },
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
    }

    confirmImport?.let { imported ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = {
                Text(
                    stringResource(
                        R.string.layout_editor_import_confirm_title,
                        imported.layout.name,
                    ),
                )
            },
            text = {
                Column {
                    Text(stringResource(R.string.layout_editor_import_confirm_body))
                    if (imported.repairNotes.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.layout_editor_import_changes_title),
                            fontWeight = FontWeight.Medium,
                        )
                        for (note in imported.repairNotes) {
                            Text(
                                stringResource(
                                    R.string.layout_editor_repair_note,
                                    note.format(context.resources),
                                ),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = "custom_${System.currentTimeMillis()}"
                    val name = imported.layout.name
                    scope.launch {
                        repository.upsertCustomLayout(imported.layout.copy(id = id))
                        message =
                            context.getString(R.string.layout_editor_import_done_message, name)
                    }
                    confirmImport = null
                }) { Text(stringResource(CommonR.string.common_import)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = null }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }

    confirmForeign?.let { converted ->
        AlertDialog(
            onDismissRequest = { confirmForeign = null },
            title = {
                Text(stringResource(R.string.layout_editor_foreign_confirm_title, converted.layout.name))
            },
            text = {
                Column {
                    Text(
                        stringResource(
                            when (converted.source) {
                                ForeignSource.FLORIS_JSON -> R.string.layout_editor_foreign_from_json
                                ForeignSource.HELIBOARD_TEXT -> R.string.layout_editor_foreign_from_text
                            },
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    // The language is a step rather than a guess applied
                    // silently: it decides the dictionary, the autocorrect, the
                    // script rules, dictation and how shift behaves, and no
                    // foreign layout file states one.
                    WmRow(
                        title = stringResource(R.string.layout_editor_foreign_language_title),
                        subtitle = LanguageRegistry.byId(foreignLangId).displayName,
                        onClick = { pickingLanguage = true },
                    )
                    if (converted.notes.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.layout_editor_import_changes_title),
                            fontWeight = FontWeight.Medium,
                        )
                        for (note in converted.notes) {
                            Text(
                                stringResource(
                                    R.string.layout_editor_repair_note,
                                    note.format(context.resources),
                                ),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = "custom_${System.currentTimeMillis()}"
                    val name = converted.layout.name
                    val langId = foreignLangId
                    scope.launch {
                        // withLanguage is the only supported way out of a
                        // conversion, for the reason its own comment gives.
                        repository.upsertCustomLayout(converted.withLanguage(langId).copy(id = id))
                        message =
                            context.getString(R.string.layout_editor_import_done_message, name)
                    }
                    confirmForeign = null
                }) { Text(stringResource(CommonR.string.common_import)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForeign = null }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }

    if (pickingLanguage) {
        ForeignLanguageDialog(
            selected = foreignLangId,
            onPick = {
                foreignLangId = it
                pickingLanguage = false
            },
            onDismiss = { pickingLanguage = false },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text(stringResource(CommonR.string.common_ok))
                }
            },
        )
    }

    confirmDelete?.let { layout ->
        val reset = layout.id in shippedIds
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = {
                Text(
                    if (reset) {
                        stringResource(R.string.layout_editor_reset_confirm_title, layout.name)
                    } else {
                        stringResource(R.string.layout_editor_delete_confirm_title, layout.name)
                    },
                )
            },
            text = {
                Text(
                    if (reset) {
                        stringResource(R.string.layout_editor_reset_confirm_body)
                    } else {
                        stringResource(R.string.layout_editor_delete_confirm_body)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteCustomLayout(layout.id) }
                    confirmDelete = null
                }) {
                    Text(
                        stringResource(
                            if (reset) CommonR.string.common_reset else CommonR.string.common_delete,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
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
    val resources = LocalContext.current.resources
    WmRow(
        title = layout.name,
        subtitle = layoutSummary(resources, layout, enabled),
        trailing = {
            Row {
                IconButton(onClick = onExport) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription =
                            stringResource(R.string.layout_editor_export_desc, layout.name),
                    )
                }
                IconButton(onClick = onDuplicate) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription =
                            stringResource(R.string.layout_editor_duplicate_desc, layout.name),
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            if (deleteIsReset) Icons.Outlined.Refresh else Icons.Outlined.Delete,
                            contentDescription = if (deleteIsReset) {
                                stringResource(R.string.layout_editor_reset_desc, layout.name)
                            } else {
                                stringResource(R.string.layout_editor_delete_desc, layout.name)
                            },
                        )
                    }
                }
            }
        },
        onClick = onEdit,
    )
}

/**
 * One line describing a layout: its language, its shape, and whether it is on.
 *
 * Takes [resources] rather than reading a string itself: the parts are counted
 * words, so each one needs the plural rule of the language on the device now.
 */
internal fun layoutSummary(resources: Resources, layout: LayoutSpec, enabled: Boolean): String {
    val letters = layout.compile(LayoutLayer.LETTERS).rows
    val keyTotal = letters.sumOf { it.size }
    val extras = layout.layers.keys.count { it != LayoutLayer.LETTERS.key }
    val parts = mutableListOf(
        resources.getString(if (enabled) CommonR.string.common_on else CommonR.string.common_off),
        baseModeTitle(layout),
        resources.getQuantityString(
            R.plurals.layout_editor_row_count,
            letters.size,
            letters.size,
        ),
        resources.getQuantityString(R.plurals.layout_editor_key_count, keyTotal, keyTotal),
    )
    if (extras > 0) {
        parts += resources.getQuantityString(
            R.plurals.layout_editor_custom_layer_count,
            extras,
            extras,
        )
    }
    return parts.joinToString(" · ")
}

/**
 * The catalog's name for a mode, so the subtitle tracks a renamed catalog entry
 * rather than duplicating it.
 */
internal fun baseModeTitle(layout: LayoutSpec): String =
    "${layout.language().displayName} · ${layout.name}"

/**
 * The check that has to pass before a layout may be switched on, as a function
 * the toggles call.
 *
 * The editor has always told the user "You must fix this before you turn this
 * layout on" under every blocking finding, and nothing anywhere enforced it —
 * [canBeEnabled] existed and was called only from tests, so a layout with no
 * delete key, no way back off its symbols layer, or keys from a newer build
 * turned on with no warning at all. Repairing at draw time keeps such a layout
 * *typeable*, but that is a backstop, not permission: the grid the user then
 * types on is not the one they built, and nothing said so.
 *
 * Returns a gate: call it with the layout id and what to do if it passes. A
 * blocking layout opens a dialog naming every reason instead, which is the same
 * list the editor shows, so the two can never disagree.
 */
@Composable
internal fun rememberLayoutEnableGate(
    settings: KeyboardSettings,
): (String, () -> Unit) -> Unit {
    val resources = LocalContext.current.resources
    var blocked by remember { mutableStateOf<Pair<String, List<LayoutMessage>>?>(null) }

    blocked?.let { (name, reasons) ->
        AlertDialog(
            onDismissRequest = { blocked = null },
            title = { Text(stringResource(R.string.layout_editor_cannot_enable_title, name)) },
            text = {
                Column {
                    Text(stringResource(R.string.layout_editor_cannot_enable_body))
                    Spacer(Modifier.height(8.dp))
                    for (reason in reasons) {
                        Text(
                            stringResource(
                                R.string.layout_editor_repair_note,
                                reason.format(resources),
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { blocked = null }) {
                    Text(stringResource(CommonR.string.common_ok))
                }
            },
        )
    }

    return { layoutId, enable ->
        val spec = resolveLayout(settings.customLayouts, layoutId)
        val reasons = validateLayout(spec)
            .filter { it.severity == LayoutSeverity.BLOCKING }
            .map { it.text }
        if (reasons.isEmpty()) enable() else blocked = spec.name to reasons
    }
}

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
    val context = LocalContext.current
    val layout = resolveLayouts(settings.customLayouts).firstOrNull { it.id == layoutId }
    if (layout == null) {
        Text(
            stringResource(R.string.layout_editor_missing_layout_message),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    var layer by rememberSaveable(layoutId) { mutableStateOf(LayoutLayer.LETTERS) }
    var selection by remember(layoutId, layer) { mutableStateOf<KeyRef?>(null) }
    var showShift by rememberSaveable(layoutId) { mutableStateOf(false) }
    // Draw the preview at the user's real key height instead of the clamped
    // one. Off by default because a tall setting pushes the grid off screen.
    var actualSize by rememberSaveable(layoutId) { mutableStateOf(false) }
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

    /**
     * The layer an edit starts from: this layout's own, the shipped one it
     * inherits, or an empty grid.
     *
     * The empty case is Fn, the one layer nothing ships. `compile`'s fallback
     * chain ends at the default *letters* grid, so without this every edit path
     * on the Fn tab — add a row, reorder, touch a key — would author Fn as a
     * second copy of the alphabet.
     */
    fun baseLayerOf(spec: LayoutSpec): LayerSpec {
        spec.layer(layer)?.let { return it }
        if (BuiltInLayouts.default.layer(layer) == null) return LayerSpec(rows = emptyList())
        val compiled = spec.compile(layer)
        return LayerSpec(rows = compiled.rows, rowHeights = compiled.rowHeights)
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
        edit { spec -> withLayerRows(spec, transform(baseLayerOf(spec).rows)) }
    }

    // Whole-layer edit, so a structural change to the rows can keep the parallel
    // per-row heights aligned. Authoring an inherited layer copies the built-in's
    // compiled grid (heights and all) first.
    fun editLayer(transform: (LayerSpec) -> LayerSpec) {
        edit { spec -> spec.copy(layers = spec.layers + (layer.key to transform(baseLayerOf(spec)))) }
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

    // Fn is the one layer nothing ships, so `compile` runs off the end of its
    // fallback chain and hands back the *letters* grid. Drawn, that stand-in
    // invited a tap, and a tap on an unauthored layer authors it from whatever
    // the grid is showing — so one keystroke on the empty Fn tab wrote a second
    // copy of QWERTY into the Fn layer and took the template row away. An empty
    // grid here leaves "Add an Fn layer" as the only way in, which is what the
    // caption below already says it is.
    val inheritable = layout.layer(layer) != null || BuiltInLayouts.default.layer(layer) != null
    val compiled = if (inheritable) {
        layout.compile(layer)
    } else {
        KeyboardLayout(name = "$layoutId/${layer.key}", rows = emptyList())
    }
    val rows = compiled.rows
    val rowHeights = compiled.rowHeights
    val selectedKey = selection?.let { rows.getOrNull(it.row)?.getOrNull(it.col) }
    // Whether the tablet expansion would actually do anything here, so its
    // toggle can say so. Asked of the letters layer on the widest form — the
    // gate is a property of the layout, not of whichever layer is on screen —
    // and only to word a subtitle, never to gate the toggle: a layout that the
    // transform declines today should still keep the author's answer on file.
    val tabletExpandApplies = remember(layout) {
        tabletGridWidth(layout.compile(LayoutLayer.LETTERS), DeviceForm.LARGE_TABLET) != null
    }

    SectionHeaderPublic(layout.name)

    // A layout's identity: its name, the language it counts as, and the
    // composer it types through. All three were reachable only by hand-editing
    // the JSON — the editor printed the name as a header and nothing else, so
    // every copy of a copy read "X copy copy", and a duplicate of QWERTY could
    // never be re-languaged even though langId decides its dictionary,
    // autocorrect, shift behaviour and dictation.
    var renaming by remember(layoutId) { mutableStateOf(false) }
    var languagePickerOpen by remember(layoutId) { mutableStateOf(false) }
    SettingsGroup {
        item {
            WmRow(
                title = stringResource(R.string.layout_editor_name_title),
                subtitle = layout.name,
                onClick = { renaming = true },
            )
        }
        item {
            WmRow(
                title = stringResource(R.string.layout_editor_language_title),
                subtitle = layout.langId.takeIf { it.isNotBlank() }
                    ?.let { LanguageRegistry.byId(it).displayName }
                    ?: stringResource(R.string.layout_editor_language_unset),
                onClick = { languagePickerOpen = true },
            )
        }
        item {
            // Null is "whatever this script normally uses", which is the right
            // answer for almost every layout; the override exists because a
            // phonetic and a direct grid for the same language differ only here.
            val inheritLabel = stringResource(R.string.layout_editor_composer_inherit)
            ChoiceSetting(
                title = R.string.layout_editor_composer_title,
                subtitle = stringResource(R.string.layout_editor_composer_subtitle),
                options = listOf<Pair<ComposerType?, String>>(null to inheritLabel) +
                    ComposerType.entries.map { it to composerLabel(it) },
                selected = layout.composer,
                info = stringResource(R.string.layout_editor_composer_info),
            ) { chosen -> edit { it.copy(composer = chosen) } }
        }
    }

    if (renaming) {
        LayoutNameDialog(
            initial = layout.name,
            onDismiss = { renaming = false },
            onConfirm = { typed ->
                renaming = false
                edit { it.copy(name = typed) }
            },
        )
    }

    if (languagePickerOpen) {
        // The same picker the foreign-layout import uses: it searches the whole
        // registry, which is what re-languaging a duplicate needs.
        ForeignLanguageDialog(
            selected = layout.langId,
            onDismiss = { languagePickerOpen = false },
            onPick = { id ->
                languagePickerOpen = false
                edit { it.copy(langId = id) }
            },
        )
    }

    LayerChips(layout, layer) { layer = it; selection = null }

    if (layout.layer(layer) == null) {
        if (layer == LayoutLayer.FN) {
            // Nothing ships an Fn layer, so there is no built-in to inherit and
            // the grid above is a stand-in. Offer the template instead.
            CaptionText(stringResource(R.string.layout_editor_fn_missing_caption))
            SettingsGroup {
                item {
                    WmRow(
                        title = stringResource(R.string.layout_editor_add_fn_title),
                        subtitle = stringResource(R.string.layout_editor_add_fn_subtitle),
                        leading = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        onClick = {
                            edit { it.copy(layers = it.layers + (layer.key to BuiltInLayouts.FN_DEFAULT)) }
                        },
                    )
                }
            }
        } else {
            CaptionText(
                stringResource(
                    R.string.layout_editor_inherited_layer_caption,
                    stringResource(layerTitleRes(layer)),
                ),
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
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Undo,
                contentDescription = stringResource(R.string.layout_editor_undo_desc),
            )
        }
        IconButton(
            enabled = redo.isNotEmpty(),
            onClick = {
                val next = redo.last()
                redo = redo.dropLast(1)
                undo = undo + layout
                stepPushed = false
                save(next)
            },
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Redo,
                contentDescription = stringResource(R.string.layout_editor_redo_desc),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.layout_editor_autosave_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    EditorGrid(
        layout = compiled,
        settings = settings,
        selection = selection,
        showShift = showShift,
        actualSize = actualSize,
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
                title = stringResource(R.string.layout_editor_add_row_title),
                subtitle = stringResource(R.string.layout_editor_add_row_subtitle),
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
                title = stringResource(R.string.layout_editor_reorder_rows_title),
                dialogTitle = stringResource(R.string.layout_editor_row_order_dialog_title),
                items = rows.indices.toList(),
                label = { i -> rowReorderLabel(context, i + 1, rows[i].size) },
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
                        title = stringResource(
                            R.string.layout_editor_reorder_keys_title,
                            ref.row + 1,
                        ),
                        dialogTitle = stringResource(R.string.layout_editor_key_order_dialog_title),
                        items = rows[ref.row],
                        label = { keyReorderLabel(context, it) },
                    ) { order ->
                        editRows { r -> r.mapIndexed { i, row -> if (i == ref.row) order else row } }
                        selection = null
                    }
                }
            }
        }
        item {
            ToggleSetting(
                R.string.layout_editor_show_shift_title,
                stringResource(R.string.layout_editor_show_shift_subtitle),
                showShift,
            ) { showShift = it }
        }
        item {
            ToggleSetting(
                R.string.layout_editor_actual_size_title,
                stringResource(R.string.layout_editor_actual_size_subtitle),
                actualSize,
                info = stringResource(R.string.layout_editor_actual_size_info),
            ) { actualSize = it }
        }
        item {
            // Layout-wide, like the JSON row below it, rather than layer-scoped
            // like everything above — and `edit`, not `editCoalesced`, because
            // one deliberate flip deserves one undo step.
            ToggleSetting(
                R.string.layout_editor_tablet_expand_title,
                stringResource(
                    if (tabletExpandApplies) {
                        R.string.layout_editor_tablet_expand_subtitle
                    } else {
                        R.string.layout_editor_tablet_expand_subtitle_na
                    },
                ),
                layout.tabletExpand,
            ) { on -> edit { it.copy(tabletExpand = on) } }
        }
        item {
            NavRow(
                R.string.layout_editor_json_title,
                subtitle = stringResource(R.string.layout_editor_json_subtitle),
            ) { onNavigate("keymap_json/$layoutId") }
        }
        if (layout.layer(layer) != null) {
            item {
                WmRow(
                    title = stringResource(R.string.layout_editor_reset_layer_title),
                    subtitle = stringResource(
                        R.string.layout_editor_reset_layer_subtitle,
                        stringResource(layerTitleRes(layer)),
                    ),
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
        SettingsGroup(stringResource(R.string.layout_editor_problems_title)) {
            for (finding in findings) {
                item {
                    WmRow(
                        title = finding.text.format(context.resources),
                        subtitle = if (finding.severity == LayoutSeverity.BLOCKING) {
                                stringResource(R.string.layout_editor_problem_blocking_subtitle)
                            } else {
                                stringResource(R.string.layout_editor_problem_warning_subtitle)
                            },
                    )
                }
            }
        }
    }

    // A layout that is already on is live while you edit it, so the standing
    // "this does not affect typing yet" line was a lie in exactly the case where
    // it mattered — the keyboard follows every keystroke made here, and only the
    // repair pass at the point of use keeps a half-built grid typeable.
    CaptionText(
        stringResource(
            if (layoutId in settings.enabledLayoutIds) {
                R.string.layout_editor_live_caption
            } else {
                R.string.layout_editor_not_live_caption
            },
        ),
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
                        baseLayerOf(spec).rows.mapIndexed { r, row ->
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

/** How a row reads in the reorder dialog: its number and how many keys it holds. */
private fun rowReorderLabel(context: Context, number: Int, keyCount: Int): String {
    val name = context.getString(R.string.layout_editor_row_number, number)
    val keys = context.resources.getQuantityString(
        R.plurals.layout_editor_key_count,
        keyCount,
        keyCount,
    )
    return "$name · $keys"
}

/**
 * How a key reads in the reorder dialog, where there is no grid to look at.
 *
 * Takes a [context] because the label lambda it feeds is a plain lambda, and the
 * name of an action is a resource now.
 */
private fun keyReorderLabel(context: Context, key: Key): String {
    val actionName = context.getString(
        KeyActionCatalog.firstOrNull { it.matches(key.action) }?.titleRes
            ?: R.string.layout_editor_key_fallback_label,
    )
    // An icon-drawn action reads as its name here too: the globe key's stored
    // label is 🌐, and a row of keys that says "🌐" identifies nothing.
    return if (key.label.isBlank() || actionIconName(key.action) != null) actionName else key.label
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
        Text(
            stringResource(R.string.layout_editor_row_number, rowIndex + 1),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.width(8.dp))
        // Only worth saying when it disagrees with the grid — the width the
        // keyboard measures every other row against. Printed on every row it
        // would be five numbers that are correct and identical almost always.
        //
        // Weighted, and wrapping, because it is a whole sentence sharing a row
        // with three buttons: at its natural width it pushed Delete row clean
        // off the screen and clipped Duplicate row — and a row wide enough to
        // warn about is precisely the row you want to delete.
        if (kotlin.math.abs(rowWidth - gridWeight) > 0.01f) {
            Text(
                stringResource(R.string.layout_editor_row_width_mismatch, rowWidth, gridWeight),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        IconButton(onClick = onAddKey) {
            Icon(
                Icons.Outlined.Add,
                contentDescription =
                    stringResource(R.string.layout_editor_add_key_desc, rowIndex + 1),
            )
        }
        IconButton(onClick = onDuplicateRow) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription =
                    stringResource(R.string.layout_editor_duplicate_row_desc, rowIndex + 1),
            )
        }
        IconButton(enabled = rowCount > 1, onClick = onDeleteRow) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription =
                    stringResource(R.string.layout_editor_delete_row_desc, rowIndex + 1),
            )
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
                label = { Text(stringResource(layerTitleRes(layer)), maxLines = 1) },
                leadingIcon = if (authored) {
                    {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription =
                                stringResource(R.string.layout_editor_customised_desc),
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

/**
 * The name of a layer, as a resource id.
 *
 * The name is resolved where it is drawn rather than here, so a sentence that
 * carries it never has to change the case of a translated word.
 */
@StringRes
internal fun layerTitleRes(layer: LayoutLayer): Int = when (layer) {
    LayoutLayer.LETTERS -> R.string.layout_editor_layer_letters
    LayoutLayer.SYMBOLS -> R.string.layout_editor_layer_symbols
    LayoutLayer.SYMBOLS_SHIFTED -> R.string.layout_editor_layer_symbols_2
    LayoutLayer.NUMBER -> R.string.layout_editor_layer_number
    LayoutLayer.PHONE -> R.string.layout_editor_layer_phone
    LayoutLayer.DATE -> R.string.layout_editor_layer_date
    LayoutLayer.TIME -> R.string.layout_editor_layer_time
    LayoutLayer.DATETIME -> R.string.layout_editor_layer_date_time
    LayoutLayer.FN -> R.string.layout_editor_layer_fn
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
    actualSize: Boolean,
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
                            stringResource(R.string.layout_editor_layer_empty_message),
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
                                    actualSize = actualSize,
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
    actualSize: Boolean,
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
    // The user's own key height keeps the preview honest, but a 100 dp setting
    // would put two rows on screen. Clamped by default and exact on request:
    // someone typing at 70 dp never otherwise saw their real proportions while
    // editing, which is most of what a preview is for.
    val height = if (actualSize) heightDp.dp else heightDp.dp.coerceIn(38.dp, 56.dp)
    // Read here rather than inside the ifBlank lambda below, which is not a
    // composable and so cannot reach a resource itself.
    val spaceLabel = stringResource(R.string.layout_editor_space_key_label)
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
            val cellIcon = KeyIcons.byName(key.icon) ?: KeyIcons.byName(actionIconName(key.action))
            if (cellIcon != null) {
                Icon(
                    cellIcon,
                    contentDescription = primary.ifBlank { key.icon },
                    tint = foreground,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = primary.ifBlank { actionGlyph(key.action, spaceLabel) },
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

/**
 * The [KeyIcons] name an action always draws with, whatever its stored label
 * says, or null for an action that draws its label.
 *
 * The keyboard itself ignores the label on these two and draws an icon (see
 * `KeyContent`), so the grid has to as well. The globe key is why: its label is
 * the emoji `🌐`, and drawing that verbatim gave the editor a colour glyph in
 * whichever house style the device's emoji font uses, beside a row of flat
 * monochrome keys, and it was never what the keyboard put on screen anyway.
 */
private fun actionIconName(action: KeyAction): String? = when (action) {
    KeyAction.LanguageSwitch -> "language"
    KeyAction.InputMethodPicker -> "keyboard"
    KeyAction.Emoji -> "emoji"
    else -> null
}

/**
 * What to draw for an action key whose label is blank, like a keypad spacebar.
 *
 * Only the handful the keyboard draws from an icon slot are answered here; every
 * other action defers to [KeyAction.fallbackLabel], which is what the keyboard
 * itself falls back to. The two used to disagree — this one ended in a catch-all
 * "·" — and a Tab or Ctrl key therefore looked present in the editor and came
 * out invisible on the keyboard.
 *
 * [spaceLabel] is the one glyph here that is a word, so the caller reads it and
 * hands it over.
 */
private fun actionGlyph(action: KeyAction, spaceLabel: String): String = when (action) {
    KeyAction.Space -> spaceLabel
    KeyAction.Enter -> "⏎"
    KeyAction.Delete -> "⌫"
    KeyAction.ForwardDelete -> "⌦"
    KeyAction.Shift -> "⇧"
    else -> action.fallbackLabel()
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
    @StringRes val titleRes: Int,
    @StringRes val groupRes: Int,
    @StringRes val detailRes: Int,
    val build: () -> KeyAction,
    val matches: (KeyAction) -> Boolean,
)

internal val KeyActionCatalog: List<KeyActionOption> = listOf(
    KeyActionOption(
        R.string.layout_editor_action_text_title,
        R.string.layout_editor_action_group_typing,
        R.string.layout_editor_action_text_detail,
        { KeyAction.Text }, { it == KeyAction.Text },
    ),
    KeyActionOption(
        R.string.layout_editor_action_shift_title,
        R.string.layout_editor_action_group_typing,
        R.string.layout_editor_action_shift_detail,
        { KeyAction.Shift }, { it == KeyAction.Shift },
    ),
    KeyActionOption(
        R.string.layout_editor_action_caps_lock_title,
        R.string.layout_editor_action_group_typing,
        R.string.layout_editor_action_caps_lock_detail,
        { KeyAction.CapsLock }, { it == KeyAction.CapsLock },
    ),
    KeyActionOption(
        R.string.layout_editor_action_delete_title,
        R.string.layout_editor_action_group_typing,
        R.string.layout_editor_action_delete_detail,
        { KeyAction.Delete }, { it == KeyAction.Delete },
    ),
    KeyActionOption(
        R.string.layout_editor_action_forward_delete_title,
        R.string.layout_editor_action_group_typing,
        R.string.layout_editor_action_forward_delete_detail,
        { KeyAction.ForwardDelete }, { it == KeyAction.ForwardDelete },
    ),
    KeyActionOption(
        R.string.layout_editor_action_space_title,
        R.string.layout_editor_action_group_typing,
        R.string.layout_editor_action_space_detail,
        { KeyAction.Space }, { it == KeyAction.Space },
    ),
    KeyActionOption(
        R.string.layout_editor_action_enter_title,
        R.string.layout_editor_action_group_typing,
        R.string.layout_editor_action_enter_detail,
        { KeyAction.Enter }, { it == KeyAction.Enter },
    ),
    KeyActionOption(
        R.string.layout_editor_action_symbols_title,
        R.string.layout_editor_action_group_layers,
        R.string.layout_editor_action_symbols_detail,
        { KeyAction.Symbols }, { it == KeyAction.Symbols },
    ),
    KeyActionOption(
        R.string.layout_editor_action_letters_title,
        R.string.layout_editor_action_group_layers,
        R.string.layout_editor_action_letters_detail,
        { KeyAction.Letters }, { it == KeyAction.Letters },
    ),
    KeyActionOption(
        R.string.layout_editor_action_emoji_title,
        R.string.layout_editor_action_group_layers,
        R.string.layout_editor_action_emoji_detail,
        { KeyAction.Emoji }, { it == KeyAction.Emoji },
    ),
    KeyActionOption(
        R.string.layout_editor_action_switch_layout_title,
        R.string.layout_editor_action_group_layers,
        R.string.layout_editor_action_switch_layout_detail,
        { KeyAction.LanguageSwitch }, { it == KeyAction.LanguageSwitch },
    ),
    KeyActionOption(
        R.string.layout_editor_action_input_method_picker_title,
        R.string.layout_editor_action_group_layers,
        R.string.layout_editor_action_input_method_picker_detail,
        { KeyAction.InputMethodPicker }, { it == KeyAction.InputMethodPicker },
    ),
    KeyActionOption(
        R.string.layout_editor_action_fn_title,
        R.string.layout_editor_action_group_layers,
        R.string.layout_editor_action_fn_detail,
        { KeyAction.Fn }, { it == KeyAction.Fn },
    ),
    KeyActionOption(
        R.string.layout_editor_action_ctrl_title,
        R.string.layout_editor_action_group_modifiers,
        R.string.layout_editor_action_modifier_detail,
        { KeyAction.Mod(ModifierKey.CTRL) },
        { it is KeyAction.Mod && it.key == ModifierKey.CTRL },
    ),
    KeyActionOption(
        R.string.layout_editor_action_alt_title,
        R.string.layout_editor_action_group_modifiers,
        R.string.layout_editor_action_modifier_detail,
        { KeyAction.Mod(ModifierKey.ALT) },
        { it is KeyAction.Mod && it.key == ModifierKey.ALT },
    ),
    KeyActionOption(
        R.string.layout_editor_action_meta_title,
        R.string.layout_editor_action_group_modifiers,
        R.string.layout_editor_action_meta_detail,
        { KeyAction.Mod(ModifierKey.META) },
        { it is KeyAction.Mod && it.key == ModifierKey.META },
    ),
    KeyActionOption(
        R.string.layout_editor_action_tab_title,
        R.string.layout_editor_action_group_send_key,
        R.string.layout_editor_action_tab_detail,
        { KeyAction.SendKey(KEYCODE_TAB) },
        { it is KeyAction.SendKey && it.keyCode == KEYCODE_TAB },
    ),
    KeyActionOption(
        R.string.layout_editor_action_escape_title,
        R.string.layout_editor_action_group_send_key,
        R.string.layout_editor_action_escape_detail,
        { KeyAction.SendKey(KEYCODE_ESCAPE) },
        { it is KeyAction.SendKey && it.keyCode == KEYCODE_ESCAPE },
    ),
    KeyActionOption(
        R.string.layout_editor_action_arrow_up_title,
        R.string.layout_editor_action_group_send_key,
        R.string.layout_editor_action_arrow_up_detail,
        { KeyAction.SendKey(KEYCODE_DPAD_UP) },
        { it is KeyAction.SendKey && it.keyCode == KEYCODE_DPAD_UP },
    ),
    KeyActionOption(
        R.string.layout_editor_action_arrow_down_title,
        R.string.layout_editor_action_group_send_key,
        R.string.layout_editor_action_arrow_down_detail,
        { KeyAction.SendKey(KEYCODE_DPAD_DOWN) },
        { it is KeyAction.SendKey && it.keyCode == KEYCODE_DPAD_DOWN },
    ),
    KeyActionOption(
        R.string.layout_editor_action_arrow_left_title,
        R.string.layout_editor_action_group_send_key,
        R.string.layout_editor_action_arrow_left_detail,
        { KeyAction.SendKey(KEYCODE_DPAD_LEFT) },
        { it is KeyAction.SendKey && it.keyCode == KEYCODE_DPAD_LEFT },
    ),
    KeyActionOption(
        R.string.layout_editor_action_arrow_right_title,
        R.string.layout_editor_action_group_send_key,
        R.string.layout_editor_action_arrow_right_detail,
        { KeyAction.SendKey(KEYCODE_DPAD_RIGHT) },
        { it is KeyAction.SendKey && it.keyCode == KEYCODE_DPAD_RIGHT },
    ),
    KeyActionOption(
        R.string.layout_editor_action_braille_dot_title,
        R.string.layout_editor_action_group_chorded,
        R.string.layout_editor_action_braille_dot_detail,
        { KeyAction.BrailleDot(1) }, { it is KeyAction.BrailleDot },
    ),
    KeyActionOption(
        R.string.layout_editor_action_morse_dot_title,
        R.string.layout_editor_action_group_chorded,
        R.string.layout_editor_action_morse_dot_detail,
        { KeyAction.MorseDot }, { it == KeyAction.MorseDot },
    ),
    KeyActionOption(
        R.string.layout_editor_action_morse_dash_title,
        R.string.layout_editor_action_group_chorded,
        R.string.layout_editor_action_morse_dash_detail,
        { KeyAction.MorseDash }, { it == KeyAction.MorseDash },
    ),
    KeyActionOption(
        R.string.layout_editor_action_broadcast_title,
        R.string.layout_editor_action_group_other,
        R.string.layout_editor_action_broadcast_detail,
        { KeyAction.Broadcast("") }, { it is KeyAction.Broadcast },
    ),
    KeyActionOption(
        R.string.layout_editor_action_none_title,
        R.string.layout_editor_action_group_other,
        R.string.layout_editor_action_none_detail,
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
            SectionHeaderPublic(
                stringResource(
                    R.string.layout_editor_key_position_title,
                    ref.row + 1,
                    ref.col + 1,
                ),
            )

            SheetField(
                label = stringResource(R.string.layout_editor_key_label_label),
                value = key.label,
                supporting = stringResource(R.string.layout_editor_key_label_hint),
                resetKey = ref,
            ) { onChange(key.copy(label = it)) }

            SheetField(
                label = stringResource(R.string.layout_editor_key_output_label),
                value = key.output.orEmpty(),
                supporting = stringResource(R.string.layout_editor_key_output_hint),
                resetKey = ref,
            ) { onChange(key.copy(output = it.ifBlank { null })) }

            SheetField(
                label = stringResource(R.string.layout_editor_key_shift_label_label),
                value = key.shiftLabel.orEmpty(),
                supporting = stringResource(R.string.layout_editor_key_shift_label_hint),
                resetKey = ref,
            ) { onChange(key.copy(shiftLabel = it.ifBlank { null })) }

            val option = KeyActionCatalog.firstOrNull { it.matches(key.action) }
            val actionDetail = option?.let { stringResource(it.detailRes) }
            NavRow(
                title = R.string.layout_editor_action_row_title,
                subtitle = actionDetail,
                value = stringResource(
                    option?.titleRes ?: R.string.layout_editor_action_unknown,
                ),
            ) { pickingAction = true }

            // Broadcast keys carry a free-form action string the automation app
            // listens for; every other action is self-contained.
            (key.action as? KeyAction.Broadcast)?.let { broadcast ->
                SheetField(
                    label = stringResource(R.string.layout_editor_broadcast_field_label),
                    value = broadcast.action,
                    supporting = stringResource(R.string.layout_editor_broadcast_field_hint),
                    resetKey = ref,
                ) { onChange(key.copy(action = KeyAction.Broadcast(it.trim()))) }
            }

            // Braille dot keys carry which of the six dots this key is.
            (key.action as? KeyAction.BrailleDot)?.let { brailleDot ->
                SheetField(
                    label = stringResource(R.string.layout_editor_dot_field_label),
                    value = brailleDot.dot.toString(),
                    supporting = stringResource(R.string.layout_editor_dot_field_hint),
                    resetKey = ref,
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
                    label = stringResource(R.string.layout_editor_icon_field_label),
                    value = key.icon.orEmpty(),
                    supporting = iconFieldSupport(key.icon),
                    resetKey = ref,
                ) { onChange(key.copy(icon = it.ifBlank { null })) }

                SheetField(
                    label = stringResource(R.string.layout_editor_icon_hint_field_label),
                    value = key.iconHint.orEmpty(),
                    supporting = iconFieldSupport(key.iconHint),
                    resetKey = ref,
                ) { onChange(key.copy(iconHint = it.ifBlank { null })) }

                SheetField(
                    label = stringResource(R.string.layout_editor_alternates_field_label),
                    value = alternates,
                    supporting = stringResource(R.string.layout_editor_alternates_field_hint),
                    resetKey = ref,
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
                    Text(stringResource(R.string.layout_editor_delete_key_action))
                }
                Spacer(Modifier.weight(1f))
                IconButton(enabled = ref.col > 0, onClick = { onMove(-1) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.layout_editor_move_left_desc),
                    )
                }
                IconButton(enabled = ref.col < rowSize - 1, onClick = { onMove(+1) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.layout_editor_move_right_desc),
                    )
                }
                IconButton(onClick = onDuplicate) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription =
                            stringResource(R.string.layout_editor_duplicate_key_desc),
                    )
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
@Composable
private fun iconFieldSupport(name: String?): String = when {
    name.isNullOrBlank() -> stringResource(R.string.layout_editor_icon_field_hint)
    KeyIcons.byName(name) != null -> stringResource(R.string.layout_editor_icon_found_hint, name)
    else -> stringResource(R.string.layout_editor_icon_missing_hint, name)
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
                    {
                        Text(
                            stringResource(R.string.layout_editor_alternate_hint_badge),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * A text field whose text survives the round trip through the settings store.
 *
 * [value] is read back out of the repository, so it lags the keystroke that
 * caused it by a frame or more. Fed straight back into a Compose text field it
 * rewinds the text *and the cursor* mid-word: typing "ABCDEF" into a key label
 * landed as "qE", and even one character a second put the last one at the front.
 *
 * So the text lives here, and an incoming value is taken only while nothing of
 * ours is in flight. [pending] is the last thing this field emitted; until the
 * store echoes exactly that back, every value arriving is an older read rather
 * than a change from outside, and ignoring it is the whole fix. Once the echo
 * lands the field is in sync again and an undo — or anything else that rewrites
 * the key — moves the text as it should.
 *
 * [resetKey] bounds all of that to one editing session: hand it the key being
 * edited, and moving to another one starts the field over rather than waiting
 * for an echo that will now never come.
 */
@Composable
private fun SheetField(
    label: String,
    value: String,
    supporting: String,
    resetKey: Any?,
    onChange: (String) -> Unit,
) {
    var text by remember(resetKey) { mutableStateOf(value) }
    var pending by remember(resetKey) { mutableStateOf<String?>(null) }
    when {
        pending == null -> if (value != text) text = value
        value == pending -> pending = null
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            pending = it
            onChange(it)
        },
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
        title = R.string.layout_editor_role_title,
        subtitle = stringResource(R.string.layout_editor_role_subtitle),
        options = listOf(
            null to stringResource(CommonR.string.common_none),
            KeyRole.Comma to stringResource(R.string.layout_editor_role_comma),
            KeyRole.Period to stringResource(R.string.layout_editor_role_period),
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
        Text(
            stringResource(R.string.layout_editor_row_height_label, height),
            style = MaterialTheme.typography.bodyLarge,
        )
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
        Text(
            stringResource(R.string.layout_editor_key_width_label, width),
            style = MaterialTheme.typography.bodyLarge,
        )
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
            ) { Text(stringResource(R.string.layout_editor_fill_row_action, remaining)) }
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
        title = { Text(stringResource(R.string.layout_editor_action_picker_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                var lastGroup: Int? = null
                for (option in KeyActionCatalog) {
                    if (option.groupRes != lastGroup) {
                        SectionHeaderPublic(stringResource(option.groupRes))
                        lastGroup = option.groupRes
                    }
                    WmRow(
                        title = stringResource(option.titleRes),
                        subtitle = stringResource(option.detailRes),
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_close)) }
        },
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
    val context = LocalContext.current
    val layout = resolveLayouts(settings.customLayouts).firstOrNull { it.id == layoutId }
    if (layout == null) {
        Text(
            stringResource(R.string.layout_editor_missing_layout_message),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    var text by rememberSaveable(layoutId) { mutableStateOf(LayoutCodec.encodeForEditing(layout)) }
    var error by remember { mutableStateOf<String?>(null) }
    var repairs by remember { mutableStateOf<List<LayoutMessage>>(emptyList()) }
    // The Apply button is a plain lambda, so the message it may set is read here.
    val invalidJsonMessage = stringResource(R.string.layout_editor_json_invalid_error)

    CaptionText(stringResource(R.string.layout_editor_json_caption))

    OutlinedTextField(
        value = text,
        onValueChange = { text = it; error = null },
        label = { Text(stringResource(R.string.layout_editor_json_field_label)) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = rememberJsonSyntaxHighlighter(),
        // Capped, and scrolling inside itself. Uncapped the field grew to the
        // height of the whole document, which put Apply — and the repair notes
        // it prints — dozens of screens below the fold on any real layout.
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp, max = 420.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )

    if (repairs.isNotEmpty()) {
        SettingsGroup(stringResource(R.string.layout_editor_json_applied_title)) {
            for (note in repairs) {
                item {
                    WmRow(
                        title = note.format(context.resources),
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
                    error = invalidJsonMessage
                    return@Button
                }
                // The id in the text is ignored: this screen edits one layout,
                // and honouring a pasted id would silently overwrite a different
                // one — or create a second layout the user never asked for.
                val repaired = parsed.copy(id = layoutId).repair()
                repairs = repaired.repairNotes
                scope.launch {
                    repository.upsertCustomLayout(repaired.spec)
                    if (repaired.repairNotes.isEmpty()) onDone()
                }
            },
        ) { Text(stringResource(R.string.layout_editor_apply_action)) }
    }
}
