package com.wasimaster.wmkeyboard.app

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.theme.BuiltInThemes
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import com.wasimaster.wmkeyboard.core.theme.SeedSwatches
import com.wasimaster.wmkeyboard.core.theme.ThemeCodec
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.theme.themeFromSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---- shared helpers ----

private fun colorOf(argb: Long): Color = Color(argb.toInt())
private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun themeImagesDir(context: android.content.Context): File =
    File(context.filesDir, "theme_images").apply { mkdirs() }

/** Effective (fallback-resolved) colors the editor shows for nullable fields. */
private fun ThemeSpec.effectivePressed(): Long =
    pressedKeyBackground ?: lerp(colorOf(keyBackground), colorOf(accent), 0.40f).argb()

private fun ThemeSpec.effectivePopup(): Long =
    popupBackground ?: colorOf(keyText).copy(alpha = if (dark) 0.20f else 0.9f)
        .compositeOver(colorOf(boardBackground)).argb()

private fun ThemeSpec.effectiveToolCircle(): Long =
    toolCircleBackground ?: colorOf(keyText).copy(alpha = 0.14f)
        .compositeOver(colorOf(boardBackground)).argb()

// ---- theme gallery ----

@Composable
fun ThemesScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onEditTheme: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Export: pick a destination file, then write the pending theme's JSON
    // (with the background image embedded as base64) into it.
    var pendingExport by remember { mutableStateOf<ThemeSpec?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val theme = pendingExport
        pendingExport = null
        if (uri != null && theme != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(ThemeCodec.encode(theme.forExport()).toByteArray())
                    }
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)!!
                        .use { it.readBytes().decodeToString() }
                    val parsed = ThemeCodec.decode(text) ?: return@runCatching
                    val id = "custom_${System.currentTimeMillis()}"
                    val imagePath = parsed.backgroundImageBase64?.let { b64 ->
                        runCatching {
                            val file = File(themeImagesDir(context), "$id.img")
                            file.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                            file.absolutePath
                        }.getOrNull()
                    }
                    repository.upsertCustomTheme(
                        parsed.copy(
                            id = id,
                            backgroundImage = imagePath,
                            backgroundImageBase64 = null,
                        )
                    )
                    repository.setKeyboardThemeId(id)
                }
            }
        }
    }
    fun export(theme: ThemeSpec) {
        pendingExport = theme
        exportLauncher.launch("${theme.name.ifBlank { "theme" }}.wmtheme.json")
    }
    fun duplicateAndEdit(base: ThemeSpec) {
        scope.launch {
            val id = "custom_${System.currentTimeMillis()}"
            repository.upsertCustomTheme(base.copy(id = id, name = "${base.name} copy"))
            repository.setKeyboardThemeId(id)
            onEditTheme(id)
        }
    }

    SectionHeaderPublic("Mode")
    Text(
        "Applies to the settings app and to the Default theme.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = settings.themeMode == mode,
                onClick = { scope.launch { repository.setThemeMode(mode) } },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
            ) {
                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
    }
    ListItem(
        headlineContent = { Text("Material You colors") },
        supportingContent = { Text("Wallpaper-based dynamic color for the Default theme") },
        trailingContent = {
            Switch(
                checked = settings.dynamicColor,
                onCheckedChange = { scope.launch { repository.setDynamicColor(it) } },
            )
        },
    )

    SectionHeaderPublic("Themes")
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        Button(onClick = {
            scope.launch {
                val id = "custom_${System.currentTimeMillis()}"
                repository.upsertCustomTheme(
                    themeFromSeed(id, "My theme", SeedSwatches.first(), dark = true)
                )
                repository.setKeyboardThemeId(id)
                onEditTheme(id)
            }
        }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Create theme")
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Import")
        }
    }
    Spacer(Modifier.height(8.dp))

    // Default (system) card first, then customs, then built-ins — two per row.
    DefaultThemeCard(
        selected = settings.keyboardThemeId == DEFAULT_THEME_ID,
        onSelect = { scope.launch { repository.setKeyboardThemeId(DEFAULT_THEME_ID) } },
    )
    val customs = settings.customThemes.sortedBy { it.name.lowercase() }
    if (customs.isNotEmpty()) SectionHeaderPublic("Your themes")
    for (rowThemes in customs.chunked(2)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp)) {
            for (theme in rowThemes) {
                Box(modifier = Modifier.weight(1f)) {
                    ThemeCard(
                        theme = theme,
                        selected = settings.keyboardThemeId == theme.id,
                        onSelect = { scope.launch { repository.setKeyboardThemeId(theme.id) } },
                        onEdit = { onEditTheme(theme.id) },
                        onExport = { export(theme) },
                        onDelete = {
                            scope.launch {
                                theme.backgroundImage?.let { File(it).delete() }
                                repository.deleteCustomTheme(theme.id)
                            }
                        },
                    )
                }
            }
            if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    SectionHeaderPublic("Built-in")
    Text(
        "Tap to use. The pencil makes an editable copy.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
    )
    for (rowThemes in BuiltInThemes.chunked(2)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp)) {
            for (theme in rowThemes) {
                Box(modifier = Modifier.weight(1f)) {
                    ThemeCard(
                        theme = theme,
                        selected = settings.keyboardThemeId == theme.id,
                        onSelect = { scope.launch { repository.setKeyboardThemeId(theme.id) } },
                        onEdit = { duplicateAndEdit(theme) },
                        onExport = { export(theme) },
                        onDelete = null,
                    )
                }
            }
            if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(24.dp))
}

/** Strips local paths and embeds the image so the file works on any device. */
private fun ThemeSpec.forExport(): ThemeSpec {
    val b64 = backgroundImage?.let { path ->
        runCatching { Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP) }.getOrNull()
    }
    return copy(backgroundImage = null, backgroundImageBase64 = b64)
}

@Composable
private fun DefaultThemeCard(selected: Boolean, onSelect: () -> Unit) {
    // Preview approximated from the app's own Material scheme.
    val scheme = MaterialTheme.colorScheme
    val preview = remember(scheme) {
        themeFromSeed("preview_default", "Default", scheme.primary.argb(), dark = false).copy(
            boardBackground = scheme.surfaceContainerLow.argb(),
            keyBackground = scheme.surfaceContainerHighest.argb(),
            keyText = scheme.onSurface.argb(),
            modifierKeyBackground = scheme.surfaceContainerHigh.argb(),
            enterKeyBackground = scheme.primary.argb(),
            enterKeyText = scheme.onPrimary.argb(),
            accent = scheme.primary.argb(),
        )
    }
    Row(modifier = Modifier.padding(horizontal = 12.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            ThemeCard(
                theme = preview.copy(name = "Default (system)"),
                selected = selected,
                onSelect = onSelect,
                onEdit = null,
                onExport = null,
                onDelete = null,
                subtitle = "Material You · follows light/dark",
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ThemeCard(
    theme: ThemeSpec,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    subtitle: String? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                }
            )
            .clickable(onClick = onSelect)
            .padding(6.dp),
    ) {
        ThemePreview(theme)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    theme.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (onEdit != null || onExport != null || onDelete != null) {
            Row {
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit ${theme.name}",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onExport != null) {
                    IconButton(onClick = onExport, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.FileUpload,
                            contentDescription = "Export ${theme.name}",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete ${theme.name}",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete theme?") },
            text = { Text("“${theme.name}” will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Miniature keyboard drawn from the spec: toolbar, two key rows, bottom row. */
@Composable
fun ThemePreview(theme: ThemeSpec, modifier: Modifier = Modifier) {
    val keyShape = RoundedCornerShape(((theme.keyCornerRadiusDp ?: 8) / 3f + 1).dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        theme.backgroundImage?.let { path ->
            val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        BitmapFactory.decodeFile(path)?.asImageBitmap()
                    }.getOrNull()
                }
            }
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(theme.backgroundImageOpacity),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(colorOf(theme.boardBackground)),
        )
        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .background(colorOf(theme.effectiveToolCircle()), CircleShape),
                    )
                }
            }
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(8) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp)
                                .background(colorOf(theme.keyBackground), keyShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(colorOf(theme.keyText), CircleShape),
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(14.dp)
                        .background(colorOf(theme.modifierKeyBackground), keyShape),
                )
                Box(
                    modifier = Modifier
                        .weight(3.6f)
                        .height(14.dp)
                        .background(colorOf(theme.keyBackground), keyShape),
                )
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(14.dp)
                        .background(colorOf(theme.enterKeyBackground), keyShape),
                )
            }
        }
    }
}

// ---- theme editor ----

@Composable
fun ThemeEditorScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    themeId: String,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val theme = settings.customThemes.find { it.id == themeId }
    if (theme == null) {
        Text(
            "This theme no longer exists.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    fun update(transform: (ThemeSpec) -> ThemeSpec) {
        scope.launch { repository.upsertCustomTheme(transform(theme)) }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val file = File(themeImagesDir(context), "${theme.id}_${System.currentTimeMillis()}.img")
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                    theme.backgroundImage?.let { File(it).delete() }
                    repository.upsertCustomTheme(theme.copy(backgroundImage = file.absolutePath))
                }
            }
        }
    }

    // Live preview pinned on top.
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ThemePreview(theme)
    }

    var name by rememberSaveable(theme.id) { mutableStateOf(theme.name) }
    OutlinedTextField(
        value = name,
        onValueChange = {
            name = it
            update { t -> t.copy(name = it.ifBlank { "Untitled" }) }
        },
        label = { Text("Theme name") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )

    SectionHeaderPublic("Start from a color")
    Text(
        "Regenerates all colors from one seed — tweak anything below afterwards.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    ListItem(
        headlineContent = { Text("Dark theme") },
        supportingContent = { Text("Seed colors generate a dark or light palette") },
        trailingContent = {
            Switch(
                checked = theme.dark,
                onCheckedChange = { dark ->
                    update { t ->
                        themeFromSeed(t.id, t.name, t.enterKeyBackground, dark).copy(
                            backgroundImage = t.backgroundImage,
                            backgroundImageOpacity = t.backgroundImageOpacity,
                            keyCornerRadiusDp = t.keyCornerRadiusDp,
                            popupCornerRadiusDp = t.popupCornerRadiusDp,
                            toolCircleRadiusDp = t.toolCircleRadiusDp,
                        )
                    }
                },
            )
        },
    )
    LazyRow(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SeedSwatches) { seed ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colorOf(seed))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable {
                        update { t ->
                            themeFromSeed(t.id, t.name, seed, t.dark).copy(
                                backgroundImage = t.backgroundImage,
                                backgroundImageOpacity = t.backgroundImageOpacity,
                                keyCornerRadiusDp = t.keyCornerRadiusDp,
                                popupCornerRadiusDp = t.popupCornerRadiusDp,
                                toolCircleRadiusDp = t.toolCircleRadiusDp,
                            )
                        }
                    },
            )
        }
    }

    SectionHeaderPublic("Board")
    ColorRow("Background", theme.boardBackground, supportsAlpha = true) {
        update { t -> t.copy(boardBackground = it) }
    }
    ListItem(
        headlineContent = { Text("Background image") },
        supportingContent = {
            Text(if (theme.backgroundImage == null) "None — pick a photo" else "Tap to replace")
        },
        leadingContent = {
            Icon(Icons.Outlined.Image, contentDescription = null)
        },
        trailingContent = {
            if (theme.backgroundImage != null) {
                TextButton(onClick = {
                    theme.backgroundImage?.let { File(it).delete() }
                    update { t -> t.copy(backgroundImage = null) }
                }) { Text("Remove") }
            }
        },
        modifier = Modifier.clickable {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )
    if (theme.backgroundImage != null) {
        SliderRow(
            "Image opacity",
            value = theme.backgroundImageOpacity,
            range = 0f..1f,
            display = "${(theme.backgroundImageOpacity * 100).toInt()}%",
        ) { update { t -> t.copy(backgroundImageOpacity = it) } }
        Text(
            "Tip: lower the background color's opacity (in its color picker) to let the image show through the board.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    SectionHeaderPublic("Keys")
    ColorRow("Letter keys", theme.keyBackground, supportsAlpha = true) {
        update { t -> t.copy(keyBackground = it) }
    }
    ColorRow("Key text", theme.keyText) { update { t -> t.copy(keyText = it) } }
    ColorRow("Modifier keys", theme.modifierKeyBackground, supportsAlpha = true) {
        update { t -> t.copy(modifierKeyBackground = it) }
    }
    NullableColorRow(
        "Modifier key text", theme.modifierKeyText, fallback = theme.keyText,
        onChange = { update { t -> t.copy(modifierKeyText = it) } },
    )
    ColorRow("Enter key", theme.enterKeyBackground) { update { t -> t.copy(enterKeyBackground = it) } }
    ColorRow("Enter key icon", theme.enterKeyText) { update { t -> t.copy(enterKeyText = it) } }
    NullableColorRow(
        "Pressed key", theme.pressedKeyBackground, fallback = theme.effectivePressed(),
        onChange = { update { t -> t.copy(pressedKeyBackground = it) } },
    )
    NullableColorRow(
        "Key border", theme.keyBorderColor, fallback = theme.keyText,
        onChange = { update { t -> t.copy(keyBorderColor = it) } },
    )
    if (theme.keyBorderColor != null) {
        SliderRow(
            "Border width",
            value = theme.keyBorderWidthDp,
            range = 0f..3f,
            display = "%.1f dp".format(theme.keyBorderWidthDp),
        ) { update { t -> t.copy(keyBorderWidthDp = (it * 10).toInt() / 10f) } }
    }

    SectionHeaderPublic("Accent & popups")
    ColorRow("Accent", theme.accent) { update { t -> t.copy(accent = it) } }
    Text(
        "Shift-on tint, gesture trail, active tools, buttons in the panels.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    NullableColorRow(
        "Popup background", theme.popupBackground, fallback = theme.effectivePopup(),
        supportsAlpha = true,
        onChange = { update { t -> t.copy(popupBackground = it) } },
    )
    NullableColorRow(
        "Popup text", theme.popupText, fallback = theme.keyText,
        onChange = { update { t -> t.copy(popupText = it) } },
    )

    SectionHeaderPublic("Toolbar")
    NullableColorRow(
        "Tool icons", theme.toolbarIcon,
        fallback = colorOf(theme.keyText).copy(alpha = 0.65f)
            .compositeOver(colorOf(theme.boardBackground)).argb(),
        onChange = { update { t -> t.copy(toolbarIcon = it) } },
    )
    NullableColorRow(
        "Tool circles", theme.toolCircleBackground, fallback = theme.effectiveToolCircle(),
        supportsAlpha = true,
        onChange = { update { t -> t.copy(toolCircleBackground = it) } },
    )
    NullableColorRow(
        "Active tool circle", theme.toolCircleActiveBackground, fallback = theme.effectivePressed(),
        supportsAlpha = true,
        onChange = { update { t -> t.copy(toolCircleActiveBackground = it) } },
    )

    SectionHeaderPublic("Panels & suggestions")
    NullableColorRow(
        "Cards & search bar", theme.chipBackground, fallback = theme.modifierKeyBackground,
        supportsAlpha = true,
        onChange = { update { t -> t.copy(chipBackground = it) } },
    )
    NullableColorRow(
        "Suggestion text", theme.suggestionText, fallback = theme.keyText,
        onChange = { update { t -> t.copy(suggestionText = it) } },
    )

    SectionHeaderPublic("Corners")
    val hasCustomRadii = theme.keyCornerRadiusDp != null
    ListItem(
        headlineContent = { Text("Theme-specific corner radii") },
        supportingContent = { Text("Off: follow the sliders in Appearance") },
        trailingContent = {
            Switch(
                checked = hasCustomRadii,
                onCheckedChange = { enable ->
                    update { t ->
                        if (enable) {
                            t.copy(
                                keyCornerRadiusDp = settings.keyCornerRadiusDp,
                                popupCornerRadiusDp = 12,
                                toolCircleRadiusDp = settings.toolCircleRadiusDp,
                            )
                        } else {
                            t.copy(
                                keyCornerRadiusDp = null,
                                popupCornerRadiusDp = null,
                                toolCircleRadiusDp = null,
                            )
                        }
                    }
                },
            )
        },
    )
    if (hasCustomRadii) {
        SliderRow(
            "Key corner radius",
            value = (theme.keyCornerRadiusDp ?: 8).toFloat(),
            range = 0f..28f,
            display = "${theme.keyCornerRadiusDp} dp",
        ) { update { t -> t.copy(keyCornerRadiusDp = it.toInt()) } }
        SliderRow(
            "Popup corner radius",
            value = (theme.popupCornerRadiusDp ?: 12).toFloat(),
            range = 0f..24f,
            display = "${theme.popupCornerRadiusDp} dp",
        ) { update { t -> t.copy(popupCornerRadiusDp = it.toInt()) } }
        SliderRow(
            "Tool circle radius",
            value = (theme.toolCircleRadiusDp ?: 20).toFloat(),
            range = 0f..20f,
            display = if ((theme.toolCircleRadiusDp ?: 20) == 0) "off" else "${theme.toolCircleRadiusDp} dp",
        ) { update { t -> t.copy(toolCircleRadiusDp = it.toInt()) } }
    }
    Spacer(Modifier.height(24.dp))
}

// ---- small building blocks ----

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

/** A required color: tap the swatch to edit. */
@Composable
private fun ColorRow(
    title: String,
    color: Long,
    supportsAlpha: Boolean = false,
    onChange: (Long) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Swatch(color) },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        ColorPickerDialog(
            title = title,
            initial = color,
            supportsAlpha = supportsAlpha,
            showReset = false,
            onPick = { onChange(it); open = false },
            onReset = {},
            onDismiss = { open = false },
        )
    }
}

/** An optional color: shows the derived fallback until overridden; resettable. */
@Composable
private fun NullableColorRow(
    title: String,
    color: Long?,
    fallback: Long,
    supportsAlpha: Boolean = false,
    onChange: (Long?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (color == null) {
            { Text("Auto", style = MaterialTheme.typography.bodySmall) }
        } else {
            null
        },
        trailingContent = { Swatch(color ?: fallback) },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        ColorPickerDialog(
            title = title,
            initial = color ?: fallback,
            supportsAlpha = supportsAlpha,
            showReset = color != null,
            onPick = { onChange(it); open = false },
            onReset = { onChange(null); open = false },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun Swatch(color: Long, size: androidx.compose.ui.unit.Dp = 28.dp) {
    // Checkerboard-ish underlay so translucent colors are visibly translucent.
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(colorOf(color)),
        )
    }
}

// ---- color picker ----

/**
 * Compact HSVA picker: preset swatches, hue/saturation/value/alpha sliders,
 * hex field. Presets set hue but keep the current alpha, so building a
 * translucent scrim color stays a one-slider job.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Long,
    supportsAlpha: Boolean,
    showReset: Boolean,
    onPick: (Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initialColor = colorOf(initial)
    var hsv by remember {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), arr)
        mutableStateOf(Triple(arr[0], arr[1], arr[2]))
    }
    var alpha by remember { mutableStateOf(initialColor.alpha) }
    val current = Color(
        android.graphics.Color.HSVToColor(
            (alpha * 255).toInt().coerceIn(0, 255),
            floatArrayOf(hsv.first, hsv.second, hsv.third),
        )
    )
    var hexText by remember { mutableStateOf("") }
    val currentHex = "%08X".format(current.toArgb())

    fun setFromArgb(argb: Int) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, arr)
        hsv = Triple(arr[0], arr[1], arr[2])
        alpha = ((argb ushr 24) and 0xFF) / 255f
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Swatch(current.argb(), size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hexText.ifEmpty { currentHex },
                        onValueChange = { text ->
                            hexText = text
                            val cleaned = text.removePrefix("#").trim()
                            if (cleaned.length == 6 || cleaned.length == 8) {
                                cleaned.toULongOrNull(16)?.let { parsed ->
                                    val argb = if (cleaned.length == 6) {
                                        (0xFF000000UL or parsed).toLong()
                                    } else {
                                        parsed.toLong()
                                    }
                                    setFromArgb(argb.toInt())
                                }
                            }
                        },
                        label = { Text("Hex (AARRGGBB)") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Preset swatches; keep current alpha.
                val presets = remember {
                    SeedSwatches + listOf(0xFFFFFFFF, 0xFFDDDDE2, 0xFF9A9AA2, 0xFF505057, 0xFF202024, 0xFF000000)
                }
                for (rowColors in presets.chunked(7)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                        for (preset in rowColors) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(colorOf(preset))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .clickable {
                                        val base = colorOf(preset)
                                        val arr = FloatArray(3)
                                        android.graphics.Color.colorToHSV(base.toArgb(), arr)
                                        hsv = Triple(arr[0], arr[1], arr[2])
                                        hexText = ""
                                    },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                PickerSlider("Hue", hsv.first, 0f..360f) { hsv = hsv.copy(first = it); hexText = "" }
                PickerSlider("Saturation", hsv.second, 0f..1f) { hsv = hsv.copy(second = it); hexText = "" }
                PickerSlider("Brightness", hsv.third, 0f..1f) { hsv = hsv.copy(third = it); hexText = "" }
                if (supportsAlpha) {
                    PickerSlider("Opacity", alpha, 0f..1f) { alpha = it; hexText = "" }
                }
            }
        },
        confirmButton = {
            Row {
                if (showReset) {
                    TextButton(onClick = onReset) { Text("Auto") }
                }
                TextButton(onClick = { onPick(current.argb()) }) { Text("Apply") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickerSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(76.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

/** Public alias so ThemeScreens can reuse MainActivity's section header style. */
@Composable
fun SectionHeaderPublic(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}
