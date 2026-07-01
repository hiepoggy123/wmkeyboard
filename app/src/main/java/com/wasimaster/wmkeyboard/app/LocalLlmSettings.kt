package com.wasimaster.wmkeyboard.app

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmCatalog
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmDownloadManager
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmDownloadManager.DownloadStatus
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmDownloadManager.FailReason
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmModel
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmStore
import com.wasimaster.wmkeyboard.core.localllm.ModelTier
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.LocalLlmBackend
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Downloads above this size ask before using mobile data. */
private const val METERED_CONFIRM_BYTES = 500_000_000L

/**
 * The On-device provider's section of the AI tool settings: Hugging Face
 * token, the downloadable model catalog with live progress, imported custom
 * models, and the compute backend. Download state lives in
 * [LocalLlmDownloadManager], so navigating away or rotating never loses an
 * in-flight download.
 */
@Composable
internal fun LocalLlmModelManager(repository: SettingsRepository, settings: KeyboardSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val filesDir = context.filesDir
    val states by LocalLlmDownloadManager.states.collectAsState()
    var customModels by remember { mutableStateOf(LocalLlmStore.customModels(filesDir)) }
    var storageUsed by remember { mutableStateOf(0L) }
    var orphanBytes by remember { mutableStateOf(0L) }
    var meteredPending by remember { mutableStateOf<LocalLlmModel?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { LocalLlmDownloadManager.refresh(filesDir) }
    LaunchedEffect(states, customModels, importing) {
        storageUsed = withContext(Dispatchers.IO) { LocalLlmStore.totalBytesUsed(filesDir) }
        orphanBytes = withContext(Dispatchers.IO) { LocalLlmStore.orphanBytes(filesDir) }
        // The only model on disk needs no selection step: adopt it — covers
        // both "first download just finished" and "selection was deleted".
        if (LocalLlmStore.selectedModelFile(filesDir, settings.aiLocalModelId) == null) {
            LocalLlmStore.soleDownloadedId(filesDir)?.let { repository.setAiLocalModelId(it) }
        }
    }

    val totalRamMb = remember {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        (info.totalMem / (1024 * 1024)).toInt()
    }

    fun startDownload(model: LocalLlmModel) {
        LocalLlmDownloadManager.start(filesDir, model, settings.hfToken)
    }

    fun requestDownload(model: LocalLlmModel) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (model.sizeBytes >= METERED_CONFIRM_BYTES && cm.isActiveNetworkMetered) {
            meteredPending = model
        } else {
            startDownload(model)
        }
    }

    SettingsGroup("Hugging Face account") {
        item {
            ApiKeyField(
                label = "Access token",
                value = settings.hfToken,
                builtInAvailable = false,
                emptyHint = "Only needed for gated models (Gemma)",
            ) { repository.setHfToken(it) }
        }
        item {
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = { uriHandler.openUri(LocalLlmCatalog.TOKEN_URL) }) {
                    Text("Get a token")
                }
            }
        }
    }

    // On disk (ready to use) vs still on Hugging Face — two questions the
    // one mixed list used to answer badly. Anything mid-download counts as
    // "available" so its progress bar stays with the thing being fetched.
    val onDisk = LocalLlmCatalog.models.filter {
        (states[it.id] ?: DownloadStatus.NotDownloaded) is DownloadStatus.Downloaded
    }
    // Selected model to the very top of its section — it's the one answer
    // "which model am I using?" needs, and scrolling for it is silly.
    val yours = onDisk.sortedByDescending { it.id == settings.aiLocalModelId }
    val available = LocalLlmCatalog.models - onDisk.toSet()

    @Composable
    fun catalogRow(model: LocalLlmModel) {
        CatalogModelRow(
            model = model,
            status = states[model.id] ?: DownloadStatus.NotDownloaded,
            selected = settings.aiLocalModelId == model.id,
            hasToken = settings.hfToken.isNotBlank(),
            downloadBusy = LocalLlmDownloadManager.isBusy,
            tooBigForRam = model.minRamMb > totalRamMb,
            onDownload = { requestDownload(model) },
            onCancel = { LocalLlmDownloadManager.cancel() },
            onSelect = { scope.launch { repository.setAiLocalModelId(model.id) } },
            onDelete = {
                LocalLlmDownloadManager.delete(filesDir, model)
                if (settings.aiLocalModelId == model.id) {
                    scope.launch { repository.setAiLocalModelId("") }
                }
            },
            onOpenLicense = { uriHandler.openUri(LocalLlmCatalog.licenseUrl(model)) },
        )
    }

    // Combined size of what's on disk, shown beside the header so "how much
    // is this costing me?" is answerable at a glance. Sums the same per-row
    // numbers the user sees (catalog sizes + custom file lengths), unlike the
    // storage caption below which also counts partial and orphaned files.
    if (yours.isNotEmpty() || customModels.isNotEmpty()) {
        val downloadedBytes = yours.sumOf { it.sizeBytes } +
            customModels.sumOf { it.length() }
        ModelsSectionHeader("Your models", formatBytes(downloadedBytes))
    }
    SettingsGroup {
        for (model in yours) item { catalogRow(model) }
        for (file in customModels.sortedByDescending {
            settings.aiLocalModelId == LocalLlmStore.CUSTOM_PREFIX + it.name
        }) {
            item {
                val id = LocalLlmStore.CUSTOM_PREFIX + file.name
                CustomModelRow(
                    file = file,
                    selected = settings.aiLocalModelId == id,
                    onSelect = { scope.launch { repository.setAiLocalModelId(id) } },
                    onDelete = {
                        LocalLlmStore.deleteCustom(filesDir, file.name)
                        customModels = LocalLlmStore.customModels(filesDir)
                        if (settings.aiLocalModelId == id) {
                            scope.launch { repository.setAiLocalModelId("") }
                        }
                    },
                )
            }
        }
    }
    if (yours.isEmpty() && customModels.isEmpty()) {
        CaptionText("No models on this device yet — download one below to use AI offline.")
    }

    SettingsGroup("Available to download") {
        for (model in available) item { catalogRow(model) }
    }
    CaptionText(
        "Ordered best-first, not smallest-first: the models at the top write " +
            "better but download bigger, respond slower and need more memory. " +
            "Gated models need a Hugging Face account — add your token above " +
            "and accept the license on the model's page once.",
    )

    SettingsGroup("Import your own") {
        item {
            ImportModelButton(
                importing = importing,
                onStart = { importing = true; importError = null },
                onDone = { error ->
                    importing = false
                    importError = error
                    customModels = LocalLlmStore.customModels(filesDir)
                },
            )
        }
    }
    importError?.let { CaptionText(it, error = true) }
    CaptionText(
        "Import any LiteRT-LM model (.litertlm or .task file) — for example " +
            "one you converted yourself or downloaded in a browser.",
    )

    SectionHeader("Compute")
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        for (backend in LocalLlmBackend.entries) {
            FilterChip(
                selected = settings.aiLocalBackend == backend,
                onClick = { scope.launch { repository.setAiLocalBackend(backend) } },
                label = { Text(backend.label) },
            )
        }
    }
    CaptionText(
        "GPU is experimental. It falls back to CPU on its own if it fails to " +
            "start, or if a model crashes mid-answer — GPU memory is much " +
            "tighter than system RAM, so bigger models often need CPU. Pick " +
            "CPU here if the GPU keeps failing.",
    )

    if (storageUsed > 0) {
        CaptionText(
            "Models use ${formatBytes(storageUsed)} of storage, kept privately in " +
                "app storage. A download you cancel or lose connection on " +
                "resumes from where it stopped.",
        )
    }
    // A model dropped from the catalog leaves a directory no row can reach.
    // Report it and let the user decide rather than deleting a multi-GB file
    // they paid the bandwidth for.
    if (orphanBytes > 0) {
        CaptionText(
            "${formatBytes(orphanBytes)} belongs to models that are no longer in " +
                "the catalog and can't be selected any more.",
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { LocalLlmStore.deleteOrphans(filesDir) }
                    orphanBytes = 0
                    storageUsed = withContext(Dispatchers.IO) {
                        LocalLlmStore.totalBytesUsed(filesDir)
                    }
                }
            }) { Text("Free up ${formatBytes(orphanBytes)}") }
        }
    }

    meteredPending?.let { model ->
        AlertDialog(
            onDismissRequest = { meteredPending = null },
            title = { Text("Use mobile data?") },
            text = {
                Text(
                    "${model.displayName} is a ${formatBytes(model.sizeBytes)} download " +
                        "and you're on a metered connection.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    startDownload(model)
                    meteredPending = null
                }) { Text("Download anyway") }
            },
            dismissButton = {
                TextButton(onClick = { meteredPending = null }) { Text("Wait for Wi-Fi") }
            },
        )
    }
}

/**
 * A section header with a muted, right-aligned trailing value — used to hang
 * the combined download size off the "Your models" title. Padding mirrors
 * [SectionHeader] so the label and trailing text line up with row content.
 */
@Composable
private fun ModelsSectionHeader(title: String, trailing: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            trailing,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatalogModelRow(
    model: LocalLlmModel,
    status: DownloadStatus,
    selected: Boolean,
    hasToken: Boolean,
    downloadBusy: Boolean,
    tooBigForRam: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onOpenLicense: () -> Unit,
) {
    val downloaded = status is DownloadStatus.Downloaded
    val subtitle = buildString {
        append("${model.params} · ${formatBytes(model.sizeBytes)}")
        append(" · ${model.description}")
        if (tooBigForRam) append(" May be too large for this device.")
    }
    // Tapping the row selects a downloaded model — the "Use this model"
    // button stays for discoverability, but the whole row is the target.
    val rowClick = if (downloaded && !selected) onSelect else null
    SelectionHighlight(selected = selected && downloaded, onClick = rowClick) {
        ListItem(
            colors = transparentListColors(),
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        model.displayName,
                        fontWeight = if (selected && downloaded) FontWeight.SemiBold else null,
                    )
                    if (model.gated && !downloaded) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = "Requires a Hugging Face token",
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TierBadge(model.tier)
                }
            },
            supportingContent = {
                Column {
                    if (selected && downloaded) {
                        Text(
                            "In use",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(subtitle)
                }
            },
            leadingContent = if (selected && downloaded) {
                { Icon(Icons.Outlined.Check, contentDescription = "Selected") }
            } else null,
            // The primary action for a row lives on its right edge, where a
            // list's actions belong — not on a button underneath it.
            trailingContent = {
                when (status) {
                    is DownloadStatus.Downloaded -> IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete ${model.displayName}",
                        )
                    }
                    is DownloadStatus.Downloading -> IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel download")
                    }
                    is DownloadStatus.Paused -> TextButton(
                        onClick = onDownload,
                        enabled = !downloadBusy,
                    ) { Text("Resume") }
                    is DownloadStatus.NotDownloaded, is DownloadStatus.Failed ->
                        if (!model.gated || hasToken) {
                            TextButton(
                                onClick = onDownload,
                                enabled = !downloadBusy,
                            ) { Text(if (status is DownloadStatus.Failed) "Retry" else "Download") }
                        }
                }
            },
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        // Everything that only sometimes has something to say sits below the
        // row and animates its height, so appearing progress or an error
        // doesn't snap the list.
        Column(modifier = Modifier.animateContentSize()) {
            when (status) {
                is DownloadStatus.NotDownloaded -> if (model.gated && !hasToken) {
                    CaptionText("Add your Hugging Face token above to download this model.")
                }
                is DownloadStatus.Downloading -> DownloadProgress(status.bytes, status.total)
                is DownloadStatus.Paused -> CaptionText("Paused at ${formatBytes(status.bytes)}")
                is DownloadStatus.Downloaded -> if (!selected) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TextButton(onClick = onSelect) { Text("Use this model") }
                    }
                }
                is DownloadStatus.Failed -> {
                    CaptionText(status.message, error = true)
                    if (status.reason == FailReason.LICENSE_NOT_ACCEPTED) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                            TextButton(onClick = onOpenLicense) { Text("Open model page") }
                        }
                    }
                }
            }
        }
    }
}

/** An imported .litertlm/.task file, styled like a catalog row. */
@Composable
private fun CustomModelRow(
    file: File,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    SelectionHighlight(selected = selected, onClick = if (selected) null else onSelect) {
        ListItem(
            colors = transparentListColors(),
            headlineContent = {
                Text(file.name, fontWeight = if (selected) FontWeight.SemiBold else null)
            },
            supportingContent = {
                Column {
                    if (selected) {
                        Text(
                            "In use",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(formatBytes(file.length()))
                }
            },
            leadingContent = if (selected) {
                { Icon(Icons.Outlined.Check, contentDescription = "Selected") }
            } else null,
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete ${file.name}")
                }
            },
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (!selected) {
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = onSelect) { Text("Use this model") }
            }
        }
    }
}

/**
 * Tints the model that's actually in use. The colour is animated so moving
 * the selection (and with it, the row's jump to the top of the section)
 * reads as a change rather than a redraw.
 */
@Composable
private fun SelectionHighlight(
    selected: Boolean,
    onClick: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else Color.Transparent,
        animationSpec = tween(300),
        label = "modelRowBackground",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .animateContentSize(),
        content = content,
    )
}

/** "Recommended" / "Untested" / "Experimental" chip next to a model's name. */
@Composable
private fun TierBadge(tier: ModelTier) {
    val label = tier.badge ?: return
    val container = if (tier == ModelTier.RECOMMENDED) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = if (tier == ModelTier.RECOMMENDED) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = onContainer,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun DownloadProgress(bytes: Long, total: Long) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (total > 0) {
            LinearProgressIndicator(
                progress = { (bytes.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${formatBytes(bytes)} of ${formatBytes(total)} · ${(bytes * 100 / total)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                formatBytes(bytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * SAF picker that copies the chosen .litertlm/.task into the custom models
 * directory via a .part + rename, so a killed copy never leaves a file that
 * looks importable.
 */
@Composable
private fun ImportModelButton(
    importing: Boolean,
    onStart: () -> Unit,
    onDone: (error: String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onStart()
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { importModel(context, uri) }.exceptionOrNull()?.message
            }
            onDone(error)
        }
    }
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        TextButton(
            onClick = { launcher.launch(arrayOf("*/*")) },
            enabled = !importing,
        ) { Text(if (importing) "Importing…" else "Import model file") }
    }
}

private fun importModel(context: Context, uri: android.net.Uri) {
    var name: String? = null
    var size = -1L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                .takeIf { it >= 0 }?.let { name = cursor.getString(it) }
            cursor.getColumnIndex(OpenableColumns.SIZE)
                .takeIf { it >= 0 }?.let { size = cursor.getLong(it) }
        }
    }
    val fileName = name?.let { File(it).name }
        ?: throw IllegalArgumentException("Couldn't read the file's name")
    val extension = fileName.substringAfterLast('.', "").lowercase()
    require(extension == "litertlm" || extension == "task") {
        "Not a model file — expected a .litertlm or .task file"
    }
    val dir = LocalLlmStore.customDir(context.filesDir).apply { mkdirs() }
    val free = android.os.StatFs(dir.path).availableBytes
    require(size < 0 || free > size + 64L * 1024 * 1024) {
        "Not enough storage for this ${formatBytes(size)} file"
    }
    val part = File(dir, "$fileName.part")
    val target = File(dir, fileName)
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            part.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Couldn't open the selected file")
        check(part.renameTo(target)) { "Couldn't move the imported file into place" }
    } finally {
        part.delete()
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1e9)
    bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1e6)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1e3)
    else -> "$bytes B"
}
