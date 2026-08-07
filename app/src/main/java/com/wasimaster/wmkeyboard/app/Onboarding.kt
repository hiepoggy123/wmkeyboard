package com.wasimaster.wmkeyboard.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.emoji.EmojiCatalog
import com.wasimaster.wmkeyboard.core.emoji.EmojiRenderCheck
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.script.DeviceLocales
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.script.LanguageSuggestions
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First-run wizard: enable the keyboard, answer a short persona quiz, then
 * walk through the choices that have no one-size-fits-all default — languages,
 * theme, haptics, spacebar gestures — plus a tour of the features nobody would
 * guess at. Every page writes straight to DataStore, so backing out or
 * skipping keeps whatever was already chosen.
 *
 * The same screen doubles as a replay ("Run setup again" under About): on a
 * replay nothing is preset and nothing is seeded, so walking through with Next
 * changes no settings.
 */
@Composable
internal fun OnboardingScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // A replay is any entry after the wizard was finished once. Frozen at
    // entry: the finish write must not re-brand the run mid-exit.
    val replay = remember { settings.onboardingDone }
    // Read once at entry too, so the welcome page doesn't pop in and out of
    // the page list while a replaying user flips the keyboard elsewhere.
    val replayImeReady = remember { imeEnabled(context) && imeSelected(context) }
    // How many catalog emoji this phone's own font can't draw; null while the
    // count is still running.
    val missingEmoji = rememberUnrenderableEmojiCount()
    val pages = remember(missingEmoji, settings.enabledTools, settings.onboarding, settings.enabledLanguages.size) {
        onboardingPages(
            missingEmoji = missingEmoji,
            samsungEmoji = shipsOwnEmojiSet(),
            persona = settings.onboarding,
            enabledTools = settings.enabledTools,
            enabledLanguageCount = settings.enabledLanguages.size,
            replay = replay,
            imeReady = replayImeReady,
        )
    }
    var current by rememberSaveable { mutableStateOf(pages.first()) }
    val index = resolvePageIndex(pages, current)
    val page = pages[index]
    val onLastPage = index == pages.lastIndex
    // Which way the last page turn went, for the slide direction.
    var lastIndex by rememberSaveable { mutableStateOf(index) }
    // Whether the tools page (or the persona quiz on its behalf) has applied
    // its recommended starting selection. Hoisted here so leaving and
    // revisiting the page can't re-apply it over the user's choices.
    var toolsSeeded by rememberSaveable { mutableStateOf(replay) }
    // Guards the Welcome page's auto-advance so it fires once — otherwise
    // navigating Back to it while already set up would bounce straight
    // forward again, defeating the Back button.
    var welcomeAutoAdvanced by rememberSaveable { mutableStateOf(false) }
    // Whether the keyboard is both turned on and selected, as the Welcome page
    // last read it. The rest of the wizard is worth nothing until it is, so
    // that page's Next button waits for it; Skip is still there for anyone who
    // wants to set the keyboard up later. A replay that starts past Welcome
    // already checked.
    var keyboardReady by remember { mutableStateOf(replay && replayImeReady) }
    if (!replay) SeedLanguagesFromDevice(repository, settings)
    val accent = OnboardingPageAccents.getValue(page)
    val finish: () -> Unit = {
        finishOnboarding(scope, repository, settings, replay)
        onFinished()
    }
    val goTo: (Int) -> Unit = { target ->
        lastIndex = index
        current = pages[target.coerceIn(0, pages.lastIndex)]
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingProgress(
                    index = index,
                    count = pages.size,
                    accent = accent,
                    reduceMotion = settings.reduceMotion,
                )
                Spacer(Modifier.weight(1f))
                if (!replay) {
                    TextButton(onClick = finish) {
                        Text(stringResource(CommonR.string.common_skip))
                    }
                } else {
                    // Keeps the row's height with the button gone.
                    Spacer(Modifier.height(48.dp))
                }
            }
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    val forward = targetState >= initialState
                    if (settings.reduceMotion) {
                        slideInHorizontally(tween(0)) togetherWith
                            slideOutHorizontally(tween(0))
                    } else {
                        val spec = tween<androidx.compose.ui.unit.IntOffset>(
                            NavTransitionMs, easing = NavTransitionEasing,
                        )
                        if (forward) {
                            slideInHorizontally(spec) { it } togetherWith
                                slideOutHorizontally(spec) { -it / 3 }
                        } else {
                            slideInHorizontally(spec) { -it / 3 } togetherWith
                                slideOutHorizontally(spec) { it }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                label = "onboardingPage",
            ) { shownIndex ->
                val shown = pages[shownIndex.coerceIn(0, pages.lastIndex)]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                ) {
                    OnboardingHero(shown)
                    when (shown) {
                        OnboardingPage.WELCOME -> WelcomePage(
                            // Set here as well as through onSetupChanged: the
                            // auto-advance below takes the page out of the
                            // composition in the same pass, so its own effect
                            // would never get to report the state that caused it.
                            onReady = {
                                keyboardReady = true
                                if (!welcomeAutoAdvanced) {
                                    welcomeAutoAdvanced = true
                                    goTo(index + 1)
                                }
                            },
                            onSetupChanged = { ready -> keyboardReady = ready },
                        )
                        OnboardingPage.PERSONA -> PersonaPage(
                            repository, settings,
                            replay = replay,
                            onToolsSeeded = { toolsSeeded = true },
                        )
                        OnboardingPage.LANGUAGES -> LanguagesPage(repository, settings)
                        OnboardingPage.LOOK -> LookPage(repository, settings)
                        OnboardingPage.EMOJI ->
                            EmojiPage(repository, settings, missingEmoji ?: 0)
                        OnboardingPage.FEEDBACK -> FeedbackPage(repository, settings)
                        OnboardingPage.GESTURES -> GesturesPage(repository, settings)
                        OnboardingPage.DISCOVER -> DiscoverPage(repository, settings)
                        OnboardingPage.TOOLS -> ToolsPage(
                            repository, settings,
                            seeded = toolsSeeded,
                            onSeeded = { toolsSeeded = true },
                        )
                        OnboardingPage.TOOL_SETUP -> ToolSetupPage(repository, settings)
                        OnboardingPage.TRY -> TryPage(settings)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                if (index > 0) {
                    OutlinedButton(onClick = { goTo(index - 1) }) {
                        Text(stringResource(CommonR.string.common_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { if (onLastPage) finish() else goTo(index + 1) },
                    enabled = page != OnboardingPage.WELCOME || keyboardReady,
                ) {
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
 * Closes the wizard. If the run never reached a point that replaced the
 * enable-everything tool default (skipping from the first page used to leave
 * all 70+ tools on), land the persona's starter set first; a replay never
 * touches anything.
 */
private fun finishOnboarding(
    scope: CoroutineScope,
    repository: SettingsRepository,
    settings: KeyboardSettings,
    replay: Boolean,
) {
    scope.launch {
        if (!replay) {
            toolsAfterFinish(settings)?.let { repository.setEnabledTools(it) }
            repository.setOnboardingDone(true)
        }
    }
}

/** The wizard's progress: one pill per page, the current one stretched and tinted. */
@Composable
private fun OnboardingProgress(
    index: Int,
    count: Int,
    accent: androidx.compose.ui.graphics.Color,
    reduceMotion: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val width by animateDpAsState(
                targetValue = if (i == index) 24.dp else 8.dp,
                animationSpec = if (reduceMotion) snap() else tween(NavTransitionMs),
                label = "progressPill",
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .background(
                        when {
                            i == index -> accent
                            i < index -> accent.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

private val HeroTileSize: Dp = 56.dp

/** The page's header: an accent icon tile, the title, and one line of intent. */
@Composable
private fun OnboardingHero(page: OnboardingPage) {
    val accent = OnboardingPageAccents.getValue(page)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        WmIconTile(accent, size = HeroTileSize, radius = 18.dp) {
            Icon(
                heroIcon(page),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(heroTitle(page), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            heroSubtitle(page),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun heroIcon(page: OnboardingPage): ImageVector = when (page) {
    OnboardingPage.WELCOME -> Icons.Outlined.Keyboard
    OnboardingPage.PERSONA -> Icons.Outlined.Tune
    OnboardingPage.LANGUAGES -> Icons.Outlined.Language
    OnboardingPage.LOOK -> Icons.Outlined.Palette
    OnboardingPage.EMOJI -> Icons.Outlined.Mood
    OnboardingPage.FEEDBACK -> Icons.Outlined.Vibration
    OnboardingPage.GESTURES -> Icons.Outlined.Swipe
    OnboardingPage.DISCOVER -> Icons.Outlined.AutoAwesome
    OnboardingPage.TOOLS -> Icons.Outlined.Widgets
    OnboardingPage.TOOL_SETUP -> Icons.Outlined.Build
    OnboardingPage.TRY -> Icons.Outlined.EditNote
}

@Composable
private fun heroTitle(page: OnboardingPage): String = stringResource(
    when (page) {
        OnboardingPage.WELCOME -> R.string.onboarding_welcome_title
        OnboardingPage.PERSONA -> R.string.onboarding_persona_title
        OnboardingPage.LANGUAGES -> R.string.onboarding_languages_title
        OnboardingPage.LOOK -> R.string.onboarding_look_title
        OnboardingPage.EMOJI -> R.string.onboarding_emoji_title
        OnboardingPage.FEEDBACK -> R.string.onboarding_feedback_title
        OnboardingPage.GESTURES -> R.string.onboarding_gestures_title
        OnboardingPage.DISCOVER -> R.string.onboarding_discover_title
        OnboardingPage.TOOLS -> R.string.onboarding_tools_title
        OnboardingPage.TOOL_SETUP -> R.string.onboarding_tool_setup_title
        OnboardingPage.TRY -> R.string.onboarding_try_title
    },
)

@Composable
private fun heroSubtitle(page: OnboardingPage): String = when (page) {
    OnboardingPage.WELCOME -> pluralStringResource(
        R.plurals.onboarding_welcome_subtitle,
        LanguageRegistry.all.size,
        LanguageRegistry.all.size,
    )
    OnboardingPage.LANGUAGES -> pluralStringResource(
        R.plurals.onboarding_languages_subtitle,
        LanguageRegistry.all.size,
        LanguageRegistry.all.size,
    )
    else -> stringResource(
        when (page) {
            OnboardingPage.PERSONA -> R.string.onboarding_persona_subtitle
            OnboardingPage.LOOK -> R.string.onboarding_look_subtitle
            OnboardingPage.EMOJI -> R.string.onboarding_emoji_subtitle
            OnboardingPage.FEEDBACK -> R.string.onboarding_feedback_subtitle
            OnboardingPage.GESTURES -> R.string.onboarding_gestures_subtitle
            OnboardingPage.DISCOVER -> R.string.onboarding_discover_subtitle
            OnboardingPage.TOOLS -> R.string.onboarding_tools_subtitle
            OnboardingPage.TOOL_SETUP -> R.string.onboarding_tool_setup_subtitle
            OnboardingPage.TRY -> R.string.onboarding_try_subtitle
            else -> R.string.onboarding_look_subtitle
        },
    )
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

/**
 * How long the wizard watches the system keyboard settings after it sends the
 * user there. Long enough for someone who reads the system's warning dialog,
 * short enough that a user who wandered off is not polled all day.
 */
private const val ENABLE_WATCH_MS = 5 * 60 * 1000L

/** How often that watch reads the enabled keyboard list. */
private const val ENABLE_POLL_MS = 500L

/** Time for the wizard's window to come back before the picker opens over it. */
private const val PICKER_DELAY_MS = 700L

/**
 * Brings the wizard back to the front the moment the keyboard is turned on in
 * the system settings, then opens the keyboard picker so the switch happens in
 * the same breath. Both halves are the point: a keyboard that is turned on but
 * never selected does nothing, and the system settings give no way back.
 *
 * The poll here is deliberately not [rememberKeyboardSetup]'s. That one reads
 * through recomposition, which Compose stops while the activity is not started
 * — so it sees nothing at all for as long as the system settings are open,
 * which is exactly the window this has to watch. A read inside the coroutine
 * keeps working, because `delay` runs off the handler rather than the frame
 * clock.
 *
 * Starting the activity from the background is allowed here: this activity is
 * in the back stack of the task the user is looking at, which is one of the
 * standing exemptions.
 */
@Composable
internal fun ReturnAfterEnabling(watching: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(watching) {
        if (!watching) return@LaunchedEffect
        var waited = 0L
        while (!imeEnabled(context) && waited < ENABLE_WATCH_MS) {
            delay(ENABLE_POLL_MS)
            waited += ENABLE_POLL_MS
        }
        if (imeEnabled(context)) {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK,
                ),
            )
            delay(PICKER_DELAY_MS)
            if (!imeSelected(context)) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        // Last, so re-keying this effect cancels nothing that still matters.
        onDone()
    }
}
