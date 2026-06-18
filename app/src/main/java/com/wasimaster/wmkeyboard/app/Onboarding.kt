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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

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
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pageCount = 6
    // Whether the tools page has applied its recommended starting selection.
    // Hoisted here (not in the page) so leaving and revisiting the page
    // can't re-apply it over the user's choices.
    var toolsSeeded by rememberSaveable { mutableStateOf(false) }
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
                PageDots(page, pageCount)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = finish) { Text("Skip") }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
            ) {
                when (page) {
                    0 -> WelcomePage()
                    1 -> LanguagesPage(repository, settings)
                    2 -> LookPage(repository, settings)
                    3 -> FeedbackPage(repository, settings)
                    4 -> GesturesPage(repository, settings)
                    5 -> ToolsPage(
                        repository, settings,
                        seeded = toolsSeeded,
                        onSeeded = { toolsSeeded = true },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                if (page > 0) {
                    OutlinedButton(onClick = { page-- }) { Text("Back") }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (page == pageCount - 1) finish() else page++ }) {
                    Text(if (page == pageCount - 1) "Finish" else "Next")
                }
            }
        }
    }
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
private fun WelcomePage() {
    val context = LocalContext.current
    PageHeader(
        "Welcome to WM Keyboard",
        "An offline English + বাংলা keyboard: Avro phonetic typing, fixed Bengali " +
            "layouts, gesture typing, themes and more. First, make it your keyboard.",
    )
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        SetupCard(context)
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
        "Pick the input modes you type in. You'll switch between them with a " +
            "quick swipe on the spacebar (or the 🌐 key if you keep it).",
    )
    for (language in LanguageCatalog) {
        Text(
            language.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        for (option in language.layouts) {
            val mode = option.mode
            ListItem(
                headlineContent = { Text(option.title) },
                supportingContent = { Text(option.subtitle) },
                trailingContent = {
                    Switch(
                        checked = mode in settings.enabledModes,
                        onCheckedChange = { enable ->
                            scope.launch {
                                val next =
                                    if (enable) settings.enabledModes + mode
                                    else settings.enabledModes - mode
                                if (next.isNotEmpty()) repository.setEnabledModes(next.distinct())
                            }
                        },
                    )
                },
            )
            HorizontalDivider()
        }
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

@Composable
private fun FeedbackPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
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
                onCheckedChange = { scope.launch { repository.setHapticFeedback(it) } },
            )
        },
    )
    if (settings.hapticFeedback) {
        Text(
            "Haptic style",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)) {
            HapticStyle.entries.forEachIndexed { index, style ->
                SegmentedButton(
                    selected = settings.hapticStyle == style,
                    onClick = { scope.launch { repository.setHapticStyle(style) } },
                    shape = SegmentedButtonDefaults.itemShape(index, HapticStyle.entries.size),
                ) {
                    Text(
                        when (style) {
                            HapticStyle.CUSTOM -> "Custom"
                            HapticStyle.CLICK -> "Click"
                            HapticStyle.HEAVY_CLICK -> "Heavy"
                            HapticStyle.SHARP -> "Sharp"
                        },
                        maxLines = 1,
                    )
                }
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
                checked = settings.keyPopup,
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
    BOTH(
        "Language + cursor (recommended)",
        "Quick swipe switches language · hold, then swipe moves the cursor",
        SpaceSwipeAction.LANGUAGE, SpaceSwipeAction.CURSOR,
    ),
    BOTH_REVERSED(
        "Cursor + language",
        "Quick swipe moves the cursor · hold, then swipe switches language",
        SpaceSwipeAction.CURSOR, SpaceSwipeAction.LANGUAGE,
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
                Icon(
                    toolIconFor(tool),
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
