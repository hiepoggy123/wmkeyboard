package com.wasimaster.wmkeyboard.app

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.core.script.LanguageDef
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperCatalog
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperDownloadManager
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperDownloadManager.DownloadStatus
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperLanguages
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperModel
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperSize
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperStore
import com.wasimaster.wmkeyboard.core.voice.whisper.WhisperTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Downloads above this size ask before using mobile data. */
private const val WHISPER_METERED_CONFIRM_BYTES = 150_000_000L

/**
 * The offline-Whisper model manager: what you have, what suits the languages
 * you actually type in, and the full catalog behind one expander so 25 entries
 * do not land on screen at once. Download state lives in
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
    var browseOpen by remember { mutableStateOf(false) }
    var sizeFilter by remember { mutableStateOf<WhisperSize?>(null) }
    var myLanguagesOnly by remember { mutableStateOf(true) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

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

    // Which Whisper languages the user's enabled layouts actually amount to.
    val enabledCodes = remember(settings.enabledLanguages) {
        settings.enabledLanguages.mapNotNullTo(LinkedHashSet()) {
            WhisperLanguages.codeForLanguage(it.id)
        }
    }

    val onDisk = WhisperCatalog.models.filter {
        (states[it.id] ?: DownloadStatus.NotDownloaded) is DownloadStatus.Downloaded
    }
    val yours = onDisk.sortedByDescending { it.id == settings.whisper.modelId }
    val selectedId = settings.whisper.modelId.ifBlank { WhisperStore.soleDownloadedId(filesDir) }
    val inUse = onDisk.firstOrNull { it.id == selectedId }
    val suggestions = WhisperCatalog.recommendedFor(enabledCodes) - onDisk.toSet()

    @Composable
    fun modelRow(model: WhisperModel) {
        WhisperModelRow(
            model = model,
            status = states[model.id] ?: DownloadStatus.NotDownloaded,
            selected = model.id == selectedId,
            downloadBusy = WhisperDownloadManager.isBusy,
            enabledCodes = enabledCodes,
            expanded = expanded[model.id] == true,
            onToggleExpand = { expanded[model.id] = expanded[model.id] != true },
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

    WhisperSectionHeader(
        "Your voice models",
        if (yours.isEmpty()) "" else formatBytes(yours.sumOf { it.sizeBytes }),
    )
    if (yours.isEmpty()) {
        CaptionText("Nothing downloaded yet — pick one below to dictate fully offline.")
    } else {
        SettingsGroup {
            for (model in yours) item { modelRow(model) }
        }
    }

    WhisperCoverageCard(inUse, settings.enabledLanguages)

    if (suggestions.isNotEmpty()) {
        WhisperSectionHeader("Suggested for your languages", "")
        SettingsGroup {
            for (model in suggestions) item { modelRow(model) }
        }
    }

    WhisperBrowseSection(
        open = browseOpen,
        onToggle = { browseOpen = !browseOpen },
        total = WhisperCatalog.models.size,
        sizeFilter = sizeFilter,
        onSizeFilter = { sizeFilter = it },
        myLanguagesOnly = myLanguagesOnly,
        onMyLanguagesOnly = { myLanguagesOnly = it },
        enabledCodes = enabledCodes,
        row = { modelRow(it) },
    )

    if (storageUsed > 0) {
        CaptionText(
            "Voice models use ${formatBytes(storageUsed)} of storage, kept privately " +
                "in app storage. A download you cancel or lose connection on " +
                "resumes from where it stopped.",
        )
    }
    if (orphanBytes > 0) {
        CaptionText("${formatBytes(orphanBytes)} belongs to models no longer in the catalog.")
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

/**
 * The "does this model actually speak my languages" answer, as one chip per
 * enabled language tinted by whether the model in use covers it. This is the
 * trap worth surfacing: a grouped or single-language model transcribes a
 * language it was not built for into confident nonsense rather than failing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhisperCoverageCard(inUse: WhisperModel?, languages: List<LanguageDef>) {
    if (languages.isEmpty()) return
    // Classified up front rather than while emitting chips: the row's content
    // lambda can recompose on its own, and appending to a list from there would
    // grow it every pass.
    val codes = languages.map { it to WhisperLanguages.codeForLanguage(it.id) }
    val unknown = codes.filter { it.second == null }.map { it.first.englishName }
    val missing = if (inUse == null) emptyList() else {
        codes.filter { (_, code) -> code != null && !inUse.covers(code) }.map { it.first.englishName }
    }
    val note = when {
        inUse == null -> "Download a model and these are the languages it needs to handle."
        missing.isEmpty() && unknown.isEmpty() ->
            "${inUse.displayName} handles every language you type in."
        missing.isNotEmpty() ->
            "${inUse.displayName} does not cover ${missing.joinToString(", ")} — " +
                "dictating those produces wrong words rather than an error. " +
                "The plain multilingual models cover all 99 languages Whisper knows."
        else ->
            "Whisper has no model for ${unknown.joinToString(", ")}; " +
                "use the system recognizer for those."
    }

    SettingsGroup("Your languages") {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for ((language, code) in codes) {
                        val covered = code != null && (inUse == null || inUse.covers(code))
                        WhisperChip(
                            language.englishName,
                            tone = if (covered) WhisperChipTone.NEUTRAL else WhisperChipTone.WARN,
                        )
                    }
                }
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (missing.isNotEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/**
 * The full catalog behind an expander, with a size filter and a "only my
 * languages" switch — 25 entries is a browse list, not something to scroll past
 * on the way to the rest of voice settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhisperBrowseSection(
    open: Boolean,
    onToggle: () -> Unit,
    total: Int,
    sizeFilter: WhisperSize?,
    onSizeFilter: (WhisperSize?) -> Unit,
    myLanguagesOnly: Boolean,
    onMyLanguagesOnly: (Boolean) -> Unit,
    enabledCodes: Set<String>,
    row: @Composable (WhisperModel) -> Unit,
) {
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "whisperBrowseChevron")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            if (open) "All $total models" else "Browse all $total models",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            contentDescription = if (open) "Collapse the catalogue" else "Expand the catalogue",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).rotate(turn),
        )
    }
    if (!open) return

    // Filtering by language only means anything once Whisper knows the language.
    val canFilterByLanguage = enabledCodes.isNotEmpty()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        if (canFilterByLanguage) {
            FilterChip(
                selected = myLanguagesOnly,
                onClick = { onMyLanguagesOnly(!myLanguagesOnly) },
                label = { Text("My languages") },
            )
        }
        for (size in WhisperSize.entries) {
            FilterChip(
                selected = sizeFilter == size,
                onClick = { onSizeFilter(if (sizeFilter == size) null else size) },
                label = { Text(size.label) },
            )
        }
    }

    val shown = WhisperCatalog.models.filter { model ->
        (sizeFilter == null || model.size == sizeFilter) &&
            (!myLanguagesOnly || !canFilterByLanguage || model.coverageOf(enabledCodes) > 0)
    }
    if (shown.isEmpty()) {
        CaptionText("No model matches those filters.")
    } else {
        SettingsGroup {
            for (model in shown) item { row(model) }
        }
    }
    CaptionText(
        "Sizes go Tiny → Large: bigger is more accurate but slower to download " +
            "and to transcribe, and Medium and Large need a lot of memory. " +
            "Models marked Recommended are the ones checked on this keyboard; " +
            "the rest come straight from the public conversions and are untested here.",
    )
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
        if (trailing.isNotEmpty()) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One catalog entry: name, a chip row carrying the facts that used to run
 * together in a paragraph-long subtitle, and details on tap. A downloaded row
 * behaves like a radio option (tap selects); an undownloaded one expands.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhisperModelRow(
    model: WhisperModel,
    status: DownloadStatus,
    selected: Boolean,
    downloadBusy: Boolean,
    enabledCodes: Set<String>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val downloaded = status is DownloadStatus.Downloaded
    val activeSelection = selected && downloaded
    val showDetail = expanded || activeSelection
    val background by animateColorAsState(
        if (activeSelection) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else Color.Transparent,
        animationSpec = tween(300),
        label = "whisperRowBackground",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable { if (downloaded && !selected) onSelect() else onToggleExpand() }
            .animateContentSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (activeSelection) FontWeight.SemiBold else null,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (activeSelection) WhisperChip("In use", WhisperChipTone.PRIMARY)
                    if (model.tier == WhisperTier.RECOMMENDED && !activeSelection) {
                        WhisperChip("Recommended", WhisperChipTone.PRIMARY)
                    }
                    WhisperChip(model.sizeLabel, WhisperChipTone.NEUTRAL)
                    WhisperChip(model.languageLabel, WhisperChipTone.NEUTRAL)
                    if (model.selectableLang) WhisperChip("Language forced", WhisperChipTone.NEUTRAL)
                    WhisperChip(formatBytes(model.sizeBytes), WhisperChipTone.NEUTRAL)
                }
            }
            when (status) {
                is DownloadStatus.Downloaded -> IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete ${model.displayName}")
                }
                is DownloadStatus.Downloading -> IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cancel download")
                }
                is DownloadStatus.Paused -> TextButton(onClick = onDownload, enabled = !downloadBusy) {
                    Text("Resume")
                }
                is DownloadStatus.NotDownloaded, is DownloadStatus.Failed ->
                    TextButton(onClick = onDownload, enabled = !downloadBusy) {
                        Text(if (status is DownloadStatus.Failed) "Retry" else "Download")
                    }
            }
        }

        if (showDetail) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) {
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WhisperLanguageDetail(model, enabledCodes)
            }
        }

        when (status) {
            is DownloadStatus.NotDownloaded -> Unit
            is DownloadStatus.Downloading -> WhisperDownloadProgress(status.bytes, status.total)
            is DownloadStatus.Paused -> CaptionText("Paused at ${formatBytes(status.bytes)}")
            is DownloadStatus.Downloaded -> if (!selected) {
                Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = onSelect) { Text("Use this model") }
                }
            }
            is DownloadStatus.Failed -> CaptionText(status.message, error = true)
        }
    }
}

/** The expanded row's language line: what it covers, and how that lands against your set. */
@Composable
private fun WhisperLanguageDetail(model: WhisperModel, enabledCodes: Set<String>) {
    val lines = buildList {
        if (model.langCodes.isNotEmpty() && model.langCodes.size > 1) {
            add("Covers ${WhisperLanguages.labels(model.langCodes).joinToString(", ")}.")
        }
        if (enabledCodes.isNotEmpty()) {
            val missed = enabledCodes.filterNot { model.covers(it) }
            if (missed.isEmpty()) {
                add("Handles all of your languages.")
            } else {
                add("Not for your ${WhisperLanguages.labels(missed).joinToString(", ")}.")
            }
        }
        if (model.supportsTranslate) add("Can translate speech to English.")
    }
    if (lines.isEmpty()) return
    Text(
        lines.joinToString(" "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(top = 6.dp),
    )
}

private enum class WhisperChipTone { NEUTRAL, PRIMARY, WARN }

/** A small fact chip — the row's metadata reads as chips instead of a run-on subtitle. */
@Composable
private fun WhisperChip(text: String, tone: WhisperChipTone) {
    val container = when (tone) {
        WhisperChipTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        WhisperChipTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
        WhisperChipTone.WARN -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (tone) {
        WhisperChipTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        WhisperChipTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
        WhisperChipTone.WARN -> MaterialTheme.colorScheme.onErrorContainer
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 7.dp, vertical = 3.dp),
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
