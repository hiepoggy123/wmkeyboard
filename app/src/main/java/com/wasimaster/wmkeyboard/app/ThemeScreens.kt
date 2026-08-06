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
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.core.feedback.KeySoundPlayer
import com.wasimaster.wmkeyboard.core.feedback.SoundStore
import com.wasimaster.wmkeyboard.core.fonts.FontStore
import com.wasimaster.wmkeyboard.core.settings.KeySoundStyle
import com.wasimaster.wmkeyboard.core.util.PlayServices
import com.wasimaster.wmkeyboard.ime.ui.KeyboardFonts
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.addons.AddonType
import com.wasimaster.wmkeyboard.core.settings.AutoThemeTrigger
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.SidePadScaleRange
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.settings.PoolEntry
import com.wasimaster.wmkeyboard.core.settings.softenedForPhoto
import com.wasimaster.wmkeyboard.core.tools.PhotoBackgroundManager
import com.wasimaster.wmkeyboard.core.theme.BuiltInThemes
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import com.wasimaster.wmkeyboard.core.theme.builtInThemeNameRes
import com.wasimaster.wmkeyboard.core.theme.GradientSpec
import com.wasimaster.wmkeyboard.core.theme.GradientType
import com.wasimaster.wmkeyboard.core.theme.DecalSpec
import com.wasimaster.wmkeyboard.core.theme.KeyEffectKind
import com.wasimaster.wmkeyboard.core.theme.KeyOverride
import com.wasimaster.wmkeyboard.core.theme.keyEffectKindOrNull
import com.wasimaster.wmkeyboard.core.theme.KeyShapeKind
import com.wasimaster.wmkeyboard.core.theme.MAX_DECALS
import com.wasimaster.wmkeyboard.core.theme.KeyTextureScale
import com.wasimaster.wmkeyboard.core.theme.keyTextureScaleOrDefault
import com.wasimaster.wmkeyboard.core.theme.SeedSwatches
import com.wasimaster.wmkeyboard.core.theme.ThemeAnimation
import com.wasimaster.wmkeyboard.core.theme.ThemeCodec
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.theme.brush
import com.wasimaster.wmkeyboard.core.theme.keyShapeFor
import com.wasimaster.wmkeyboard.core.theme.keyShapeKindOrNull
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

/** The name of a key shape, in the language of the device. */
@Composable
internal fun keyShapeName(kind: KeyShapeKind): String = stringResource(
    when (kind) {
        KeyShapeKind.ROUNDED -> R.string.theme_key_shape_rounded_label
        KeyShapeKind.SHARP -> R.string.theme_key_shape_sharp_label
        KeyShapeKind.PILL -> R.string.theme_key_shape_pill_label
        KeyShapeKind.CUT -> R.string.theme_key_shape_cut_label
        KeyShapeKind.SQUIRCLE -> R.string.theme_key_shape_squircle_label
        KeyShapeKind.ARCH -> R.string.theme_key_shape_arch_label
        KeyShapeKind.LEAF -> R.string.theme_key_shape_leaf_label
        KeyShapeKind.SLANT -> R.string.theme_key_shape_slant_label
        KeyShapeKind.HEXAGON -> R.string.theme_key_shape_hexagon_label
        KeyShapeKind.SCALLOP -> R.string.theme_key_shape_scallop_label
        KeyShapeKind.TICKET -> R.string.theme_key_shape_ticket_label
    },
)

/**
 * One key drawn in [kind], at the size and the proportions of a real key so the
 * shape reads the way it will on the keyboard. [radiusDp] is the theme's own
 * key radius, which two of the shapes follow.
 *
 * A letter key on a phone is about 31 dp across and 48 dp tall — taller than it
 * is wide. The swatch used to be 52 x 34, half again as wide as it was tall,
 * which flattered every shape that leans or points: a slant that eats a third
 * of a real key barely tilted, and the radius slider looked far gentler than it
 * is. Drawing the swatch at the real size means the same radius in dp draws the
 * same corner here and on the keyboard.
 */
@Composable
internal fun KeyShapeSwatch(kind: KeyShapeKind, radiusDp: Int, color: Color) {
    Box(
        modifier = Modifier
            // The horizontal room a key has either side of it on the keyboard,
            // which is what a leaning shape spills into. Without it the slant
            // would draw over whatever sits beside the swatch.
            .padding(horizontal = KeySwatchGapDp.dp)
            .width(31.dp)
            .height(48.dp)
            .background(color, keyShapeFor(kind, radiusDp, bleedDp = KeySwatchGapDp)),
    )
}

/** The keyboard's own horizontal key gap, which [KeyShapeSwatch] reproduces. */
private const val KeySwatchGapDp = 2.5f

/**
 * Radio list of every key shape, each row with the shape drawn beside its name.
 * Shared by the key shape and the popup shape, hence the caller's [title].
 */
@Composable
internal fun KeyShapePickerDialog(
    selected: KeyShapeKind,
    radiusDp: Int,
    onPick: (KeyShapeKind) -> Unit,
    onDismiss: () -> Unit,
    @StringRes title: Int = R.string.theme_key_shape_title,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (kind in KeyShapeKind.entries) {
                    val picked = kind == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(kind) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = picked, onClick = { onPick(kind) })
                        Spacer(Modifier.width(8.dp))
                        Text(keyShapeName(kind), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        KeyShapeSwatch(
                            kind = kind,
                            radiusDp = radiusDp,
                            // The picked shape carries the accent, so the row
                            // that is on reads at a glance from the swatches.
                            color = if (picked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
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
    onNavigate: (String) -> Unit,
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
            ChoiceControl(
                options = ThemeMode.entries.map { it to stringResource(themeModeLabelRes(it)) },
                selected = settings.themeMode,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { mode -> scope.launch { repository.setThemeMode(mode) } }
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
                ChoiceControl(
                    options = AutoThemeTrigger.entries.map { it to stringResource(it.labelRes) },
                    selected = auto.trigger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { trigger -> scope.launch { repository.setAutoThemeTrigger(trigger) } }
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
    // After the built-ins, where someone who has looked through everything the
    // app ships with is standing.
    AddonStoreGroup(AddonType.Theme, onNavigate)
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
    var shapePickerOpen by rememberSaveable(theme.id) { mutableStateOf(false) }
    var popupShapePickerOpen by rememberSaveable(theme.id) { mutableStateOf(false) }
    var toolShapePickerOpen by rememberSaveable(theme.id) { mutableStateOf(false) }
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
                    // Blurring an animation re-renders the effect every frame,
                    // so the two settings exclude each other: raising the blur
                    // stops the animation, and the switch below zeroes the blur.
                ) { update { t -> t.copy(backgroundImageBlur = it, backgroundAnimated = false) } }
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.theme_background_animated_title))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.theme_background_animated_subtitle))
                    },
                    trailingContent = {
                        Switch(
                            checked = theme.backgroundAnimated,
                            onCheckedChange = { on ->
                                update { t ->
                                    t.copy(
                                        backgroundAnimated = on,
                                        backgroundImageBlur =
                                            if (on) 0f else t.backgroundImageBlur,
                                    )
                                }
                            },
                        )
                    },
                    colors = transparentListColors(),
                )
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

    val keyRadiusDp = theme.keyCornerRadiusDp ?: settings.keyCornerRadiusDp
    if (shapePickerOpen) {
        KeyShapePickerDialog(
            selected = theme.keyShape,
            radiusDp = keyRadiusDp,
            onPick = { kind ->
                update { t -> t.copy(keyShape = kind) }
                shapePickerOpen = false
            },
            onDismiss = { shapePickerOpen = false },
        )
    }
    if (popupShapePickerOpen) {
        KeyShapePickerDialog(
            selected = keyShapeKindOrNull(theme.popupShape) ?: settings.popup.shape,
            radiusDp = theme.popupCornerRadiusDp ?: settings.popup.cornerRadiusDp,
            onPick = { kind ->
                update { t -> t.copy(popupShape = kind.name) }
                popupShapePickerOpen = false
            },
            onDismiss = { popupShapePickerOpen = false },
            title = R.string.theme_popup_shape_title,
        )
    }
    if (toolShapePickerOpen) {
        KeyShapePickerDialog(
            selected = keyShapeKindOrNull(theme.toolShape) ?: settings.toolShape,
            radiusDp = theme.toolCircleRadiusDp ?: settings.toolCircleRadiusDp,
            onPick = { kind ->
                update { t -> t.copy(toolShape = kind.name) }
                toolShapePickerOpen = false
            },
            onDismiss = { toolShapePickerOpen = false },
            title = R.string.theme_tool_shape_title,
        )
    }

    SettingsGroup(stringResource(R.string.theme_keys_section_title)) {
        item {
            // A row plus a dialog, not a segmented row: eleven shapes never fit
            // side by side, and a name on its own ("Squircle", "Leaf") does not
            // say what the key will look like. The dialog draws each one.
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_key_shape_title)) },
                supportingContent = { Text(keyShapeName(theme.keyShape)) },
                trailingContent = {
                    KeyShapeSwatch(
                        kind = theme.keyShape,
                        radiusDp = keyRadiusDp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                colors = transparentListColors(),
                modifier = Modifier.clickable { shapePickerOpen = true },
            )
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

    var texturePickerSlot by remember(theme.id) { mutableStateOf<KeyTextureSlot?>(null) }
    val texturePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val slot = texturePickerSlot
        texturePickerSlot = null
        if (uri != null && slot != null) {
            scope.launch(Dispatchers.IO) {
                runCancellable {
                    val path = importKeyTexture(context, theme.id, slot, uri)
                    if (path != null) {
                        slot.pathIn(theme)?.let { File(it).delete() }
                        repository.upsertCustomTheme(slot.withPath(theme, path))
                    }
                }
            }
        }
    }
    SettingsGroup(stringResource(R.string.theme_texture_section_title)) {
        item { CaptionText(stringResource(R.string.theme_texture_section_body)) }
        for (slot in KeyTextureSlot.entries) {
            item {
                val path = slot.pathIn(theme)
                ListItem(
                    headlineContent = { Text(stringResource(slot.titleRes)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (path != null) {
                                    R.string.theme_texture_set_label
                                } else {
                                    R.string.theme_texture_unset_label
                                },
                            ),
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Image, contentDescription = null) },
                    trailingContent = {
                        if (path != null) {
                            IconButton(
                                onClick = {
                                    update { t ->
                                        slot.pathIn(t)?.let { File(it).delete() }
                                        slot.withPath(t, null)
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription =
                                        stringResource(CommonR.string.common_remove),
                                )
                            }
                        }
                    },
                    colors = transparentListColors(),
                    modifier = Modifier.clickable {
                        texturePickerSlot = slot
                        texturePicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                )
            }
        }
        if (KeyTextureSlot.entries.any { it.pathIn(theme) != null }) {
            item {
                ChoiceControl(
                    options = KeyTextureScale.entries.map { mode ->
                        mode to stringResource(
                            when (mode) {
                                KeyTextureScale.CROP -> R.string.theme_texture_scale_crop_label
                                KeyTextureScale.STRETCH ->
                                    R.string.theme_texture_scale_stretch_label
                                KeyTextureScale.TILE -> R.string.theme_texture_scale_tile_label
                            },
                        )
                    },
                    selected = keyTextureScaleOrDefault(theme.keyTextureScale),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { mode -> update { t -> t.copy(keyTextureScale = mode.name) } }
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_texture_opacity_title),
                    value = theme.keyTextureOpacity,
                    range = 0.1f..1f,
                    display = { "${(it * 100).toInt()}%" },
                ) { update { t -> t.copy(keyTextureOpacity = (it * 100).toInt() / 100f) } }
            }
        }
    }

    var overrideEditorId by rememberSaveable(theme.id) { mutableStateOf<String?>(null) }
    var addOverrideOpen by rememberSaveable(theme.id) { mutableStateOf(false) }
    SettingsGroup(stringResource(R.string.theme_key_override_section_title)) {
        item { CaptionText(stringResource(R.string.theme_key_override_section_body)) }
        for (id in theme.keyOverrides.keys.sorted()) {
            item {
                ListItem(
                    headlineContent = { Text(keyOverrideDisplayName(id)) },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                update { t -> t.copy(keyOverrides = t.keyOverrides - id) }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(CommonR.string.common_remove),
                            )
                        }
                    },
                    colors = transparentListColors(),
                    modifier = Modifier.clickable { overrideEditorId = id },
                )
            }
        }
        item {
            OutlinedButton(
                onClick = { addOverrideOpen = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.theme_key_override_add_action)) }
        }
    }
    if (addOverrideOpen) {
        AddKeyOverrideDialog(
            onAdd = { id ->
                addOverrideOpen = false
                update { t ->
                    t.copy(keyOverrides = t.keyOverrides + (id to (t.keyOverrides[id] ?: KeyOverride())))
                }
                overrideEditorId = id
            },
            onDismiss = { addOverrideOpen = false },
        )
    }
    overrideEditorId?.let { id ->
        KeyOverrideDialog(
            id = id,
            override = theme.keyOverrides[id] ?: KeyOverride(),
            theme = theme,
            onChange = { changed ->
                update { t -> t.copy(keyOverrides = t.keyOverrides + (id to changed)) }
            },
            onDismiss = { overrideEditorId = null },
        )
    }

    var decalEditorId by rememberSaveable(theme.id) { mutableStateOf<String?>(null) }
    val decalPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCancellable {
                    val decalId = "d${System.currentTimeMillis()}"
                    val path = importDecalImage(context, theme.id, decalId, uri)
                    if (path != null) {
                        repository.upsertCustomTheme(
                            theme.copy(
                                decals = theme.decals + DecalSpec(id = decalId, image = path),
                            ),
                        )
                        decalEditorId = decalId
                    }
                }
            }
        }
    }
    SettingsGroup(stringResource(R.string.theme_decal_section_title)) {
        item { CaptionText(stringResource(R.string.theme_decal_section_body)) }
        theme.decals.forEachIndexed { index, decal ->
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.theme_decal_item_label, index + 1))
                    },
                    leadingContent = { Icon(Icons.Outlined.Image, contentDescription = null) },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                update { t ->
                                    decal.image?.let { File(it).delete() }
                                    t.copy(decals = t.decals.filterNot { it.id == decal.id })
                                }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(CommonR.string.common_remove),
                            )
                        }
                    },
                    colors = transparentListColors(),
                    modifier = Modifier.clickable { decalEditorId = decal.id },
                )
            }
        }
        if (theme.decals.size < MAX_DECALS) {
            item {
                OutlinedButton(
                    onClick = {
                        decalPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.theme_decal_add_action)) }
            }
        }
    }
    decalEditorId?.let { id ->
        DecalDialog(
            theme = theme,
            decalId = id,
            onChange = { changed ->
                update { t ->
                    t.copy(decals = t.decals.map { if (it.id == id) changed else it })
                }
            },
            onDismiss = { decalEditorId = null },
        )
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
        item {
            // Beside the popup colours rather than down in Corners, where it
            // sat behind the custom-radii switch: the shape is not a radius,
            // and a theme that wants round popups on the standard radii had to
            // turn a slider group on to reach it.
            val popupShape = keyShapeKindOrNull(theme.popupShape) ?: settings.popup.shape
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_popup_shape_title)) },
                supportingContent = { Text(keyShapeName(popupShape)) },
                trailingContent = {
                    KeyShapeSwatch(
                        kind = popupShape,
                        radiusDp = theme.popupCornerRadiusDp ?: settings.popup.cornerRadiusDp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                colors = transparentListColors(),
                modifier = Modifier.clickable { popupShapePickerOpen = true },
            )
        }
    }

    SettingsGroup(stringResource(R.string.theme_toolbar_section_title)) {
        item {
            val toolShape = keyShapeKindOrNull(theme.toolShape) ?: settings.toolShape
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_tool_shape_title)) },
                supportingContent = { Text(keyShapeName(toolShape)) },
                trailingContent = {
                    KeyShapeSwatch(
                        kind = toolShape,
                        radiusDp = theme.toolCircleRadiusDp ?: settings.toolCircleRadiusDp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                colors = transparentListColors(),
                modifier = Modifier.clickable { toolShapePickerOpen = true },
            )
        }
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
        item {
            // Colour then width, the way the key border is set: the colour is
            // what turns the outline on, and the width row appears with it.
            NullableColorRow(
                stringResource(R.string.theme_tool_border_title),
                theme.toolBorderColor, fallback = theme.toolbarIcon ?: theme.keyText,
                supportsAlpha = true,
                onChange = { update { t -> t.copy(toolBorderColor = it) } },
            )
        }
        if (theme.toolBorderColor != null) {
            item {
                SliderRow(
                    stringResource(R.string.theme_tool_border_width_title),
                    value = theme.toolBorderWidthDp,
                    range = 0f..3f,
                    display = { "%.1f dp".format(it) },
                ) { update { t -> t.copy(toolBorderWidthDp = (it * 10).toInt() / 10f) } }
            }
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
                                // Radii only. The popup and tool shapes live
                                // with their own colours now, and a theme that
                                // has picked one keeps it whether or not it
                                // also carries its own radii.
                                if (enable) {
                                    t.copy(
                                        keyCornerRadiusDp = settings.keyCornerRadiusDp,
                                        popupCornerRadiusDp = settings.popup.cornerRadiusDp,
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
                    value = (theme.popupCornerRadiusDp ?: settings.popup.cornerRadiusDp).toFloat(),
                    range = 0f..40f,
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

    // The toolbar-height field doubles as the group's on/off sentinel: the
    // switch always seeds or clears all ten fields together, so any one of
    // them being set means the group is on.
    val hasLayoutOverrides = theme.toolbarHeightDp != null
    SettingsGroup(stringResource(R.string.theme_layout_section_title)) {
        item { CaptionText(stringResource(R.string.theme_layout_section_body)) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_custom_layout_title)) },
                supportingContent = { Text(stringResource(R.string.theme_custom_layout_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = hasLayoutOverrides,
                        onCheckedChange = { enable ->
                            update { t ->
                                if (enable) {
                                    t.copy(
                                        toolWidthDp = settings.toolbarBehavior.toolWidthDp,
                                        toolbarHeightDp = settings.toolbarHeightDp,
                                        popupHeightDp = settings.popup.heightDp,
                                        keyHeightDp = settings.keyHeightDp,
                                        keyGapScale = settings.keyGapScale,
                                        sidePadScale = settings.layoutBehavior.sidePadScale,
                                        fontScale = settings.fontScale,
                                        boldKeyLabels = settings.boldKeyLabels,
                                        hintFontScale = settings.layoutBehavior.hintFontScale,
                                        gestureTrailWidthDp = settings.gesture.trailWidthDp,
                                        gestureTrailOpacity = settings.gesture.trailOpacity,
                                    )
                                } else {
                                    t.copy(
                                        toolWidthDp = null,
                                        toolbarHeightDp = null,
                                        popupHeightDp = null,
                                        keyHeightDp = null,
                                        keyGapScale = null,
                                        sidePadScale = null,
                                        fontScale = null,
                                        boldKeyLabels = null,
                                        hintFontScale = null,
                                        gestureTrailWidthDp = null,
                                        gestureTrailOpacity = null,
                                    )
                                }
                            }
                        },
                    )
                },
                colors = transparentListColors(),
            )
        }
        if (hasLayoutOverrides) {
            item {
                SliderRow(
                    stringResource(R.string.theme_tool_width_title),
                    value = (theme.toolWidthDp ?: 38).toFloat(),
                    range = 38f..64f,
                    display = { "${it.toInt()} dp" },
                ) { update { t -> t.copy(toolWidthDp = it.toInt()) } }
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_toolbar_height_title),
                    value = (theme.toolbarHeightDp ?: 44).toFloat(),
                    range = 32f..80f,
                    display = { "${it.toInt()} dp" },
                ) { update { t -> t.copy(toolbarHeightDp = it.toInt()) } }
            }
            item {
                // One height for whichever bubble style is on, the way the
                // setting itself works: the global slider keeps a separate
                // value for the on-key and the floating bubble, and the
                // override lands on the one the user is looking at.
                SliderRow(
                    stringResource(R.string.theme_popup_height_title),
                    value = (theme.popupHeightDp ?: settings.popup.heightDp).toFloat(),
                    range = 32f..160f,
                    display = { "${it.toInt()} dp" },
                ) { update { t -> t.copy(popupHeightDp = it.toInt()) } }
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_key_height_title),
                    value = (theme.keyHeightDp ?: 48).toFloat(),
                    range = 32f..100f,
                    display = { "${it.toInt()} dp" },
                ) { update { t -> t.copy(keyHeightDp = it.toInt()) } }
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_key_gap_title),
                    value = theme.keyGapScale ?: 1f,
                    range = 0f..2f,
                    display = { "%.2f×".format(it) },
                ) { update { t -> t.copy(keyGapScale = (it * 20).toInt() / 20f) } }
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_side_padding_title),
                    value = theme.sidePadScale ?: 0f,
                    range = SidePadScaleRange,
                    display = { "${(it * 100).toInt()} %" },
                ) { update { t -> t.copy(sidePadScale = (it * 100).toInt() / 100f) } }
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_font_scale_title),
                    value = theme.fontScale ?: 1f,
                    range = 0.7f..1.5f,
                    display = { "%.2f×".format(it) },
                ) { update { t -> t.copy(fontScale = (it * 20).toInt() / 20f) } }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.theme_bold_labels_title)) },
                    trailingContent = {
                        Switch(
                            checked = theme.boldKeyLabels ?: false,
                            onCheckedChange = { update { t -> t.copy(boldKeyLabels = it) } },
                        )
                    },
                    colors = transparentListColors(),
                )
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_hint_scale_title),
                    value = theme.hintFontScale ?: 1f,
                    range = 0.5f..2f,
                    display = { "%.2f×".format(it) },
                ) { update { t -> t.copy(hintFontScale = (it * 20).toInt() / 20f) } }
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_trail_width_title),
                    value = theme.gestureTrailWidthDp ?: 10f,
                    range = 2f..24f,
                    display = { "${it.toInt()} dp" },
                ) { update { t -> t.copy(gestureTrailWidthDp = it.toInt().toFloat()) } }
            }
            item {
                SliderRow(
                    stringResource(R.string.theme_trail_opacity_title),
                    value = theme.gestureTrailOpacity ?: 0.55f,
                    range = 0.1f..1f,
                    display = { "${(it * 100).toInt()} %" },
                ) { update { t -> t.copy(gestureTrailOpacity = (it * 100).toInt() / 100f) } }
            }
        }
    }

    SettingsGroup(stringResource(R.string.theme_animation_section_title)) {
        item { CaptionText(stringResource(R.string.theme_animation_section_body)) }
        item {
            ChoiceControl(
                options = ThemeAnimation.entries.map { anim ->
                    anim to when (anim) {
                        ThemeAnimation.NONE -> stringResource(CommonR.string.common_none)
                        ThemeAnimation.FLOW -> stringResource(R.string.theme_animation_flow_label)
                        ThemeAnimation.HUE_CYCLE ->
                            stringResource(R.string.theme_animation_hue_cycle_label)
                    }
                },
                selected = theme.animation,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { anim -> update { t -> t.copy(animation = anim) } }
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

    SettingsGroup(stringResource(R.string.theme_effect_section_title)) {
        item { CaptionText(stringResource(R.string.theme_effect_section_body)) }
        item {
            val current = keyEffectKindOrNull(theme.keyEffect)
            ChoiceControl(
                options = listOf<KeyEffectKind?>(null).plus(KeyEffectKind.entries).map { kind ->
                    kind to when (kind) {
                        null -> stringResource(CommonR.string.common_none)
                        KeyEffectKind.STARS -> stringResource(R.string.theme_effect_stars_label)
                        KeyEffectKind.HEARTS -> stringResource(R.string.theme_effect_hearts_label)
                        KeyEffectKind.SPARKLE ->
                            stringResource(R.string.theme_effect_sparkle_label)
                        KeyEffectKind.CONFETTI ->
                            stringResource(R.string.theme_effect_confetti_label)
                        KeyEffectKind.EMOJI -> stringResource(R.string.theme_effect_emoji_label)
                    }
                },
                selected = current,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { kind -> update { t -> t.copy(keyEffect = kind?.name) } }
        }
        if (keyEffectKindOrNull(theme.keyEffect) == KeyEffectKind.EMOJI) {
            item {
                // Local state is the source of truth while typing: a field
                // bound straight to the async theme write scrambles input when
                // the DataStore emission echoes back mid-edit.
                var emojiParam by remember(theme.id) {
                    mutableStateOf(theme.keyEffectParam.orEmpty())
                }
                OutlinedTextField(
                    value = emojiParam,
                    onValueChange = { text ->
                        val clipped = text.take(16)
                        emojiParam = clipped
                        update { t -> t.copy(keyEffectParam = clipped) }
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.theme_effect_emoji_field_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (keyEffectKindOrNull(theme.keyEffect) != null) {
            item {
                SliderRow(
                    stringResource(R.string.theme_effect_intensity_title),
                    value = theme.keyEffectIntensity,
                    range = 0.4f..2.4f,
                    display = { "%.1f×".format(it) },
                ) { update { t -> t.copy(keyEffectIntensity = (it * 10).toInt() / 10f) } }
            }
        }
    }

    var fontPickerOpen by rememberSaveable(theme.id) { mutableStateOf(false) }
    var soundPickerOpen by rememberSaveable(theme.id) { mutableStateOf(false) }
    SettingsGroup(stringResource(R.string.theme_font_sound_section_title)) {
        item { CaptionText(stringResource(R.string.theme_font_sound_section_body)) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_font_title)) },
                supportingContent = {
                    Text(
                        theme.fontId?.let { KeyboardFonts.displayName(context, it, "") }
                            ?: stringResource(R.string.theme_follow_settings_label),
                    )
                },
                modifier = Modifier.clickable { fontPickerOpen = true },
                colors = transparentListColors(),
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_sound_title)) },
                supportingContent = { Text(themeSoundLabel(theme)) },
                modifier = Modifier.clickable { soundPickerOpen = true },
                colors = transparentListColors(),
            )
        }
    }
    if (fontPickerOpen) {
        ThemeFontPickerDialog(
            current = theme.fontId,
            onPick = { id ->
                fontPickerOpen = false
                update { t -> t.copy(fontId = id) }
            },
            onDismiss = { fontPickerOpen = false },
        )
    }
    if (soundPickerOpen) {
        ThemeSoundPickerDialog(
            currentStyle = theme.soundStyle,
            currentCustomId = theme.soundCustomId,
            onPick = { style, customId ->
                soundPickerOpen = false
                update { t -> t.copy(soundStyle = style, soundCustomId = customId) }
            },
            onDismiss = { soundPickerOpen = false },
        )
    }
    Spacer(Modifier.height(24.dp))
}

/** The sound row's current-value line: a style's name, or "follow settings". */
@Composable
private fun themeSoundLabel(theme: ThemeSpec): String {
    val context = LocalContext.current
    val style = theme.soundStyle?.let { name ->
        KeySoundStyle.entries.firstOrNull { it.name == name }
    } ?: return stringResource(R.string.theme_follow_settings_label)
    if (style == KeySoundStyle.CUSTOM) {
        val name = remember(theme.soundCustomId) {
            SoundStore.get(context).sounds()
                .firstOrNull { it.id == theme.soundCustomId }?.name
        }
        if (name != null) return name
    }
    return stringResource(keySoundStyleLabelRes(style))
}

@StringRes
private fun keySoundStyleLabelRes(style: KeySoundStyle): Int = when (style) {
    KeySoundStyle.CLICK -> R.string.hardware_sound_style_click_label
    KeySoundStyle.STANDARD -> R.string.hardware_sound_style_standard_label
    KeySoundStyle.POP -> R.string.hardware_sound_style_pop_label
    KeySoundStyle.THOCK -> R.string.hardware_sound_style_thock_label
    KeySoundStyle.CHIME -> R.string.hardware_sound_style_chime_label
    KeySoundStyle.CUSTOM -> CommonR.string.common_custom
}

/**
 * Picks the theme's key font: follow the global setting, a Google face, or a
 * font from the installed library. Every row draws in its own face — the row
 * is the preview, the same trick the Fonts screen uses.
 */
@Composable
private fun ThemeFontPickerDialog(
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val installed = remember { FontStore.get(context).textFonts() }
    val googleNames =
        if (PlayServices.hasFontProvider(context)) KeyboardFonts.googleFonts else emptyList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_font_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ThemeFontChoiceRow(
                    label = stringResource(R.string.theme_follow_settings_label),
                    family = null,
                    selected = current == null,
                ) { onPick(null) }
                for (font in installed) {
                    val id = FontStore.fontIdFor(font.id)
                    ThemeFontChoiceRow(
                        label = font.name,
                        family = remember(id) { KeyboardFonts.family(context, id) },
                        selected = current == id,
                    ) { onPick(id) }
                }
                for (name in googleNames) {
                    val id = KeyboardFonts.googleId(name)
                    ThemeFontChoiceRow(
                        label = name,
                        family = remember(id) { KeyboardFonts.family(context, id) },
                        selected = current == id,
                    ) { onPick(id) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

@Composable
private fun ThemeFontChoiceRow(
    label: String,
    family: FontFamily?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            label,
            fontFamily = family,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Picks the theme's key sound. Tapping a row previews it right away — a sound
 * has to be heard to be chosen. Installed sounds each get their own row (they
 * are the CUSTOM style plus an id under the hood).
 */
@Composable
private fun ThemeSoundPickerDialog(
    currentStyle: String?,
    currentCustomId: String?,
    onPick: (style: String?, customId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val installed = remember { SoundStore.get(context).sounds() }
    val styles = KeySoundStyle.entries.filter { it != KeySoundStyle.CUSTOM }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_sound_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ThemeFontChoiceRow(
                    label = stringResource(R.string.theme_follow_settings_label),
                    family = null,
                    selected = currentStyle == null,
                ) { onPick(null, null) }
                for (style in styles) {
                    ThemeFontChoiceRow(
                        label = stringResource(keySoundStyleLabelRes(style)),
                        family = null,
                        selected = currentStyle == style.name && currentCustomId == null,
                    ) {
                        KeySoundPlayer.preview(context, style, PREVIEW_SOUND_VOLUME)
                        onPick(style.name, null)
                    }
                }
                for (sound in installed) {
                    ThemeFontChoiceRow(
                        label = sound.name,
                        family = null,
                        selected = currentStyle == KeySoundStyle.CUSTOM.name &&
                            currentCustomId == sound.id,
                    ) {
                        KeySoundPlayer.preview(
                            context, KeySoundStyle.CUSTOM, PREVIEW_SOUND_VOLUME, sound.id,
                        )
                        onPick(KeySoundStyle.CUSTOM.name, sound.id)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

/** Loud enough to judge by, without reading the user's live volume setting. */
private const val PREVIEW_SOUND_VOLUME = 0.6f

/**
 * The five texture slots of the editor's key-texture section, each knowing
 * which [ThemeSpec] field it reads and writes.
 */
private enum class KeyTextureSlot(@StringRes val titleRes: Int, val fileTag: String) {
    NORMAL(R.string.theme_texture_normal_title, "tex"),
    MODIFIER(R.string.theme_texture_modifier_title, "tex_mod"),
    ENTER(R.string.theme_texture_enter_title, "tex_enter"),
    SPACE(R.string.theme_texture_space_title, "tex_space"),
    PRESSED(R.string.theme_texture_pressed_title, "tex_press"),
    ;

    fun pathIn(theme: ThemeSpec): String? = when (this) {
        NORMAL -> theme.keyTexture
        MODIFIER -> theme.keyTextureModifier
        ENTER -> theme.keyTextureEnter
        SPACE -> theme.keyTextureSpace
        PRESSED -> theme.keyTexturePressed
    }

    fun withPath(theme: ThemeSpec, path: String?): ThemeSpec = when (this) {
        NORMAL -> theme.copy(keyTexture = path)
        MODIFIER -> theme.copy(keyTextureModifier = path)
        ENTER -> theme.copy(keyTextureEnter = path)
        SPACE -> theme.copy(keyTextureSpace = path)
        PRESSED -> theme.copy(keyTexturePressed = path)
    }
}

/**
 * The special keys the per-key style editor offers, with the id convention the
 * keyboard resolves (`keyOverrideId`): the action's name in uppercase.
 */
private val specialOverrideKeys = listOf(
    "ENTER", "SPACE", "SHIFT", "DELETE", "SYMBOLS", "EMOJI", "LANGUAGESWITCH",
)

/** How an override id reads in the editor list: `A` for a letter, `Enter` for a special. */
private fun keyOverrideDisplayName(id: String): String =
    if (id.length <= 2 && id.none { it.isUpperCase() }) {
        id.uppercase()
    } else {
        id.lowercase().replaceFirstChar { it.uppercase() }
    }

/** Picks which key gets its own style: type a letter, or choose a special key. */
@Composable
private fun AddKeyOverrideDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_key_override_add_action)) },
        text = {
            Column {
                Text(stringResource(R.string.theme_key_override_add_body))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(2) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.theme_key_override_letter_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                for (special in specialOverrideKeys) {
                    Text(
                        keyOverrideDisplayName(special),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(special) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(text.trim().lowercase()) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(CommonR.string.common_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

/** One key's own colours: face, label, border, and its preview bubble. */
@Composable
private fun KeyOverrideDialog(
    id: String,
    override: KeyOverride,
    theme: ThemeSpec,
    onChange: (KeyOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(keyOverrideDisplayName(id)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                NullableColorRow(
                    stringResource(R.string.theme_key_background_title),
                    override.background, fallback = theme.keyBackground,
                    supportsAlpha = true,
                    onChange = { onChange(override.copy(background = it)) },
                )
                NullableColorRow(
                    stringResource(R.string.theme_key_text_title),
                    override.text, fallback = theme.keyText,
                    onChange = { onChange(override.copy(text = it)) },
                )
                NullableColorRow(
                    stringResource(R.string.theme_key_border_title),
                    override.border, fallback = theme.keyBorderColor ?: theme.keyText,
                    onChange = { onChange(override.copy(border = it)) },
                )
                NullableColorRow(
                    stringResource(R.string.theme_popup_background_title),
                    override.popupBackground,
                    fallback = theme.popupBackground ?: theme.keyBackground,
                    onChange = { onChange(override.copy(popupBackground = it)) },
                )
                NullableColorRow(
                    stringResource(R.string.theme_popup_text_title),
                    override.popupText, fallback = theme.popupText ?: theme.keyText,
                    onChange = { onChange(override.copy(popupText = it)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_done)) }
        },
    )
}

/**
 * One sticker's placement, adjusted over a live preview: the miniature
 * keyboard at the top draws the decal exactly where the sliders put it.
 */
@Composable
private fun DecalDialog(
    theme: ThemeSpec,
    decalId: String,
    onChange: (DecalSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    val decal = theme.decals.firstOrNull { it.id == decalId } ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_decal_section_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ThemePreview(theme, animatedBadge = false)
                Spacer(Modifier.height(8.dp))
                SliderRow(
                    stringResource(R.string.theme_decal_x_title),
                    value = decal.x,
                    range = 0f..1f,
                    display = { "${(it * 100).toInt()}%" },
                ) { onChange(decal.copy(x = (it * 100).toInt() / 100f)) }
                SliderRow(
                    stringResource(R.string.theme_decal_y_title),
                    value = decal.y,
                    range = 0f..1f,
                    display = { "${(it * 100).toInt()}%" },
                ) { onChange(decal.copy(y = (it * 100).toInt() / 100f)) }
                SliderRow(
                    stringResource(R.string.theme_decal_size_title),
                    value = decal.scale,
                    range = 0.05f..0.8f,
                    display = { "${(it * 100).toInt()}%" },
                ) { onChange(decal.copy(scale = (it * 100).toInt() / 100f)) }
                SliderRow(
                    stringResource(R.string.theme_decal_rotation_title),
                    value = decal.rotationDeg,
                    range = -180f..180f,
                    display = { "${it.toInt()}°" },
                ) { onChange(decal.copy(rotationDeg = it.toInt().toFloat())) }
                SliderRow(
                    stringResource(R.string.theme_image_opacity_title),
                    value = decal.opacity,
                    range = 0.1f..1f,
                    display = { "${(it * 100).toInt()}%" },
                ) { onChange(decal.copy(opacity = (it * 100).toInt() / 100f)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_done)) }
        },
    )
}

/**
 * Copies a picked sticker into the theme-images folder, downscaled like a key
 * texture and kept as PNG so its transparency survives.
 */
private fun importDecalImage(
    context: android.content.Context,
    themeId: String,
    decalId: String,
    uri: android.net.Uri,
): String? = runCatching {
    val source = context.contentResolver.requireInputStream(uri).use { input ->
        android.graphics.BitmapFactory.decodeStream(input)
    } ?: return null
    val longest = maxOf(source.width, source.height)
    val scaled = if (longest > KEY_TEXTURE_IMPORT_PX) {
        val scale = KEY_TEXTURE_IMPORT_PX.toFloat() / longest
        android.graphics.Bitmap.createScaledBitmap(
            source,
            maxOf(1, (source.width * scale).toInt()),
            maxOf(1, (source.height * scale).toInt()),
            true,
        )
    } else {
        source
    }
    val file = File(themeImagesDir(context), "${themeId}_decal_${decalId}.img")
    file.outputStream().use { out ->
        scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    }
    file.absolutePath
}.getOrNull()

/** Longest edge a texture is stored at. A key never draws bigger than this. */
private const val KEY_TEXTURE_IMPORT_PX = 512

/**
 * Copies a picked texture into the theme-images folder, downscaled to at most
 * [KEY_TEXTURE_IMPORT_PX] on its longest edge — a camera photo per key would
 * bloat every export for pixels no key can show. Compressed as PNG so pixel
 * art (the most likely texture) and alpha both survive. Null when the image
 * cannot be read.
 */
private fun importKeyTexture(
    context: android.content.Context,
    themeId: String,
    slot: KeyTextureSlot,
    uri: android.net.Uri,
): String? = runCatching {
    val source = context.contentResolver.requireInputStream(uri).use { input ->
        android.graphics.BitmapFactory.decodeStream(input)
    } ?: return null
    val longest = maxOf(source.width, source.height)
    val scaled = if (longest > KEY_TEXTURE_IMPORT_PX) {
        val scale = KEY_TEXTURE_IMPORT_PX.toFloat() / longest
        android.graphics.Bitmap.createScaledBitmap(
            source,
            maxOf(1, (source.width * scale).toInt()),
            maxOf(1, (source.height * scale).toInt()),
            true,
        )
    } else {
        source
    }
    val file = File(
        themeImagesDir(context),
        "${themeId}_${slot.fileTag}_${System.currentTimeMillis()}.img",
    )
    file.outputStream().use { out ->
        scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    }
    file.absolutePath
}.getOrNull()

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
    ChoiceControl(
        options = GradientType.entries.map { type ->
            type to when (type) {
                GradientType.LINEAR -> stringResource(R.string.theme_gradient_linear_label)
                GradientType.RADIAL -> stringResource(R.string.theme_gradient_radial_label)
                GradientType.SWEEP -> stringResource(R.string.theme_gradient_sweep_label)
            }
        },
        selected = gradient.type,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) { type -> onChange(gradient.copy(type = type)) }
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
                    ChoiceControl(
                        options = listOf(
                            2.4f to stringResource(R.string.theme_crop_ratio_keyboard_label),
                            1.7f to stringResource(R.string.theme_crop_ratio_wide_label),
                            1f to stringResource(R.string.theme_crop_ratio_square_label),
                        ),
                        selected = aspect,
                    ) { value ->
                        aspect = value
                        zoom = 1f
                        offset = Offset.Zero
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

/** Shared with the sticker editor, which needs the same throttled slider. */
@Composable
internal fun SliderRow(
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
