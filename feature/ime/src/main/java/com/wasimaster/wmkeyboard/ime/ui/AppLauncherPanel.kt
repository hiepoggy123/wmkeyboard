package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.ime.AppCatalog
import com.wasimaster.wmkeyboard.ime.FocusRegion
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.LauncherActivity
import com.wasimaster.wmkeyboard.ime.LauncherApp
import com.wasimaster.wmkeyboard.ime.PanelMode
import com.wasimaster.wmkeyboard.ime.R

/**
 * The app-launcher panel's callbacks, bundled into one object rather than
 * seven parameters on [KeyboardScreen]: that function's argument list already
 * flirts with the JVM's 64K method-size ceiling at its call site, and the
 * launcher was the straw that crossed it.
 */
@androidx.compose.runtime.Immutable
class LauncherPanelCallbacks(
    val onAppTap: (LauncherApp) -> Unit = {},
    val onOpenDetail: (LauncherApp) -> Unit = {},
    val onActivityTap: (LauncherActivity) -> Unit = {},
    val onPinToggle: (String) -> Unit = {},
    val onAppInfo: (String) -> Unit = {},
    val onDetailClose: () -> Unit = {},
    /** Resolves an app's icon through the service's LRU cache, off-main. */
    val iconFor: suspend (LauncherApp) -> ImageBitmap? = { null },
)

/**
 * The app-launcher tool: a searchable grid of every launchable app, a
 * pinned/recents row, and (long-press) a per-app drill-down listing the
 * activities inside it. The service owns the catalog and the launches; this
 * panel renders [KeyboardUiState.launcherApps]/[KeyboardUiState.launcherDetail]
 * and reports taps.
 *
 * [iconFor] resolves one app's icon off the main thread through the service's
 * LRU cache — cells draw a neutral placeholder until theirs lands, so a cold
 * open never decodes a hundred adaptive icons on the composition clock.
 */
@Composable
internal fun AppLauncherPanel(
    state: KeyboardUiState,
    callbacks: LauncherPanelCallbacks,
    onQueryTap: () -> Unit,
) {
    val detail = state.launcherDetail
    if (detail != null) {
        LauncherDetail(
            state, detail.app, callbacks.iconFor,
            callbacks.onActivityTap, callbacks.onPinToggle, callbacks.onAppInfo,
        )
        return
    }
    LauncherGrid(
        state, callbacks.iconFor, callbacks.onAppTap,
        callbacks.onOpenDetail, callbacks.onPinToggle, onQueryTap,
    )
}

@Composable
private fun LauncherGrid(
    state: KeyboardUiState,
    iconFor: suspend (LauncherApp) -> ImageBitmap?,
    onAppTap: (LauncherApp) -> Unit,
    onOpenDetail: (LauncherApp) -> Unit,
    onPinToggle: (String) -> Unit,
    onQueryTap: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val launcher = state.settings.launcher
    val query = state.mediaQuery
    val apps = AppCatalog.filterApps(state.launcherApps, query)
    val sorted = AppCatalog.sortApps(
        apps,
        recentFirst = launcher.sortOrder ==
            com.wasimaster.wmkeyboard.core.settings.AppSortOrder.RECENT_FIRST,
        pinned = launcher.pinned,
        recents = launcher.recents,
    )
    // Pinned first, then recent — deduplicated so a pinned app never shows
    // twice in the shortcut row. Hidden while searching: the grid is the answer.
    val shortcuts = if (query.isEmpty() && !state.mediaSearchActive) {
        val byPackage = state.launcherApps.associateBy { it.packageName }
        (launcher.pinned + if (launcher.recentsEnabled) launcher.recents else emptyList())
            .distinct()
            .mapNotNull { byPackage[it] }
    } else {
        emptyList()
    }

    PanelFocusTarget(PanelMode.APP_LAUNCHER, 1, 1, FocusRegion.SEARCH) { onQueryTap() }
    PanelFocusTarget(PanelMode.APP_LAUNCHER, shortcuts.size, shortcuts.size, FocusRegion.CHIPS) {
        shortcuts.getOrNull(it)?.let(onAppTap)
    }
    // The grid is Adaptive, so the true column count is a layout fact; six is
    // close enough for arrow-key math on every phone width this ships on.
    PanelFocusTarget(PanelMode.APP_LAUNCHER, sorted.size, 6) {
        sorted.getOrNull(it)?.let(onAppTap)
    }

    when {
        state.launcherLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(
                    stringResource(R.string.ime_launcher_loading),
                    color = kb.secondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        sorted.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Apps,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .alpha(0.4f),
                    tint = kb.secondaryText,
                )
                Text(
                    stringResource(R.string.ime_launcher_empty),
                    color = kb.secondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        else -> Column(Modifier.fillMaxSize()) {
            if (shortcuts.isNotEmpty()) {
                Text(
                    stringResource(
                        if (launcher.pinned.isEmpty()) R.string.ime_launcher_recents_label
                        else R.string.ime_launcher_pinned_label,
                    ),
                    color = kb.secondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp),
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    rowItemsIndexed(shortcuts, key = { _, app -> app.packageName }) { _, app ->
                        ShortcutCell(
                            app = app,
                            pinned = app.packageName in launcher.pinned,
                            iconFor = iconFor,
                            onTap = { onAppTap(app) },
                            onLongPress = { onPinToggle(app.packageName) },
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 68.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
            ) {
                itemsIndexed(sorted, key = { _, app -> app.packageName }) { _, app ->
                    AppCell(
                        app = app,
                        showLabel = launcher.showLabels,
                        pinned = app.packageName in launcher.pinned,
                        iconFor = iconFor,
                        onTap = { onAppTap(app) },
                        onLongPress = {
                            if (launcher.activityDrilldown) onOpenDetail(app)
                            else onPinToggle(app.packageName)
                        },
                    )
                }
            }
        }
    }
}

/** One app's icon, resolved through the service cache, placeholder first. */
@Composable
private fun AppIcon(
    app: LauncherApp,
    sizeDp: Int,
    iconFor: suspend (LauncherApp) -> ImageBitmap?,
) {
    val kb = LocalKbTheme.current
    val icon by produceState<ImageBitmap?>(null, app.packageName, app.activityName) {
        value = iconFor(app)
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(
            bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(kb.chip, CircleShape),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(
    app: LauncherApp,
    showLabel: Boolean,
    pinned: Boolean,
    iconFor: suspend (LauncherApp) -> ImageBitmap?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = {
                    feedback()
                    onLongPress()
                },
            )
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(app, sizeDp = 42, iconFor = iconFor)
            if (pinned) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = stringResource(R.string.ime_launcher_pinned_label),
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.TopEnd),
                    tint = kb.toolCircleActiveIcon,
                )
            }
        }
        if (showLabel) {
            Text(
                app.label,
                color = kb.suggestionText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 3.dp, start = 2.dp, end = 2.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutCell(
    app: LauncherApp,
    pinned: Boolean,
    iconFor: suspend (LauncherApp) -> ImageBitmap?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = {
                    feedback()
                    onLongPress()
                },
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(app, sizeDp = 36, iconFor = iconFor)
            if (pinned) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = null,
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.TopEnd),
                    tint = kb.toolCircleActiveIcon,
                )
            }
        }
        Text(
            app.label,
            color = kb.secondaryText,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The drill-down: the app's header card (icon, label, package), an action
 * row, and every activity it declares — exported first, the rest dimmed with
 * a lock, shown only behind the setting.
 */
@Composable
private fun LauncherDetail(
    state: KeyboardUiState,
    app: LauncherApp,
    iconFor: suspend (LauncherApp) -> ImageBitmap?,
    onActivityTap: (LauncherActivity) -> Unit,
    onPinToggle: (String) -> Unit,
    onAppInfo: (String) -> Unit,
) {
    val kb = LocalKbTheme.current
    val detail = state.launcherDetail ?: return
    val launcher = state.settings.launcher
    val shown = detail.activities.filter { it.exported || launcher.showNonExported }

    PanelFocusTarget(PanelMode.APP_LAUNCHER, shown.size, 1) {
        shown.getOrNull(it)?.let(onActivityTap)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app, sizeDp = 40, iconFor = iconFor)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    app.label,
                    color = kb.suggestionText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    app.packageName,
                    color = kb.secondaryText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val pinned = app.packageName in launcher.pinned
            Icon(
                Icons.Outlined.PushPin,
                contentDescription = stringResource(
                    if (pinned) R.string.ime_launcher_unpin_action
                    else R.string.ime_launcher_pin_action,
                ),
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { onPinToggle(app.packageName) }
                    .padding(8.dp),
                tint = if (pinned) kb.toolCircleActiveIcon else kb.toolbarIcon,
            )
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.ime_launcher_app_info_action),
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { onAppInfo(app.packageName) }
                    .padding(8.dp),
                tint = kb.toolbarIcon,
            )
        }
        Text(
            stringResource(R.string.ime_launcher_activities_label),
            color = kb.secondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
        )
        if (detail.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            return@Column
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            rowItemsIndexed(shown, key = { _, a -> "${a.packageName}/${a.className}" }) { _, activity ->
                val launchable = activity.exported && activity.enabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onActivityTap(activity) }
                        .alpha(if (launchable) 1f else 0.45f)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            activity.label,
                            color = kb.suggestionText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            activity.className,
                            color = kb.secondaryText,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!launchable) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription =
                                stringResource(R.string.ime_launcher_not_exported_label),
                            modifier = Modifier
                                .size(14.dp)
                                .padding(start = 2.dp),
                            tint = kb.secondaryText,
                        )
                    }
                    Spacer(Modifier.width(0.dp))
                }
            }
        }
    }
}
