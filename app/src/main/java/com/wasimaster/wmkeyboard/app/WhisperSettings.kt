package com.wasimaster.wmkeyboard.app

import android.content.Context
import android.net.ConnectivityManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperCatalog
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperDownloadManager
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperDownloadManager.DownloadStatus
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperModel
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperStore
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Downloads above this size ask before using mobile data. */
private const val WHISPER_METERED_CONFIRM_BYTES = 150_000_000L

/**
 * The offline-Whisper model manager: the downloadable catalog with live
 * progress and a "use this model" selection. Download state lives in
 * [WhisperDownloadManager], so navigating away or rotating never loses an
 * in-flight download. Shown only in the full flavor (gated by the caller).
 */
@Composable
internal fun WhisperModelManager(repository: SettingsRepository, settings: KeyboardSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filesDir = context.filesDir
    val states by WhisperDownloadManager.states.collectAsState()
    var storageUsed by remember { mutableStateOf(0L) }
    var orphanBytes by remember { mutableStateOf(0L) }
    var meteredPending by remember { mutableStateOf<WhisperModel?>(null) }

    LaunchedEffect(Unit) { WhisperDownloadManager.refresh(filesDir) }
    LaunchedEffect(states) {
        storageUsed = withContext(Dispatchers.IO) { WhisperStore.totalBytesUsed(filesDir) }
        orphanBytes = withContext(Dispatchers.IO) { WhisperStore.orphanBytes(filesDir) }
        // The only model on disk needs no selection step: adopt it — covers
        // both "first download just finished" and "selection was deleted".
        if (WhisperStore.selectedModel(filesDir, settings.whisper.modelId) == null) {
            WhisperStore.soleDownloadedId(filesDir)?.let { repository.setWhisperModelId(it) }
        }
    }

    fun startDownload(model: WhisperModel) {
        WhisperDownloadManager.start(filesDir, model)
    }

    fun requestDownload(model: WhisperModel) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (model.sizeBytes >= WHISPER_METERED_CONFIRM_BYTES && cm.isActiveNetworkMetered) {
            meteredPending = model
        } else {
            startDownload(model)
        }
    }

    val onDisk = WhisperCatalog.models.filter {
        (states[it.id] ?: DownloadStatus.NotDownloaded) is DownloadStatus.Downloaded
    }
    val yours = onDisk.sortedByDescending { it.id == settings.whisper.modelId }
    val available = WhisperCatalog.models - onDisk.toSet()

    @Composable
    fun catalogRow(model: WhisperModel) {
        WhisperCatalogRow(
            model = model,
            status = states[model.id] ?: DownloadStatus.NotDownloaded,
            selected = settings.whisper.modelId == model.id ||
                (settings.whisper.modelId.isBlank() && WhisperStore.soleDownloadedId(filesDir) == model.id),
            downloadBusy = WhisperDownloadManager.isBusy,
            onDownload = { requestDownload(model) },
            onCancel = { WhisperDownloadManager.cancel() },
            onSelect = { scope.launch { repository.setWhisperModelId(model.id) } },
            onDelete = {
                WhisperDownloadManager.delete(filesDir, model)
                if (settings.whisper.modelId == model.id) {
                    scope.launch { repository.setWhisperModelId("") }
                }
            },
        )
    }

    if (yours.isNotEmpty()) {
        WhisperSectionHeader("Your voice models", formatBytes(yours.sumOf { it.sizeBytes }))
    }
    SettingsGroup {
        for (model in yours) item { catalogRow(model) }
    }
    if (yours.isEmpty()) {
        CaptionText("No voice models yet — download one below to dictate fully offline.")
    }

    SettingsGroup("Available to download") {
        for (model in available) item { catalogRow(model) }
    }
    CaptionText(
        "Multilingual models cover ~99 languages and can translate speech to " +
            "English; the single-language models are smaller and faster when you " +
            "only ever dictate one language. Bigger models are more accurate but " +
            "slower to download and transcribe.",
    )

    if (storageUsed > 0) {
        CaptionText(
            "Voice models use ${formatBytes(storageUsed)} of storage, kept privately " +
                "in app storage. A download you cancel or lose connection on " +
                "resumes from where it stopped.",
        )
    }
    if (orphanBytes > 0) {
        CaptionText(
            "${formatBytes(orphanBytes)} belongs to models no longer in the catalog.",
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { WhisperStore.deleteOrphans(filesDir) }
                    orphanBytes = 0
                    storageUsed = withContext(Dispatchers.IO) { WhisperStore.totalBytesUsed(filesDir) }
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

@Composable
private fun WhisperSectionHeader(title: String, trailing: String) {
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
private fun WhisperCatalogRow(
    model: WhisperModel,
    status: DownloadStatus,
    selected: Boolean,
    downloadBusy: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val downloaded = status is DownloadStatus.Downloaded
    val subtitle = buildString {
        append("${model.sizeLabel} · ${model.languageLabel} · ${formatBytes(model.sizeBytes)}")
        append(" · ${model.description}")
    }
    val activeSelection = selected && downloaded
    val rowClick = if (downloaded && !selected) onSelect else null
    WhisperSelectionHighlight(selected = activeSelection, onClick = rowClick) {
        ListItem(
            colors = transparentListColors(),
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        model.displayName,
                        fontWeight = if (activeSelection) FontWeight.SemiBold else null,
                    )
                    WhisperTierBadge(model.tier)
                }
            },
            supportingContent = {
                Column {
                    if (activeSelection) {
                        Text(
                            "In use",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(subtitle)
                }
            },
            leadingContent = if (activeSelection) {
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
                    is DownloadStatus.Paused -> TextButton(
                        onClick = onDownload,
                        enabled = !downloadBusy,
                    ) { Text("Resume") }
                    is DownloadStatus.NotDownloaded, is DownloadStatus.Failed ->
                        TextButton(
                            onClick = onDownload,
                            enabled = !downloadBusy,
                        ) { Text(if (status is DownloadStatus.Failed) "Retry" else "Download") }
                }
            },
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(modifier = Modifier.animateContentSize()) {
            when (status) {
                is DownloadStatus.NotDownloaded -> Unit
                is DownloadStatus.Downloading -> WhisperDownloadProgress(status.bytes, status.total)
                is DownloadStatus.Paused -> CaptionText("Paused at ${formatBytes(status.bytes)}")
                is DownloadStatus.Downloaded -> if (!selected) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TextButton(onClick = onSelect) { Text("Use this model") }
                    }
                }
                is DownloadStatus.Failed -> CaptionText(status.message, error = true)
            }
        }
    }
}

@Composable
private fun WhisperSelectionHighlight(
    selected: Boolean,
    onClick: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else Color.Transparent,
        animationSpec = tween(300),
        label = "whisperRowBackground",
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

@Composable
private fun WhisperTierBadge(tier: WhisperTier) {
    val label = tier.badge ?: return
    val recommended = tier == WhisperTier.RECOMMENDED
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = if (recommended) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (recommended) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun WhisperDownloadProgress(bytes: Long, total: Long) {
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
