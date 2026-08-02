package com.wasimaster.wmkeyboard.ime.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.settings.GifSourceMode
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.tools.GifItem
import com.wasimaster.wmkeyboard.core.tools.GifSource
import com.wasimaster.wmkeyboard.core.tools.GifSources
import com.wasimaster.wmkeyboard.core.tools.ToolApiKeys
import com.wasimaster.wmkeyboard.core.tools.ImageResult
import com.wasimaster.wmkeyboard.core.tools.TranslateClient
import com.wasimaster.wmkeyboard.core.tools.WebResult
import com.wasimaster.wmkeyboard.ime.ImageSearchUi
import com.wasimaster.wmkeyboard.ime.FocusRegion
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.PanelMode
import com.wasimaster.wmkeyboard.ime.MediaUi
import com.wasimaster.wmkeyboard.ime.R
import com.wasimaster.wmkeyboard.ime.WebSearchUi

// ---- shared bits ----

/** Panel height while its search box is active and the key rows are shown below. */
private val MediaSearchHeight = 132.dp

/**
 * Query text plus a blinking caret, for the in-panel search fields.
 *
 * None of those are real text fields — keys are rerouted into panel state
 * instead of the focused editor — so the platform draws no cursor for them
 * and typing looked dead. This fakes the caret: it sits after the query, or
 * before the placeholder while the field is empty, and resets to solid on
 * every keystroke like a real one.
 */
@Composable
internal fun SearchQueryText(
    query: String,
    placeholder: String,
    active: Boolean,
    textColor: Color,
    placeholderColor: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (active && query.isEmpty()) {
            SearchCaret(textColor, fontSize, query)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = query.ifEmpty { placeholder },
            color = if (query.isEmpty()) placeholderColor else textColor,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (active && query.isNotEmpty()) {
            Spacer(Modifier.width(2.dp))
            SearchCaret(textColor, fontSize, query)
        }
    }
}

/** The blinking bar itself. [restartKey] resets the phase on each keystroke. */
@Composable
private fun SearchCaret(color: Color, fontSize: TextUnit, restartKey: Any?) {
    // Reduce motion holds it solid. The caret still has to be drawn — it is
    // the only thing marking where typing lands — so this is a branch on the
    // loop rather than on an animation spec: with the blink gone there is no
    // spec left to slow down, and a caret that snapped between visible and
    // hidden would be the same flicker at a harder edge.
    val reduceMotion = LocalKbTheme.current.reduceMotion
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(restartKey, reduceMotion) {
        visible = true
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(500)
            visible = !visible
        }
    }
    val height = with(LocalDensity.current) { fontSize.toDp() * 1.25f }
    Box(
        modifier = Modifier
            .width(1.5.dp)
            .height(height)
            .background(if (visible) color else Color.Transparent),
    )
}

/** One ImageLoader per composition with animated GIF/WebP support. */
@Composable
fun rememberMediaImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}

/**
 * Header-row variant of the media search field, for full-bleed panels: it
 * sits next to the back button in the [FullBleedTool] header and takes the
 * row's free width. Same key-rerouting trick as emoji search — typing goes
 * into [KeyboardUiState.mediaQuery], never the focused field.
 */
@Composable
internal fun RowScope.MediaHeaderSearchBar(
    state: KeyboardUiState,
    placeholder: String,
    onQueryTap: () -> Unit,
    attribution: String? = null,
    // Prompt shown once the field is typed into. Null falls back to the search
    // wording; tools that aren't searching (e.g. translate) pass their own.
    // A default argument cannot hold a resource, hence the null.
    activePlaceholder: String? = null,
) {
    val kb = LocalKbTheme.current
    val activeHint = activePlaceholder ?: stringResource(R.string.ime_media_search_active_hint)
    Row(
        modifier = Modifier
            .weight(1f)
            .padding(start = 6.dp, end = 4.dp)
            .background(kb.chip, RoundedCornerShape(18.dp))
            .clickable { onQueryTap() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = kb.toolbarIcon,
        )
        Spacer(Modifier.width(8.dp))
        SearchQueryText(
            query = state.mediaQuery,
            placeholder = if (state.mediaSearchActive) activeHint else placeholder,
            active = state.mediaSearchActive,
            textColor = kb.suggestionText,
            placeholderColor = kb.secondaryText,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (attribution != null) {
            Text(
                attribution,
                color = kb.secondaryText,
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/**
 * The tappable search field at the top of the media panels — same
 * key-rerouting trick as emoji search: tapping it keeps the key rows on
 * screen and typing goes into [KeyboardUiState.mediaQuery].
 */
@Composable
private fun MediaSearchBar(
    state: KeyboardUiState,
    placeholder: String,
    onQueryTap: () -> Unit,
    attribution: String? = null,
    focused: Boolean = false,
) {
    val kb = LocalKbTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .background(kb.chip, RoundedCornerShape(20.dp))
                .focusRing(focused, RoundedCornerShape(20.dp))
                .clickable { onQueryTap() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = kb.toolbarIcon,
            )
            Spacer(Modifier.width(8.dp))
            SearchQueryText(
                query = state.mediaQuery,
                placeholder = if (state.mediaSearchActive) {
                    stringResource(R.string.ime_media_search_active_hint)
                } else {
                    placeholder
                },
                active = state.mediaSearchActive,
                textColor = kb.suggestionText,
                placeholderColor = kb.secondaryText,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
        }
        if (attribution != null) {
            Text(
                attribution,
                color = kb.secondaryText,
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Centered message with an optional action chip, in panel theme colors. */
@Composable
private fun PanelNotice(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val kb = LocalKbTheme.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            color = kb.toolbarIcon,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                actionLabel,
                color = kb.toolCircleActiveIcon,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(kb.toolCircleActive, RoundedCornerShape(18.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Standing bar across the top of a media grid: this field takes no rich
 * content, so nothing here can be sent into it. Deliberately not a toast —
 * the limitation belongs to the field, not to the tap, so it stays visible
 * for as long as that field is focused.
 */
@Composable
private fun MediaUnsupportedNotice(stickers: Boolean) {
    val kb = LocalKbTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(kb.toolCircle, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = kb.toolbarIcon,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (stickers) {
                stringResource(R.string.ime_sticker_unsupported_field_notice)
            } else {
                stringResource(R.string.ime_gif_unsupported_field_notice)
            },
            color = kb.toolbarIcon,
            fontSize = 11.sp,
            maxLines = 2,
        )
    }
}

@Composable
private fun PanelSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
            color = LocalKbTheme.current.accent,
        )
    }
}

// ---- GIF & sticker panels ----

// GIF/sticker picker: trending on open, live search from the search bar,
// animated previews, tap to download & commit into the editor. With
// several providers configured, either a chip per source or one
// evenly-mixed grid, per the tool's settings.

/** The hint in the search box: GIFs and stickers each have their own wording. */
@Composable
private fun gifSearchHint(stickers: Boolean): String = stringResource(
    if (stickers) R.string.ime_sticker_search_hint else R.string.ime_gif_search_hint,
)

/** "from Klipy · GIPHY" — whichever providers this grid is actually pulling from. */
@Composable
private fun gifAttribution(state: KeyboardUiState, stickers: Boolean = false): String? {
    val sources = gifSourcesFor(state, stickers)
    val tabs = state.settings.gifSourceMode == GifSourceMode.TABS
    val targets = GifSources.targets(sources, state.mediaSource, tabs)
    // Nothing to credit for the user's own packs.
    if (targets.isEmpty() || targets == listOf(GifSource.LOCAL)) return null
    val names = StringBuilder()
    for (target in targets) {
        if (names.isNotEmpty()) names.append(" · ")
        names.append(stringResource(GifSources.displayNameRes(target)))
    }
    return stringResource(R.string.ime_media_attribution, names.toString())
}

private fun gifSourcesFor(state: KeyboardUiState, stickers: Boolean): List<GifSource> =
    if (stickers) ToolApiKeys.stickerSources(state.settings) else ToolApiKeys.gifSources(state.settings)

/** The GIF/sticker search box sized for a [FullBleedTool] header row. */
@Composable
internal fun RowScope.GifHeaderSearchBar(
    state: KeyboardUiState,
    stickers: Boolean,
    onQueryTap: () -> Unit,
) {
    MediaHeaderSearchBar(
        state = state,
        placeholder = gifSearchHint(stickers),
        onQueryTap = onQueryTap,
        attribution = gifAttribution(state, stickers),
    )
}

/**
 * @param fullBleed the panel is inside a [FullBleedTool], which owns the
 *   height and hosts the search box in its header — so the body draws
 *   neither.
 */
@Composable
internal fun GifPanel(
    state: KeyboardUiState,
    stickers: Boolean,
    onQueryTap: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (GifItem) -> Unit,
    onSourceSelect: (GifSource) -> Unit,
    onOpenToolSettings: (ToolbarTool) -> Unit,
    fullBleed: Boolean = false,
    onLongPress: (GifItem) -> Unit = {},
    onPackFilter: (String?) -> Unit = {},
    onSaveToPack: (GifItem, String?) -> Unit = { _, _ -> },
    onCopy: (GifItem) -> Unit = {},
    onReport: (GifItem) -> Unit = {},
    onDismissAction: () -> Unit = {},
    onOpenRoute: (String) -> Unit = {},
) {
    val ui = if (stickers) state.sticker else state.gif
    val tool = if (stickers) ToolbarTool.STICKER else ToolbarTool.GIF
    val sources = gifSourcesFor(state, stickers)
    val tabsMode = state.settings.gifSourceMode == GifSourceMode.TABS
    val chips = GifSources.chips(sources, tabsMode)
    val localGrid = GifSources.targets(sources, state.mediaSource, tabsMode) == listOf(GifSource.LOCAL)
    val sizing = if (fullBleed) {
        Modifier.fillMaxSize()
    } else {
        val height = if (state.mediaSearchActive) MediaSearchHeight else keyRowsHeight(state)
        Modifier
            .fillMaxWidth()
            .height(height)
    }
    Box(modifier = sizing) {
        Column(modifier = Modifier.fillMaxSize()) {
            PanelFocusTarget(
                panel = state.panel,
                region = FocusRegion.SEARCH,
                count = 1,
                columns = 1,
                onActivate = { onQueryTap() },
            )
            if (!fullBleed) {
                MediaSearchBar(
                    state = state,
                    placeholder = gifSearchHint(stickers),
                    onQueryTap = onQueryTap,
                    attribution = gifAttribution(state, stickers),
                    focused = state.focusedIndex(FocusRegion.SEARCH) == 0,
                )
            }
            if (chips.isNotEmpty() && !state.mediaSearchActive) {
                // Tab reaches the source chips: results are useless if the
                // keyboard can browse them but not switch where they come from.
                PanelFocusTarget(
                    panel = state.panel,
                    region = FocusRegion.CHIPS,
                    count = chips.size,
                    columns = chips.size,
                    onActivate = { index -> chips.getOrNull(index)?.let { onSourceSelect(it.source) } },
                )
                GifSourceChips(
                    chips = chips,
                    selectedIndex = GifSources.selectedChip(chips, state.mediaSource),
                    onSelect = onSourceSelect,
                    focused = state.focusedIndex(FocusRegion.CHIPS),
                )
            }
            if (localGrid && state.stickerPacks.size > 1 && !state.mediaSearchActive) {
                StickerPackChips(
                    packs = state.stickerPacks,
                    selected = state.stickerPackId,
                    onSelect = onPackFilter,
                )
            }
            // The field publishes the MIME types it accepts; when none of them
            // is an image, nothing this panel can send will ever arrive. Say so
            // once, up front, instead of letting every tap end in a download
            // and a "copied, paste it instead" toast.
            val unsupported = !state.acceptsRichMedia
            // Not while the search box is up — the panel is squeezed to a couple
            // of rows there, and the notice is waiting when the results land.
            if (unsupported && !state.mediaSearchActive) MediaUnsupportedNotice(stickers)
            when (ui) {
                MediaUi.NeedKey -> PanelNotice(
                    if (stickers) {
                        stringResource(R.string.ime_sticker_need_key_body)
                    } else {
                        stringResource(R.string.ime_gif_need_key_body)
                    },
                    actionLabel = stringResource(R.string.ime_open_settings_action),
                    onAction = { onOpenToolSettings(tool) },
                )
                MediaUi.Loading -> PanelSpinner()
                is MediaUi.Error -> PanelNotice(
                    ui.message,
                    actionLabel = stringResource(CommonR.string.common_retry),
                    onAction = onRetry,
                )
                is MediaUi.Ready -> {
                    if (ui.items.isEmpty()) {
                        LocalStickerEmptyNotice(
                            localGrid = localGrid,
                            state = state,
                            query = ui.query,
                            stickers = stickers,
                            onOpenRoute = onOpenRoute,
                        )
                    } else {
                        // Dimmed rather than removed when the field can't take
                        // them: long-press (save, copy, report) still works, and
                        // the user can see what they'd get in a field that
                        // accepts it.
                        Box(modifier = Modifier.alpha(if (unsupported) 0.45f else 1f)) {
                            GifGrid(
                                items = ui.items,
                                downloadingId = state.mediaDownloadingId,
                                onSelect = onSelect,
                                onLongPress = onLongPress,
                                panel = state.panel,
                                focused = state.focusedIndex(),
                            )
                        }
                    }
                }
            }
        }
        val action = state.mediaAction
        if (action != null) {
            MediaActionSheet(
                item = action,
                stickers = stickers,
                packs = state.stickerPacks,
                onSaveToPack = onSaveToPack,
                onCopy = onCopy,
                onReport = onReport,
                onOpenRoute = onOpenRoute,
                onDismiss = onDismissAction,
            )
        }
    }
}

/** The empty grid: local packs have their own way of being empty. */
@Composable
private fun LocalStickerEmptyNotice(
    localGrid: Boolean,
    state: KeyboardUiState,
    query: String,
    stickers: Boolean,
    onOpenRoute: (String) -> Unit,
) {
    when {
        !localGrid -> PanelNotice(
            when {
                query.isBlank() -> stringResource(R.string.ime_media_trending_empty)
                stickers -> stringResource(R.string.ime_sticker_search_empty, query)
                else -> stringResource(R.string.ime_gif_search_empty, query)
            },
        )
        query.isNotBlank() ->
            PanelNotice(stringResource(R.string.ime_sticker_local_search_empty, query))
        state.stickerPacks.isEmpty() -> PanelNotice(
            stringResource(R.string.ime_sticker_no_packs_empty),
            actionLabel = stringResource(R.string.ime_sticker_manage_packs_action),
            onAction = { onOpenRoute(STICKER_PACKS_ROUTE) },
        )
        else -> PanelNotice(
            stringResource(R.string.ime_sticker_pack_empty),
            actionLabel = stringResource(R.string.ime_sticker_manage_packs_action),
            onAction = { onOpenRoute(STICKER_PACKS_ROUTE) },
        )
    }
}

/** Settings route hosting the sticker pack manager. */
internal const val STICKER_PACKS_ROUTE = "sticker_packs"

/** Source chips: Klipy / GIPHY / My stickers, or Online / My stickers when mixed. */
@Composable
private fun GifSourceChips(
    chips: List<com.wasimaster.wmkeyboard.core.tools.SourceChip>,
    selectedIndex: Int,
    onSelect: (GifSource) -> Unit,
    focused: Int? = null,
) {
    val kb = LocalKbTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEachIndexed { index, chip ->
            val active = index == selectedIndex
            Text(
                stringResource(chip.labelRes),
                color = if (active) kb.toolCircleActiveIcon else kb.suggestionText,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .background(if (active) kb.toolCircleActive else kb.chip, RoundedCornerShape(12.dp))
                    .focusRing(index == focused, RoundedCornerShape(12.dp))
                    .clickable { onSelect(chip.source) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** Pack chips under the "My stickers" tab: All, then one per pack. */
@Composable
private fun StickerPackChips(
    packs: List<com.wasimaster.wmkeyboard.core.stickers.StickerPack>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val kb = LocalKbTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val allLabel = stringResource(R.string.ime_sticker_pack_all_label)
        val entries = listOf<Pair<String?, String>>(null to allLabel) + packs.map { it.id to it.name }
        for ((id, label) in entries) {
            val active = id == selected
            Text(
                label,
                color = if (active) kb.toolCircleActiveIcon else kb.suggestionText,
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .background(if (active) kb.toolCircleActive else kb.chip, RoundedCornerShape(10.dp))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Long-press menu over the GIF or sticker grid.
 *
 * An IME can't put up a dialog — it has no activity window — so this is a
 * scrim plus a panel-local surface, dismissed by tapping outside.
 *
 * Which rows show depends on where the item came from. Saving to a pack is
 * sticker-only (a GIF can't become one), and neither reporting nor saving
 * makes sense for the user's own stickers — they own the file already, and
 * there's nobody to report it to.
 */
@Composable
private fun MediaActionSheet(
    item: GifItem,
    stickers: Boolean,
    packs: List<com.wasimaster.wmkeyboard.core.stickers.StickerPack>,
    onSaveToPack: (GifItem, String?) -> Unit,
    onCopy: (GifItem) -> Unit,
    onReport: (GifItem) -> Unit,
    onOpenRoute: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val kb = LocalKbTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onDismiss()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(kb.popup)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
        ) {
            val local = item.source == GifSource.LOCAL
            if (stickers) {
                if (local) {
                    MediaActionRow(stringResource(R.string.ime_sticker_manage_packs_action)) {
                        onOpenRoute(STICKER_PACKS_ROUTE)
                    }
                } else if (packs.isEmpty()) {
                    MediaActionRow(stringResource(R.string.ime_sticker_save_new_pack_action)) {
                        onSaveToPack(item, null)
                    }
                } else {
                    for (pack in packs) {
                        val label =
                            stringResource(R.string.ime_sticker_save_to_pack_action, pack.name)
                        MediaActionRow(label) { onSaveToPack(item, pack.id) }
                    }
                }
            }
            MediaActionRow(stringResource(CommonR.string.common_copy)) { onCopy(item) }
            if (!local) {
                MediaActionRow(stringResource(R.string.ime_media_report_action)) { onReport(item) }
            }
        }
    }
}

@Composable
private fun MediaActionRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = LocalKbTheme.current.popupText,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

@Composable
private fun GifGrid(
    items: List<GifItem>,
    downloadingId: String?,
    onSelect: (GifItem) -> Unit,
    onLongPress: ((GifItem) -> Unit)? = null,
    panel: PanelMode = PanelMode.GIF,
    focused: Int? = null,
) {
    val loader = rememberMediaImageLoader()
    val gridState = rememberLazyGridState()
    PanelFocusTarget(
        panel = panel,
        count = items.size,
        columns = 3,
        onActivate = { index -> items.getOrNull(index)?.let(onSelect) },
    )
    ScrollFocusIntoView(focused) { gridState.animateScrollToItem(it) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            Box(
                modifier = Modifier
                    .height(86.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalKbTheme.current.chip)
                    .focusRing(index == focused, RoundedCornerShape(8.dp))
                    .combinedClickable(
                        enabled = downloadingId == null,
                        onClick = { onSelect(item) },
                        onLongClick = onLongPress?.let { { it(item) } },
                    ),
            ) {
                AsyncImage(
                    model = item.previewUrl,
                    contentDescription = stringResource(R.string.ime_gif_item_desc),
                    imageLoader = loader,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                if (downloadingId == item.id) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

// ---- web search panel ----

/**
 * Web search results (full-bleed body; the search bar lives in the
 * [FullBleedTool] header): tap a result to insert its link.
 */
@Composable
internal fun WebSearchPanel(
    state: KeyboardUiState,
    onRetry: () -> Unit,
    onResult: (WebResult) -> Unit,
    onOpen: (WebResult) -> Unit,
    onOpenToolSettings: (ToolbarTool) -> Unit,
) {
    val kb = LocalKbTheme.current
    Column(modifier = Modifier.fillMaxSize()) {
        when (val ui = state.webSearch) {
            WebSearchUi.NeedKey -> PanelNotice(
                stringResource(R.string.ime_web_search_need_key_body),
                actionLabel = stringResource(R.string.ime_open_settings_action),
                onAction = { onOpenToolSettings(ToolbarTool.WEB_SEARCH) },
            )
            WebSearchUi.Idle -> PanelNotice(stringResource(R.string.ime_web_search_idle))
            WebSearchUi.Loading -> PanelSpinner()
            is WebSearchUi.Error -> PanelNotice(
                ui.message,
                actionLabel = stringResource(CommonR.string.common_retry),
                onAction = onRetry,
            )
            is WebSearchUi.Ready -> {
                if (ui.results.isEmpty()) {
                    PanelNotice(stringResource(R.string.ime_web_search_empty, ui.query))
                } else {
                    val focused = state.focusedIndex()
                    val listState = rememberLazyListState()
                    PanelFocusTarget(
                        panel = PanelMode.WEB_SEARCH,
                        count = ui.results.size,
                        columns = 1,
                        // The open-in-browser icon stays touch-only; Enter does
                        // what a tap on the row does, which is insert.
                        onActivate = { index -> ui.results.getOrNull(index)?.let(onResult) },
                    )
                    ScrollFocusIntoView(focused) { listState.animateScrollToItem(it) }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(ui.results) { index, result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onResult(result) }
                                    .focusRing(index == focused, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        result.title,
                                        color = kb.accent,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        result.displayUrl,
                                        color = kb.secondaryText,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (result.snippet.isNotBlank()) {
                                        Text(
                                            result.snippet,
                                            color = kb.suggestionText,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(onClick = { onOpen(result) }) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.OpenInNew,
                                        contentDescription =
                                            stringResource(R.string.ime_web_search_open_desc),
                                        modifier = Modifier.size(18.dp),
                                        tint = kb.toolbarIcon,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- image search panel ----

/**
 * Image search: grid of thumbnails; tap inserts the image itself (via
 * commitContent, like clipboard images), long-press inserts the image URL.
 */
@Composable
internal fun ImageSearchPanel(
    state: KeyboardUiState,
    onRetry: () -> Unit,
    onResult: (ImageResult) -> Unit,
    onResultLink: (ImageResult) -> Unit,
    onOpenToolSettings: (ToolbarTool) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when (val ui = state.imageSearch) {
            ImageSearchUi.NeedKey -> PanelNotice(
                stringResource(R.string.ime_image_search_need_key_body),
                actionLabel = stringResource(R.string.ime_open_settings_action),
                onAction = { onOpenToolSettings(ToolbarTool.IMAGE_SEARCH) },
            )
            ImageSearchUi.Idle -> PanelNotice(stringResource(R.string.ime_image_search_idle))
            ImageSearchUi.Loading -> PanelSpinner()
            is ImageSearchUi.Error -> PanelNotice(
                ui.message,
                actionLabel = stringResource(CommonR.string.common_retry),
                onAction = onRetry,
            )
            is ImageSearchUi.Ready -> {
                if (ui.results.isEmpty()) {
                    PanelNotice(stringResource(R.string.ime_image_search_empty, ui.query))
                } else {
                    ImageGrid(
                        results = ui.results,
                        downloadingId = state.mediaDownloadingId,
                        onResult = onResult,
                        onResultLink = onResultLink,
                        focused = state.focusedIndex(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageGrid(
    results: List<ImageResult>,
    downloadingId: String?,
    onResult: (ImageResult) -> Unit,
    onResultLink: (ImageResult) -> Unit,
    focused: Int? = null,
) {
    val loader = rememberMediaImageLoader()
    // imageUrl is the LazyGrid key, which must be unique — Brave can return the
    // same full-image URL for two results (a widely-reposted image), and a
    // duplicate key throws during composition and crashes the IME. Dedup first.
    val uniqueResults = remember(results) { results.distinctBy { it.imageUrl } }
    val gridState = rememberLazyGridState()
    // Indexed against the deduped list, which is what the grid actually draws.
    PanelFocusTarget(
        panel = PanelMode.IMAGE_SEARCH,
        count = uniqueResults.size,
        columns = 3,
        onActivate = { index ->
            if (downloadingId == null) uniqueResults.getOrNull(index)?.let(onResult)
        },
    )
    ScrollFocusIntoView(focused) { gridState.animateScrollToItem(it) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(uniqueResults, key = { _, result -> result.imageUrl }) { index, result ->
            Box(
                modifier = Modifier
                    .height(86.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalKbTheme.current.chip)
                    .focusRing(index == focused, RoundedCornerShape(8.dp))
                    .pointerInput(result.imageUrl, downloadingId == null) {
                        detectTapGestures(
                            onTap = { if (downloadingId == null) onResult(result) },
                            onLongPress = { onResultLink(result) },
                        )
                    },
            ) {
                AsyncImage(
                    model = result.thumbUrl,
                    contentDescription = result.title,
                    imageLoader = loader,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                if (downloadingId == result.imageUrl) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

// ---- translate panel ----

/**
 * Translation window: the query types into the panel's own search bar
 * (media-search key rerouting — the focused field is never read) and the
 * result follows live. Insert types the translation at the cursor; Replace
 * swaps the whole field for it. Source language is auto-detected; the chip
 * picks the target.
 */
@Composable
internal fun TranslatePanel(
    state: KeyboardUiState,
    onTarget: (String) -> Unit,
    onReplace: () -> Unit,
    onInsert: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val translate = state.translate
    val target = state.settings.translateTargetLang
    var pickerOpen by remember { mutableStateOf(false) }
    // The panel is its own translation window: the query types into the
    // header search bar (field text is never read). The FullBleedTool
    // wrapper collapses the panel while typing — the keys sit right below
    // and the live result still fits above them.
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val detected = translate.detectedSource
                .takeIf { it.isNotBlank() && translate.translated.isNotEmpty() }
            val sourceName = detected?.let { TranslateClient.languageName(it) }
                ?: stringResource(R.string.ime_translate_auto_detect_label)
            Text(
                "$sourceName  →",
                color = kb.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Box {
                Row(
                    modifier = Modifier
                        .background(kb.chip, RoundedCornerShape(14.dp))
                        .clickable { pickerOpen = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        TranslateClient.languageName(target),
                        color = kb.suggestionText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Icon(
                        Icons.Outlined.ArrowDropDown,
                        contentDescription =
                            stringResource(R.string.ime_translate_select_language_desc),
                        modifier = Modifier.size(18.dp),
                        tint = kb.toolbarIcon,
                    )
                }
                if (pickerOpen) {
                    TranslateLanguagePicker(
                        current = target,
                        onPick = {
                            pickerOpen = false
                            onTarget(it)
                        },
                        onDismiss = { pickerOpen = false },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (translate.translating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = kb.accent,
                )
            }
        }
        Text(
            text = when {
                translate.error != null -> translate.error
                translate.translated.isNotEmpty() -> translate.translated
                translate.translating -> stringResource(R.string.ime_translate_progress)
                else -> stringResource(R.string.ime_translate_idle)
            },
            color = when {
                translate.error != null -> kb.accent
                translate.translated.isEmpty() -> kb.secondaryText
                else -> kb.suggestionText
            },
            fontSize = if (state.mediaSearchActive) 14.sp else 16.sp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TranslateAction(
                label = stringResource(R.string.ime_translate_replace_action),
                icon = Icons.Outlined.SwapVert,
                enabled = translate.translated.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { onReplace() }
            TranslateAction(
                label = stringResource(R.string.ime_insert_action),
                icon = null,
                enabled = translate.translated.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { onInsert() }
        }
        }
    }
}

@Composable
private fun TranslateAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val kb = LocalKbTheme.current
    Row(
        modifier = modifier
            .background(if (enabled) kb.toolCircleActive else kb.chip, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (enabled) kb.toolCircleActiveIcon else kb.secondaryText,
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            color = if (enabled) kb.toolCircleActiveIcon else kb.secondaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TranslateLanguagePicker(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val kb = LocalKbTheme.current
    Popup(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 180.dp, max = 240.dp)
                .heightIn(max = 260.dp)
                .background(kb.popup, RoundedCornerShape(12.dp))
                .padding(vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            for ((code, name) in TranslateClient.languages) {
                Text(
                    name,
                    color = if (code == current) kb.accent else kb.popupText,
                    fontWeight = if (code == current) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(code) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
