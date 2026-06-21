package com.wasimaster.wmkeyboard.ime.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ArrowDropDown
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.MediaUi
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
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(restartKey) {
        visible = true
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
private fun rememberMediaImageLoader(): ImageLoader {
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
) {
    val kb = LocalKbTheme.current
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
            placeholder = if (state.mediaSearchActive) "Type to search…" else placeholder,
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
                placeholder = if (state.mediaSearchActive) "Type to search…" else placeholder,
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

/**
 * GIF/sticker picker: trending on open, live search from the search bar,
 * animated previews, tap to download & commit into the editor. With
 * several providers configured, either a chip per source or one
 * evenly-mixed grid, per the tool's settings.
 */
private fun gifNoun(stickers: Boolean): String = if (stickers) "stickers" else "GIFs"

/** "via Klipy · GIPHY" — whichever providers this grid is actually pulling from. */
private fun gifAttribution(state: KeyboardUiState): String? {
    val sources = ToolApiKeys.gifSources(state.settings)
    return when {
        sources.isEmpty() -> null
        state.settings.gifSourceMode == GifSourceMode.TABS -> "via " + GifSources.displayName(
            state.mediaSource.takeIf { it in sources } ?: sources.first(),
        )
        else -> "via " + sources.joinToString(" · ") { GifSources.displayName(it) }
    }
}

/** The GIF/sticker search box sized for a [FullBleedTool] header row. */
@Composable
internal fun RowScope.GifHeaderSearchBar(
    state: KeyboardUiState,
    stickers: Boolean,
    onQueryTap: () -> Unit,
) {
    MediaHeaderSearchBar(
        state = state,
        placeholder = "Search ${gifNoun(stickers)}",
        onQueryTap = onQueryTap,
        attribution = gifAttribution(state),
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
) {
    val ui = if (stickers) state.sticker else state.gif
    val tool = if (stickers) ToolbarTool.STICKER else ToolbarTool.GIF
    val noun = gifNoun(stickers)
    val sources = ToolApiKeys.gifSources(state.settings)
    val tabsMode = state.settings.gifSourceMode == GifSourceMode.TABS
    val sizing = if (fullBleed) {
        Modifier.fillMaxSize()
    } else {
        val height = if (state.mediaSearchActive) MediaSearchHeight else keyRowsHeight(state.settings)
        Modifier
            .fillMaxWidth()
            .height(height)
    }
    Column(modifier = sizing) {
        if (!fullBleed) {
            MediaSearchBar(
                state = state,
                placeholder = "Search $noun",
                onQueryTap = onQueryTap,
                attribution = gifAttribution(state),
            )
        }
        if (tabsMode && sources.size > 1 && !state.mediaSearchActive) {
            GifSourceChips(
                sources = sources,
                selected = state.mediaSource.takeIf { it in sources } ?: sources.first(),
                onSelect = onSourceSelect,
            )
        }
        when (ui) {
            MediaUi.NeedKey -> PanelNotice(
                "The $noun tool needs an API key — Klipy or GIPHY (both free). " +
                    "Add one in the tool's settings.",
                actionLabel = "Open settings",
                onAction = { onOpenToolSettings(tool) },
            )
            MediaUi.Loading -> PanelSpinner()
            is MediaUi.Error -> PanelNotice(ui.message, actionLabel = "Retry", onAction = onRetry)
            is MediaUi.Ready -> {
                if (ui.items.isEmpty()) {
                    PanelNotice(
                        if (ui.query.isBlank()) "Nothing trending right now"
                        else "No $noun for “${ui.query}”",
                    )
                } else {
                    GifGrid(items = ui.items, downloadingId = state.mediaDownloadingId, onSelect = onSelect)
                }
            }
        }
    }
}

/** Provider chips (tabs mode): Klipy / GIPHY. */
@Composable
private fun GifSourceChips(
    sources: List<GifSource>,
    selected: GifSource,
    onSelect: (GifSource) -> Unit,
) {
    val kb = LocalKbTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (source in sources) {
            val active = source == selected
            Text(
                GifSources.displayName(source),
                color = if (active) kb.toolCircleActiveIcon else kb.suggestionText,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(if (active) kb.toolCircleActive else kb.chip, RoundedCornerShape(12.dp))
                    .clickable { onSelect(source) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun GifGrid(
    items: List<GifItem>,
    downloadingId: String?,
    onSelect: (GifItem) -> Unit,
) {
    val loader = rememberMediaImageLoader()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items, key = { it.id }) { item ->
            Box(
                modifier = Modifier
                    .height(86.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalKbTheme.current.chip)
                    .clickable(enabled = downloadingId == null) { onSelect(item) },
            ) {
                AsyncImage(
                    model = item.previewUrl,
                    contentDescription = "GIF",
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
                "Web search needs an API key — Brave Search. Add one in the tool's settings.",
                actionLabel = "Open settings",
                onAction = { onOpenToolSettings(ToolbarTool.WEB_SEARCH) },
            )
            WebSearchUi.Idle -> PanelNotice("Type a search and press enter.\nTapping a result inserts its link.")
            WebSearchUi.Loading -> PanelSpinner()
            is WebSearchUi.Error -> PanelNotice(ui.message, actionLabel = "Retry", onAction = onRetry)
            is WebSearchUi.Ready -> {
                if (ui.results.isEmpty()) {
                    PanelNotice("No results for “${ui.query}”")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(ui.results) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onResult(result) }
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
                                        contentDescription = "Open in browser",
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
                "Image search needs an API key — Brave Search. Add one in the tool's settings.",
                actionLabel = "Open settings",
                onAction = { onOpenToolSettings(ToolbarTool.IMAGE_SEARCH) },
            )
            ImageSearchUi.Idle -> PanelNotice("Type a search and press enter.\nTap inserts the image, long-press its link.")
            ImageSearchUi.Loading -> PanelSpinner()
            is ImageSearchUi.Error -> PanelNotice(ui.message, actionLabel = "Retry", onAction = onRetry)
            is ImageSearchUi.Ready -> {
                if (ui.results.isEmpty()) {
                    PanelNotice("No images for “${ui.query}”")
                } else {
                    ImageGrid(
                        results = ui.results,
                        downloadingId = state.mediaDownloadingId,
                        onResult = onResult,
                        onResultLink = onResultLink,
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
) {
    val loader = rememberMediaImageLoader()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(results, key = { it.imageUrl }) { result ->
            Box(
                modifier = Modifier
                    .height(86.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocalKbTheme.current.chip)
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
            Text(
                (detected?.let { TranslateClient.languageName(it) } ?: "Auto-detect") + "  →",
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
                        contentDescription = "Choose language",
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
                translate.translating -> "Translating…"
                else -> "The translation shows here as you type."
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
                label = "Replace text",
                icon = Icons.Outlined.SwapVert,
                enabled = translate.translated.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { onReplace() }
            TranslateAction(
                label = "Insert",
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
