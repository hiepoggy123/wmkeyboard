package com.wasimaster.wmkeyboard.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.core.stickers.CustomSticker
import com.wasimaster.wmkeyboard.core.stickers.StickerAddResult
import com.wasimaster.wmkeyboard.core.stickers.StickerImage
import com.wasimaster.wmkeyboard.core.stickers.StickerImportResult
import com.wasimaster.wmkeyboard.core.stickers.StickerPack
import com.wasimaster.wmkeyboard.core.stickers.StickerPackFile
import com.wasimaster.wmkeyboard.core.stickers.StickerPackStore
import com.wasimaster.wmkeyboard.core.util.requireInputStream
import com.wasimaster.wmkeyboard.ime.ui.rememberMediaImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The sticker packs the user owns, and everything that edits them.
 *
 * The keyboard can only view, send, and save-a-search-result into a pack —
 * an IME has no activity window, so a photo picker, a rename field or a
 * delete confirmation all have to live here.
 */
@Composable
internal fun StickerPacksScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { StickerPackStore.get(context) }
    // Bumped after every mutation; the store is a plain file-backed object with
    // no flow of its own, so this is what re-reads it.
    var revision by remember { mutableIntStateOf(0) }
    val packs = remember(revision) { store.packs() }

    var newPackName by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<StickerPack?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    // CreateDocument cannot carry a payload, so the pack waiting to be written
    // is parked here between launching the picker and its result.
    var pendingExport by remember { mutableStateOf<StickerPack?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(StickerPackFile.MIME_TYPE),
    ) { uri ->
        val pack = pendingExport
        pendingExport = null
        if (uri == null || pack == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        StickerPackFile.write(
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
                    context.contentResolver.requireInputStream(uri)
                        .use { StickerPackFile.import(it, store) }
                }.getOrDefault(StickerImportResult.Failed)
            }
            revision++
            message = when (result) {
                is StickerImportResult.Imported -> buildString {
                    append("Imported ${result.pack.name} — ${result.pack.stickers.size} stickers.")
                    if (result.repairs.isNotEmpty()) {
                        append("\n\nChanged on the way in:")
                        for (line in result.repairs) append("\n• $line")
                    }
                }
                StickerImportResult.NotAStickerPack -> "That file is not a WMKeyboard sticker pack."
                is StickerImportResult.NoStickers -> buildString {
                    append("No stickers could be read out of that pack.")
                    for (line in result.repairs.take(MAX_SHOWN_REPAIRS)) append("\n• $line")
                    val extra = result.repairs.size - MAX_SHOWN_REPAIRS
                    if (extra > 0) append("\n• …and $extra more")
                }
                StickerImportResult.TooManyPacks ->
                    "You already have ${StickerPackStore.MAX_PACKS} packs. Delete one first."
                StickerImportResult.Failed -> "That file could not be read."
            }
        }
    }

    CaptionText(
        "Your own stickers, sent from the sticker tool's “My stickers” tab. " +
            "Stills are resized to 512×512 WebP so they arrive as real stickers in " +
            "WhatsApp; animated GIFs are kept as they are and send as images.",
    )

    SettingsGroup("Your packs") {
        if (packs.isEmpty()) {
            item {
                ListItem(
                    colors = transparentListColors(),
                    headlineContent = { Text("No sticker packs yet") },
                    supportingContent = { Text("Make one below, or import a pack someone shared.") },
                )
            }
        }
        for (pack in packs) {
            item {
                StickerPackRow(
                    pack = pack,
                    fileFor = { store.fileFor(pack.id, it) },
                    onOpen = { onNavigate("sticker_pack/${pack.id}") },
                    onExport = {
                        pendingExport = pack
                        exportLauncher.launch(StickerPackFile.fileName(pack))
                    },
                    onDelete = { confirmDelete = pack },
                )
            }
        }
    }

    SettingsGroup {
        item {
            ListItem(
                colors = transparentListColors(),
                modifier = Modifier.clickable { newPackName = "" },
                leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                headlineContent = { Text("New pack") },
                supportingContent = { Text("Then add stickers from your photos") },
            )
        }
        item {
            ListItem(
                colors = transparentListColors(),
                modifier = Modifier.clickable {
                    importLauncher.launch(StickerPackFile.IMPORT_MIME_TYPES)
                },
                leadingContent = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
                headlineContent = { Text("Import a pack") },
                supportingContent = { Text("Opens a .wmstickers file someone shared") },
            )
        }
    }

    newPackName?.let { draft ->
        NameDialog(
            title = "New pack",
            value = draft,
            onValueChange = { newPackName = it },
            onDismiss = { newPackName = null },
            onConfirm = {
                val created = store.createPack(draft)
                revision++
                newPackName = null
                if (created == null) {
                    message = "You already have ${StickerPackStore.MAX_PACKS} packs."
                } else {
                    onNavigate("sticker_pack/${created.id}")
                }
            },
        )
    }

    confirmDelete?.let { pack ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${pack.name}?") },
            text = {
                Text(
                    "Its ${pack.stickers.size} sticker" +
                        (if (pack.stickers.size == 1) "" else "s") +
                        " are deleted from this device. Export the pack first if you " +
                        "want to keep a copy.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.deletePack(pack.id)
                    revision++
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
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
}

@Composable
private fun StickerPackRow(
    pack: StickerPack,
    fileFor: (CustomSticker) -> File?,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val loader = rememberMediaImageLoader()
    ListItem(
        colors = transparentListColors(),
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(pack.name) },
        supportingContent = {
            Column {
                Text(
                    if (pack.stickers.isEmpty()) "Empty"
                    else "${pack.stickers.size} sticker" + if (pack.stickers.size == 1) "" else "s",
                )
                if (pack.stickers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (sticker in pack.stickers.take(6)) {
                            AsyncImage(
                                model = fileFor(sticker),
                                contentDescription = null,
                                imageLoader = loader,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                    }
                }
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onExport) {
                    Icon(Icons.Outlined.Share, contentDescription = "Export ${pack.name}")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete ${pack.name}")
                }
            }
        },
    )
}

/** One pack: rename it, add stickers from photos, edit or remove each one. */
@Composable
internal fun StickerPackScreen(packId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { StickerPackStore.get(context) }
    var revision by remember { mutableIntStateOf(0) }
    val pack = remember(revision) { store.pack(packId) }
    val allPacks = remember(revision) { store.packs() }
    val loader = rememberMediaImageLoader()

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CustomSticker?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                var added = 0
                var tooLarge = 0
                var unreadable = 0
                var full = false
                for (uri in uris) {
                    val bytes = runCatching {
                        context.contentResolver.requireInputStream(uri).use { it.readBytes() }
                    }.getOrNull()
                    if (bytes == null) {
                        unreadable++
                        continue
                    }
                    when (val processed = StickerImage.process(bytes)) {
                        is StickerImage.Result.Ok ->
                            when (store.addSticker(packId, processed.sticker)) {
                                is StickerAddResult.Added -> added++
                                StickerAddResult.PackFull -> full = true
                                else -> unreadable++
                            }
                        StickerImage.Result.TooLarge -> tooLarge++
                        StickerImage.Result.NotAnImage -> unreadable++
                    }
                    if (full) break
                }
                AddOutcome(added, tooLarge, unreadable, full)
            }
            busy = false
            revision++
            message = outcome.describe()
        }
    }

    if (pack == null) {
        CaptionText("This pack is gone.")
        return
    }

    CaptionText(
        "Stills become 512×512 WebP under 100 KB — WhatsApp's sticker spec. " +
            "Animated GIFs are stored untouched, because Android can't encode " +
            "animated WebP; those always send as images.",
    )

    SettingsGroup("Pack") {
        item {
            ListItem(
                colors = transparentListColors(),
                modifier = Modifier.clickable { renaming = pack.name },
                headlineContent = { Text(pack.name) },
                supportingContent = { Text("Tap to rename") },
            )
        }
    }

    SettingsGroup {
        item {
            ListItem(
                colors = transparentListColors(),
                modifier = Modifier.clickable(enabled = !busy) {
                    pickLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                headlineContent = { Text(if (busy) "Adding…" else "Add stickers") },
                supportingContent = {
                    Text(
                        "${pack.stickers.size} of ${StickerPackStore.MAX_STICKERS_PER_PACK} used",
                    )
                },
            )
        }
    }

    if (pack.stickers.isEmpty()) {
        SettingsGroup {
            item {
                ListItem(
                    colors = transparentListColors(),
                    headlineContent = { Text("No stickers in this pack") },
                    supportingContent = {
                        Text("Add some from your photos, or long-press a Klipy or GIPHY sticker in the keyboard.")
                    },
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(((pack.stickers.size + 2) / 3 * 104).coerceAtMost(640).dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(pack.stickers, key = { it.id }) { sticker ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { editing = sticker },
                ) {
                    AsyncImage(
                        model = store.fileFor(packId, sticker),
                        contentDescription = sticker.name.ifBlank { "Sticker" },
                        imageLoader = loader,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    renaming?.let { draft ->
        NameDialog(
            title = "Rename pack",
            value = draft,
            onValueChange = { renaming = it },
            onDismiss = { renaming = null },
            onConfirm = {
                store.renamePack(packId, draft)
                revision++
                renaming = null
            },
        )
    }

    editing?.let { sticker ->
        StickerEditDialog(
            sticker = sticker,
            otherPacks = allPacks.filter { it.id != packId },
            onDismiss = { editing = null },
            onSave = { name, emojis ->
                store.updateSticker(packId, sticker.id, name, emojis)
                revision++
                editing = null
            },
            onMove = { targetId ->
                if (!store.moveSticker(packId, sticker.id, targetId)) {
                    message = "That pack is full."
                }
                revision++
                editing = null
            },
            onReorder = { delta ->
                store.reorderSticker(packId, sticker.id, delta)
                revision++
                editing = null
            },
            onDelete = {
                store.removeSticker(packId, sticker.id)
                revision++
                editing = null
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
}

/** How many photos one trip through the picker may add. */
private const val MAX_PICK = 30

/** A pack that dropped every sticker has one reason per sticker; show a few. */
private const val MAX_SHOWN_REPAIRS = 5

private data class AddOutcome(
    val added: Int,
    val tooLarge: Int,
    val unreadable: Int,
    val packFull: Boolean,
) {
    fun describe(): String = buildString {
        append(if (added == 0) "Nothing added." else "Added $added sticker${if (added == 1) "" else "s"}.")
        if (tooLarge > 0) append(" $tooLarge were too large (animated files are capped at 2 MB).")
        if (unreadable > 0) append(" $unreadable could not be read as an image.")
        if (packFull) append(" The pack is now full.")
    }
}

@Composable
private fun StickerEditDialog(
    sticker: CustomSticker,
    otherPacks: List<StickerPack>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
    onMove: (String) -> Unit,
    onReorder: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(sticker.id) { mutableStateOf(sticker.name) }
    var tags by remember(sticker.id) { mutableStateOf(sticker.emojis.joinToString(" ")) }
    var moveOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit sticker") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Emoji tags") },
                    supportingText = { Text("Searched alongside the name, e.g. 😂 🐱") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { onReorder(-1) }) { Text("Move up") }
                    TextButton(onClick = { onReorder(1) }) { Text("Move down") }
                }
                if (otherPacks.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { moveOpen = true }) { Text("Move to another pack") }
                        DropdownMenu(expanded = moveOpen, onDismissRequest = { moveOpen = false }) {
                            for (pack in otherPacks) {
                                DropdownMenuItem(
                                    text = {
                                        Text(pack.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    onClick = {
                                        moveOpen = false
                                        onMove(pack.id)
                                    },
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onDelete) { Text("Delete this sticker") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name, tags.split(" ").map { it.trim() }.filter { it.isNotEmpty() })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = value.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
