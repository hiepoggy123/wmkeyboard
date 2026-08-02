package com.wasimaster.wmkeyboard.app

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.core.net.toUri
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.settings.AutoThemeTrigger
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.settings.PoolEntry
import com.wasimaster.wmkeyboard.core.settings.softenedForPhoto
import com.wasimaster.wmkeyboard.core.tools.PhotoBackgroundManager
import com.wasimaster.wmkeyboard.core.theme.BuiltInThemes
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import com.wasimaster.wmkeyboard.core.theme.builtInThemeNameRes
import com.wasimaster.wmkeyboard.core.theme.GradientSpec
import com.wasimaster.wmkeyboard.core.theme.GradientType
import com.wasimaster.wmkeyboard.core.theme.KeyShapeKind
import com.wasimaster.wmkeyboard.core.theme.SeedSwatches
import com.wasimaster.wmkeyboard.core.theme.ThemeAnimation
import com.wasimaster.wmkeyboard.core.theme.ThemeCodec
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.theme.brush
import com.wasimaster.wmkeyboard.core.theme.reseeded
import com.wasimaster.wmkeyboard.core.theme.themeFromSeed
import com.wasimaster.wmkeyboard.core.theme.themeName
import com.wasimaster.wmkeyboard.core.theme.withEmbeddedImages
import com.wasimaster.wmkeyboard.core.theme.withExtractedImages
import com.wasimaster.wmkeyboard.core.util.requireInputStream
import com.wasimaster.wmkeyboard.core.util.runCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

// ---- shared helpers ----

private fun colorOf(argb: Long): Color = Color(argb.toInt())
private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun themeImagesDir(context: android.content.Context): File =
    File(context.filesDir, "theme_images").apply { mkdirs() }

/** Effective (fallback-resolved) colors the editor shows for nullable fields. */
private fun ThemeSpec.effectivePressed(): Long =
    pressedKeyBackground ?: lerp(colorOf(keyBackground), colorOf(accent), 0.40f).argb()

private fun ThemeSpec.effectivePopup(): Long =
    popupBackground ?: colorOf(keyText).copy(alpha = if (dark) 0.20f else 0.06f)
        .compositeOver(colorOf(boardBackground)).argb()

private fun ThemeSpec.effectiveToolCircle(): Long =
    toolCircleBackground ?: colorOf(keyText).copy(alpha = 0.14f)
        .compositeOver(colorOf(boardBackground)).argb()

/** The label for one of the app-wide light/dark modes. */
@StringRes
private fun themeModeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system_label
    ThemeMode.LIGHT -> R.string.theme_mode_light_label
    ThemeMode.DARK -> R.string.theme_mode_dark_label
    ThemeMode.AMOLED -> R.string.theme_mode_amoled_label
}

/**
 * The display name for a theme id in the same namespace as keyboardThemeId.
 * Composable so the built-in default resolves in the caller's locale; the id
 * itself is what gets stored, never this text.
 */
@Composable
internal fun themeDisplayName(settings: KeyboardSettings, id: String): String {
    val defaultName = stringResource(R.string.theme_default_name)
    val builtInName = builtInThemeNameRes(id)?.let { stringResource(it) }
    return when (id) {
        DEFAULT_THEME_ID -> defaultName
        else -> settings.customThemes.find { it.id == id }?.name
            ?: builtInName
            ?: defaultName
    }
}

/** Radio-list of every selectable theme, for choosing an auto light/dark slot. */
@Composable
private fun ThemePickerDialog(
    title: String,
    settings: KeyboardSettings,
    selectedId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultName = stringResource(R.string.theme_default_name)
    val options = buildList {
        add(DEFAULT_THEME_ID to defaultName)
        BuiltInThemes.forEach { add(it.id to themeName(it)) }
        settings.customThemes.sortedBy { it.name.lowercase() }.forEach { add(it.id to it.name) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for ((id, name) in options) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = id == selectedId, onClick = { onPick(id) })
                        Spacer(Modifier.width(8.dp))
                        Text(name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_done)) }
        },
    )
}

/**
 * [ThemePickerDialog] with an "Inherit" row at the top, for a keyboard mode —
 * where having no theme of its own is the default and the common case.
 */
@Composable
internal fun ModeThemePickerDialog(
    settings: KeyboardSettings,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val inheritLabel = stringResource(R.string.theme_mode_inherit_label)
    val defaultName = stringResource(R.string.theme_default_name)
    val options = buildList<Pair<String?, String>> {
        add(null to inheritLabel)
        add(DEFAULT_THEME_ID to defaultName)
        BuiltInThemes.forEach { add(it.id to themeName(it)) }
        settings.customThemes.sortedBy { it.name.lowercase() }.forEach { add(it.id to it.name) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_mode_picker_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for ((id, name) in options) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = id == selectedId, onClick = { onPick(id) })
                        Spacer(Modifier.width(8.dp))
                        Text(name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_done)) }
        },
    )
}

/** `7:00 AM` / `19:00`, following the phone's own 12-vs-24-hour setting. */
@Composable
private fun formatMinutesOfDay(minutes: Int): String {
    val context = LocalContext.current
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, minutes / 60)
        set(java.util.Calendar.MINUTE, minutes % 60)
    }
    // The Android formatter, not java.text's: only this one honours the
    // "use 24-hour format" switch, which is a system setting rather than
    // something the locale decides.
    return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
}

/** Clock-face picker for one of the auto-theme switchover times. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeOfDayPickerDialog(
    title: String,
    minutes: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = minutes / 60,
        initialMinute = minutes % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.theme_time_set_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

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
        ActivityResultContracts.CreateDocument(ThemeCodec.MIME_TYPE)
    ) { uri ->
        val theme = pendingExport
        pendingExport = null
        if (uri != null && theme != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(ThemeCodec.encode(theme.withEmbeddedImages()).toByteArray())
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
                runCancellable {
                    val text = context.contentResolver.requireInputStream(uri)
                        .use { it.readBytes().decodeToString() }
                    val parsed = ThemeCodec.decode(text) ?: return@runCancellable
                    val id = "custom_${System.currentTimeMillis()}"
                    // Set the fresh id first so the extracted image filenames key
                    // off it and stay unique against existing themes.
                    repository.upsertCustomTheme(
                        parsed.copy(id = id).withExtractedImages(themeImagesDir(context))
                    )
                    repository.setKeyboardThemeId(id)
                }
            }
        }
    }
    fun export(theme: ThemeSpec) {
        pendingExport = theme
        exportLauncher.launch("${theme.name.ifBlank { "theme" }}.${ThemeCodec.FILE_EXTENSION}")
    }
    fun duplicateAndEdit(base: ThemeSpec) {
        scope.launch {
            val id = "custom_${System.currentTimeMillis()}"
            val copyName = context.getString(R.string.theme_duplicate_name, base.name)
            repository.upsertCustomTheme(base.copy(id = id, name = copyName))
            repository.setKeyboardThemeId(id)
            onEditTheme(id)
        }
    }

    SettingsGroup(stringResource(R.string.theme_mode_section_title)) {
        item { CaptionText(stringResource(R.string.theme_mode_section_body)) }
        item {
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
                        Text(stringResource(themeModeLabelRes(mode)))
                    }
                }
            }
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_material_you_title)) },
                supportingContent = { Text(stringResource(R.string.theme_material_you_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = { scope.launch { repository.setDynamicColor(it) } },
                    )
                },
                colors = transparentListColors(),
            )
        }
    }

    // null = closed; true = choosing the light theme; false = the dark theme.
    var pickerForLight by remember { mutableStateOf<Boolean?>(null) }
    // null = closed; true = editing when day starts, false = when night does.
    var timePickerForDay by remember { mutableStateOf<Boolean?>(null) }
    val auto = settings.autoTheme
    SettingsGroup(stringResource(R.string.theme_auto_section_title)) {
        item { CaptionText(stringResource(R.string.theme_auto_section_body)) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_auto_title)) },
                supportingContent = { Text(stringResource(R.string.theme_auto_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = auto.enabled,
                        onCheckedChange = { scope.launch { repository.setAutoThemeEnabled(it) } },
                    )
                },
                colors = transparentListColors(),
            )
        }
        if (auto.enabled) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.theme_auto_light_title)) },
                    supportingContent = { Text(themeDisplayName(settings, auto.lightThemeId)) },
                    colors = transparentListColors(),
                    modifier = Modifier.clickable { pickerForLight = true },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.theme_auto_dark_title)) },
                    supportingContent = { Text(themeDisplayName(settings, auto.darkThemeId)) },
                    colors = transparentListColors(),
                    modifier = Modifier.clickable { pickerForLight = false },
                )
            }
            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AutoThemeTrigger.entries.forEachIndexed { index, trigger ->
                        SegmentedButton(
                            selected = auto.trigger == trigger,
                            onClick = { scope.launch { repository.setAutoThemeTrigger(trigger) } },
                            shape = SegmentedButtonDefaults.itemShape(
                                index,
                                AutoThemeTrigger.entries.size,
                            ),
                        ) {
                            Text(stringResource(trigger.labelRes))
                        }
                    }
                }
            }
            when (auto.trigger) {
                AutoThemeTrigger.SYSTEM ->
                    item { CaptionText(stringResource(R.string.theme_auto_trigger_system_body)) }
                AutoThemeTrigger.SCHEDULE -> {
                    item {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.theme_auto_light_from_title))
                            },
                            supportingContent = { Text(formatMinutesOfDay(auto.dayStartMinutes)) },
                            colors = transparentListColors(),
                            modifier = Modifier.clickable { timePickerForDay = true },
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.theme_auto_dark_from_title))
                            },
                            supportingContent = { Text(formatMinutesOfDay(auto.nightStartMinutes)) },
                            colors = transparentListColors(),
                            modifier = Modifier.clickable { timePickerForDay = false },
                        )
                    }
                }
                AutoThemeTrigger.SUN -> item {
                    // Resolved inside the item: the group builder is a plain
                    // lambda, not a composable one.
                    val hasLocation =
                        settings.weatherLatitude != null && settings.weatherLongitude != null
                    val place = settings.weatherPlaceName.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.theme_auto_trigger_sun_place_fallback)
                    CaptionText(
                        if (hasLocation) {
                            stringResource(R.string.theme_auto_trigger_sun_body, place)
                        } else {
                            stringResource(R.string.theme_auto_trigger_sun_no_location_body)
                        },
                    )
                }
            }
        }
    }
    timePickerForDay?.let { forDay ->
        TimeOfDayPickerDialog(
            title = stringResource(
                if (forDay) R.string.theme_auto_light_from_title
                else R.string.theme_auto_dark_from_title,
            ),
            minutes = if (forDay) auto.dayStartMinutes else auto.nightStartMinutes,
            onPick = { picked ->
                scope.launch {
                    if (forDay) repository.setAutoThemeDayStart(picked)
                    else repository.setAutoThemeNightStart(picked)
                }
                timePickerForDay = null
            },
            onDismiss = { timePickerForDay = null },
        )
    }
    pickerForLight?.let { forLight ->
        ThemePickerDialog(
            title = stringResource(
                if (forLight) R.string.theme_auto_light_title else R.string.theme_auto_dark_title,
            ),
            settings = settings,
            selectedId = if (forLight) auto.lightThemeId else auto.darkThemeId,
            onPick = { id ->
                scope.launch {
                    if (forLight) repository.setAutoThemeLightId(id)
                    else repository.setAutoThemeDarkId(id)
                }
                pickerForLight = null
            },
            onDismiss = { pickerForLight = null },
        )
    }

    // The gallery is a grid of theme cards, which are their own surfaces, so
    // it keeps a plain header rather than being wrapped in a settings card.
    SectionHeaderPublic(stringResource(R.string.theme_gallery_section_title))
    if (auto.enabled) {
        CaptionText(stringResource(R.string.theme_gallery_auto_on_body))
    }
    val newThemeName = stringResource(R.string.theme_new_default_name)
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        Button(onClick = {
            scope.launch {
                val id = "custom_${System.currentTimeMillis()}"
                repository.upsertCustomTheme(
                    themeFromSeed(id, newThemeName, SeedSwatches.first(), dark = true)
                )
                repository.setKeyboardThemeId(id)
                onEditTheme(id)
            }
        }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.theme_create_action))
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { importLauncher.launch(ThemeCodec.IMPORT_MIME_TYPES) }) {
            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(CommonR.string.common_import))
        }
    }
    Spacer(Modifier.height(8.dp))

    // Default (system) card first, then customs, then built-ins — two per row.
    DefaultThemeCard(
        selected = settings.keyboardThemeId == DEFAULT_THEME_ID,
        onSelect = { scope.launch { repository.setKeyboardThemeId(DEFAULT_THEME_ID) } },
    )
    val customs = settings.customThemes.sortedBy { it.name.lowercase() }
    if (customs.isNotEmpty()) SectionHeaderPublic(stringResource(R.string.theme_custom_section_title))
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
                                theme.backgroundImageLandscape?.let { File(it).delete() }
                                repository.deleteCustomTheme(theme.id)
                            }
                        },
                    )
                }
            }
            if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    SectionHeaderPublic(stringResource(R.string.theme_builtin_section_title))
    CaptionText(stringResource(R.string.theme_builtin_section_body))
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
                theme = preview.copy(name = stringResource(R.string.theme_default_name)),
                selected = selected,
                onSelect = onSelect,
                onEdit = null,
                onExport = null,
                onDelete = null,
                subtitle = stringResource(R.string.theme_default_card_subtitle),
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
    // A built-in theme draws its translated name; a theme the user made keeps
    // the name the user typed.
    val displayName = themeName(theme)
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
                    displayName,
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
                    contentDescription = stringResource(R.string.theme_selected_desc),
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
                            contentDescription = stringResource(R.string.theme_edit_desc, displayName),
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onExport != null) {
                    IconButton(onClick = onExport, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.FileUpload,
                            contentDescription = stringResource(R.string.theme_export_desc, displayName),
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.theme_delete_desc, displayName),
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
            title = { Text(stringResource(R.string.theme_delete_title)) },
            text = { Text(stringResource(R.string.theme_delete_message, displayName)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) {
                    Text(stringResource(CommonR.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }
}

// ---- theme editor ----

@Composable
fun ThemeEditorScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    themeId: String,
    onNavigate: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val theme = settings.customThemes.find { it.id == themeId }
    if (theme == null) {
        Text(
            stringResource(R.string.theme_editor_missing_body),
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
                runCancellable {
                    val file = File(themeImagesDir(context), "${theme.id}_${System.currentTimeMillis()}.img")
                    context.contentResolver.requireInputStream(uri).use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                    theme.backgroundImage?.let { File(it).delete() }
                    // Same treatment an online photo gets: the board goes
                    // see-through so the image shows, and the keys stop
                    // covering all of it. The user raises either back.
                    repository.upsertCustomTheme(
                        theme.copy(
                            backgroundImage = file.absolutePath,
                            backgroundPhoto = null,
                            boardBackground = theme.boardBackground and 0x00FFFFFFL,
                            keyBackground = theme.keyBackground.softenedForPhoto(),
                            modifierKeyBackground = theme.modifierKeyBackground.softenedForPhoto(),
                        )
                    )
                    // A photo the user picked is worth keeping: it can then go
                    // on another theme, or into the rotation, without being
                    // hunted down in the gallery again.
                    PhotoBackgroundManager.addToCollection(context, file)
                }
            }
        }
    }
    // Separate picker for the landscape image: it only swaps that path, and
    // leaves the board alpha alone (the portrait picker already set the scrim).
    val imagePickerLandscape = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCancellable {
                    val file = File(
                        themeImagesDir(context),
                        "${theme.id}_land_${System.currentTimeMillis()}.img",
                    )
                    context.contentResolver.requireInputStream(uri).use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                    theme.backgroundImageLandscape?.let { File(it).delete() }
                    repository.upsertCustomTheme(
                        theme.copy(
                            backgroundImageLandscape = file.absolutePath,
                            backgroundPhotoLandscape = null,
                        ),
                    )
                    PhotoBackgroundManager.addToCollection(context, file)
                }
            }
        }
    }
    var cropOpen by rememberSaveable(theme.id) { mutableStateOf(false) }
    var cropLandscapeOpen by rememberSaveable(theme.id) { mutableStateOf(false) }
    var sourceDialogSlot by remember(theme.id) { mutableStateOf<BackgroundSlot?>(null) }

    // The photo rows appear only once the user has started using photos.
    // Somebody who sets one picture from their gallery -- which is most people
    // -- never sees either, and the screen they do use stays shorter for it.
    val collection by produceState(initialValue = emptyList<PoolEntry>(), themeId) {
        value = PhotoBackgroundManager.readPool(context).entries
    }
    val photos = settings.photoBackground
    val showRotation = collection.isNotEmpty() || photos.rotateEnabled
    val showServices = photos.unsplashApiKey.isNotBlank() ||
        photos.pexelsApiKey.isNotBlank() ||
        collection.any { it.credit != null }

    sourceDialogSlot?.let { slot ->
        val landscape = slot == BackgroundSlot.LANDSCAPE
        BackgroundSourceDialog(
            hasImage = if (landscape) {
                theme.backgroundImageLandscape != null
            } else {
                theme.backgroundImage != null
            },
            onDevice = {
                sourceDialogSlot = null
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (landscape) imagePickerLandscape.launch(request) else imagePicker.launch(request)
            },
            onOnline = {
                sourceDialogSlot = null
                onNavigate(photoBrowseRoute(theme.id, slot))
            },
            onSaved = {
                sourceDialogSlot = null
                onNavigate(photoLibraryRoute(theme.id, slot))
            },
            onRemove = {
                sourceDialogSlot = null
                scope.launch {
                    // The repository restores the board's opacity if applying a
                    // photo had zeroed it, so removal never leaves a
                    // see-through board.
                    repository.clearThemePhoto(theme.id, landscape)?.let { File(it).delete() }
                }
            },
            onDismiss = { sourceDialogSlot = null },
        )
    }

    // Live preview pinned on top.
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ThemePreview(theme)
    }

    val untitledName = stringResource(R.string.theme_untitled_name)
    val offLabel = stringResource(CommonR.string.common_off)
    var name by rememberSaveable(theme.id) { mutableStateOf(theme.name) }
    OutlinedTextField(
        value = name,
        onValueChange = {
            name = it
            update { t -> t.copy(name = it.ifBlank { untitledName }) }
        },
        label = { Text(stringResource(R.string.theme_name_label)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )

    SettingsGroup(stringResource(R.string.theme_seed_section_title)) {
        item { CaptionText(stringResource(R.string.theme_seed_section_body)) }
        // Changing the seed or the light/dark switch rebuilds every colour. The
        // board keeps how see-through it is, so the photo stays visible -- but
        // it is worth saying, because the colours around it do all change.
        if (theme.backgroundImage != null || theme.backgroundImageLandscape != null) {
            item { CaptionText(stringResource(R.string.photo_seed_keeps_image_body)) }
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_editor_dark_title)) },
                supportingContent = { Text(stringResource(R.string.theme_editor_dark_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = theme.dark,
                        onCheckedChange = { dark ->
                            update { t -> t.reseeded(t.enterKeyBackground, dark) }
                        },
                    )
                },
                colors = transparentListColors(),
            )
        }
        item {
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SeedSwatches) { seed ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colorOf(seed))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { update { t -> t.reseeded(seed, t.dark) } },
                    )
                }
            }
        }
    }

    SettingsGroup(stringResource(R.string.theme_board_section_title)) {
        item {
            ColorRow(
                stringResource(R.string.theme_board_background_title),
                theme.boardBackground,
                supportsAlpha = true,
            ) {
                update { t -> t.copy(boardBackground = it) }
            }
        }
        item {
            GradientEditor(
                title = stringResource(R.string.theme_board_gradient_title),
                subtitle = stringResource(R.string.theme_board_gradient_subtitle),
                gradient = theme.boardGradient,
                defaultGradient = GradientSpec(
                    colors = listOf(theme.boardBackground or 0xFF000000L, theme.accent),
                    type = GradientType.LINEAR,
                    angleDeg = 135f,
                ),
                onChange = { update { t -> t.copy(boardGradient = it) } },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_background_image_title)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (theme.backgroundImage == null) R.string.theme_background_image_none
                            else R.string.theme_background_image_replace,
                        ),
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Image, contentDescription = null) },
                trailingContent = {
                    val existingImage = theme.backgroundImage
                    if (existingImage != null) {
                        TextButton(onClick = {
                            File(existingImage).delete()
                            update { t ->
                                // Give the board its alpha back if picking the
                                // image zeroed it, so removal does not leave a
                                // see-through board.
                                val board = if ((t.boardBackground ushr 24) == 0L) {
                                    t.boardBackground or 0xFF000000L
                                } else {
                                    t.boardBackground
                                }
                                t.copy(backgroundImage = null, boardBackground = board)
                            }
                        }) { Text(stringResource(CommonR.string.common_delete)) }
                    }
                },
                colors = transparentListColors(),
                modifier = Modifier.clickable { sourceDialogSlot = BackgroundSlot.PORTRAIT },
            )
        }
        theme.backgroundPhoto?.let { credit ->
            // Both services make it necessary to name the photographer where
            // their photo is shown, so the credit sits on the row itself.
            item { PhotoCreditRow(credit) { url -> openLink(context, url) } }
        }
        if (theme.backgroundImage != null) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.theme_crop_image_title)) },
                    supportingContent = { Text(stringResource(R.string.theme_crop_image_subtitle)) },
                    leadingContent = { Icon(Icons.Outlined.Crop, contentDescription = null) },
                    colors = transparentListColors(),
                    modifier = Modifier.clickable { cropOpen = true },
                )
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_image_opacity_title),
                    value = theme.backgroundImageOpacity,
                    range = 0f..1f,
                    display = { "${(it * 100).toInt()}%" },
                ) { update { t -> t.copy(backgroundImageOpacity = it) } }
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_image_blur_title),
                    value = theme.backgroundImageBlur,
                    range = 0f..25f,
                    display = { if (it < 0.5f) offLabel else it.toInt().toString() },
                ) { update { t -> t.copy(backgroundImageBlur = it) } }
            }
            item { CaptionText(stringResource(R.string.theme_background_image_alpha_body)) }
        }
        item {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.theme_background_image_landscape_title))
                },
                supportingContent = {
                    Text(
                        stringResource(
                            if (theme.backgroundImageLandscape == null) {
                                R.string.theme_background_image_landscape_none
                            } else {
                                R.string.theme_background_image_replace
                            },
                        ),
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Image, contentDescription = null) },
                trailingContent = {
                    val existingLandscapeImage = theme.backgroundImageLandscape
                    if (existingLandscapeImage != null) {
                        TextButton(onClick = {
                            File(existingLandscapeImage).delete()
                            update { t -> t.copy(backgroundImageLandscape = null) }
                        }) { Text(stringResource(CommonR.string.common_delete)) }
                    }
                },
                colors = transparentListColors(),
                modifier = Modifier.clickable { sourceDialogSlot = BackgroundSlot.LANDSCAPE },
            )
        }
        theme.backgroundPhotoLandscape?.let { credit ->
            item { PhotoCreditRow(credit) { url -> openLink(context, url) } }
        }
        if (theme.backgroundImageLandscape != null) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.theme_crop_landscape_title)) },
                    supportingContent = { Text(stringResource(R.string.theme_crop_image_subtitle)) },
                    leadingContent = { Icon(Icons.Outlined.Crop, contentDescription = null) },
                    colors = transparentListColors(),
                    modifier = Modifier.clickable { cropLandscapeOpen = true },
                )
            }
        }
        // Only offered once there is something to rotate. Most people set one
        // photo and stop, and a row for a feature they have not started is
        // clutter in the one screen they do use.
        if (showRotation) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.photo_rotation_title)) },
                    supportingContent = { Text(stringResource(R.string.photo_rotation_subtitle)) },
                    leadingContent = { Icon(Icons.Outlined.Autorenew, contentDescription = null) },
                    trailingContent = {
                        Text(
                            stringResource(
                                if (settings.photoBackground.rotateEnabled) {
                                    CommonR.string.common_on
                                } else {
                                    CommonR.string.common_off
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = transparentListColors(),
                    modifier = Modifier.clickable { onNavigate(PHOTO_ROTATION_ROUTE) },
                )
            }
        }
        // Likewise: somebody who never opens the online picker has no key to
        // manage. The picker's own "add a key" action reaches this screen.
        if (showServices) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.photo_services_title)) },
                    supportingContent = { Text(stringResource(R.string.photo_services_subtitle)) },
                    leadingContent = { Icon(Icons.Outlined.Wallpaper, contentDescription = null) },
                    colors = transparentListColors(),
                    modifier = Modifier.clickable { onNavigate(PHOTO_HUB_ROUTE) },
                )
            }
        }
    }

    val cropSource = theme.backgroundImage
    if (cropOpen && cropSource != null) {
        CropImageDialog(
            path = cropSource,
            onCropped = { newPath ->
                scope.launch {
                    File(cropSource).delete()
                    repository.upsertCustomTheme(theme.copy(backgroundImage = newPath))
                }
                cropOpen = false
            },
            onDismiss = { cropOpen = false },
        )
    }
    val cropLandscapeSource = theme.backgroundImageLandscape
    if (cropLandscapeOpen && cropLandscapeSource != null) {
        CropImageDialog(
            path = cropLandscapeSource,
            onCropped = { newPath ->
                scope.launch {
                    File(cropLandscapeSource).delete()
                    repository.upsertCustomTheme(theme.copy(backgroundImageLandscape = newPath))
                }
                cropLandscapeOpen = false
            },
            onDismiss = { cropLandscapeOpen = false },
        )
    }

    SettingsGroup(stringResource(R.string.theme_keys_section_title)) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.theme_key_shape_title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val labels = mapOf(
                        KeyShapeKind.ROUNDED to stringResource(R.string.theme_key_shape_rounded_label),
                        KeyShapeKind.PILL to stringResource(R.string.theme_key_shape_pill_label),
                        KeyShapeKind.CUT to stringResource(R.string.theme_key_shape_cut_label),
                        KeyShapeKind.SQUIRCLE to stringResource(R.string.theme_key_shape_squircle_label),
                    )
                    KeyShapeKind.entries.forEachIndexed { index, kind ->
                        SegmentedButton(
                            selected = theme.keyShape == kind,
                            onClick = { update { t -> t.copy(keyShape = kind) } },
                            shape = SegmentedButtonDefaults.itemShape(index, KeyShapeKind.entries.size),
                        ) {
                            Text(labels.getValue(kind), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item {
            ColorRow(
                stringResource(R.string.theme_letter_keys_title),
                theme.keyBackground,
                supportsAlpha = true,
            ) {
                update { t -> t.copy(keyBackground = it) }
            }
        }
        item {
            GradientEditor(
                title = stringResource(R.string.theme_key_gradient_title),
                subtitle = stringResource(R.string.theme_key_gradient_subtitle),
                gradient = theme.keyGradient,
                defaultGradient = GradientSpec(
                    colors = listOf(0x26FFFFFF, 0x00FFFFFF),
                    type = GradientType.LINEAR,
                    angleDeg = 90f,
                ),
                onChange = { update { t -> t.copy(keyGradient = it) } },
            )
        }
        item {
            ColorRow(stringResource(R.string.theme_key_text_title), theme.keyText) {
                update { t -> t.copy(keyText = it) }
            }
        }
        item {
            ColorRow(
                stringResource(R.string.theme_modifier_keys_title),
                theme.modifierKeyBackground,
                supportsAlpha = true,
            ) {
                update { t -> t.copy(modifierKeyBackground = it) }
            }
        }
        item {
            NullableColorRow(
                stringResource(R.string.theme_modifier_key_text_title),
                theme.modifierKeyText, fallback = theme.keyText,
                onChange = { update { t -> t.copy(modifierKeyText = it) } },
            )
        }
        item {
            ColorRow(stringResource(R.string.theme_enter_key_title), theme.enterKeyBackground) {
                update { t -> t.copy(enterKeyBackground = it) }
            }
        }
        item {
            ColorRow(stringResource(R.string.theme_enter_key_icon_title), theme.enterKeyText) {
                update { t -> t.copy(enterKeyText = it) }
            }
        }
        item {
            NullableColorRow(
                stringResource(R.string.theme_pressed_key_title),
                theme.pressedKeyBackground, fallback = theme.effectivePressed(),
                onChange = { update { t -> t.copy(pressedKeyBackground = it) } },
            )
        }
        item {
            NullableColorRow(
                stringResource(R.string.theme_key_border_title),
                theme.keyBorderColor, fallback = theme.keyText,
                onChange = { update { t -> t.copy(keyBorderColor = it) } },
            )
        }
        if (theme.keyBorderColor != null) {
            item {
                SliderRow(
                    stringResource(R.string.theme_border_width_title),
                    value = theme.keyBorderWidthDp,
                    range = 0f..3f,
                    display = { "%.1f dp".format(it) },
                ) { update { t -> t.copy(keyBorderWidthDp = (it * 10).toInt() / 10f) } }
            }
        }
    }

    SettingsGroup(stringResource(R.string.theme_accent_section_title)) {
        item {
            ColorRow(stringResource(R.string.theme_accent_title), theme.accent) {
                update { t -> t.copy(accent = it) }
            }
        }
        item { CaptionText(stringResource(R.string.theme_accent_body)) }
        item {
            NullableColorRow(
                stringResource(R.string.theme_gesture_trail_title),
                theme.gestureTrailColor, fallback = theme.accent,
                supportsAlpha = true,
                onChange = { update { t -> t.copy(gestureTrailColor = it) } },
            )
        }
        item {
            NullableColorRow(
                stringResource(R.string.theme_popup_background_title),
                theme.popupBackground, fallback = theme.effectivePopup(),
                supportsAlpha = true,
                onChange = { update { t -> t.copy(popupBackground = it) } },
            )
        }
        item {
            NullableColorRow(
                stringResource(R.string.theme_popup_text_title),
                theme.popupText, fallback = theme.keyText,
                onChange = { update { t -> t.copy(popupText = it) } },
            )
        }
    }

    SettingsGroup(stringResource(R.string.theme_toolbar_section_title)) {
        item {
            NullableColorRow(
                stringResource(R.string.theme_tool_icons_title), theme.toolbarIcon,
                fallback = colorOf(theme.keyText).copy(alpha = 0.65f)
                    .compositeOver(colorOf(theme.boardBackground)).argb(),
                onChange = { update { t -> t.copy(toolbarIcon = it) } },
            )
        }
        item {
            NullableColorRow(
                stringResource(R.string.theme_tool_circles_title),
                theme.toolCircleBackground, fallback = theme.effectiveToolCircle(),
                supportsAlpha = true,
                onChange = { update { t -> t.copy(toolCircleBackground = it) } },
            )
        }
        item {
            NullableColorRow(
                stringResource(R.string.theme_tool_circle_active_title),
                theme.toolCircleActiveBackground, fallback = theme.effectivePressed(),
                supportsAlpha = true,
                onChange = { update { t -> t.copy(toolCircleActiveBackground = it) } },
            )
        }
    }

    SettingsGroup(stringResource(R.string.theme_panels_section_title)) {
        item {
            NullableColorRow(
                stringResource(R.string.theme_cards_title),
                theme.chipBackground, fallback = theme.modifierKeyBackground,
                supportsAlpha = true,
                onChange = { update { t -> t.copy(chipBackground = it) } },
            )
        }
        item {
            NullableColorRow(
                stringResource(R.string.theme_suggestion_text_title),
                theme.suggestionText, fallback = theme.keyText,
                onChange = { update { t -> t.copy(suggestionText = it) } },
            )
        }
    }

    val hasCustomRadii = theme.keyCornerRadiusDp != null
    SettingsGroup(stringResource(R.string.theme_corners_section_title)) {
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_custom_radii_title)) },
                supportingContent = { Text(stringResource(R.string.theme_custom_radii_subtitle)) },
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
                colors = transparentListColors(),
            )
        }
        if (hasCustomRadii) {
            item {
                SliderRow(
                    stringResource(R.string.theme_key_radius_title),
                    value = (theme.keyCornerRadiusDp ?: 8).toFloat(),
                    range = 0f..28f,
                    display = { "${it.toInt()} dp" },
                ) { update { t -> t.copy(keyCornerRadiusDp = it.toInt()) } }
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_popup_radius_title),
                    value = (theme.popupCornerRadiusDp ?: 12).toFloat(),
                    range = 0f..24f,
                    display = { "${it.toInt()} dp" },
                ) { update { t -> t.copy(popupCornerRadiusDp = it.toInt()) } }
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_tool_circle_radius_title),
                    value = (theme.toolCircleRadiusDp ?: 20).toFloat(),
                    range = 0f..20f,
                    display = { if (it.toInt() == 0) offLabel else "${it.toInt()} dp" },
                ) { update { t -> t.copy(toolCircleRadiusDp = it.toInt()) } }
            }
        }
    }

    SettingsGroup(stringResource(R.string.theme_animation_section_title)) {
        item { CaptionText(stringResource(R.string.theme_animation_section_body)) }
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val labels = mapOf(
                    ThemeAnimation.NONE to stringResource(CommonR.string.common_none),
                    ThemeAnimation.FLOW to stringResource(R.string.theme_animation_flow_label),
                    ThemeAnimation.HUE_CYCLE to stringResource(R.string.theme_animation_hue_cycle_label),
                )
                ThemeAnimation.entries.forEachIndexed { index, anim ->
                    SegmentedButton(
                        selected = theme.animation == anim,
                        onClick = { update { t -> t.copy(animation = anim) } },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeAnimation.entries.size),
                    ) {
                        Text(labels.getValue(anim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (theme.animation != ThemeAnimation.NONE) {
            item {
                SliderRow(
                    stringResource(R.string.theme_animation_speed_title),
                    value = theme.animationSpeed,
                    range = 0.25f..3f,
                    display = { "%.2f×".format(it) },
                ) { update { t -> t.copy(animationSpeed = (it * 20).toInt() / 20f) } }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

/** Opens a credit link in the browser. Failure is not worth a message. */
private fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri()))
    }
}

// ---- gradient editor ----

/**
 * Toggleable gradient block: on/off switch, gradient type, angle, and 2–4
 * color stops (each with alpha). A live strip previews the result.
 */
@Composable
private fun GradientEditor(
    title: String,
    subtitle: String,
    gradient: GradientSpec?,
    defaultGradient: GradientSpec,
    onChange: (GradientSpec?) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = gradient != null,
                onCheckedChange = { on -> onChange(if (on) defaultGradient else null) },
            )
        },
        colors = transparentListColors(),
    )
    if (gradient == null) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .background(gradient.brush()),
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val labels = mapOf(
            GradientType.LINEAR to stringResource(R.string.theme_gradient_linear_label),
            GradientType.RADIAL to stringResource(R.string.theme_gradient_radial_label),
            GradientType.SWEEP to stringResource(R.string.theme_gradient_sweep_label),
        )
        GradientType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = gradient.type == type,
                onClick = { onChange(gradient.copy(type = type)) },
                shape = SegmentedButtonDefaults.itemShape(index, GradientType.entries.size),
            ) {
                Text(labels.getValue(type), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (gradient.type != GradientType.RADIAL) {
        SliderRow(
            stringResource(R.string.theme_gradient_angle_title),
            value = gradient.angleDeg,
            range = 0f..360f,
            display = { "${it.toInt()}°" },
        ) { onChange(gradient.copy(angleDeg = it.toInt().toFloat())) }
    }
    gradient.colors.forEachIndexed { index, stop ->
        val stopTitle = stringResource(R.string.theme_gradient_color_title, index + 1)
        ColorRow(stopTitle, stop, supportsAlpha = true) { picked ->
            onChange(
                gradient.copy(
                    colors = gradient.colors.toMutableList().also { it[index] = picked },
                )
            )
        }
    }
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (gradient.colors.size < 4) {
            TextButton(onClick = {
                onChange(gradient.copy(colors = gradient.colors + gradient.colors.last()))
            }) { Text(stringResource(R.string.theme_gradient_add_color_action)) }
        }
        if (gradient.colors.size > 2) {
            TextButton(onClick = {
                onChange(gradient.copy(colors = gradient.colors.dropLast(1)))
            }) { Text(stringResource(R.string.theme_gradient_delete_color_action)) }
        }
    }
}

// ---- image cropper ----

/**
 * Pinch/drag cropper. The frame is the crop; the image pans and zooms under
 * it (cover-scaled, so the frame is always filled). Confirming maps the
 * frame back into bitmap coordinates and writes a new image file.
 */
@Composable
private fun CropImageDialog(
    path: String,
    onCropped: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                // Downsample huge photos; a keyboard background never needs
                // more than ~2048 px on a side.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 2048 || bounds.outHeight / (sample * 2) >= 2048) {
                    sample *= 2
                }
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    var aspect by rememberSaveable { mutableFloatStateOf(2.4f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }

    fun coverScale(bmp: android.graphics.Bitmap): Float =
        if (frame == IntSize.Zero) 1f
        else max(frame.width / bmp.width.toFloat(), frame.height / bmp.height.toFloat())

    fun clampOffset(o: Offset, bmp: android.graphics.Bitmap, z: Float): Offset {
        val s = coverScale(bmp) * z
        val maxX = ((bmp.width * s - frame.width) / 2f).coerceAtLeast(0f)
        val maxY = ((bmp.height * s - frame.height) / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_crop_image_title)) },
        text = {
            Column {
                val bmp = bitmap
                if (bmp == null) {
                    Text(
                        stringResource(CommonR.string.common_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .onGloballyPositioned {
                                if (frame != it.size) {
                                    frame = it.size
                                    offset = clampOffset(offset, bmp, zoom)
                                }
                            }
                            .pointerInput(bmp, aspect) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                                    offset = clampOffset(offset + pan, bmp, zoom)
                                }
                            },
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val s = coverScale(bmp) * zoom
                            val w = (bmp.width * s).roundToInt()
                            val h = (bmp.height * s).roundToInt()
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstOffset = IntOffset(
                                    ((size.width - w) / 2f + offset.x).roundToInt(),
                                    ((size.height - h) / 2f + offset.y).roundToInt(),
                                ),
                                dstSize = IntSize(w, h),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val ratios = listOf(
                            stringResource(R.string.theme_crop_ratio_keyboard_label) to 2.4f,
                            stringResource(R.string.theme_crop_ratio_wide_label) to 1.7f,
                            stringResource(R.string.theme_crop_ratio_square_label) to 1f,
                        )
                        ratios.forEachIndexed { index, (label, value) ->
                            SegmentedButton(
                                selected = aspect == value,
                                onClick = {
                                    aspect = value
                                    zoom = 1f
                                    offset = Offset.Zero
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, ratios.size),
                            ) {
                                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = bitmap != null && frame != IntSize.Zero && !saving,
                onClick = {
                    val bmp = bitmap ?: return@TextButton
                    saving = true
                    scope.launch(Dispatchers.IO) {
                        val saved = runCatching {
                            val s = coverScale(bmp) * zoom
                            val srcW = (frame.width / s).roundToInt().coerceIn(1, bmp.width)
                            val srcH = (frame.height / s).roundToInt().coerceIn(1, bmp.height)
                            val srcLeft = ((bmp.width - srcW) / 2f - offset.x / s)
                                .roundToInt().coerceIn(0, bmp.width - srcW)
                            val srcTop = ((bmp.height - srcH) / 2f - offset.y / s)
                                .roundToInt().coerceIn(0, bmp.height - srcH)
                            val cropped = android.graphics.Bitmap.createBitmap(bmp, srcLeft, srcTop, srcW, srcH)
                            val file = File(themeImagesDir(context), "crop_${System.currentTimeMillis()}.img")
                            file.outputStream().use { out ->
                                cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                            }
                            if (cropped !== bmp) cropped.recycle()
                            file.absolutePath
                        }.getOrNull()
                        withContext(Dispatchers.Main) {
                            saving = false
                            if (saved != null) onCropped(saved) else onDismiss()
                        }
                    }
                },
            ) {
                Text(
                    stringResource(
                        if (saving) R.string.theme_crop_saving_progress else R.string.theme_crop_action
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

// ---- small building blocks ----

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    // Local drag state, throttled writes — see rememberLiveSlider; without it
    // the thumb waits for the theme to round-trip through DataStore.
    val slider = rememberLiveSlider(value, onChange)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(display(slider.value), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = slider.value,
            onValueChange = slider::onDrag,
            onValueChangeFinished = slider::onRelease,
            valueRange = range,
        )
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
        colors = transparentListColors(),
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
// detekt (1.23, K1 frontend) reads `color ?: fallback` in this @Composable as
// having an unreachable right-hand side. `color` is a nullable parameter with no
// preceding narrowing, and the Kotlin 2.2 compiler reports nothing here.
@Suppress("UnreachableCode")
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
            {
                Text(
                    stringResource(CommonR.string.common_auto),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            null
        },
        trailingContent = { Swatch(color ?: fallback) },
        colors = transparentListColors(),
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
internal fun Swatch(color: Long, size: androidx.compose.ui.unit.Dp = 28.dp) {
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
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }
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
                        label = { Text(stringResource(R.string.theme_color_hex_label)) },
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
                PickerSlider(stringResource(R.string.theme_color_hue_label), hsv.first, 0f..360f) {
                    hsv = hsv.copy(first = it); hexText = ""
                }
                PickerSlider(
                    stringResource(R.string.theme_color_saturation_label), hsv.second, 0f..1f,
                ) { hsv = hsv.copy(second = it); hexText = "" }
                PickerSlider(
                    stringResource(R.string.theme_color_brightness_label), hsv.third, 0f..1f,
                ) { hsv = hsv.copy(third = it); hexText = "" }
                if (supportsAlpha) {
                    PickerSlider(stringResource(R.string.theme_color_opacity_label), alpha, 0f..1f) {
                        alpha = it; hexText = ""
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (showReset) {
                    TextButton(onClick = onReset) { Text(stringResource(CommonR.string.common_auto)) }
                }
                TextButton(onClick = { onPick(current.argb()) }) {
                    Text(stringResource(R.string.theme_color_apply_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
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

/**
 * Where a background image comes from.
 *
 * The row used to open the device photo picker outright. Now that a photo can
 * also come from a service or from the saved library, the row asks first.
 */
@Composable
private fun BackgroundSourceDialog(
    hasImage: Boolean,
    onDevice: () -> Unit,
    onOnline: () -> Unit,
    onSaved: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_background_source_title)) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.photo_add_device_title)) },
                    supportingContent = { Text(stringResource(R.string.photo_add_device_subtitle)) },
                    leadingContent = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                    colors = transparentListColors(),
                    modifier = Modifier.clickable(onClick = onDevice),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.photo_find_title)) },
                    supportingContent = { Text(stringResource(R.string.photo_find_subtitle)) },
                    leadingContent = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    colors = transparentListColors(),
                    modifier = Modifier.clickable(onClick = onOnline),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.photo_library_title)) },
                    supportingContent = { Text(stringResource(R.string.photo_library_subtitle)) },
                    leadingContent = { Icon(Icons.Outlined.Collections, contentDescription = null) },
                    colors = transparentListColors(),
                    modifier = Modifier.clickable(onClick = onSaved),
                )
                if (hasImage) {
                    ListItem(
                        headlineContent = { Text(stringResource(CommonR.string.common_remove)) },
                        leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        colors = transparentListColors(),
                        modifier = Modifier.clickable(onClick = onRemove),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}
