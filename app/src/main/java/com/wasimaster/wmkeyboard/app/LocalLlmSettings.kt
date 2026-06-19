package com.wasimaster.wmkeyboard.app

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmCatalog
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmDownloadManager
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmDownloadManager.DownloadStatus
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmDownloadManager.FailReason
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmModel
import com.wasimaster.wmkeyboard.core.localllm.LocalLlmStore
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
    var meteredPending by remember { mutableStateOf<LocalLlmModel?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { LocalLlmDownloadManager.refresh(filesDir) }
    LaunchedEffect(states, customModels, importing) {
        storageUsed = withContext(Dispatchers.IO) { LocalLlmStore.totalBytesUsed(filesDir) }
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

    SettingsGroup("Models") {
        for (model in LocalLlmCatalog.models) {
            item {
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
        }
    }
    CaptionText(
        "Sorted by size — bigger models write better but respond slower and " +
            "need more memory. Gated models need a Hugging Face account: add " +
            "your token above and accept the license on the model's page once.",
    )

    SettingsGroup("Custom models") {
        for (file in customModels) {
            item {
                val id = LocalLlmStore.CUSTOM_PREFIX + file.name
                ListItem(
                    colors = transparentListColors(),
                    headlineContent = { Text(file.name) },
                    supportingContent = { Text(formatBytes(file.length())) },
                    leadingContent = if (settings.aiLocalModelId == id) {
                        { Icon(Icons.Outlined.Check, contentDescription = "Selected") }
                    } else null,
                    trailingContent = {
                        IconButton(onClick = {
                            LocalLlmStore.deleteCustom(filesDir, file.name)
                            customModels = LocalLlmStore.customModels(filesDir)
                            if (settings.aiLocalModelId == id) {
                                scope.launch { repository.setAiLocalModelId("") }
                            }
                        }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete ${file.name}") }
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                if (settings.aiLocalModelId != id) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TextButton(onClick = {
                            scope.launch { repository.setAiLocalModelId(id) }
                        }) { Text("Use this model") }
                    }
                }
            }
        }
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
    CaptionText("GPU is experimental — if it fails to start, the model falls back to CPU automatically.")

    if (storageUsed > 0) {
        CaptionText(
            "Models use ${formatBytes(storageUsed)} of storage, kept privately in " +
                "app storage. A download you cancel or lose connection on " +
                "resumes from where it stopped.",
        )
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
    val subtitle = buildString {
        append("${model.params} · ${formatBytes(model.sizeBytes)}")
        append(" · ${model.description}")
        if (tooBigForRam) append(" May be too large for this device.")
    }
    ListItem(
        colors = transparentListColors(),
        headlineContent = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(model.displayName)
                if (model.gated && status !is DownloadStatus.Downloaded) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = "Requires a Hugging Face token",
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        supportingContent = { Text(subtitle) },
        leadingContent = if (selected && status is DownloadStatus.Downloaded) {
            { Icon(Icons.Outlined.Check, contentDescription = "Selected") }
        } else null,
        trailingContent = {
            when (status) {
                is DownloadStatus.Downloaded -> IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete ${model.displayName}")
                }
                is DownloadStatus.Downloading -> IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cancel download")
                }
                else -> {}
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    when (status) {
        is DownloadStatus.NotDownloaded -> Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            if (model.gated && !hasToken) {
                CaptionText("Add your Hugging Face token above to download this model.")
            } else {
                TextButton(onClick = onDownload, enabled = !downloadBusy) { Text("Download") }
            }
        }
        is DownloadStatus.Downloading -> DownloadProgress(status.bytes, status.total)
        is DownloadStatus.Paused -> Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            TextButton(onClick = onDownload, enabled = !downloadBusy) { Text("Resume") }
            Text(
                "Paused at ${formatBytes(status.bytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is DownloadStatus.Downloaded -> if (!selected) {
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = onSelect) { Text("Use this model") }
            }
        }
        is DownloadStatus.Failed -> Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            CaptionText(status.message, error = true)
            Row {
                when (status.reason) {
                    FailReason.LICENSE_NOT_ACCEPTED ->
                        TextButton(onClick = onOpenLicense) { Text("Open model page") }
                    FailReason.GATED_NO_TOKEN -> {}
                    else -> TextButton(onClick = onDownload, enabled = !downloadBusy) { Text("Retry") }
                }
            }
        }
    }
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
