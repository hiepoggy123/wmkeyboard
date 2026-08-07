package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.feedback.HapticPlayer
import com.wasimaster.wmkeyboard.core.fonts.FontStore
import com.wasimaster.wmkeyboard.core.icons.IconSlots
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.resolveLayout
import com.wasimaster.wmkeyboard.core.script.LanguageDef
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.settings.DefaultToolOrder
import com.wasimaster.wmkeyboard.core.settings.EmojiBarMode
import com.wasimaster.wmkeyboard.core.settings.EmojiFontChoice
import com.wasimaster.wmkeyboard.core.settings.HapticStyle
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.RecommendedTools
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.settings.isSupportedTool
import com.wasimaster.wmkeyboard.core.theme.BuiltInThemes
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import com.wasimaster.wmkeyboard.core.util.PlayServices
import com.wasimaster.wmkeyboard.ime.ui.SlotIcon
import kotlinx.coroutines.launch

// The wizard's fixed pages, one composable per [OnboardingPage] entry. The
// shell in Onboarding.kt draws each page's hero (title, subtitle, icon), so
// the composables here start straight at their content. The persona, discover
// and try pages live in their own files.

@Composable
internal fun WelcomePage(onReady: () -> Unit, onSetupChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val setup = rememberKeyboardSetup(context, onReady)
    LaunchedEffect(setup.ready) { onSetupChanged(setup.ready) }
    // Set as the user leaves for the system keyboard settings, so the wizard
    // knows to watch that screen and come back on its own.
    var awaitingEnable by rememberSaveable { mutableStateOf(false) }
    ReturnAfterEnabling(awaitingEnable) { awaitingEnable = false }
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        SetupCard(context, setup = setup, onEnableRequested = { awaitingEnable = true })
    }
    if (!setup.ready) CaptionText(stringResource(R.string.onboarding_welcome_required))
    Text(
        stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
        textAlign = TextAlign.Start,
    )
}

@Composable
internal fun LanguagesPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
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
 * Adds any language in the registry, without leaving the wizard. Tapping a
 * language with one layout enables it on the spot; a language with several
 * asks which of them to enable first — Bengali alone ships three input systems
 * (Avro, Probhat, National) and most people type in exactly one of them, so
 * enabling all three unasked put two dead layouts on the 🌐 cycle. Secondary
 * suggestion sources stay in Settings → Languages, which has room for them.
 */
@Composable
private fun AddLanguageSection(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    // The language whose layout-picker dialog is open, by id — the id rather
    // than the LanguageDef so the open dialog survives rotation.
    var layoutChoice by rememberSaveable { mutableStateOf<String?>(null) }
    val enabledLangIds = settings.enabledLanguages.mapTo(HashSet()) { it.id }
    val q = query.trim().lowercase()
    val matches = searchLanguages(q).filter { it.id !in enabledLangIds }
    val suggested = rememberSuggestedLanguages(settings, limit = ONBOARDING_SUGGESTION_LIMIT)
    // Asked here too. The wizard is where most languages are added, so skipping
    // the question here would leave the download unasked-for in the common case
    // — which is the whole point of asking.
    val dataPrompt = rememberLanguageDataPrompt()
    val add: (LanguageDef) -> Unit = { language ->
        if (language.layoutIds.size > 1) {
            layoutChoice = language.id
        } else {
            dataPrompt.ask(language) {
                addLanguage(scope, repository, settings, language)
                query = ""
            }
        }
    }

    layoutChoice?.let { langId ->
        val language = LanguageRegistry.byId(langId)
        LayoutPickerDialog(
            language = language,
            layoutName = { resolveLayout(settings.customLayouts, it).name },
            onConfirm = { chosen ->
                layoutChoice = null
                // After the picker, not instead of it: which layout to type on
                // and whether to spend the data are separate questions, and the
                // second one only makes sense once the first is answered.
                dataPrompt.ask(language) {
                    query = ""
                    scope.launch {
                        repository.setEnabledLayoutIds(
                            (settings.enabledLayoutIds + chosen).distinct(),
                        )
                    }
                }
            },
            onDismiss = { layoutChoice = null },
        )
    }

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
                    .clickable { add(suggestion.language) },
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
                .clickable { add(language) },
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

/**
 * Asks which of a multi-layout language's layouts to enable, as it is added.
 * The default (first) layout starts checked, so Add without touching anything
 * does what adding the language always did; unchecking it and checking another
 * is the whole point — the person who types Bengali in Probhat should never
 * have Avro on their 🌐 cycle. At least one box must stay checked: a language
 * added with no layout would not exist.
 */
@Composable
private fun LayoutPickerDialog(
    language: LanguageDef,
    layoutName: (String) -> String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(language.id) {
        mutableStateOf(setOfNotNull(language.layoutIds.firstOrNull()))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onboarding_layout_picker_title, language.displayName)) },
        text = {
            // Scrollable for the long tail: Chinese ships six input systems,
            // and a small-screen dialog has to reach all of them.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.onboarding_layout_picker_body))
                Spacer(Modifier.height(8.dp))
                for (layoutId in language.layoutIds) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected =
                                    if (layoutId in selected) selected - layoutId
                                    else selected + layoutId
                            },
                    ) {
                        Checkbox(
                            checked = layoutId in selected,
                            // The row is the click target; a second one on the
                            // box itself would double-toggle under TalkBack.
                            onCheckedChange = null,
                        )
                        Text(layoutName(layoutId))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                // Filtered through layoutIds rather than passed as the set, so
                // the layouts enable in the language's shipped order however
                // the boxes were ticked.
                onClick = { onConfirm(language.layoutIds.filter { it in selected }) },
            ) { Text(stringResource(CommonR.string.common_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

@Composable
internal fun LookPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
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
        contentPadding = PaddingValues(horizontal = 16.dp),
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
    // Both switches above are the *global* answer, and the seeded modes
    // override both of them per app and per field. Someone who turns the emoji
    // row off here and then sees it in WhatsApp has met a bug, unless the page
    // said so first.
    OnboardingNotice(stringResource(R.string.onboarding_rows_modes_info))
}

/**
 * A note about something the keyboard decides for itself, next to the setting
 * it overrides. Drawn on its own surface rather than as loose caption text, so
 * it does not read as the subtitle of the row above it.
 */
@Composable
internal fun OnboardingNotice(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
internal fun EmojiPage(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    missingCount: Int,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
        // Noto comes from the Play services font provider, so it is a real
        // choice only where that provider answers. This page exists to fix
        // missing emoji; offering a set that cannot be fetched would fix none.
        if (PlayServices.hasFontProvider(context)) {
            add(EmojiFontChoice.NOTO to stringResource(R.string.langemoji_emoji_font_noto_label))
        }
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
internal fun FeedbackPage(repository: SettingsRepository, settings: KeyboardSettings) {
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

@Composable
internal fun GesturesPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    // The badge follows the persona answer rather than being baked into one
    // row's title, so the recommendation is honest: it always marks a choice
    // that matches what the quiz actually set.
    val recommended = recommendedSpacebarChoice(settings.onboarding)
    for (choice in SpacebarChoice.entries) {
        val selected = settings.spaceShortSwipe == choice.short &&
            settings.spaceLongSwipe == choice.long
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(choice.titleRes))
                    if (choice == recommended) {
                        Text(
                            stringResource(R.string.onboarding_recommended_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            },
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

/**
 * Per-tool first-run choices, one section per enabled tool from
 * [ToolSetupTools]. Follows the tools page so it reflects what was just
 * switched on; every option lives in the tool's settings too.
 */
@Composable
internal fun ToolSetupPage(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    if (ToolbarTool.CALENDAR in settings.enabledTools) {
        OnboardingSectionTitle(stringResource(toolTitle(ToolbarTool.CALENDAR)))
        // The two rows below are additions, not replacements, and reading them
        // as a calendar *picker* is the obvious mistake. So the Gregorian
        // calendar gets a row of its own, above them and with no control on
        // it: the thing that is always there, drawn as always there.
        ListItem(
            leadingContent = {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            headlineContent = {
                Text(stringResource(R.string.onboarding_calendar_gregorian_title))
            },
            supportingContent = {
                Text(stringResource(R.string.onboarding_calendar_gregorian_subtitle))
            },
            trailingContent = {
                Text(
                    stringResource(R.string.onboarding_calendar_gregorian_always),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
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
        OnboardingSectionTitle(stringResource(toolTitle(ToolbarTool.WEATHER)))
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
        OnboardingSectionTitle(stringResource(toolTitle(ToolbarTool.COMPASS)))
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
internal fun OnboardingSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * One of the two tool presets. The pair splits the width between them so it
 * reads as a choice rather than as two links lost above the list, and each
 * label may wrap to a second line rather than being cut off — which is why the
 * row that holds them measures to the taller of the two.
 */
@Composable
private fun ToolPresetButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ToolsPage(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    seeded: Boolean,
    onSeeded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // First visit swaps the enable-everything default for the recommended
    // starter set — but only over an untouched default, so a user who
    // already toggled tools (or reinstalled with settings intact) keeps
    // their selection. The persona page usually got here first; its answer
    // marks the seed done.
    LaunchedEffect(Unit) {
        if (!seeded) {
            onSeeded()
            if (settings.enabledTools.toSet() == ToolbarTool.entries.toSet()) {
                repository.setEnabledTools(RecommendedTools)
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolPresetButton(
            icon = Icons.Outlined.AutoAwesome,
            label = stringResource(R.string.onboarding_tools_recommended_action),
            modifier = Modifier.weight(1f),
        ) { scope.launch { repository.setEnabledTools(RecommendedTools) } }
        ToolPresetButton(
            icon = Icons.Outlined.SelectAll,
            label = stringResource(R.string.onboarding_tools_everything_action),
            modifier = Modifier.weight(1f),
        ) { scope.launch { repository.setEnabledTools(ToolbarTool.entries) } }
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
