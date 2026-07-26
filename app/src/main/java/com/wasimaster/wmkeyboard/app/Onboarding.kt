package com.wasimaster.wmkeyboard.app

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.core.emoji.EmojiCatalog
import com.wasimaster.wmkeyboard.core.emoji.EmojiRenderCheck
import com.wasimaster.wmkeyboard.core.feedback.HapticPlayer
import com.wasimaster.wmkeyboard.core.icons.IconSlots
import com.wasimaster.wmkeyboard.ime.ui.SlotIcon
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.resolveLayout
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.settings.DefaultToolOrder
import com.wasimaster.wmkeyboard.core.settings.EmojiBarMode
import com.wasimaster.wmkeyboard.core.settings.HapticStyle
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.RecommendedTools
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.SpaceSwipeAction
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.settings.isSupportedTool
import com.wasimaster.wmkeyboard.core.theme.BuiltInThemes
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Every wizard step, in the order they're asked. Which ones actually appear is
 * decided per device and per run — see [visiblePages].
 */
private enum class OnboardingPage {
    WELCOME, LANGUAGES, LOOK, EMOJI, FEEDBACK, GESTURES, TOOLS, TOOL_SETUP,
}

/**
 * First-run wizard: enable the keyboard, then walk through the choices that
 * have no one-size-fits-all default — languages, theme, haptics, spacebar
 * gestures. Every page writes straight to DataStore, so backing out or
 * skipping keeps whatever was already chosen.
 */
@Composable
internal fun OnboardingScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // How many catalog emoji this phone's own font can't draw; null while the
    // count is still running.
    val missingEmoji = rememberUnrenderableEmojiCount()
    val pages = visiblePages(missingEmoji, settings)
    var current by rememberSaveable { mutableStateOf(OnboardingPage.WELCOME) }
    // A page can disappear under the user (the emoji count landing at zero, or
    // the tools page turning off the last tool with setup); fall back to the
    // nearest earlier page that survived rather than snapping to the start.
    val index = pages.indexOf(current).let { found ->
        if (found >= 0) found else pages.indexOfLast { it < current }.coerceAtLeast(0)
    }
    val onLastPage = index == pages.lastIndex
    // Whether the tools page has applied its recommended starting selection.
    // Hoisted here (not in the page) so leaving and revisiting the page
    // can't re-apply it over the user's choices.
    var toolsSeeded by rememberSaveable { mutableStateOf(false) }
    // Guards the Welcome page's auto-advance so it fires once — otherwise
    // navigating Back to it while already set up would bounce straight
    // forward again, defeating the Back button.
    var welcomeAutoAdvanced by rememberSaveable { mutableStateOf(false) }
    val finish: () -> Unit = {
        scope.launch { repository.setOnboardingDone(true) }
        onFinished()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageDots(index, pages.size)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = finish) { Text("Skip") }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
            ) {
                when (pages[index]) {
                    OnboardingPage.WELCOME -> WelcomePage(
                        onReady = {
                            if (!welcomeAutoAdvanced) {
                                welcomeAutoAdvanced = true
                                current = pages[(index + 1).coerceAtMost(pages.lastIndex)]
                            }
                        },
                    )
                    OnboardingPage.LANGUAGES -> LanguagesPage(repository, settings)
                    OnboardingPage.LOOK -> LookPage(repository, settings)
                    OnboardingPage.EMOJI -> EmojiPage(repository, settings, missingEmoji ?: 0)
                    OnboardingPage.FEEDBACK -> FeedbackPage(repository, settings)
                    OnboardingPage.GESTURES -> GesturesPage(repository, settings)
                    OnboardingPage.TOOLS -> ToolsPage(
                        repository, settings,
                        seeded = toolsSeeded,
                        onSeeded = { toolsSeeded = true },
                    )
                    OnboardingPage.TOOL_SETUP -> ToolSetupPage(repository, settings)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                if (index > 0) {
                    OutlinedButton(onClick = { current = pages[index - 1] }) { Text("Back") }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (onLastPage) finish() else current = pages[index + 1] }) {
                    Text(if (onLastPage) "Finish" else "Next")
                }
            }
        }
    }
}

/**
 * The wizard's live page list. Two steps are conditional: the emoji page has
 * nothing to say on a phone whose font already draws the whole catalog (and is
 * held back until the count lands, so it never flashes in and out), and the
 * tool-setup page only exists when a tool with first-run choices is enabled —
 * which the tools page right before it decides.
 */
@Composable
private fun visiblePages(
    missingEmoji: Int?,
    settings: KeyboardSettings,
): List<OnboardingPage> = remember(missingEmoji, settings.enabledTools) {
    OnboardingPage.entries.filter { page ->
        when (page) {
            OnboardingPage.EMOJI -> (missingEmoji ?: 0) > 0
            OnboardingPage.TOOL_SETUP -> settings.enabledTools.any { it in ToolSetupTools }
            else -> true
        }
    }
}

/**
 * Counts the catalog emoji this phone's own emoji font can't draw. Runs off the
 * main thread; null until the count lands.
 */
@Composable
private fun rememberUnrenderableEmojiCount(): Int? {
    val context = LocalContext.current
    var count by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        count = withContext(Dispatchers.Default) {
            val catalog = runCatching {
                context.assets.open("emoji/catalog.tsv").use { EmojiCatalog.load(it) }
            }.getOrDefault(emptyList())
            EmojiRenderCheck.unrenderable(catalog.map { it.emoji }, null).size
        }
    }
    return count
}

@Composable
private fun PageDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == current) 10.dp else 8.dp)
                    .background(
                        if (index == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- pages ----

@Composable
private fun WelcomePage(onReady: () -> Unit) {
    val context = LocalContext.current
    PageHeader(
        "Welcome to WM Keyboard",
        "An offline multilingual keyboard: ${LanguageRegistry.all.size} languages " +
            "built in, phonetic and native layouts, gesture typing, themes and " +
            "more. First, make it your keyboard.",
    )
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        SetupCard(context, onReady = onReady)
    }
    Text(
        "The next few steps set up the things everyone likes differently — " +
            "languages, look, feel and gestures. Everything can be changed later " +
            "in settings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun LanguagesPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    PageHeader(
        "Your languages",
        "Type in as many as you like — switch between them with a quick swipe on " +
            "the spacebar (or the 🌐 key). All ${LanguageRegistry.all.size} " +
            "languages are built in, so add whichever you need now or later in " +
            "Settings → Languages.",
    )
    // The enabled set, grouped by language (deduped, in switch order); toggling
    // a layout off is how you drop one during setup, and the search below adds
    // any of the rest.
    for (language in settings.enabledLanguages) {
        Text(
            language.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        val layoutIds = settings.enabledLayoutIds.filter {
            resolveLayout(settings.customLayouts, it).language().id == language.id
        }
        for (layoutId in layoutIds) {
            ListItem(
                headlineContent = { Text(resolveLayout(settings.customLayouts, layoutId).name) },
                trailingContent = {
                    Switch(
                        checked = layoutId in settings.enabledLayoutIds,
                        onCheckedChange = { enable ->
                            scope.launch {
                                val next =
                                    if (enable) settings.enabledLayoutIds + layoutId
                                    else settings.enabledLayoutIds - layoutId
                                if (next.isNotEmpty()) {
                                    repository.setEnabledLayoutIds(next.distinct())
                                }
                            }
                        },
                    )
                },
            )
            HorizontalDivider()
        }
    }
    AddLanguageSection(repository, settings)
}

/**
 * How many not-yet-added languages the onboarding list draws at once. The
 * wizard scrolls its pages in a plain `Column`, so the whole registry cannot be
 * composed the way the lazy Settings screen composes it; the search narrows the
 * list long before the cap bites, and the cap announces itself when it does.
 */
private const val ONBOARDING_LANGUAGE_LIMIT = 30

/**
 * Adds any language in the registry, without leaving the wizard. Tapping one
 * enables its default layout — the layouts it also ships, and secondary
 * suggestion sources, stay in Settings → Languages, which has room for them.
 */
@Composable
private fun AddLanguageSection(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val enabledLangIds = settings.enabledLanguages.mapTo(HashSet()) { it.id }
    val q = query.trim().lowercase()
    val matches = LanguageRegistry.all.filter { it.id !in enabledLangIds && it.matchesQuery(q) }

    Text(
        "Add a language",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search languages") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
    for (language in matches.take(ONBOARDING_LANGUAGE_LIMIT)) {
        ListItem(
            headlineContent = { Text(language.displayName) },
            supportingContent = { Text(languageRowSubtitle(language)) },
            trailingContent = {
                Icon(Icons.Outlined.Add, contentDescription = "Add ${language.englishName}")
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // A language joins by its default layout; the rest of its
                    // layouts are then togglable in the list above.
                    val first = language.layoutIds.firstOrNull() ?: return@clickable
                    scope.launch {
                        repository.setEnabledLayoutIds(
                            (settings.enabledLayoutIds + first).distinct(),
                        )
                    }
                    query = ""
                },
        )
        HorizontalDivider()
    }
    if (matches.isEmpty()) {
        CaptionText(
            if (q.isEmpty()) "Every language is already added."
            else "No languages match “$query”.",
        )
    } else if (matches.size > ONBOARDING_LANGUAGE_LIMIT) {
        CaptionText(
            "…and ${matches.size - ONBOARDING_LANGUAGE_LIMIT} more — search to " +
                "narrow the list.",
        )
    }
}

@Composable
private fun LookPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    PageHeader(
        "Look & feel",
        "How should the keyboard and app look? AMOLED is pitch black for OLED " +
            "screens; Material You tints everything from your wallpaper.",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = settings.themeMode == mode,
                onClick = { scope.launch { repository.setThemeMode(mode) } },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
            ) {
                Text(
                    when (mode) {
                        ThemeMode.SYSTEM -> "Auto"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.AMOLED -> "AMOLED"
                    },
                    maxLines = 1,
                )
            }
        }
    }
    ListItem(
        headlineContent = { Text("Material You colors") },
        supportingContent = { Text("Tint the keyboard from your wallpaper (Android 12+)") },
        trailingContent = {
            Switch(
                checked = settings.dynamicColor,
                onCheckedChange = { scope.launch { repository.setDynamicColor(it) } },
            )
        },
    )
    Text(
        "Keyboard theme",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OnboardingThemeCard(
                selected = settings.keyboardThemeId == DEFAULT_THEME_ID,
                onSelect = { scope.launch { repository.setKeyboardThemeId(DEFAULT_THEME_ID) } },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Default", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    "Material You",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
        items(BuiltInThemes, key = { it.id }) { theme ->
            OnboardingThemeCard(
                selected = settings.keyboardThemeId == theme.id,
                onSelect = { scope.launch { repository.setKeyboardThemeId(theme.id) } },
            ) {
                ThemePreview(theme, modifier = Modifier.fillMaxWidth())
                Text(
                    theme.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
    }
    Text(
        "Custom colors, background images and import/export live in " +
            "Appearance → Keyboard themes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
    Text(
        "Rows above the keys",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    Text(
        "The toolbar (suggestions, tools and the toolbox) is always there. " +
            "The emoji and symbol rows are up to you — both can be reordered " +
            "and customized later in Rows & bars.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Text(
        "Emoji row",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        val options = listOf(
            EmojiBarMode.OFF to "Off",
            EmojiBarMode.BUTTON to "Button",
            EmojiBarMode.ALWAYS to "Own row",
        )
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = settings.emojiBarMode == mode,
                onClick = { scope.launch { repository.setEmojiBarMode(mode) } },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
    ListItem(
        headlineContent = { Text("Symbol row") },
        supportingContent = {
            Text("Special characters and snippets — @gmail.com, https://, brackets — one tap away")
        },
        trailingContent = {
            Switch(
                checked = settings.symbolRowEnabled,
                onCheckedChange = { scope.launch { repository.setSymbolRowEnabled(it) } },
            )
        },
    )
}

@Composable
private fun OnboardingThemeCard(
    selected: Boolean,
    onSelect: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/**
 * Only reached when the phone's font is actually missing emoji — [missingCount]
 * is how many, and the wizard drops this page entirely when it's zero.
 */
@Composable
private fun EmojiPage(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    missingCount: Int,
) {
    val scope = rememberCoroutineScope()
    PageHeader(
        "Emoji",
        "Every phone ships its own emoji drawings, and older ones can't draw the " +
            "newest emoji — those show up as an empty box.",
    )
    Text(
        "This phone can't display $missingCount of the emoji in the catalog. They'd " +
            "show up as empty boxes.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    ListItem(
        headlineContent = { Text("Hide emoji this phone can't display") },
        supportingContent = {
            Text("Skip the empty boxes in the emoji panel, search and suggestions")
        },
        trailingContent = {
            Switch(
                checked = settings.emoji.hideUnrenderable,
                onCheckedChange = { scope.launch { repository.setHideUnrenderableEmoji(it) } },
            )
        },
    )
    Text(
        "To see them all instead, use a complete emoji font: pick \"Google\" (Noto " +
            "Color Emoji) or import an emoji font file (like Twemoji or OpenMoji) under " +
            "Emoji → Emoji font. WM Keyboard uses this phone's emoji font by default and " +
            "ships none of its own. You can change all of this later in Settings → Emoji.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun FeedbackPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // The system haptic styles are played through a real view, so hand the
    // player one — without it they fall back to a generic hardware click and
    // the chips all feel the same.
    val view = LocalView.current
    // Persisting the style is a suspending DataStore write; the buzz has to be
    // fired straight from the click instead of waiting for it to land, or the
    // chip feels dead.
    fun preview(style: HapticStyle) = HapticPlayer.preview(
        context, style, settings.hapticAmplitude, settings.hapticStrengthMs, view,
    )
    PageHeader(
        "Typing feedback",
        "Some people want every press confirmed, others want silence. " +
            "Fine-tuning (strength, duration, popups) lives in Typing settings.",
    )
    ListItem(
        headlineContent = { Text("Key press haptics") },
        supportingContent = { Text("Vibrate on every key press") },
        trailingContent = {
            Switch(
                checked = settings.hapticFeedback,
                onCheckedChange = { enable ->
                    scope.launch { repository.setHapticFeedback(enable) }
                    if (enable) preview(settings.hapticStyle)
                },
            )
        },
    )
    if (settings.hapticFeedback) {
        Text(
            "Haptic style",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        Text(
            "Tap one to feel it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            HapticStyle.entries.forEach { style ->
                FilterChip(
                    selected = settings.hapticStyle == style,
                    onClick = {
                        scope.launch { repository.setHapticStyle(style) }
                        preview(style)
                    },
                    label = { Text(style.label, maxLines = 1) },
                )
            }
        }
    }
    ListItem(
        headlineContent = { Text("Key press sound") },
        supportingContent = { Text("Click sound on every key press") },
        trailingContent = {
            Switch(
                checked = settings.keySound,
                onCheckedChange = { scope.launch { repository.setKeySound(it) } },
            )
        },
    )
    ListItem(
        headlineContent = { Text("Key popup") },
        supportingContent = { Text("Show a character bubble above the pressed key") },
        trailingContent = {
            Switch(
                checked = settings.popup.enabled,
                onCheckedChange = { scope.launch { repository.setKeyPopup(it) } },
            )
        },
    )
}

/**
 * One combined choice for the two spacebar-swipe slots, so the wizard asks
 * a single question; the two independent settings stay available under
 * Typing → Gestures.
 */
private enum class SpacebarChoice(
    val title: String,
    val subtitle: String,
    val short: SpaceSwipeAction,
    val long: SpaceSwipeAction,
) {
    CURSOR_THEN_LANGUAGE(
        "Cursor + language (recommended)",
        "Quick swipe moves the cursor · hold, then swipe switches language",
        SpaceSwipeAction.CURSOR, SpaceSwipeAction.LANGUAGE,
    ),
    LANGUAGE_THEN_CURSOR(
        "Language + cursor",
        "Quick swipe switches language · hold, then swipe moves the cursor",
        SpaceSwipeAction.LANGUAGE, SpaceSwipeAction.CURSOR,
    ),
    LANGUAGE_ONLY(
        "Only switch language",
        "Any spacebar swipe cycles through your languages",
        SpaceSwipeAction.LANGUAGE, SpaceSwipeAction.LANGUAGE,
    ),
    CURSOR_ONLY(
        "Only move the cursor",
        "Any spacebar swipe moves the text cursor",
        SpaceSwipeAction.CURSOR, SpaceSwipeAction.CURSOR,
    ),
    OFF(
        "Nothing",
        "Spacebar swipes are ignored",
        SpaceSwipeAction.NONE, SpaceSwipeAction.NONE,
    ),
}

@Composable
private fun GesturesPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    PageHeader(
        "Spacebar & keys",
        "Swiping sideways on the spacebar is the fastest way to switch language " +
            "or nudge the text cursor. What should it do?",
    )
    for (choice in SpacebarChoice.entries) {
        val selected = settings.spaceShortSwipe == choice.short &&
            settings.spaceLongSwipe == choice.long
        ListItem(
            headlineContent = { Text(choice.title) },
            supportingContent = { Text(choice.subtitle) },
            leadingContent = { RadioButton(selected = selected, onClick = null) },
            modifier = Modifier.clickable {
                scope.launch {
                    repository.setSpaceShortSwipe(choice.short)
                    repository.setSpaceLongSwipe(choice.long)
                }
            },
        )
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    ListItem(
        headlineContent = { Text("Emoji key instead of 🌐") },
        supportingContent = {
            Text("The 🌐 key becomes an emoji key — handy once language switching is on the spacebar")
        },
        trailingContent = {
            Switch(
                checked = settings.globeAsEmoji,
                onCheckedChange = { scope.launch { repository.setGlobeAsEmoji(it) } },
            )
        },
    )
    ListItem(
        headlineContent = { Text("Number row") },
        supportingContent = { Text("Dedicated 1–0 row above the letters") },
        trailingContent = {
            Switch(
                checked = settings.numberRow,
                onCheckedChange = { scope.launch { repository.setNumberRow(it) } },
            )
        },
    )
}

/** Tools with a first-run choice worth asking about on the setup page. */
private val ToolSetupTools = setOf(
    ToolbarTool.CALENDAR, ToolbarTool.WEATHER, ToolbarTool.COMPASS,
)

/**
 * Per-tool first-run choices, one section per enabled tool from
 * [ToolSetupTools]. Follows the tools page so it reflects what was just
 * switched on; every option lives in the tool's settings too.
 */
@Composable
private fun ToolSetupPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    PageHeader(
        "Set up your tools",
        "A few of the tools you enabled have choices of their own. " +
            "All of this can be changed later in each tool's settings.",
    )
    if (ToolbarTool.CALENDAR in settings.enabledTools) {
        SectionTitle("Calendar")
        AltCalendarSetting(
            title = "First calendar",
            subtitle = "Shown alongside the Gregorian month, and inside each day cell",
            selected = settings.calendarAltOne,
            onChange = { scope.launch { repository.setCalendarAltOne(it) } },
        )
        AltCalendarSetting(
            title = "Second calendar",
            subtitle = "A second one for the header and the selected day",
            selected = settings.calendarAltTwo,
            onChange = { scope.launch { repository.setCalendarAltTwo(it) } },
        )
    }
    if (ToolbarTool.WEATHER in settings.enabledTools) {
        SectionTitle("Weather")
        // The tool is dead without a place, so the same search-or-coordinates
        // editor the settings screen uses is right here rather than a pointer
        // to it.
        WeatherLocationSetting(repository, settings)
        ListItem(
            headlineContent = { Text("Fahrenheit") },
            supportingContent = { Text("Off shows temperatures in Celsius") },
            trailingContent = {
                Switch(
                    checked = settings.weatherFahrenheit,
                    onCheckedChange = { scope.launch { repository.setWeatherFahrenheit(it) } },
                )
            },
        )
    }
    if (ToolbarTool.COMPASS in settings.enabledTools) {
        SectionTitle("Compass")
        ListItem(
            headlineContent = { Text("Show qibla") },
            supportingContent = { Text("Mark the direction of the Kaaba on the compass rose") },
            trailingContent = {
                Switch(
                    checked = settings.compassShowQibla,
                    onCheckedChange = { scope.launch { repository.setCompassShowQibla(it) } },
                )
            },
        )
        if (settings.compassShowQibla) {
            Text(
                "The qibla bearing uses the same saved place as the weather tool.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            // Same place, so only offer the editor here when the weather
            // section above isn't already showing one.
            if (ToolbarTool.WEATHER !in settings.enabledTools) {
                WeatherLocationSetting(repository, settings)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToolsPage(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    seeded: Boolean,
    onSeeded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    PageHeader(
        "Your tools",
        "The toolbox packs everything from GIFs to a calculator into the " +
            "toolbar's grid button. Start with just the tools you'll actually " +
            "use — every one can be toggled any time in Tools settings.",
    )
    // First visit swaps the enable-everything default for the recommended
    // starter set — but only over an untouched default, so a user who
    // already toggled tools (or reinstalled with settings intact) keeps
    // their selection.
    LaunchedEffect(Unit) {
        if (!seeded) {
            onSeeded()
            if (settings.enabledTools.toSet() == ToolbarTool.entries.toSet()) {
                repository.setEnabledTools(RecommendedTools)
            }
        }
    }
    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = { scope.launch { repository.setEnabledTools(RecommendedTools) } }) {
            Text("Recommended")
        }
        TextButton(onClick = { scope.launch { repository.setEnabledTools(ToolbarTool.entries) } }) {
            Text("Everything")
        }
    }
    // Most-used-by-most-people first — same order the toolbox itself opens with.
    for (tool in DefaultToolOrder.filter(::isSupportedTool)) {
        ListItem(
            leadingContent = {
                SlotIcon(
                    IconSlots.forTool(tool),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            headlineContent = { Text(toolTitle(tool)) },
            supportingContent = { Text(toolDescription(tool)) },
            trailingContent = {
                Switch(
                    checked = tool in settings.enabledTools,
                    onCheckedChange = { scope.launch { repository.setToolEnabled(tool, it) } },
                )
            },
        )
    }
}
