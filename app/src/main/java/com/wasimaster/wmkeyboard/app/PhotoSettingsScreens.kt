package com.wasimaster.wmkeyboard.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.PhotoBackgroundSettings
import com.wasimaster.wmkeyboard.core.settings.PoolEntry
import com.wasimaster.wmkeyboard.core.settings.RotationInterval
import com.wasimaster.wmkeyboard.core.settings.RotationScope
import com.wasimaster.wmkeyboard.core.settings.RotationSourceKind
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.tools.PhotoBackgroundManager
import com.wasimaster.wmkeyboard.core.tools.PhotoTopic
import com.wasimaster.wmkeyboard.core.tools.ToolApiKeys
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wasimaster.wmkeyboard.common.R as CommonR

/** Keys for the photo services, reached from a theme and from the picker. */
@Composable
fun PhotoServicesScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onBack: () -> Unit,
) {
    val photos = settings.photoBackground
    WmScreen(
        title = stringResource(R.string.photo_services_title),
        onBack = onBack,
        route = PHOTO_HUB_ROUTE,
        icon = { Icon(Icons.Outlined.Wallpaper, contentDescription = null) },
    ) {
        SettingsGroup(stringResource(R.string.photo_providers_section_title)) {
            item { CaptionText(stringResource(R.string.photo_providers_body)) }
            item {
                ApiKeyField(
                    label = stringResource(R.string.photo_unsplash_key_label),
                    value = photos.unsplashApiKey,
                    builtInAvailable = ToolApiKeys.builtInUnsplash,
                    emptyHint = stringResource(R.string.photo_unsplash_key_hint),
                ) { repository.setUnsplashApiKey(it) }
            }
            item {
                ApiKeyField(
                    label = stringResource(R.string.photo_pexels_key_label),
                    value = photos.pexelsApiKey,
                    builtInAvailable = ToolApiKeys.builtInPexels,
                    emptyHint = stringResource(R.string.photo_pexels_key_hint),
                ) { repository.setPexelsApiKey(it) }
            }
            item { CaptionText(stringResource(R.string.photo_licence_body)) }
        }
        HighContrastNote(settings)
    }
}

/** The background that changes on its own. Part of the theme settings. */
@Composable
fun PhotoRotationScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos = settings.photoBackground
    var poolRevision by remember { mutableIntStateOf(0) }
    val pool by produceState(initialValue = emptyList<PoolEntry>(), poolRevision) {
        value = PhotoBackgroundManager.readPool(context).entries
    }
    var intervalOpen by remember { mutableStateOf(false) }
    var topicsOpen by remember { mutableStateOf(false) }
    var scopeOpen by remember { mutableStateOf(false) }

    val devicePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { PhotoBackgroundManager.addDevicePhoto(context, it) }
                poolRevision++
            }
        }
    }

    WmScreen(
        title = stringResource(R.string.photo_rotation_title),
        onBack = onBack,
        route = PHOTO_ROTATION_ROUTE,
        icon = { Icon(Icons.Outlined.Autorenew, contentDescription = null) },
    ) {
        if (!photos.rotateEnabled) {
            // First run is one explanation and one button, rather than fifteen
            // rows for something that is not switched on.
            PhotoNotice(
                icon = Icons.Outlined.Autorenew,
                title = stringResource(R.string.photo_rotation_intro_title),
                body = stringResource(R.string.photo_rotation_intro_body),
                actionLabel = stringResource(R.string.photo_rotation_start_action),
                onAction = { scope.launch { repository.setPhotoRotateEnabled(true) } },
            )
            return@WmScreen
        }

        SettingsGroup(stringResource(R.string.photo_rotation_now_section_title)) {
            item {
                when {
                    pool.isEmpty() -> CaptionText(stringResource(R.string.photo_rotation_now_empty_body))
                    pool.size < photos.poolTarget -> CaptionText(
                        stringResource(
                            R.string.photo_rotation_filling_progress,
                            pool.size,
                            photos.poolTarget,
                        ),
                    )
                    else -> CaptionText(
                        pluralStringResource(R.plurals.photo_pool_count, pool.size, pool.size),
                    )
                }
            }
            if (!photos.hasSource) {
                item { CaptionText(stringResource(R.string.photo_rotation_no_source_body)) }
            }
            item {
                WmRow(
                    title = stringResource(R.string.photo_rotation_shuffle_title),
                    subtitle = stringResource(R.string.photo_rotation_shuffle_subtitle),
                    enabled = pool.size >= 2,
                    trailing = {
                        TextButton(
                            enabled = pool.size >= 2,
                            onClick = {
                                scope.launch {
                                    PhotoBackgroundManager.rotate(
                                        context = context,
                                        repository = repository,
                                        themeId = settings.keyboardThemeId,
                                        current = null,
                                        wantWide = photos.landscapeOnly,
                                        sources = photos.sources,
                                    )
                                    poolRevision++
                                }
                            },
                        ) { Text(stringResource(R.string.photo_rotation_shuffle_action)) }
                    },
                )
            }
        }

        SettingsGroup(stringResource(R.string.photo_rotation_schedule_section_title)) {
            item {
                ToggleSetting(
                    title = stringResource(R.string.photo_rotation_on_title),
                    subtitle = stringResource(R.string.photo_rotation_on_subtitle),
                    checked = photos.rotateEnabled,
                ) { scope.launch { repository.setPhotoRotateEnabled(it) } }
            }
            item {
                // A dialog, not a row of chips: six choices with names this
                // long do not fit across a phone.
                NavRow(
                    title = stringResource(R.string.photo_rotation_interval_title),
                    value = stringResource(photos.interval.labelRes),
                    onClick = { intervalOpen = true },
                )
            }
            item {
                NavRow(
                    title = stringResource(R.string.photo_rotation_scope_title),
                    value = stringResource(photos.scope.labelRes),
                    onClick = { scopeOpen = true },
                )
            }
        }

        SettingsGroup(stringResource(R.string.photo_rotation_sources_section_title)) {
            item {
                SourceToggle(
                    title = stringResource(R.string.photo_rotation_source_saved_title),
                    subtitle = stringResource(R.string.photo_rotation_source_saved_subtitle),
                    kind = RotationSourceKind.SAVED,
                    photos = photos,
                ) { scope.launch { repository.setPhotoRotateSources(it) } }
            }
            item {
                SourceToggle(
                    title = stringResource(R.string.photo_rotation_source_device_title),
                    subtitle = stringResource(R.string.photo_rotation_source_device_subtitle),
                    kind = RotationSourceKind.DEVICE,
                    photos = photos,
                ) { scope.launch { repository.setPhotoRotateSources(it) } }
            }
            item {
                WmRow(
                    title = stringResource(R.string.photo_rotation_device_pick_title),
                    subtitle = stringResource(R.string.photo_rotation_device_pick_subtitle),
                    icon = Icons.Outlined.PhotoLibrary,
                    onClick = {
                        devicePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }
            item {
                SourceToggle(
                    title = stringResource(R.string.photo_rotation_source_online_title),
                    subtitle = stringResource(R.string.photo_rotation_source_online_subtitle),
                    kind = RotationSourceKind.ONLINE,
                    photos = photos,
                ) { scope.launch { repository.setPhotoRotateSources(it) } }
            }
        }

        if (photos.usesNetwork) {
            SettingsGroup(stringResource(R.string.photo_rotation_online_section_title)) {
                item { CaptionText(stringResource(R.string.photo_rotation_subject_body)) }
                item {
                    NavRow(
                        title = stringResource(R.string.photo_rotation_topics_title),
                        subtitle = stringResource(R.string.photo_rotation_topics_subtitle),
                        value = pluralStringResource(
                            R.plurals.photo_rotation_topics_value,
                            photos.topics.size,
                            photos.topics.size,
                        ),
                        onClick = { topicsOpen = true },
                    )
                }
                item {
                    TextFieldSetting(
                        label = stringResource(R.string.photo_rotation_terms_label),
                        value = photos.queries.joinToString(", "),
                        hint = stringResource(R.string.photo_rotation_terms_hint),
                    ) { text ->
                        repository.setPhotoRotateQueries(
                            text.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        )
                    }
                }
                item {
                    ToggleSetting(
                        title = stringResource(R.string.photo_rotation_wide_title),
                        subtitle = stringResource(R.string.photo_rotation_wide_subtitle),
                        checked = photos.landscapeOnly,
                    ) { scope.launch { repository.setPhotoLandscapeOnly(it) } }
                }
                item {
                    ToggleSetting(
                        title = stringResource(R.string.photo_rotation_safe_title),
                        subtitle = stringResource(R.string.photo_rotation_safe_subtitle),
                        checked = photos.safeSearch,
                    ) { scope.launch { repository.setPhotoSafeSearch(it) } }
                }
                item {
                    ToggleSetting(
                        title = stringResource(R.string.photo_rotation_metered_title),
                        subtitle = stringResource(R.string.photo_rotation_metered_subtitle),
                        checked = photos.fetchOnMetered,
                    ) { scope.launch { repository.setPhotoFetchOnMetered(it) } }
                }
                item {
                    NavRow(
                        title = stringResource(R.string.photo_services_title),
                        subtitle = stringResource(R.string.photo_services_subtitle),
                        route = PHOTO_HUB_ROUTE,
                        onClick = { onNavigate(PHOTO_HUB_ROUTE) },
                    )
                }
            }
        }

        SettingsGroup(stringResource(R.string.photo_rotation_storage_section_title)) {
            item {
                SliderSetting(
                    title = stringResource(R.string.photo_rotation_pool_title),
                    value = photos.poolTarget.toFloat(),
                    range = PhotoBackgroundSettings.MIN_POOL_TARGET.toFloat()..
                        PhotoBackgroundSettings.MAX_POOL_TARGET.toFloat(),
                    display = { it.toInt().toString() },
                    info = stringResource(R.string.photo_rotation_pool_info),
                ) { scope.launch { repository.setPhotoPoolTarget(it.toInt()) } }
            }
            item {
                NavRow(
                    title = stringResource(R.string.photo_library_title),
                    subtitle = stringResource(R.string.photo_library_subtitle),
                    route = PHOTO_LIBRARY_ROUTE,
                    onClick = { onNavigate(photoLibraryRoute(null)) },
                )
            }
            item {
                WmRow(
                    title = stringResource(R.string.photo_rotation_delete_downloads_title),
                    subtitle = stringResource(R.string.photo_rotation_delete_downloads_subtitle),
                    trailing = {
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    PhotoBackgroundManager.poolDir(context)
                                        .listFiles()
                                        ?.forEach { it.delete() }
                                }
                                poolRevision++
                            }
                        }) { Text(stringResource(CommonR.string.common_delete)) }
                    },
                )
            }
        }
        HighContrastNote(settings)
    }

    if (intervalOpen) {
        RadioPickerDialog(
            title = stringResource(R.string.photo_rotation_interval_title),
            options = RotationInterval.entries.map { it to stringResource(it.labelRes) },
            selected = photos.interval,
            onDismiss = { intervalOpen = false },
        ) { picked ->
            scope.launch { repository.setPhotoRotateInterval(picked) }
            intervalOpen = false
        }
    }
    if (scopeOpen) {
        RadioPickerDialog(
            title = stringResource(R.string.photo_rotation_scope_title),
            options = RotationScope.entries.map { it to stringResource(it.labelRes) },
            selected = photos.scope,
            onDismiss = { scopeOpen = false },
        ) { picked ->
            scope.launch { repository.setPhotoRotateScope(picked) }
            scopeOpen = false
        }
    }
    if (topicsOpen) {
        TopicPickerDialog(photos.topics.toSet(), onDismiss = { topicsOpen = false }) { chosen ->
            scope.launch { repository.setPhotoRotateTopics(chosen.toList()) }
            topicsOpen = false
        }
    }
}

/** Photos the user kept, reusable on any theme. Works with no network. */
@Composable
fun PhotoLibraryScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    themeId: String,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val entries by produceState(initialValue = emptyList<PoolEntry>(), revision) {
        value = PhotoBackgroundManager.readPool(context).entries
    }
    val bytes = remember(entries) { entries.sumOf { it.bytes } }

    val devicePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { PhotoBackgroundManager.addDevicePhoto(context, it) }
                revision++
            }
        }
    }

    WmLazyScreen(
        title = stringResource(R.string.photo_library_title),
        onBack = onBack,
        route = PHOTO_LIBRARY_ROUTE,
        subtitle = themeId.takeIf { it.isNotBlank() }?.let { id ->
            settings.customThemes.find { it.id == id }?.name
        },
        subtitleInBar = true,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding(),
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                WmRow(
                    title = stringResource(R.string.photo_add_device_title),
                    subtitle = stringResource(R.string.photo_add_device_subtitle),
                    icon = Icons.Outlined.PhotoLibrary,
                    onClick = {
                        devicePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                WmRow(
                    title = stringResource(R.string.photo_storage_title),
                    subtitle = formatPhotoSize(bytes),
                )
            }
            if (entries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PhotoNotice(
                        icon = Icons.Outlined.Collections,
                        title = stringResource(R.string.photo_library_empty_title),
                        body = stringResource(R.string.photo_library_empty_body),
                        actionLabel = stringResource(R.string.photo_library_empty_action),
                        onAction = { onNavigate(photoBrowseRoute(themeId.takeIf { it.isNotBlank() })) },
                    )
                }
            } else {
                items(entries, key = { it.fileName }) { entry ->
                    SavedPhotoTile(
                        file = File(PhotoBackgroundManager.poolDir(context), entry.fileName),
                        entry = entry,
                        onApply = {
                            val target = themeId.takeIf { it.isNotBlank() } ?: settings.keyboardThemeId
                            scope.launch {
                                repository.applyThemePhoto(
                                    themeId = target,
                                    path = File(
                                        PhotoBackgroundManager.poolDir(context),
                                        entry.fileName,
                                    ).absolutePath,
                                    credit = entry.credit,
                                    landscape = false,
                                )
                                onBack()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                PhotoBackgroundManager.removeFromPool(context, entry.fileName)
                                revision++
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HighContrastNote(settings: KeyboardSettings) {
    // High-contrast keys drops background images entirely, so a photo picked
    // here would quietly do nothing. Saying so beats letting it look broken.
    if (settings.highContrastKeys) {
        CaptionText(stringResource(R.string.photo_high_contrast_body))
    }
}

@Composable
private fun SourceToggle(
    title: String,
    subtitle: String,
    kind: RotationSourceKind,
    photos: PhotoBackgroundSettings,
    onChange: (Set<RotationSourceKind>) -> Unit,
) {
    ToggleSetting(title = title, subtitle = subtitle, checked = kind in photos.sources) { on ->
        onChange(if (on) photos.sources + kind else photos.sources - kind)
    }
}

/** A radio list, for choices whose names are too long to sit in a row of chips. */
@Composable
private fun <T> RadioPickerDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { (value, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(selected = value == selected, onClick = { onPick(value) })
                        },
                        colors = transparentListColors(),
                        modifier = Modifier.clickable { onPick(value) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_close)) }
        },
    )
}

@Composable
private fun TopicPickerDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var chosen by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photo_rotation_topics_title)) },
        text = {
            LazyColumn {
                items(PhotoTopic.entries) { topic ->
                    ListItem(
                        headlineContent = { Text(stringResource(topic.labelRes)) },
                        leadingContent = {
                            Checkbox(
                                checked = topic.slug in chosen,
                                onCheckedChange = { on ->
                                    chosen = if (on) chosen + topic.slug else chosen - topic.slug
                                },
                            )
                        },
                        colors = transparentListColors(),
                        modifier = Modifier.clickable {
                            chosen = if (topic.slug in chosen) chosen - topic.slug else chosen + topic.slug
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }) {
                Text(stringResource(CommonR.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

@Composable
private fun SavedPhotoTile(
    file: File,
    entry: PoolEntry,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        AsyncImage(
            model = file,
            contentDescription = entry.credit?.photographer,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(SAVED_TILE_ASPECT)
                .clip(RoundedCornerShape(12.dp))
                .clickable { menuOpen = true },
            contentScale = ContentScale.Crop,
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_apply_action)) },
                onClick = {
                    menuOpen = false
                    onApply()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(CommonR.string.common_delete)) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

/** A rough size for a storage readout, which never needs to be exact. */
internal fun formatPhotoSize(bytes: Long): String = when {
    bytes >= BYTES_PER_MB -> "${bytes / BYTES_PER_MB} MB"
    bytes >= BYTES_PER_KB -> "${bytes / BYTES_PER_KB} kB"
    else -> "$bytes B"
}

private const val SAVED_TILE_ASPECT = 3f / 2f
private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = 1024L * 1024
