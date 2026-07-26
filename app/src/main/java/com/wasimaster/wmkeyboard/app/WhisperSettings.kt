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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.RadioButton
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
 * The offline-Whisper model manager. Two things are on this screen: which model
 * transcribes each language you type in, and what you can download.
 *
 * There is deliberately no global "use this model" switch. Dictation resolves the
 * model from the language of the active layout — the same way the system
 * recognizer follows the layout's locale — so the meaningful choice is per
 * language, and that is what [WhisperRoutingCard] edits.
 *
 * Download state lives in [WhisperDownloadManager], so navigating away or
 * rotating never loses an in-flight download. Shown only in the full flavor
 * (gated by the caller).
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
    var routingFor by remember { mutableStateOf<LanguageDef?>(null) }
    var browseOpen by remember { mutableStateOf(false) }
    var sizeFilter by remember { mutableStateOf<WhisperSize?>(null) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) { WhisperDownloadManager.refresh(filesDir) }
    LaunchedEffect(states) {
        storageUsed = withContext(Dispatchers.IO) { WhisperStore.totalBytesUsed(filesDir) }
        orphanBytes = withContext(Dispatchers.IO) { WhisperStore.orphanBytes(filesDir) }
        // The only model on disk needs no choosing: adopt it as the fallback —
        // covers both "first download just finished" and "the fallback was deleted".
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
    // Language id → the model that will actually transcribe it, so both the
    // routing card and the "used for" chips read from one answer.
    val routing = settings.enabledLanguages.associate { language ->
        language.id to WhisperStore.pickForLanguage(
            onDisk, language.id, settings.whisper.modelId, settings.whisper.modelByLang,
        )
    }
    val suggestions = WhisperCatalog.recommendedFor(enabledCodes) - onDisk.toSet()

    @Composable
    fun modelRow(model: WhisperModel) {
        WhisperModelRow(
            model = model,
            status = states[model.id] ?: DownloadStatus.NotDownloaded,
            downloadBusy = WhisperDownloadManager.isBusy,
            enabledCodes = enabledCodes,
            usedFor = settings.enabledLanguages
                .filter { routing[it.id]?.id == model.id }
                .map { it.englishName },
            isFallback = model.id == settings.whisper.modelId,
            expanded = expanded[model.id] == true,
            onToggleExpand = { expanded[model.id] = expanded[model.id] != true },
            onDownload = { requestDownload(model) },
            onCancel = { WhisperDownloadManager.cancel() },
            onDelete = {
                WhisperDownloadManager.delete(filesDir, model)
                scope.launch {
                    repository.clearWhisperModelAssignments(model.id)
                    if (settings.whisper.modelId == model.id) repository.setWhisperModelId("")
                }
            },
        )
    }

    WhisperRoutingCard(
        languages = settings.enabledLanguages,
        routing = routing,
        pinned = settings.whisper.modelByLang,
        anyDownloaded = onDisk.isNotEmpty(),
        onEdit = { routingFor = it },
    )

    WhisperSectionHeader(
        "Your voice models",
        if (onDisk.isEmpty()) "" else formatBytes(onDisk.sumOf { it.sizeBytes }),
    )
    if (onDisk.isEmpty()) {
        CaptionText("Nothing downloaded yet — pick one below to dictate fully offline.")
    } else {
        SettingsGroup {
            for (model in onDisk) item { modelRow(model) }
        }
    }

    if (suggestions.isNotEmpty()) {
        WhisperSectionHeader("Suggested for your languages", "")
        SettingsGroup {
            for (model in suggestions) item { modelRow(model) }
        }
    }

    WhisperBrowseSection(
        open = browseOpen,
        onToggle = { browseOpen = !browseOpen },
        visible = WhisperCatalog.visibleFor(enabledCodes),
        sizeFilter = sizeFilter,
        onSizeFilter = { sizeFilter = it },
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

    routingFor?.let { language ->
        WhisperRoutingDialog(
            language = language,
            downloaded = onDisk,
            resolved = routing[language.id],
            pinnedId = settings.whisper.modelByLang[language.id],
            onPick = { id ->
                scope.launch { repository.setWhisperModelForLanguage(language.id, id) }
                routingFor = null
            },
            onDismiss = { routingFor = null },
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

/**
 * One row per enabled language, each naming the model that will transcribe it.
 * This replaces the old single "use this model" selection: dictation picks the
 * model from the language of the active layout, so the choice only means anything
 * per language.
 *
 * The failure this surfaces is a model transcribing a language it was not built
 * for — that produces confident wrong words rather than an error, so a language
 * its model cannot handle is called out in the error colour.
 */
@Composable
private fun WhisperRoutingCard(
    languages: List<LanguageDef>,
    routing: Map<String, WhisperModel?>,
    pinned: Map<String, String>,
    anyDownloaded: Boolean,
    onEdit: (LanguageDef) -> Unit,
) {
    if (languages.isEmpty()) return
    SettingsGroup("Model per language") {
        for (language in languages) {
            item {
                val code = WhisperLanguages.codeForLanguage(language.id)
                val model = routing[language.id]
                val covered = code != null && model != null && model.covers(code)
                val detail = when {
                    code == null ->
                        "Whisper has no model for this language — dictation falls back to " +
                            "the system recognizer."
                    model == null -> "No model downloaded yet."
                    !covered ->
                        "${model.displayName} cannot transcribe this language — it will " +
                            "produce wrong words. Download one that covers it."
                    pinned[language.id] == model.id -> "${model.displayName} · your choice"
                    else -> "${model.displayName} · chosen automatically"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .then(
                            if (code != null && anyDownloaded) {
                                Modifier.clickable { onEdit(language) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(language.englishName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (code != null && model != null && !covered) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (code != null && anyDownloaded) {
                        Text(
                            "Change",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
    CaptionText(
        "Dictation uses the language of the layout you are typing on. Models built " +
            "for one language, and the multi-language ones that accept a language " +
            "input, are told which language to expect instead of guessing it from a " +
            "short clip.",
    )
}

/** Picks the model for one language: automatic, or a specific downloaded one. */
@Composable
private fun WhisperRoutingDialog(
    language: LanguageDef,
    downloaded: List<WhisperModel>,
    resolved: WhisperModel?,
    pinnedId: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val code = WhisperLanguages.codeForLanguage(language.id) ?: return
    // Ones that can actually do this language first; the rest stay pickable but
    // are labelled, since choosing one is a mistake worth naming rather than hiding.
    val ordered = WhisperCatalog.rankedFor(code, downloaded) +
        downloaded.filterNot { it.covers(code) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model for ${language.englishName}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                WhisperRoutingOption(
                    title = "Automatic",
                    detail = resolved?.let { "Currently ${it.displayName}" }
                        ?: "No model downloaded yet",
                    selected = pinnedId == null,
                    onClick = { onPick("") },
                )
                for (model in ordered) {
                    WhisperRoutingOption(
                        title = model.displayName,
                        detail = when {
                            !model.covers(code) -> "Does not cover ${language.englishName}"
                            model.fixedLang == code -> "Built for ${language.englishName} only"
                            model.selectableLang -> "Told to expect ${language.englishName}"
                            else -> "Detects the language itself"
                        },
                        selected = pinnedId == model.id,
                        warn = !model.covers(code),
                        onClick = { onPick(model.id) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun WhisperRoutingOption(
    title: String,
    detail: String,
    selected: Boolean,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (warn) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The catalog behind an expander, split by whether a model handles any language
 * or exactly one. Single-language entries only appear for languages that are
 * actually enabled — see [WhisperCatalog.visibleFor] — so this list grows with
 * the Languages screen instead of listing graphs nothing would ever route to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhisperBrowseSection(
    open: Boolean,
    onToggle: () -> Unit,
    visible: List<WhisperModel>,
    sizeFilter: WhisperSize?,
    onSizeFilter: (WhisperSize?) -> Unit,
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
            if (open) "All ${visible.size} models" else "Browse all ${visible.size} models",
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

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        for (size in WhisperSize.entries) {
            FilterChip(
                selected = sizeFilter == size,
                onClick = { onSizeFilter(if (sizeFilter == size) null else size) },
                label = { Text(size.label) },
            )
        }
    }

    val shown = visible.filter { sizeFilter == null || it.size == sizeFilter }
    val anyLanguage = shown.filter { it.fixedLang == null }
    val oneLanguage = shown.filter { it.fixedLang != null }
    if (shown.isEmpty()) {
        CaptionText("No model matches that size.")
    }
    if (anyLanguage.isNotEmpty()) {
        WhisperSectionHeader("Any language", "")
        SettingsGroup {
            for (model in anyLanguage) item { row(model) }
        }
    }
    if (oneLanguage.isNotEmpty()) {
        WhisperSectionHeader("Built for one language", "")
        SettingsGroup {
            for (model in oneLanguage) item { row(model) }
        }
    }
    CaptionText(
        "Sizes go Tiny → Large: bigger is more accurate but slower to download and " +
            "to transcribe, and Medium and Large need close to a gigabyte of memory " +
            "while they run. A model built for one language beats a multi-language " +
            "model of the same size at that language. Single-language models appear " +
            "here once you enable the language on the Languages screen.",
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
 * together in a paragraph-long subtitle, and details on tap. Which languages a
 * downloaded model serves is shown here but chosen in [WhisperRoutingCard].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhisperModelRow(
    model: WhisperModel,
    status: DownloadStatus,
    downloadBusy: Boolean,
    enabledCodes: Set<String>,
    usedFor: List<String>,
    isFallback: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val inUse = status is DownloadStatus.Downloaded && usedFor.isNotEmpty()
    val background by animateColorAsState(
        if (inUse) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else Color.Transparent,
        animationSpec = tween(300),
        label = "whisperRowBackground",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onToggleExpand)
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
                    fontWeight = if (inUse) FontWeight.SemiBold else null,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (inUse) {
                        WhisperChip("Used for ${usedFor.joinToString(", ")}", WhisperChipTone.PRIMARY)
                    }
                    if (model.tier == WhisperTier.RECOMMENDED && !inUse) {
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

        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) {
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WhisperLanguageDetail(model, enabledCodes, isFallback)
            }
        }

        when (status) {
            is DownloadStatus.NotDownloaded, is DownloadStatus.Downloaded -> Unit
            is DownloadStatus.Downloading -> WhisperDownloadProgress(status.bytes, status.total)
            is DownloadStatus.Paused -> CaptionText("Paused at ${formatBytes(status.bytes)}")
            is DownloadStatus.Failed -> CaptionText(status.message, error = true)
        }
    }
}

/** The expanded row's language line: what it covers, and how that lands against your set. */
@Composable
private fun WhisperLanguageDetail(
    model: WhisperModel,
    enabledCodes: Set<String>,
    isFallback: Boolean,
) {
    val lines = buildList {
        if (model.langCodes.size > 1) {
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
        if (isFallback) add("Used for any language you have not chosen a model for.")
    }
    if (lines.isEmpty()) return
    Text(
        lines.joinToString(" "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(top = 6.dp),
    )
}

private enum class WhisperChipTone { NEUTRAL, PRIMARY }

/** A small fact chip — the row's metadata reads as chips instead of a run-on subtitle. */
@Composable
private fun WhisperChip(text: String, tone: WhisperChipTone) {
    val container = when (tone) {
        WhisperChipTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        WhisperChipTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when (tone) {
        WhisperChipTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        WhisperChipTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
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
