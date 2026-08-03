package com.wasimaster.wmkeyboard.app

import android.os.Build
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.emoji.EmojiCatalog
import com.wasimaster.wmkeyboard.core.emoji.EmojiRenderCheck
import com.wasimaster.wmkeyboard.core.feedback.HapticPlayer
import com.wasimaster.wmkeyboard.core.fonts.FontStore
import com.wasimaster.wmkeyboard.core.icons.IconSlots
import com.wasimaster.wmkeyboard.ime.ui.SlotIcon
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.resolveLayout
import com.wasimaster.wmkeyboard.core.script.DeviceLocales
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.script.LanguageSuggestions
import com.wasimaster.wmkeyboard.core.settings.DefaultToolOrder
import com.wasimaster.wmkeyboard.core.settings.EmojiBarMode
import com.wasimaster.wmkeyboard.core.settings.EmojiFontChoice
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
    SeedLanguagesFromDevice(repository, settings)
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
                TextButton(onClick = finish) { Text(stringResource(CommonR.string.common_skip)) }
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
                    OutlinedButton(onClick = { current = pages[index - 1] }) {
                        Text(stringResource(CommonR.string.common_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (onLastPage) finish() else current = pages[index + 1] }) {
                    Text(
                        stringResource(
                            if (onLastPage) R.string.onboarding_finish_action
                            else CommonR.string.common_next,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Starts a fresh install in the languages the phone is already set to.
 *
 * Runs at the top of the wizard rather than on the languages page, so someone
 * who taps Skip on the very first screen still gets a keyboard that matches
 * their phone instead of a fixed pair of languages.
 *
 * Only ever fires when the enabled set is still *exactly* the built-in fallback:
 * that is the one state nobody can have chosen on purpose during onboarding, and
 * checking it means a phone whose keyboard was set up from the system settings
 * before the app was ever opened keeps what it was given.
 */
@Composable
private fun SeedLanguagesFromDevice(repository: SettingsRepository, settings: KeyboardSettings) {
    val context = LocalContext.current
    val untouched = settings.enabledLayoutIds == BuiltInLayouts.defaultEnabledIds
    // rememberSaveable, so a rotation mid-wizard can't re-seed over a language
    // the user has since removed on the page below.
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(untouched) {
        if (seeded || !untouched) return@LaunchedEffect
        seeded = true
        val ids = withContext(Dispatchers.IO) {
            LanguageSuggestions.seedLayoutIds(DeviceLocales.read(context))
        }
        if (ids.isNotEmpty()) repository.setEnabledLayoutIds(ids)
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
            OnboardingPage.EMOJI -> (missingEmoji ?: 0) > 0 || shipsOwnEmojiSet()
            OnboardingPage.TOOL_SETUP -> settings.enabledTools.any { it in ToolSetupTools }
            else -> true
        }
    }
}

/**
 * Whether this phone draws emoji in the maker's own set rather than the Android
 * one. Nothing is missing on those phones — the count above is zero — but the
 * glyphs are not what most people have seen everywhere else, and swapping the
 * font is not a setting anybody goes looking for.
 *
 * Samsung by name because Samsung is the one that ships a complete set of its
 * own; the check is deliberately not "any OEM", which would put the step in
 * front of people whose emoji are already the standard ones.
 */
private fun shipsOwnEmojiSet(): Boolean =
    Build.MANUFACTURER.equals("samsung", ignoreCase = true)

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
        stringResource(R.string.onboarding_welcome_title),
        pluralStringResource(
            R.plurals.onboarding_welcome_subtitle,
            LanguageRegistry.all.size,
            LanguageRegistry.all.size,
        ),
    )
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        SetupCard(context, onReady = onReady)
    }
    Text(
        stringResource(R.string.onboarding_welcome_body),
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
        stringResource(R.string.onboarding_languages_title),
        pluralStringResource(
            R.plurals.onboarding_languages_subtitle,
            LanguageRegistry.all.size,
            LanguageRegistry.all.size,
        ),
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
 * How many device-derived suggestions the wizard offers. Shorter than the
 * settings screen's list: this one sits above the search box on a page the user
 * is trying to get past, so it has to stay glanceable.
 */
private const val ONBOARDING_SUGGESTION_LIMIT = 4

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
    val suggested = rememberSuggestedLanguages(settings, limit = ONBOARDING_SUGGESTION_LIMIT)

    // Above the search box, because for most people this is the whole step:
    // the languages their phone is already in are the ones they came to add.
    if (suggested.isNotEmpty()) {
        Text(
            stringResource(R.string.onboarding_language_suggested_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        )
        for (suggestion in suggested) {
            ListItem(
                headlineContent = { Text(suggestion.language.displayName) },
                supportingContent = { Text(suggestionReasonLabel(suggestion)) },
                trailingContent = {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = stringResource(
                            R.string.onboarding_language_add_desc,
                            suggestion.language.englishName,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { addLanguage(scope, repository, settings, suggestion.language) },
            )
            HorizontalDivider()
        }
        CaptionText(stringResource(R.string.onboarding_language_suggested_info))
    }

    Text(
        stringResource(R.string.onboarding_language_add_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text(stringResource(R.string.onboarding_language_search_hint)) },
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
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(
                        R.string.onboarding_language_add_desc,
                        language.englishName,
                    ),
                )
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
            if (q.isEmpty()) stringResource(R.string.onboarding_language_all_added)
            else stringResource(R.string.onboarding_language_no_match, query),
        )
    } else if (matches.size > ONBOARDING_LANGUAGE_LIMIT) {
        val extra = matches.size - ONBOARDING_LANGUAGE_LIMIT
        CaptionText(pluralStringResource(R.plurals.onboarding_language_more_count, extra, extra))
    }
}

@Composable
private fun LookPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    PageHeader(
        stringResource(R.string.onboarding_look_title),
        stringResource(R.string.onboarding_look_subtitle),
    )
    ChoiceControl(
        options = ThemeMode.entries.map { mode ->
            mode to stringResource(
                when (mode) {
                    ThemeMode.SYSTEM -> R.string.onboarding_theme_mode_auto
                    ThemeMode.LIGHT -> R.string.onboarding_theme_mode_light
                    ThemeMode.DARK -> R.string.onboarding_theme_mode_dark
                    ThemeMode.AMOLED -> R.string.onboarding_theme_mode_amoled
                },
            )
        },
        selected = settings.themeMode,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) { mode -> scope.launch { repository.setThemeMode(mode) } }
    ListItem(
        headlineContent = { Text(stringResource(R.string.onboarding_dynamic_color_title)) },
        supportingContent = {
            Text(stringResource(R.string.onboarding_dynamic_color_subtitle))
        },
        trailingContent = {
            Switch(
                checked = settings.dynamicColor,
                onCheckedChange = { scope.launch { repository.setDynamicColor(it) } },
            )
        },
    )
    Text(
        stringResource(R.string.onboarding_keyboard_theme_title),
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
                    Text(
                        stringResource(CommonR.string.common_default),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    stringResource(R.string.onboarding_keyboard_theme_material_you_label),
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
        stringResource(R.string.onboarding_keyboard_theme_info),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
    Text(
        stringResource(R.string.onboarding_rows_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    Text(
        stringResource(R.string.onboarding_rows_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Text(
        stringResource(R.string.onboarding_emoji_row_label),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
    )
    ChoiceControl(
        options = listOf(
            EmojiBarMode.OFF to CommonR.string.common_off,
            EmojiBarMode.BUTTON to R.string.onboarding_emoji_row_button,
            EmojiBarMode.ALWAYS to R.string.onboarding_emoji_row_always,
        ).map { (mode, labelRes) -> mode to stringResource(labelRes) },
        selected = settings.emojiBarMode,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) { mode -> scope.launch { repository.setEmojiBarMode(mode) } }
    ListItem(
        headlineContent = { Text(stringResource(R.string.onboarding_symbol_row_title)) },
        supportingContent = {
            Text(stringResource(R.string.onboarding_symbol_row_subtitle))
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
    val context = LocalContext.current
    PageHeader(
        stringResource(R.string.onboarding_emoji_title),
        stringResource(R.string.onboarding_emoji_subtitle),
    )
    Text(
        if (missingCount > 0) {
            pluralStringResource(
                R.plurals.onboarding_emoji_missing_count,
                missingCount,
                missingCount,
            )
        } else {
            // The other way onto this page: everything draws, it just draws in
            // the phone maker's own set.
            stringResource(R.string.onboarding_emoji_own_set_body)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    // Installed faces are only worth offering when there is one to pick.
    val installedFonts = remember { FontStore.get(context).emojiFonts() }
    val fontOptions = buildList {
        add(EmojiFontChoice.SYSTEM to stringResource(R.string.langemoji_emoji_font_system_label))
        add(EmojiFontChoice.NOTO to stringResource(R.string.langemoji_emoji_font_noto_label))
        if (installedFonts.isNotEmpty()) {
            add(
                EmojiFontChoice.INSTALLED to
                    stringResource(R.string.langemoji_emoji_font_installed_label),
            )
        }
    }
    ChoiceControl(
        options = fontOptions,
        // A font imported and then deleted leaves the setting pointing at
        // nothing; show that as the system set, which is what is drawn anyway.
        selected = settings.emojiFont.takeIf { choice ->
            fontOptions.any { it.first == choice }
        } ?: EmojiFontChoice.SYSTEM,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) { choice -> scope.launch { repository.setEmojiFont(choice) } }
    EmojiFontPreviewRow(
        choice = settings.emojiFont,
        installedId = settings.emojiFontInstalled.installedId,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    if (missingCount > 0) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.onboarding_emoji_hide_title)) },
            supportingContent = {
                Text(stringResource(R.string.onboarding_emoji_hide_subtitle))
            },
            trailingContent = {
                Switch(
                    checked = settings.emoji.hideUnrenderable,
                    onCheckedChange = { scope.launch { repository.setHideUnrenderableEmoji(it) } },
                )
            },
        )
    }
    Text(
        stringResource(R.string.onboarding_emoji_font_info),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
    // The wizard cannot open the add-on store — there is nowhere to come back
    // to mid-setup — so this page says where the fonts are rather than
    // offering to fetch one now.
    Text(
        stringResource(R.string.onboarding_emoji_font_download_info),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
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
        stringResource(R.string.onboarding_feedback_title),
        stringResource(R.string.onboarding_feedback_subtitle),
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.onboarding_haptics_title)) },
        supportingContent = { Text(stringResource(R.string.onboarding_haptics_subtitle)) },
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
            stringResource(R.string.onboarding_haptic_style_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        Text(
            stringResource(R.string.onboarding_haptic_style_hint),
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
                    label = { Text(stringResource(style.labelRes), maxLines = 1) },
                )
            }
        }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.onboarding_key_sound_title)) },
        supportingContent = { Text(stringResource(R.string.onboarding_key_sound_subtitle)) },
        trailingContent = {
            Switch(
                checked = settings.keySound,
                onCheckedChange = { scope.launch { repository.setKeySound(it) } },
            )
        },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.onboarding_key_popup_title)) },
        supportingContent = { Text(stringResource(R.string.onboarding_key_popup_subtitle)) },
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
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val short: SpaceSwipeAction,
    val long: SpaceSwipeAction,
) {
    CURSOR_THEN_LANGUAGE(
        R.string.onboarding_spacebar_cursor_language_title,
        R.string.onboarding_spacebar_cursor_language_subtitle,
        SpaceSwipeAction.CURSOR, SpaceSwipeAction.LANGUAGE,
    ),
    LANGUAGE_THEN_CURSOR(
        R.string.onboarding_spacebar_language_cursor_title,
        R.string.onboarding_spacebar_language_cursor_subtitle,
        SpaceSwipeAction.LANGUAGE, SpaceSwipeAction.CURSOR,
    ),
    LANGUAGE_ONLY(
        R.string.onboarding_spacebar_language_only_title,
        R.string.onboarding_spacebar_language_only_subtitle,
        SpaceSwipeAction.LANGUAGE, SpaceSwipeAction.LANGUAGE,
    ),
    CURSOR_ONLY(
        R.string.onboarding_spacebar_cursor_only_title,
        R.string.onboarding_spacebar_cursor_only_subtitle,
        SpaceSwipeAction.CURSOR, SpaceSwipeAction.CURSOR,
    ),
    OFF(
        R.string.onboarding_spacebar_off_title,
        R.string.onboarding_spacebar_off_subtitle,
        SpaceSwipeAction.NONE, SpaceSwipeAction.NONE,
    ),
}

@Composable
private fun GesturesPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    PageHeader(
        stringResource(R.string.onboarding_gestures_title),
        stringResource(R.string.onboarding_gestures_subtitle),
    )
    for (choice in SpacebarChoice.entries) {
        val selected = settings.spaceShortSwipe == choice.short &&
            settings.spaceLongSwipe == choice.long
        ListItem(
            headlineContent = { Text(stringResource(choice.titleRes)) },
            supportingContent = { Text(stringResource(choice.subtitleRes)) },
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
        headlineContent = { Text(stringResource(R.string.onboarding_emoji_key_title)) },
        supportingContent = {
            Text(stringResource(R.string.onboarding_emoji_key_subtitle))
        },
        trailingContent = {
            Switch(
                checked = settings.globeAsEmoji,
                onCheckedChange = { scope.launch { repository.setGlobeAsEmoji(it) } },
            )
        },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.onboarding_number_row_title)) },
        supportingContent = { Text(stringResource(R.string.onboarding_number_row_subtitle)) },
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
        stringResource(R.string.onboarding_tool_setup_title),
        stringResource(R.string.onboarding_tool_setup_subtitle),
    )
    if (ToolbarTool.CALENDAR in settings.enabledTools) {
        SectionTitle(stringResource(toolTitle(ToolbarTool.CALENDAR)))
        // All three already open on what the device's region suggests — the
        // Bengali calendar in Bangladesh, era years in Japan, a Friday-Saturday
        // weekend across much of the Middle East. This page is where someone
        // sees that guess and corrects it.
        AltCalendarSetting(
            title = stringResource(R.string.onboarding_calendar_first_title),
            subtitle = stringResource(R.string.onboarding_calendar_first_subtitle),
            selected = settings.calendarAltOne,
            onChange = { scope.launch { repository.setCalendarAltOne(it) } },
        )
        AltCalendarSetting(
            title = stringResource(R.string.onboarding_calendar_second_title),
            subtitle = stringResource(R.string.onboarding_calendar_second_subtitle),
            selected = settings.calendarAltTwo,
            onChange = { scope.launch { repository.setCalendarAltTwo(it) } },
        )
        WeekendSetting(
            selected = settings.calendarWeekend,
            onChange = { scope.launch { repository.setCalendarWeekend(it) } },
        )
    }
    if (ToolbarTool.WEATHER in settings.enabledTools) {
        SectionTitle(stringResource(toolTitle(ToolbarTool.WEATHER)))
        // The tool is dead without a place, so the same search-or-coordinates
        // editor the settings screen uses is right here rather than a pointer
        // to it.
        WeatherLocationSetting(repository, settings)
        ListItem(
            headlineContent = { Text(stringResource(R.string.onboarding_weather_fahrenheit_title)) },
            supportingContent = {
                Text(stringResource(R.string.onboarding_weather_fahrenheit_subtitle))
            },
            trailingContent = {
                Switch(
                    checked = settings.weatherFahrenheit,
                    onCheckedChange = { scope.launch { repository.setWeatherFahrenheit(it) } },
                )
            },
        )
    }
    if (ToolbarTool.COMPASS in settings.enabledTools) {
        SectionTitle(stringResource(toolTitle(ToolbarTool.COMPASS)))
        ListItem(
            headlineContent = { Text(stringResource(R.string.onboarding_compass_qibla_title)) },
            supportingContent = {
                Text(stringResource(R.string.onboarding_compass_qibla_subtitle))
            },
            trailingContent = {
                Switch(
                    checked = settings.compassShowQibla,
                    onCheckedChange = { scope.launch { repository.setCompassShowQibla(it) } },
                )
            },
        )
        if (settings.compassShowQibla) {
            Text(
                stringResource(R.string.onboarding_compass_qibla_info),
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
        stringResource(R.string.onboarding_tools_title),
        stringResource(R.string.onboarding_tools_subtitle),
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
            Text(stringResource(R.string.onboarding_tools_recommended_action))
        }
        TextButton(onClick = { scope.launch { repository.setEnabledTools(ToolbarTool.entries) } }) {
            Text(stringResource(R.string.onboarding_tools_everything_action))
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
            headlineContent = { Text(stringResource(toolTitle(tool))) },
            supportingContent = { Text(stringResource(toolDescription(tool))) },
            trailingContent = {
                Switch(
                    checked = tool in settings.enabledTools,
                    onCheckedChange = { scope.launch { repository.setToolEnabled(tool, it) } },
                )
            },
        )
    }
}
