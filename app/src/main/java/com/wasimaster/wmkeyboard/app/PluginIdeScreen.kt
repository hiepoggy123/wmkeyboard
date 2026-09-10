package com.wasimaster.wmkeyboard.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.plugins.PluginDraft
import com.wasimaster.wmkeyboard.core.plugins.PluginEvent
import com.wasimaster.wmkeyboard.core.plugins.PluginManifest
import com.wasimaster.wmkeyboard.core.plugins.PluginManifestCodec
import com.wasimaster.wmkeyboard.core.plugins.PluginPermission
import com.wasimaster.wmkeyboard.core.plugins.PluginPreviewSession
import com.wasimaster.wmkeyboard.core.plugins.PluginRuntime
import com.wasimaster.wmkeyboard.core.plugins.PluginStore
import com.wasimaster.wmkeyboard.core.plugins.PluginWorkspace
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaDocuments
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaHostShape
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaNavigation
import com.wasimaster.wmkeyboard.core.plugins.lua.LuaOutlineItem
import com.wasimaster.wmkeyboard.core.plugins.resolve
import com.wasimaster.wmkeyboard.core.plugins.ui.LocalPluginPanelStyle
import com.wasimaster.wmkeyboard.core.plugins.ui.PluginInputHost
import com.wasimaster.wmkeyboard.core.plugins.ui.PluginPanelStyle
import com.wasimaster.wmkeyboard.core.plugins.ui.PluginWidgetList
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long typing has to pause before the draft is written. */
private const val AUTOSAVE_MS = 400L

private enum class IdePanel { CLOSED, PREVIEW, CONSOLE, PROBLEMS, OUTLINE }

/** What one pass of the checks found: the problems, and the outline of the last text that parsed. */
private data class IdeInspection(val diagnostics: List<CodeDiagnostic>, val outline: List<LuaOutlineItem>) {
    companion object {
        val None = IdeInspection(emptyList(), emptyList())
    }
}

// ---------------------------------------------------------------------------
// The drafts list
// ---------------------------------------------------------------------------

/** The plugin editor's front page: a new plugin, and every draft in the workspace. */
@Composable
internal fun PluginIdeProjectsScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val workspace = remember { PluginWorkspace.get(context) }
    val store = remember { PluginStore.get(context) }
    val revision by workspace.revision.collectAsStateWithLifecycle()
    val drafts = remember(revision) { workspace.drafts().map { it to workspace.manifest(it.draftId) } }
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PluginDraft?>(null) }
    val untitled = stringResource(R.string.plugin_ide_draft_untitled)
    val newError = stringResource(R.string.plugin_ide_new_error)

    // No scroller of its own: SettingsScreen already wraps this content in a
    // Column(verticalScroll).
    SettingsGroup {
        item {
            WmRow(
                title = stringResource(R.string.plugin_ide_new_title),
                subtitle = stringResource(R.string.plugin_ide_new_subtitle),
                trailing = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                onClick = { creating = true },
            )
        }
    }

    SettingsGroup(stringResource(R.string.plugin_ide_drafts_title)) {
        if (drafts.isEmpty()) {
            item { CaptionText(stringResource(R.string.plugin_ide_drafts_empty)) }
        } else {
            for ((draft, manifest) in drafts) {
                item {
                    val edited = DateUtils.getRelativeTimeSpanString(
                        draft.updatedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                    val subtitle = if (draft.publishedVersion.isNotEmpty()) {
                        stringResource(R.string.plugin_ide_draft_published_label, draft.publishedVersion, edited)
                    } else {
                        stringResource(R.string.plugin_ide_draft_edited_label, edited)
                    }
                    WmRow(
                        title = manifest?.name?.ifBlank { null } ?: untitled,
                        subtitle = subtitle,
                        trailing = {
                            IconButton(onClick = { deleting = draft }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.plugin_ide_delete_draft_desc))
                            }
                        },
                        onClick = { onNavigate("plugin_ide/${draft.draftId}") },
                    )
                }
            }
        }
    }

    if (creating) {
        NewPluginDialog(
            onDismiss = { creating = false },
            onCreate = { name ->
                creating = false
                scope.launch {
                    val draft = withContext(Dispatchers.IO) { newBlankDraft(workspace, store, name) }
                    if (draft != null) {
                        onNavigate("plugin_ide/${draft.draftId}")
                    } else {
                        Toast.makeText(context, newError, Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    deleting?.let { draft ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.plugin_ide_delete_draft_title)) },
            text = { Text(stringResource(R.string.plugin_ide_delete_draft_body)) },
            confirmButton = {
                TextButton({
                    workspace.delete(draft.draftId)
                    deleting = null
                }) { Text(stringResource(CommonR.string.common_delete)) }
            },
            dismissButton = {
                TextButton({ deleting = null }) { Text(stringResource(CommonR.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun NewPluginDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val fallback = stringResource(R.string.plugin_ide_new_default_name)
    var name by rememberSaveable { mutableStateOf(fallback) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_ide_new_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(PluginManifestCodec.MAX_NAME) },
                label = { Text(stringResource(R.string.plugin_ide_new_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton({ onCreate(name.trim().ifBlank { fallback }) }) {
                Text(stringResource(R.string.plugin_ide_new_action))
            }
        },
        dismissButton = {
            TextButton(onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

// ---------------------------------------------------------------------------
// The code screen
// ---------------------------------------------------------------------------

/**
 * One draft, open for editing: the code, full height, and under it a panel that
 * shows the plugin running and what it printed.
 *
 * Builds its own Scaffold rather than going through SettingsScreen, as
 * AiChatScreen does: the house frame's collapsing heading and scrolling column
 * would take a third of a phone screen from a page whose whole point is showing
 * as much code as fits, and the code field scrolls inside itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginIdeScreen(draftId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val workspace = remember { PluginWorkspace.get(context) }
    val store = remember { PluginStore.get(context) }
    if (remember(draftId) { workspace.draft(draftId) } == null) {
        MissingDraft(onBack)
        return
    }
    val ide = remember(draftId) { PluginIdeState(draftId, WorkspaceIdePorts(workspace, store, draftId)) }
    val editor = rememberCodeEditorState(draftId, rules = LuaSmartRules, keepHistory = true) { ide.savedText }
    val scope = rememberCoroutineScope()
    val preview = remember(draftId) { PluginPreviewSession(workspace, store, post = { body -> scope.launch { body() } }) }
    val previewState by preview.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var panel by rememberSaveable { mutableStateOf(IdePanel.CLOSED) }
    var menuOpen by remember { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }

    val text = editor.text
    // The draft on disk is the save point: written after a pause in typing, and
    // again as the screen goes, so leaving never loses a character.
    LaunchedEffect(text) {
        delay(AUTOSAVE_MS)
        withContext(Dispatchers.IO) { ide.save(text) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        ide.save(editor.text)
        preview.stop()
    }
    DisposableEffect(preview) {
        onDispose {
            ide.save(editor.text)
            preview.shutdown()
        }
    }

    val lineStarts = remember(text) { lineStartOffsets(text) }
    // The storage check follows the manifest as it is edited, not as it was saved.
    val storage = PluginPermission.Storage.wire in ide.manifest.permissions
    val inspection by produceState(IdeInspection.None, text, storage) {
        delay(250)
        val previous = value
        value = withContext(Dispatchers.Default) {
            val found = LuaCode.diagnostics(text, LuaHostShape(storage))
            // The same document the checks just built, so this is not a second parse.
            val outline = LuaDocuments.of(text).analysis?.let(LuaNavigation::outline) ?: previous.outline
            IdeInspection(found, outline)
        }
    }
    val diagnostics = inspection.diagnostics
    val failureLine = previewState.failure?.takeIf { it.chunk == MAIN_CHUNK }?.line?.minus(1)
    val decorations = remember(diagnostics, lineStarts, failureLine) {
        val marks = HashMap<Int, CodeSeverity>()
        for (diagnostic in diagnostics) {
            val line = lineOf(lineStarts, diagnostic.range.min.coerceIn(0, lineStarts.last()))
            val held = marks[line]
            if (held == null || diagnostic.severity < held) marks[line] = diagnostic.severity
        }
        failureLine?.takeIf { it in lineStarts.indices }?.let { marks[it] = CodeSeverity.ERROR }
        if (marks.isEmpty()) CodeDecorations.None else CodeDecorations(squiggles = diagnostics, gutterMarks = marks)
    }

    val title = ide.manifest.name.ifBlank { stringResource(R.string.plugin_ide_draft_untitled) }
    RegisterSettingsCrumb(title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(CommonR.string.common_back))
                    }
                },
                actions = {
                    val running = previewState.status == PluginPreviewSession.Status.RUNNING
                    IconButton(onClick = {
                        if (running) {
                            preview.stop()
                        } else {
                            preview.run(draftId, editor.text, ide.manifest)
                            if (panel == IdePanel.CLOSED) panel = IdePanel.PREVIEW
                        }
                    }) {
                        Icon(
                            if (running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = stringResource(if (running) R.string.plugin_ide_stop_desc else R.string.plugin_ide_run_desc),
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val outcome = withContext(Dispatchers.IO) { ide.publish(editor.text) }
                            snackbar.showSnackbar(publishMessage(context, outcome))
                        }
                    }) {
                        Icon(Icons.Outlined.Publish, contentDescription = stringResource(R.string.plugin_ide_publish_desc))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.plugin_ide_more_desc))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.plugin_ide_format_action)) },
                                onClick = {
                                    menuOpen = false
                                    editor.replace(LuaCode.format(editor.text))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.plugin_ide_details_action)) },
                                onClick = {
                                    menuOpen = false
                                    detailsOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.plugin_ide_version_action)) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        val kept = withContext(Dispatchers.IO) { ide.saveVersion(editor.text) }
                                        snackbar.showSnackbar(
                                            context.getString(
                                                if (kept == null) R.string.plugin_ide_version_same_message else R.string.plugin_ide_version_saved_message,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // No imePadding here, as in AiChatScreen: the activity is not edge-to-edge,
        // so the window already resizes for the keyboard.
        Column(Modifier.padding(padding).fillMaxSize()) {
            CodeSurface(
                state = editor,
                language = LuaCode,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
                lineStarts = lineStarts,
                decorations = decorations,
                wrap = true,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                completions = true,
            )
            IdePanelBar(panel, problems = diagnostics.size) { chosen -> panel = if (panel == chosen) IdePanel.CLOSED else chosen }
            if (panel != IdePanel.CLOSED) {
                HorizontalDivider()
                Box(Modifier.fillMaxWidth().fillMaxHeight(PANEL_FRACTION)) {
                    when (panel) {
                        IdePanel.PREVIEW -> PreviewPane(preview, previewState)
                        IdePanel.CONSOLE -> ConsolePane(preview, previewState.consoleRevision) { line ->
                            editor.moveTo(offsetOfLine(editor.text, line - 1))
                        }
                        IdePanel.PROBLEMS -> ProblemsPane(diagnostics, lineStarts) { range -> editor.select(range) }
                        IdePanel.OUTLINE -> OutlinePane(inspection.outline, lineStarts) { range -> editor.select(range) }
                        IdePanel.CLOSED -> Unit
                    }
                }
            }
        }
    }

    if (detailsOpen) {
        PluginDetailsDialog(
            manifest = ide.manifest,
            onDismiss = { detailsOpen = false },
            onSave = { updated ->
                detailsOpen = false
                scope.launch { withContext(Dispatchers.IO) { ide.updateManifest(updated) } }
            },
        )
    }
}

/** The chunk name the runtime gives the plugin's own script. */
private const val MAIN_CHUNK = "main.lua"

/** How much of the screen the panel under the code takes when it is open. */
private const val PANEL_FRACTION = 0.42f

private fun publishMessage(context: Context, outcome: PublishOutcome): String = when (outcome) {
    is PublishOutcome.Published -> context.getString(
        if (outcome.replaced) R.string.plugin_ide_updated_message else R.string.plugin_ide_published_message,
        outcome.version,
    )
    is PublishOutcome.Invalid -> outcome.reason.resolve(context)
    PublishOutcome.TooManyPlugins -> context.getString(R.string.plugin_ide_too_many_error)
    PublishOutcome.Failed -> context.getString(R.string.plugin_ide_publish_failed_error)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissingDraft(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(CommonR.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            CaptionText(stringResource(R.string.plugin_ide_missing))
        }
    }
}

@Composable
private fun IdePanelBar(panel: IdePanel, problems: Int, onSelect: (IdePanel) -> Unit) {
    val numbers = remember { NumberFormat.getIntegerInstance() }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = panel == IdePanel.PREVIEW,
            onClick = { onSelect(IdePanel.PREVIEW) },
            label = { Text(stringResource(R.string.plugin_ide_preview_label)) },
        )
        FilterChip(
            selected = panel == IdePanel.CONSOLE,
            onClick = { onSelect(IdePanel.CONSOLE) },
            label = { Text(stringResource(R.string.plugin_ide_console_label)) },
        )
        FilterChip(
            selected = panel == IdePanel.PROBLEMS,
            onClick = { onSelect(IdePanel.PROBLEMS) },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.plugin_ide_problems_label))
                    if (problems > 0) {
                        Spacer(Modifier.width(6.dp))
                        Badge { Text(numbers.format(problems)) }
                    }
                }
            },
        )
        FilterChip(
            selected = panel == IdePanel.OUTLINE,
            onClick = { onSelect(IdePanel.OUTLINE) },
            label = { Text(stringResource(R.string.plugin_ide_outline_label)) },
        )
    }
}

/** The plugin's panel as the keyboard would draw it, in this app's colours, with real text fields. */
@Composable
private fun PreviewPane(preview: PluginPreviewSession, state: PluginPreviewSession.State) {
    val context = LocalContext.current
    val style = rememberMaterialPluginStyle()
    val host = remember(preview, state.inputs) { PreviewInputHost(preview, state.inputs, context) }
    val copied = stringResource(R.string.plugin_ide_copied_message)
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        state.usage[PluginRuntime.Phase.RENDER]?.let { usage ->
            val numbers = remember { NumberFormat.getIntegerInstance() }
            Text(
                stringResource(R.string.plugin_ide_usage_label, numbers.format(usage.instructions), numbers.format(usage.instructionLimit)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.failure?.let { failure ->
            Text(
                failure.text.substringBefore('\n'),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.ui.root.isEmpty()) {
            Text(
                stringResource(R.string.plugin_ide_preview_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompositionLocalProvider(LocalPluginPanelStyle provides style) {
                PluginWidgetList(
                    widgets = state.ui.root,
                    host = host,
                    onEvent = preview::send,
                    onInsert = { copyText(context, it, copied) },
                    onCopy = { copyText(context, it, copied) },
                )
            }
        }
    }
}

/** What the plugin printed, where it failed and when it stopped, oldest first. */
@Composable
private fun ConsolePane(preview: PluginPreviewSession, revision: Int, onJump: (Int) -> Unit) {
    val entries = remember(revision) { preview.console() }
    val list = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) list.scrollToItem(entries.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.plugin_ide_console_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { preview.clearConsole() }) { Text(stringResource(R.string.plugin_ide_console_clear_action)) }
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            items(entries) { entry -> ConsoleLine(entry, onJump) }
        }
    }
}

@Composable
private fun ConsoleLine(entry: PluginPreviewSession.ConsoleEntry, onJump: (Int) -> Unit) {
    val context = LocalContext.current
    val words = when (entry.kind) {
        PluginPreviewSession.ConsoleEntry.Kind.STARTED -> stringResource(R.string.plugin_ide_console_started)
        PluginPreviewSession.ConsoleEntry.Kind.CANCELLED -> stringResource(R.string.plugin_ide_console_cancelled)
        else -> entry.text.ifEmpty { entry.message?.resolve(context).orEmpty() }
    }
    val colour = when (entry.kind) {
        PluginPreviewSession.ConsoleEntry.Kind.ERROR, PluginPreviewSession.ConsoleEntry.Kind.STOPPED -> MaterialTheme.colorScheme.error
        PluginPreviewSession.ConsoleEntry.Kind.PRINT -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val line = entry.line?.takeIf { entry.chunk == MAIN_CHUNK }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (line != null) Modifier.clickable { onJump(line) } else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            words,
            fontFamily = CodeFontFamily,
            fontSize = 12.sp,
            color = colour,
            modifier = Modifier.weight(1f),
        )
        if (line != null) {
            Text(
                stringResource(R.string.plugin_ide_console_line_label, line),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** The manifest, with what the importer would refuse listed under it as it is typed. */
@Composable
private fun PluginDetailsDialog(manifest: PluginManifest, onDismiss: () -> Unit, onSave: (PluginManifest) -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(manifest.name) }
    var id by rememberSaveable { mutableStateOf(manifest.id) }
    var version by rememberSaveable { mutableStateOf(manifest.pluginVersion) }
    var author by rememberSaveable { mutableStateOf(manifest.author) }
    var description by rememberSaveable { mutableStateOf(manifest.description) }
    var storage by rememberSaveable { mutableStateOf(PluginPermission.Storage.wire in manifest.permissions) }
    val edited = manifest.copy(
        format = PluginManifestCodec.FORMAT,
        name = name,
        id = id,
        pluginVersion = version,
        author = author,
        description = description,
        permissions = if (storage) listOf(PluginPermission.Storage.wire) else emptyList(),
    )
    val problems = remember(edited) { PluginManifestCodec.problems(edited) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_ide_details_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.plugin_ide_details_name_label)) }, singleLine = true)
                OutlinedTextField(id, { id = it }, label = { Text(stringResource(R.string.plugin_ide_details_id_label)) }, singleLine = true)
                OutlinedTextField(version, { version = it }, label = { Text(stringResource(R.string.plugin_ide_details_version_label)) }, singleLine = true)
                OutlinedTextField(author, { author = it }, label = { Text(stringResource(R.string.plugin_ide_details_author_label)) }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.plugin_ide_details_description_label)) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = storage, onCheckedChange = { storage = it })
                    Text(stringResource(PluginPermission.Storage.labelRes))
                }
                for (problem in problems) {
                    Text(
                        problem.text.resolve(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton({ onSave(edited) }) { Text(stringResource(CommonR.string.common_save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(CommonR.string.common_cancel)) } },
    )
}

/** The shared plugin renderer, dressed in this app's Material colours. No focus ring, no chip outline. */
@Composable
private fun rememberMaterialPluginStyle(): PluginPanelStyle {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    return remember(colors, shape) {
        PluginPanelStyle(
            text = colors.onSurface,
            secondaryText = colors.onSurfaceVariant,
            surface = colors.surfaceContainerHigh,
            onSurface = colors.onSurface,
            accent = colors.primary,
            cardShape = shape,
            cardBorder = { modifier, _ -> modifier },
            ring = { modifier, _ -> modifier },
            chip = { label, selected, enabled, modifier, onClick ->
                FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, enabled = enabled, modifier = modifier)
            },
        )
    }
}

/** A preview's inputs are real fields: typing is an input event, and Paste reads the clipboard. */
private class PreviewInputHost(
    private val preview: PluginPreviewSession,
    private val inputs: Map<String, String>,
    private val context: Context,
) : PluginInputHost {
    override fun value(id: String): String = inputs[id].orEmpty()

    override fun isFocused(id: String): Boolean = false

    override fun onFocus(id: String) = Unit

    override fun onPaste(id: String) {
        pasteText(context)?.let { preview.send(PluginEvent.InputChanged(id, it)) }
    }

    override val onValueChange: (String, String) -> Unit = { id, text ->
        preview.send(PluginEvent.InputChanged(id, text))
    }
}

private fun copyText(context: Context, text: String, confirmation: String) {
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("plugin", text))
        Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
    }
}

private fun pasteText(context: Context): String? = runCatching {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = manager.primaryClip ?: return null
    (0 until clip.itemCount).asSequence().mapNotNull { clip.getItemAt(it).coerceToText(context)?.toString() }.firstOrNull()
}.getOrNull()
