package com.wasimaster.wmkeyboard.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.core.icons.IconSlots
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.ui.toolAccentColor
import com.wasimaster.wmkeyboard.ime.ui.SlotIcon
import kotlinx.coroutines.delay

/**
 * The setting the user picked out of search, remembered just long enough for
 * its destination screen to compose and flash it.
 *
 * A plain object rather than a nav argument because the row composables that
 * do the flashing sit far below the NavHost and are shared by every screen —
 * threading an argument down to all ~200 call sites would mean touching all
 * of them, while this needs only the six row helpers to read it.
 */
internal object SettingsHighlight {
    /** Title of the row to flash, or null when nothing is pending. */
    var target: String? by mutableStateOf(null)
        private set

    /**
     * Bumped on every [request].
     *
     * A screen being torn down clears any highlight it is leaving behind, so
     * that one which found no matching row doesn't flash something unrelated
     * later. But a settings screen can also *arm* a highlight on its way out —
     * an addon's Use button does exactly that — and then it would wipe its own
     * request a frame after making it. Comparing this against the value the
     * screen saw when it opened tells the two apart.
     */
    var serial: Int by mutableIntStateOf(0)
        private set

    fun request(title: String) {
        target = title
        serial++
    }

    fun clear() {
        target = null
    }

    /** Clears only if nothing new was requested since [serialAtEntry]. */
    fun clearIfUnchanged(serialAtEntry: Int) {
        if (serial == serialAtEntry) target = null
    }
}

/**
 * Wraps a settings row so it scrolls itself into view and pulses once when it
 * is the setting the user searched for. Matching is on the title, which is
 * unique within a screen — the only scope where two rows are ever composed at
 * the same time.
 *
 * A null [title] is a row nothing can match on — it still gets the wrapper, so
 * that a group which names itself only once it has content ("Repositories")
 * keeps its children's state when the name appears. Branching on the title
 * around [content] instead would move the slot and discard everything inside.
 */
@Composable
internal fun HighlightableRow(title: String?, content: @Composable () -> Unit) {
    val requested = title != null && SettingsHighlight.target == title
    var flashing by remember { mutableStateOf(false) }
    val requester = remember { BringIntoViewRequester() }
    val color by animateColorAsState(
        targetValue = if (flashing) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else Color.Transparent,
        animationSpec = tween(durationMillis = 320),
        label = "settingHighlight",
    )
    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        // One frame's grace so the row has been placed before we scroll to it.
        delay(80)
        requester.bringIntoView()
        flashing = true
        delay(1400)
        flashing = false
        // Consumed: a later visit to the same screen must not flash again.
        SettingsHighlight.clear()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .background(color),
    ) {
        content()
    }
}

/**
 * Full-screen search over every setting. Results carry their breadcrumb, and
 * tapping one opens the owning screen with the row flashed.
 */
/**
 * The glyph each destination is drawn with, keyed by route — the settings
 * home's own icons, extended to the screens that hang off it. A result
 * therefore looks like the row it will take you to, and the screens all share
 * an icon with their rows.
 *
 * Tool routes are absent on purpose: those draw the tool's own icon, which
 * the user can replace with an icon pack.
 */
internal val SettingsRouteIcons: Map<String, ImageVector> = mapOf(
    "typing" to Icons.Outlined.Keyboard,
    "keypress" to Icons.Outlined.TouchApp,
    "languages" to Icons.Outlined.Language,
    "appearance" to Icons.Outlined.Palette,
    "themes" to Icons.Outlined.Palette,
    "fonts" to Icons.Outlined.TextFields,
    "icons" to Icons.Outlined.Image,
    "layout" to Icons.Outlined.AspectRatio,
    "keymaps" to Icons.Outlined.GridOn,
    "rows" to Icons.Outlined.ViewAgenda,
    "modes" to Icons.Outlined.Tune,
    "emoji" to Icons.Outlined.EmojiEmotions,
    "emojikeywords" to Icons.Outlined.EmojiEmotions,
    "tools" to Icons.Outlined.Widgets,
    "sticker_packs" to Icons.AutoMirrored.Outlined.StickyNote2,
    "plugins" to Icons.Outlined.Extension,
    "addons" to Icons.Outlined.Extension,
    "accessibility" to Icons.Outlined.Accessibility,
    "privacy" to Icons.Outlined.Security,
    "backup" to Icons.Outlined.Save,
    "about" to Icons.Outlined.Info,
    "licenses" to Icons.Outlined.Gavel,
    "debug_log" to Icons.AutoMirrored.Outlined.Article,
    "dictionary" to Icons.AutoMirrored.Outlined.MenuBook,
    "customdictionaries" to Icons.AutoMirrored.Outlined.MenuBook,
    "blacklist" to Icons.Outlined.VisibilityOff,
    "hwshortcuts" to Icons.Outlined.Keyboard,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSearchScreen(
    settings: KeyboardSettings,
    onBack: () -> Unit,
    onOpen: (SettingsSearchEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { searchSettings(query) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search settings") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        // The field is the app bar, so it must not draw one of
                        // its own: no container fill, no indicator line.
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            query.isBlank() -> SearchHint(Modifier.padding(padding))
            results.isEmpty() -> EmptyResults(query, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(results, key = { "${it.route}/${it.title}" }) { result ->
                    ResultRow(result, settings) {
                        keyboard?.hide()
                        onOpen(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(entry: SettingsSearchEntry, settings: KeyboardSettings, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            leadingContent = { ResultIcon(entry, settings) },
            headlineContent = { Text(entry.title) },
            supportingContent = {
                Column {
                    if (entry.subtitle.isNotBlank()) Text(entry.subtitle)
                    Text(
                        entry.screen,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            colors = transparentListColors(),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        )
    }
}

/**
 * The icon beside a result: the tool's own glyph on a tool page — icon pack
 * and accent colour included, so it matches the Tools list — otherwise the
 * icon of the screen it lives on. The magnifier is the fallback for a route
 * with no icon of its own.
 */
@Composable
private fun ResultIcon(entry: SettingsSearchEntry, settings: KeyboardSettings) {
    val tool = entry.tool
    if (tool != null) {
        SlotIcon(
            IconSlots.forTool(tool),
            contentDescription = null,
            tint = if (settings.coloredToolIcons) toolAccentColor(tool, settings.toolColorOverrides)
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Icon(
        SettingsRouteIcons[entry.route] ?: Icons.Outlined.Search,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SearchHint(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(24.dp))
        CaptionText(
            "Search every setting by name — \"haptic\", \"number row\", \"incognito\" — " +
                "or by the tool it belongs to.",
        )
    }
}

@Composable
private fun EmptyResults(query: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(24.dp))
        CaptionText("No setting matches “$query”.")
    }
}
