package com.wasimaster.wmkeyboard.app

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.view.KeyEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.wasimaster.wmkeyboard.core.settings.SuggestionHotkeyMode
import com.wasimaster.wmkeyboard.core.tools.CheatSheetLetter
import com.wasimaster.wmkeyboard.core.tools.DefaultLeader
import com.wasimaster.wmkeyboard.core.tools.DefaultToolLetters
import com.wasimaster.wmkeyboard.core.tools.KeyChord
import com.wasimaster.wmkeyboard.core.tools.LeaderTrigger
import com.wasimaster.wmkeyboard.core.tools.ReservedChords
import com.wasimaster.wmkeyboard.core.tools.ReservedLetters
import com.wasimaster.wmkeyboard.core.tools.TapModifier
import com.wasimaster.wmkeyboard.core.tools.ToolboxLetter
import com.wasimaster.wmkeyboard.core.tools.describeChord
import com.wasimaster.wmkeyboard.core.tools.formatChord
import com.wasimaster.wmkeyboard.core.tools.formatLeader
import com.wasimaster.wmkeyboard.core.tools.parseLeader
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.wasimaster.wmkeyboard.core.ui.toolAccentColor
import com.wasimaster.wmkeyboard.core.ui.toolAccentColorArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.content.R as ContentR
import com.wasimaster.wmkeyboard.feedback.R as FeedbackR
import com.wasimaster.wmkeyboard.ime.R as ImeR
import com.wasimaster.wmkeyboard.core.tools.leaderLabel
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import android.provider.OpenableColumns
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.os.Build
import com.wasimaster.wmkeyboard.core.feedback.HapticPlayer
import com.wasimaster.wmkeyboard.core.feedback.KeySoundPlayer
import com.wasimaster.wmkeyboard.core.handwriting.HandwritingModels
import com.wasimaster.wmkeyboard.core.settings.EmojiBarContent
import com.wasimaster.wmkeyboard.core.settings.EmojiBarCountRange
import com.wasimaster.wmkeyboard.core.settings.BottomRowHeightRange
import com.wasimaster.wmkeyboard.core.settings.SidePadScaleRange
import com.wasimaster.wmkeyboard.core.settings.ShiftCapsLockMsRange
import com.wasimaster.wmkeyboard.core.settings.DefaultCurrencyKeys
import com.wasimaster.wmkeyboard.core.settings.EmojiBarMode
import com.wasimaster.wmkeyboard.core.settings.EmojiFontChoice
import com.wasimaster.wmkeyboard.core.settings.EmojiSkinTone
import com.wasimaster.wmkeyboard.core.icons.IconPackStore
import com.wasimaster.wmkeyboard.core.icons.IconSlots
import com.wasimaster.wmkeyboard.core.util.requireInputStream
import com.wasimaster.wmkeyboard.core.util.runCancellable
import com.wasimaster.wmkeyboard.ime.WMKeyboardService
import com.wasimaster.wmkeyboard.ime.ui.IconDefaults
import com.wasimaster.wmkeyboard.ime.ui.KeyboardFonts
import com.wasimaster.wmkeyboard.ime.ui.LocalIconSet
import com.wasimaster.wmkeyboard.ime.ui.SlotIcon
import com.wasimaster.wmkeyboard.ime.ui.rememberIconSet
import com.wasimaster.wmkeyboard.ime.ui.ModeIcons
import com.wasimaster.wmkeyboard.core.settings.EmojiInsertMode
import com.wasimaster.wmkeyboard.core.settings.EmojiTabMode
import com.wasimaster.wmkeyboard.core.settings.HapticStyle
import com.wasimaster.wmkeyboard.core.settings.KeySoundStyle
import com.wasimaster.wmkeyboard.core.settings.AiAction
import com.wasimaster.wmkeyboard.core.settings.AiProvider
import com.wasimaster.wmkeyboard.core.settings.GifContentFilter
import com.wasimaster.wmkeyboard.core.settings.GifSourceMode
import com.wasimaster.wmkeyboard.core.settings.GrammarDialect
import com.wasimaster.wmkeyboard.core.settings.MediaSendMode
import com.wasimaster.wmkeyboard.core.settings.QrEccLevel
import com.wasimaster.wmkeyboard.core.tools.AiClient
import com.wasimaster.wmkeyboard.core.tools.AltCalendar
import com.wasimaster.wmkeyboard.core.tools.Weekend
import com.wasimaster.wmkeyboard.core.tools.AiPrompts
import com.wasimaster.wmkeyboard.core.tools.GeoPlace
import com.wasimaster.wmkeyboard.core.tools.SmartSuggest
import com.wasimaster.wmkeyboard.core.tools.ToolApiKeys
import com.wasimaster.wmkeyboard.core.tools.TypingBests
import com.wasimaster.wmkeyboard.core.tools.TypingHistory
import com.wasimaster.wmkeyboard.core.tools.TypingTestMode
import com.wasimaster.wmkeyboard.core.tools.ToolHttp
import com.wasimaster.wmkeyboard.core.tools.TranslateClient
import com.wasimaster.wmkeyboard.core.tools.WeatherClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wasimaster.wmkeyboard.core.settings.BarRow
import com.wasimaster.wmkeyboard.core.settings.CursorTools
import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.core.settings.KeyboardAlignment
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.script.NumeralCommitScope
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.input.composer.CjkLearning
import com.wasimaster.wmkeyboard.core.mlkit.MlKitInit
import com.wasimaster.wmkeyboard.core.layout.AssetLayouts
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.resolveLayout
import com.wasimaster.wmkeyboard.core.settings.ConfigBackup
import com.wasimaster.wmkeyboard.core.settings.SettingsBackup
import com.wasimaster.wmkeyboard.core.settings.KeyboardMode
import com.wasimaster.wmkeyboard.core.settings.DefaultKeyboardModes
import com.wasimaster.wmkeyboard.core.settings.DefaultToolbarTools
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.isSupportedTool
import com.wasimaster.wmkeyboard.core.settings.ModeField
import com.wasimaster.wmkeyboard.core.tools.BuiltInSymbolSets
import com.wasimaster.wmkeyboard.core.tools.resolveSymbolSets
import com.wasimaster.wmkeyboard.core.tools.SymbolSet
import com.wasimaster.wmkeyboard.core.settings.OneHandedMode
import com.wasimaster.wmkeyboard.core.settings.OneHandedSide
import com.wasimaster.wmkeyboard.core.settings.PowerSavingTrigger
import com.wasimaster.wmkeyboard.core.settings.ScreenVariant
import com.wasimaster.wmkeyboard.core.settings.SensitiveClipHandling
import com.wasimaster.wmkeyboard.core.debug.DebugLog
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.sizingValuesFor
import com.wasimaster.wmkeyboard.core.settings.LetterSwipeAction
import com.wasimaster.wmkeyboard.core.settings.SpaceSwipeAction
import com.wasimaster.wmkeyboard.core.settings.SpacebarDisplay
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.settings.ToolboxLayout
import com.wasimaster.wmkeyboard.core.settings.ToolboxPageSizeRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictCatalog
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictDownloadManager
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictStore
import com.wasimaster.wmkeyboard.core.emoji.EmojiFontShaping
import com.wasimaster.wmkeyboard.core.emoji.EmojiKeywordPack
import com.wasimaster.wmkeyboard.core.emoji.EmojiKeywordPacks
import com.wasimaster.wmkeyboard.core.emoji.EmojiSearchExamples
import com.wasimaster.wmkeyboard.core.prediction.CustomDictionaries
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.feedback.SoundFile
import com.wasimaster.wmkeyboard.core.feedback.SoundImportResult
import com.wasimaster.wmkeyboard.core.feedback.SoundStore
import com.wasimaster.wmkeyboard.core.fonts.FontFile
import com.wasimaster.wmkeyboard.core.fonts.FontImportResult
import com.wasimaster.wmkeyboard.core.fonts.FontStore
import com.wasimaster.wmkeyboard.core.fonts.InstalledFont
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.core.snippets.SnippetFile
import com.wasimaster.wmkeyboard.core.snippets.SnippetStore
import com.wasimaster.wmkeyboard.core.snippets.SnippetVariable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Settings app: setup wizard plus every keyboard option, Material 3 +
 * dynamic color, all state backed by DataStore via [SettingsRepository].
 */
class MainActivity : ComponentActivity() {

    companion object {
        /**
         * Intent extra with a [ToolbarTool] name: the keyboard uses it
         * (tool long-press, "needs an API key" panels) to jump straight
         * to that tool's settings page.
         */
        const val EXTRA_OPEN_TOOL = MainActivityContract.EXTRA_OPEN_TOOL
        /**
         * Intent extra with a specific settings route string (e.g., "themes").
         */
        const val EXTRA_OPEN_ROUTE = MainActivityContract.EXTRA_OPEN_ROUTE
    }

    private lateinit var repository: SettingsRepository

    /**
     * Where the intent that started (or re-entered) this activity wants to go.
     *
     * A flow rather than a value read once in [onCreate]: the activity is
     * `singleTop`, so a second `wmkeyboard://` link — or a second "open
     * settings" from the keyboard — arrives at [onNewIntent] on the instance
     * that is already running. Reading `intent` once would silently drop it.
     */
    private val pendingNav = MutableStateFlow<PendingNav?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Shares a process with the keyboard service when both are running, so
        // this is often a no-op — but the settings app can be opened first.
        DebugLog.attach(applicationContext)
        // Same story for ML Kit: when the shared process came up on the lock
        // screen its init provider was skipped, and the handwriting model
        // manager below is one of the screens that pays for it.
        MlKitInit.ensure(applicationContext)
        repository = SettingsRepository(applicationContext)
        // The JSON asset layouts back the tail of the language list; load them
        // before the first settings emission so an enabled asset layout resolves
        // to its real language here (a few small files, parsed once).
        AssetLayouts.load(applicationContext.assets)
        // Modes added since this install was first seeded — the settings
        // screen should list them even if the keyboard has not run yet.
        lifecycleScope.launch { repository.seedNewDefaultModes() }
        pendingNav.value = navFor(intent)
        setContent {
            // Null until DataStore's first emission: rendering nothing for a
            // frame beats flashing onboarding at users who finished it.
            val settings by repository.settings
                .collectAsStateWithLifecycle(null as KeyboardSettings?)
            val pending by pendingNav.collectAsStateWithLifecycle()
            settings?.let { loaded ->
                AppTheme(loaded) {
                    SettingsNavHost(
                        repository = repository,
                        settings = loaded,
                        pending = pending,
                        onPendingHandled = { pendingNav.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navFor(intent)?.let { pendingNav.value = it }
    }

    /**
     * What an intent asks for: a `wmkeyboard://` deep link, or one of the
     * extras the keyboard uses to jump into a tool's page.
     */
    private fun navFor(intent: Intent?): PendingNav? {
        if (intent == null) return null
        AddonDeepLink.routeFor(intent.data)?.let { return PendingNav(route = it) }
        SettingsShortcuts.routeFor(intent.data)?.let { return PendingNav(route = it) }
        intent.getStringExtra(EXTRA_OPEN_ROUTE)?.takeIf { it.isNotEmpty() }
            ?.let { return PendingNav(route = it) }
        val tool = intent.getStringExtra(EXTRA_OPEN_TOOL)
            ?.let { name -> ToolbarTool.entries.find { it.name == name } }
        return tool?.let { PendingNav(tool = it) }
    }
}

/** A navigation an incoming intent asked for; exactly one field is set. */
internal data class PendingNav(
    val route: String? = null,
    val tool: ToolbarTool? = null,
)

@Composable
internal fun AppTheme(settings: KeyboardSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val supportsDynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var scheme = when {
        supportsDynamic && dark -> dynamicDarkColorScheme(context)
        supportsDynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    if (settings.themeMode == ThemeMode.AMOLED) {
        scheme = scheme.copy(background = Color.Black, surface = Color.Black)
    }
    // Every settings surface draws tool icons, so the user's icon set is
    // provided here rather than per screen — the Tools list and the keyboard
    // must not disagree about what a tool looks like.
    val iconSet by rememberIconSet(settings.icons)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalIconSet provides iconSet, content = content)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SettingsNavHost(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    pending: PendingNav? = null,
    onPendingHandled: () -> Unit = {},
) {
    // A section's icon and name fly from its home row to the heading of the
    // screen it opens, so the two read as one object being opened rather than
    // as a list and an unrelated page. Published for the whole graph here;
    // each destination adds its own scope, and the rows and headings pick both
    // up without being handed anything.
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            // A shared element is a motion and has no still version, so
            // reduced motion switches it off at the source.
            LocalSharedTransition provides if (settings.reduceMotion) null else this,
        ) {
            SettingsNavGraph(repository, settings, pending, onPendingHandled)
        }
    }
}

@Composable
private fun SettingsNavGraph(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    pending: PendingNav?,
    onPendingHandled: () -> Unit,
) {
    val navController = rememberNavController()
    // A pack downloaded from these screens has to reach the running keyboard,
    // which holds its merged emoji catalogue in memory. Bumping the counter it
    // watches is that message. Collected here rather than on the screen that
    // started the download, because navigating away mid-download must not lose
    // the notification.
    LaunchedEffect(Unit) {
        EmojiDictDownloadManager.completions.collect {
            repository.bumpEmojiKeywordPackVersion()
        }
    }
    // Where an incoming intent wants to go: a wmkeyboard:// link, or the
    // keyboard's own "open settings" (tool long-press, a panel's link). Cleared
    // once handled, so returning to this screen doesn't navigate again.
    LaunchedEffect(pending) {
        if (pending == null || !settings.onboardingDone) return@LaunchedEffect
        when {
            !pending.route.isNullOrEmpty() -> navController.navigate(pending.route)
            pending.tool != null -> {
                navController.navigate("tools")
                navController.navigate("tool/${pending.tool.name}")
            }
        }
        onPendingHandled()
    }
    // A screen push, not a cross-fade: the arriving screen comes the whole way
    // in from the right while the one behind it drifts a third of the way left,
    // so the two read as a stack being pushed rather than two things dissolving.
    // Both surfaces are opaque, so nothing fades — a fade over a full-width
    // slide only makes the overlap look muddy. Collapsed to an instant cut when
    // the user has asked for reduced motion.
    val navMs = if (settings.reduceMotion) 0 else NavTransitionMs
    val spec = tween<androidx.compose.ui.unit.IntOffset>(
        durationMillis = navMs,
        easing = NavTransitionEasing,
    )
    val parallax = 3
    // Frozen at first composition: completing onboarding navigates away
    // explicitly, it must not yank the graph out from under the NavHost.
    val startDestination = remember { if (settings.onboardingDone) "home" else "onboarding" }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(spec) { it } },
        exitTransition = { slideOutHorizontally(spec) { -it / parallax } },
        popEnterTransition = { slideInHorizontally(spec) { -it / parallax } },
        popExitTransition = { slideOutHorizontally(spec) { it } },
    ) {
        composable("onboarding") {
            OnboardingScreen(
                repository = repository,
                settings = settings,
                onFinished = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
            )
        }
        composable("home") {
            HomeScreen(
                settings = settings,
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable("search") {
            SettingsSearchScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
                onOpen = { result ->
                    // Arm the flash before navigating: the destination's rows
                    // read it during their first composition.
                    SettingsHighlight.request(result.titleRes)
                    // The search screen itself is dropped from the back stack,
                    // so backing out of the setting lands on the home list.
                    navController.popBackStack()
                    navController.navigate(result.route)
                },
            )
        }
        composable("typing") {
            SettingsScreen(
                stringResource(R.string.home_typing_title),
                { navController.popBackStack() },
                route = "typing",
            ) {
                TypingSettings(
                    repository, settings,
                    onOpenDictionary = { navController.navigate("dictionary") },
                    onOpenCustomDictionaries = { navController.navigate("customdictionaries") },
                    onOpenBlacklist = { navController.navigate("blacklist") },
                    onOpenHardwareShortcuts = { navController.navigate("hwshortcuts") },
                )
            }
        }
        composable("keypress") {
            SettingsScreen(
                stringResource(R.string.home_keypress_title),
                { navController.popBackStack() },
                route = "keypress",
            ) {
                KeyPressSettings(repository, settings)
            }
        }
        composable("dictionary") {
            SettingsScreen(
                stringResource(R.string.home_screen_dictionary_title),
                { navController.popBackStack() },
                route = "dictionary",
            ) {
                DictionarySettings(repository)
            }
        }
        composable("backup") {
            SettingsScreen(
                stringResource(R.string.home_backup_title),
                { navController.popBackStack() },
                route = "backup",
            ) {
                BackupSettings(repository)
            }
        }
        composable("customdictionaries") {
            SettingsScreen(
                stringResource(R.string.home_screen_custom_dictionaries_title),
                { navController.popBackStack() },
                route = "customdictionaries",
            ) {
                CustomDictionarySettings(repository, settings)
            }
        }
        composable("emojikeywords") {
            SettingsScreen(
                stringResource(R.string.home_screen_emoji_keywords_title),
                { navController.popBackStack() },
                route = "emojikeywords",
            ) {
                EmojiKeywordSettings(repository, settings)
            }
        }
        composable("blacklist") {
            SettingsScreen(
                stringResource(R.string.home_screen_blacklist_title),
                { navController.popBackStack() },
                route = "blacklist",
            ) {
                BlacklistSettings(repository, settings)
            }
        }
        composable("hwshortcuts") {
            SettingsScreen(
                stringResource(R.string.home_screen_hwshortcuts_title),
                { navController.popBackStack() },
                route = "hwshortcuts",
            ) {
                HardwareShortcutsSettings(repository, settings)
            }
        }
        composable("appearance") {
            SettingsScreen(
                stringResource(R.string.home_appearance_title),
                { navController.popBackStack() },
                route = "appearance",
            ) {
                AppearanceSettings(
                    repository, settings,
                    onOpenThemes = { navController.navigate("themes") },
                    onOpenFonts = { navController.navigate("fonts") },
                    onOpenIcons = { navController.navigate("icons") },
                    onOpenPhotos = { navController.navigate(PHOTO_HUB_ROUTE) },
                )
            }
        }
        composable("layout") {
            SettingsScreen(
                stringResource(R.string.home_layout_title),
                { navController.popBackStack() },
                route = "layout",
            ) {
                LayoutSettings(repository, settings)
            }
        }
        composable("fonts") {
            SettingsScreen(
                stringResource(R.string.home_screen_fonts_title),
                { navController.popBackStack() },
                route = "fonts",
            ) {
                FontSettings(repository, settings)
            }
        }
        composable("icons") {
            SettingsScreen(
                stringResource(R.string.home_screen_icons_title),
                { navController.popBackStack() },
                route = "icons",
            ) {
                IconsScreen(repository, settings)
            }
        }
        composable("themes") {
            SettingsScreen(
                stringResource(R.string.home_screen_themes_title),
                { navController.popBackStack() },
                route = "themes",
            ) {
                ThemesScreen(repository, settings) { id -> navController.navigate("theme_edit/$id") }
            }
        }
        composable("theme_edit/{themeId}") { backStackEntry ->
            val themeId = backStackEntry.arguments?.getString("themeId").orEmpty()
            SettingsScreen(stringResource(R.string.home_screen_theme_edit_title), { navController.popBackStack() }) {
                ThemeEditorScreen(repository, settings, themeId) { route ->
                    navController.navigate(route)
                }
            }
        }
        composable(PHOTO_HUB_ROUTE) {
            PhotoBackgroundsScreen(
                repository = repository,
                settings = settings,
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "$PHOTO_BROWSE_ROUTE?theme={theme}&slot={slot}",
            arguments = listOf(
                navArgument("theme") { type = NavType.StringType; defaultValue = "" },
                navArgument("slot") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            PhotoBrowseScreen(
                settings = settings,
                themeId = entry.arguments?.getString("theme").orEmpty(),
                onOpenPhoto = { photo ->
                    PhotoSelection.current = photo
                    navController.navigate(PHOTO_DETAIL_ROUTE)
                },
                onNavigate = { route ->
                    if (route == BACK_ROUTE) navController.popBackStack() else navController.navigate(route)
                },
            )
        }
        composable(PHOTO_DETAIL_ROUTE) {
            val photo = PhotoSelection.current
            // The browse screen sets this immediately before navigating; it is
            // only ever null after the process was killed on the back stack.
            if (photo == null) {
                navController.popBackStack()
            } else {
                val browse = navController.previousBackStackEntry?.arguments
                PhotoDetailScreen(
                    repository = repository,
                    settings = settings,
                    photo = photo,
                    themeId = browse?.getString("theme").orEmpty(),
                    slot = BackgroundSlot.of(browse?.getString("slot")),
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = "$PHOTO_LIBRARY_ROUTE?theme={theme}&slot={slot}",
            arguments = listOf(
                navArgument("theme") { type = NavType.StringType; defaultValue = "" },
                navArgument("slot") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            PhotoLibraryScreen(
                repository = repository,
                settings = settings,
                themeId = entry.arguments?.getString("theme").orEmpty(),
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(PHOTO_ROTATION_ROUTE) {
            PhotoRotationScreen(
                repository = repository,
                settings = settings,
                onBack = { navController.popBackStack() },
            )
        }
        composable("keymaps") {
            SettingsScreen(
                stringResource(R.string.home_keymaps_title),
                { navController.popBackStack() },
                route = "keymaps",
            ) {
                KeyLayoutsScreen(repository, settings) { route -> navController.navigate(route) }
            }
        }
        composable("sticker_packs") {
            SettingsScreen(
                stringResource(R.string.home_screen_sticker_packs_title),
                { navController.popBackStack() },
                route = "sticker_packs",
            ) {
                StickerPacksScreen { route -> navController.navigate(route) }
            }
        }
        composable("plugins") {
            SettingsScreen(
                stringResource(R.string.home_screen_plugins_title),
                { navController.popBackStack() },
                route = "plugins",
            ) {
                PluginsScreen { route -> navController.navigate(route) }
            }
        }
        composable("plugin/{pluginId}") { entry ->
            val pluginId = entry.arguments?.getString("pluginId").orEmpty()
            SettingsScreen(stringResource(R.string.home_screen_plugin_title), { navController.popBackStack() }) {
                PluginDetailScreen(pluginId) { navController.popBackStack() }
            }
        }
        // The optional `add` argument carries a repository URL from a
        // wmkeyboard://repo link; it pre-fills the add dialog, which is still
        // where the user confirms.
        composable(
            "addons?add={add}",
            arguments = listOf(
                navArgument("add") { defaultValue = ""; type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val prefill = decodeRouteArg(backStackEntry.arguments?.getString("add"))
            SettingsScreen(
                stringResource(R.string.home_addons_title),
                { navController.popBackStack() },
                route = "addons",
            ) {
                AddonsScreen(prefill) { route -> navController.navigate(route) }
            }
        }
        // The repository URL travels in the path, percent-encoded — a deep link
        // names a repository by address, not by its position in the user's list.
        composable("addon_repo/{repoUrl}") { backStackEntry ->
            val url = decodeRouteArg(backStackEntry.arguments?.getString("repoUrl"))
            // Headed with the repository, not with the act of browsing it: its
            // name over its author, both centred, both carried into the strip.
            val repo = rememberRepoHeading(url)
            SettingsScreen(
                repo.name,
                { navController.popBackStack() },
                route = addonRepoFlightRoute(url),
                centerTitle = true,
                subtitle = repo.author.ifBlank { null },
                subtitleInBar = true,
            ) {
                AddonRepoScreen(url) { route -> navController.navigate(route) }
            }
        }
        composable("addon/{repoUrl}/{addonId}") { backStackEntry ->
            val url = decodeRouteArg(backStackEntry.arguments?.getString("repoUrl"))
            val addonId = decodeRouteArg(backStackEntry.arguments?.getString("addonId"))
            // Headed with what the addon is, not with the word "Addon": the
            // type is the one thing the catalogue card already showed, so it
            // is the word that can grow into the heading.
            val heading = rememberAddonHeading(url, addonId)
            SettingsScreen(
                stringResource(heading.titleRes),
                { navController.popBackStack() },
                route = addonFlightRoute(url, addonId),
                icon = {
                    Icon(
                        heading.icon,
                        contentDescription = null,
                        modifier = Modifier.size(WmIconTileGlyph),
                    )
                },
                accent = heading.accent,
                iconTile = false,
                iconInBar = true,
                barTint = heading.accent,
            ) {
                AddonDetailScreen(url, addonId) { route -> navController.navigate(route) }
            }
        }
        composable("sticker_pack/{packId}") { backStackEntry ->
            val packId = backStackEntry.arguments?.getString("packId").orEmpty()
            SettingsScreen(stringResource(R.string.home_screen_sticker_pack_edit_title), { navController.popBackStack() }) {
                StickerPackScreen(packId)
            }
        }
        composable("keymap_edit/{layoutId}") { backStackEntry ->
            val layoutId = backStackEntry.arguments?.getString("layoutId").orEmpty()
            SettingsScreen(stringResource(R.string.home_screen_layout_edit_title), { navController.popBackStack() }) {
                KeyLayoutEditorScreen(repository, settings, layoutId) { route ->
                    navController.navigate(route)
                }
            }
        }
        composable("keymap_json/{layoutId}") { backStackEntry ->
            val layoutId = backStackEntry.arguments?.getString("layoutId").orEmpty()
            SettingsScreen(stringResource(R.string.home_screen_layout_json_title), { navController.popBackStack() }) {
                KeyLayoutJsonScreen(repository, settings, layoutId) { navController.popBackStack() }
            }
        }
        composable("languages") {
            SettingsScreen(
                stringResource(R.string.home_languages_title),
                { navController.popBackStack() },
                route = "languages",
            ) {
                LanguageSettings(repository, settings) { route -> navController.navigate(route) }
            }
        }
        composable("add_language") {
            SettingsScreen(
                stringResource(R.string.home_screen_add_language_title),
                { navController.popBackStack() },
                route = "add_language",
            ) {
                AddLanguageScreen(repository, settings) { langId ->
                    navController.navigate("language/$langId")
                }
            }
        }
        composable("language/{langId}") { backStackEntry ->
            val langId = backStackEntry.arguments?.getString("langId").orEmpty()
            SettingsScreen(
                LanguageRegistry.byId(langId).displayName,
                { navController.popBackStack() },
                route = "language/$langId",
            ) {
                LanguageDetailScreen(
                    langId, repository, settings,
                    onNavigate = { route -> navController.navigate(route) },
                    onRemoved = { navController.popBackStack() },
                )
            }
        }
        composable("emoji") {
            SettingsScreen(
                stringResource(R.string.home_emoji_title),
                { navController.popBackStack() },
                route = "emoji",
            ) {
                EmojiSettings(repository, settings) { navController.navigate(it) }
            }
        }
        composable("tools") {
            SettingsScreen(
                stringResource(R.string.home_tools_title),
                { navController.popBackStack() },
                route = "tools",
            ) {
                ToolsSettings(repository, settings) { tool -> navController.navigate("tool/${tool.name}") }
            }
        }
        composable("tool/{toolName}") { backStackEntry ->
            val tool = backStackEntry.arguments?.getString("toolName")
                ?.let { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
            if (tool != null) {
                // A tool's colour belongs to the tool, not to a place in the
                // settings tree, so it paints the glyph and leaves the bar
                // alone — a dozen tool pages each repainting the strip would
                // read as a dozen unrelated apps. No tile either: the glyph
                // bare is the same object the Tools row drew, so it can fly
                // from it. It stays through the collapse — it is the only
                // thing naming which tool this is.
                SettingsScreen(
                    stringResource(toolTitle(tool)),
                    { navController.popBackStack() },
                    route = toolRoute(tool),
                    icon = { ToolGlyph(tool) },
                    accent = toolAccentColor(tool, settings.toolColorOverrides),
                    iconTile = false,
                    iconInBar = true,
                ) {
                    ToolDetailSettings(repository, settings, tool) { route ->
                        navController.navigate(route)
                    }
                }
            }
        }
        composable("accessibility") {
            SettingsScreen(
                stringResource(R.string.home_accessibility_title),
                { navController.popBackStack() },
                route = "accessibility",
            ) {
                AccessibilitySettings(
                    repository, settings,
                    onOpenFonts = { navController.navigate("fonts") },
                    onOpenLayout = { navController.navigate("layout") },
                    onOpenKeyPress = { navController.navigate("keypress") },
                )
            }
        }
        composable("privacy") {
            SettingsScreen(
                stringResource(R.string.home_privacy_title),
                { navController.popBackStack() },
                route = "privacy",
            ) {
                PrivacySettings(repository, settings)
            }
        }
        composable("rows") {
            SettingsScreen(
                stringResource(R.string.home_rows_title),
                { navController.popBackStack() },
                route = "rows",
            ) {
                RowsSettings(repository, settings) { navController.navigate(it) }
            }
        }
        composable("symbol_set_edit/{setId}") { backStackEntry ->
            val setId = backStackEntry.arguments?.getString("setId").orEmpty()
            SettingsScreen(stringResource(R.string.home_screen_symbol_set_edit_title), { navController.popBackStack() }) {
                SymbolSetEditor(repository, settings, setId) { navController.popBackStack() }
            }
        }
        composable("modes") {
            SettingsScreen(
                stringResource(R.string.home_modes_title),
                { navController.popBackStack() },
                route = "modes",
            ) {
                ModesSettings(repository, settings) { navController.navigate(it) }
            }
        }
        composable("mode_edit/{modeId}") { backStackEntry ->
            val modeId = backStackEntry.arguments?.getString("modeId").orEmpty()
            SettingsScreen(stringResource(R.string.home_screen_mode_edit_title), { navController.popBackStack() }) {
                ModeEditor(repository, settings, modeId) { navController.popBackStack() }
            }
        }
        composable("about") {
            SettingsScreen(
                stringResource(R.string.home_about_title),
                { navController.popBackStack() },
                route = "about",
            ) {
                AboutSettings(
                    onOpenLicenses = { navController.navigate("licenses") },
                    onOpenLicenseText = { navController.navigate("license_text/$it") },
                    onOpenDebugLog = { navController.navigate("debug_log") },
                )
            }
        }
        composable("debug_log") {
            SettingsScreen(
                stringResource(R.string.home_screen_debug_log_title),
                { navController.popBackStack() },
                route = "debug_log",
            ) {
                DebugLogScreen()
            }
        }
        composable("licenses") {
            SettingsScreen(
                stringResource(R.string.home_screen_licenses_title),
                { navController.popBackStack() },
                route = "licenses",
            ) {
                LicensesScreen { navController.navigate("license_text/$it") }
            }
        }
        composable("license_text/{asset}") { backStackEntry ->
            val asset = backStackEntry.arguments?.getString("asset").orEmpty()
            SettingsScreen(stringResource(R.string.home_screen_license_title), { navController.popBackStack() }) {
                LicenseTextScreen(asset)
            }
        }
    }
}

// ---- home / setup ----

@Composable
private fun AnimatedVisibilityScope.HomeScreen(
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val setup = rememberKeyboardSetup(context)
    // The one screen whose heading is the app's own name rather than a place
    // inside it, so it is centred rather than hung off the bar's left edge.
    // Once there is nothing to set up, the card saying so would be a whole
    // card spent on good news — it becomes a line under the heading instead.
    WmScreen(
        title = stringResource(R.string.app_name),
        route = "home",
        centerTitle = true,
        subtitle = if (setup.ready) stringResource(R.string.home_active_subtitle) else null,
        subtitleIcon = if (setup.ready) Icons.Outlined.CheckCircle else null,
        subtitleIconTint = ActiveGreen,
        badge = { AppIconBadge() },
        badgeInBar = true,
        anim = this,
        actions = {
            IconButton(onClick = { onNavigate("search") }) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.home_search_desc),
                )
            }
        },
    ) {
        if (!setup.ready) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SetupCard(context, setup = setup)
            }
            Spacer(Modifier.height(8.dp))
        }
        SettingsGroup(stringResource(R.string.home_group_typing_title)) {
            item {
                HomeItem(
                    "typing", Icons.Outlined.Keyboard,
                    stringResource(R.string.home_typing_title),
                    stringResource(R.string.home_typing_subtitle), onNavigate,
                )
            }
            item {
                HomeItem(
                    "keypress", Icons.Outlined.TouchApp,
                    stringResource(R.string.home_keypress_title),
                    stringResource(R.string.home_keypress_subtitle), onNavigate,
                )
            }
            item {
                // Named from what is actually enabled, not a fixed pair — the
                // enabled set now starts from the phone's own languages, so
                // there is no one right answer to hard-code here.
                HomeItem(
                    "languages", Icons.Outlined.Language,
                    stringResource(R.string.home_languages_title),
                    enabledLanguagesSummary(settings), onNavigate,
                )
            }
        }
        SettingsGroup(stringResource(R.string.home_group_keyboard_title)) {
            item {
                HomeItem(
                    "appearance", Icons.Outlined.Palette,
                    stringResource(R.string.home_appearance_title),
                    stringResource(R.string.home_appearance_subtitle), onNavigate,
                )
            }
            item {
                HomeItem(
                    "layout", Icons.Outlined.AspectRatio,
                    stringResource(R.string.home_layout_title),
                    stringResource(R.string.home_layout_subtitle), onNavigate,
                )
            }
            item {
                HomeItem(
                    "keymaps", Icons.Outlined.GridOn,
                    stringResource(R.string.home_keymaps_title),
                    stringResource(R.string.home_keymaps_subtitle), onNavigate,
                )
            }
            item {
                HomeItem(
                    "rows", Icons.Outlined.ViewAgenda,
                    stringResource(R.string.home_rows_title),
                    stringResource(R.string.home_rows_subtitle), onNavigate,
                )
            }
            item {
                HomeItem(
                    "modes", Icons.Outlined.Tune,
                    stringResource(R.string.home_modes_title),
                    stringResource(R.string.home_modes_subtitle), onNavigate,
                )
            }
        }
        SettingsGroup(stringResource(R.string.home_group_features_title)) {
            item {
                HomeItem(
                    "emoji", Icons.Outlined.EmojiEmotions,
                    stringResource(R.string.home_emoji_title),
                    stringResource(R.string.home_emoji_subtitle), onNavigate,
                )
            }
            item {
                HomeItem(
                    "tools", Icons.Outlined.Widgets,
                    stringResource(R.string.home_tools_title),
                    stringResource(R.string.home_tools_subtitle), onNavigate,
                )
            }
            item {
                HomeItem(
                    "addons", Icons.Outlined.Extension,
                    stringResource(R.string.home_addons_title),
                    stringResource(R.string.home_addons_subtitle), onNavigate,
                )
            }
        }
        SettingsGroup(stringResource(R.string.home_group_accessibility_title)) {
            item {
                HomeItem(
                    "accessibility", Icons.Outlined.Accessibility,
                    stringResource(R.string.home_accessibility_title),
                    stringResource(R.string.home_accessibility_subtitle), onNavigate,
                )
            }
        }
        SettingsGroup(stringResource(R.string.home_group_data_title)) {
            item {
                HomeItem(
                    "privacy", Icons.Outlined.Security,
                    stringResource(R.string.home_privacy_title),
                    stringResource(R.string.home_privacy_subtitle), onNavigate,
                )
            }
            item {
                HomeItem(
                    "backup", Icons.Outlined.Save,
                    stringResource(R.string.home_backup_title),
                    stringResource(R.string.home_backup_subtitle), onNavigate,
                )
            }
        }
        SettingsGroup(stringResource(R.string.home_group_about_title)) {
            item {
                HomeItem(
                    "about", Icons.Outlined.Info,
                    stringResource(R.string.home_about_title),
                    stringResource(R.string.home_about_subtitle), onNavigate,
                )
            }
        }
    }
}

/** The launcher squircle's corner, as a share of the icon's own width. */
private const val AppIconCorner = 28

/** The tick beside "currently active" — a state, so it is green rather than themed. */
private val ActiveGreen = Color(0xFF43A047)

/**
 * The launcher icon, drawn above the root screen's heading.
 *
 * Composed by hand from the adaptive icon's own two layers rather than loaded
 * as `@mipmap/ic_launcher`: on API 26 and up that resource *is* the
 * `<adaptive-icon>` XML, which `painterResource` cannot inflate. The layers are
 * 108 units wide with the visible circle 72 of them across, so they are drawn
 * oversized by that ratio and masked back down — the same arithmetic the
 * launcher does.
 */
@Composable
private fun AppIconBadge() {
    val layer = HeaderBadgeSize * 108f / 72f
    Box(
        // The launcher's own squircle rather than a circle, so the icon here
        // and the icon on the home screen are recognisably the same object.
        modifier = Modifier.size(HeaderBadgeSize).clip(RoundedCornerShape(AppIconCorner)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(com.wasimaster.wmkeyboard.R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.size(layer),
        )
        Image(
            painterResource(com.wasimaster.wmkeyboard.R.mipmap.ic_launcher_fg),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(layer),
        )
    }
}

/** How far through enabling and selecting the keyboard the user has got. */
internal data class KeyboardSetupState(val enabled: Boolean, val selected: Boolean) {
    val ready: Boolean get() = enabled && selected
}

/**
 * Watches whether this keyboard is enabled and selected. The IME picker is a
 * system dialog, so the activity never pauses or resumes when the user
 * switches keyboards — polling while visible is what keeps the answer honest.
 *
 * [onReady] fires once per transition into the ready state, so onboarding can
 * advance without the user tapping Next after returning from Settings, the way
 * other keyboard apps do.
 */
@Composable
internal fun rememberKeyboardSetup(
    context: Context,
    onReady: (() -> Unit)? = null,
): KeyboardSetupState {
    val imm = remember { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            refresh++
            delay(1000)
        }
    }
    val state = remember(refresh) {
        KeyboardSetupState(
            enabled = imm.enabledInputMethodList.any { it.packageName == context.packageName },
            selected = Settings.Secure
                .getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/') == context.packageName,
        )
    }
    LaunchedEffect(state.ready) {
        if (state.ready) onReady?.invoke()
    }
    return state
}

/**
 * The enable-and-select prompt. Callers that have already read the state — the
 * home screen, which says the same thing in its heading instead — pass it in
 * rather than starting a second poll.
 */
@Composable
internal fun SetupCard(
    context: Context,
    onReady: (() -> Unit)? = null,
    setup: KeyboardSetupState = rememberKeyboardSetup(context, onReady),
) {
    val imm = remember { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    val enabled = setup.enabled

    if (setup.ready) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.home_setup_active_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.home_setup_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (enabled) stringResource(R.string.home_setup_enabled_body)
                else stringResource(R.string.home_setup_disabled_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                if (!enabled) {
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    }) { Text(stringResource(R.string.home_setup_enable_action)) }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(onClick = { imm.showInputMethodPicker() }) {
                    Text(stringResource(R.string.home_setup_switch_action))
                }
            }
        }
    }
}

/**
 * A destination on the settings home. The icon is drawn on the destination's
 * own accent tile — the home list is the app's front door, so it is the one
 * place that trades a uniform column of primary for something scannable.
 *
 * The tile and the name are also the take-off end of the flight into the
 * screen this row opens: the same tile becomes that screen's heading icon, and
 * the same word its heading.
 */
@Composable
private fun HomeItem(
    route: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onNavigate: (String) -> Unit,
) {
    WmRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        accent = routeAccent(route),
        flightTo = route,
        onClick = { onNavigate(route) },
    )
}

// ---- shared scaffold & group card system ----

/**
 * A settings destination. Declared on [AnimatedVisibilityScope] so every call
 * inside a `composable { }` block picks the destination's own animation scope
 * up for free — that scope is half of what a shared element needs.
 *
 * [route] is set on the screens that have a row on the home list: it earns the
 * heading its icon, and flies both the icon and the name over from that row.
 */
@Composable
private fun AnimatedVisibilityScope.SettingsScreen(
    title: String,
    onBack: () -> Unit,
    route: String? = null,
    icon: (@Composable () -> Unit)? = null,
    accent: Color? = null,
    iconTile: Boolean = true,
    iconInBar: Boolean = false,
    barTint: Color? = null,
    centerTitle: Boolean = false,
    subtitle: String? = null,
    subtitleInBar: Boolean = false,
    content: @Composable () -> Unit,
) {
    // A highlight that found no matching row on this screen (the searched
    // entry was the screen itself, or its row is conditionally hidden) must
    // not survive to flash something unrelated on the next screen — unless it
    // was armed *from* this screen on the way out, which is what an addon's
    // Use button does.
    val highlightSerial = remember(title) { SettingsHighlight.serial }
    DisposableEffect(title) { onDispose { SettingsHighlight.clearIfUnchanged(highlightSerial) } }
    WmScreen(
        title = title,
        onBack = onBack,
        route = route,
        icon = icon,
        accent = accent,
        iconTile = iconTile,
        iconInBar = iconInBar,
        barTint = barTint,
        centerTitle = centerTitle,
        subtitle = subtitle,
        subtitleInBar = subtitleInBar,
        anim = this,
        content = content,
    )
}

/**
 * Collects the rows of one visually grouped card stack — the modern
 * Material settings look: each row sits on its own surface with tiny
 * gaps between rows, large rounded corners at the group's ends and
 * small ones inside.
 */
internal class SettingsGroupScope {
    val items = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

@Composable
internal fun SettingsGroup(
    title: String? = null,
    @StringRes highlightKey: Int = 0,
    builder: SettingsGroupScope.() -> Unit,
) {
    // The builder runs during composition, so rows may be added
    // conditionally on snapshot state (e.g. sliders that appear only
    // while their feature's toggle is on).
    val scope = SettingsGroupScope().apply(builder)
    if (scope.items.isEmpty()) return
    // A named group is a scroll target in its own right. Some things the user
    // arrives at from search — or from an addon's Use button — are a whole
    // section rather than one row: "Icon pack", "Your packs", "Installed
    // fonts". Unnamed groups have nothing to match on and stay plain.
    HighlightableRow(title, highlightKey) {
        if (title != null) SectionHeader(title)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            scope.items.forEachIndexed { index, row ->
                val top = if (index == 0) 24.dp else 6.dp
                val bottom = if (index == scope.items.lastIndex) 24.dp else 6.dp
                Surface(
                    shape = RoundedCornerShape(
                        topStart = top, topEnd = top,
                        bottomStart = bottom, bottomEnd = bottom,
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) { row() }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** ListItem colors that let the group card's surface show through. */
@Composable
internal fun transparentListColors(): ListItemColors =
    ListItemDefaults.colors(containerColor = Color.Transparent)

/** "?" affordance that opens a dialog with the full explanation of a setting. */
@Composable
internal fun InfoButton(title: String, detail: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = stringResource(R.string.home_info_desc, title),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(detail) },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(CommonR.string.common_ok))
                }
            },
        )
    }
}

/**
 * A permission's grant state, re-read every time the settings screen comes
 * back to the foreground. Both the runtime permissions and the special ones
 * (Usage Access) are granted on a system screen we leave the app for, so a
 * plain read during composition stays stale until something unrelated
 * recomposes — which is what made the "permission required" rows outlive the
 * grant.
 */
@Composable
internal fun rememberGrantState(check: (Context) -> Boolean): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(check(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = check(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/** The permission that lets the clipboard read the user's screenshots. */
private val ImagesPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun hasImagesPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, ImagesPermission) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Whether the user granted Usage Access — a special permission, so it is an
 * app-op rather than a runtime grant. Mirrors the IME's own check.
 */
private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        ?: return false
    val mode = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName,
            )
        }
    }.getOrDefault(AppOpsManager.MODE_ERRORED)
    return mode == AppOpsManager.MODE_ALLOWED
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        // Aligns with the text inside group rows: 16dp group margin
        // plus the rows' own 16dp content inset.
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    )
}

/** Free-standing explanatory text aligned with group content. */
@Composable
internal fun CaptionText(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

/**
 * A navigation row: title, optional subtitle, optional current value, chevron.
 *
 * [route] names the destination the row opens, which flies the row's name up
 * into that screen's heading. These rows carry no icon — the tile treatment is
 * the home list's alone, and half a group wearing tiles reads as a mistake —
 * so only the name travels; the heading's icon fades in with its screen.
 */
@Composable
internal fun NavRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    route: String? = null,
    @StringRes highlightKey: Int = 0,
    onClick: () -> Unit,
) {
    HighlightableRow(title, highlightKey) {
        WmRow(
            title = title,
            subtitle = subtitle,
            flightTo = route,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value != null) {
                        Text(
                            value,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            onClick = onClick,
        )
    }
}

@Composable
internal fun ToggleSetting(
    title: String,
    subtitle: String?,
    checked: Boolean,
    info: String? = null,
    switchKey: String? = null,
    @StringRes highlightKey: Int = 0,
    onChange: (Boolean) -> Unit,
) {
    HighlightableRow(title, highlightKey) {
        WmRow(
            title = title,
            subtitle = subtitle,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (info != null) InfoButton(title, info)
                    Switch(
                        checked = checked,
                        onCheckedChange = onChange,
                        // The same switch the row that opened this screen was
                        // showing, when the caller says so.
                        modifier = if (switchKey == null) Modifier
                        else Modifier.wmSharedElement(switchKey),
                    )
                }
            },
        )
    }
}

/**
 * How often a drag in progress is pushed through [SliderSetting]'s `onChange`,
 * i.e. written to DataStore. Live previews still follow the finger closely, but
 * a 100-pixel drag no longer queues 100 preference writes (each of which
 * recomposes the whole settings screen).
 */
private const val SLIDER_WRITE_INTERVAL_MS = 40L

/**
 * The live position of a settings slider, held locally so the thumb follows the
 * finger instead of the stored value: routing every touch event through a
 * DataStore write and waiting for the settings flow to come back made the thumb
 * visibly trail. Create one with [rememberLiveSlider], read [value] for both the
 * thumb and the readout, and hand [onDrag]/[onRelease] to the `Slider`.
 */
@Stable
internal class LiveSliderState(initial: Float) {
    var value by mutableFloatStateOf(initial)
        private set
    internal var dragging by mutableStateOf(false)
        private set

    /** Replaced on every composition so the latest lambda is always called. */
    internal var commit: (Float) -> Unit = {}

    internal fun adopt(external: Float) {
        if (!dragging) value = external
    }

    fun onDrag(next: Float) {
        dragging = true
        value = next
    }

    fun onRelease() {
        dragging = false
        commit(value)
    }
}

/**
 * A [LiveSliderState] wired to [value] and [onChange]. Writes are throttled to
 * one per [SLIDER_WRITE_INTERVAL_MS] while dragging — enough for anything
 * previewing the setting to keep up, without queueing a preference write (and a
 * recomposition of the whole screen) per touch event — with a final write when
 * the finger lifts. [value] is adopted only while no drag is in progress, so an
 * edit from elsewhere (a reset, another screen showing the same setting) still
 * moves the thumb but the user's own drag is never fought.
 */
@Composable
internal fun rememberLiveSlider(value: Float, onChange: (Float) -> Unit): LiveSliderState {
    val state = remember { LiveSliderState(value) }
    state.commit = onChange
    LaunchedEffect(value) { state.adopt(value) }
    LaunchedEffect(state) {
        snapshotFlow { state.value }
            .conflate()
            .collect {
                if (state.dragging) state.commit(it)
                delay(SLIDER_WRITE_INTERVAL_MS)
            }
    }
    return state
}

/**
 * A labelled slider row. [display] formats the *live* value rather than taking a
 * pre-rendered string, so the readout tracks the thumb instead of the stored
 * setting; see [rememberLiveSlider] for the rest.
 */
@Composable
internal fun SliderSetting(
    title: String,
    subtitle: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    info: String? = null,
    @StringRes highlightKey: Int = 0,
    onChange: (Float) -> Unit,
) {
    val slider = rememberLiveSlider(value, onChange)
    HighlightableRow(title, highlightKey) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (info != null) InfoButton(title, info)
                Spacer(Modifier.weight(1f))
                Text(display(slider.value), style = MaterialTheme.typography.labelLarge)
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = slider.value,
                onValueChange = slider::onDrag,
                onValueChangeFinished = slider::onRelease,
                valueRange = range,
            )
        }
    }
}

/**
 * Restores the toolbar's default pins ([DefaultToolbarTools]) from Settings —
 * the global set. A mode's own pinned toolbar is reset from that mode's
 * editor (Keyboard modes → the mode → turn off "Custom pinned tools").
 * Confirms first, since it discards whatever the user dragged onto the bar.
 */
@Composable
private fun ResetPinnedToolsSetting(repository: SettingsRepository, scope: CoroutineScope) {
    var confirm by remember { mutableStateOf(false) }
    val title = stringResource(R.string.home_reset_pinned_tools_title)
    HighlightableRow(title) {
        WmRow(
            title = title,
            subtitle = stringResource(R.string.home_reset_pinned_tools_subtitle),
            trailing = {
                OutlinedButton(onClick = { confirm = true }) {
                    Text(stringResource(CommonR.string.common_reset))
                }
            },
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.home_reset_pinned_tools_confirm_title)) },
            text = { Text(stringResource(R.string.home_reset_pinned_tools_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch { repository.setToolbarTools(DefaultToolbarTools) }
                }) { Text(stringResource(CommonR.string.common_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }
}

/** A titled single-choice row of segmented buttons over [options]. */
@Composable
internal fun <T> ChoiceSetting(
    title: String,
    subtitle: String? = null,
    info: String? = null,
    options: List<Pair<T, String>>,
    selected: T,
    @StringRes highlightKey: Int = 0,
    onChange: (T) -> Unit,
) {
    HighlightableRow(title, highlightKey) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (info != null) InfoButton(title, info)
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)) {
                options.forEachIndexed { index, (option, label) ->
                    SegmentedButton(
                        selected = selected == option,
                        onClick = { onChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** One spacebar-swipe slot (quick or hold+swipe): nothing / language / cursor. */
@Composable
private fun SpaceSwipeSetting(
    title: String,
    subtitle: String,
    info: String,
    value: SpaceSwipeAction,
    onChange: (SpaceSwipeAction) -> Unit,
) {
    val nothing = stringResource(R.string.home_space_swipe_none_label)
    val language = stringResource(R.string.home_space_swipe_language_label)
    val cursor = stringResource(R.string.home_space_swipe_cursor_label)
    val numpad = stringResource(R.string.home_space_swipe_numpad_label)
    ChoiceSetting(
        title = title,
        subtitle = subtitle,
        info = info,
        options = SpaceSwipeAction.entries.map { action ->
            action to when (action) {
                SpaceSwipeAction.NONE -> nothing
                SpaceSwipeAction.LANGUAGE -> language
                SpaceSwipeAction.CURSOR -> cursor
                SpaceSwipeAction.NUMPAD -> numpad
            }
        },
        selected = value,
        onChange = onChange,
    )
}

// ---- typing ----

@Composable
private fun TypingSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onOpenDictionary: () -> Unit,
    onOpenCustomDictionaries: () -> Unit,
    onOpenBlacklist: () -> Unit,
    onOpenHardwareShortcuts: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    SettingsGroup(stringResource(R.string.typing_group_corrections_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.typing_autocorrect_title),
                stringResource(R.string.typing_autocorrect_subtitle),
                settings.autocorrect,
                info = stringResource(R.string.typing_autocorrect_info),
            ) { scope.launch { repository.setAutocorrect(it) } }
        }
        if (settings.autocorrect) {
            item {
                val valueFormat = stringResource(R.string.typing_value_multiplier_prefix)
                SliderSetting(
                    stringResource(R.string.typing_autocorrect_confidence_title),
                    subtitle = stringResource(R.string.typing_autocorrect_confidence_subtitle),
                    value = settings.autocorrectConfidence,
                    range = 1.5f..10f,
                    display = { valueFormat.format("%.1f".format(it)) },
                    info = stringResource(R.string.typing_autocorrect_confidence_info),
                ) { scope.launch { repository.setAutocorrectConfidence(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.typing_undo_autocorrect_title),
                    stringResource(R.string.typing_undo_autocorrect_subtitle),
                    settings.revertAutocorrectOnBackspace,
                    info = stringResource(R.string.typing_undo_autocorrect_info),
                ) { scope.launch { repository.setRevertAutocorrectOnBackspace(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.typing_skip_all_caps_title),
                    stringResource(R.string.typing_skip_all_caps_subtitle),
                    settings.autocorrectSkipAllCaps,
                    info = stringResource(R.string.typing_skip_all_caps_info),
                ) { scope.launch { repository.setAutocorrectSkipAllCaps(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.typing_block_offensive_title),
                    stringResource(R.string.typing_block_offensive_subtitle),
                    settings.suggestionStrip.blockOffensiveWords,
                    info = stringResource(R.string.typing_block_offensive_info),
                ) { scope.launch { repository.setBlockOffensiveWords(it) } }
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_auto_apostrophe_title),
                stringResource(R.string.typing_auto_apostrophe_subtitle),
                settings.autoApostrophe,
                info = stringResource(R.string.typing_auto_apostrophe_info),
            ) { scope.launch { repository.setAutoApostrophe(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_auto_capitalize_title),
                stringResource(R.string.typing_auto_capitalize_subtitle),
                settings.autoCapitalize,
                info = stringResource(R.string.typing_auto_capitalize_info),
            ) { scope.launch { repository.setAutoCapitalize(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_double_space_period_title),
                stringResource(R.string.typing_double_space_period_subtitle),
                settings.doubleSpacePeriod,
                info = stringResource(R.string.typing_double_space_period_info),
            ) { scope.launch { repository.setDoubleSpacePeriod(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_double_space_tab_title),
                stringResource(R.string.typing_double_space_tab_subtitle),
                settings.doubleSpaceTab,
                info = stringResource(R.string.typing_double_space_tab_info),
            ) { scope.launch { repository.setDoubleSpaceTab(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_auto_space_punctuation_title),
                stringResource(R.string.typing_auto_space_punctuation_subtitle),
                settings.autoSpaceAfterPunctuation,
                info = stringResource(R.string.typing_auto_space_punctuation_info),
            ) { scope.launch { repository.setAutoSpaceAfterPunctuation(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_space_after_suggestion_title),
                stringResource(R.string.typing_space_after_suggestion_subtitle),
                settings.suggestionStrip.autoSpaceAfterSuggestion,
                info = stringResource(R.string.typing_space_after_suggestion_info),
            ) { scope.launch { repository.setAutoSpaceAfterSuggestion(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_wrap_selection_title),
                stringResource(R.string.typing_wrap_selection_subtitle),
                settings.textEditing.wrapSelectionWithPair,
                info = stringResource(R.string.typing_wrap_selection_info),
            ) { scope.launch { repository.setWrapSelectionWithPair(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_shift_recase_title),
                stringResource(R.string.typing_shift_recase_subtitle),
                settings.textEditing.recapitalizeSelectionWithShift,
                info = stringResource(R.string.typing_shift_recase_info),
            ) { scope.launch { repository.setRecapitalizeSelectionWithShift(it) } }
        }
    }

    SettingsGroup(stringResource(R.string.typing_group_suggestions_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.typing_suggestions_title),
                stringResource(R.string.typing_suggestions_subtitle),
                settings.suggestions,
                info = stringResource(R.string.typing_suggestions_info),
            ) { scope.launch { repository.setSuggestions(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_punctuation_suggestions_title),
                stringResource(R.string.typing_punctuation_suggestions_subtitle),
                settings.suggestionStrip.punctuation,
                info = stringResource(R.string.typing_punctuation_suggestions_info),
            ) { scope.launch { repository.setPunctuationSuggestions(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_suggestions_all_fields_title),
                stringResource(R.string.typing_suggestions_all_fields_subtitle),
                settings.showSuggestionsInAllFields,
                info = stringResource(R.string.typing_suggestions_all_fields_info),
            ) { scope.launch { repository.setShowSuggestionsInAllFields(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_suggestions_first_title),
                stringResource(R.string.typing_suggestions_first_subtitle),
                settings.suggestionStrip.suggestionsFirst,
                info = stringResource(R.string.typing_suggestions_first_info),
            ) { scope.launch { repository.setSuggestionsFirst(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_primary_center_title),
                stringResource(R.string.typing_primary_center_subtitle),
                settings.suggestionStrip.suggestionPrimaryCenter,
                info = stringResource(R.string.typing_primary_center_info),
            ) { scope.launch { repository.setSuggestionPrimaryCenter(it) } }
        }
        item {
            val context = LocalContext.current
            // Prominent disclosure before the system prompt, never the prompt on
            // its own: see PermissionDisclosure.
            val contactsPermission =
                rememberDisclosedPermissionRequest(PermissionDisclosures.CONTACT_NAMES) {
                    scope.launch { repository.setContactSuggestions(true) }
                }
            ToggleSetting(
                stringResource(R.string.typing_contact_names_title),
                stringResource(R.string.typing_contact_names_subtitle),
                settings.contactSuggestions,
                info = stringResource(R.string.typing_contact_names_info),
            ) { enabled ->
                when {
                    !enabled -> scope.launch { repository.setContactSuggestions(false) }
                    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                        PackageManager.PERMISSION_GRANTED ->
                        scope.launch { repository.setContactSuggestions(true) }
                    else -> contactsPermission()
                }
            }
        }
        item {
            val context = LocalContext.current
            val emailPermission =
                rememberDisclosedPermissionRequest(PermissionDisclosures.CONTACT_EMAILS) {
                    scope.launch { repository.setContactEmailSuggestions(true) }
                }
            ToggleSetting(
                stringResource(R.string.typing_contact_emails_title),
                stringResource(R.string.typing_contact_emails_subtitle),
                settings.contactEmailSuggestions,
                info = stringResource(R.string.typing_contact_emails_info),
            ) { enabled ->
                when {
                    !enabled -> scope.launch { repository.setContactEmailSuggestions(false) }
                    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                        PackageManager.PERMISSION_GRANTED ->
                        scope.launch { repository.setContactEmailSuggestions(true) }
                    else -> emailPermission()
                }
            }
        }
        if (settings.contactEmailSuggestions) {
            item {
                ToggleSetting(
                    stringResource(R.string.typing_contact_emails_in_email_fields_title),
                    stringResource(R.string.typing_contact_emails_in_email_fields_subtitle),
                    settings.contactEmailSuggestionsInEmailFields,
                    info = stringResource(R.string.typing_contact_emails_in_email_fields_info),
                ) { scope.launch { repository.setContactEmailSuggestionsInEmailFields(it) } }
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_app_names_title),
                stringResource(R.string.typing_app_names_subtitle),
                settings.appNameSuggestions,
                info = stringResource(R.string.typing_app_names_info),
            ) { scope.launch { repository.setAppNameSuggestions(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_inline_emoji_search_title),
                stringResource(R.string.typing_inline_emoji_search_subtitle),
                settings.inlineEmojiSearch,
                info = stringResource(R.string.typing_inline_emoji_search_info),
            ) { scope.launch { repository.setInlineEmojiSearch(it) } }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            item {
                ToggleSetting(
                    stringResource(R.string.typing_inline_autofill_title),
                    stringResource(R.string.typing_inline_autofill_subtitle),
                    settings.inlineAutofill,
                    info = stringResource(R.string.typing_inline_autofill_info),
                ) { scope.launch { repository.setInlineAutofill(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.typing_smart_replies_title),
                    stringResource(R.string.typing_smart_replies_subtitle),
                    settings.suggestionStrip.systemSmartReplies,
                    info = stringResource(R.string.typing_smart_replies_info),
                ) { scope.launch { repository.setSystemSmartReplies(it) } }
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_smart_hit_detection_title),
                stringResource(R.string.typing_smart_hit_detection_subtitle),
                settings.layoutBehavior.smartHitDetection,
                info = stringResource(R.string.typing_smart_hit_detection_info),
            ) { scope.launch { repository.setSmartHitDetection(it) } }
        }
        item {
            NavRow(
                stringResource(R.string.typing_personal_dictionary_title),
                stringResource(R.string.typing_personal_dictionary_subtitle),
                route = "dictionary",
                onClick = onOpenDictionary,
            )
        }
        item {
            NavRow(
                stringResource(R.string.typing_custom_dictionaries_title),
                stringResource(R.string.typing_custom_dictionaries_subtitle),
                route = "customdictionaries",
                onClick = onOpenCustomDictionaries,
            )
        }
        item {
            val count = settings.suggestionBlacklist.size
            NavRow(
                stringResource(R.string.typing_blacklist_title),
                if (count == 0) {
                    stringResource(R.string.typing_blacklist_subtitle)
                } else {
                    pluralStringResource(R.plurals.typing_blacklist_count_subtitle, count, count)
                },
                route = "blacklist",
                onClick = onOpenBlacklist,
            )
        }
    }

    SettingsGroup(stringResource(R.string.typing_group_smart_chips_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.typing_smart_chips_title),
                stringResource(R.string.typing_smart_chips_subtitle),
                settings.smartSuggestions,
                info = stringResource(R.string.typing_smart_chips_info),
            ) { scope.launch { repository.setSmartSuggestions(it) } }
        }
        if (settings.smartSuggestions) {
            item {
                ToggleSetting(
                    stringResource(R.string.typing_smart_calc_title),
                    stringResource(R.string.typing_smart_calc_subtitle),
                    settings.smartCalc,
                ) { scope.launch { repository.setSmartCalc(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.typing_smart_currency_title),
                    stringResource(R.string.typing_smart_currency_subtitle, settings.currencyTo),
                    settings.smartCurrency,
                ) { scope.launch { repository.setSmartCurrency(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.typing_smart_units_title),
                    stringResource(R.string.typing_smart_units_subtitle),
                    settings.smartUnits,
                ) { scope.launch { repository.setSmartUnits(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.typing_smart_tool_keywords_title),
                    stringResource(R.string.typing_smart_tool_keywords_subtitle),
                    settings.smartToolKeywords,
                    info = stringResource(R.string.typing_smart_tool_keywords_info),
                ) { scope.launch { repository.setSmartToolKeywords(it) } }
            }
        }
    }

    SettingsGroup(stringResource(R.string.typing_group_gestures_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.typing_glide_typing_title),
                stringResource(R.string.typing_glide_typing_subtitle),
                settings.gestureTyping,
                info = stringResource(R.string.typing_glide_typing_info),
            ) { scope.launch { repository.setGestureTyping(it) } }
        }
        // What a letter swipe does — glide a word or handwrite it. Full builds
        // only (needs the ML Kit handwriting model), and only relevant once
        // letter swipes are switched on above.
        if (BuildConfig.ENABLE_ML_KIT_HANDWRITING && settings.gestureTyping) {
            item {
                ChoiceSetting(
                    title = stringResource(R.string.typing_letter_swipe_action_title),
                    subtitle = stringResource(R.string.typing_letter_swipe_action_subtitle),
                    info = stringResource(R.string.typing_letter_swipe_action_info),
                    options = listOf(
                        LetterSwipeAction.TYPE_WORDS to
                            stringResource(R.string.typing_letter_swipe_type_words_label),
                        LetterSwipeAction.HANDWRITE to
                            stringResource(R.string.typing_letter_swipe_handwrite_label),
                    ),
                    selected = settings.letterSwipeAction,
                    onChange = { scope.launch { repository.setLetterSwipeAction(it) } },
                )
            }
        }
        if (settings.gestureTyping) {
            // Glide-word only: crossing the spacebar to chain words has no
            // meaning when a swipe draws handwriting instead.
            if (settings.letterSwipeAction == LetterSwipeAction.TYPE_WORDS) {
                item {
                    ToggleSetting(
                        stringResource(R.string.typing_space_glide_multiword_title),
                        stringResource(R.string.typing_space_glide_multiword_subtitle),
                        settings.gesture.spaceGlideMultiWord,
                        info = stringResource(R.string.typing_space_glide_multiword_info),
                    ) { scope.launch { repository.setGestureSpaceMultiWord(it) } }
                }
            }
            item {
                val valueFormat = stringResource(R.string.typing_value_multiplier_suffix)
                SliderSetting(
                    stringResource(R.string.typing_swipe_start_distance_title),
                    subtitle = stringResource(R.string.typing_swipe_start_distance_subtitle),
                    value = settings.gesture.startThresholdSlop,
                    range = 0.5f..4f,
                    display = { valueFormat.format("%.1f".format(it)) },
                    info = stringResource(R.string.typing_swipe_start_distance_info),
                ) { scope.launch { repository.setGestureStartThresholdSlop(it) } }
            }
            // Glide-word only: the guard raises the swipe-start bar, which never
            // runs in handwrite mode (there is no word glide to suppress).
            if (settings.letterSwipeAction == LetterSwipeAction.TYPE_WORDS) {
                item {
                    val offLabel = stringResource(CommonR.string.common_off)
                    val msFormat = stringResource(R.string.typing_value_milliseconds)
                    SliderSetting(
                        stringResource(R.string.typing_gesture_cooldown_title),
                        subtitle = stringResource(R.string.typing_gesture_cooldown_subtitle),
                        value = settings.gesture.postTypeCooldownMs.toFloat(),
                        range = 0f..500f,
                        display = { if (it.roundToInt() == 0) offLabel else msFormat.format(it.roundToInt()) },
                        info = stringResource(R.string.typing_gesture_cooldown_info),
                    ) { scope.launch { repository.setGesturePostTypeCooldownMs(it.roundToInt()) } }
                }
            }
            // Handwrite-with-swipes only: window after a drawn stroke in which a
            // tap is grabbed as an ink dot rather than typing.
            if (BuildConfig.ENABLE_ML_KIT_HANDWRITING &&
                settings.letterSwipeAction == LetterSwipeAction.HANDWRITE
            ) {
                item {
                    val offLabel = stringResource(CommonR.string.common_off)
                    val msFormat = stringResource(R.string.typing_value_milliseconds)
                    SliderSetting(
                        stringResource(R.string.typing_handwrite_dot_title),
                        subtitle = stringResource(R.string.typing_handwrite_dot_subtitle),
                        value = settings.gesture.handwriteDotCooldownMs.toFloat(),
                        range = 0f..1500f,
                        display = { if (it.roundToInt() == 0) offLabel else msFormat.format(it.roundToInt()) },
                        info = stringResource(R.string.typing_handwrite_dot_info),
                    ) { scope.launch { repository.setGestureHandwriteDotCooldownMs(it.roundToInt()) } }
                }
            }
            item {
                val dpFormat = stringResource(R.string.typing_value_dp)
                SliderSetting(
                    stringResource(R.string.typing_trail_width_title),
                    subtitle = stringResource(R.string.typing_trail_width_subtitle),
                    value = settings.gesture.trailWidthDp,
                    range = 2f..24f,
                    display = { dpFormat.format(it.roundToInt()) },
                ) { scope.launch { repository.setGestureTrailWidthDp(it) } }
            }
            item {
                val msFormat = stringResource(R.string.typing_value_milliseconds)
                SliderSetting(
                    stringResource(R.string.typing_trail_length_title),
                    subtitle = stringResource(R.string.typing_trail_length_subtitle),
                    value = settings.gesture.trailDurationMs.toFloat(),
                    range = 100f..1200f,
                    display = { msFormat.format(it.roundToInt()) },
                ) { scope.launch { repository.setGestureTrailDurationMs(it.roundToInt()) } }
            }
            item {
                val percentFormat = stringResource(R.string.typing_value_percent)
                SliderSetting(
                    stringResource(R.string.typing_trail_opacity_title),
                    value = settings.gesture.trailOpacity,
                    range = 0.1f..1f,
                    display = { percentFormat.format((it * 100).roundToInt()) },
                ) { scope.launch { repository.setGestureTrailOpacity(it) } }
            }
        }
        item {
            SpaceSwipeSetting(
                title = stringResource(R.string.typing_space_short_swipe_title),
                subtitle = stringResource(R.string.typing_space_short_swipe_subtitle),
                info = stringResource(R.string.typing_space_short_swipe_info),
                value = settings.spaceShortSwipe,
            ) { scope.launch { repository.setSpaceShortSwipe(it) } }
        }
        item {
            SpaceSwipeSetting(
                title = stringResource(R.string.typing_space_long_swipe_title),
                subtitle = stringResource(R.string.typing_space_long_swipe_subtitle),
                info = stringResource(R.string.typing_space_long_swipe_info),
                value = settings.spaceLongSwipe,
            ) { scope.launch { repository.setSpaceLongSwipe(it) } }
        }
        // 2-D cursor pad only makes sense once a slide is set to cursor control.
        if (settings.spaceShortSwipe == SpaceSwipeAction.CURSOR ||
            settings.spaceLongSwipe == SpaceSwipeAction.CURSOR
        ) {
            item {
                ToggleSetting(
                    stringResource(R.string.typing_space_cursor_2d_title),
                    stringResource(R.string.typing_space_cursor_2d_subtitle),
                    settings.layoutBehavior.spaceCursor2d,
                    info = stringResource(R.string.typing_space_cursor_2d_info),
                ) { scope.launch { repository.setSpaceCursor2d(it) } }
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_space_swipe_down_hide_title),
                stringResource(R.string.typing_space_swipe_down_hide_subtitle),
                settings.layoutBehavior.spaceSwipeDownHide,
                info = stringResource(R.string.typing_space_swipe_down_hide_info),
            ) { scope.launch { repository.setSpaceSwipeDownHide(it) } }
        }
        if (settings.spaceShortSwipe == SpaceSwipeAction.LANGUAGE ||
            settings.spaceLongSwipe == SpaceSwipeAction.LANGUAGE
        ) {
            item {
                ToggleSetting(
                    stringResource(R.string.typing_spacebar_language_arrows_title),
                    stringResource(R.string.typing_spacebar_language_arrows_subtitle),
                    settings.spacebarLanguageArrows,
                    info = stringResource(R.string.typing_spacebar_language_arrows_info),
                ) { scope.launch { repository.setSpacebarLanguageArrows(it) } }
            }
        }
        item {
            ChoiceSetting(
                stringResource(R.string.typing_spacebar_display_title),
                subtitle = stringResource(R.string.typing_spacebar_display_subtitle),
                info = stringResource(R.string.typing_spacebar_display_info),
                options = listOf(
                    SpacebarDisplay.LANGUAGE to
                        stringResource(R.string.typing_spacebar_display_language_label),
                    SpacebarDisplay.LAYOUT to
                        stringResource(R.string.typing_spacebar_display_layout_label),
                    SpacebarDisplay.BOTH to
                        stringResource(R.string.typing_spacebar_display_both_label),
                ),
                selected = settings.layoutBehavior.spacebarDisplay,
            ) { scope.launch { repository.setSpacebarDisplay(it) } }
        }
        item {
            TextFieldSetting(
                label = stringResource(R.string.typing_spacebar_text_label),
                value = settings.spacebarLabel,
                // The %s token is text the user types, so it travels as an argument.
                hint = stringResource(R.string.typing_spacebar_text_hint, "%s"),
            ) { repository.setSpacebarLabel(it) }
        }
    }

    SettingsGroup(stringResource(R.string.typing_group_backspace_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.typing_backspace_swipe_title),
                stringResource(R.string.typing_backspace_swipe_subtitle),
                settings.backspaceSwipeDelete,
                info = stringResource(R.string.typing_backspace_swipe_info),
            ) { scope.launch { repository.setBackspaceSwipeDelete(it) } }
        }
    }

    SettingsGroup(stringResource(R.string.typing_group_enter_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.typing_shift_enter_title),
                stringResource(R.string.typing_shift_enter_subtitle),
                settings.layoutBehavior.shiftEnterNewline,
                info = stringResource(R.string.typing_shift_enter_info),
            ) { scope.launch { repository.setShiftEnterNewline(it) } }
        }
    }

    SettingsGroup(stringResource(R.string.typing_group_volume_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.typing_volume_cursor_title),
                stringResource(R.string.typing_volume_cursor_subtitle),
                settings.volumeCursor,
                info = stringResource(R.string.typing_volume_cursor_info),
            ) { scope.launch { repository.setVolumeCursor(it) } }
        }
        if (settings.volumeCursor) {
            item {
                ToggleSetting(
                    stringResource(R.string.typing_volume_cursor_media_title),
                    stringResource(R.string.typing_volume_cursor_media_subtitle),
                    settings.volumeCursorMediaAware,
                    info = stringResource(R.string.typing_volume_cursor_media_info),
                ) { scope.launch { repository.setVolumeCursorMediaAware(it) } }
            }
        }
    }

    SettingsGroup(stringResource(R.string.typing_group_hardware_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.typing_hardware_input_title),
                stringResource(R.string.typing_hardware_input_subtitle),
                settings.hardwareKeyboardInput,
                info = stringResource(R.string.typing_hardware_input_info),
            ) { scope.launch { repository.setHardwareKeyboardInput(it) } }
        }
        val hw = settings.hardwareKeyboard
        item {
            ToggleSetting(
                stringResource(R.string.typing_hw_shortcuts_title),
                stringResource(R.string.typing_hw_shortcuts_subtitle),
                hw.shortcutsEnabled,
                info = stringResource(R.string.typing_hw_shortcuts_info),
            ) { scope.launch { repository.setHwShortcutsEnabled(it) } }
        }
        if (hw.shortcutsEnabled) {
            item {
                // A chord spells itself, so it arrives with no template around it.
                val leaderParts = leaderLabel(parseLeader(hw.leader) ?: DefaultLeader)
                val leaderText = if (leaderParts.templateRes == 0) {
                    leaderParts.text
                } else {
                    stringResource(leaderParts.templateRes, leaderParts.text)
                }
                NavRow(
                    stringResource(R.string.typing_hw_shortcuts_list_title),
                    stringResource(R.string.typing_hw_shortcuts_list_subtitle),
                    value = leaderText,
                    route = "hwshortcuts",
                    onClick = onOpenHardwareShortcuts,
                )
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_hw_panel_nav_title),
                stringResource(R.string.typing_hw_panel_nav_subtitle),
                hw.panelNavigation,
                info = stringResource(R.string.typing_hw_panel_nav_info),
            ) { scope.launch { repository.setHwPanelNavigation(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_hw_esc_title),
                stringResource(R.string.typing_hw_esc_subtitle),
                hw.escClosesPanel,
                info = stringResource(R.string.typing_hw_esc_info),
            ) { scope.launch { repository.setHwEscClosesPanel(it) } }
        }
        item {
            ChoiceSetting(
                stringResource(R.string.typing_hw_suggestion_hotkeys_title),
                subtitle = stringResource(R.string.typing_hw_suggestion_hotkeys_subtitle),
                info = stringResource(R.string.typing_hw_suggestion_hotkeys_info),
                options = SuggestionHotkeyMode.entries.map { it to stringResource(it.labelRes) },
                selected = hw.suggestionHotkeys,
            ) { scope.launch { repository.setHwSuggestionHotkeys(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.typing_hw_auto_show_title),
                stringResource(R.string.typing_hw_auto_show_subtitle),
                hw.autoShowUi,
                info = stringResource(R.string.typing_hw_auto_show_info),
            ) { scope.launch { repository.setHwAutoShowUi(it) } }
        }
    }
}

/**
 * The letter that opens each tool from a physical keyboard, plus the shortcut key
 * that arms them.
 *
 * The rows are every supported tool rather than a list the user builds, so there
 * is no "add" — a tool either has a letter or it does not, and the unbound ones
 * are still reachable through the toolbox.
 */
@Composable
private fun HardwareShortcutsSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val hw = settings.hardwareKeyboard
    val leader = parseLeader(hw.leader) ?: DefaultLeader
    var editingLeader by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ToolbarTool?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val tools = remember(hw.toolByLetter, settings.enabledTools) {
        val letterOf = hw.toolByLetter.entries.associate { (letter, tool) -> tool to letter }
        // Bound tools first, in letter order, so the table reads as "what my
        // keyboard does" before "what else could be bound".
        ToolbarTool.entries.filter(::isSupportedTool)
            .sortedWith(compareBy({ letterOf[it] == null }, { letterOf[it] ?: ' ' }, { it.name }))
    }
    val letterOf = hw.toolByLetter.entries.associate { (letter, tool) -> tool to letter }
    // A chord spells itself, so it arrives as plain text; a double tap needs the
    // wording around the modifier name, which only this layer can resolve.
    val leaderSpec = leaderLabel(leader)
    val leaderName = if (leaderSpec.templateRes == 0) {
        leaderSpec.text
    } else {
        stringResource(leaderSpec.templateRes, leaderSpec.text)
    }
    val leaderTitle = stringResource(R.string.hardware_shortcuts_leader_title)

    Column {
        CaptionText(
            stringResource(
                R.string.hardware_shortcuts_intro_body,
                ToolboxLetter,
                CheatSheetLetter,
            ),
        )
        SettingsGroup(leaderTitle) {
            item {
                NavRow(
                    leaderTitle,
                    stringResource(R.string.hardware_shortcuts_leader_subtitle),
                    value = leaderName,
                    onClick = { editingLeader = true },
                )
            }
        }
        SettingsGroup(stringResource(R.string.hardware_shortcuts_tools_group_title)) {
            for (tool in tools) {
                item {
                    val letter = letterOf[tool]
                    WmRow(
                        title = stringResource(toolTitle(tool)),
                        leading = {
                            SlotIcon(IconSlots.forTool(tool), contentDescription = null)
                        },
                        supporting = if (tool !in settings.enabledTools) {
                            { CaptionText(stringResource(R.string.hardware_shortcuts_tool_off_subtitle)) }
                        } else null,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    letter?.toString() ?: "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (letter == null) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                                if (letter != null) {
                                    IconButton(onClick = {
                                        scope.launch { repository.setHwToolLetter(letter, null) }
                                    }) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = stringResource(
                                                R.string.hardware_shortcuts_unbind_desc,
                                                toolTitle(tool),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                        onClick = { editing = tool },
                    )
                }
            }
        }
        TextButton(
            onClick = { confirmReset = true },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) { Text(stringResource(CommonR.string.common_reset_defaults)) }
    }

    if (editingLeader) {
        LeaderCaptureDialog(
            current = leader,
            onDismiss = { editingLeader = false },
            onPick = { picked ->
                editingLeader = false
                scope.launch { repository.setHwLeader(formatLeader(picked)) }
            },
        )
    }
    editing?.let { tool ->
        LetterCaptureDialog(
            tool = tool,
            current = letterOf[tool],
            takenBy = { letter -> hw.toolByLetter[letter] },
            onDismiss = { editing = null },
            onPick = { letter ->
                editing = null
                scope.launch { repository.setHwToolLetter(letter, tool) }
            },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.hardware_shortcuts_reset_title)) },
            text = { Text(stringResource(R.string.hardware_shortcuts_reset_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        repository.setHwToolLetters(DefaultToolLetters)
                        repository.setHwLeader(formatLeader(DefaultLeader))
                    }
                }) { Text(stringResource(CommonR.string.common_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }
}

/**
 * Picks the shortcut key: a double-tapped modifier, or a chord pressed on an
 * attached keyboard.
 *
 * The double-tap choices matter more than the capture field — most people
 * editing this screen are holding a phone with no keyboard plugged in, and a
 * "press a key" prompt would leave them stuck.
 */
@Composable
private fun LeaderCaptureDialog(
    current: LeaderTrigger,
    onDismiss: () -> Unit,
    onPick: (LeaderTrigger) -> Unit,
) {
    var captured by remember { mutableStateOf<KeyChord?>(null) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hardware_shortcuts_leader_title)) },
        text = {
            Column {
                CaptionText(stringResource(R.string.hardware_shortcuts_double_tap_body))
                for (modifier in TapModifier.entries) {
                    val trigger = LeaderTrigger.DoubleTap(modifier)
                    // The same wording the row on the settings screen shows, and
                    // a double tap always carries a template to fill.
                    val spec = leaderLabel(trigger)
                    WmRow(
                        title = stringResource(spec.templateRes, spec.text),
                        trailing = {
                            if (current == trigger && captured == null) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = stringResource(
                                        R.string.hardware_shortcuts_current_desc,
                                    ),
                                )
                            }
                        },
                        onClick = { onPick(trigger) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                CaptionText(stringResource(R.string.hardware_shortcuts_capture_body))
                // A real focusable window, unlike the keyboard's own, so Compose
                // focus is the right tool here.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .focusRequester(requester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val native = event.nativeKeyEvent
                            // Wait for the key the modifiers are qualifying.
                            if (KeyEvent.isModifierKey(native.keyCode)) return@onPreviewKeyEvent true
                            val chord = KeyChord(
                                keyCode = native.keyCode,
                                ctrl = native.isCtrlPressed,
                                alt = native.isAltPressed,
                                shift = native.isShiftPressed,
                                meta = native.isMetaPressed,
                            )
                            // A bare key would swallow ordinary typing, and a
                            // chord this app cannot name cannot be stored.
                            captured = chord.takeIf { it.hasModifier && formatChord(it) != null }
                            true
                        },
                ) {
                    Text(
                        captured?.let(::describeChord)
                            ?: stringResource(R.string.hardware_shortcuts_waiting_progress),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                captured?.let { chord ->
                    if (chord in ReservedChords) {
                        CaptionText(
                            stringResource(
                                R.string.hardware_shortcuts_reserved_error,
                                describeChord(chord),
                            ),
                            error = true,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = captured != null,
                onClick = { captured?.let { onPick(LeaderTrigger.Chord(it)) } },
            ) { Text(stringResource(R.string.hardware_shortcuts_use_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

/**
 * Picks the letter for one tool. Typed rather than captured: this is a single
 * character, and a text field works with or without a keyboard attached.
 */
@Composable
private fun LetterCaptureDialog(
    tool: ToolbarTool,
    current: Char?,
    takenBy: (Char) -> ToolbarTool?,
    onDismiss: () -> Unit,
    onPick: (Char) -> Unit,
) {
    var text by remember { mutableStateOf(current?.toString().orEmpty()) }
    val letter = text.trim().uppercase().firstOrNull()
    val valid = letter != null && (letter in 'A'..'Z' || letter in '0'..'9') &&
        letter !in ReservedLetters
    val clash = letter?.let(takenBy)?.takeIf { it != tool }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(toolTitle(tool))) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.takeLast(1) },
                    label = { Text(stringResource(R.string.hardware_shortcuts_letter_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    letter in ReservedLetters -> CaptionText(
                        stringResource(
                            R.string.hardware_shortcuts_letter_reserved_error,
                            letter?.toString().orEmpty(),
                            ToolboxLetter,
                            CheatSheetLetter,
                        ),
                        error = true,
                    )
                    clash != null -> CaptionText(
                        stringResource(
                            R.string.hardware_shortcuts_letter_clash_body,
                            letter.toString(),
                            toolTitle(clash),
                        ),
                    )
                    else -> CaptionText(stringResource(R.string.hardware_shortcuts_letter_hint))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { letter?.let(onPick) }) {
                Text(
                    if (clash != null) {
                        stringResource(R.string.hardware_shortcuts_letter_move_action)
                    } else {
                        stringResource(CommonR.string.common_save)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

// ---- key press ----

/**
 * Key-press sound controls, shared by the Key press settings screen and the
 * sound & haptics tool's detail page. Changes preview immediately through
 * [KeySoundPlayer]. [trailing] appends extra rows to the same card group.
 */
@Composable
private fun KeySoundGroup(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    trailing: (SettingsGroupScope.() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Slider readouts are plain lambdas, so their format strings are resolved
    // here and captured. The format also puts the number through the locale,
    // which is what gives Bengali or Arabic digits.
    val percentFormat = stringResource(R.string.typing_value_percent)
    SettingsGroup(stringResource(R.string.hardware_sound_group_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.hardware_sound_key_title),
                stringResource(R.string.hardware_sound_key_subtitle),
                settings.keySound,
            ) {
                scope.launch { repository.setKeySound(it) }
                if (it) {
                    KeySoundPlayer.preview(context, settings.keySoundStyle, settings.keySoundVolume)
                }
            }
        }
        item {
            // Hand-built rather than a ChoiceSetting (the chips need their own
            // row), so the highlight wrapper every other control gets for free
            // is spelled out here — this is where the Sound addon's Use button
            // lands. The anchor is the row's own string resource, so the match
            // holds in every language.
            HighlightableRow(null, highlightKey = R.string.hardware_sound_style_title) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.hardware_sound_style_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    InfoButton(
                        stringResource(R.string.hardware_sound_style_title),
                        stringResource(R.string.hardware_sound_style_info),
                    )
                }
                // Custom is a segment like any other, so the styles read as one
                // choice rather than five here and a sixth hidden in a list. It
                // names a file rather than a fixed waveform, so it needs a sound
                // installed before it can be picked — the list below is where that
                // sound is chosen and where the "install one" note lives.
                val soundStore = remember { SoundStore.get(context) }
                val soundRevision by soundStore.revision.collectAsStateWithLifecycle()
                val installedSounds = remember(soundRevision) { soundStore.sounds() }
                // Chips rather than a segmented row. Six equal segments across a
                // phone leave ~55dp of label each, which truncated "Chime" to
                // "Chim" and "Custom" to "Custo"; a segmented row set to scroll is
                // worse still, since SegmentedButton has a wide minimum and only
                // three and a half fit. Chips size to their own text, so every
                // style keeps its real name, and the row scrolls only as far as it
                // has to. Same control the addon type filter uses.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (style in KeySoundStyle.entries) {
                        val custom = style == KeySoundStyle.CUSTOM
                        FilterChip(
                            selected = settings.keySoundStyle == style,
                            onClick = {
                                scope.launch {
                                    if (custom) {
                                        // Falls back to the first installed sound
                                        // when none has been chosen yet, so the
                                        // chip always makes a sound. With nothing
                                        // installed it still selects — the section
                                        // it reveals is where a sound is imported,
                                        // so a disabled chip would hide its own
                                        // remedy.
                                        val id = settings.keySoundCustom.customId
                                            .takeIf { id -> installedSounds.any { it.id == id } }
                                            ?: installedSounds.firstOrNull()?.id
                                        if (id == null) {
                                            repository.setKeySoundStyle(style)
                                        } else {
                                            repository.setKeySoundCustomId(id)
                                            KeySoundPlayer.preview(
                                                context, style, settings.keySoundVolume, id,
                                            )
                                        }
                                    } else {
                                        repository.setKeySoundStyle(style)
                                        // Sound the freshly picked style so the user
                                        // hears the choice immediately.
                                        KeySoundPlayer.preview(context, style, settings.keySoundVolume)
                                    }
                                }
                            },
                            label = {
                                Text(
                                    stringResource(
                                        when (style) {
                                            KeySoundStyle.CLICK ->
                                                R.string.hardware_sound_style_click_label
                                            KeySoundStyle.STANDARD ->
                                                R.string.hardware_sound_style_standard_label
                                            KeySoundStyle.POP ->
                                                R.string.hardware_sound_style_pop_label
                                            KeySoundStyle.THOCK ->
                                                R.string.hardware_sound_style_thock_label
                                            KeySoundStyle.CHIME ->
                                                R.string.hardware_sound_style_chime_label
                                            KeySoundStyle.CUSTOM -> CommonR.string.common_custom
                                        },
                                    ),
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }
        }
        // Only under Custom. The sound library and its import button are what
        // Custom *means*; showing them under Click is offering a choice that
        // has no effect until the style changes too.
        if (settings.keySoundStyle == KeySoundStyle.CUSTOM) {
            item { InstalledSoundSection(repository, settings) }
        }
        item {
            SliderSetting(
                stringResource(R.string.hardware_sound_volume_title),
                subtitle = stringResource(R.string.hardware_sound_volume_subtitle),
                value = settings.keySoundVolume,
                range = 0.05f..1f,
                display = { percentFormat.format((it * 100).roundToInt()) },
            ) {
                scope.launch { repository.setKeySoundVolume(it) }
                // Debounced inside the player, so dragging previews smoothly.
                KeySoundPlayer.preview(context, settings.keySoundStyle, it)
            }
        }
        trailing?.invoke(this)
    }
}

/**
 * The installed key sounds — whatever came from an addon repository, plus
 * anything imported here — and the import button.
 *
 * Picking one also switches the style to [KeySoundStyle.CUSTOM]; choosing a
 * sound and then finding the keyboard still clicking would be baffling.
 */
@Composable
private fun InstalledSoundSection(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val store = remember { SoundStore.get(context) }
    val revision by store.revision.collectAsStateWithLifecycle()
    val sounds = remember(revision) { store.sounds() }
    var message by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.requireInputStream(uri).use {
                        SoundFile.import(it, store, name = fontFileLabel(context, uri))
                    }
                }.getOrElse {
                    SoundImportResult.Failed(FeedbackR.string.core_feedback_sound_import_read_error)
                }
            }
            when (result) {
                is SoundImportResult.Imported -> {
                    repository.setKeySoundCustomId(result.sound.id)
                    KeySoundPlayer.preview(
                        context, KeySoundStyle.CUSTOM, settings.keySoundVolume, result.sound.id,
                    )
                }
                is SoundImportResult.NotASound -> message = context.getString(result.messageRes)
                SoundImportResult.TooManySounds ->
                    message = context.resources.getQuantityString(
                        R.plurals.hardware_sound_limit_error,
                        SoundStore.MAX_SOUNDS,
                        SoundStore.MAX_SOUNDS,
                    )
                // The refusal carries at most one argument, and "" means none.
                is SoundImportResult.Failed -> message = if (result.messageArg.isEmpty()) {
                    context.getString(result.messageRes)
                } else {
                    context.getString(result.messageRes, result.messageArg)
                }
            }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text(stringResource(CommonR.string.common_ok))
                }
            },
        )
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        if (sounds.isEmpty()) {
            CaptionText(stringResource(R.string.hardware_sound_empty))
        }
        for (sound in sounds) {
            val selected = settings.keySoundStyle == KeySoundStyle.CUSTOM &&
                settings.keySoundCustom.customId == sound.id
            WmRow(
                title = sound.name,
                supporting = sound.author.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = stringResource(
                                    R.string.hardware_sound_selected_desc,
                                ),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                if (selected) repository.setKeySoundStyle(KeySoundStyle.CLICK)
                                withContext(Dispatchers.IO) { store.delete(sound.id) }
                                // The pool keeps its decoded copy independently
                                // of the file, so it has to be told too.
                                KeySoundPlayer.forgetCustom(sound.id)
                            }
                        }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(
                                    R.string.hardware_sound_delete_desc,
                                    sound.name,
                                ),
                            )
                        }
                    }
                },
                onClick = {
                    scope.launch { repository.setKeySoundCustomId(sound.id) }
                    KeySoundPlayer.preview(
                        context, KeySoundStyle.CUSTOM, settings.keySoundVolume, sound.id,
                    )
                },
            )
        }
        OutlinedButton(
            onClick = { importLauncher.launch(SoundFile.IMPORT_MIME_TYPES) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text(stringResource(R.string.hardware_sound_import_action)) }
    }
}

@Composable
private fun KeyPressSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Lets the SYSTEM_* preview fire through the real platform key haptic.
    val view = LocalView.current
    SettingsGroup(stringResource(R.string.keypress_haptics_group_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.keypress_haptics_title),
                stringResource(R.string.keypress_haptics_subtitle),
                settings.hapticFeedback,
                info = stringResource(R.string.keypress_haptics_info),
            ) {
                scope.launch { repository.setHapticFeedback(it) }
                if (it) {
                    HapticPlayer.preview(
                        context, settings.hapticStyle, settings.hapticAmplitude, settings.hapticStrengthMs,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.keypress_haptic_style_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                InfoButton(
                    stringResource(R.string.keypress_haptic_style_title),
                    stringResource(R.string.keypress_haptic_style_info),
                )
            }
            // Six styles overflow a segmented row; wrapping chips give each a
            // full, readable label. Ordered best-to-worst via HapticStyle.entries.
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
                            // Fire the motor with the freshly picked style so the
                            // user feels the choice immediately.
                            HapticPlayer.preview(
                                context, style, settings.hapticAmplitude, settings.hapticStrengthMs, view,
                            )
                        },
                        label = { Text(stringResource(style.labelRes), maxLines = 1) },
                    )
                }
            }
        }
        if (settings.hapticStyle == HapticStyle.CUSTOM) {
            item {
                SliderSetting(
                    stringResource(R.string.keypress_haptic_strength_title),
                    subtitle = stringResource(R.string.keypress_haptic_strength_subtitle),
                    value = settings.hapticStrengthMs.toFloat(),
                    range = 5f..60f,
                    display = { context.getString(R.string.keypress_value_ms, it.roundToInt()) },
                    info = stringResource(R.string.keypress_haptic_strength_info),
                ) {
                    scope.launch { repository.setHapticStrengthMs(it.toInt()) }
                    // Debounced inside the player, so dragging previews smoothly.
                    HapticPlayer.preview(context, settings.hapticStyle, settings.hapticAmplitude, it.toInt(), view)
                }
            }
        }
        if (settings.hapticStyle == HapticStyle.CUSTOM || settings.hapticStyle == HapticStyle.SHARP) {
            item {
                SliderSetting(
                    stringResource(R.string.keypress_haptic_intensity_title),
                    subtitle = stringResource(R.string.keypress_haptic_intensity_subtitle),
                    value = settings.hapticAmplitude.toFloat(),
                    range = 1f..255f,
                    display = {
                        context.getString(R.string.keypress_value_percent, it.roundToInt() * 100 / 255)
                    },
                    info = stringResource(R.string.keypress_haptic_intensity_info),
                ) {
                    scope.launch { repository.setHapticAmplitude(it.toInt()) }
                    HapticPlayer.preview(context, settings.hapticStyle, it.toInt(), settings.hapticStrengthMs, view)
                }
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.keypress_long_press_haptics_title),
                stringResource(R.string.keypress_long_press_haptics_subtitle),
                settings.hapticOnLongPress,
                info = stringResource(R.string.keypress_long_press_haptics_info),
            ) { scope.launch { repository.setHapticOnLongPress(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.keypress_long_press_release_title),
                stringResource(R.string.keypress_long_press_release_subtitle),
                settings.hapticOnLongPressRelease,
                info = stringResource(R.string.keypress_long_press_release_info),
            ) { scope.launch { repository.setHapticOnLongPressRelease(it) } }
        }
        // Per-event gates: only meaningful while the master switch above is on,
        // so they fold away when it is off.
        if (settings.hapticFeedback) {
            item {
                ToggleSetting(
                    stringResource(R.string.keypress_vibrate_space_title),
                    stringResource(R.string.keypress_vibrate_space_subtitle),
                    settings.feedback.vibrateOnSpace,
                    info = stringResource(R.string.keypress_vibrate_space_info),
                ) { scope.launch { repository.setVibrateOnSpace(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.keypress_vibrate_delete_swipe_title),
                    stringResource(R.string.keypress_vibrate_delete_swipe_subtitle),
                    settings.feedback.vibrateOnDeleteSwipe,
                    info = stringResource(R.string.keypress_vibrate_delete_swipe_info),
                ) { scope.launch { repository.setVibrateOnDeleteSwipe(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.keypress_vibrate_repeat_title),
                    stringResource(R.string.keypress_vibrate_repeat_subtitle),
                    settings.feedback.vibrateOnRepeat,
                    info = stringResource(R.string.keypress_vibrate_repeat_info),
                ) { scope.launch { repository.setVibrateOnRepeat(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.keypress_dnd_mute_title),
                    stringResource(R.string.keypress_dnd_mute_subtitle),
                    settings.feedback.hapticsRespectDnd,
                    info = stringResource(R.string.keypress_dnd_mute_info),
                ) { scope.launch { repository.setHapticsRespectDnd(it) } }
            }
        }
    }

    KeySoundGroup(repository, settings)

    SettingsGroup(stringResource(R.string.keypress_popup_group_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.keypress_popup_title),
                stringResource(R.string.keypress_popup_subtitle),
                settings.popup.enabled,
                info = stringResource(R.string.keypress_popup_info),
            ) { scope.launch { repository.setKeyPopup(it) } }
        }
        if (settings.popup.enabled) {
            item {
                ToggleSetting(
                    stringResource(R.string.keypress_popup_numeric_title),
                    stringResource(R.string.keypress_popup_numeric_subtitle),
                    settings.popup.inNumericFields,
                    info = stringResource(R.string.keypress_popup_numeric_info),
                ) { scope.launch { repository.setKeyPopupInNumericFields(it) } }
            }
            item {
                SliderSetting(
                    stringResource(R.string.keypress_popup_min_duration_title),
                    subtitle = stringResource(R.string.keypress_popup_min_duration_subtitle),
                    value = settings.popup.minDurationMs.toFloat(),
                    range = 0f..300f,
                    display = { context.getString(R.string.keypress_value_ms, it.toInt()) },
                    info = stringResource(R.string.keypress_popup_min_duration_info),
                ) { scope.launch { repository.setKeyPopupMinDurationMs(it.toInt()) } }
            }
            item {
                SliderSetting(
                    stringResource(R.string.keypress_popup_max_duration_title),
                    subtitle = stringResource(R.string.keypress_popup_max_duration_subtitle),
                    value = settings.popup.maxDurationMs.toFloat(),
                    range = 400f..2000f,
                    display = { context.getString(R.string.keypress_value_ms, it.toInt()) },
                    info = stringResource(R.string.keypress_popup_max_duration_info),
                ) { scope.launch { repository.setKeyPopupMaxDurationMs(it.toInt()) } }
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.keypress_popup_on_key_title),
                stringResource(R.string.keypress_popup_on_key_subtitle),
                settings.popup.onKey,
                info = stringResource(R.string.keypress_popup_on_key_info),
            ) { scope.launch { repository.setKeyPopupOnKey(it) } }
        }
        item {
            SliderSetting(
                stringResource(R.string.keypress_popup_font_size_title),
                subtitle = stringResource(R.string.keypress_popup_font_size_subtitle),
                value = settings.popup.fontScale,
                range = 0.7f..1.6f,
                display = { context.getString(R.string.keypress_value_multiplier, it) },
                info = stringResource(R.string.keypress_popup_font_size_info),
            ) { scope.launch { repository.setPopupFontScale(it) } }
        }
        item {
            SliderSetting(
                stringResource(R.string.keypress_popup_height_title),
                subtitle = stringResource(R.string.keypress_popup_height_subtitle),
                value = settings.popup.heightDp.toFloat(),
                range = 32f..160f,
                display = { context.getString(R.string.keypress_value_dp, it.toInt()) },
                info = stringResource(R.string.keypress_popup_height_info),
            ) { scope.launch { repository.setKeyPopupHeightDp(it.toInt()) } }
        }
    }

    SettingsGroup(stringResource(R.string.keypress_timing_group_title)) {
        item {
            SliderSetting(
                stringResource(R.string.keypress_long_press_delay_title),
                subtitle = stringResource(R.string.keypress_long_press_delay_subtitle),
                value = settings.longPressDelayMs.toFloat(),
                range = 150f..700f,
                display = { context.getString(R.string.keypress_value_ms, it.toInt()) },
                info = stringResource(R.string.keypress_long_press_delay_info),
            ) { scope.launch { repository.setLongPressDelayMs(it.toInt()) } }
        }
        item {
            SliderSetting(
                stringResource(R.string.keypress_key_repeat_title),
                subtitle = stringResource(R.string.keypress_key_repeat_subtitle),
                value = settings.keyRepeatIntervalMs.toFloat(),
                range = 20f..200f,
                display = { context.getString(R.string.keypress_value_ms, it.toInt()) },
                info = stringResource(R.string.keypress_key_repeat_info),
            ) { scope.launch { repository.setKeyRepeatIntervalMs(it.toInt()) } }
        }
        item {
            SliderSetting(
                stringResource(R.string.keypress_caps_lock_title),
                subtitle = stringResource(R.string.keypress_caps_lock_subtitle),
                value = settings.layoutBehavior.shiftCapsLockMs.toFloat(),
                range = ShiftCapsLockMsRange.first.toFloat()..ShiftCapsLockMsRange.last.toFloat(),
                display = { context.getString(R.string.keypress_value_ms, it.toInt()) },
                info = stringResource(R.string.keypress_caps_lock_info),
            ) { scope.launch { repository.setShiftCapsLockMs(it.toInt()) } }
        }
    }

    SettingsGroup(stringResource(R.string.keypress_shortcuts_group_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.keypress_long_press_hints_title),
                stringResource(R.string.keypress_long_press_hints_subtitle),
                settings.longPressHints,
                info = stringResource(R.string.keypress_long_press_hints_info),
            ) { scope.launch { repository.setLongPressHints(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.keypress_all_accents_title),
                stringResource(R.string.keypress_all_accents_subtitle),
                settings.layoutBehavior.showAllPopupKeys,
                info = stringResource(R.string.keypress_all_accents_info),
            ) { scope.launch { repository.setShowAllPopupKeys(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.keypress_symbols_numpad_title),
                stringResource(R.string.keypress_symbols_numpad_subtitle),
                settings.layoutBehavior.symbolsLongPressNumpad,
                info = stringResource(R.string.keypress_symbols_numpad_info),
            ) { scope.launch { repository.setSymbolsLongPressNumpad(it) } }
        }
        item {
            // A44: the $ key's long-press currency glyphs, space-separated. Blank
            // restores the built-in set. Mirrors the layout editor's alternates field.
            var currencyText by remember(settings.layoutBehavior.currencyKeys) {
                mutableStateOf(
                    settings.layoutBehavior.currencyKeys
                        .ifEmpty { DefaultCurrencyKeys }
                        .joinToString(" "),
                )
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.keypress_currency_keys_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    InfoButton(
                        stringResource(R.string.keypress_currency_keys_title),
                        stringResource(R.string.keypress_currency_keys_info),
                    )
                }
                OutlinedTextField(
                    value = currencyText,
                    onValueChange = {
                        currencyText = it
                        scope.launch {
                            repository.setCurrencyKeys(it.trim().split(" ").filter { s -> s.isNotBlank() })
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.keypress_ctrl_raw_title),
                stringResource(R.string.keypress_ctrl_raw_subtitle),
                settings.rawClipboardShortcuts,
                info = stringResource(R.string.keypress_ctrl_raw_info),
            ) { scope.launch { repository.setRawClipboardShortcuts(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.keypress_hold_a_title),
                stringResource(R.string.keypress_hold_a_subtitle),
                settings.longPressLetterActions.selectAll,
                info = stringResource(R.string.keypress_hold_a_info),
            ) { scope.launch { repository.setLongPressASelectAll(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.keypress_hold_c_title),
                stringResource(R.string.keypress_hold_c_subtitle),
                settings.longPressLetterActions.copy,
                info = stringResource(R.string.keypress_hold_c_info),
            ) { scope.launch { repository.setLongPressCCopy(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.keypress_hold_x_title),
                stringResource(R.string.keypress_hold_x_subtitle),
                settings.longPressLetterActions.cut,
                info = stringResource(R.string.keypress_hold_x_info),
            ) { scope.launch { repository.setLongPressXCut(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.keypress_hold_v_title),
                stringResource(R.string.keypress_hold_v_subtitle),
                settings.longPressLetterActions.paste,
                info = stringResource(R.string.keypress_hold_v_info),
            ) { scope.launch { repository.setLongPressVPaste(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.keypress_hold_z_title),
                stringResource(R.string.keypress_hold_z_subtitle),
                settings.longPressLetterActions.undo,
                info = stringResource(R.string.keypress_hold_z_info),
            ) { scope.launch { repository.setLongPressZUndo(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.keypress_hold_y_title),
                stringResource(R.string.keypress_hold_y_subtitle),
                settings.longPressLetterActions.redo,
                info = stringResource(R.string.keypress_hold_y_info),
            ) { scope.launch { repository.setLongPressYRedo(it) } }
        }
    }
}

// ---- appearance ----

@Composable
private fun AppearanceSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onOpenThemes: () -> Unit,
    onOpenFonts: () -> Unit,
    onOpenIcons: () -> Unit,
    onOpenPhotos: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Slider readouts are plain lambdas, so their format strings are resolved
    // here and captured. The format also puts the number through the locale,
    // which is what gives Bengali or Arabic digits.
    val dpFormat = stringResource(R.string.typing_value_dp)
    val spFormat = stringResource(R.string.values_sp)
    val multiplierFormat = stringResource(R.string.keypress_value_multiplier)
    // Turning the toolbar off is guarded — it hides suggestions and every tool.
    var confirmDisableToolbar by remember { mutableStateOf(false) }
    SettingsGroup(stringResource(R.string.appearance_style_section_title)) {
        item {
            val selected = settings.customThemes.find { it.id == settings.keyboardThemeId }
                ?: com.wasimaster.wmkeyboard.core.theme.BuiltInThemes
                    .find { it.id == settings.keyboardThemeId }
            NavRow(
                stringResource(R.string.appearance_themes_title),
                stringResource(R.string.appearance_themes_subtitle),
                value = if (selected == null) {
                    stringResource(CommonR.string.common_default)
                } else {
                    com.wasimaster.wmkeyboard.core.theme.themeName(selected)
                },
                route = "themes",
                onClick = onOpenThemes,
            )
        }
        item {
            NavRow(
                stringResource(R.string.appearance_font_title),
                stringResource(R.string.appearance_font_subtitle),
                value = KeyboardFonts.genericDisplayName(
                    LocalContext.current,
                    settings.keyFontId,
                    settings.customFontName,
                ),
                route = "fonts",
                onClick = onOpenFonts,
            )
        }
        item {
            val active = settings.icons.activePackId
            val changed = settings.icons.overrides.size
            val defaultLabel = stringResource(CommonR.string.common_default)
            NavRow(
                stringResource(R.string.appearance_icons_title),
                stringResource(R.string.appearance_icons_subtitle),
                value = when {
                    active.isNotEmpty() ->
                        IconPackStore.get(LocalContext.current).pack(active)?.name ?: defaultLabel
                    changed > 0 -> pluralStringResource(
                        R.plurals.appearance_icons_changed_count,
                        changed,
                        changed,
                    )
                    else -> defaultLabel
                },
                route = "icons",
                onClick = onOpenIcons,
            )
        }
        item {
            NavRow(
                stringResource(R.string.photo_hub_title),
                stringResource(R.string.photo_hub_subtitle),
                value = stringResource(
                    if (settings.photoBackground.rotateEnabled) {
                        CommonR.string.common_on
                    } else {
                        CommonR.string.common_off
                    },
                ),
                route = PHOTO_HUB_ROUTE,
                onClick = onOpenPhotos,
            )
        }
    }

    SettingsGroup(stringResource(R.string.appearance_keys_section_title)) {
        item {
            SliderSetting(
                stringResource(R.string.appearance_key_corner_radius_title),
                subtitle = stringResource(R.string.appearance_key_corner_radius_subtitle),
                value = settings.keyCornerRadiusDp.toFloat(),
                range = 0f..28f,
                display = { dpFormat.format(it.toInt()) },
                info = stringResource(R.string.appearance_key_corner_radius_info),
            ) { scope.launch { repository.setKeyCornerRadiusDp(it.toInt()) } }
        }
        item {
            SliderSetting(
                stringResource(R.string.appearance_key_label_size_title),
                subtitle = stringResource(R.string.appearance_key_label_size_subtitle),
                value = settings.fontScale,
                range = 0.7f..1.5f,
                display = { multiplierFormat.format(it) },
                info = stringResource(R.string.appearance_key_label_size_info),
            ) { scope.launch { repository.setFontScale(it) } }
        }
        item {
            SliderSetting(
                stringResource(R.string.appearance_key_hint_size_title),
                subtitle = stringResource(R.string.appearance_key_hint_size_subtitle),
                value = settings.layoutBehavior.hintFontScale,
                range = 0.5f..2.0f,
                display = { multiplierFormat.format(it) },
                info = stringResource(R.string.appearance_key_hint_size_info),
            ) { scope.launch { repository.setHintFontScale(it) } }
        }
    }

    SettingsGroup(stringResource(R.string.appearance_toolbar_section_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.appearance_toolbar_show_title),
                stringResource(R.string.appearance_toolbar_show_subtitle),
                settings.toolbarBehavior.enabled,
                info = stringResource(R.string.appearance_toolbar_show_info),
            ) { on ->
                // Enabling is harmless; disabling loses real features, so confirm.
                if (on) scope.launch { repository.setToolbarEnabled(true) }
                else confirmDisableToolbar = true
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.appearance_toolbar_swipe_down_title),
                stringResource(R.string.appearance_toolbar_swipe_down_subtitle),
                settings.toolbarBehavior.swipeDownHide,
                info = stringResource(R.string.appearance_toolbar_swipe_down_info),
            ) { scope.launch { repository.setToolbarSwipeDownHide(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.appearance_toolbar_hardware_only_title),
                stringResource(R.string.appearance_toolbar_hardware_only_subtitle),
                settings.toolbarBehavior.onlyWithHardwareKeyboard,
                info = stringResource(R.string.appearance_toolbar_hardware_only_info),
            ) { scope.launch { repository.setToolbarOnlyWithHardwareKeyboard(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.appearance_toolbar_rtl_title),
                stringResource(R.string.appearance_toolbar_rtl_subtitle),
                settings.toolbarBehavior.reverseForRtl,
                info = stringResource(R.string.appearance_toolbar_rtl_info),
            ) { scope.launch { repository.setReverseToolbarForRtl(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.appearance_toolbar_spread_title),
                stringResource(R.string.appearance_toolbar_spread_subtitle),
                settings.toolbarBehavior.greedy,
                info = stringResource(R.string.appearance_toolbar_spread_info),
            ) { scope.launch { repository.setToolbarGreedy(it) } }
        }
        item {
            SliderSetting(
                stringResource(R.string.appearance_toolbar_height_title),
                subtitle = stringResource(R.string.appearance_toolbar_height_subtitle),
                value = settings.toolbarHeightDp.toFloat(),
                range = 32f..80f,
                display = { dpFormat.format(it.roundToInt()) },
                info = stringResource(R.string.appearance_toolbar_height_info),
            ) { scope.launch { repository.setToolbarHeightDp(it.roundToInt()) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.appearance_toolbar_scroll_title),
                stringResource(R.string.appearance_toolbar_scroll_subtitle),
                settings.toolbarBehavior.scrollable,
                info = stringResource(R.string.appearance_toolbar_scroll_info),
            ) { scope.launch { repository.setToolbarScrollable(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.appearance_toolbar_lock_title),
                stringResource(R.string.appearance_toolbar_lock_subtitle),
                settings.toolbarBehavior.hideWhenLocked,
                info = stringResource(R.string.appearance_toolbar_lock_info),
            ) { scope.launch { repository.setToolbarHideWhenLocked(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.appearance_toolbar_labels_title),
                stringResource(R.string.appearance_toolbar_labels_subtitle),
                settings.toolbarLabels,
                info = stringResource(R.string.appearance_toolbar_labels_info),
            ) { scope.launch { repository.setToolbarLabels(it) } }
        }
        if (settings.toolbarLabels) {
            item {
                SliderSetting(
                    stringResource(R.string.appearance_toolbar_label_size_title),
                    subtitle = stringResource(R.string.appearance_toolbar_label_size_subtitle),
                    value = settings.toolbarLabelSize.toFloat(),
                    range = 7f..14f,
                    display = { spFormat.format(it.roundToInt()) },
                ) { scope.launch { repository.setToolbarLabelSize(it.roundToInt()) } }
            }
        }
        item {
            ResetPinnedToolsSetting(repository, scope)
        }
        item {
            val offLabel = stringResource(CommonR.string.common_off)
            SliderSetting(
                stringResource(R.string.appearance_tool_circle_title),
                subtitle = stringResource(R.string.appearance_tool_circle_subtitle),
                value = settings.toolCircleRadiusDp.toFloat(),
                range = 0f..20f,
                display = { if (it.toInt() == 0) offLabel else dpFormat.format(it.toInt()) },
                info = stringResource(R.string.appearance_tool_circle_info),
            ) { scope.launch { repository.setToolCircleRadiusDp(it.toInt()) } }
        }
        item {
            ChoiceSetting(
                stringResource(R.string.appearance_toolbox_layout_title),
                subtitle = stringResource(R.string.appearance_toolbox_layout_subtitle),
                options = listOf(
                    ToolboxLayout.ICONS to
                        stringResource(R.string.appearance_toolbox_layout_icons_label),
                    ToolboxLayout.PILLS to
                        stringResource(R.string.appearance_toolbox_layout_pills_label),
                ),
                selected = settings.toolbox.layout,
                info = stringResource(R.string.appearance_toolbox_layout_info),
            ) { scope.launch { repository.setToolboxLayout(it) } }
        }
        if (settings.toolbox.layout == ToolboxLayout.ICONS) {
            item {
                val perRow = stringResource(R.string.appearance_slider_per_row_value)
                SliderSetting(
                    stringResource(R.string.appearance_toolbox_columns_title),
                    subtitle = stringResource(R.string.appearance_toolbox_columns_subtitle),
                    value = settings.toolboxColumns.toFloat(),
                    range = 3f..6f,
                    display = { perRow.format(it.roundToInt()) },
                    info = stringResource(R.string.appearance_toolbox_columns_info),
                ) { scope.launch { repository.setToolboxColumns(it.roundToInt()) } }
            }
        } else {
            item {
                val perRow = stringResource(R.string.appearance_slider_per_row_value)
                SliderSetting(
                    stringResource(R.string.appearance_toolbox_pill_columns_title),
                    subtitle = stringResource(R.string.appearance_toolbox_pill_columns_subtitle),
                    value = settings.toolbox.pillColumns.toFloat(),
                    range = 1f..3f,
                    display = { perRow.format(it.roundToInt()) },
                    info = stringResource(R.string.appearance_toolbox_pill_columns_info),
                ) { scope.launch { repository.setToolboxPillColumns(it.roundToInt()) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.appearance_toolbox_pill_filled_title),
                    stringResource(R.string.appearance_toolbox_pill_filled_subtitle),
                    settings.toolbox.pillFilled,
                    info = stringResource(R.string.appearance_toolbox_pill_filled_info),
                ) { scope.launch { repository.setToolboxPillFilled(it) } }
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.appearance_toolbox_paginate_title),
                stringResource(R.string.appearance_toolbox_paginate_subtitle),
                settings.toolbox.paginate,
                info = stringResource(R.string.appearance_toolbox_paginate_info),
            ) { scope.launch { repository.setToolboxPaginate(it) } }
        }
        if (settings.toolbox.paginate) {
            item {
                val perPage = stringResource(R.string.appearance_slider_per_page_value)
                SliderSetting(
                    stringResource(R.string.appearance_toolbox_page_size_title),
                    subtitle = stringResource(R.string.appearance_toolbox_page_size_subtitle),
                    value = settings.toolbox.pageSize.toFloat(),
                    range = ToolboxPageSizeRange.first.toFloat()..ToolboxPageSizeRange.last.toFloat(),
                    display = { perPage.format(it.roundToInt()) },
                    info = stringResource(R.string.appearance_toolbox_page_size_info),
                ) { scope.launch { repository.setToolboxPageSize(it.roundToInt()) } }
            }
        }
    }

    if (confirmDisableToolbar) {
        AlertDialog(
            onDismissRequest = { confirmDisableToolbar = false },
            title = { Text(stringResource(R.string.appearance_toolbar_disable_dialog_title)) },
            text = { Text(stringResource(R.string.appearance_toolbar_disable_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisableToolbar = false
                    scope.launch { repository.setToolbarEnabled(false) }
                }) { Text(stringResource(CommonR.string.common_disable)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisableToolbar = false }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }
}

// ---- layout & size ----

@Composable
private fun LayoutSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    // Slider readouts are plain lambdas, so their format strings are resolved
    // here and captured. The format also puts the number through the locale,
    // which is what gives Bengali or Arabic digits.
    val dpFormat = stringResource(R.string.typing_value_dp)
    val percentFormat = stringResource(R.string.typing_value_percent)
    val multiplierFormat = stringResource(R.string.keypress_value_multiplier)
    SettingsGroup(stringResource(R.string.layout_number_row_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.layout_number_row_title),
                stringResource(R.string.layout_number_row_subtitle),
                settings.numberRow,
                info = stringResource(R.string.layout_number_row_info),
            ) { scope.launch { repository.setNumberRow(it) } }
        }
        if (settings.numberRow) {
            item {
                SliderSetting(
                    stringResource(R.string.layout_number_row_height_title),
                    subtitle = stringResource(R.string.layout_number_row_height_subtitle),
                    value = settings.numberRowHeightDp.toFloat(),
                    range = 32f..100f,
                    display = { dpFormat.format(it.toInt()) },
                    info = stringResource(R.string.layout_number_row_height_info),
                ) { scope.launch { repository.setNumberRowHeightDp(it.toInt()) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.layout_number_row_shift_symbols_title),
                    stringResource(R.string.layout_number_row_shift_symbols_subtitle),
                    settings.layoutBehavior.numberRowShiftSymbols,
                    info = stringResource(R.string.layout_number_row_shift_symbols_info),
                ) { scope.launch { repository.setNumberRowShiftSymbols(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.layout_number_row_in_symbols_title),
                    stringResource(R.string.layout_number_row_in_symbols_subtitle),
                    settings.layoutBehavior.numberRowInSymbols,
                    info = stringResource(R.string.layout_number_row_in_symbols_info),
                ) { scope.launch { repository.setNumberRowInSymbols(it) } }
            }
        }
    }

    SettingsGroup(stringResource(R.string.layout_numerals_title)) {
        item {
            ChoiceSetting(
                stringResource(R.string.layout_numeral_scope_title),
                subtitle = stringResource(R.string.layout_numeral_scope_subtitle),
                info = stringResource(R.string.layout_numeral_scope_info),
                options = NumeralCommitScope.entries.map { it to stringResource(it.labelRes) },
                selected = settings.layoutBehavior.numeralCommitScope,
            ) { scope.launch { repository.setNumeralCommitScope(it) } }
        }
        item {
            CaptionText(stringResource(R.string.layout_numerals_caption))
        }
    }

    SettingsGroup(stringResource(R.string.layout_size_position_title)) {
        item {
            SliderSetting(
                stringResource(R.string.layout_key_height_title),
                subtitle = stringResource(R.string.layout_key_height_subtitle),
                value = settings.keyHeightDp.toFloat(),
                range = 32f..100f,
                display = { dpFormat.format(it.toInt()) },
                info = stringResource(R.string.layout_key_height_info),
            ) { scope.launch { repository.setKeyHeightDp(it.toInt()) } }
        }
        item {
            val followKeys = stringResource(R.string.layout_bottom_row_follow_keys_label)
            SliderSetting(
                stringResource(R.string.layout_bottom_row_height_title),
                subtitle = stringResource(R.string.layout_bottom_row_height_subtitle),
                value = settings.layoutBehavior.bottomRowHeightDp.toFloat(),
                range = 0f..BottomRowHeightRange.last.toFloat(),
                display = { if (it < 1f) followKeys else dpFormat.format(it.toInt()) },
                info = stringResource(R.string.layout_bottom_row_height_info),
            ) { scope.launch { repository.setBottomRowHeightDp(it.toInt()) } }
        }
        item {
            SliderSetting(
                stringResource(R.string.layout_side_padding_title),
                subtitle = stringResource(R.string.layout_side_padding_subtitle),
                value = settings.layoutBehavior.sidePadScale,
                range = SidePadScaleRange.start..SidePadScaleRange.endInclusive,
                display = { percentFormat.format((it * 100).toInt()) },
                info = stringResource(R.string.layout_side_padding_info),
            ) { scope.launch { repository.setSidePadScale(it) } }
        }
        item {
            SliderSetting(
                stringResource(R.string.layout_key_spacing_title),
                subtitle = stringResource(R.string.layout_key_spacing_subtitle),
                value = settings.keyGapScale,
                range = 0f..2f,
                display = { percentFormat.format((it * 100).toInt()) },
                info = stringResource(R.string.layout_key_spacing_info),
            ) { scope.launch { repository.setKeyGapScale(it) } }
        }
        item {
            SliderSetting(
                stringResource(R.string.layout_bottom_padding_title),
                subtitle = stringResource(R.string.layout_bottom_padding_subtitle),
                value = settings.bottomPaddingDp.toFloat(),
                range = 0f..40f,
                display = { dpFormat.format(it.toInt()) },
                info = stringResource(R.string.layout_bottom_padding_info),
            ) { scope.launch { repository.setBottomPaddingDp(it.toInt()) } }
        }
        item {
            SliderSetting(
                stringResource(R.string.layout_keyboard_width_title),
                subtitle = stringResource(R.string.layout_keyboard_width_subtitle),
                value = settings.keyboardWidthPercent.toFloat(),
                range = 50f..100f,
                display = { percentFormat.format(it.toInt()) },
                info = stringResource(R.string.layout_keyboard_width_info),
            ) { scope.launch { repository.setKeyboardWidthPercent(it.toInt()) } }
        }
        if (settings.keyboardWidthPercent < 100) {
            item {
                ChoiceSetting(
                    title = stringResource(R.string.layout_keyboard_position_title),
                    info = stringResource(R.string.layout_keyboard_position_info),
                    options = KeyboardAlignment.entries.map { alignment ->
                        alignment to stringResource(layoutAlignmentLabelRes(alignment))
                    },
                    selected = settings.keyboardAlignment,
                ) { scope.launch { repository.setKeyboardAlignment(it) } }
            }
        }
    }

    var expandedVariant by remember { mutableStateOf<ScreenVariant?>(null) }
    SettingsGroup(stringResource(R.string.layout_per_screen_title)) {
        item {
            CaptionText(stringResource(R.string.layout_per_screen_caption))
        }
        for (variant in ScreenVariant.entries.filter { it.isOverride }) {
            val override = settings.sizingOverrides[variant]
            val values = settings.sizingValuesFor(variant)
            item {
                NavRow(
                    stringResource(variant.labelRes),
                    if (override == null || override.isEmpty) {
                        stringResource(R.string.layout_variant_follows_portrait_label)
                    } else {
                        stringResource(
                            R.string.layout_variant_summary,
                            values.keyHeightDp ?: settings.keyHeightDp,
                            values.keyboardWidthPercent ?: settings.keyboardWidthPercent,
                        )
                    },
                    onClick = {
                        expandedVariant = if (expandedVariant == variant) null else variant
                    },
                )
            }
            if (expandedVariant == variant) {
                item {
                    SliderSetting(
                        stringResource(R.string.layout_keyboard_scale_title),
                        subtitle = stringResource(R.string.layout_keyboard_scale_subtitle),
                        value = values.keyboardScale ?: 1f,
                        range = 0.5f..1.5f,
                        display = { percentFormat.format((it * 100).toInt()) },
                    ) { scope.launch { repository.setVariantKeyboardScale(variant, it) } }
                }
                item {
                    SliderSetting(
                        stringResource(R.string.layout_key_height_title),
                        value = (values.keyHeightDp ?: settings.keyHeightDp).toFloat(),
                        range = 32f..100f,
                        display = { dpFormat.format(it.toInt()) },
                    ) { scope.launch { repository.setVariantKeyHeightDp(variant, it.toInt()) } }
                }
                if (settings.numberRow) {
                    item {
                        SliderSetting(
                            stringResource(R.string.layout_number_row_height_title),
                            value = (values.numberRowHeightDp ?: settings.numberRowHeightDp).toFloat(),
                            range = 32f..100f,
                            display = { dpFormat.format(it.toInt()) },
                        ) {
                            scope.launch {
                                repository.setVariantNumberRowHeightDp(variant, it.toInt())
                            }
                        }
                    }
                }
                item {
                    SliderSetting(
                        stringResource(R.string.layout_bottom_padding_title),
                        value = (values.bottomPaddingDp ?: settings.bottomPaddingDp).toFloat(),
                        range = 0f..40f,
                        display = { dpFormat.format(it.toInt()) },
                    ) { scope.launch { repository.setVariantBottomPaddingDp(variant, it.toInt()) } }
                }
                item {
                    SliderSetting(
                        stringResource(R.string.layout_keyboard_width_title),
                        value = (values.keyboardWidthPercent ?: settings.keyboardWidthPercent).toFloat(),
                        range = 50f..100f,
                        display = { percentFormat.format(it.toInt()) },
                    ) { scope.launch { repository.setVariantWidthPercent(variant, it.toInt()) } }
                }
                item {
                    SliderSetting(
                        stringResource(R.string.layout_font_size_title),
                        value = values.fontScale ?: settings.fontScale,
                        range = 0.7f..1.5f,
                        display = { multiplierFormat.format(it) },
                    ) { scope.launch { repository.setVariantFontScale(variant, it) } }
                }
                if ((values.keyboardWidthPercent ?: settings.keyboardWidthPercent) < 100) {
                    item {
                        ChoiceSetting(
                            title = stringResource(R.string.layout_keyboard_position_title),
                            options = KeyboardAlignment.entries.map { alignment ->
                                alignment to stringResource(layoutAlignmentLabelRes(alignment))
                            },
                            selected = values.keyboardAlignment ?: settings.keyboardAlignment,
                        ) { scope.launch { repository.setVariantAlignment(variant, it) } }
                    }
                }
                if (override != null && !override.isEmpty) {
                    item {
                        NavRow(
                            stringResource(R.string.layout_follow_portrait_title),
                            stringResource(
                                R.string.layout_follow_portrait_subtitle,
                                stringResource(variant.labelRes),
                            ),
                            onClick = { scope.launch { repository.clearVariantSizing(variant) } },
                        )
                    }
                }
            }
        }
    }

    SettingsGroup(stringResource(R.string.layout_one_handed_group_title)) {
        item {
            ChoiceSetting(
                title = stringResource(R.string.layout_one_handed_title),
                subtitle = stringResource(R.string.layout_one_handed_subtitle),
                options = OneHandedMode.entries.map { mode ->
                    mode to stringResource(layoutOneHandedModeLabelRes(mode))
                },
                selected = settings.oneHandedMode,
            ) { scope.launch { repository.setOneHandedMode(it) } }
        }
        item {
            CaptionText(stringResource(R.string.layout_one_handed_caption))
        }
        val orientations = listOf(
            false to R.string.layout_orientation_portrait_label,
            true to R.string.layout_orientation_landscape_label,
        )
        for ((landscape, orientationRes) in orientations) {
            val profile = settings.oneHanded.forLandscape(landscape)
            item {
                val orientationLabel = stringResource(orientationRes)
                SliderSetting(
                    stringResource(R.string.layout_one_handed_width_title, orientationLabel),
                    subtitle = stringResource(
                        R.string.layout_one_handed_width_subtitle,
                        orientationLabel,
                    ),
                    value = profile.widthPercent.toFloat(),
                    range = SettingsRepository.ONE_HANDED_WIDTH_MIN.toFloat()..
                        SettingsRepository.ONE_HANDED_WIDTH_MAX.toFloat(),
                    display = { percentFormat.format(it.toInt()) },
                    info = stringResource(R.string.layout_one_handed_width_info),
                ) { scope.launch { repository.setOneHandedWidthPercent(landscape, it.toInt()) } }
            }
            item {
                SliderSetting(
                    stringResource(
                        R.string.layout_one_handed_height_title,
                        stringResource(orientationRes),
                    ),
                    subtitle = stringResource(R.string.layout_one_handed_height_subtitle),
                    value = profile.heightScale.toFloat(),
                    range = SettingsRepository.ONE_HANDED_HEIGHT_SCALE_MIN.toFloat()..
                        SettingsRepository.ONE_HANDED_HEIGHT_SCALE_MAX.toFloat(),
                    display = { percentFormat.format(it.toInt()) },
                    info = stringResource(R.string.layout_one_handed_height_info),
                ) { scope.launch { repository.setOneHandedHeightScale(landscape, it.toInt()) } }
            }
            item {
                val orientationLabel = stringResource(orientationRes)
                ChoiceSetting(
                    title = stringResource(
                        R.string.layout_one_handed_side_title,
                        orientationLabel,
                    ),
                    subtitle = stringResource(
                        R.string.layout_one_handed_side_subtitle,
                        orientationLabel,
                    ),
                    options = OneHandedSide.entries.map { side ->
                        side to stringResource(layoutOneHandedSideLabelRes(side))
                    },
                    selected = profile.side,
                ) { scope.launch { repository.setOneHandedSide(landscape, it) } }
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.layout_split_title),
                stringResource(R.string.layout_split_subtitle),
                settings.splitKeyboard,
                info = stringResource(R.string.layout_split_info),
            ) { scope.launch { repository.setSplitKeyboard(it) } }
        }
        if (settings.splitKeyboard) {
            item {
                SliderSetting(
                    stringResource(R.string.layout_split_gap_title),
                    subtitle = stringResource(R.string.layout_split_gap_subtitle),
                    value = settings.splitGapPercent.toFloat(),
                    range = 5f..40f,
                    display = { percentFormat.format(it.toInt()) },
                    info = stringResource(R.string.layout_split_gap_info),
                ) { scope.launch { repository.setSplitGapPercent(it.toInt()) } }
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.layout_floating_title),
                stringResource(R.string.layout_floating_subtitle),
                settings.floatingKeyboard,
                info = stringResource(R.string.layout_floating_info),
            ) { scope.launch { repository.setFloatingKeyboard(it) } }
        }
        if (settings.floatingKeyboard) {
            item {
                SliderSetting(
                    stringResource(R.string.layout_floating_width_title),
                    subtitle = stringResource(R.string.layout_floating_width_subtitle),
                    value = settings.floatingWidthDp.toFloat(),
                    range = 240f..500f,
                    display = { dpFormat.format(it.toInt()) },
                    info = stringResource(R.string.layout_floating_width_info),
                ) { scope.launch { repository.setFloatingWidthDp(it.toInt()) } }
            }
        }
    }

    SettingsGroup(stringResource(R.string.layout_bottom_row_keys_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.layout_comma_emoji_title),
                stringResource(R.string.layout_comma_emoji_subtitle),
                settings.commaAsEmoji,
                info = stringResource(R.string.layout_comma_emoji_info),
            ) { scope.launch { repository.setCommaAsEmoji(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.layout_globe_emoji_title),
                stringResource(R.string.layout_globe_emoji_subtitle),
                settings.globeAsEmoji,
                info = stringResource(R.string.layout_globe_emoji_info),
            ) { scope.launch { repository.setGlobeAsEmoji(it) } }
        }
    }
}

/** The name drawn on the segmented button for each [KeyboardAlignment]. */
@StringRes
private fun layoutAlignmentLabelRes(alignment: KeyboardAlignment): Int = when (alignment) {
    KeyboardAlignment.LEFT -> R.string.layout_edge_left_label
    KeyboardAlignment.CENTER -> R.string.layout_edge_centre_label
    KeyboardAlignment.RIGHT -> R.string.layout_edge_right_label
}

/** The name drawn on the segmented button for each [OneHandedMode]. */
@StringRes
private fun layoutOneHandedModeLabelRes(mode: OneHandedMode): Int = when (mode) {
    OneHandedMode.OFF -> CommonR.string.common_off
    OneHandedMode.LEFT -> R.string.layout_edge_left_label
    OneHandedMode.RIGHT -> R.string.layout_edge_right_label
}

/** The name drawn on the segmented button for each [OneHandedSide]. */
@StringRes
private fun layoutOneHandedSideLabelRes(side: OneHandedSide): Int = when (side) {
    OneHandedSide.LEFT -> R.string.layout_edge_left_label
    OneHandedSide.RIGHT -> R.string.layout_edge_right_label
}

// ---- languages ----

@Composable
private fun LanguageSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    CaptionText(stringResource(R.string.langemoji_lang_intro_body))
    // "Your languages" is the enabled set (deduped, in switch order); each opens
    // its detail. Adding one is a search over the whole registry.
    SettingsGroup(stringResource(R.string.langemoji_lang_your_languages_title)) {
        for (language in settings.enabledLanguages) {
            item {
                val names = settings.enabledLayoutIds
                    .filter { resolveLayout(settings.customLayouts, it).language().id == language.id }
                    .joinToString { resolveLayout(settings.customLayouts, it).name }
                NavRow(
                    language.displayName,
                    subtitle = names.ifBlank { null },
                    route = "language/${language.id}",
                ) { onNavigate("language/${language.id}") }
            }
        }
        item {
            NavRow(
                stringResource(R.string.langemoji_lang_add_title),
                subtitle = pluralStringResource(
                    R.plurals.langemoji_lang_add_subtitle,
                    LanguageRegistry.all.size,
                    LanguageRegistry.all.size,
                ),
                route = "add_language",
            ) { onNavigate("add_language") }
        }
    }
    // A short device-derived shortlist, so the common case never has to go
    // through the full registry. The reasoning lives in LanguageSuggestions.
    val suggested = rememberSuggestedLanguages(settings, limit = LANGUAGE_SCREEN_SUGGESTIONS)
    if (suggested.isNotEmpty()) {
        SettingsGroup(stringResource(R.string.langemoji_lang_suggested_title)) {
            for (suggestion in suggested) {
                item {
                    NavRow(
                        suggestion.language.displayName,
                        subtitle = suggestionReasonLabel(suggestion),
                    ) {
                        addLanguage(scope, repository, settings, suggestion.language)
                        onNavigate("language/${suggestion.language.id}")
                    }
                }
            }
            item {
                CaptionText(stringResource(R.string.langemoji_lang_suggested_source_body))
            }
        }
    }
    // Reorder the switch ring (spacebar swipe / 🌐 cycle) across every enabled
    // layout, not just languages, so AZERTY and QWERTY keep distinct slots.
    if (settings.enabledLayoutIds.size > 1) {
        SettingsGroup {
            item {
                ReorderSetting(
                    stringResource(R.string.langemoji_lang_switch_order_title),
                    stringResource(R.string.langemoji_lang_switch_order_dialog_title),
                    settings.enabledLayoutIds,
                    label = { resolveLayout(settings.customLayouts, it).name },
                    onReordered = { scope.launch { repository.setEnabledLayoutIds(it) } },
                )
            }
        }
    }
    // Custom layouts get their own group after the languages: they are the
    // user's own grids (edited on the Key layouts screen), not a language to add.
    // Only the user's own grids. An override of a shipped layout — built-in or
    // JSON asset — is an edit of that layout, not a layout of their own, and
    // listing it here would show the same name twice: once as the language's
    // layout above, once as if they had made it.
    val customs = settings.customLayouts
        .filter {
            com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts.byId(it.id) == null &&
                com.wasimaster.wmkeyboard.core.layout.AssetLayouts.byId(it.id) == null
        }
        .sortedBy { it.name.lowercase() }
    SettingsGroup(stringResource(R.string.langemoji_lang_your_layouts_title)) {
        for (layout in customs) {
            item {
                ToggleSetting(
                    layout.name,
                    stringResource(
                        R.string.langemoji_lang_custom_layout_subtitle,
                        baseModeTitle(layout),
                    ),
                    layout.id in settings.enabledLayoutIds,
                ) { enable ->
                    scope.launch {
                        val next =
                            if (enable) settings.enabledLayoutIds + layout.id
                            else settings.enabledLayoutIds - layout.id
                        if (next.isNotEmpty()) repository.setEnabledLayoutIds(next.distinct())
                    }
                }
            }
        }
        item {
            NavRow(
                stringResource(R.string.langemoji_lang_keymaps_title),
                subtitle = if (customs.isEmpty()) {
                    stringResource(R.string.langemoji_lang_keymaps_empty_subtitle)
                } else {
                    stringResource(R.string.langemoji_lang_keymaps_subtitle)
                },
                route = "keymaps",
            ) { onNavigate("keymaps") }
        }
    }
    SettingsGroup(stringResource(R.string.langemoji_lang_per_app_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.langemoji_lang_per_app_toggle_title),
                stringResource(R.string.langemoji_lang_per_app_toggle_subtitle),
                settings.perAppLanguage.enabled,
                info = stringResource(R.string.langemoji_lang_per_app_toggle_info),
            ) { scope.launch { repository.setRememberLayoutPerApp(it) } }
        }
    }
    SettingsGroup(stringResource(R.string.langemoji_lang_system_switcher_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.langemoji_lang_os_switcher_title),
                stringResource(R.string.langemoji_lang_os_switcher_subtitle),
                settings.osLanguageSwitcher,
                info = stringResource(R.string.langemoji_lang_os_switcher_info),
            ) { scope.launch { repository.setOsLanguageSwitcher(it) } }
        }
        if (settings.osLanguageSwitcher) {
            item {
                ToggleSetting(
                    stringResource(R.string.langemoji_lang_app_name_first_title),
                    stringResource(R.string.langemoji_lang_app_name_first_subtitle),
                    settings.subtypeAppNameFirst,
                    info = stringResource(R.string.langemoji_lang_app_name_first_info),
                ) { scope.launch { repository.setSubtypeAppNameFirst(it) } }
            }
            item {
                NavRow(
                    stringResource(R.string.langemoji_lang_subtype_enabler_title),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        stringResource(R.string.langemoji_lang_subtype_enabler_subtitle)
                    } else {
                        stringResource(R.string.langemoji_lang_subtype_enabler_legacy_subtitle)
                    },
                ) { openSubtypeEnabler(context) }
            }
        }
    }
    // Conjunct-aware backspace used to live here as one switch across every
    // cluster-forming script at once. It is per language now, on each language's
    // own screen, next to that language's other options — see
    // [ConjunctBackspaceSetting].
}

// ---- emoji ----

@Composable
private fun EmojiSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // The slider readout is a plain lambda, so its format string is resolved
    // here and captured. The format also puts the number through the locale,
    // which is what gives Bengali or Arabic digits.
    val numberFormat = stringResource(R.string.values_number)
    // Examples in the user's own languages: "type জন্মদিন" only reads as proof
    // the feature works to someone who reads Bengali.
    val languageIds = settings.enabledLanguages.map { it.id }
    val birthdayWord = EmojiSearchExamples.one(EmojiSearchExamples.birthday, languageIds)
    SettingsGroup(stringResource(R.string.langemoji_emoji_access_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.langemoji_emoji_toolbar_title),
                stringResource(R.string.langemoji_emoji_toolbar_subtitle),
                settings.emojiToolbar,
                info = stringResource(R.string.langemoji_emoji_toolbar_info),
            ) { scope.launch { repository.setEmojiToolbar(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.langemoji_emoji_full_bleed_title),
                stringResource(R.string.langemoji_emoji_full_bleed_subtitle),
                settings.emojiFullBleed,
                info = stringResource(R.string.langemoji_emoji_full_bleed_info),
            ) { scope.launch { repository.setEmojiFullBleed(it) } }
        }
    }
    SettingsGroup(stringResource(R.string.langemoji_emoji_suggestions_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.langemoji_emoji_prediction_title),
                stringResource(R.string.langemoji_emoji_prediction_subtitle),
                settings.emojiPrediction,
                info = stringResource(R.string.langemoji_emoji_prediction_info, birthdayWord),
            ) { scope.launch { repository.setEmojiPrediction(it) } }
        }
        if (settings.emojiPrediction) {
            item {
                ChoiceSetting(
                    title = stringResource(R.string.langemoji_emoji_insert_mode_title),
                    subtitle = stringResource(R.string.langemoji_emoji_insert_mode_subtitle),
                    info = stringResource(R.string.langemoji_emoji_insert_mode_info),
                    options = listOf(
                        EmojiInsertMode.REPLACE to
                            stringResource(R.string.langemoji_emoji_insert_replace_label),
                        EmojiInsertMode.APPEND to
                            stringResource(R.string.langemoji_emoji_insert_append_label),
                    ),
                    selected = settings.emojiInsertMode,
                ) { scope.launch { repository.setEmojiInsertMode(it) } }
            }
        }
    }
    SettingsGroup(stringResource(R.string.langemoji_emoji_skin_tone_group_title)) {
        item {
            ChoiceSetting(
                title = stringResource(R.string.langemoji_emoji_skin_tone_title),
                subtitle = stringResource(R.string.langemoji_emoji_skin_tone_subtitle),
                info = stringResource(R.string.langemoji_emoji_skin_tone_info),
                options = listOf(
                    EmojiSkinTone.NONE to "✋",
                    EmojiSkinTone.LIGHT to "✋🏻",
                    EmojiSkinTone.MEDIUM_LIGHT to "✋🏼",
                    EmojiSkinTone.MEDIUM to "✋🏽",
                    EmojiSkinTone.MEDIUM_DARK to "✋🏾",
                    EmojiSkinTone.DARK to "✋🏿",
                ),
                selected = settings.emoji.defaultSkinTone,
            ) { scope.launch { repository.setEmojiDefaultSkinTone(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.langemoji_emoji_tone_override_title),
                stringResource(R.string.langemoji_emoji_tone_override_subtitle),
                settings.emoji.toneOverrideByLastUsed,
                info = stringResource(R.string.langemoji_emoji_tone_override_info),
            ) { scope.launch { repository.setEmojiToneOverrideByLastUsed(it) } }
        }
    }
    SettingsGroup(stringResource(R.string.langemoji_emoji_panel_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.langemoji_emoji_close_after_insert_title),
                stringResource(R.string.langemoji_emoji_close_after_insert_subtitle),
                settings.emoji.closeAfterInsert,
                info = stringResource(R.string.langemoji_emoji_close_after_insert_info),
            ) { scope.launch { repository.setEmojiCloseAfterInsert(it) } }
        }
        item {
            ChoiceSetting(
                title = stringResource(R.string.langemoji_emoji_tab_mode_title),
                subtitle = stringResource(R.string.langemoji_emoji_tab_mode_subtitle),
                info = stringResource(R.string.langemoji_emoji_tab_mode_info),
                options = listOf(
                    EmojiTabMode.RECENTS to stringResource(R.string.langemoji_emoji_recent_label),
                    EmojiTabMode.MOST_USED to
                        stringResource(R.string.langemoji_emoji_most_used_label),
                ),
                selected = settings.emojiTabMode,
            ) { scope.launch { repository.setEmojiTabMode(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.langemoji_emoji_clear_recents_title),
                stringResource(R.string.langemoji_emoji_clear_recents_subtitle),
                settings.emojiClearRecentsButton,
                info = stringResource(R.string.langemoji_emoji_clear_recents_info),
            ) { scope.launch { repository.setEmojiClearRecentsButton(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.langemoji_emoji_kaomoji_title),
                stringResource(R.string.langemoji_emoji_kaomoji_subtitle),
                settings.emoji.kaomojiTabs,
                info = stringResource(R.string.langemoji_emoji_kaomoji_info),
            ) { scope.launch { repository.setEmojiKaomojiTabs(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.langemoji_emoji_long_press_name_title),
                stringResource(R.string.langemoji_emoji_long_press_name_subtitle),
                settings.emojiLongPressName,
                info = stringResource(R.string.langemoji_emoji_long_press_name_info),
            ) { scope.launch { repository.setEmojiLongPressName(it) } }
        }
        item {
            NavRow(
                stringResource(R.string.langemoji_emoji_keywords_title),
                stringResource(R.string.langemoji_emoji_keywords_subtitle),
                route = "emojikeywords",
            ) { onNavigate("emojikeywords") }
        }
    }
    SettingsGroup(stringResource(R.string.langemoji_emoji_row_title)) {
        item {
            ChoiceSetting(
                title = stringResource(R.string.langemoji_emoji_row_title),
                subtitle = stringResource(R.string.langemoji_emoji_bar_mode_subtitle),
                info = stringResource(R.string.langemoji_emoji_bar_mode_info),
                options = listOf(
                    EmojiBarMode.OFF to stringResource(CommonR.string.common_off),
                    EmojiBarMode.BUTTON to
                        stringResource(R.string.langemoji_emoji_bar_button_label),
                    EmojiBarMode.ALWAYS to
                        stringResource(R.string.langemoji_emoji_bar_always_label),
                ),
                selected = settings.emojiBarMode,
            ) { scope.launch { repository.setEmojiBarMode(it) } }
        }
        if (settings.emojiBarMode != EmojiBarMode.OFF) {
            item {
                ChoiceSetting(
                    title = stringResource(R.string.langemoji_emoji_bar_content_title),
                    subtitle = stringResource(R.string.langemoji_emoji_bar_content_subtitle),
                    options = listOf(
                        EmojiBarContent.MOST_USED to
                            stringResource(R.string.langemoji_emoji_most_used_label),
                        EmojiBarContent.RECENTS to
                            stringResource(R.string.langemoji_emoji_recent_label),
                        EmojiBarContent.FAVOURITES to
                            stringResource(R.string.langemoji_emoji_favourites_label),
                    ),
                    selected = settings.emojiBarContent,
                ) { scope.launch { repository.setEmojiBarContent(it) } }
            }
            item {
                SliderSetting(
                    title = stringResource(R.string.langemoji_emoji_bar_count_title),
                    subtitle = stringResource(R.string.langemoji_emoji_bar_count_subtitle),
                    value = settings.emoji.barCount.toFloat(),
                    range = EmojiBarCountRange.first.toFloat()..EmojiBarCountRange.last.toFloat(),
                    display = { numberFormat.format(it.roundToInt()) },
                    info = stringResource(R.string.langemoji_emoji_bar_count_info),
                ) { scope.launch { repository.setEmojiBarCount(it.roundToInt()) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.langemoji_emoji_bar_scroll_title),
                    stringResource(R.string.langemoji_emoji_bar_scroll_subtitle),
                    settings.emoji.barScrollable,
                    info = stringResource(R.string.langemoji_emoji_bar_scroll_info),
                ) { scope.launch { repository.setEmojiBarScrollable(it) } }
            }
        }
    }
    if (settings.emojiBarMode == EmojiBarMode.ALWAYS) {
        CaptionText(stringResource(R.string.langemoji_emoji_row_position_body))
    }
    SettingsGroup(stringResource(R.string.langemoji_emoji_style_title)) {
        item {
            val context = LocalContext.current
            // Bumped after an import so the preview re-resolves the (same-named) file.
            var fontRefresh by remember { mutableIntStateOf(0) }
            ChoiceSetting(
                title = stringResource(R.string.langemoji_emoji_font_title),
                subtitle = stringResource(R.string.langemoji_emoji_font_subtitle),
                info = stringResource(R.string.langemoji_emoji_font_info),
                // Where the Emoji font addon's Use button lands.
                highlightKey = R.string.langemoji_emoji_font_title,
                options = listOf(
                    EmojiFontChoice.SYSTEM to
                        stringResource(R.string.langemoji_emoji_font_system_label),
                    EmojiFontChoice.NOTO to
                        stringResource(R.string.langemoji_emoji_font_noto_label),
                    EmojiFontChoice.INSTALLED to
                        stringResource(R.string.langemoji_emoji_font_installed_label),
                    EmojiFontChoice.CUSTOM to stringResource(CommonR.string.common_custom),
                ),
                selected = settings.emojiFont,
            ) { scope.launch { repository.setEmojiFont(it) } }
            val previewFamily = remember(
                settings.emojiFont,
                settings.emojiFontInstalled.installedId,
                fontRefresh,
            ) {
                KeyboardFonts.emojiFamily(
                    context,
                    settings.emojiFont,
                    settings.emojiFontInstalled.installedId,
                )
            }
            // Shaped exactly as the keyboard shapes them, so the preview shows
            // what the panel will show — ❤️ in particular is the one that
            // silently comes from the system font in a font without a
            // variation-selector table (see EmojiFontShaping).
            // Read off the main thread, so the row draws unshaped for a frame
            // rather than stalling the screen on a megabyte of font tables.
            val previewShaper by produceState(
                EmojiFontShaping.Identity,
                settings.emojiFont,
                settings.emojiFontInstalled.installedId,
                fontRefresh,
            ) {
                val file = KeyboardFonts.emojiFontFile(
                    context,
                    settings.emojiFont,
                    settings.emojiFontInstalled.installedId,
                )
                withContext(Dispatchers.Default) { EmojiFontShaping.warm(file) }
                value = EmojiFontShaping.forFontFile(file)
            }
            // One Text per emoji: emoji fonts often have no space glyph, so drawing
            // them as a single spaced string makes the glyphs overlap.
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                for (emoji in listOf("😀", "😂", "🥰", "😎", "🤔", "👍", "❤️", "🎉")) {
                    val spelling = previewShaper.spelling(emoji)
                    Text(
                        spelling.text,
                        fontSize = 24.sp,
                        fontFamily = if (spelling.systemFont) null else previewFamily,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            if (settings.emojiFont == EmojiFontChoice.INSTALLED) {
                InstalledEmojiFontList(repository, settings)
            }
            if (settings.emojiFont == EmojiFontChoice.CUSTOM) {
                val importEmojiFont = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            importFontFile(context, uri, KeyboardFonts.customEmojiFontFile(context))
                        }
                        fontRefresh++
                    }
                }
                val imported = KeyboardFonts.customEmojiFontFile(context).exists()
                if (!imported) {
                    Text(
                        stringResource(R.string.langemoji_emoji_font_missing_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                val importLabel = if (imported) {
                    stringResource(R.string.langemoji_emoji_font_replace_action)
                } else {
                    stringResource(R.string.langemoji_emoji_font_import_action)
                }
                OutlinedButton(
                    onClick = { importEmojiFont.launch(FONT_MIME_TYPES) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text(importLabel) }
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            // The phone is always the one to blame here: an emoji the chosen
            // font is missing is drawn in the phone's own emoji font instead,
            // so the only emoji that stay blank are the ones neither has.
            val ownFont = settings.emojiFont != EmojiFontChoice.SYSTEM
            val hideInfo = stringResource(R.string.langemoji_emoji_hide_unrenderable_info)
            val ownFontInfo =
                stringResource(R.string.langemoji_emoji_hide_unrenderable_own_font_info)
            ToggleSetting(
                stringResource(R.string.langemoji_emoji_hide_unrenderable_title),
                stringResource(R.string.langemoji_emoji_hide_unrenderable_subtitle),
                settings.emoji.hideUnrenderable,
                info = if (ownFont) "$hideInfo\n\n$ownFontInfo" else hideInfo,
            ) { scope.launch { repository.setHideUnrenderableEmoji(it) } }
        }
    }
    CaptionText(stringResource(R.string.langemoji_emoji_tip_body))
}

/**
 * The emoji faces in the font library, with the one in use ticked.
 *
 * Emoji fonts live in the same [FontStore] as the text faces — same files, same
 * lifecycle — but are listed apart, since drawing key labels in a colour emoji
 * font is not a choice anyone makes on purpose.
 */
@Composable
private fun InstalledEmojiFontList(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val store = remember { FontStore.get(context) }
    val revision by store.revision.collectAsStateWithLifecycle()
    val fonts = remember(revision) { store.emojiFonts() }

    if (fonts.isEmpty()) {
        CaptionText(stringResource(R.string.langemoji_emoji_fonts_empty))
        return
    }
    for (font in fonts) {
        val selected = settings.emojiFontInstalled.installedId == font.id
        WmRow(
            title = font.name,
            supporting = font.author.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription =
                                stringResource(R.string.langemoji_emoji_font_selected_desc),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            repository.forgetInstalledEmojiFont(font.id)
                            withContext(Dispatchers.IO) { store.delete(font.id) }
                        }
                    }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(
                                R.string.langemoji_emoji_font_delete_desc,
                                font.name,
                            ),
                        )
                    }
                }
            },
            onClick = { scope.launch { repository.setInstalledEmojiFont(font.id) } },
        )
    }
}

// ---- personal dictionary ----

/**
 * The learned-words file, edited directly from the settings app. Every
 * change bumps the DataStore lexicon version so the IME (which holds its
 * own in-memory copy) reloads from disk instead of clobbering the edit.
 */
@Composable
private fun DictionarySettings(repository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val file = remember { java.io.File(context.filesDir, "learning/user_lexicon.json") }
    // UserLexicon's constructor reads and JSON-parses the whole learned-words
    // file, so it (and every save) runs on Dispatchers.IO, never in composition
    // or on a click handler. The list draws empty for a moment then fills in.
    var lexicon by remember { mutableStateOf<UserLexicon?>(null) }
    var words by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val lex = withContext(Dispatchers.IO) { UserLexicon(file) }
        words = lex.allWords().sortedByDescending { it.second }
        lexicon = lex
    }

    fun persist(mutate: (UserLexicon) -> Unit) {
        val lex = lexicon ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                mutate(lex)
                lex.save()
            }
            words = lex.allWords().sortedByDescending { it.second }
            repository.bumpLexiconVersion()
        }
    }

    Text(
        stringResource(R.string.backup_dictionary_info),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Button(
        onClick = { showAdd = true },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text(stringResource(R.string.backup_add_word_action)) }
    Spacer(Modifier.height(12.dp))
    if (words.isEmpty()) {
        CaptionText(stringResource(R.string.backup_dictionary_empty))
    }
    SettingsGroup {
        for ((word, count) in words) {
            item {
                WmRow(
                    title = word,
                    subtitle = if (count >= 200) {
                        stringResource(R.string.backup_dictionary_added_subtitle)
                    } else {
                        pluralStringResource(R.plurals.backup_dictionary_seen_count, count, count)
                    },
                    trailing = {
                        IconButton(onClick = { persist { it.forget(word) } }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.backup_delete_word_desc, word),
                            )
                        }
                    },
                )
            }
        }
    }

    if (showAdd) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.backup_add_word_title)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.backup_word_field_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        persist { it.addWord(input.trim()) }
                        showAdd = false
                    },
                ) { Text(stringResource(CommonR.string.common_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }
}

// ---- suggestion blacklist ----

/**
 * The never-suggest word list, stored in settings. A blacklisted word is kept
 * out of the suggestion strip and never used as an autocorrect target, but can
 * still be typed and committed normally. Matched case-insensitively.
 */
@Composable
private fun BlacklistSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val words = remember(settings.suggestionBlacklist) {
        settings.suggestionBlacklist.sorted()
    }
    var showAdd by remember { mutableStateOf(false) }

    Text(
        stringResource(R.string.backup_blacklist_info),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Button(
        onClick = { showAdd = true },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text(stringResource(R.string.backup_add_word_action)) }
    Spacer(Modifier.height(12.dp))
    if (words.isEmpty()) {
        CaptionText(stringResource(R.string.backup_blacklist_empty))
    }
    SettingsGroup {
        for (word in words) {
            item {
                WmRow(
                    title = word,
                    trailing = {
                        IconButton(onClick = {
                            scope.launch { repository.removeSuggestionBlacklistWord(word) }
                        }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.backup_delete_word_desc, word),
                            )
                        }
                    },
                )
            }
        }
    }

    if (showAdd) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.backup_add_word_title)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.backup_word_field_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        scope.launch { repository.addSuggestionBlacklistWord(input) }
                        showAdd = false
                    },
                ) { Text(stringResource(CommonR.string.common_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }
}

// ---- backup ----

/** Human name for a bundle section, used in toggles and the import dialog. */
@StringRes
internal fun sectionLabelRes(section: ConfigBackup.Section): Int = when (section) {
    ConfigBackup.Section.SETTINGS -> R.string.backup_section_settings_label
    ConfigBackup.Section.THEMES -> R.string.backup_section_themes_label
    ConfigBackup.Section.DICTIONARY -> R.string.backup_section_dictionary_label
    ConfigBackup.Section.CLIPBOARD -> R.string.backup_section_clipboard_label
    ConfigBackup.Section.SNIPPETS -> R.string.backup_section_snippets_label
    ConfigBackup.Section.STICKERS -> R.string.backup_section_stickers_label
    ConfigBackup.Section.ICONS -> R.string.backup_section_icons_label
    ConfigBackup.Section.WORDLISTS -> R.string.backup_section_wordlists_label
    ConfigBackup.Section.ADDONS -> R.string.backup_section_addons_label
}

internal fun sectionLabel(context: Context, section: ConfigBackup.Section): String =
    context.getString(sectionLabelRes(section))

/**
 * The same name for the middle of a sentence ("Restored themes, snippets.").
 * A translation cannot be lowercased in code, so each name carries its own
 * lower-case value.
 */
internal fun sectionLabelLowercase(context: Context, section: ConfigBackup.Section): String =
    context.getString(
        when (section) {
            ConfigBackup.Section.SETTINGS -> R.string.backup_section_settings_label_lowercase
            ConfigBackup.Section.THEMES -> R.string.backup_section_themes_label_lowercase
            ConfigBackup.Section.DICTIONARY -> R.string.backup_section_dictionary_label_lowercase
            ConfigBackup.Section.CLIPBOARD -> R.string.backup_section_clipboard_label_lowercase
            ConfigBackup.Section.SNIPPETS -> R.string.backup_section_snippets_label_lowercase
            ConfigBackup.Section.STICKERS -> R.string.backup_section_stickers_label_lowercase
            ConfigBackup.Section.ICONS -> R.string.backup_section_icons_label_lowercase
            ConfigBackup.Section.WORDLISTS -> R.string.backup_section_wordlists_label_lowercase
            ConfigBackup.Section.ADDONS -> R.string.backup_section_addons_label_lowercase
        },
    )

@PluralsRes
private fun sectionCountPlural(section: ConfigBackup.Section): Int = when (section) {
    ConfigBackup.Section.SETTINGS -> R.plurals.backup_section_settings_count
    ConfigBackup.Section.THEMES -> R.plurals.backup_section_themes_count
    ConfigBackup.Section.DICTIONARY -> R.plurals.backup_section_dictionary_count
    ConfigBackup.Section.CLIPBOARD -> R.plurals.backup_section_clipboard_count
    ConfigBackup.Section.SNIPPETS -> R.plurals.backup_section_snippets_count
    ConfigBackup.Section.STICKERS -> R.plurals.backup_section_stickers_count
    ConfigBackup.Section.ICONS -> R.plurals.backup_section_icons_count
    ConfigBackup.Section.WORDLISTS -> R.plurals.backup_section_wordlists_count
    ConfigBackup.Section.ADDONS -> R.plurals.backup_section_addons_count
}

/** "3 themes", "1 snippet": the count line shown per section on import. */
internal fun sectionSummary(context: Context, section: ConfigBackup.Section, count: Int): String =
    context.resources.getQuantityString(sectionCountPlural(section), count, count)

/** A file picked for import, once we know which of the two formats it is. */
private sealed interface PendingImport {
    val text: String
    data class Config(override val text: String) : PendingImport
    data class Legacy(override val text: String) : PendingImport
}

@Composable
private fun BackupSettings(repository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // What the export file will contain. Configuration-like parts default on;
    // the personal ones (learned words) and the private, short-lived one
    // (clipboard) default off, so a backup shared or synced by habit doesn't
    // carry them without a deliberate opt-in.
    var includeSettings by remember { mutableStateOf(true) }
    var includeThemes by remember { mutableStateOf(true) }
    var includeSnippets by remember { mutableStateOf(true) }
    // Off by default only because the images make the file large, not because
    // there's anything private about them.
    var includeStickers by remember { mutableStateOf(false) }
    var includeIcons by remember { mutableStateOf(false) }
    var includeWordlists by remember { mutableStateOf(true) }
    // Just the list of repository addresses — small, and the one part of the
    // addon setup a restore would otherwise lose.
    var includeAddons by remember { mutableStateOf(true) }
    var includeDictionary by remember { mutableStateOf(false) }
    var includeClipboard by remember { mutableStateOf(false) }
    var includeSecrets by remember { mutableStateOf(false) }

    var message by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf<PendingImport?>(null) }

    fun selectedSections(): Set<ConfigBackup.Section> = buildSet {
        if (includeSettings) add(ConfigBackup.Section.SETTINGS)
        if (includeThemes) add(ConfigBackup.Section.THEMES)
        if (includeDictionary) add(ConfigBackup.Section.DICTIONARY)
        if (includeClipboard) add(ConfigBackup.Section.CLIPBOARD)
        if (includeSnippets) add(ConfigBackup.Section.SNIPPETS)
        if (includeStickers) add(ConfigBackup.Section.STICKERS)
        if (includeIcons) add(ConfigBackup.Section.ICONS)
        if (includeWordlists) add(ConfigBackup.Section.WORDLISTS)
        if (includeAddons) add(ConfigBackup.Section.ADDONS)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConfigBackup.MIME_TYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val sections = selectedSections()
        scope.launch {
            val ok = runCancellable {
                val text = repository.exportConfig(
                    sections = sections,
                    includeSecrets = includeSecrets,
                    appVersion = BuildConfig.VERSION_CODE,
                    appVersionName = BuildConfig.VERSION_NAME,
                )
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray())
                    } ?: error("no stream")
                }
            }.isSuccess
            message = when {
                !ok -> context.getString(R.string.backup_export_write_error)
                includeSettings && includeSecrets ->
                    context.getString(R.string.backup_export_done_with_keys)
                else -> context.getString(R.string.backup_export_done)
            }
        }
    }

    // Import reads the file first and asks before writing: restoring is not
    // something to discover you have done. Both the full-config bundle and the
    // older settings-only file are accepted.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.requireInputStream(uri)
                        .use { it.readBytes().decodeToString() }
                }.getOrNull()
            }
            confirmImport = when {
                text == null -> {
                    message = context.getString(R.string.backup_import_read_error); null
                }
                ConfigBackup.decode(text) != null -> PendingImport.Config(text)
                SettingsBackup.decode(text) != null -> PendingImport.Legacy(text)
                else -> {
                    message = context.getString(R.string.backup_not_a_backup); null
                }
            }
        }
    }

    Text(
        stringResource(R.string.backup_info),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )

    SettingsGroup(stringResource(R.string.backup_include_group_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.backup_section_settings_label),
                stringResource(R.string.backup_include_settings_subtitle),
                includeSettings,
            ) { includeSettings = it }
        }
        if (includeSettings) {
            item {
                ToggleSetting(
                    stringResource(R.string.backup_include_secrets_title),
                    stringResource(R.string.backup_include_secrets_subtitle),
                    includeSecrets,
                    info = stringResource(R.string.backup_include_secrets_info),
                ) { includeSecrets = it }
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.backup_section_themes_label),
                stringResource(R.string.backup_include_themes_subtitle),
                includeThemes,
                info = stringResource(R.string.backup_include_themes_info),
            ) { includeThemes = it }
        }
        item {
            ToggleSetting(
                stringResource(R.string.backup_section_dictionary_label),
                stringResource(R.string.backup_include_dictionary_subtitle),
                includeDictionary,
                info = stringResource(R.string.backup_include_dictionary_info),
            ) { includeDictionary = it }
        }
        item {
            ToggleSetting(
                stringResource(R.string.backup_section_clipboard_label),
                stringResource(R.string.backup_include_clipboard_subtitle),
                includeClipboard,
                info = stringResource(R.string.backup_include_clipboard_info),
            ) { includeClipboard = it }
        }
        item {
            ToggleSetting(
                stringResource(R.string.backup_section_snippets_label),
                stringResource(R.string.backup_include_snippets_subtitle),
                includeSnippets,
            ) { includeSnippets = it }
        }
        item {
            ToggleSetting(
                stringResource(R.string.backup_section_stickers_label),
                stringResource(R.string.backup_include_stickers_subtitle),
                includeStickers,
                info = stringResource(R.string.backup_include_stickers_info),
            ) { includeStickers = it }
        }
        item {
            ToggleSetting(
                stringResource(R.string.backup_section_icons_label),
                stringResource(R.string.backup_include_icons_subtitle),
                includeIcons,
                info = stringResource(R.string.backup_include_icons_info),
            ) { includeIcons = it }
        }
        item {
            ToggleSetting(
                stringResource(R.string.backup_section_wordlists_label),
                stringResource(R.string.backup_include_wordlists_subtitle),
                includeWordlists,
            ) { includeWordlists = it }
        }
        item {
            ToggleSetting(
                stringResource(R.string.backup_section_addons_label),
                stringResource(R.string.backup_include_addons_subtitle),
                includeAddons,
                info = stringResource(R.string.backup_include_addons_info),
            ) { includeAddons = it }
        }
    }

    SettingsGroup {
        item {
            OutlinedButton(
                enabled = selectedSections().isNotEmpty(),
                onClick = {
                    // Datestamp the default name so successive backups don't
                    // overwrite each other and each file self-labels when it was made.
                    // Locale.US, not the default: on a Thai-Buddhist locale the
                    // platform formatter stamps 2569 for 2026, and a filename that
                    // sorts by date has to mean the same thing everywhere.
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    exportLauncher.launch(
                        "wmkeyboard-backup-$stamp.${ConfigBackup.FILE_EXTENSION}",
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.backup_export_action)) }
        }
    }

    SettingsGroup(stringResource(R.string.backup_import_group_title)) {
        item {
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(stringResource(R.string.backup_import_action)) }
        }
    }
    CaptionText(stringResource(R.string.backup_import_note))
    Spacer(Modifier.height(16.dp))

    when (val pending = confirmImport) {
        is PendingImport.Config -> {
            val parsed = remember(pending.text) { ConfigBackup.decode(pending.text) }
            val counts = remember(pending.text) { parsed?.let { repository.describeConfig(it) }.orEmpty() }
            val hasSecrets = remember(pending.text) { parsed?.let { repository.configContainsSecrets(it) } ?: false }
            AlertDialog(
                onDismissRequest = { confirmImport = null },
                title = { Text(stringResource(R.string.backup_import_confirm_title)) },
                text = {
                    Text(
                        buildString {
                            append(context.getString(R.string.backup_import_contains))
                            append("\n")
                            for ((section, count) in counts) {
                                append("\n")
                                append(
                                    context.getString(
                                        R.string.backup_import_section_line,
                                        sectionLabel(context, section),
                                        sectionSummary(context, section, count),
                                    ),
                                )
                            }
                            append("\n\n")
                            append(context.getString(R.string.backup_import_merge_note))
                            if (hasSecrets) {
                                append("\n\n")
                                append(context.getString(R.string.backup_import_api_keys_note))
                            }
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmImport = null
                        scope.launch {
                            message = when (val result = repository.importConfig(pending.text)) {
                                is SettingsRepository.ConfigImportResult.Applied -> buildString {
                                    if (result.restored.isEmpty()) {
                                        append(context.getString(R.string.backup_restore_nothing))
                                    } else {
                                        append(
                                            context.getString(
                                                R.string.backup_restore_done,
                                                result.restored.joinToString {
                                                    sectionLabelLowercase(context, it)
                                                },
                                            ),
                                        )
                                    }
                                    if (result.settingsFailed) {
                                        append("\n\n")
                                        append(
                                            context.getString(R.string.backup_restore_settings_failed),
                                        )
                                    }
                                }
                                SettingsRepository.ConfigImportResult.NotABackup ->
                                    context.getString(R.string.backup_not_a_backup)
                            }
                        }
                    }) { Text(stringResource(CommonR.string.common_import)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmImport = null }) {
                        Text(stringResource(CommonR.string.common_cancel))
                    }
                },
            )
        }
        is PendingImport.Legacy -> {
            val parsed = remember(pending.text) { SettingsBackup.decode(pending.text) }
            AlertDialog(
                onDismissRequest = { confirmImport = null },
                title = { Text(stringResource(R.string.backup_import_settings_confirm_title)) },
                text = {
                    Text(
                        buildString {
                            val entries = parsed?.entries?.size ?: 0
                            append(
                                context.resources.getQuantityString(
                                    R.plurals.backup_import_settings_overwrite,
                                    entries,
                                    entries,
                                ),
                            )
                            if (parsed?.containsSecrets == true) {
                                append("\n\n")
                                append(context.getString(R.string.backup_import_api_keys_note))
                            }
                            val skipped = parsed?.skipped ?: 0
                            if (skipped > 0) {
                                append("\n\n")
                                append(
                                    context.resources.getQuantityString(
                                        R.plurals.backup_import_settings_skipped,
                                        skipped,
                                        skipped,
                                    ),
                                )
                            }
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmImport = null
                        scope.launch {
                            message = when (val result = repository.importSettings(pending.text)) {
                                is SettingsRepository.ImportResult.Applied ->
                                    context.resources.getQuantityString(
                                        R.plurals.backup_restore_settings_count,
                                        result.settings,
                                        result.settings,
                                    )
                                SettingsRepository.ImportResult.RolledBack ->
                                    context.getString(R.string.backup_restore_rolled_back)
                                SettingsRepository.ImportResult.NotABackup ->
                                    context.getString(R.string.backup_not_a_settings_backup)
                            }
                        }
                    }) { Text(stringResource(CommonR.string.common_import)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmImport = null }) {
                        Text(stringResource(CommonR.string.common_cancel))
                    }
                },
            )
        }
        null -> {}
    }

    val messageText = message
    if (messageText != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(messageText) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text(stringResource(CommonR.string.common_ok))
                }
            },
        )
    }
}

// ---- custom dictionaries ----

/** Human name for a language id, used as the word-list group header. */
private fun languageLabel(langId: String): String =
    LanguageRegistry.byId(langId).englishName

/** One imported list: the file plus how many words it parsed to. */
private data class WordListEntry(val file: java.io.File, val words: Int)

@Composable
private fun CustomDictionarySettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var lists by remember {
        mutableStateOf<Map<String, List<WordListEntry>>>(emptyMap())
    }
    var pending by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var urlDialogFor by remember { mutableStateOf<String?>(null) }

    // Counting words means reading every list, so it never runs on the main
    // thread — the screen draws empty for a moment and fills in.
    suspend fun refresh() {
        lists = withContext(Dispatchers.IO) {
            settings.enabledLanguages.associate { language ->
                language.id to CustomDictionaries.lists(context.filesDir, language.id).map { file ->
                    val words = runCatching {
                        file.inputStream().use { DictionaryLoader.loadEntries(it).size }
                    }.getOrDefault(0)
                    WordListEntry(file, words)
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun importFromUrl(langId: String, url: String) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = android.net.Uri.parse(url.trim())
                    if (uri.scheme != "http" && uri.scheme != "https") {
                        return@runCatching -2
                    }
                    val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null }
                        ?: "wordlist"
                    val temp = java.io.File.createTempFile("dict_url_", ".tmp", context.cacheDir)
                    try {
                        ToolHttp.download(url.trim(), temp, maxBytes = CustomDictionaries.MAX_BYTES)
                        temp.inputStream().use { CustomDictionaries.import(context.filesDir, langId, name, it) }
                    } finally {
                        temp.delete()
                    }
                }.getOrElse { -1 }
            }
            busy = false
            message = when {
                result == -2 -> context.getString(R.string.customdict_url_scheme_error)
                result < 0 -> context.getString(R.string.customdict_url_download_error)
                result == 0 -> context.getString(R.string.customdict_import_empty_error)
                else -> context.resources.getQuantityString(
                    R.plurals.customdict_import_added_words,
                    result,
                    result,
                    languageLabel(langId),
                )
            }
            if (result > 0) {
                refresh()
                repository.bumpCustomDictVersion()
            }
        }
    }

    val importList = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val language = pending
        pending = null
        if (uri == null || language == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver
                        .query(uri, null, null, null, null)?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                        } ?: "wordlist"
                    val size = context.contentResolver
                        .query(uri, null, null, null, null)?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
                        } ?: -1L
                    if (size > CustomDictionaries.MAX_BYTES) return@runCatching -1
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: return@runCatching 0
                    stream.use { CustomDictionaries.import(context.filesDir, language, name, it) }
                }.getOrDefault(0)
            }
            busy = false
            message = when {
                result < 0 -> context.getString(R.string.customdict_import_too_large_error)
                result == 0 -> context.getString(R.string.customdict_import_empty_error)
                else -> context.resources.getQuantityString(
                    R.plurals.customdict_import_added_words,
                    result,
                    result,
                    languageLabel(language),
                )
            }
            if (result > 0) {
                refresh()
                repository.bumpCustomDictVersion()
            }
        }
    }

    Text(
        stringResource(R.string.customdict_info),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )

    for (language in settings.enabledLanguages) {
        val entries = lists[language.id].orEmpty()
        SettingsGroup(language.englishName) {
            for (entry in entries) {
                item {
                    WmRow(
                        title = entry.file.nameWithoutExtension,
                        subtitle = pluralStringResource(
                            R.plurals.customdict_word_count,
                            entry.words,
                            entry.words,
                        ),
                        trailing = {
                            IconButton(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            CustomDictionaries.remove(entry.file)
                                        }
                                        refresh()
                                        repository.bumpCustomDictVersion()
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(
                                        R.string.customdict_delete_list_desc,
                                        entry.file.nameWithoutExtension,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
            item {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            pending = language.id
                            importList.launch(arrayOf("*/*"))
                        },
                    ) {
                        Text(
                            stringResource(
                                if (entries.isEmpty()) R.string.customdict_import_action
                                else R.string.customdict_import_another_action,
                            ),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { urlDialogFor = language.id },
                    ) { Text(stringResource(R.string.customdict_from_url_action)) }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))

    val messageText = message
    if (messageText != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(messageText) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text(stringResource(CommonR.string.common_ok))
                }
            },
        )
    }

    val urlLanguage = urlDialogFor
    if (urlLanguage != null) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { urlDialogFor = null },
            title = { Text(stringResource(R.string.customdict_url_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("https://…") },
                    placeholder = { Text(stringResource(R.string.customdict_url_dialog_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank(),
                    onClick = {
                        urlDialogFor = null
                        importFromUrl(urlLanguage, url)
                    },
                ) { Text(stringResource(CommonR.string.common_download)) }
            },
            dismissButton = {
                TextButton(onClick = { urlDialogFor = null }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }
}

// ---- emoji keyword packs ----

/** One imported emoji pack: the file plus how many emoji it names. */
private data class EmojiPackEntry(val file: java.io.File, val emoji: Int)

/**
 * Per-language emoji keyword packs: the downloadable dictionaries from the
 * data repo, and the user's own imports.
 *
 * Deliberately the same shape as [CustomDictionarySettings] — per-language
 * groups, a download row, import from a file or a URL, delete a row — because
 * it solves the same problem: the app can only bundle so many languages, and
 * everything past that has to arrive from somewhere else.
 */
@Composable
private fun EmojiKeywordSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var packs by remember { mutableStateOf<Map<String, List<EmojiPackEntry>>>(emptyMap()) }
    var pending by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var urlDialogFor by remember { mutableStateOf<String?>(null) }

    // Enabled languages are the ones worth *offering* an import for, but a pack
    // can arrive for a language that isn't enabled — an addon repository
    // installs by langId, and languages get turned off again. Those groups
    // still have to appear or the pack would be uninstallable from here.
    val languageIds = remember(settings.enabledLanguages, packs.keys) {
        (settings.enabledLanguages.map { it.id } + packs.keys).distinct()
    }

    // Counting emoji means parsing every pack, so it never runs on the main
    // thread — the screen draws empty for a moment and fills in.
    suspend fun refresh() {
        packs = withContext(Dispatchers.IO) {
            // Enabled languages are the ones worth offering a download for,
            // but a pack can outlive the language being on — an addon repo
            // installs by langId, and languages get turned off again. Those
            // groups still have to appear or the pack is unreachable.
            val ids = (
                settings.enabledLanguages.map { it.id } +
                    EmojiKeywordPacks.languages(context.filesDir) +
                    EmojiDictStore.downloadedLanguageIds(context.filesDir)
                ).distinct()
            ids.associateWith { id ->
                EmojiKeywordPacks.packs(context.filesDir, id).map { file ->
                    val count = runCatching {
                        file.inputStream().use { EmojiKeywordPack.load(it).size }
                    }.getOrDefault(0)
                    EmojiPackEntry(file, count)
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    suspend fun finish(langId: String, result: Int) {
        busy = false
        message = when {
            result == -2 -> context.getString(R.string.customdict_url_scheme_error)
            result == -1 -> context.getString(R.string.customdict_import_too_large_error)
            result == 0 -> context.getString(R.string.customdict_emoji_import_empty_error)
            else -> context.resources.getQuantityString(
                R.plurals.customdict_emoji_import_added,
                result,
                result,
                languageLabel(langId),
            )
        }
        if (result > 0) {
            refresh()
            repository.bumpEmojiKeywordPackVersion()
        }
    }

    fun importFromUrl(langId: String, url: String) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = android.net.Uri.parse(url.trim())
                    if (uri.scheme != "http" && uri.scheme != "https") {
                        return@runCatching -2
                    }
                    val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null }
                        ?: "emoji"
                    val temp = java.io.File.createTempFile("emoji_url_", ".tmp", context.cacheDir)
                    try {
                        ToolHttp.download(url.trim(), temp, maxBytes = EmojiKeywordPack.MAX_BYTES)
                        temp.inputStream().use {
                            EmojiKeywordPacks.import(context.filesDir, langId, name, it)
                        }
                    } finally {
                        temp.delete()
                    }
                }.getOrElse { -1 }
            }
            finish(langId, result)
        }
    }

    val importPack = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val language = pending
        pending = null
        if (uri == null || language == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver
                        .query(uri, null, null, null, null)?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                        } ?: "emoji"
                    val size = context.contentResolver
                        .query(uri, null, null, null, null)?.use { cursor ->
                            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
                        } ?: -1L
                    if (size > EmojiKeywordPack.MAX_BYTES) return@runCatching -1
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: return@runCatching 0
                    stream.use {
                        EmojiKeywordPacks.import(context.filesDir, language, name, it)
                    }
                }.getOrDefault(0)
            }
            finish(language, result)
        }
    }

    // The examples are drawn from the languages this user actually types, so
    // the line demonstrates the feature instead of demonstrating three scripts
    // they may not read.
    val packExamples = EmojiSearchExamples
        .pick(EmojiSearchExamples.money, settings.enabledLanguages.map { it.id }, limit = 3)
        .joinToString(", ")
    Text(
        stringResource(R.string.customdict_emoji_info, packExamples),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )

    SettingsGroup(stringResource(R.string.customdict_emoji_downloads_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.customdict_emoji_auto_download_title),
                stringResource(R.string.customdict_emoji_auto_download_subtitle),
                settings.emoji.autoDownloadKeywords,
                info = stringResource(R.string.customdict_emoji_auto_download_info),
            ) { scope.launch { repository.setEmojiAutoDownloadKeywords(it) } }
        }
    }

    for (languageId in languageIds) {
        val entries = packs[languageId].orEmpty()
        val dict = EmojiDictCatalog.forLanguage(languageId)
        SettingsGroup(languageLabel(languageId)) {
            if (dict != null) {
                item { EmojiDictRow(dict) }
            }
            for (entry in entries) {
                item {
                    WmRow(
                        title = entry.file.nameWithoutExtension,
                        subtitle = pluralStringResource(
                            R.plurals.customdict_emoji_count,
                            entry.emoji,
                            entry.emoji,
                        ),
                        trailing = {
                            IconButton(
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            EmojiKeywordPacks.remove(entry.file)
                                        }
                                        refresh()
                                        repository.bumpEmojiKeywordPackVersion()
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(
                                        R.string.customdict_delete_pack_desc,
                                        entry.file.nameWithoutExtension,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
            item {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            pending = languageId
                            importPack.launch(arrayOf("*/*"))
                        },
                    ) {
                        Text(
                            stringResource(
                                if (entries.isEmpty()) R.string.customdict_emoji_import_action
                                else R.string.customdict_import_another_action,
                            ),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { urlDialogFor = languageId },
                    ) { Text(stringResource(R.string.customdict_from_url_action)) }
                }
            }
        }
    }

    Text(
        stringResource(R.string.customdict_emoji_format_info),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Spacer(Modifier.height(16.dp))

    val messageText = message
    if (messageText != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(messageText) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text(stringResource(CommonR.string.common_ok))
                }
            },
        )
    }

    val urlLanguage = urlDialogFor
    if (urlLanguage != null) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { urlDialogFor = null },
            title = { Text(stringResource(R.string.customdict_emoji_url_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("https://…") },
                    placeholder = {
                        Text(stringResource(R.string.customdict_emoji_url_dialog_hint))
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank(),
                    onClick = {
                        urlDialogFor = null
                        importFromUrl(urlLanguage, url)
                    },
                ) { Text(stringResource(CommonR.string.common_download)) }
            },
            dismissButton = {
                TextButton(onClick = { urlDialogFor = null }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }
}

// ---- fonts ----

/** Mime types SAF offers when picking a font; octet-stream covers file managers that don't tag fonts. */
private val FONT_MIME_TYPES = arrayOf(
    "font/ttf", "font/otf", "font/*", "application/x-font-ttf", "application/octet-stream",
)

/**
 * A refused font import, kept as resource ids rather than finished words: the
 * import runs off the main thread with no way to draw, so the dialog is what
 * resolves the wording against the language the app is running in.
 *
 * A message that counts fonts sets [pluralsRes] and [quantity] instead of
 * [stringRes]; [args] fills the placeholders of [stringRes].
 */
private data class FontMessage(
    @StringRes val stringRes: Int = 0,
    @PluralsRes val pluralsRes: Int = 0,
    val quantity: Int = 0,
    val args: List<Any> = emptyList(),
)

/**
 * Font picker: separate English and Bengali choices, each offering the
 * system default, the installed-font library, curated Google Fonts (every row
 * rendered in its own face as a live preview — faces download on first view and
 * are cached by the system provider), plus the legacy single imported file per
 * script.
 *
 * Importing a file here fills the library rather than overwriting one fixed
 * slot, so importing a second font no longer evicts the first. The two old
 * single-slot files still render and stay selectable for anyone who set one
 * before the library existed; nothing migrates and nothing is lost.
 */
@Composable
private fun FontSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fontStore = remember { FontStore.get(context) }
    val fontRevision by fontStore.revision.collectAsStateWithLifecycle()
    // Text faces only: an emoji font is chosen on the Emoji screen, and picking
    // one for the key labels would draw the alphabet as coloured pictograms.
    val installedFonts = remember(fontRevision) { fontStore.textFonts() }
    // The failure to show, still unresolved; see [FontMessage].
    var fontMessage by remember { mutableStateOf<FontMessage?>(null) }

    fun importIntoLibrary(uri: android.net.Uri, apply: suspend (String) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.requireInputStream(uri).use {
                        FontFile.import(it, fontStore, name = fontFileLabel(context, uri))
                    }
                }.getOrElse {
                    FontImportResult.Failed(ContentR.string.core_content_font_error_read)
                }
            }
            when (result) {
                is FontImportResult.Imported -> apply(FontStore.fontIdFor(result.font.id))
                is FontImportResult.NotAFont -> fontMessage = FontMessage(result.messageRes)
                FontImportResult.TooManyFonts -> fontMessage = FontMessage(
                    pluralsRes = R.plurals.fonts_import_limit_message,
                    quantity = FontStore.MAX_FONTS,
                )
                is FontImportResult.Failed ->
                    fontMessage = FontMessage(result.messageRes, args = result.messageArgs)
            }
        }
    }

    fun deleteInstalled(font: InstalledFont) {
        scope.launch {
            // Drop the selection first, so the keyboard never renders against a
            // file that is about to disappear. Covers the per-script overrides
            // too, which neither of the pickers on this screen can see.
            repository.forgetInstalledFont(FontStore.fontIdFor(font.id))
            withContext(Dispatchers.IO) { fontStore.delete(font.id) }
        }
    }

    fontMessage?.let { message ->
        // Spelled out rather than spread: no font message takes more than one
        // argument, and a spread copies the array on every recomposition.
        val text = when {
            message.pluralsRes != 0 -> pluralStringResource(
                message.pluralsRes,
                message.quantity,
                message.quantity,
            )
            message.args.isEmpty() -> stringResource(message.stringRes)
            else -> stringResource(message.stringRes, message.args.first())
        }
        AlertDialog(
            onDismissRequest = { fontMessage = null },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { fontMessage = null }) {
                    Text(stringResource(CommonR.string.common_ok))
                }
            },
        )
    }

    Text(
        stringResource(R.string.fonts_info),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    FontPickerSection(
        header = stringResource(R.string.fonts_english_header),
        sample = "The quick brown fox jumps over the lazy dog",
        selectedId = settings.keyFontId,
        googleNames = KeyboardFonts.googleFonts,
        customId = KeyboardFonts.CUSTOM_ID,
        customFile = KeyboardFonts.customFontFile(context),
        customName = settings.customFontName,
        onSelect = { id -> scope.launch { repository.setKeyFontId(id) } },
        onImport = { uri -> importIntoLibrary(uri) { repository.setKeyFontId(it) } },
        installedFonts = installedFonts,
        installedTitle = stringResource(R.string.fonts_installed_header),
        // The English picker also drives Cyrillic and Greek, which have no
        // picker of their own — a font claiming any of the three belongs here.
        scripts = setOf(ScriptId.LATIN, ScriptId.CYRILLIC, ScriptId.GREEK),
        onDeleteInstalled = ::deleteInstalled,
    )
    // Curated font pickers for the non-Latin scripts, each shown only while a
    // language using that script is enabled. Every one offers the script's
    // automatic Noto face plus a few alternatives; the scripts that also take an
    // imported file (Bengali today) additionally get the import button and the
    // installed-font library. Latin/Cyrillic/Greek follow the English font above.
    val enabledScripts = settings.enabledLanguages.mapTo(mutableSetOf()) { it.script }
    for (choices in KeyboardFonts.scriptFontChoices) {
        if (choices.script !in enabledScripts) continue
        val script = choices.script.name
        // Non-null only for the scripts whose picker takes an imported file;
        // everything import-shaped below hangs off it.
        val customId = KeyboardFonts.customScriptFontId(choices.script)
        val importable = customId != null
        val onImportFont: ((Uri) -> Unit)? = customId?.let {
            { uri: Uri -> importIntoLibrary(uri) { id -> repository.setScriptFontId(script, id) } }
        }
        // The name of the script, drawn into both headers of this picker.
        val scriptName = stringResource(choices.labelRes)
        FontPickerSection(
            header = stringResource(R.string.fonts_script_header, scriptName),
            sample = choices.sample,
            selectedId = settings.scriptFontIds[script] ?: KeyboardFonts.DEFAULT_ID,
            googleNames = choices.fonts,
            defaultLabel = stringResource(R.string.fonts_default_noto_label),
            customId = customId,
            customFile = KeyboardFonts.customScriptFontFile(context, choices.script),
            customName = settings.customScriptFontNames[script].orEmpty(),
            onSelect = { id -> scope.launch { repository.setScriptFontId(script, id) } },
            onImport = onImportFont,
            installedFonts = if (importable) installedFonts else emptyList(),
            installedTitle = stringResource(R.string.fonts_installed_script_header, scriptName),
            scripts = setOf(choices.script),
            onDeleteInstalled = if (importable) ::deleteInstalled else null,
        )
    }
    Spacer(Modifier.height(16.dp))
}

/**
 * One script's font list: the default row, curated Google faces, and — for the
 * scripts that support it (English/Bengali) — the single imported file and an
 * import button. Scripts that only offer curated faces pass [customFile] null;
 * their default row is relabelled via [defaultLabel] since it is the script's
 * automatic Noto face rather than the raw system font.
 *
 * [installedFonts] is the library filled by the Addons screen and by importing a
 * file here. It gets its own section above the curated list: it is a short,
 * personal list next to twenty stock faces, and burying it inside them makes a
 * font the user deliberately installed harder to find than one they didn't.
 *
 * [scripts] is what the picker is for. A font that declares which languages it
 * covers is only offered where it covers something — plenty of display faces are
 * Latin-only, and offering one for Bengali offers a keyboard of empty boxes. A
 * font that declares nothing makes no claim and is offered everywhere.
 */
@Composable
private fun FontPickerSection(
    header: String,
    sample: String,
    selectedId: String,
    googleNames: List<String>,
    onSelect: (String) -> Unit,
    defaultLabel: String = stringResource(R.string.fonts_default_system_label),
    /** The imported-file id this picker writes, or null if it takes no import. */
    customId: String? = KeyboardFonts.CUSTOM_ID,
    customFile: java.io.File? = null,
    customName: String = "",
    onImport: ((android.net.Uri) -> Unit)? = null,
    installedFonts: List<InstalledFont> = emptyList(),
    installedTitle: String = stringResource(R.string.fonts_installed_header),
    scripts: Set<ScriptId> = emptySet(),
    onDeleteInstalled: ((InstalledFont) -> Unit)? = null,
) {
    val context = LocalContext.current
    val importFont = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onImport?.invoke(uri) }
    val relevant = remember(installedFonts, scripts) {
        installedFonts.filter { font ->
            font.langIds.isEmpty() || scripts.isEmpty() ||
                font.langIds.any { LanguageRegistry.byId(it).script in scripts }
        }
    }
    if (relevant.isNotEmpty()) {
        SettingsGroup(installedTitle) {
            for (font in relevant) {
                item {
                    val id = FontStore.fontIdFor(font.id)
                    FontChoiceRow(
                        label = font.name,
                        family = remember(id) { KeyboardFonts.family(context, id) },
                        sample = sample,
                        selected = selectedId == id,
                        onDelete = onDeleteInstalled?.let { delete -> { delete(font) } },
                    ) { onSelect(id) }
                }
            }
        }
    }
    SettingsGroup(header) {
        item {
            FontChoiceRow(
                label = defaultLabel,
                family = null,
                sample = sample,
                selected = selectedId == KeyboardFonts.DEFAULT_ID,
            ) { onSelect(KeyboardFonts.DEFAULT_ID) }
        }
        for (name in googleNames) {
            item {
                val id = KeyboardFonts.googleId(name)
                FontChoiceRow(
                    label = name,
                    family = remember(id) { KeyboardFonts.family(context, id) },
                    sample = sample,
                    selected = selectedId == id,
                ) { onSelect(id) }
            }
        }
        // The single imported file this picker used to keep before fonts moved
        // into the shared library above. Shown only while that file is still
        // there, so an old import stays selectable and a fresh install never
        // sees the row.
        if (customId != null && customFile?.exists() == true) {
            item {
                val importedLabel = stringResource(R.string.fonts_imported_label)
                FontChoiceRow(
                    label = customName.ifBlank { importedLabel },
                    family = remember(customName) { KeyboardFonts.family(context, customId) },
                    sample = sample,
                    selected = selectedId == customId,
                ) { onSelect(customId) }
            }
        }
        if (onImport != null) {
            item {
                OutlinedButton(
                    onClick = { importFont.launch(FONT_MIME_TYPES) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.fonts_import_action)) }
            }
        }
    }
}

/** One selectable font row, its label and sample line drawn in the font itself. */
@Composable
private fun FontChoiceRow(
    label: String,
    family: FontFamily?,
    sample: String,
    selected: Boolean,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    WmRow(
        title = label,
        // The row is the preview: name and sample both drawn in the font
        // itself, so picking one shows what it will look like.
        titleContent = { Text(label, fontFamily = family, fontSize = 18.sp) },
        supporting = {
            Text(
                sample,
                fontFamily = family,
                maxLines = 1,
            )
        },
        trailing = if (selected || onDelete != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = stringResource(R.string.fonts_selected_desc),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(
                                    R.string.fonts_delete_desc,
                                    label,
                                ),
                            )
                        }
                    }
                }
            }
        } else {
            null
        },
        onClick = onClick,
    )
}

/**
 * Copies a picked font into private storage and returns its display name,
 * or null when the stream can't be read or the platform can't parse the
 * file (the bad copy is deleted so it never sticks as the "custom font").
 */
/**
 * A human-readable name for a picked font file: the provider's display name
 * with the extension stripped, since "Inter-Regular" reads better in the picker
 * than "Inter-Regular.ttf".
 */
private fun fontFileLabel(context: Context, uri: android.net.Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
    return name?.substringBeforeLast('.')?.trim().orEmpty()
        .ifBlank { context.getString(R.string.fonts_imported_label) }
}

private fun importFontFile(context: Context, uri: android.net.Uri, dest: java.io.File): String? {
    return runCatching {
        dest.parentFile?.mkdirs()
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (copied == null) return null
        val parsed = runCatching { android.graphics.Typeface.createFromFile(dest) }.getOrNull()
        if (parsed == null || parsed == android.graphics.Typeface.DEFAULT) {
            dest.delete()
            return null
        }
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: dest.name
    }.getOrNull()
}

// ---- tools ----

/**
 * Whether a tool's settings page offers anything beyond the enable switch —
 * drives the "has more settings" marker on the tools list. Kept as the
 * caption-only exceptions so a new tool with options is marked by default.
 */
/**
 * Whether the tool's detail page offers anything beyond the enable switch —
 * gates the "has more settings" affordance in the tools list. Keep in sync
 * with [ToolDetailSettings]'s `when`: a tool whose page is just the toggle
 * (or a caption) doesn't earn the icon.
 */
private fun toolHasOptions(tool: ToolbarTool): Boolean =
    tool !in setOf(
        ToolbarTool.UNIT_CONVERT, ToolbarTool.SETTINGS,
        // Quick toggles/panels, not tools with settings of their own —
        // their options all live elsewhere (Appearance, Typing).
        ToolbarTool.THEMES, ToolbarTool.SOUND_HAPTICS, ToolbarTool.ONE_HANDED,
        // Just an enable toggle — the transport lives on the keyboard panel,
        // there is nothing to configure here.
        ToolbarTool.MEDIA_CONTROL,
    )

/**
 * The name of a tool on its settings screen, as a string resource the caller
 * resolves while it draws.
 *
 * The keyboard toolbar names the same tools in `toolLabelRes`, and half of them
 * word it identically: those reuse the keyboard's own resource rather than
 * carry a second copy for translators. The rest are the settings-screen wording,
 * which has room for the longer name the toolbar cannot fit ("Bubble level"
 * against "Level"), and those live in this module.
 */
@StringRes
internal fun toolTitle(tool: ToolbarTool): Int = when (tool) {
    ToolbarTool.EMOJI -> ImeR.string.ime_tool_emoji
    ToolbarTool.CLIPBOARD -> ImeR.string.ime_tool_clipboard
    ToolbarTool.SNIPPETS -> ImeR.string.ime_tool_snippets
    ToolbarTool.TEXT_EDIT -> ImeR.string.ime_tool_text_edit
    ToolbarTool.ONE_HANDED -> R.string.fonts_tool_one_handed_title
    ToolbarTool.SPLIT -> R.string.fonts_tool_split_title
    ToolbarTool.FLOATING -> R.string.fonts_tool_floating_title
    ToolbarTool.SETTINGS -> R.string.fonts_tool_settings_title
    ToolbarTool.FLASHLIGHT -> ImeR.string.ime_tool_flashlight
    ToolbarTool.COMPASS -> ImeR.string.ime_tool_compass
    ToolbarTool.LEVEL -> R.string.fonts_tool_level_title
    ToolbarTool.UNDO -> ImeR.string.ime_tool_undo
    ToolbarTool.REDO -> ImeR.string.ime_tool_redo
    ToolbarTool.MOON_PHASE -> R.string.fonts_tool_moon_phase_title
    ToolbarTool.WEATHER -> ImeR.string.ime_tool_weather
    ToolbarTool.CALENDAR -> ImeR.string.ime_tool_calendar
    ToolbarTool.INCOGNITO -> ImeR.string.ime_tool_incognito
    ToolbarTool.POWER_SAVING -> ImeR.string.ime_tool_power_saving
    ToolbarTool.THEMES -> ImeR.string.ime_tool_themes
    ToolbarTool.AUTOCORRECT -> ImeR.string.ime_tool_autocorrect
    ToolbarTool.SOUND_HAPTICS -> ImeR.string.ime_tool_sound_haptics
    ToolbarTool.NUMPAD -> ImeR.string.ime_tool_numpad
    ToolbarTool.HANDWRITING -> ImeR.string.ime_tool_handwriting
    ToolbarTool.CAMERA -> ImeR.string.ime_tool_camera
    ToolbarTool.DICTIONARY -> ImeR.string.ime_tool_dictionary
    ToolbarTool.TRANSLATE -> ImeR.string.ime_tool_translate
    ToolbarTool.GIF -> ImeR.string.ime_tool_gif
    ToolbarTool.STICKER -> ImeR.string.ime_tool_sticker
    ToolbarTool.WEB_SEARCH -> R.string.fonts_tool_web_search_title
    ToolbarTool.IMAGE_SEARCH -> R.string.fonts_tool_image_search_title
    ToolbarTool.OCR -> R.string.fonts_tool_ocr_title
    ToolbarTool.QR_SCAN -> R.string.fonts_tool_qr_scan_title
    ToolbarTool.DOC_SCAN -> R.string.fonts_tool_doc_scan_title
    ToolbarTool.VOICE -> R.string.fonts_tool_voice_title
    ToolbarTool.GRAMMAR -> R.string.fonts_tool_grammar_title
    ToolbarTool.WIKIPEDIA -> ImeR.string.ime_tool_wikipedia
    ToolbarTool.SYMBOLS -> R.string.fonts_tool_symbols_title
    ToolbarTool.CALCULATOR -> ImeR.string.ime_tool_calculator
    ToolbarTool.UNIT_CONVERT -> R.string.fonts_tool_unit_convert_title
    ToolbarTool.CURRENCY -> R.string.fonts_tool_currency_title
    ToolbarTool.QR_GEN -> R.string.fonts_tool_qr_gen_title
    ToolbarTool.PASSWORD_GEN -> R.string.fonts_tool_password_gen_title
    ToolbarTool.TYPING_TEST -> R.string.fonts_tool_typing_test_title
    ToolbarTool.MEDIA_CONTROL -> R.string.fonts_tool_media_control_title
    ToolbarTool.PLUGINS -> ImeR.string.ime_tool_plugins
    ToolbarTool.AI -> R.string.fonts_tool_ai_title
    ToolbarTool.MODES -> R.string.fonts_tool_modes_title
    ToolbarTool.CURSOR_LEFT -> R.string.fonts_tool_cursor_left_title
    ToolbarTool.CURSOR_RIGHT -> R.string.fonts_tool_cursor_right_title
    ToolbarTool.CURSOR_WORD_LEFT -> ImeR.string.ime_tool_cursor_word_left
    ToolbarTool.CURSOR_WORD_RIGHT -> ImeR.string.ime_tool_cursor_word_right
    ToolbarTool.CURSOR_UP -> R.string.fonts_tool_cursor_up_title
    ToolbarTool.CURSOR_DOWN -> R.string.fonts_tool_cursor_down_title
    ToolbarTool.CURSOR_HOME -> ImeR.string.ime_tool_cursor_home
    ToolbarTool.CURSOR_END -> ImeR.string.ime_tool_cursor_end
    ToolbarTool.PAGE_UP -> ImeR.string.ime_tool_page_up
    ToolbarTool.PAGE_DOWN -> ImeR.string.ime_tool_page_down
    ToolbarTool.SELECT_WORD -> ImeR.string.ime_tool_select_word
    ToolbarTool.SELECT_LINE -> ImeR.string.ime_tool_select_line
    ToolbarTool.HIDE_KEYBOARD -> R.string.fonts_tool_hide_keyboard_title
}

/** The one-line description under a tool's name, as a string resource. */
@StringRes
internal fun toolDescription(tool: ToolbarTool): Int = when (tool) {
    ToolbarTool.EMOJI -> R.string.fonts_tool_emoji_desc
    ToolbarTool.CLIPBOARD -> R.string.fonts_tool_clipboard_desc
    ToolbarTool.SNIPPETS -> R.string.fonts_tool_snippets_desc
    ToolbarTool.TEXT_EDIT -> R.string.fonts_tool_text_edit_desc
    ToolbarTool.ONE_HANDED -> R.string.fonts_tool_one_handed_desc
    ToolbarTool.SPLIT -> R.string.fonts_tool_split_desc
    ToolbarTool.FLOATING -> R.string.fonts_tool_floating_desc
    ToolbarTool.SETTINGS -> R.string.fonts_tool_settings_desc
    ToolbarTool.FLASHLIGHT -> R.string.fonts_tool_flashlight_desc
    ToolbarTool.COMPASS -> R.string.fonts_tool_compass_desc
    ToolbarTool.LEVEL -> R.string.fonts_tool_level_desc
    ToolbarTool.UNDO -> R.string.fonts_tool_undo_desc
    ToolbarTool.REDO -> R.string.fonts_tool_redo_desc
    ToolbarTool.MOON_PHASE -> R.string.fonts_tool_moon_phase_desc
    ToolbarTool.WEATHER -> R.string.fonts_tool_weather_desc
    ToolbarTool.CALENDAR -> R.string.fonts_tool_calendar_desc
    ToolbarTool.INCOGNITO -> R.string.fonts_tool_incognito_desc
    ToolbarTool.POWER_SAVING -> R.string.fonts_tool_power_saving_desc
    ToolbarTool.THEMES -> R.string.fonts_tool_themes_desc
    ToolbarTool.AUTOCORRECT -> R.string.fonts_tool_autocorrect_desc
    ToolbarTool.SOUND_HAPTICS -> R.string.fonts_tool_sound_haptics_desc
    ToolbarTool.NUMPAD -> R.string.fonts_tool_numpad_desc
    ToolbarTool.HANDWRITING -> R.string.fonts_tool_handwriting_desc
    ToolbarTool.CAMERA -> R.string.fonts_tool_camera_desc
    ToolbarTool.DICTIONARY -> R.string.fonts_tool_dictionary_desc
    ToolbarTool.TRANSLATE -> R.string.fonts_tool_translate_desc
    ToolbarTool.GIF -> R.string.fonts_tool_gif_desc
    ToolbarTool.STICKER -> R.string.fonts_tool_sticker_desc
    ToolbarTool.WEB_SEARCH -> R.string.fonts_tool_web_search_desc
    ToolbarTool.IMAGE_SEARCH -> R.string.fonts_tool_image_search_desc
    ToolbarTool.OCR -> R.string.fonts_tool_ocr_desc
    ToolbarTool.QR_SCAN -> R.string.fonts_tool_qr_scan_desc
    ToolbarTool.DOC_SCAN -> R.string.fonts_tool_doc_scan_desc
    ToolbarTool.VOICE -> R.string.fonts_tool_voice_desc
    ToolbarTool.GRAMMAR -> R.string.fonts_tool_grammar_desc
    ToolbarTool.WIKIPEDIA -> R.string.fonts_tool_wikipedia_desc
    ToolbarTool.SYMBOLS -> R.string.fonts_tool_symbols_desc
    ToolbarTool.CALCULATOR -> R.string.fonts_tool_calculator_desc
    ToolbarTool.UNIT_CONVERT -> R.string.fonts_tool_unit_convert_desc
    ToolbarTool.CURRENCY -> R.string.fonts_tool_currency_desc
    ToolbarTool.QR_GEN -> R.string.fonts_tool_qr_gen_desc
    ToolbarTool.PASSWORD_GEN -> R.string.fonts_tool_password_gen_desc
    ToolbarTool.TYPING_TEST -> R.string.fonts_tool_typing_test_desc
    ToolbarTool.MEDIA_CONTROL -> R.string.fonts_tool_media_control_desc
    ToolbarTool.PLUGINS -> R.string.fonts_tool_plugins_desc
    ToolbarTool.AI -> R.string.fonts_tool_ai_desc
    ToolbarTool.MODES -> R.string.fonts_tool_modes_desc
    ToolbarTool.CURSOR_LEFT -> R.string.fonts_tool_cursor_left_desc
    ToolbarTool.CURSOR_RIGHT -> R.string.fonts_tool_cursor_right_desc
    ToolbarTool.CURSOR_WORD_LEFT -> R.string.fonts_tool_cursor_word_left_desc
    ToolbarTool.CURSOR_WORD_RIGHT -> R.string.fonts_tool_cursor_word_right_desc
    ToolbarTool.CURSOR_UP -> R.string.fonts_tool_cursor_up_desc
    ToolbarTool.CURSOR_DOWN -> R.string.fonts_tool_cursor_down_desc
    ToolbarTool.CURSOR_HOME -> R.string.fonts_tool_cursor_home_desc
    ToolbarTool.CURSOR_END -> R.string.fonts_tool_cursor_end_desc
    ToolbarTool.PAGE_UP -> R.string.fonts_tool_page_up_desc
    ToolbarTool.PAGE_DOWN -> R.string.fonts_tool_page_down_desc
    ToolbarTool.SELECT_WORD -> R.string.fonts_tool_select_word_desc
    ToolbarTool.SELECT_LINE -> R.string.fonts_tool_select_line_desc
    ToolbarTool.HIDE_KEYBOARD -> R.string.fonts_tool_hide_keyboard_desc
}

internal fun toolIconFor(tool: ToolbarTool): androidx.compose.ui.graphics.vector.ImageVector =
    IconDefaults.forTool(tool)

/**
 * The tool menu, grouped by what the tools do. Everything else — the
 * enable switch and the tool's own options — lives one level down.
 */
@Composable
private fun ToolsSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onOpenTool: (ToolbarTool) -> Unit,
) {
    val scope = rememberCoroutineScope()
    CaptionText(stringResource(R.string.tools_intro_info))
    ToggleSetting(
        title = stringResource(R.string.tools_colored_icons_title),
        subtitle = stringResource(R.string.tools_colored_icons_subtitle),
        checked = settings.coloredToolIcons,
        onChange = { scope.launch { repository.setColoredToolIcons(it) } },
    )
    if (settings.coloredToolIcons && settings.toolColorOverrides.isNotEmpty()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { scope.launch { repository.clearToolColors() } }) {
                Text(stringResource(R.string.tools_reset_colors_action))
            }
        }
    }
    val groups = listOf(
        stringResource(R.string.tools_group_panels_title) to listOf(
            ToolbarTool.EMOJI, ToolbarTool.CLIPBOARD, ToolbarTool.SNIPPETS,
            ToolbarTool.TEXT_EDIT, ToolbarTool.NUMPAD, ToolbarTool.HANDWRITING,
            ToolbarTool.VOICE, ToolbarTool.CAMERA, ToolbarTool.DICTIONARY,
            ToolbarTool.GRAMMAR,
        ),
        stringResource(R.string.tools_group_scanners_title) to listOf(
            ToolbarTool.OCR, ToolbarTool.QR_SCAN, ToolbarTool.DOC_SCAN,
        ),
        stringResource(R.string.tools_group_online_title) to listOf(
            ToolbarTool.TRANSLATE, ToolbarTool.GIF, ToolbarTool.STICKER,
            ToolbarTool.WEB_SEARCH, ToolbarTool.IMAGE_SEARCH,
            ToolbarTool.WIKIPEDIA, ToolbarTool.CURRENCY, ToolbarTool.AI,
        ),
        stringResource(R.string.tools_group_create_title) to listOf(
            ToolbarTool.SYMBOLS, ToolbarTool.CALCULATOR, ToolbarTool.UNIT_CONVERT,
            ToolbarTool.QR_GEN, ToolbarTool.PASSWORD_GEN, ToolbarTool.TYPING_TEST,
        ),
        stringResource(R.string.tools_group_modes_title) to listOf(
            ToolbarTool.MODES, ToolbarTool.ONE_HANDED, ToolbarTool.SPLIT, ToolbarTool.FLOATING,
        ),
        stringResource(R.string.tools_group_cursor_title) to (CursorTools + ToolbarTool.HIDE_KEYBOARD),
        stringResource(R.string.tools_group_quick_actions_title) to listOf(
            ToolbarTool.UNDO, ToolbarTool.REDO, ToolbarTool.AUTOCORRECT,
            ToolbarTool.INCOGNITO, ToolbarTool.SOUND_HAPTICS, ToolbarTool.THEMES,
            ToolbarTool.POWER_SAVING, ToolbarTool.SETTINGS,
        ),
        stringResource(R.string.tools_group_utilities_title) to listOf(
            ToolbarTool.FLASHLIGHT, ToolbarTool.COMPASS, ToolbarTool.LEVEL,
            ToolbarTool.CALENDAR, ToolbarTool.WEATHER, ToolbarTool.MOON_PHASE,
        ),
    )
    val otherTitle = stringResource(R.string.tools_group_other_title)
    // Safety net: a tool added to the enum but forgotten here still gets a
    // settings entry (this menu is the only path to a tool's options).
    val grouped = groups.flatMap { it.second }.toSet()
    val ungrouped = ToolbarTool.entries.filterNot { it in grouped }
    val allGroups = (if (ungrouped.isEmpty()) groups else groups + (otherTitle to ungrouped))
        // Tools this build can't provide (lite flavor) get no settings entry.
        .map { (title, tools) -> title to tools.filter(::isSupportedTool) }
        .filter { it.second.isNotEmpty() }
    for ((groupTitle, tools) in allGroups) {
        SettingsGroup(groupTitle) {
            for (tool in tools) {
                item {
                    WmRow(
                        title = stringResource(toolTitle(tool)),
                        subtitle = stringResource(toolDescription(tool)),
                        leading = {
                            SlotIcon(
                                IconSlots.forTool(tool),
                                contentDescription = null,
                                modifier = Modifier
                                    .wmSharedElement(takeOffKey("icon", toolRoute(tool))),
                                tint = if (settings.coloredToolIcons)
                                    toolAccentColor(tool, settings.toolColorOverrides)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        flightTo = toolRoute(tool),
                        subtitleFlies = true,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (toolHasOptions(tool)) {
                                    Icon(
                                        Icons.Outlined.Tune,
                                        contentDescription = stringResource(
                                            R.string.tools_has_options_desc,
                                        ),
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = tool in settings.enabledTools,
                                    onCheckedChange = { enabled ->
                                        scope.launch { repository.setToolEnabled(tool, enabled) }
                                    },
                                    modifier = Modifier
                                        .wmSharedElement(takeOffKey("switch", toolRoute(tool))),
                                )
                            }
                        },
                        onClick = { onOpenTool(tool) },
                    )
                }
            }
        }
    }
}

/**
 * A tool's own settings page, as flights name it. Not the navigation route
 * (`tool/{toolName}`), which is the same string for every tool and would put
 * one key on all of them.
 */
internal fun toolRoute(tool: ToolbarTool): String = "tool/${tool.name}"

/** A tool's glyph at heading size — the icon pack's, if the user installed one. */
@Composable
private fun ToolGlyph(tool: ToolbarTool) {
    SlotIcon(
        IconSlots.forTool(tool),
        contentDescription = null,
        modifier = Modifier.size(HeaderGlyphSize),
    )
}

/** One tool's screen: the enable switch plus every setting the tool has. */
@Composable
private fun ToolDetailSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    tool: ToolbarTool,
    onNavigate: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // Slider readouts are plain lambdas, so their format strings are resolved
    // here and captured. The format also puts the number through the locale,
    // which is what gives Bengali or Arabic digits.
    val numberFormat = stringResource(R.string.values_number)
    val percentFormat = stringResource(R.string.typing_value_percent)
    val dpFormat = stringResource(R.string.typing_value_dp)
    val msFormat = stringResource(R.string.typing_value_milliseconds)
    val minutesFormat = stringResource(R.string.values_minutes)
    val hoursFormat = stringResource(R.string.values_hours)
    val pixelsFormat = stringResource(R.string.values_pixels)
    val daysFormat = stringResource(R.string.values_days)
    val daysAheadFormat = stringResource(R.string.values_days_ahead)
    CaptionText(
        stringResource(toolDescription(tool)),
        modifier = Modifier.wmSharedBounds(landingKey("subtitle")),
    )
    SettingsGroup {
        item {
            ToggleSetting(
                stringResource(CommonR.string.common_enable),
                stringResource(R.string.tooldetail_enabled_subtitle),
                tool in settings.enabledTools,
                switchKey = landingKey("switch"),
            ) { scope.launch { repository.setToolEnabled(tool, it) } }
        }
        // Recolour just this tool's icon. Only meaningful while the global
        // "Colorful tool icons" switch is on, since it's what paints them.
        if (settings.coloredToolIcons) {
            item {
                var showPicker by remember { mutableStateOf(false) }
                val override = settings.toolColorOverrides[tool]
                val resolved = override ?: toolAccentColorArgb(tool)
                WmRow(
                    title = stringResource(R.string.tooldetail_icon_colour_title),
                    subtitle = if (override != null) {
                        stringResource(R.string.tooldetail_icon_colour_custom_subtitle)
                    } else {
                        stringResource(R.string.tooldetail_icon_colour_default_subtitle)
                    },
                    leading = { Swatch(resolved) },
                    onClick = { showPicker = true },
                )
                if (showPicker) {
                    ColorPickerDialog(
                        title = stringResource(
                            R.string.tooldetail_icon_colour_dialog_title,
                            stringResource(toolTitle(tool)),
                        ),
                        initial = resolved,
                        supportsAlpha = false,
                        showReset = override != null,
                        onPick = {
                            scope.launch { repository.setToolColor(tool, it) }
                            showPicker = false
                        },
                        onReset = {
                            scope.launch { repository.setToolColor(tool, null) }
                            showPicker = false
                        },
                        onDismiss = { showPicker = false },
                    )
                }
            }
        }
    }
    ToolKeywordSetting(repository, settings, tool)
    when (tool) {
        ToolbarTool.PLUGINS -> SettingsGroup(stringResource(R.string.tooldetail_plugins_group)) {
            item {
                WmRow(
                    title = stringResource(R.string.tooldetail_plugins_manage_title),
                    subtitle = stringResource(R.string.tooldetail_plugins_manage_subtitle),
                    onClick = { onNavigate("plugins") },
                )
            }
        }
        ToolbarTool.EMOJI -> SettingsGroup(stringResource(R.string.tooldetail_emoji_group)) {
            item {
                ToggleSetting(
                    stringResource(R.string.tooldetail_emoji_toolbar_title),
                    stringResource(R.string.tooldetail_emoji_toolbar_subtitle),
                    settings.emojiToolbar,
                ) { scope.launch { repository.setEmojiToolbar(it) } }
            }
            item {
                NavRow(
                    stringResource(R.string.tooldetail_emoji_all_title),
                    stringResource(R.string.tooldetail_emoji_all_subtitle),
                    onClick = { onNavigate("emoji") },
                )
            }
        }
        ToolbarTool.SNIPPETS -> SnippetSettings()
        ToolbarTool.CLIPBOARD -> {
            // Both grants happen on a system screen, so they are read through
            // rememberGrantState: the rows below disappear as soon as we come back
            // with the permission in hand, instead of on the next unrelated redraw.
            val screenshotsGranted = rememberGrantState(::hasImagesPermission)
            val usageAccessGranted = rememberGrantState(::hasUsageAccess)
            SettingsGroup(stringResource(R.string.tooldetail_clipboard_history_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_clipboard_history_title),
                        stringResource(R.string.tooldetail_clipboard_history_subtitle),
                        settings.clipboard.history,
                    ) { scope.launch { repository.setClipboardHistory(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_clipboard_suggest_recent_title),
                        stringResource(R.string.tooldetail_clipboard_suggest_recent_subtitle),
                        settings.clipboard.suggestRecent,
                    ) { scope.launch { repository.setClipboardSuggestRecent(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_clipboard_toast_title),
                        stringResource(R.string.tooldetail_clipboard_toast_subtitle),
                        settings.feedback.toastOnCopy,
                        info = stringResource(R.string.tooldetail_clipboard_toast_info),
                    ) { scope.launch { repository.setToastOnCopy(it) } }
                }
                item {
                    // The readout lambda is not composable, so the "never" word
                    // is resolved here and captured, like the hours format.
                    val never = stringResource(R.string.tooldetail_clipboard_expiry_never)
                    SliderSetting(
                        stringResource(R.string.tooldetail_clipboard_expiry_title),
                        subtitle = stringResource(R.string.tooldetail_clipboard_expiry_subtitle),
                        value = settings.clipboard.expiryHours.toFloat(),
                        range = 0f..168f,
                        display = { if (it.toInt() == 0) never else hoursFormat.format(it.toInt()) },
                    ) { scope.launch { repository.setClipboardExpiryHours(it.toInt()) } }
                }
                item {
                    SliderSetting(
                        stringResource(R.string.tooldetail_clipboard_max_title),
                        subtitle = stringResource(R.string.tooldetail_clipboard_max_subtitle),
                        value = settings.clipboard.maxItems.toFloat(),
                        range = 5f..500f,
                        display = { numberFormat.format(it.toInt()) },
                        info = stringResource(R.string.tooldetail_clipboard_max_info),
                    ) { scope.launch { repository.setClipboardMaxItems(it.toInt()) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_clipboard_bottom_row_title),
                        stringResource(R.string.tooldetail_clipboard_bottom_row_subtitle),
                        settings.clipboard.bottomRow,
                    ) { scope.launch { repository.setClipboardBottomRow(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_clipboard_pinned_last_title),
                        stringResource(R.string.tooldetail_clipboard_pinned_last_subtitle),
                        settings.clipboard.pinnedLast,
                    ) { scope.launch { repository.setClipboardPinnedLast(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_clipboard_search_title),
                        stringResource(R.string.tooldetail_clipboard_search_subtitle),
                        settings.clipboard.search,
                    ) { scope.launch { repository.setClipboardSearch(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_clipboard_entities_title),
                        stringResource(R.string.tooldetail_clipboard_entities_subtitle),
                        settings.clipboard.detectEntities,
                        info = stringResource(R.string.tooldetail_clipboard_entities_info),
                    ) { scope.launch { repository.setClipboardDetectEntities(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_clipboard_password_paste_title),
                        stringResource(R.string.tooldetail_clipboard_password_paste_subtitle),
                        settings.clipboard.clearAfterPasswordPaste,
                        info = stringResource(R.string.tooldetail_clipboard_password_paste_info),
                    ) { scope.launch { repository.setClipboardClearAfterPasswordPaste(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_clipboard_link_previews_title),
                        stringResource(R.string.tooldetail_clipboard_link_previews_subtitle),
                        settings.clipboard.linkPreviews,
                    ) { scope.launch { repository.setClipboardLinkPreviews(it) } }
                }
                item {
                    val context = LocalContext.current
                    ToggleSetting(
                        stringResource(R.string.tooldetail_clipboard_screenshots_title),
                        stringResource(R.string.tooldetail_clipboard_screenshots_subtitle),
                        settings.clipboard.userScreenshots,
                    ) { on ->
                        scope.launch { repository.setClipboardUserScreenshots(on) }
                        if (on && !hasImagesPermission(context)) {
                            runCatching {
                                context.startActivity(Intent(context, ImagesPermissionActivity::class.java))
                            }
                        }
                    }
                }
                // The guard sits outside item {} on purpose: an item whose body
                // draws nothing still gets its own card, which showed up as a
                // sliver of empty surface once the permission was granted.
                if (settings.clipboard.userScreenshots && !screenshotsGranted) {
                    item {
                        val context = LocalContext.current
                        NavRow(
                            stringResource(R.string.tooldetail_clipboard_storage_permission_title),
                            stringResource(R.string.tooldetail_clipboard_storage_permission_subtitle),
                        ) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    }
                }
                item {
                    val context = LocalContext.current
                    val usageAccess = rememberDisclosedSpecialAccess(SpecialAccess.USAGE)
                    ToggleSetting(
                        stringResource(R.string.tooldetail_clipboard_track_source_title),
                        stringResource(R.string.tooldetail_clipboard_track_source_subtitle),
                        settings.clipboard.trackSource,
                        info = stringResource(R.string.tooldetail_clipboard_track_source_info),
                    ) { on ->
                        scope.launch { repository.setClipboardTrackSource(on) }
                        // Disclosure then the grant screen, the first time they
                        // switch it on — but not when it is already granted, which
                        // is the common case for a toggle flipped off and on again.
                        if (on && !hasUsageAccess(context)) usageAccess()
                    }
                }
                if (settings.clipboard.trackSource && !usageAccessGranted) {
                    item {
                        val usageAccessRow = rememberDisclosedSpecialAccess(SpecialAccess.USAGE)
                        NavRow(
                            stringResource(R.string.tooldetail_clipboard_usage_permission_title),
                            stringResource(R.string.tooldetail_clipboard_usage_permission_subtitle),
                        ) { usageAccessRow() }
                    }
                }
            }
            SettingsGroup(stringResource(R.string.tooldetail_clipboard_sensitive_group)) {
                item {
                    ChoiceSetting(
                        title = stringResource(R.string.tooldetail_clipboard_sensitive_title),
                        subtitle = stringResource(R.string.tooldetail_clipboard_sensitive_subtitle),
                        info = stringResource(R.string.tooldetail_clipboard_sensitive_info),
                        options = SensitiveClipHandling.entries.map { it to stringResource(it.labelRes) },
                        selected = settings.clipboard.sensitiveHandling,
                    ) { scope.launch { repository.setClipboardSensitiveHandling(it) } }
                }
                if (settings.clipboard.sensitiveHandling != SensitiveClipHandling.KEEP) {
                    item {
                        ToggleSetting(
                            stringResource(R.string.tooldetail_clipboard_detect_sensitive_title),
                            stringResource(R.string.tooldetail_clipboard_detect_sensitive_subtitle),
                            settings.clipboard.detectSensitive,
                            info = stringResource(R.string.tooldetail_clipboard_detect_sensitive_info),
                        ) { scope.launch { repository.setClipboardDetectSensitive(it) } }
                    }
                }
                if (settings.clipboard.sensitiveHandling == SensitiveClipHandling.SHORT_LIVED) {
                    item {
                        SliderSetting(
                            stringResource(R.string.tooldetail_clipboard_sensitive_expiry_title),
                            subtitle = stringResource(
                                R.string.tooldetail_clipboard_sensitive_expiry_subtitle,
                            ),
                            value = settings.clipboard.sensitiveExpiryMinutes.toFloat(),
                            range = 1f..120f,
                            display = { minutesFormat.format(it.toInt()) },
                        ) {
                            scope.launch { repository.setClipboardSensitiveExpiryMinutes(it.toInt()) }
                        }
                    }
                }
            }
        }
        ToolbarTool.SPLIT -> SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
            item {
                SliderSetting(
                    stringResource(R.string.tooldetail_split_gap_title),
                    subtitle = stringResource(R.string.tooldetail_split_gap_subtitle),
                    value = settings.splitGapPercent.toFloat(),
                    range = 5f..40f,
                    display = { percentFormat.format(it.toInt()) },
                ) { scope.launch { repository.setSplitGapPercent(it.toInt()) } }
            }
            item {
                NavRow(
                    stringResource(R.string.tooldetail_layout_nav_title),
                    stringResource(R.string.tooldetail_layout_nav_split_subtitle),
                    onClick = { onNavigate("layout") },
                )
            }
        }
        ToolbarTool.FLOATING -> SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
            item {
                SliderSetting(
                    stringResource(R.string.tooldetail_floating_width_title),
                    subtitle = stringResource(R.string.tooldetail_floating_width_subtitle),
                    value = settings.floatingWidthDp.toFloat(),
                    range = 240f..500f,
                    display = { dpFormat.format(it.toInt()) },
                ) { scope.launch { repository.setFloatingWidthDp(it.toInt()) } }
            }
            item {
                NavRow(
                    stringResource(R.string.tooldetail_layout_nav_title),
                    stringResource(R.string.tooldetail_layout_nav_floating_subtitle),
                    onClick = { onNavigate("layout") },
                )
            }
        }
        ToolbarTool.FLASHLIGHT -> SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
            item {
                ToggleSetting(
                    stringResource(R.string.tooldetail_flashlight_auto_off_title),
                    stringResource(R.string.tooldetail_flashlight_auto_off_subtitle),
                    settings.flashlightAutoOff,
                    info = stringResource(R.string.tooldetail_flashlight_auto_off_info),
                ) { scope.launch { repository.setFlashlightAutoOff(it) } }
            }
        }
        ToolbarTool.COMPASS -> {
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_compass_degrees_title),
                        stringResource(R.string.tooldetail_compass_degrees_subtitle),
                        settings.compassShowDegrees,
                    ) { scope.launch { repository.setCompassShowDegrees(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_compass_qibla_title),
                        stringResource(R.string.tooldetail_compass_qibla_subtitle),
                        settings.compassShowQibla,
                        info = stringResource(R.string.tooldetail_compass_qibla_info),
                    ) { scope.launch { repository.setCompassShowQibla(it) } }
                }
            }
            if (settings.compassShowQibla && settings.weatherLatitude == null) {
                CaptionText(
                    stringResource(R.string.tooldetail_compass_no_location_error),
                    error = true,
                )
            }
        }
        ToolbarTool.LEVEL -> SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
            item {
                ToggleSetting(
                    stringResource(R.string.tooldetail_level_angles_title),
                    stringResource(R.string.tooldetail_level_angles_subtitle),
                    settings.levelShowAngles,
                ) { scope.launch { repository.setLevelShowAngles(it) } }
            }
        }
        ToolbarTool.UNDO, ToolbarTool.REDO ->
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_redo_ctrl_y_title),
                        stringResource(R.string.tooldetail_redo_ctrl_y_subtitle),
                        settings.redoUsesCtrlY,
                        info = stringResource(R.string.tooldetail_redo_ctrl_y_info),
                    ) { scope.launch { repository.setRedoUsesCtrlY(it) } }
                }
            }
        ToolbarTool.MOON_PHASE -> SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
            item {
                ToggleSetting(
                    stringResource(R.string.tooldetail_moon_southern_title),
                    stringResource(R.string.tooldetail_moon_southern_subtitle),
                    settings.moonSouthernHemisphere,
                ) { scope.launch { repository.setMoonSouthernHemisphere(it) } }
            }
        }
        ToolbarTool.WEATHER -> {
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item { WeatherLocationSetting(repository, settings) }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_weather_fahrenheit_title),
                        stringResource(R.string.tooldetail_weather_fahrenheit_subtitle),
                        settings.weatherFahrenheit,
                    ) { scope.launch { repository.setWeatherFahrenheit(it) } }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_weather_info))
        }
        ToolbarTool.CALENDAR -> {
            val showsHijri = settings.calendarAltOne == AltCalendar.HIJRI ||
                settings.calendarAltTwo == AltCalendar.HIJRI
            SettingsGroup(stringResource(R.string.tooldetail_calendar_group)) {
                item {
                    AltCalendarSetting(
                        title = stringResource(R.string.tooldetail_calendar_first_title),
                        subtitle = stringResource(R.string.tooldetail_calendar_first_subtitle),
                        selected = settings.calendarAltOne,
                        onChange = { scope.launch { repository.setCalendarAltOne(it) } },
                    )
                }
                item {
                    AltCalendarSetting(
                        title = stringResource(R.string.tooldetail_calendar_second_title),
                        subtitle = stringResource(R.string.tooldetail_calendar_second_subtitle),
                        selected = settings.calendarAltTwo,
                        onChange = { scope.launch { repository.setCalendarAltTwo(it) } },
                    )
                }
                item {
                    WeekendSetting(settings.calendarWeekend) {
                        scope.launch { repository.setCalendarWeekend(it) }
                    }
                }
                if (showsHijri) {
                    item {
                        SliderSetting(
                            stringResource(R.string.tooldetail_calendar_hijri_title),
                            subtitle = stringResource(R.string.tooldetail_calendar_hijri_subtitle),
                            value = settings.hijriAdjustDays.toFloat(),
                            range = -2f..2f,
                            display = { days ->
                                val d = days.roundToInt()
                                if (d > 0) daysAheadFormat.format(d) else daysFormat.format(d)
                            },
                            info = stringResource(R.string.tooldetail_calendar_hijri_info),
                        ) { scope.launch { repository.setHijriAdjustDays(it.roundToInt()) } }
                    }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_calendar_info))
            CaptionText(stringResource(R.string.tooldetail_calendar_events_info))
        }
        ToolbarTool.CAMERA -> {
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_camera_front_title),
                        stringResource(R.string.tooldetail_camera_front_subtitle),
                        settings.camera.preferFront,
                    ) { scope.launch { repository.setCameraPreferFront(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_camera_mirror_title),
                        stringResource(R.string.tooldetail_camera_mirror_subtitle),
                        settings.camera.mirrorFront,
                        info = stringResource(R.string.tooldetail_camera_mirror_info),
                    ) { scope.launch { repository.setCameraMirrorFront(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_camera_gallery_title),
                        stringResource(R.string.tooldetail_camera_gallery_subtitle),
                        settings.camera.saveToGallery,
                        info = stringResource(R.string.tooldetail_camera_gallery_info),
                    ) { scope.launch { repository.setCameraSaveToGallery(it) } }
                }
            }
            SettingsGroup(stringResource(R.string.tooldetail_camera_feedback_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_camera_shutter_title),
                        stringResource(R.string.tooldetail_camera_shutter_subtitle),
                        settings.camera.shutterSound,
                    ) { scope.launch { repository.setCameraShutterSound(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_camera_haptics_title),
                        stringResource(R.string.tooldetail_camera_haptics_subtitle),
                        settings.camera.haptics,
                        info = stringResource(R.string.tooldetail_camera_haptics_info),
                    ) { scope.launch { repository.setCameraHaptics(it) } }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_camera_info))
        }
        ToolbarTool.DICTIONARY -> {
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_dictionary_auto_title),
                        stringResource(R.string.tooldetail_dictionary_auto_subtitle),
                        settings.dictionaryAutoLookup,
                    ) { scope.launch { repository.setDictionaryAutoLookup(it) } }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_dictionary_info))
        }
        ToolbarTool.TEXT_EDIT -> SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
            item {
                SliderSetting(
                    stringResource(R.string.tooldetail_text_edit_repeat_title),
                    subtitle = stringResource(R.string.tooldetail_text_edit_repeat_subtitle),
                    value = settings.textEditing.repeatMs.toFloat(),
                    range = 30f..200f,
                    display = { msFormat.format(it.toInt()) },
                ) { scope.launch { repository.setTextEditRepeatMs(it.toInt()) } }
            }
        }
        ToolbarTool.NUMPAD -> SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
            item {
                ToggleSetting(
                    stringResource(R.string.tooldetail_numpad_calc_title),
                    stringResource(R.string.tooldetail_numpad_calc_subtitle),
                    settings.numpadCalculatorLayout,
                ) { scope.launch { repository.setNumpadCalculatorLayout(it) } }
            }
        }
        ToolbarTool.INCOGNITO -> {
            SettingsGroup(stringResource(R.string.tooldetail_incognito_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_incognito_learning_title),
                        stringResource(R.string.tooldetail_incognito_learning_subtitle),
                        settings.incognitoPausesLearning,
                    ) { scope.launch { repository.setIncognitoPausesLearning(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_incognito_clipboard_title),
                        stringResource(R.string.tooldetail_incognito_clipboard_subtitle),
                        settings.incognitoPausesClipboard,
                    ) { scope.launch { repository.setIncognitoPausesClipboard(it) } }
                }
            }
            SettingsGroup(stringResource(R.string.tooldetail_incognito_auto_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_incognito_auto_title),
                        stringResource(R.string.tooldetail_incognito_auto_subtitle),
                        settings.autoIncognito,
                        info = stringResource(AUTO_INCOGNITO_INFO),
                    ) { scope.launch { repository.setAutoIncognito(it) } }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_incognito_info))
        }
        ToolbarTool.POWER_SAVING -> {
            val ps = settings.powerSaving
            SettingsGroup(stringResource(R.string.tooldetail_power_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_power_now_title),
                        stringResource(R.string.tooldetail_power_now_subtitle),
                        ps.manual,
                        info = stringResource(R.string.tooldetail_power_now_info),
                    ) { scope.launch { repository.setPowerSavingManual(it) } }
                }
                item {
                    ChoiceSetting(
                        stringResource(R.string.tooldetail_power_trigger_title),
                        subtitle = stringResource(R.string.tooldetail_power_trigger_subtitle),
                        info = stringResource(R.string.tooldetail_power_trigger_info),
                        options = PowerSavingTrigger.entries.map { it to stringResource(it.labelRes) },
                        selected = ps.trigger,
                    ) { scope.launch { repository.setPowerSavingTrigger(it) } }
                }
                if (ps.trigger == PowerSavingTrigger.LOW_BATTERY ||
                    ps.trigger == PowerSavingTrigger.EITHER
                ) {
                    item {
                        SliderSetting(
                            stringResource(R.string.tooldetail_power_battery_title),
                            subtitle = stringResource(R.string.tooldetail_power_battery_subtitle),
                            value = ps.batteryPercent.toFloat(),
                            range = 5f..50f,
                            display = { percentFormat.format(it.toInt()) },
                        ) { scope.launch { repository.setPowerSavingBatteryPercent(it.toInt()) } }
                    }
                }
                if (ps.trigger != PowerSavingTrigger.OFF) {
                    item {
                        ToggleSetting(
                            stringResource(R.string.tooldetail_power_charging_title),
                            stringResource(R.string.tooldetail_power_charging_subtitle),
                            ps.offWhileCharging,
                            info = stringResource(R.string.tooldetail_power_charging_info),
                        ) { scope.launch { repository.setPowerSavingOffWhileCharging(it) } }
                    }
                }
            }
            SettingsGroup(stringResource(R.string.tooldetail_power_drop_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_power_drop_haptics_title),
                        stringResource(R.string.tooldetail_power_drop_haptics_subtitle),
                        ps.dropHaptics,
                    ) { scope.launch { repository.setPowerSavingDropHaptics(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_power_drop_sound_title),
                        stringResource(R.string.tooldetail_power_drop_sound_subtitle),
                        ps.dropKeySound,
                    ) { scope.launch { repository.setPowerSavingDropKeySound(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_power_drop_anim_title),
                        stringResource(R.string.tooldetail_power_drop_anim_subtitle),
                        ps.dropAnimations,
                    ) { scope.launch { repository.setPowerSavingDropAnimations(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_power_drop_trail_title),
                        stringResource(R.string.tooldetail_power_drop_trail_subtitle),
                        ps.dropGlideTrail,
                        info = stringResource(R.string.tooldetail_power_drop_trail_info),
                    ) { scope.launch { repository.setPowerSavingDropGlideTrail(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_power_drop_popup_title),
                        stringResource(R.string.tooldetail_power_drop_popup_subtitle),
                        ps.dropKeyPopup,
                    ) { scope.launch { repository.setPowerSavingDropKeyPopup(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_power_drop_glide_title),
                        stringResource(R.string.tooldetail_power_drop_glide_subtitle),
                        ps.dropGestureTyping,
                        info = stringResource(R.string.tooldetail_power_drop_glide_info),
                    ) { scope.launch { repository.setPowerSavingDropGestureTyping(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_power_drop_emoji_title),
                        stringResource(R.string.tooldetail_power_drop_emoji_subtitle),
                        ps.dropEmojiPrediction,
                    ) { scope.launch { repository.setPowerSavingDropEmojiPrediction(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_power_drop_chips_title),
                        stringResource(R.string.tooldetail_power_drop_chips_subtitle),
                        ps.dropSmartChips,
                    ) { scope.launch { repository.setPowerSavingDropSmartChips(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_power_drop_network_title),
                        stringResource(R.string.tooldetail_power_drop_network_subtitle),
                        ps.dropBackgroundNetwork,
                        info = stringResource(R.string.tooldetail_power_drop_network_info),
                    ) { scope.launch { repository.setPowerSavingDropBackgroundNetwork(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_power_drop_screenshot_title),
                        stringResource(R.string.tooldetail_power_drop_screenshot_subtitle),
                        ps.dropScreenshotWatch,
                    ) { scope.launch { repository.setPowerSavingDropScreenshotWatch(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_power_drop_models_title),
                        stringResource(R.string.tooldetail_power_drop_models_subtitle),
                        ps.dropOnDeviceModels,
                        info = stringResource(R.string.tooldetail_power_drop_models_info),
                    ) { scope.launch { repository.setPowerSavingDropOnDeviceModels(it) } }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_power_info))
        }
        ToolbarTool.AUTOCORRECT -> SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
            item {
                ToggleSetting(
                    stringResource(R.string.tooldetail_autocorrect_title),
                    stringResource(R.string.tooldetail_autocorrect_subtitle),
                    settings.autocorrect,
                ) { scope.launch { repository.setAutocorrect(it) } }
            }
            item {
                NavRow(
                    stringResource(R.string.tooldetail_typing_nav_title),
                    stringResource(R.string.tooldetail_typing_nav_subtitle),
                    onClick = { onNavigate("typing") },
                )
            }
        }
        ToolbarTool.SOUND_HAPTICS -> {
            KeySoundGroup(repository, settings) {
                item {
                    NavRow(
                        stringResource(R.string.tooldetail_keypress_nav_title),
                        stringResource(R.string.tooldetail_keypress_nav_subtitle),
                        onClick = { onNavigate("keypress") },
                    )
                }
            }
            CaptionText(stringResource(R.string.tooldetail_sound_haptics_info))
        }
        ToolbarTool.HANDWRITING -> {
            SettingsGroup(stringResource(R.string.tooldetail_handwriting_input_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_handwriting_stylus_title),
                        stringResource(R.string.tooldetail_handwriting_stylus_subtitle),
                        settings.handwritingStylusOnly,
                        info = stringResource(R.string.tooldetail_handwriting_stylus_info),
                    ) { scope.launch { repository.setHandwritingStylusOnly(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_handwriting_auto_space_title),
                        stringResource(R.string.tooldetail_handwriting_auto_space_subtitle),
                        settings.handwritingAutoSpace,
                    ) { scope.launch { repository.setHandwritingAutoSpace(it) } }
                }
                item {
                    SliderSetting(
                        stringResource(R.string.tooldetail_handwriting_pause_title),
                        subtitle = stringResource(R.string.tooldetail_handwriting_pause_subtitle),
                        value = settings.handwritingCommitDelayMs.toFloat(),
                        range = 300f..2000f,
                        display = { msFormat.format(it.roundToInt()) },
                        info = stringResource(R.string.tooldetail_handwriting_pause_info),
                    ) { scope.launch { repository.setHandwritingCommitDelayMs(it.roundToInt()) } }
                }
            }
            SectionHeader(stringResource(R.string.tooldetail_handwriting_models_header))
            CaptionText(stringResource(R.string.tooldetail_handwriting_models_info))
            HandwritingModelManager(settings)
            SettingsGroup {
                item {
                    NavRow(
                        stringResource(R.string.tooldetail_handwriting_languages_title),
                        stringResource(R.string.tooldetail_handwriting_languages_subtitle),
                        onClick = { onNavigate("languages") },
                    )
                }
            }
        }
        ToolbarTool.THEMES -> SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
            item {
                NavRow(
                    stringResource(R.string.tooldetail_themes_nav_title),
                    stringResource(R.string.tooldetail_themes_nav_subtitle),
                    onClick = { onNavigate("themes") },
                )
            }
        }
        ToolbarTool.ONE_HANDED -> SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
            item {
                NavRow(
                    stringResource(R.string.tooldetail_layout_nav_title),
                    stringResource(R.string.tooldetail_layout_nav_one_handed_subtitle),
                    onClick = { onNavigate("layout") },
                )
            }
        }
        ToolbarTool.TRANSLATE -> {
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item { TranslateLanguageSetting(repository, settings) }
            }
            SettingsGroup(stringResource(R.string.tooldetail_translate_key_group)) {
                item {
                    ApiKeyField(
                        label = stringResource(R.string.tooldetail_translate_key_label),
                        value = settings.translateApiKey,
                        builtInAvailable = ToolApiKeys.builtInTranslate,
                        emptyHint = stringResource(R.string.tooldetail_translate_key_hint),
                    ) { repository.setTranslateApiKey(it) }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_translate_info))
        }
        ToolbarTool.GIF, ToolbarTool.STICKER -> {
            if (tool == ToolbarTool.STICKER) {
                SettingsGroup(stringResource(R.string.tooldetail_sticker_packs_group)) {
                    item {
                        NavRow(
                            stringResource(R.string.tooldetail_sticker_packs_title),
                            stringResource(R.string.tooldetail_sticker_packs_subtitle),
                            route = "sticker_packs",
                            onClick = { onNavigate("sticker_packs") },
                        )
                    }
                }
            }
            SettingsGroup(stringResource(R.string.tooldetail_media_layout_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_media_full_bleed_title),
                        stringResource(R.string.tooldetail_media_full_bleed_subtitle),
                        settings.mediaFullBleed,
                        info = stringResource(R.string.tooldetail_media_full_bleed_info),
                    ) { scope.launch { repository.setMediaFullBleed(it) } }
                }
            }
            SettingsGroup(stringResource(R.string.tooldetail_media_keys_group)) {
                item {
                    ApiKeyField(
                        label = stringResource(R.string.tooldetail_media_klipy_label),
                        value = settings.klipyApiKey,
                        builtInAvailable = ToolApiKeys.builtInKlipy,
                        emptyHint = stringResource(R.string.tooldetail_media_klipy_hint),
                    ) { repository.setKlipyApiKey(it) }
                }
                item {
                    ApiKeyField(
                        label = stringResource(R.string.tooldetail_media_giphy_label),
                        value = settings.giphyApiKey,
                        builtInAvailable = ToolApiKeys.builtInGiphy,
                        emptyHint = stringResource(R.string.tooldetail_media_giphy_hint),
                    ) { repository.setGiphyApiKey(it) }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_media_info))
            // Resolved out here: the group builder lambda is not composable.
            val stickerOption = stringResource(R.string.tooldetail_media_send_sticker_option)
            val imageOption = stringResource(R.string.tooldetail_media_send_image_option)
            SettingsGroup(stringResource(R.string.tooldetail_media_sending_group)) {
                item {
                    ChoiceSetting(
                        title = stringResource(R.string.tooldetail_media_sticker_send_title),
                        subtitle = stringResource(R.string.tooldetail_media_sticker_send_subtitle),
                        info = stringResource(R.string.tooldetail_media_sticker_send_info),
                        options = listOf(
                            MediaSendMode.STICKER to stickerOption,
                            MediaSendMode.IMAGE to imageOption,
                        ),
                        selected = settings.stickerSendMode,
                    ) { scope.launch { repository.setStickerSendMode(it) } }
                }
                item {
                    ChoiceSetting(
                        title = stringResource(R.string.tooldetail_media_gif_send_title),
                        subtitle = stringResource(R.string.tooldetail_media_gif_send_subtitle),
                        info = stringResource(R.string.tooldetail_media_gif_send_info),
                        options = listOf(
                            MediaSendMode.IMAGE to imageOption,
                            MediaSendMode.STICKER to stickerOption,
                        ),
                        selected = settings.gifSendMode,
                    ) { scope.launch { repository.setGifSendMode(it) } }
                }
            }
            SectionHeader(stringResource(R.string.tooldetail_media_sources_header))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                GifSourceMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.gifSourceMode == mode,
                        onClick = { scope.launch { repository.setGifSourceMode(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, GifSourceMode.entries.size),
                    ) {
                        Text(
                            when (mode) {
                                GifSourceMode.TABS ->
                                    stringResource(R.string.tooldetail_media_source_tabs)
                                GifSourceMode.MIX ->
                                    stringResource(R.string.tooldetail_media_source_mixed)
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_media_sources_info))
            SectionHeader(stringResource(R.string.tooldetail_media_filter_header))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                GifContentFilter.entries.forEachIndexed { index, filter ->
                    SegmentedButton(
                        selected = settings.gifContentFilter == filter,
                        onClick = { scope.launch { repository.setGifContentFilter(filter) } },
                        shape = SegmentedButtonDefaults.itemShape(index, GifContentFilter.entries.size),
                    ) {
                        Text(
                            when (filter) {
                                GifContentFilter.OFF ->
                                    stringResource(CommonR.string.common_off)
                                GifContentFilter.LOW ->
                                    stringResource(R.string.tooldetail_media_filter_low)
                                GifContentFilter.MEDIUM ->
                                    stringResource(R.string.tooldetail_media_filter_medium)
                                GifContentFilter.HIGH ->
                                    stringResource(R.string.tooldetail_media_filter_high)
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_media_filter_info))
        }
        ToolbarTool.WEB_SEARCH, ToolbarTool.IMAGE_SEARCH -> {
            SettingsGroup(stringResource(R.string.tooldetail_search_group)) {
                item {
                    ApiKeyField(
                        label = stringResource(R.string.tooldetail_search_key_label),
                        value = settings.braveApiKey,
                        builtInAvailable = ToolApiKeys.builtInBrave,
                        emptyHint = stringResource(R.string.tooldetail_search_key_hint),
                    ) { repository.setBraveApiKey(it) }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_search_info))
            SettingsGroup(stringResource(R.string.tooldetail_search_results_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_search_safe_title),
                        stringResource(R.string.tooldetail_search_safe_subtitle),
                        settings.searchSafe,
                    ) { scope.launch { repository.setSearchSafe(it) } }
                }
                item {
                    SliderSetting(
                        stringResource(R.string.tooldetail_search_count_title),
                        subtitle = stringResource(R.string.tooldetail_search_count_subtitle),
                        value = settings.searchResultCount.toFloat(),
                        range = 1f..10f,
                        display = { numberFormat.format(it.roundToInt()) },
                    ) { scope.launch { repository.setSearchResultCount(it.roundToInt()) } }
                }
            }
        }
        ToolbarTool.OCR -> {
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_ocr_select_all_title),
                        stringResource(R.string.tooldetail_ocr_select_all_subtitle),
                        settings.ocrAutoSelectWords,
                    ) { scope.launch { repository.setOcrAutoSelectWords(it) } }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_ocr_info))
        }
        ToolbarTool.QR_SCAN -> {
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_qr_scan_auto_title),
                        stringResource(R.string.tooldetail_qr_scan_auto_subtitle),
                        settings.qrScanAutoInsert,
                    ) { scope.launch { repository.setQrScanAutoInsert(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_qr_scan_haptics_title),
                        stringResource(R.string.tooldetail_qr_scan_haptics_subtitle),
                        settings.qrScanHaptics,
                    ) { scope.launch { repository.setQrScanHaptics(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_qr_scan_preview_title),
                        stringResource(R.string.tooldetail_qr_scan_preview_subtitle),
                        settings.qrScanLinkPreviews,
                    ) { scope.launch { repository.setQrScanLinkPreviews(it) } }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_qr_scan_info))
        }
        ToolbarTool.DOC_SCAN -> {
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_doc_scan_gallery_title),
                        stringResource(R.string.tooldetail_doc_scan_gallery_subtitle),
                        settings.docScanSaveToGallery,
                    ) { scope.launch { repository.setDocScanSaveToGallery(it) } }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_doc_scan_info))
        }
        ToolbarTool.VOICE -> {
            val whisperEnabled = com.wasimaster.wmkeyboard.core.settings.isWhisperEnabled()
            val usingWhisper = whisperEnabled && settings.whisper.engine == "whisper"
            if (whisperEnabled) {
                val systemEngine = stringResource(R.string.tooldetail_voice_engine_system)
                val whisperEngine = stringResource(R.string.tooldetail_voice_engine_whisper)
                SettingsGroup(stringResource(R.string.tooldetail_voice_engine_group)) {
                    item {
                        ChoiceSetting(
                            stringResource(R.string.tooldetail_voice_engine_title),
                            subtitle = stringResource(R.string.tooldetail_voice_engine_subtitle),
                            info = stringResource(R.string.tooldetail_voice_engine_info),
                            options = listOf(
                                "system" to systemEngine,
                                "whisper" to whisperEngine,
                            ),
                            selected = settings.whisper.engine,
                        ) { scope.launch { repository.setVoiceEngine(it) } }
                    }
                }
            }
            SettingsGroup(stringResource(R.string.tooldetail_voice_dictation_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_voice_strip_title),
                        stringResource(R.string.tooldetail_voice_strip_subtitle),
                        settings.voiceStripMode,
                    ) { scope.launch { repository.setVoiceStripMode(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_voice_continuous_title),
                        stringResource(R.string.tooldetail_voice_continuous_subtitle),
                        settings.voiceContinuous,
                    ) { scope.launch { repository.setVoiceContinuous(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_voice_punctuation_title),
                        stringResource(R.string.tooldetail_voice_punctuation_subtitle),
                        settings.voiceSpokenPunctuation,
                    ) { scope.launch { repository.setVoiceSpokenPunctuation(it) } }
                }
            }
            if (usingWhisper) {
                SettingsGroup(stringResource(R.string.tooldetail_voice_offline_group)) {
                    item {
                        ToggleSetting(
                            stringResource(R.string.tooldetail_voice_translate_title),
                            stringResource(R.string.tooldetail_voice_translate_subtitle),
                            settings.whisper.translate,
                        ) { scope.launch { repository.setWhisperTranslate(it) } }
                    }
                }
                WhisperModelManager(repository, settings)
            } else {
                CaptionText(stringResource(R.string.tooldetail_voice_system_info))
            }
        }
        ToolbarTool.GRAMMAR -> {
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item {
                    ChoiceSetting(
                        stringResource(R.string.tooldetail_grammar_dialect_title),
                        subtitle = stringResource(R.string.tooldetail_grammar_dialect_subtitle),
                        options = GrammarDialect.entries.map { it to stringResource(it.labelRes) },
                        selected = settings.grammarDialect,
                    ) { scope.launch { repository.setGrammarDialect(it) } }
                }
                item {
                    SliderSetting(
                        stringResource(R.string.tooldetail_grammar_debounce_title),
                        subtitle = stringResource(R.string.tooldetail_grammar_debounce_subtitle),
                        value = settings.grammarDebounceMs.toFloat(),
                        range = 100f..1500f,
                        display = { msFormat.format(it.toInt()) },
                    ) { scope.launch { repository.setGrammarDebounceMs(it.toInt()) } }
                }
            }
            if (BuildConfig.ENABLE_GRAMMAR) {
                val context = LocalContext.current
                SettingsGroup(stringResource(R.string.tooldetail_grammar_system_group)) {
                    item {
                        NavRow(
                            stringResource(R.string.tooldetail_grammar_system_title),
                            stringResource(R.string.tooldetail_grammar_system_subtitle),
                            onClick = { openSpellCheckerSettings(context) },
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        item {
                            ToggleSetting(
                                stringResource(
                                    R.string.tooldetail_grammar_no_suggestions_title,
                                ),
                                stringResource(
                                    R.string.tooldetail_grammar_no_suggestions_subtitle,
                                ),
                                settings.spellCheckerNoSuggestions,
                            ) {
                                scope.launch {
                                    repository.setSpellCheckerNoSuggestions(it)
                                }
                            }
                        }
                    }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_grammar_info))
        }
        ToolbarTool.WIKIPEDIA -> {
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item {
                    TextFieldSetting(
                        label = stringResource(R.string.tooldetail_wiki_language_label),
                        value = settings.wikiLanguage,
                        hint = stringResource(R.string.tooldetail_wiki_language_hint),
                    ) { repository.setWikiLanguage(it) }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_wiki_markdown_title),
                        stringResource(R.string.tooldetail_wiki_markdown_subtitle),
                        settings.wikiLinksMarkdown,
                    ) { scope.launch { repository.setWikiLinksMarkdown(it) } }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_wiki_info))
        }
        ToolbarTool.SYMBOLS -> {
            SettingsGroup(stringResource(R.string.tooldetail_symbols_recents_group)) {
                item {
                    val remembered = settings.symbolRecents.size
                    WmRow(
                        title = stringResource(R.string.tooldetail_symbols_clear_title),
                        subtitle = if (remembered == 0) {
                            stringResource(R.string.tooldetail_symbols_clear_empty)
                        } else {
                            pluralStringResource(
                                R.plurals.tooldetail_symbols_remembered_count,
                                remembered,
                                remembered,
                            )
                        },
                        onClick = { scope.launch { repository.clearSymbolRecents() } },
                    )
                }
            }
            CaptionText(stringResource(R.string.tooldetail_symbols_info))
        }
        ToolbarTool.CALCULATOR -> SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
            item {
                ToggleSetting(
                    stringResource(R.string.tooldetail_calc_smart_title),
                    stringResource(R.string.tooldetail_calc_smart_subtitle),
                    settings.smartCalc,
                    info = stringResource(R.string.tooldetail_calc_smart_info),
                ) { scope.launch { repository.setSmartCalc(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.tooldetail_calc_degrees_title),
                    stringResource(R.string.tooldetail_calc_degrees_subtitle),
                    settings.calcDegrees,
                ) { scope.launch { repository.setCalcDegrees(it) } }
            }
            item {
                SliderSetting(
                    stringResource(R.string.tooldetail_calc_precision_title),
                    subtitle = stringResource(R.string.tooldetail_calc_precision_subtitle),
                    value = settings.calcPrecision.toFloat(),
                    range = 0f..12f,
                    display = { numberFormat.format(it.roundToInt()) },
                ) { scope.launch { repository.setCalcPrecision(it.roundToInt()) } }
            }
        }
        ToolbarTool.UNIT_CONVERT -> {
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_units_smart_title),
                        stringResource(R.string.tooldetail_units_smart_subtitle),
                        settings.smartUnits,
                        info = stringResource(R.string.tooldetail_units_smart_info),
                    ) { scope.launch { repository.setSmartUnits(it) } }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_units_info))
        }
        ToolbarTool.CURRENCY -> {
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_currency_smart_title),
                        stringResource(
                            R.string.tooldetail_currency_smart_subtitle,
                            settings.currencyTo,
                        ),
                        settings.smartCurrency,
                        info = stringResource(R.string.tooldetail_currency_smart_info),
                    ) { scope.launch { repository.setSmartCurrency(it) } }
                }
                item {
                    SliderSetting(
                        stringResource(R.string.tooldetail_currency_decimals_title),
                        subtitle = stringResource(R.string.tooldetail_currency_decimals_subtitle),
                        value = settings.currencyDecimals.toFloat(),
                        range = 0f..6f,
                        display = { numberFormat.format(it.toInt()) },
                    ) { scope.launch { repository.setCurrencyDecimals(it.toInt()) } }
                }
                item {
                    SliderSetting(
                        stringResource(R.string.tooldetail_currency_refresh_title),
                        subtitle = stringResource(R.string.tooldetail_currency_refresh_subtitle),
                        value = settings.currencyCacheHours.toFloat(),
                        range = 1f..48f,
                        display = { hoursFormat.format(it.toInt()) },
                        info = stringResource(R.string.tooldetail_currency_refresh_info),
                    ) { scope.launch { repository.setCurrencyCacheHours(it.toInt()) } }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_currency_info))
        }
        ToolbarTool.QR_GEN -> {
            val qrImageOption = stringResource(R.string.tooldetail_media_send_image_option)
            val qrStickerOption = stringResource(R.string.tooldetail_media_send_sticker_option)
            SettingsGroup(stringResource(R.string.tooldetail_options_group)) {
                item {
                    SliderSetting(
                        stringResource(R.string.tooldetail_qr_gen_size_title),
                        subtitle = stringResource(R.string.tooldetail_qr_gen_size_subtitle),
                        value = settings.qrSizePx.toFloat(),
                        range = 256f..2048f,
                        display = { pixelsFormat.format(it.roundToInt()) },
                    ) { scope.launch { repository.setQrSizePx(it.roundToInt()) } }
                }
                item {
                    ChoiceSetting(
                        title = stringResource(R.string.tooldetail_qr_gen_send_title),
                        subtitle = stringResource(R.string.tooldetail_qr_gen_send_subtitle),
                        info = stringResource(R.string.tooldetail_qr_gen_send_info),
                        options = listOf(
                            MediaSendMode.IMAGE to qrImageOption,
                            MediaSendMode.STICKER to qrStickerOption,
                        ),
                        selected = settings.qrSendMode,
                    ) { scope.launch { repository.setQrSendMode(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_qr_gen_gallery_title),
                        stringResource(R.string.tooldetail_qr_gen_gallery_subtitle),
                        settings.qrSaveToGallery,
                    ) { scope.launch { repository.setQrSaveToGallery(it) } }
                }
            }
            SectionHeader(stringResource(R.string.tooldetail_qr_gen_ecc_header))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                QrEccLevel.entries.forEachIndexed { index, level ->
                    SegmentedButton(
                        selected = settings.qrEcc == level,
                        onClick = { scope.launch { repository.setQrEcc(level) } },
                        shape = SegmentedButtonDefaults.itemShape(index, QrEccLevel.entries.size),
                    ) { Text(level.name, maxLines = 1) }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_qr_gen_ecc_info))
        }
        ToolbarTool.PASSWORD_GEN -> {
            SettingsGroup(stringResource(R.string.tooldetail_password_group)) {
                item {
                    SliderSetting(
                        stringResource(R.string.tooldetail_password_length_title),
                        value = settings.passwordGenerator.pwLength.toFloat(),
                        range = 4f..64f,
                        display = { numberFormat.format(it.roundToInt()) },
                    ) { scope.launch { repository.setPwLength(it.roundToInt()) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_password_uppercase_title),
                        stringResource(R.string.tooldetail_password_uppercase_subtitle),
                        settings.passwordGenerator.pwUppercase,
                    ) { scope.launch { repository.setPwUppercase(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_password_digits_title),
                        stringResource(R.string.tooldetail_password_digits_subtitle),
                        settings.passwordGenerator.pwDigits,
                    ) { scope.launch { repository.setPwDigits(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_password_symbols_title),
                        stringResource(R.string.tooldetail_password_symbols_subtitle),
                        settings.passwordGenerator.pwSymbols,
                    ) { scope.launch { repository.setPwSymbols(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_password_ambiguous_title),
                        stringResource(R.string.tooldetail_password_ambiguous_subtitle),
                        settings.passwordGenerator.pwExcludeAmbiguous,
                    ) { scope.launch { repository.setPwExcludeAmbiguous(it) } }
                }
            }
            SettingsGroup(stringResource(R.string.tooldetail_passphrase_group)) {
                item {
                    SliderSetting(
                        stringResource(R.string.tooldetail_passphrase_words_title),
                        value = settings.passwordGenerator.ppWordCount.toFloat(),
                        range = 2f..10f,
                        display = { numberFormat.format(it.roundToInt()) },
                    ) { scope.launch { repository.setPpWordCount(it.roundToInt()) } }
                }
                item {
                    TextFieldSetting(
                        label = stringResource(R.string.tooldetail_passphrase_separator_label),
                        value = settings.passwordGenerator.ppSeparator,
                        hint = stringResource(R.string.tooldetail_passphrase_separator_hint),
                    ) { repository.setPpSeparator(it) }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_passphrase_capitalize_title),
                        stringResource(R.string.tooldetail_passphrase_capitalize_subtitle),
                        settings.passwordGenerator.ppCapitalize,
                    ) { scope.launch { repository.setPpCapitalize(it) } }
                }
                item {
                    ToggleSetting(
                        stringResource(R.string.tooldetail_passphrase_digit_title),
                        stringResource(R.string.tooldetail_passphrase_digit_subtitle),
                        settings.passwordGenerator.ppIncludeDigit,
                    ) { scope.launch { repository.setPpIncludeDigit(it) } }
                }
            }
            CaptionText(stringResource(R.string.tooldetail_password_info))
        }
        ToolbarTool.TYPING_TEST -> TypingTestToolSettings(repository, settings)
        ToolbarTool.AI -> AiToolSettings(repository, settings)
        ToolbarTool.MODES -> SettingsGroup(stringResource(R.string.tooldetail_modes_group)) {
            item {
                NavRow(
                    stringResource(R.string.tooldetail_modes_edit_title),
                    stringResource(R.string.tooldetail_modes_edit_subtitle),
                    value = "${settings.keyboardModes.size}",
                ) { onNavigate("modes") }
            }
        }
        else -> {}
    }
}

/**
 * The typing test's settings. These are the same values the panel's own
 * chip row edits — this screen is the slower way round to them, plus the
 * records the panel only shows one config of at a time.
 */
@Composable
private fun TypingTestToolSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    // Slider readouts are plain lambdas, so their format strings are resolved
    // here and captured. The format also puts the number through the locale,
    // which is what gives Bengali or Arabic digits.
    val secondsFormat = stringResource(R.string.values_seconds)
    val numberFormat = stringResource(R.string.values_number)
    val bests = remember(settings.typingTestBests) { TypingBests.decode(settings.typingTestBests) }
    val history = remember(settings.typingTestHistory) {
        TypingHistory.decode(settings.typingTestHistory)
    }

    SectionHeader(stringResource(R.string.toolai_typing_default_test_title))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        for (mode in TypingTestMode.entries) {
            FilterChip(
                selected = settings.typingTestMode == mode,
                onClick = { scope.launch { repository.setTypingTestMode(mode) } },
                label = {
                    Text(
                        stringResource(
                            when (mode) {
                                TypingTestMode.TIME -> R.string.toolai_typing_mode_time_label
                                TypingTestMode.WORDS -> R.string.toolai_typing_mode_words_label
                                TypingTestMode.QUOTE -> R.string.toolai_typing_mode_quote_label
                            },
                        ),
                    )
                },
            )
        }
    }

    SettingsGroup(stringResource(R.string.toolai_typing_length_title)) {
        when (settings.typingTestMode) {
            TypingTestMode.TIME -> item {
                SliderSetting(
                    stringResource(R.string.toolai_typing_seconds_label),
                    value = settings.typingTestDuration.toFloat(),
                    range = 15f..120f,
                    display = { secondsFormat.format(it.roundToInt()) },
                ) { scope.launch { repository.setTypingTestDuration(it.roundToInt()) } }
            }
            TypingTestMode.WORDS -> item {
                SliderSetting(
                    stringResource(R.string.toolai_typing_words_label),
                    value = settings.typingTestWordCount.toFloat(),
                    range = 10f..100f,
                    display = { numberFormat.format(it.roundToInt()) },
                ) { scope.launch { repository.setTypingTestWordCount(it.roundToInt()) } }
            }
            // Quotes come at whatever length they were written.
            TypingTestMode.QUOTE -> item {
                CaptionText(stringResource(R.string.toolai_typing_quote_info))
            }
        }
    }

    if (settings.typingTestMode != TypingTestMode.QUOTE) {
        SettingsGroup(stringResource(R.string.toolai_typing_difficulty_title)) {
            item {
                ToggleSetting(
                    stringResource(R.string.toolai_typing_punctuation_title),
                    stringResource(R.string.toolai_typing_punctuation_subtitle),
                    settings.typingTestPunctuation,
                ) { scope.launch { repository.setTypingTestPunctuation(it) } }
            }
            item {
                ToggleSetting(
                    stringResource(R.string.toolai_typing_numbers_title),
                    stringResource(R.string.toolai_typing_numbers_subtitle),
                    settings.typingTestNumbers,
                ) { scope.launch { repository.setTypingTestNumbers(it) } }
            }
        }
    }

    SettingsGroup(stringResource(R.string.toolai_typing_records_title)) {
        item {
            WmRow(
                title = stringResource(R.string.toolai_typing_tests_completed_title),
                trailing = { Text("${settings.typingTestsCompleted}") },
            )
        }
        if (history.isNotEmpty()) {
            item {
                WmRow(
                    title = stringResource(R.string.toolai_typing_recent_average_title),
                    subtitle = pluralStringResource(
                        R.plurals.toolai_typing_recent_average_subtitle,
                        history.size,
                        history.size,
                    ),
                    trailing = {
                        Text(
                            stringResource(
                                R.string.toolai_typing_wpm_value,
                                history.average().roundToInt(),
                            ),
                        )
                    },
                )
            }
        }
        // One row per config the user has actually run, best first.
        for ((key, wpm) in bests.entries.sortedByDescending { it.value }) {
            item {
                WmRow(
                    title = typingBestLabel(key),
                    trailing = {
                        Text(stringResource(R.string.toolai_typing_wpm_value, wpm.roundToInt()))
                    },
                )
            }
        }
        if (bests.isNotEmpty() || settings.typingTestsCompleted > 0) {
            item {
                NavRow(
                    stringResource(R.string.toolai_typing_clear_records_title),
                    stringResource(R.string.toolai_typing_clear_records_subtitle),
                ) {
                    scope.launch { repository.clearTypingStats() }
                }
            }
        }
    }

    CaptionText(stringResource(R.string.toolai_typing_info))
}

/** Turns a stored best's key ("time30", "quote") back into a heading. */
@Composable
private fun typingBestLabel(key: String): String = when {
    key == "quote" -> stringResource(R.string.toolai_typing_mode_quote_label)
    key.startsWith("time") ->
        stringResource(R.string.toolai_typing_best_seconds_label, key.removePrefix("time"))
    key.startsWith("words") ->
        stringResource(R.string.toolai_typing_best_words_label, key.removePrefix("words"))
    else -> key
}

/** The AI tool's settings: provider, credentials, output and prompts. */
@Composable
private fun AiToolSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    // The slider readout is a plain lambda, so its format string is resolved
    // here and captured. The format also puts the number through the locale,
    // which is what gives Bengali or Arabic digits.
    val numberFormat = stringResource(R.string.values_number)
    SectionHeader(stringResource(R.string.toolai_ai_provider_title))
    // Six providers no longer fit a segmented row; chips wrap instead.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val providers = AiProvider.entries.filter {
            it != AiProvider.ON_DEVICE || BuildConfig.ENABLE_LOCAL_LLM
        }
        for (provider in providers) {
            FilterChip(
                selected = settings.aiProvider == provider,
                onClick = { scope.launch { repository.setAiProvider(provider) } },
                label = { Text(stringResource(provider.labelRes), maxLines = 1) },
            )
        }
    }
    when (settings.aiProvider) {
        AiProvider.ANTHROPIC -> SettingsGroup(
            stringResource(R.string.toolai_ai_anthropic_group_title),
        ) {
            item {
                ApiKeyField(
                    label = stringResource(R.string.toolai_ai_anthropic_key_label),
                    value = settings.aiAnthropicKey,
                    builtInAvailable = false,
                    emptyHint = stringResource(R.string.toolai_ai_anthropic_key_hint),
                ) { repository.setAiAnthropicKey(it) }
            }
            item {
                TextFieldSetting(
                    label = stringResource(R.string.toolai_ai_model_label),
                    value = settings.aiAnthropicModel,
                    hint = stringResource(
                        R.string.toolai_ai_model_hint,
                        AiClient.DefaultModels.ANTHROPIC,
                    ),
                ) { repository.setAiAnthropicModel(it) }
            }
        }
        AiProvider.OPENAI -> SettingsGroup(
            stringResource(R.string.toolai_ai_openai_group_title),
        ) {
            item {
                ApiKeyField(
                    label = stringResource(R.string.toolai_ai_openai_key_label),
                    value = settings.aiOpenAiKey,
                    builtInAvailable = false,
                    emptyHint = stringResource(R.string.toolai_ai_openai_key_hint),
                ) { repository.setAiOpenAiKey(it) }
            }
            item {
                TextFieldSetting(
                    label = stringResource(R.string.toolai_ai_model_label),
                    value = settings.aiOpenAiModel,
                    hint = stringResource(
                        R.string.toolai_ai_model_hint,
                        AiClient.DefaultModels.OPENAI,
                    ),
                ) { repository.setAiOpenAiModel(it) }
            }
        }
        AiProvider.GEMINI -> SettingsGroup(
            stringResource(R.string.toolai_ai_gemini_group_title),
        ) {
            item {
                ApiKeyField(
                    label = stringResource(R.string.toolai_ai_gemini_key_label),
                    value = settings.aiGeminiKey,
                    builtInAvailable = false,
                    emptyHint = stringResource(R.string.toolai_ai_gemini_key_hint),
                ) { repository.setAiGeminiKey(it) }
            }
            item {
                TextFieldSetting(
                    label = stringResource(R.string.toolai_ai_model_label),
                    value = settings.aiGeminiModel,
                    hint = stringResource(
                        R.string.toolai_ai_model_hint,
                        AiClient.DefaultModels.GEMINI,
                    ),
                ) { repository.setAiGeminiModel(it) }
            }
        }
        AiProvider.OLLAMA -> SettingsGroup(
            stringResource(R.string.toolai_ai_ollama_group_title),
        ) {
            item {
                TextFieldSetting(
                    label = stringResource(R.string.toolai_ai_server_address_label),
                    value = settings.aiOllamaUrl,
                    hint = stringResource(R.string.toolai_ai_ollama_url_hint),
                ) { repository.setAiOllamaUrl(it) }
            }
            item {
                TextFieldSetting(
                    label = stringResource(R.string.toolai_ai_model_label),
                    value = settings.aiOllamaModel,
                    hint = stringResource(
                        R.string.toolai_ai_model_hint,
                        AiClient.DefaultModels.OLLAMA,
                    ),
                ) { repository.setAiOllamaModel(it) }
            }
        }
        AiProvider.LM_STUDIO -> SettingsGroup(
            stringResource(R.string.toolai_ai_lm_studio_group_title),
        ) {
            item {
                TextFieldSetting(
                    label = stringResource(R.string.toolai_ai_server_address_label),
                    value = settings.aiLmStudioUrl,
                    hint = stringResource(R.string.toolai_ai_lm_studio_url_hint),
                ) { repository.setAiLmStudioUrl(it) }
            }
            item {
                TextFieldSetting(
                    label = stringResource(R.string.toolai_ai_model_label),
                    value = settings.aiLmStudioModel,
                    hint = stringResource(R.string.toolai_ai_lm_studio_model_hint),
                ) { repository.setAiLmStudioModel(it) }
            }
        }
        AiProvider.ON_DEVICE -> LocalLlmModelManager(repository, settings)
    }
    if (settings.aiProvider == AiProvider.OLLAMA || settings.aiProvider == AiProvider.LM_STUDIO) {
        CaptionText(stringResource(R.string.toolai_ai_local_server_info))
    }
    SettingsGroup(stringResource(R.string.toolai_ai_output_title)) {
        if (settings.aiProvider != AiProvider.ON_DEVICE) {
            item {
                SliderSetting(
                    stringResource(R.string.toolai_ai_max_tokens_title),
                    subtitle = stringResource(R.string.toolai_ai_max_tokens_subtitle),
                    value = settings.aiMaxTokens.toFloat(),
                    range = 256f..8192f,
                    display = { numberFormat.format(it.roundToInt()) },
                ) { scope.launch { repository.setAiMaxTokens(it.roundToInt()) } }
            }
        }
        item {
            TextFieldSetting(
                label = stringResource(R.string.toolai_ai_translate_to_label),
                value = settings.aiTranslateTo,
                hint = stringResource(R.string.toolai_ai_translate_to_hint),
            ) { repository.setAiTranslateTo(it) }
        }
        item {
            ToggleSetting(
                stringResource(R.string.toolai_ai_show_thinking_title),
                stringResource(R.string.toolai_ai_show_thinking_subtitle),
                settings.aiShowThinking,
            ) { scope.launch { repository.setAiShowThinking(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.toolai_ai_model_picker_title),
                stringResource(R.string.toolai_ai_model_picker_subtitle),
                settings.aiPanelModelPicker,
            ) { scope.launch { repository.setAiPanelModelPicker(it) } }
        }
    }
    SectionHeader(stringResource(R.string.toolai_ai_prompts_title))
    CaptionText(stringResource(R.string.toolai_ai_prompts_info))
    for (action in AiAction.entries) {
        // Custom has no stored prompt — its instruction is typed per run.
        if (action == AiAction.CUSTOM) continue
        val current = when (action) {
            AiAction.REWRITE -> settings.aiPromptRewrite
            AiAction.SUMMARIZE -> settings.aiPromptSummarize
            AiAction.TRANSLATE -> settings.aiPromptTranslate
            AiAction.IMPROVE -> settings.aiPromptImprove
            AiAction.FIX_GRAMMAR -> settings.aiPromptFixGrammar
            AiAction.EXPLAIN -> settings.aiPromptExplain
            AiAction.CONTINUE -> settings.aiPromptContinue
            AiAction.CUSTOM -> ""
        }
        val builtIn = AiPrompts.defaultPrompt(action, settings.aiTranslateTo)
        PromptFieldSetting(
            label = stringResource(action.labelRes),
            // Pre-filled with the built-in prompt so editing starts from the
            // real text instead of a blank field; saving identical text is a
            // no-op override.
            value = current.ifBlank { builtIn },
            defaultPrompt = builtIn,
        ) { repository.setAiPrompt(action, it) }
    }
    CaptionText(
        stringResource(
            if (settings.aiProvider == AiProvider.ON_DEVICE) {
                R.string.toolai_ai_on_device_info
            } else {
                R.string.toolai_ai_cloud_info
            },
        ),
    )
}

/**
 * The words that make this tool offer itself on the suggestion strip.
 * Only tools that ship a default get the row — a keyword for "Undo" would
 * fire on prose and there is nothing to open anyway.
 */
@Composable
private fun ToolKeywordSetting(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    tool: ToolbarTool,
) {
    val defaults = SmartSuggest.defaultKeywords[tool] ?: return
    val scope = rememberCoroutineScope()
    val saved = SmartSuggest.keywordsFor(tool, settings.toolKeywords)
    var text by remember(tool) { mutableStateOf(saved.joinToString(", ")) }
    SettingsGroup(stringResource(R.string.toolai_keyword_group_title)) {
        item {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    scope.launch { repository.setToolKeywords(tool, it.split(',')) }
                },
                label = { Text(stringResource(R.string.toolai_keyword_field_label)) },
                singleLine = true,
                supportingText = {
                    Text(
                        if (saved.isEmpty()) {
                            stringResource(R.string.toolai_keyword_empty_hint)
                        } else {
                            stringResource(
                                R.string.toolai_keyword_hint,
                                stringResource(toolTitle(tool)),
                            )
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (saved != defaults) {
            item {
                WmRow(
                    title = stringResource(CommonR.string.common_reset_defaults),
                    subtitle = defaults.joinToString(", "),
                    onClick = {
                        text = defaults.joinToString(", ")
                        scope.launch { repository.setToolKeywords(tool, defaults) }
                    },
                )
            }
        }
    }
    if (!settings.smartSuggestions || !settings.smartToolKeywords) {
        CaptionText(stringResource(R.string.toolai_keyword_off_info))
    }
}

/** A plain saved-as-you-type text setting (same mechanics as ApiKeyField). */
@Composable
internal fun TextFieldSetting(
    label: String,
    value: String,
    hint: String,
    onSave: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember(label) { mutableStateOf(value) }
    HighlightableRow(label) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                scope.launch { onSave(it) }
            },
            label = { Text(label) },
            singleLine = true,
            supportingText = { Text(hint) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/** Multi-line prompt override; the built-in prompt shows as the hint. */
@Composable
private fun PromptFieldSetting(
    label: String,
    value: String,
    defaultPrompt: String,
    onSave: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Re-init when the built-in prompt changes (e.g. the Translate target
    // language changed under us), so the field doesn't keep showing — and
    // mislabel as "Custom" — the previous language's prompt. Keyed on
    // defaultPrompt, not value: value echoes the user's own keystrokes back
    // asynchronously, and keying on it would drop fast typing.
    var text by remember(label, defaultPrompt) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            scope.launch { onSave(it) }
        },
        label = { Text(label) },
        minLines = 1,
        maxLines = 4,
        supportingText = {
            Text(
                when {
                    text.isBlank() -> defaultPrompt
                    text == defaultPrompt -> stringResource(R.string.toolai_prompt_builtin_hint)
                    else -> stringResource(R.string.toolai_prompt_custom_hint)
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * One API-key input. Saves as you type (it's a paste, in practice). The
 * user's key always beats any key baked into the build via
 * local.properties — leaving the field blank falls back to the built-in
 * key when the build has one.
 */
@Composable
internal fun ApiKeyField(
    label: String,
    value: String,
    builtInAvailable: Boolean,
    emptyHint: String,
    onSave: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember(label) { mutableStateOf(value) }
    HighlightableRow(label) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                scope.launch { onSave(it) }
            },
            label = { Text(label) },
            singleLine = true,
            supportingText = {
                Text(
                    when {
                        text.isNotBlank() -> stringResource(R.string.toolai_api_key_yours_hint)
                        builtInAvailable -> stringResource(R.string.toolai_api_key_builtin_hint)
                        else -> emptyHint
                    },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/**
 * One of the calendar tool's two alternate-calendar slots. A dialog rather
 * than a segmented row: nine choices never fit side by side, and the tool's
 * settings and the onboarding page both ask the same question.
 */
@Composable
internal fun AltCalendarSetting(
    title: String,
    subtitle: String,
    selected: AltCalendar,
    onChange: (AltCalendar) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    // The label of every calendar but NONE reads "English name · own name";
    // the row has room for the first half only. NONE's label is already short.
    NavRow(
        title,
        subtitle = subtitle,
        value = stringResource(selected.labelRes).substringBefore(" ·"),
        onClick = { dialogOpen = true },
    )
    if (dialogOpen) {
        val selectedDesc = stringResource(R.string.toolai_selected_desc)
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(title) },
            text = {
                LazyColumn {
                    items(AltCalendar.entries) { calendar ->
                        ListItem(
                            headlineContent = { Text(stringResource(calendar.labelRes)) },
                            trailingContent = if (calendar == selected) {
                                { Icon(Icons.Outlined.Check, contentDescription = selectedDesc) }
                            } else null,
                            modifier = Modifier.clickable {
                                dialogOpen = false
                                onChange(calendar)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResource(CommonR.string.common_close))
                }
            },
        )
    }
}

/**
 * The weekend picker, as a row plus dialog so it works both on the tool's
 * settings screen and in the onboarding wizard, which has no [SettingsGroup].
 */
@Composable
internal fun WeekendSetting(selected: Weekend, onChange: (Weekend) -> Unit) {
    var dialogOpen by remember { mutableStateOf(false) }
    NavRow(
        stringResource(R.string.toolai_weekend_title),
        subtitle = stringResource(R.string.toolai_weekend_subtitle),
        value = stringResource(selected.labelRes),
        onClick = { dialogOpen = true },
    )
    if (dialogOpen) {
        val selectedDesc = stringResource(R.string.toolai_selected_desc)
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(stringResource(R.string.toolai_weekend_title)) },
            text = {
                LazyColumn {
                    items(Weekend.entries) { weekend ->
                        ListItem(
                            headlineContent = { Text(stringResource(weekend.labelRes)) },
                            trailingContent = if (weekend == selected) {
                                { Icon(Icons.Outlined.Check, contentDescription = selectedDesc) }
                            } else null,
                            modifier = Modifier.clickable {
                                dialogOpen = false
                                onChange(weekend)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResource(CommonR.string.common_close))
                }
            },
        )
    }
}

/** "Translate into" row with a full-language-list dialog. */
@Composable
private fun TranslateLanguageSetting(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    var dialogOpen by remember { mutableStateOf(false) }
    NavRow(
        stringResource(R.string.toolai_translate_into_title),
        subtitle = stringResource(R.string.toolai_translate_into_subtitle),
        value = TranslateClient.languageName(settings.translateTargetLang),
        onClick = { dialogOpen = true },
    )
    if (dialogOpen) {
        val selectedDesc = stringResource(R.string.toolai_selected_desc)
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(stringResource(R.string.toolai_translate_into_title)) },
            text = {
                LazyColumn {
                    items(TranslateClient.languages) { (code, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            trailingContent = if (code == settings.translateTargetLang) {
                                { Icon(Icons.Outlined.Check, contentDescription = selectedDesc) }
                            } else null,
                            modifier = Modifier.clickable {
                                dialogOpen = false
                                scope.launch { repository.setTranslateTargetLang(code) }
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResource(CommonR.string.common_close))
                }
            },
        )
    }
}

/**
 * Open Android's Spell checker settings screen — where WM Keyboard's Harper
 * service can be picked as the system checker.
 *
 * There is no public [Settings] action for this screen, so we aim the direct
 * AOSP Settings component first and only fall back to the input-method
 * settings page (its parent) when that component is missing or hidden, as it
 * is on some OEM builds. Resolving before launching keeps a stock ROM that
 * renamed the activity from throwing an [android.content.ActivityNotFoundException].
 */
private fun openSpellCheckerSettings(context: Context) {
    val direct = Intent(Intent.ACTION_MAIN).setComponent(
        ComponentName(
            "com.android.settings",
            "com.android.settings.Settings\$SpellCheckersSettingsActivity",
        )
    )
    val resolves = context.packageManager.resolveActivity(direct, 0) != null
    val launched = resolves && runCatching { context.startActivity(direct) }.isSuccess
    if (!launched) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }
}

/**
 * Opens Android's subtype enabler for this keyboard — the screen where the user
 * ticks which of our registered languages the system switcher may list. Needed
 * on Android 13 and older, where an IME cannot enable its own subtypes and the
 * framework otherwise picks one from the phone's language list.
 *
 * The extra is what scopes the screen to us; without it (or on an OEM build
 * that dropped the activity) we fall back to the input-method settings page,
 * which is one tap away from the same place.
 */
private fun openSubtypeEnabler(context: Context) {
    val imeId = ComponentName(context, WMKeyboardService::class.java).flattenToShortString()
    val direct = Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS)
        .putExtra(Settings.EXTRA_INPUT_METHOD_ID, imeId)
    if (runCatching { context.startActivity(direct) }.isFailure) {
        runCatching { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
    }
}

/**
 * Download/delete state for the handwriting model of every language the user
 * types in — drawn from ML Kit's full ink catalogue, then narrowed to the
 * enabled languages so the list is only ever as long as it is useful. Status
 * is re-read from ML Kit's model manager after every action.
 */
@Composable
private fun HandwritingModelManager(settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val languages = remember(settings.enabledLanguages) {
        HandwritingModels.modelsFor(settings.enabledLanguages)
    }
    val missing = remember(settings.enabledLanguages, languages) {
        settings.enabledLanguages.distinctBy { it.id }
            .filter { HandwritingModels.tagFor(it) == null }
    }
    // tag -> "checking" | "missing" | "downloaded" | "downloading" | "error"
    val statuses = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(languages) {
        for (language in languages) {
            statuses[language.tag] =
                if (HandwritingModels.isDownloaded(language.tag)) "downloaded" else "missing"
        }
    }
    if (languages.isEmpty()) {
        CaptionText(stringResource(R.string.privacy_handwriting_none_info))
        return
    }
    SettingsGroup {
        for (language in languages) {
            item {
                val status = statuses[language.tag] ?: "checking"
                WmRow(
                    title = language.displayName,
                    subtitle = when (status) {
                            "checking" -> stringResource(R.string.privacy_handwriting_status_checking)
                            "downloaded" -> stringResource(R.string.privacy_handwriting_status_downloaded)
                            "downloading" -> stringResource(CommonR.string.common_downloading)
                            "error" -> stringResource(R.string.privacy_handwriting_status_failed)
                            else -> stringResource(R.string.privacy_handwriting_status_missing)
                        },
                    trailing = {
                        when (status) {
                            "downloading", "checking" -> CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                            "downloaded" -> IconButton(onClick = {
                                scope.launch {
                                    HandwritingModels.delete(language.tag)
                                    statuses[language.tag] =
                                        if (HandwritingModels.isDownloaded(language.tag)) "downloaded" else "missing"
                                }
                            }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(
                                        R.string.privacy_handwriting_delete_desc,
                                        language.displayName,
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            else -> TextButton(onClick = {
                                statuses[language.tag] = "downloading"
                                scope.launch {
                                    val ok = runCancellable { HandwritingModels.download(language.tag) }.isSuccess
                                    statuses[language.tag] = if (ok) "downloaded" else "error"
                                }
                            }) { Text(stringResource(CommonR.string.common_download)) }
                        }
                    },
                )
            }
        }
    }
    if (missing.isNotEmpty()) {
        CaptionText(
            stringResource(
                R.string.privacy_handwriting_missing_info,
                missing.joinToString(", ") { it.englishName },
            ),
        )
    }
}

/**
 * Weather location: place label plus coordinates, edited in a dialog. Shared
 * with the onboarding tool-setup page, which asks the same question.
 */
@Composable
internal fun WeatherLocationSetting(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    val unnamedPlace = stringResource(R.string.privacy_weather_place_unnamed)
    val savedLatitude = settings.weatherLatitude
    val savedLongitude = settings.weatherLongitude
    val summary = if (savedLatitude != null && savedLongitude != null) {
        stringResource(
            R.string.privacy_weather_location_summary,
            settings.weatherPlaceName.ifBlank { unnamedPlace },
            savedLatitude,
            savedLongitude,
        )
    } else {
        stringResource(R.string.privacy_weather_location_empty)
    }
    WmRow(
        title = stringResource(R.string.privacy_weather_location_title),
        subtitle = summary,
        trailing = {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.privacy_weather_edit_desc),
            )
        },
        onClick = { editing = true },
    )
    if (!editing) return

    var place by remember { mutableStateOf(settings.weatherPlaceName) }
    var lat by remember { mutableStateOf(settings.weatherLatitude?.toString().orEmpty()) }
    var lon by remember { mutableStateOf(settings.weatherLongitude?.toString().orEmpty()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    val parsedLat = lat.trim().toFloatOrNull()?.takeIf { it in -90f..90f }
    val parsedLon = lon.trim().toFloatOrNull()?.takeIf { it in -180f..180f }

    fun search() {
        if (query.isBlank() || searching) return
        searching = true
        searchFailed = false
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { WeatherClient.geocode(query) }.getOrNull()
            }
            searching = false
            if (found == null) {
                searchFailed = true
            } else {
                results = found
                searchFailed = found.isEmpty()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { editing = false },
        // Typing in the search/coordinate fields moves the dialog around the
        // keyboard, so a tap can land on the scrim where the dialog just was
        // and silently swallow the half-entered location. Explicit
        // Cancel/Save only; back still dismisses.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.privacy_weather_dialog_title)) },
        text = {
            val unknownRegion = stringResource(R.string.privacy_weather_region_unknown)
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.privacy_weather_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { search() }, enabled = query.isNotBlank() && !searching) {
                        Text(if (searching) "…" else stringResource(CommonR.string.common_search))
                    }
                }
                if (searchFailed) {
                    Text(
                        if (results.isEmpty() && !searching) {
                            stringResource(R.string.privacy_weather_no_matches_error)
                        } else {
                            stringResource(R.string.privacy_weather_search_error)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                for (result in results) {
                    ListItem(
                        headlineContent = { Text(result.name) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.privacy_weather_result_summary,
                                    result.region.ifBlank { unknownRegion },
                                    result.latitude,
                                    result.longitude,
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                place = result.name
                                lat = result.latitude.toString()
                                lon = result.longitude.toString()
                                results = emptyList()
                            },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.privacy_weather_manual_info),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text(stringResource(R.string.privacy_weather_name_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text(stringResource(R.string.privacy_weather_latitude_hint)) },
                    singleLine = true,
                    isError = lat.isNotBlank() && parsedLat == null,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it },
                    label = { Text(stringResource(R.string.privacy_weather_longitude_hint)) },
                    singleLine = true,
                    isError = lon.isNotBlank() && parsedLon == null,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsedLat != null && parsedLon != null,
                onClick = {
                    scope.launch {
                        repository.setWeatherLocation(parsedLat, parsedLon, place.trim())
                    }
                    editing = false
                },
            ) { Text(stringResource(CommonR.string.common_save)) }
        },
        dismissButton = {
            Row {
                if (settings.weatherLatitude != null) {
                    TextButton(onClick = {
                        scope.launch { repository.setWeatherLocation(null, null, "") }
                        editing = false
                    }) { Text(stringResource(CommonR.string.common_clear)) }
                }
                TextButton(onClick = { editing = false }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            }
        },
    )
}

// ---- privacy ----

/**
 * The explanation of "Follow private browsing". Two screens show it: the
 * Privacy screen below, and the incognito tool's own settings. It is a
 * resource id, not the text, so it is read where it is drawn.
 */
@StringRes
private val AUTO_INCOGNITO_INFO = R.string.privacy_auto_incognito_info

@Composable
private fun PrivacySettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    SettingsGroup(stringResource(R.string.privacy_learning_group_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.privacy_learn_typing_title),
                stringResource(R.string.privacy_learn_typing_subtitle),
                settings.learnFromTyping,
                info = stringResource(R.string.privacy_learn_typing_info),
            ) { scope.launch { repository.setLearnFromTyping(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.privacy_system_dictionary_title),
                stringResource(R.string.privacy_system_dictionary_subtitle),
                settings.addWordsToSystemDictionary,
                info = stringResource(R.string.privacy_system_dictionary_info),
            ) { scope.launch { repository.setAddWordsToSystemDictionary(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.privacy_dict_shortcuts_title),
                stringResource(R.string.privacy_dict_shortcuts_subtitle),
                settings.suggestionStrip.expandUserDictShortcuts,
                info = stringResource(R.string.privacy_dict_shortcuts_info),
            ) { scope.launch { repository.setExpandUserDictShortcuts(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.privacy_incognito_title),
                stringResource(R.string.privacy_incognito_subtitle),
                settings.incognito,
                info = stringResource(R.string.privacy_incognito_info),
            ) { scope.launch { repository.setIncognito(it) } }
        }
        item {
            ToggleSetting(
                stringResource(R.string.privacy_auto_incognito_title),
                stringResource(R.string.privacy_auto_incognito_subtitle),
                settings.autoIncognito,
                info = stringResource(AUTO_INCOGNITO_INFO),
            ) { scope.launch { repository.setAutoIncognito(it) } }
        }
    }
    SettingsGroup(stringResource(R.string.privacy_backup_group_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.privacy_backup_title),
                stringResource(R.string.privacy_backup_subtitle),
                settings.cloudBackup,
                info = stringResource(R.string.privacy_backup_info),
            ) { scope.launch { repository.setCloudBackup(it) } }
        }
    }
    SettingsGroup(stringResource(R.string.privacy_data_group_title)) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                OutlinedButton(onClick = {
                    java.io.File(context.filesDir, "learning/user_lexicon.json").delete()
                    java.io.File(context.filesDir, "learning/emoji_usage.json").delete()
                    // Chinese/Japanese/Cantonese picks live apart from the Latin
                    // lexicon, so clearing has to name them or they survive it.
                    java.io.File(context.filesDir, "learning/cjk_history.json").delete()
                    CjkLearning.store?.clear()
                }) { Text(stringResource(R.string.privacy_delete_learned_words_action)) }
            }
        }
    }
    CaptionText(stringResource(R.string.privacy_on_device_info))
}

// ---- snippets ----

@Composable
private fun SnippetSettings() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val file = remember { java.io.File(context.filesDir, "snippets/snippets.json") }
    // SnippetStore's constructor reads and JSON-parses the file, so it (and
    // every save) runs on Dispatchers.IO, not during composition or on a click.
    var store by remember { mutableStateOf<SnippetStore?>(null) }
    var snippets by remember { mutableStateOf<List<Snippet>>(emptyList()) }
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val s = withContext(Dispatchers.IO) { SnippetStore(file) }
        snippets = s.items()
        store = s
    }

    fun mutate(block: (SnippetStore) -> Unit) {
        val s = store ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                block(s)
                s.save()
            }
            snippets = s.items()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SnippetFile.MIME_TYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = snippets
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(
                            SnippetFile.encode(
                                current,
                                appVersion = BuildConfig.VERSION_CODE,
                                appVersionName = BuildConfig.VERSION_NAME,
                            ).toByteArray(),
                        )
                    } ?: error("no stream")
                }.isSuccess
            }
            message = if (ok) {
                context.resources.getQuantityString(
                    R.plurals.privacy_snippets_saved_count, current.size, current.size,
                )
            } else {
                context.getString(R.string.privacy_snippets_export_error)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val s = store ?: return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.requireInputStream(uri)
                        .use { SnippetFile.decode(it.readBytes().decodeToString()) }
                }.getOrNull()
            }
            message = if (imported == null) {
                context.getString(R.string.privacy_snippets_import_error)
            } else {
                // Added alongside what's already there, with fresh ids — an
                // import should never quietly replace snippets someone wrote.
                withContext(Dispatchers.IO) {
                    for (snippet in imported.snippets) {
                        s.add(snippet.label, snippet.text, snippet.trigger)
                    }
                    // add() is in-memory only; save() is what writes the file.
                    s.save()
                }
                snippets = s.items()
                buildString {
                    append(
                        context.resources.getQuantityString(
                            R.plurals.privacy_snippets_imported_count,
                            imported.snippets.size,
                            imported.snippets.size,
                        ),
                    )
                    if (imported.repairs.isNotEmpty()) {
                        append("\n\n")
                        append(context.getString(R.string.privacy_snippets_import_repairs_title))
                        // The reader hands back a resource and its arguments,
                        // so the note is worded here.
                        for (line in imported.repairs) append("\n• ${line.resolve(context)}")
                    }
                }
            }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text(stringResource(CommonR.string.common_ok))
                }
            },
        )
    }

    Text(
        stringResource(R.string.privacy_snippets_intro_info),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.privacy_snippets_variables_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            // Live examples: expand the actual templates so the preview always
            // matches what an insertion would produce right now. The variables
            // the IME alone can fill in get a stand-in example instead.
            for (variable in SnippetVariable.entries) {
                VariableRow(
                    variable.token,
                    stringResource(variable.descriptionRes),
                    sampleFor(variable),
                )
            }
            VariableRow(
                "{date:…}", stringResource(R.string.privacy_snippets_var_date_pattern_info),
                SnippetStore.expand("{date:EEE d MMM}"),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.privacy_snippets_variables_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { showAdd = true }) {
            Text(stringResource(R.string.privacy_snippets_add_action))
        }
        OutlinedButton(
            onClick = { importLauncher.launch(SnippetFile.IMPORT_MIME_TYPES) },
        ) { Text(stringResource(CommonR.string.common_import)) }
        OutlinedButton(
            onClick = { exportLauncher.launch(SnippetFile.fileName()) },
            enabled = snippets.isNotEmpty(),
        ) { Text(stringResource(CommonR.string.common_export)) }
    }
    Spacer(Modifier.height(12.dp))
    SettingsGroup {
        for (snippet in snippets) {
            item {
                WmRow(
                    title = snippet.label,
                    supporting = {
                        Column {
                            Text(snippet.text, maxLines = 2)
                            val preview = SnippetStore.expandWithCursor(
                                snippet.text,
                                context = SNIPPET_PREVIEW_CONTEXT,
                            ).text
                            if (snippet.text != preview) {
                                Text(
                                    stringResource(
                                        R.string.privacy_snippets_inserts_as_label,
                                        preview,
                                    ),
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            val trigger = snippet.trigger
                            if (trigger != null) {
                                Text(
                                    stringResource(
                                        R.string.privacy_snippets_trigger_label,
                                        trigger,
                                    ),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    trailing = {
                        Row {
                            IconButton(onClick = { editing = snippet }) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = stringResource(CommonR.string.common_edit),
                                )
                            }
                            IconButton(onClick = { mutate { it.remove(snippet.id) } }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(CommonR.string.common_delete),
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    if (showAdd || editing != null) {
        SnippetDialog(
            initial = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { label, text, trigger ->
                val current = editing
                mutate { s ->
                    if (current == null) s.add(label, text, trigger) else s.update(current.id, label, text, trigger)
                }
                showAdd = false
                editing = null
            },
        )
    }
}

/**
 * Stand-in values so the settings preview shows a realistic expansion.
 *
 * The getter is composable because one of the stand-ins is text the user
 * reads. Every read of this property has to sit in a composable body.
 */
private val SNIPPET_PREVIEW_CONTEXT: SnippetStore.Companion.Context
    @Composable get() = SnippetStore.Companion.Context(
        clipboard = "…",
        appName = stringResource(R.string.rows_snippet_preview_app_name),
        packageName = "com.example.app",
        selection = "…",
    )

/**
 * Example value for the reference card. Most variables can be expanded for
 * real; the ones that depend on the keyboard's live context (clipboard, app,
 * selection) get a description of what they'd produce instead.
 */
@Composable
private fun sampleFor(variable: SnippetVariable): String = when (variable) {
    SnippetVariable.CLIP -> stringResource(R.string.rows_snippet_sample_clip)
    SnippetVariable.SELECTION -> stringResource(R.string.rows_snippet_sample_selection)
    SnippetVariable.APP -> "Messages"
    SnippetVariable.PACKAGE -> "com.google.android.apps.messaging"
    SnippetVariable.CURSOR -> stringResource(R.string.rows_snippet_sample_cursor)
    else -> SnippetStore.expand(variable.token)
}

/** One row in the template-variable reference card. */
@Composable
private fun VariableRow(variable: String, meaning: String, example: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            variable,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(96.dp),
        )
        Column {
            Text(meaning, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.rows_snippet_variable_example, example),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SnippetDialog(
    initial: Snippet?,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var trigger by remember { mutableStateOf(initial?.trigger.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) {
                        R.string.rows_snippet_new_title
                    } else {
                        R.string.rows_snippet_edit_title
                    },
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.rows_snippet_label_label)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.rows_snippet_text_label)) },
                    minLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    label = { Text(stringResource(R.string.rows_snippet_trigger_label)) },
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.rows_snippet_trigger_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && text.isNotBlank(),
                onClick = { onSave(label.trim(), text, trigger.trim().ifBlank { null }) },
            ) { Text(stringResource(CommonR.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

// ---- rows & bars ----

@StringRes
private fun barRowTitle(row: BarRow): Int = when (row) {
    BarRow.TOPBAR -> R.string.rows_bar_topbar_title
    BarRow.EMOJI -> R.string.rows_bar_emoji_title
    BarRow.SYMBOL -> R.string.rows_symbol_row_title
}

@StringRes
private fun barRowSubtitle(row: BarRow, settings: KeyboardSettings): Int = when (row) {
    BarRow.TOPBAR -> R.string.rows_bar_topbar_subtitle
    BarRow.EMOJI -> when (settings.emojiBarMode) {
        EmojiBarMode.OFF -> R.string.rows_bar_emoji_off_subtitle
        EmojiBarMode.BUTTON -> R.string.rows_bar_emoji_button_subtitle
        EmojiBarMode.ALWAYS -> R.string.rows_bar_emoji_always_subtitle
    }
    BarRow.SYMBOL -> if (settings.symbolRowEnabled) {
        CommonR.string.common_on
    } else {
        CommonR.string.common_off
    }
}

/** Row layout above the keys: symbol row, row order and symbol sets. */
@Composable
private fun RowsSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    SettingsGroup(stringResource(R.string.rows_symbol_row_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.rows_symbol_row_title),
                stringResource(R.string.rows_symbol_row_subtitle),
                settings.symbolRowEnabled,
                info = stringResource(R.string.rows_symbol_row_info),
            ) { scope.launch { repository.setSymbolRowEnabled(it) } }
        }
    }
    SettingsGroup(stringResource(R.string.rows_row_order_title)) {
        val order = settings.barOrder
        order.forEachIndexed { index, row ->
            item {
                WmRow(
                    title = stringResource(barRowTitle(row)),
                    subtitle = stringResource(barRowSubtitle(row, settings)),
                    trailing = {
                        if (row != BarRow.TOPBAR) {
                            Row {
                                IconButton(
                                    enabled = index > 0,
                                    onClick = {
                                        val next = order.toMutableList()
                                        next[index] = next[index - 1].also { next[index - 1] = next[index] }
                                        scope.launch { repository.setBarOrder(next) }
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.ArrowUpward,
                                        contentDescription = stringResource(R.string.rows_move_up_desc),
                                    )
                                }
                                IconButton(
                                    enabled = index < order.lastIndex,
                                    onClick = {
                                        val next = order.toMutableList()
                                        next[index] = next[index + 1].also { next[index + 1] = next[index] }
                                        scope.launch { repository.setBarOrder(next) }
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.ArrowDownward,
                                        contentDescription = stringResource(R.string.rows_move_down_desc),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
    CaptionText(stringResource(R.string.rows_row_order_caption))
    SettingsGroup(stringResource(R.string.rows_symbol_sets_title)) {
        val allSets = resolveSymbolSets(settings.customSymbolSets)
        for (set in allSets) {
            item {
                val enabled = set.id in settings.symbolRowSetIds
                val edited = settings.customSymbolSets.any { it.id == set.id }
                val builtIn = BuiltInSymbolSets.byId(set.id) != null
                // A shipped set the user has not renamed draws its translated
                // name; anything the user named draws that name as typed.
                val shippedNameRes = BuiltInSymbolSets.nameRes(set)
                val setName = if (shippedNameRes != null) {
                    stringResource(shippedNameRes)
                } else {
                    set.name
                }
                WmRow(
                    title = setName,
                    supporting = {
                        Text(
                            set.chars.take(8).joinToString(" ") + if (set.chars.size > 8) " …" else "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leading = {
                        Checkbox(
                            checked = enabled,
                            onCheckedChange = { on ->
                                val next = if (on) {
                                    settings.symbolRowSetIds + set.id
                                } else {
                                    settings.symbolRowSetIds - set.id
                                }
                                // At least one set stays enabled — an empty row
                                // would have nothing to show.
                                if (next.isNotEmpty()) {
                                    scope.launch { repository.setSymbolRowSetIds(next) }
                                }
                            },
                        )
                    },
                    // Every set is editable now, built-ins included: editing
                    // one stores an override under the same id, so modes that
                    // reference it keep working and "Reset" brings it back.
                    trailing = {
                        IconButton(onClick = { onNavigate("symbol_set_edit/${set.id}") }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = stringResource(
                                    if (builtIn && !edited) {
                                        R.string.rows_symbol_set_edit_builtin_desc
                                    } else {
                                        R.string.rows_symbol_set_edit_desc
                                    },
                                ),
                            )
                        }
                    },
                )
            }
        }
        item {
            WmRow(
                title = stringResource(R.string.rows_symbol_set_new_title),
                subtitle = stringResource(R.string.rows_symbol_set_new_subtitle),
                leading = { Icon(Icons.Outlined.Add, contentDescription = null) },
                onClick = {
                    onNavigate("symbol_set_edit/custom_${System.currentTimeMillis()}")
                },
            )
        }
    }
    CaptionText(stringResource(R.string.rows_symbol_sets_caption))
}

/**
 * Create or edit one symbol set, built-ins included. Editing a built-in
 * saves an override stored under the same id, so anything referencing that
 * id (a mode's pinned sets, the row's active set) keeps pointing at it and
 * "Reset" simply drops the override to bring the shipped set back.
 */
@Composable
private fun SymbolSetEditor(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    setId: String,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val override = settings.customSymbolSets.firstOrNull { it.id == setId }
    val builtIn = BuiltInSymbolSets.byId(setId)
    val existing = override ?: builtIn
    var name by remember(setId) { mutableStateOf(existing?.name.orEmpty()) }
    var charsText by remember(setId) { mutableStateOf(existing?.chars?.joinToString(" ").orEmpty()) }
    if (builtIn != null) {
        // The stored English name is what a shipped set is keyed on, so only
        // the drawn name is resolved here. Nothing writes it back.
        val shippedNameRes = BuiltInSymbolSets.nameRes(builtIn)
        val shippedName = if (shippedNameRes != null) {
            stringResource(shippedNameRes)
        } else {
            builtIn.name
        }
        CaptionText(stringResource(R.string.rows_symbol_set_builtin_caption, shippedName))
    }
    val defaultSetName = stringResource(R.string.rows_symbol_set_default_name)
    SettingsGroup {
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.rows_symbol_set_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item {
            OutlinedTextField(
                value = charsText,
                onValueChange = { charsText = it },
                label = { Text(stringResource(R.string.rows_symbol_set_chars_label)) },
                supportingText = {
                    Text(stringResource(R.string.rows_symbol_set_chars_hint))
                },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Only an existing stored set can be removed — and for a built-in
        // that removal is a reset, not a delete.
        if (override != null) {
            TextButton(onClick = {
                scope.launch {
                    repository.deleteSymbolSet(setId)
                }
                onDone()
            }) {
                Icon(
                    if (builtIn != null) Icons.Outlined.Refresh else Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(
                        if (builtIn != null) {
                            R.string.rows_symbol_set_reset_action
                        } else {
                            R.string.rows_symbol_set_delete_action
                        },
                    ),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            enabled = charsText.isNotBlank(),
            onClick = {
                val chars = charsText.split(Regex("\\s+")).filter { it.isNotEmpty() }
                scope.launch {
                    repository.upsertSymbolSet(
                        SymbolSet(
                            setId,
                            name.trim().ifEmpty { builtIn?.name ?: defaultSetName },
                            chars,
                        ),
                    )
                    // A new set should show up in the row right away.
                    if (setId !in settings.symbolRowSetIds) {
                        repository.setSymbolRowSetIds(settings.symbolRowSetIds + setId)
                    }
                }
                onDone()
            },
        ) { Text(stringResource(CommonR.string.common_save)) }
    }
}

// ---- keyboard modes ----

/**
 * One-line recap of a mode's bindings for the list screen.
 *
 * The parts are joined, so nothing here may be re-cased afterwards: the first
 * letter of a translated word is not ours to change. The lower-case field
 * names are their own resources for the same reason.
 */
@Composable
private fun modeBindingsSummary(mode: KeyboardMode): String {
    val resources = LocalContext.current.resources
    val parts = mutableListOf<String>()
    if (mode.apps.isNotEmpty()) {
        parts += resources.getQuantityString(
            R.plurals.rows_mode_bindings_apps, mode.apps.size, mode.apps.size,
        )
    }
    if (mode.fieldKinds.isNotEmpty()) {
        parts += resources.getString(
            R.string.rows_mode_bindings_fields,
            mode.fieldKinds.joinToString(", ") {
                resources.getString(modeFieldLowercaseLabel(it))
            },
        )
    }
    // " + " rather than " · ": with both set, both have to match.
    return if (parts.isEmpty()) {
        resources.getString(R.string.rows_mode_bindings_manual)
    } else {
        resources.getString(R.string.rows_mode_bindings_auto, parts.joinToString(" + "))
    }
}

@Composable
private fun modeFieldLabel(field: ModeField): String = stringResource(
    when (field) {
        ModeField.PASSWORD -> R.string.rows_mode_field_password_label
        ModeField.EMAIL -> R.string.rows_mode_field_email_label
        ModeField.URL -> R.string.rows_mode_field_url_label
        ModeField.NUMBER -> R.string.rows_mode_field_number_label
        ModeField.PHONE -> R.string.rows_mode_field_phone_label
        ModeField.TEXT -> R.string.rows_mode_field_text_label
    },
)

/** The same names, written the way they read inside a sentence. */
@StringRes
private fun modeFieldLowercaseLabel(field: ModeField): Int = when (field) {
    ModeField.PASSWORD -> R.string.rows_mode_field_password_lowercase_label
    ModeField.EMAIL -> R.string.rows_mode_field_email_lowercase_label
    ModeField.URL -> R.string.rows_mode_field_url_lowercase_label
    ModeField.NUMBER -> R.string.rows_mode_field_number_lowercase_label
    ModeField.PHONE -> R.string.rows_mode_field_phone_lowercase_label
    ModeField.TEXT -> R.string.rows_mode_field_text_lowercase_label
}

/** Row height inside [ReorderDialog] — fixed, so drags map to index shifts. */
private val ReorderRowHeight = 52.dp

/**
 * Drags a list into the order the user wants. Rows carry a handle on the
 * right; dragging one past the next row's height swaps the two, so the item
 * tracks the finger and the list settles as it goes.
 *
 * The working copy only reaches the caller through [onConfirm] — backing out
 * leaves the stored order alone.
 */
@Composable
internal fun <T> ReorderDialog(
    title: String,
    items: List<T>,
    label: (T) -> String,
    onConfirm: (List<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember { mutableStateOf(items) }
    // -1 = nothing being dragged.
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { ReorderRowHeight.toPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CaptionText(stringResource(R.string.rows_reorder_caption))
                // Deliberately not a LazyColumn: every row has to stay
                // composed for a drag to swap past it, and these lists are
                // short enough that laying them all out is free.
                working.forEachIndexed { index, item ->
                    val dragging = index == dragIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ReorderRowHeight)
                            // The dragged row rides above its neighbours.
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                    ) {
                        Text(
                            stringResource(R.string.rows_reorder_position_label, index + 1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                        Text(
                            label(item),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Outlined.DragHandle,
                            contentDescription = stringResource(
                                R.string.rows_reorder_handle_desc, label(item),
                            ),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(28.dp)
                                // Keyed on Unit so a swap mid-drag never
                                // restarts the gesture: slot `index` is fixed
                                // for the life of the row, only the item in it
                                // moves. `dragIndex` is the live position.
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragIndex = index
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            dragIndex = -1
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            dragIndex = -1
                                            dragOffset = 0f
                                        },
                                    ) { change, drag ->
                                        change.consume()
                                        dragOffset += drag.y
                                        val from = dragIndex
                                        val to = from + (dragOffset / rowPx).roundToInt()
                                        if (from >= 0 && to != from && to in working.indices) {
                                            working = working.toMutableList().apply {
                                                add(to, removeAt(from))
                                            }
                                            dragIndex = to
                                            // Keep the offset relative to the
                                            // row's new home, or the item
                                            // would jump a full row.
                                            dragOffset -= (to - from) * rowPx
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(working) }) {
                Text(stringResource(CommonR.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

/**
 * A "Reorder…" row that opens a [ReorderDialog]. Disabled with a nudge when
 * there is nothing to reorder yet.
 */
@Composable
internal fun <T> ReorderSetting(
    title: String,
    dialogTitle: String,
    items: List<T>,
    label: (T) -> String,
    onReordered: (List<T>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val enabled = items.size > 1
    WmRow(
        title = title,
        subtitle = if (enabled) {
                items.joinToString(" · ", limit = 4) { label(it) }
            } else {
                stringResource(R.string.rows_reorder_empty_subtitle)
            },
        trailing = { Icon(Icons.Outlined.DragHandle, contentDescription = null) },
        enabled = enabled,
        onClick = { open = true },
    )
    if (open) {
        ReorderDialog(
            title = dialogTitle,
            items = items,
            label = label,
            onConfirm = {
                open = false
                onReordered(it)
            },
            onDismiss = { open = false },
        )
    }
}

/** A wrapping row of tool chips, used for a mode's pins and toolbox order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolChips(
    tools: List<ToolbarTool>,
    selected: List<ToolbarTool>,
    onToggle: (ToolbarTool) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (tool in tools) {
            FilterChip(
                selected = tool in selected,
                onClick = { onToggle(tool) },
                label = { Text(stringResource(toolTitle(tool)), maxLines = 1) },
            )
        }
    }
}

/** The modes list: tap to edit, plus creating a new mode. */
@Composable
private fun ModesSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    CaptionText(stringResource(R.string.modes_intro_body))
    val deleteModeDesc = stringResource(R.string.modes_delete_action)
    SettingsGroup(stringResource(R.string.modes_group_title)) {
        for (mode in settings.keyboardModes) {
            item {
                WmRow(
                    title = mode.name,
                    subtitle = modeBindingsSummary(mode),
                    leading = {
                        Icon(ModeIcons.icon(mode.icon), contentDescription = null)
                    },
                    trailing = {
                        IconButton(onClick = {
                            scope.launch { repository.deleteKeyboardMode(mode.id) }
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = deleteModeDesc)
                        }
                    },
                    onClick = { onNavigate("mode_edit/${mode.id}") },
                )
            }
        }
        item {
            WmRow(
                title = stringResource(R.string.modes_new_title),
                leading = { Icon(Icons.Outlined.Add, contentDescription = null) },
                onClick = { onNavigate("mode_edit/mode_custom_${System.currentTimeMillis()}") },
            )
        }
    }
    SettingsGroup(stringResource(R.string.modes_rearrange_group_title)) {
        item {
            ToggleSetting(
                stringResource(R.string.modes_drag_edits_title),
                stringResource(R.string.modes_drag_edits_subtitle),
                settings.modeToolOrderEdits,
                info = stringResource(R.string.modes_drag_edits_info),
            ) { scope.launch { repository.setModeToolOrderEdits(it) } }
        }
    }
    CaptionText(stringResource(R.string.modes_tool_order_body))
}

/** Everything one mode overrides, and when it activates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeEditor(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    modeId: String,
    onDeleted: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // Resolved up here: the name lands in stored settings from a plain lambda,
    // which is no place for stringResource().
    val newModeName = stringResource(R.string.modes_new_default_name)
    val unnamedModeName = stringResource(R.string.modes_unnamed_name)
    val mode = settings.keyboardModes.firstOrNull { it.id == modeId }
        ?: KeyboardMode(modeId, newModeName)
    // A brand-new mode is only persisted on its first edit — backing out of
    // an untouched editor leaves nothing behind.
    val save: (KeyboardMode) -> Unit = { scope.launch { repository.upsertKeyboardMode(it) } }
    // Only the shipped modes can be reset — a user-made mode has no default to
    // fall back to. Matched by id so an edited built-in still offers it.
    val builtInDefault = DefaultKeyboardModes.firstOrNull { it.id == modeId }
    var confirmReset by remember { mutableStateOf(false) }

    SettingsGroup {
        item {
            TextFieldSetting(
                label = stringResource(R.string.modes_name_label),
                value = mode.name,
                hint = stringResource(R.string.modes_name_hint),
            ) {
                repository.upsertKeyboardMode(
                    mode.copy(name = it.trim().ifEmpty { unnamedModeName }),
                )
            }
        }
        item {
            var pickerOpen by remember { mutableStateOf(false) }
            WmRow(
                title = stringResource(R.string.modes_icon_title),
                subtitle = stringResource(R.string.modes_icon_subtitle),
                leading = {
                    Icon(ModeIcons.icon(mode.icon), contentDescription = null)
                },
                onClick = { pickerOpen = true },
            )
            if (pickerOpen) {
                ModeIconPickerDialog(
                    selected = mode.icon,
                    onPick = { id ->
                        pickerOpen = false
                        save(mode.copy(icon = id))
                    },
                    onDismiss = { pickerOpen = false },
                )
            }
        }
    }
    SettingsGroup(stringResource(R.string.modes_changes_group_title)) {
        item {
            ChoiceSetting(
                title = stringResource(R.string.modes_emoji_row_title),
                subtitle = stringResource(R.string.modes_active_subtitle),
                options = listOf(
                    null to stringResource(R.string.modes_inherit_label),
                    EmojiBarMode.OFF to stringResource(CommonR.string.common_off),
                    EmojiBarMode.BUTTON to stringResource(R.string.modes_emoji_row_button_label),
                    EmojiBarMode.ALWAYS to stringResource(R.string.modes_emoji_row_row_label),
                ),
                selected = mode.emojiBarMode,
            ) { save(mode.copy(emojiBarMode = it)) }
        }
        item {
            ChoiceSetting(
                title = stringResource(R.string.modes_symbol_row_title),
                options = listOf(
                    null to stringResource(R.string.modes_inherit_label),
                    true to stringResource(CommonR.string.common_on),
                    false to stringResource(CommonR.string.common_off),
                ),
                selected = mode.symbolRowEnabled,
            ) { save(mode.copy(symbolRowEnabled = it)) }
        }
        item {
            var themePickerOpen by remember { mutableStateOf(false) }
            WmRow(
                title = stringResource(R.string.modes_theme_title),
                subtitle = mode.themeId?.let { themeDisplayName(settings, it) }
                    ?: stringResource(R.string.modes_theme_inherit_subtitle),
                trailing = {
                    if (mode.themeId != null) {
                        TextButton(onClick = { save(mode.copy(themeId = null)) }) {
                            Text(stringResource(CommonR.string.common_clear))
                        }
                    }
                },
                onClick = { themePickerOpen = true },
            )
            if (themePickerOpen) {
                ModeThemePickerDialog(
                    settings = settings,
                    selectedId = mode.themeId,
                    onPick = { id ->
                        themePickerOpen = false
                        save(mode.copy(themeId = id))
                    },
                    onDismiss = { themePickerOpen = false },
                )
            }
        }
        if (mode.themeId != null) {
            item {
                CaptionText(stringResource(R.string.modes_theme_override_body))
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.modes_pinned_tools_title),
                stringResource(R.string.modes_pinned_tools_subtitle),
                mode.toolbarTools != null,
            ) { on ->
                save(
                    mode.copy(
                        // Appending starts from nothing (the user's own pins
                        // are already there); replacing starts from a copy of
                        // the current toolbar to edit down.
                        toolbarTools = if (on) {
                            if (mode.toolbarToolsAppend) emptyList() else settings.toolbarTools
                        } else {
                            null
                        },
                    ),
                )
            }
        }
        val pinned = mode.toolbarTools
        if (pinned != null) {
            item {
                ChoiceSetting(
                    title = stringResource(R.string.modes_pinned_behaviour_title),
                    subtitle = if (mode.toolbarToolsAppend) {
                        stringResource(R.string.modes_pinned_behaviour_append_subtitle)
                    } else {
                        stringResource(R.string.modes_pinned_behaviour_replace_subtitle)
                    },
                    options = listOf(
                        true to stringResource(R.string.modes_pinned_behaviour_append_label),
                        false to stringResource(R.string.modes_pinned_behaviour_replace_label),
                    ),
                    selected = mode.toolbarToolsAppend,
                ) { append ->
                    // Switching to append: the copied-in global pins would
                    // duplicate what is already on the toolbar, so drop them.
                    save(
                        mode.copy(
                            toolbarToolsAppend = append,
                            toolbarTools = if (append) pinned - settings.toolbarTools.toSet() else pinned,
                        ),
                    )
                }
            }
            item {
                ToolChips(
                    tools = ToolbarTool.entries
                        .filter { it in settings.enabledTools && isSupportedTool(it) },
                    selected = pinned,
                ) { tool ->
                    save(
                        mode.copy(
                            toolbarTools = if (tool in pinned) pinned - tool else pinned + tool,
                        ),
                    )
                }
            }
            item {
                // toolTitle() hands back a resource id, and the reorder dialog
                // takes a plain (T) -> String, so the names are resolved here.
                val toolNames = mutableMapOf<ToolbarTool, String>()
                for (tool in pinned) {
                    toolNames[tool] = stringResource(toolTitle(tool))
                }
                ReorderSetting(
                    title = stringResource(R.string.modes_pinned_order_title),
                    dialogTitle = stringResource(R.string.modes_pinned_order_dialog_title),
                    items = pinned,
                    label = { toolNames[it].orEmpty() },
                ) { save(mode.copy(toolbarTools = it)) }
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.modes_toolbox_order_title),
                stringResource(R.string.modes_toolbox_order_subtitle),
                mode.toolboxOrder != null,
            ) { on ->
                save(mode.copy(toolboxOrder = if (on) emptyList() else null))
            }
        }
        val order = mode.toolboxOrder
        if (order != null) {
            item {
                ToolChips(
                    tools = settings.toolboxOrder.filter {
                        it in settings.enabledTools && isSupportedTool(it)
                    },
                    selected = order,
                ) { tool ->
                    save(
                        mode.copy(
                            toolboxOrder = if (tool in order) order - tool else order + tool,
                        ),
                    )
                }
            }
            item {
                val toolNames = mutableMapOf<ToolbarTool, String>()
                for (tool in order) {
                    toolNames[tool] = stringResource(toolTitle(tool))
                }
                ReorderSetting(
                    title = stringResource(R.string.modes_toolbox_order_reorder_title),
                    dialogTitle = stringResource(R.string.modes_toolbox_order_dialog_title),
                    items = order,
                    label = { toolNames[it].orEmpty() },
                ) { save(mode.copy(toolboxOrder = it)) }
            }
            item {
                CaptionText(stringResource(R.string.modes_toolbox_order_body))
            }
        }
        item {
            ToggleSetting(
                stringResource(R.string.modes_symbol_sets_title),
                stringResource(R.string.modes_symbol_sets_subtitle),
                mode.symbolSetIds != null,
            ) { on ->
                save(
                    mode.copy(
                        symbolSetIds = if (on) {
                            settings.symbolRowSetIds.ifEmpty { BuiltInSymbolSets.defaultEnabledIds }
                        } else {
                            null
                        },
                    ),
                )
            }
        }
        val modeSets = mode.symbolSetIds
        if (modeSets != null) {
            item {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (set in resolveSymbolSets(settings.customSymbolSets)) {
                        // A shipped set that still carries its shipped name is
                        // drawn from resources; a renamed one keeps the name
                        // the user typed.
                        val setLabel = BuiltInSymbolSets.nameRes(set)
                            ?.let { stringResource(it) } ?: set.name
                        FilterChip(
                            selected = set.id in modeSets,
                            onClick = {
                                val next =
                                    if (set.id in modeSets) modeSets - set.id else modeSets + set.id
                                if (next.isNotEmpty()) save(mode.copy(symbolSetIds = next))
                            },
                            label = { Text(setLabel, maxLines = 1) },
                        )
                    }
                }
            }
            item {
                val setNames = mutableMapOf<String, String>()
                for (set in resolveSymbolSets(settings.customSymbolSets)) {
                    setNames[set.id] = BuiltInSymbolSets.nameRes(set)
                        ?.let { stringResource(it) } ?: set.name
                }
                val setName = { id: String -> setNames[id] ?: id }
                ReorderSetting(
                    title = stringResource(R.string.modes_symbol_set_order_title),
                    dialogTitle = stringResource(R.string.modes_symbol_set_order_dialog_title),
                    items = modeSets,
                    label = setName,
                ) { save(mode.copy(symbolSetIds = it)) }
            }
            item {
                CaptionText(stringResource(R.string.modes_symbol_set_order_body))
            }
        }
    }
    SettingsGroup(stringResource(R.string.modes_auto_group_title)) {
        item {
            Text(
                stringResource(R.string.modes_field_types_title),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (field in ModeField.entries) {
                    FilterChip(
                        selected = field in mode.fieldKinds,
                        onClick = {
                            save(
                                mode.copy(
                                    fieldKinds =
                                        if (field in mode.fieldKinds) mode.fieldKinds - field
                                        else mode.fieldKinds + field,
                                ),
                            )
                        },
                        label = { Text(modeFieldLabel(field), maxLines = 1) },
                    )
                }
            }
            if (mode.apps.isNotEmpty() && mode.fieldKinds.isNotEmpty()) {
                CaptionText(stringResource(R.string.modes_auto_both_match_body))
            }
        }
        for (pkg in mode.apps) {
            item {
                val context = LocalContext.current
                val label = remember(pkg) {
                    runCatching {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(pkg, 0),
                        ).toString()
                    }.getOrDefault(pkg)
                }
                WmRow(
                    title = label,
                    supporting = if (label != pkg) {
                        { Text(pkg) }
                    } else null,
                    trailing = {
                        IconButton(onClick = { save(mode.copy(apps = mode.apps - pkg)) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.modes_app_remove_desc),
                            )
                        }
                    },
                )
            }
        }
        item {
            var pickerOpen by remember { mutableStateOf(false) }
            WmRow(
                title = stringResource(R.string.modes_add_app_title),
                subtitle = if (mode.fieldKinds.isEmpty()) {
                        stringResource(R.string.modes_add_app_subtitle_any)
                    } else {
                        stringResource(R.string.modes_add_app_subtitle_fields)
                    },
                leading = { Icon(Icons.Outlined.Add, contentDescription = null) },
                onClick = { pickerOpen = true },
            )
            if (pickerOpen) {
                AppPickerDialog(
                    exclude = mode.apps,
                    onPick = { pkg ->
                        pickerOpen = false
                        save(mode.copy(apps = mode.apps + pkg))
                    },
                    onDismiss = { pickerOpen = false },
                )
            }
        }
    }
    CaptionText(stringResource(R.string.modes_matching_body))
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (builtInDefault != null) {
            TextButton(onClick = { confirmReset = true }) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.modes_reset_default_action))
            }
            Spacer(Modifier.width(8.dp))
        }
        TextButton(onClick = {
            scope.launch { repository.deleteKeyboardMode(modeId) }
            onDeleted()
        }) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.modes_delete_action))
        }
    }
    if (confirmReset && builtInDefault != null) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = {
                Text(stringResource(R.string.modes_reset_confirm_title, builtInDefault.name))
            },
            text = { Text(stringResource(R.string.modes_reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { repository.resetKeyboardModeToDefault(modeId) }
                }) { Text(stringResource(CommonR.string.common_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            },
        )
    }
}

/**
 * Picks a mode's icon from [ModeIcons.catalog]. Chips rather than a grid of
 * bare icons: the selected state comes styled and the touch targets land on
 * the same size the rest of the settings use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeIconPickerDialog(
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modes_icon_picker_title)) },
        text = {
            FlowRow(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for ((id, vector) in ModeIcons.catalog) {
                    FilterChip(
                        selected = id == selected,
                        onClick = { onPick(id) },
                        label = {
                            Icon(vector, contentDescription = id, modifier = Modifier.size(22.dp))
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}

/** Picks one installed app (launcher activities) for a mode binding. */
@Composable
private fun AppPickerDialog(
    exclude: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }
    val shown = apps.filter { (pkg, label) ->
        pkg !in exclude &&
            (query.isBlank() || label.contains(query, ignoreCase = true) ||
                pkg.contains(query, ignoreCase = true))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modes_app_picker_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(CommonR.string.common_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.first }) { (pkg, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            supportingContent = { Text(pkg) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(pkg) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        },
    )
}
