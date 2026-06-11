package com.wasimaster.wmkeyboard.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.OpenableColumns
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.os.Build
import com.wasimaster.wmkeyboard.core.feedback.HapticPlayer
import com.wasimaster.wmkeyboard.core.handwriting.HandwritingModels
import com.wasimaster.wmkeyboard.core.settings.EmojiBarContent
import com.wasimaster.wmkeyboard.core.settings.EmojiBarMode
import com.wasimaster.wmkeyboard.core.settings.EmojiFontChoice
import com.wasimaster.wmkeyboard.ime.ui.KeyboardFonts
import com.wasimaster.wmkeyboard.core.settings.EmojiInsertMode
import com.wasimaster.wmkeyboard.core.settings.EmojiTabMode
import com.wasimaster.wmkeyboard.core.settings.HapticStyle
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.settings.KeySoundStyle
import com.wasimaster.wmkeyboard.core.tools.GeoPlace
import com.wasimaster.wmkeyboard.core.tools.WeatherClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wasimaster.wmkeyboard.core.settings.KeyboardAlignment
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.OneHandedMode
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.SpaceSwipeAction
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import kotlin.math.roundToInt
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.snippets.Snippet
import com.wasimaster.wmkeyboard.core.snippets.SnippetStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings app: setup wizard plus every keyboard option, Material 3 +
 * dynamic color, all state backed by DataStore via [SettingsRepository].
 */
class MainActivity : ComponentActivity() {

    private lateinit var repository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SettingsRepository(applicationContext)
        setContent {
            // Null until DataStore's first emission: rendering nothing for a
            // frame beats flashing onboarding at users who finished it.
            val settings by repository.settings
                .collectAsStateWithLifecycle(null as KeyboardSettings?)
            settings?.let { loaded ->
                AppTheme(loaded) {
                    SettingsNavHost(repository, loaded)
                }
            }
        }
    }
}

@Composable
private fun AppTheme(settings: KeyboardSettings, content: @Composable () -> Unit) {
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
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun SettingsNavHost(repository: SettingsRepository, settings: KeyboardSettings) {
    val navController = rememberNavController()
    // Quick shared-axis slide instead of the sluggish default cross-fade.
    val spec = tween<androidx.compose.ui.unit.IntOffset>(220)
    val fadeSpec = tween<Float>(220)
    // Frozen at first composition: completing onboarding navigates away
    // explicitly, it must not yank the graph out from under the NavHost.
    val startDestination = remember { if (settings.onboardingDone) "home" else "onboarding" }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(spec) { it / 5 } + fadeIn(fadeSpec) },
        exitTransition = { slideOutHorizontally(spec) { -it / 5 } + fadeOut(fadeSpec) },
        popEnterTransition = { slideInHorizontally(spec) { -it / 5 } + fadeIn(fadeSpec) },
        popExitTransition = { slideOutHorizontally(spec) { it / 5 } + fadeOut(fadeSpec) },
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
                onNavigate = { navController.navigate(it) },
            )
        }
        composable("typing") {
            SettingsScreen("Typing", { navController.popBackStack() }) {
                TypingSettings(repository, settings) { navController.navigate("dictionary") }
            }
        }
        composable("keypress") {
            SettingsScreen("Key press", { navController.popBackStack() }) {
                KeyPressSettings(repository, settings)
            }
        }
        composable("dictionary") {
            SettingsScreen("Personal dictionary", { navController.popBackStack() }) {
                DictionarySettings(repository)
            }
        }
        composable("appearance") {
            SettingsScreen("Appearance", { navController.popBackStack() }) {
                AppearanceSettings(
                    repository, settings,
                    onOpenThemes = { navController.navigate("themes") },
                    onOpenFonts = { navController.navigate("fonts") },
                )
            }
        }
        composable("layout") {
            SettingsScreen("Layout & size", { navController.popBackStack() }) {
                LayoutSettings(repository, settings)
            }
        }
        composable("fonts") {
            SettingsScreen("Keyboard font", { navController.popBackStack() }) {
                FontSettings(repository, settings)
            }
        }
        composable("themes") {
            SettingsScreen("Keyboard themes", { navController.popBackStack() }) {
                ThemesScreen(repository, settings) { id -> navController.navigate("theme_edit/$id") }
            }
        }
        composable("theme_edit/{themeId}") { backStackEntry ->
            val themeId = backStackEntry.arguments?.getString("themeId").orEmpty()
            SettingsScreen("Edit theme", { navController.popBackStack() }) {
                ThemeEditorScreen(repository, settings, themeId)
            }
        }
        composable("languages") {
            SettingsScreen("Languages", { navController.popBackStack() }) {
                LanguageSettings(repository, settings)
            }
        }
        composable("emoji") {
            SettingsScreen("Emoji", { navController.popBackStack() }) {
                EmojiSettings(repository, settings)
            }
        }
        composable("tools") {
            SettingsScreen("Tools", { navController.popBackStack() }) {
                ToolsSettings(settings) { tool -> navController.navigate("tool/${tool.name}") }
            }
        }
        composable("tool/{toolName}") { backStackEntry ->
            val tool = backStackEntry.arguments?.getString("toolName")
                ?.let { runCatching { ToolbarTool.valueOf(it) }.getOrNull() }
            if (tool != null) {
                SettingsScreen(toolTitle(tool), { navController.popBackStack() }) {
                    ToolDetailSettings(repository, settings, tool) {
                        navController.navigate("emoji")
                    }
                }
            }
        }
        composable("privacy") {
            SettingsScreen("Privacy", { navController.popBackStack() }) {
                PrivacySettings(repository, settings)
            }
        }
    }
}

// ---- home / setup ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(settings: KeyboardSettings, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(title = { Text("WM Keyboard") }, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SetupCard(context)
            }
            Spacer(Modifier.height(8.dp))
            SettingsGroup("Typing") {
                item {
                    HomeItem(
                        Icons.Outlined.Keyboard, "Typing",
                        "Autocorrect, suggestions, gestures",
                    ) { onNavigate("typing") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.TouchApp, "Key press",
                        "Haptics, key popup, long-press shortcuts",
                    ) { onNavigate("keypress") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.Language, "Languages",
                        "English, বাংলা (Avro phonetic, প্রভাত, জাতীয়)",
                    ) { onNavigate("languages") }
                }
            }
            SettingsGroup("Keyboard") {
                item {
                    HomeItem(
                        Icons.Outlined.Palette, "Appearance",
                        "Themes, fonts, toolbar style",
                    ) { onNavigate("appearance") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.AspectRatio, "Layout & size",
                        "Key size, number row, one-handed, split & floating",
                    ) { onNavigate("layout") }
                }
            }
            SettingsGroup("Features") {
                item {
                    HomeItem(
                        Icons.Outlined.EmojiEmotions, "Emoji",
                        "Suggestions, emoji row, emoji style, favourites",
                    ) { onNavigate("emoji") }
                }
                item {
                    HomeItem(
                        Icons.Outlined.Widgets, "Tools",
                        "Flashlight, compass, snippets, calendar & more",
                    ) { onNavigate("tools") }
                }
            }
            SettingsGroup("Data") {
                item {
                    HomeItem(
                        Icons.Outlined.Security, "Privacy",
                        "On-device learning, incognito",
                    ) { onNavigate("privacy") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun SetupCard(context: Context) {
    val imm = remember { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    // The IME picker is a system dialog, so the activity never pauses or
    // resumes when the user switches keyboards — poll while visible to
    // keep the card's state honest.
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            refresh++
            delay(1000)
        }
    }
    val enabled = remember(refresh) {
        imm.enabledInputMethodList.any { it.packageName == context.packageName }
    }
    val selected = remember(refresh) {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/') == context.packageName
    }

    if (enabled && selected) {
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
                    "WM Keyboard is your active keyboard.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Setup", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (enabled) "WM Keyboard is enabled. Use the button below to switch to it."
                else "Enable WM Keyboard in system settings, then select it as your input method.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                if (!enabled) {
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    }) { Text("Enable keyboard") }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(onClick = { imm.showInputMethodPicker() }) {
                    Text("Switch keyboard")
                }
            }
        }
    }
}

@Composable
private fun HomeItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        colors = transparentListColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

// ---- shared scaffold & group card system ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Collects the rows of one visually grouped card stack — the modern
 * Material settings look: each row sits on its own surface with tiny
 * gaps between rows, large rounded corners at the group's ends and
 * small ones inside.
 */
private class SettingsGroupScope {
    val items = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

@Composable
private fun SettingsGroup(
    title: String? = null,
    builder: SettingsGroupScope.() -> Unit,
) {
    // The builder runs during composition, so rows may be added
    // conditionally on snapshot state (e.g. sliders that appear only
    // while their feature's toggle is on).
    val scope = SettingsGroupScope().apply(builder)
    if (scope.items.isEmpty()) return
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

/** ListItem colors that let the group card's surface show through. */
@Composable
private fun transparentListColors(): ListItemColors =
    ListItemDefaults.colors(containerColor = Color.Transparent)

/** "?" affordance that opens a dialog with the full explanation of a setting. */
@Composable
private fun InfoButton(title: String, detail: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = "More about $title",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(detail) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
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
private fun CaptionText(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
    )
}

/** A navigation row: title, optional subtitle, optional current value, chevron. */
@Composable
private fun NavRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
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
        colors = transparentListColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun ToggleSetting(
    title: String,
    subtitle: String?,
    checked: Boolean,
    info: String? = null,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info != null) InfoButton(title, info)
                Switch(checked = checked, onCheckedChange = onChange)
            }
        },
        colors = transparentListColors(),
    )
}

@Composable
private fun SliderSetting(
    title: String,
    subtitle: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    info: String? = null,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (info != null) InfoButton(title, info)
            Spacer(Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.labelLarge)
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

/** A titled single-choice row of segmented buttons over [options]. */
@Composable
private fun <T> ChoiceSetting(
    title: String,
    subtitle: String? = null,
    info: String? = null,
    options: List<Pair<T, String>>,
    selected: T,
    onChange: (T) -> Unit,
) {
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

/** One spacebar-swipe slot (quick or hold+swipe): nothing / language / cursor. */
@Composable
private fun SpaceSwipeSetting(
    title: String,
    subtitle: String,
    info: String,
    value: SpaceSwipeAction,
    onChange: (SpaceSwipeAction) -> Unit,
) {
    ChoiceSetting(
        title = title,
        subtitle = subtitle,
        info = info,
        options = SpaceSwipeAction.entries.map { action ->
            action to when (action) {
                SpaceSwipeAction.NONE -> "Nothing"
                SpaceSwipeAction.LANGUAGE -> "Language"
                SpaceSwipeAction.CURSOR -> "Cursor"
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
) {
    val scope = rememberCoroutineScope()
    SettingsGroup("Automatic corrections") {
        item {
            ToggleSetting(
                "Autocorrect", "Fix typos automatically when you press space", settings.autocorrect,
                info = "When you press space, the word you just typed is checked against the " +
                    "dictionary. If it looks like a slip of an obviously more common word, it is " +
                    "replaced. Words you have taught the keyboard are never \"corrected\" away, " +
                    "and autocorrect stays off in password fields.",
            ) { scope.launch { repository.setAutocorrect(it) } }
        }
        item {
            ToggleSetting(
                "Fix missing apostrophes", "arent → aren't, im → I'm, dont → don't",
                settings.autoApostrophe,
                info = "When you press space after a contraction typed without its " +
                    "apostrophe (arent, isnt, youre, oclock…), the apostrophe is put " +
                    "back — and a lone \"i\" becomes \"I\". Words that are also real " +
                    "English words without the apostrophe (its, well, ill, shell…) are " +
                    "deliberately left alone. Works independently of autocorrect.",
            ) { scope.launch { repository.setAutoApostrophe(it) } }
        }
        item {
            ToggleSetting(
                "Auto-capitalize", "Capitalize the first letter of sentences", settings.autoCapitalize,
                info = "Shift turns on by itself at the start of a text field and after " +
                    "sentence-ending punctuation (. ! ? ।). It only applies in fields that ask " +
                    "for sentence capitalization, and only in English mode. Caps lock is never " +
                    "changed automatically.",
            ) { scope.launch { repository.setAutoCapitalize(it) } }
        }
        item {
            ToggleSetting(
                "Double-space period", "Double-tapping space inserts “. ”", settings.doubleSpacePeriod,
                info = "Tapping space twice quickly at the end of a word replaces the first " +
                    "space with a period, so you can end sentences without visiting the symbols " +
                    "layout.",
            ) { scope.launch { repository.setDoubleSpacePeriod(it) } }
        }
    }

    SettingsGroup("Suggestions") {
        item {
            ToggleSetting(
                "Suggestions", "Show word predictions above the keyboard", settings.suggestions,
                info = "Shows up to three candidates above the keys while you type: completions, " +
                    "corrections, and next-word predictions learned from your typing. Tap one to " +
                    "insert it followed by a space.",
            ) { scope.launch { repository.setSuggestions(it) } }
        }
        item {
            NavRow(
                "Personal dictionary",
                "Words the keyboard has learned — review, remove, add your own",
                onClick = onOpenDictionary,
            )
        }
    }

    SettingsGroup("Gestures") {
        item {
            ToggleSetting(
                "Gesture typing", "Swipe across letters to type a word", settings.gestureTyping,
                info = "Slide your finger from letter to letter without lifting; the word is " +
                    "committed when you lift. Alternate interpretations appear in the suggestion " +
                    "bar, so a wrong guess is one tap away from fixed. English only for now.",
            ) { scope.launch { repository.setGestureTyping(it) } }
        }
        item {
            SpaceSwipeSetting(
                title = "Quick swipe on spacebar",
                subtitle = "A swipe that starts moving right away",
                info = "Slide horizontally on the spacebar without pausing. \"Language\" cycles " +
                    "your enabled input modes with a live preview above the spacebar — release " +
                    "to switch. \"Cursor\" moves the text cursor one character per step. A tap " +
                    "without movement always types a space.",
                value = settings.spaceShortSwipe,
            ) { scope.launch { repository.setSpaceShortSwipe(it) } }
        }
        item {
            SpaceSwipeSetting(
                title = "Hold + swipe on spacebar",
                subtitle = "Hold the spacebar briefly, then swipe",
                info = "Hold the spacebar past the long-press delay first, then slide. This " +
                    "gives the spacebar a second, independent swipe action — for example a " +
                    "quick swipe to switch language and a hold + swipe to move the cursor. " +
                    "Both may also be set to the same action.",
                value = settings.spaceLongSwipe,
            ) { scope.launch { repository.setSpaceLongSwipe(it) } }
        }
    }
}

// ---- key press ----

@Composable
private fun KeyPressSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    SettingsGroup("Haptic feedback") {
        item {
            ToggleSetting(
                "Key press haptics", "Vibrate on every key press", settings.hapticFeedback,
                info = "A short vibration confirms each key press, including spacebar cursor " +
                    "movement steps. Style and strength are adjustable below.",
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
                Text("Haptic style", style = MaterialTheme.typography.bodyLarge)
                InfoButton(
                    "Haptic style",
                    "Click and Heavy click use the device's hardware-tuned haptic effects " +
                        "(Android 10+). Sharp plays the hardware click primitive (Android 11+) — " +
                        "a short, hard thump whose strength follows the intensity slider; " +
                        "this is how stock keyboards get a powerful buzz that stays crisp. " +
                        "Custom drives the vibration motor directly using the duration and " +
                        "intensity sliders — without the hardware's overdrive and braking it " +
                        "feels softer. On devices without these effects, styles fall back to " +
                        "Click, then Custom.",
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)) {
                HapticStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = settings.hapticStyle == style,
                        onClick = {
                            scope.launch { repository.setHapticStyle(style) }
                            // Fire the motor with the freshly picked style so the
                            // user feels the choice immediately.
                            HapticPlayer.preview(
                                context, style, settings.hapticAmplitude, settings.hapticStrengthMs,
                            )
                        },
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
        if (settings.hapticStyle == HapticStyle.CUSTOM) {
            item {
                SliderSetting(
                    "Haptic strength",
                    subtitle = "Vibration length per key press",
                    value = settings.hapticStrengthMs.toFloat(),
                    range = 5f..60f,
                    display = "${settings.hapticStrengthMs} ms",
                    info = "Duration of the vibration pulse in milliseconds. Longer pulses feel " +
                        "stronger on most phones.",
                ) {
                    scope.launch { repository.setHapticStrengthMs(it.toInt()) }
                    // Debounced inside the player, so dragging previews smoothly.
                    HapticPlayer.preview(context, settings.hapticStyle, settings.hapticAmplitude, it.toInt())
                }
            }
        }
        if (settings.hapticStyle == HapticStyle.CUSTOM || settings.hapticStyle == HapticStyle.SHARP) {
            item {
                SliderSetting(
                    "Haptic intensity",
                    subtitle = "Vibration amplitude per key press",
                    value = settings.hapticAmplitude.toFloat(),
                    range = 1f..255f,
                    display = "${settings.hapticAmplitude * 100 / 255}%",
                    info = "How hard the vibration motor is driven (1–255). For Sharp this scales " +
                        "the hardware click primitive; the length stays fixed, only the punch " +
                        "changes. For Custom it only takes effect on devices whose vibrator " +
                        "supports amplitude control; on others only the duration above matters. " +
                        "The system-wide \"Touch feedback\" vibration setting still scales the " +
                        "final strength on top of this.",
                ) {
                    scope.launch { repository.setHapticAmplitude(it.toInt()) }
                    HapticPlayer.preview(context, settings.hapticStyle, it.toInt(), settings.hapticStrengthMs)
                }
            }
        }
        item {
            ToggleSetting(
                "Long-press haptics", "Vibrate when a long press registers", settings.hapticOnLongPress,
                info = "A second buzz the moment a long press kicks in — when the alternate-" +
                    "character popup opens, or a long-press action fires — telling your finger " +
                    "it can let go. Delete and space are unaffected; their key-repeat already " +
                    "vibrates on every repeat.",
            ) { scope.launch { repository.setHapticOnLongPress(it) } }
        }
        item {
            ToggleSetting(
                "Long-press release haptics", "Vibrate on release after a long press",
                settings.hapticOnLongPressRelease,
                info = "An extra buzz when you lift your finger at the end of a long press, " +
                    "closing the press-hold-release loop. Off by default; some find the third " +
                    "vibration excessive.",
            ) { scope.launch { repository.setHapticOnLongPressRelease(it) } }
        }
    }

    SettingsGroup("Key popup") {
        item {
            ToggleSetting(
                "Key popup", "Show a character bubble above the pressed key", settings.keyPopup,
                info = "While a key is held, its character floats in a bubble above your finger " +
                    "so you can see what you hit.",
            ) { scope.launch { repository.setKeyPopup(it) } }
        }
        if (settings.keyPopup) {
            item {
                SliderSetting(
                    "Minimum popup duration",
                    subtitle = "How long the bubble stays up even on a fast tap",
                    value = settings.keyPopupMinDurationMs.toFloat(),
                    range = 0f..300f,
                    display = "${settings.keyPopupMinDurationMs} ms",
                    info = "On a quick tap the key is released almost instantly, which can make " +
                        "the bubble a barely-visible flicker. The bubble lingers after release " +
                        "until it has been shown for at least this long. 0 hides it the moment " +
                        "you let go.",
                ) { scope.launch { repository.setKeyPopupMinDurationMs(it.toInt()) } }
            }
        }
        item {
            ToggleSetting(
                "Popup on key",
                "Grow the bubble upward from the pressed key itself",
                settings.keyPopupOnKey,
                info = "On: the preview bubble sits on the pressed key and stretches upward, " +
                    "key-wide with a large character near its top — the stock-keyboard look. " +
                    "Off: a compact bubble floats above your fingertip with a gap.",
            ) { scope.launch { repository.setKeyPopupOnKey(it) } }
        }
        item {
            SliderSetting(
                "Popup font size",
                subtitle = "Scale of the key preview bubble and long-press alternates",
                value = settings.popupFontScale,
                range = 0.7f..1.6f,
                display = "×%.2f".format(settings.popupFontScale),
                info = "Multiplies the text size inside the character bubble shown while a key " +
                    "is pressed and in the long-press alternates popup, independently of the " +
                    "key label size.",
            ) { scope.launch { repository.setPopupFontScale(it) } }
        }
        item {
            SliderSetting(
                "Popup height",
                subtitle = "Height of the key preview bubble",
                value = settings.keyPopupHeightDp.toFloat(),
                range = 32f..160f,
                display = "${settings.keyPopupHeightDp} dp",
                info = "Height of the character bubble. With \"Popup on key\" enabled this is " +
                    "measured from the bottom of the pressed key, so anything taller than the " +
                    "key extends above it and stays visible past your finger.",
            ) { scope.launch { repository.setKeyPopupHeightDp(it.toInt()) } }
        }
    }

    SettingsGroup("Timing") {
        item {
            SliderSetting(
                "Long-press delay",
                subtitle = "Hold time before alternate characters appear",
                value = settings.longPressDelayMs.toFloat(),
                range = 150f..700f,
                display = "${settings.longPressDelayMs} ms",
                info = "How long a key must be held before its long-press alternates (accents, " +
                    "digits, symbols) pop up. Lower is faster but easier to trigger by accident.",
            ) { scope.launch { repository.setLongPressDelayMs(it.toInt()) } }
        }
        item {
            SliderSetting(
                "Key repeat interval",
                subtitle = "Speed of repeated delete while held",
                value = settings.keyRepeatIntervalMs.toFloat(),
                range = 20f..200f,
                display = "${settings.keyRepeatIntervalMs} ms",
                info = "While delete (or space) is held it repeats at this interval. Lower " +
                    "values delete faster.",
            ) { scope.launch { repository.setKeyRepeatIntervalMs(it.toInt()) } }
        }
    }

    SettingsGroup("Long-press shortcuts") {
        item {
            ToggleSetting(
                "Long-press hints", "Show each key's long-press character in its corner",
                settings.longPressHints,
                info = "A small label in the top-right corner of each key previews its first " +
                    "long-press character — the digit, symbol or accent the popup leads with. " +
                    "Keys running a clipboard shortcut below show no hint.",
            ) { scope.launch { repository.setLongPressHints(it) } }
        }
        item {
            ToggleSetting(
                "Hold A to select all", "Long-pressing A selects all text",
                settings.longPressASelectAll,
                info = "Replaces the A key's accent popup with a select-all shortcut. Turn it " +
                    "off to get the accents (à á â ä å) back.",
            ) { scope.launch { repository.setLongPressASelectAll(it) } }
        }
        item {
            ToggleSetting(
                "Hold C to copy", "Copies the selection, or everything if nothing is selected",
                settings.longPressCCopy,
                info = "With text selected, a long press on C copies just that selection. With " +
                    "no selection it selects all first, so one hold copies the whole field. " +
                    "Replaces the C key's accent popup (ç ć) while enabled.",
            ) { scope.launch { repository.setLongPressCCopy(it) } }
        }
        item {
            ToggleSetting(
                "Hold X to cut", "Cuts the selection, or everything if nothing is selected",
                settings.longPressXCut,
                info = "With text selected, a long press on X cuts just that selection. With " +
                    "no selection it selects all first, so one hold cuts the whole field.",
            ) { scope.launch { repository.setLongPressXCut(it) } }
        }
        item {
            ToggleSetting(
                "Hold V to paste", "Long-pressing V pastes the clipboard",
                settings.longPressVPaste,
                info = "Pastes the current clipboard content at the cursor, replacing any " +
                    "selection — the classic Ctrl+V, one hold away.",
            ) { scope.launch { repository.setLongPressVPaste(it) } }
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
) {
    val scope = rememberCoroutineScope()
    SettingsGroup("Style") {
        item {
            val selected = settings.customThemes.find { it.id == settings.keyboardThemeId }
                ?: com.wasimaster.wmkeyboard.core.theme.BuiltInThemes
                    .find { it.id == settings.keyboardThemeId }
            NavRow(
                "Keyboard themes",
                "Light/dark/AMOLED, colors, background images, import/export",
                value = selected?.name ?: "Default",
                onClick = onOpenThemes,
            )
        }
        item {
            NavRow(
                "Keyboard font",
                "Google Fonts, or import your own font file",
                value = KeyboardFonts.displayName(settings.keyFontId, settings.customFontName),
                onClick = onOpenFonts,
            )
        }
    }

    SettingsGroup("Keys") {
        item {
            SliderSetting(
                "Key corner radius",
                subtitle = "Roundness of the key corners",
                value = settings.keyCornerRadiusDp.toFloat(),
                range = 0f..28f,
                display = "${settings.keyCornerRadiusDp} dp",
                info = "0 gives square keys, 28 gives fully pill-shaped keys.",
            ) { scope.launch { repository.setKeyCornerRadiusDp(it.toInt()) } }
        }
        item {
            SliderSetting(
                "Key label font size",
                subtitle = "Scale of the labels printed on the keys",
                value = settings.fontScale,
                range = 0.7f..1.5f,
                display = "×%.2f".format(settings.fontScale),
                info = "Multiplies the size of every label on the keys themselves. Popup " +
                    "bubbles have their own font size under Key press → Key popup.",
            ) { scope.launch { repository.setFontScale(it) } }
        }
    }

    SettingsGroup("Toolbar") {
        item {
            ToggleSetting(
                "Spread tools across the bar",
                "Toolbar tools split the available width evenly",
                settings.toolbarGreedy,
                info = "On: the tools on the top toolbar greedily share the whole bar, like " +
                    "the suggestion candidates do. Off: they pack to the left at a fixed " +
                    "size. Which tools appear there is customized from the keyboard itself: " +
                    "open the toolbox (grid button on the toolbar), then hold and drag tools " +
                    "between the toolbar and the toolbox.",
            ) { scope.launch { repository.setToolbarGreedy(it) } }
        }
        item {
            SliderSetting(
                "Tool circle radius",
                subtitle = "Roundness of the circle behind each toolbar tool",
                value = settings.toolCircleRadiusDp.toFloat(),
                range = 0f..20f,
                display = if (settings.toolCircleRadiusDp == 0) "off" else "${settings.toolCircleRadiusDp} dp",
                info = "20 draws a full circle behind every tool icon (Gboard style), " +
                    "smaller values give rounded squares, and 0 removes the background " +
                    "entirely, leaving bare icons.",
            ) { scope.launch { repository.setToolCircleRadiusDp(it.toInt()) } }
        }
    }
}

// ---- layout & size ----

@Composable
private fun LayoutSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    SettingsGroup("Number row") {
        item {
            ToggleSetting(
                "Number row", "Show a dedicated digit row above the letters", settings.numberRow,
                info = "Adds a 1–0 row on top of the letter layout so you never long-press for " +
                    "digits. The digits normally on the top letter row's long press are dropped " +
                    "while this is on. Costs one extra row of height.",
            ) { scope.launch { repository.setNumberRow(it) } }
        }
        if (settings.numberRow) {
            item {
                SliderSetting(
                    "Number row height",
                    subtitle = "Height of the digit row, independent of the letter keys",
                    value = settings.numberRowHeightDp.toFloat(),
                    range = 32f..100f,
                    display = "${settings.numberRowHeightDp} dp",
                    info = "A shorter digit row keeps quick number access without costing a " +
                        "full row of extra keyboard height.",
                ) { scope.launch { repository.setNumberRowHeightDp(it.toInt()) } }
            }
        }
    }

    SettingsGroup("Size & position") {
        item {
            SliderSetting(
                "Key height",
                subtitle = "Height of each key row — sets the overall input height",
                value = settings.keyHeightDp.toFloat(),
                range = 32f..100f,
                display = "${settings.keyHeightDp} dp",
                info = "Taller keys are easier to hit but the keyboard covers more of the " +
                    "screen. The emoji, clipboard and snippet panels scale with this value too.",
            ) { scope.launch { repository.setKeyHeightDp(it.toInt()) } }
        }
        item {
            SliderSetting(
                "Bottom padding",
                subtitle = "Extra space below the keys, above the navigation bar",
                value = settings.bottomPaddingDp.toFloat(),
                range = 0f..40f,
                display = "${settings.bottomPaddingDp} dp",
                info = "Raises the whole keyboard away from the bottom edge and the gesture " +
                    "navigation bar. Increase it if the bottom row feels cramped against the " +
                    "edge of the screen or you keep triggering system navigation.",
            ) { scope.launch { repository.setBottomPaddingDp(it.toInt()) } }
        }
        item {
            SliderSetting(
                "Keyboard width",
                subtitle = "Shrink the keyboard horizontally",
                value = settings.keyboardWidthPercent.toFloat(),
                range = 50f..100f,
                display = "${settings.keyboardWidthPercent}%",
                info = "Below 100% the keyboard no longer spans the whole screen; choose which " +
                    "edge it sits at below. Handy on very wide screens. One-handed mode " +
                    "(below) is a quick preset that overrides this while active.",
            ) { scope.launch { repository.setKeyboardWidthPercent(it.toInt()) } }
        }
        if (settings.keyboardWidthPercent < 100) {
            item {
                ChoiceSetting(
                    title = "Keyboard position",
                    info = "Where the narrowed keyboard sits: hugging the left edge, centered, " +
                        "or hugging the right edge.",
                    options = KeyboardAlignment.entries.map { alignment ->
                        alignment to alignment.name.lowercase().replaceFirstChar { it.uppercase() }
                    },
                    selected = settings.keyboardAlignment,
                ) { scope.launch { repository.setKeyboardAlignment(it) } }
            }
        }
    }

    SettingsGroup("One-handed, split & floating") {
        item {
            ChoiceSetting(
                title = "One-handed mode",
                subtitle = "Shrink the keyboard toward one edge",
                options = OneHandedMode.entries.map { mode ->
                    mode to mode.name.lowercase().replaceFirstChar { it.uppercase() }
                },
                selected = settings.oneHandedMode,
            ) { scope.launch { repository.setOneHandedMode(it) } }
        }
        item {
            ToggleSetting(
                "Split keyboard", "Divide the keys into left and right halves", settings.splitKeyboard,
                info = "Splits every row down the middle with a gap between the halves, so " +
                    "your thumbs travel less on wide screens — most useful on tablets, " +
                    "foldables and phones in landscape. The spacebar is divided between the " +
                    "two halves.",
            ) { scope.launch { repository.setSplitKeyboard(it) } }
        }
        if (settings.splitKeyboard) {
            item {
                SliderSetting(
                    "Split gap",
                    subtitle = "Width of the gap between the halves",
                    value = settings.splitGapPercent.toFloat(),
                    range = 5f..40f,
                    display = "${settings.splitGapPercent}%",
                    info = "The center gap, as a percentage of the keyboard width. Bigger gaps " +
                        "push the halves further toward the edges but make each key narrower.",
                ) { scope.launch { repository.setSplitGapPercent(it.toInt()) } }
            }
        }
        item {
            ToggleSetting(
                "Floating keyboard", "Detach the keyboard into a movable panel", settings.floatingKeyboard,
                info = "The keyboard becomes a compact floating panel that hovers over apps " +
                    "instead of docking to the bottom of the screen. Drag the pill at the top " +
                    "of the panel to move it, drag the corner grip to resize it, and tap the " +
                    "dock button to return to the normal keyboard. Apps are no longer resized " +
                    "while it floats, and touches outside the panel go straight to the app.",
            ) { scope.launch { repository.setFloatingKeyboard(it) } }
        }
        if (settings.floatingKeyboard) {
            item {
                SliderSetting(
                    "Floating keyboard width",
                    subtitle = "Also adjustable by dragging the panel's corner grip",
                    value = settings.floatingWidthDp.toFloat(),
                    range = 240f..500f,
                    display = "${settings.floatingWidthDp} dp",
                    info = "Width of the floating panel. Key heights still follow the sliders " +
                        "above.",
                ) { scope.launch { repository.setFloatingWidthDp(it.toInt()) } }
            }
        }
    }

    SettingsGroup("Bottom-row keys") {
        item {
            ToggleSetting(
                "Comma key opens emoji",
                "Replace the comma key with an emoji key",
                settings.commaAsEmoji,
                info = "The bottom-row comma key becomes an emoji-panel key; comma moves " +
                    "into its long-press alternates. Turning this on also removes the " +
                    "emoji tool from the toolbar since the key replaces it — drag it back " +
                    "out of the toolbox if you want both.",
            ) { scope.launch { repository.setCommaAsEmoji(it) } }
        }
        item {
            ToggleSetting(
                "Emoji key instead of 🌐",
                "Replace the language key with an emoji key",
                settings.globeAsEmoji,
                info = "The bottom-row 🌐 key opens the emoji panel instead of switching " +
                    "language. Language switching stays available on the spacebar: set a " +
                    "swipe to \"Language\" under Typing → Gestures (a quick swipe does it " +
                    "by default). Turn this off to get the 🌐 key back.",
            ) { scope.launch { repository.setGlobeAsEmoji(it) } }
        }
    }
}

// ---- languages ----

@Composable
private fun LanguageSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    CaptionText("Cycle between enabled input modes with the 🌐 key or a spacebar swipe.")
    SettingsGroup("Input modes") {
        val labels = mapOf(
            InputMode.ENGLISH to Triple(
                "English", "QWERTY with suggestions",
                "Standard QWERTY layout with autocorrect, predictions and gesture typing.",
            ),
            InputMode.AVRO to Triple(
                "বাংলা — Avro phonetic", "Type \"ami valo achi\", get আমি ভালো আছি",
                "Type Bengali phonetically with Latin letters; the transliteration happens " +
                    "live as you type, and the suggestion bar offers dictionary spellings.",
            ),
            InputMode.PROBHAT to Triple(
                "বাংলা — প্রভাত (Probhat)", "Fixed Bengali layout",
                "The fixed Probhat layout familiar from Linux: vowel signs on the home row, " +
                    "consonants by frequency, aspirates on shift.",
            ),
            InputMode.JATIYA to Triple(
                "বাংলা — জাতীয় (National)", "Bangladesh standard fixed layout",
                "The National (Jatiya) fixed layout standardized in Bangladesh; the same " +
                    "arrangement used by Bijoy-style keyboards, with aspirates on shift and " +
                    "independent vowels on long-press.",
            ),
        )
        for (mode in InputMode.entries) {
            val (title, subtitle, info) = labels.getValue(mode)
            item {
                ToggleSetting(title, subtitle, mode in settings.enabledModes, info = info) { enable ->
                    scope.launch {
                        val next = if (enable) settings.enabledModes + mode else settings.enabledModes - mode
                        if (next.isNotEmpty()) repository.setEnabledModes(next.distinct())
                    }
                }
            }
        }
    }
    SettingsGroup("Bengali") {
        item {
            ToggleSetting(
                "Conjunct-aware backspace",
                "Delete a whole যুক্তবর্ণ (like ক্ষ or স্ত্রী) as one unit",
                settings.conjunctBackspace,
                info = "Normally backspace removes one code point at a time, which can leave " +
                    "half-formed conjuncts. With this on, a conjunct cluster like স্ত্রী is " +
                    "deleted in a single press.",
            ) {
                scope.launch { repository.setConjunctBackspace(it) }
            }
        }
    }
}

// ---- emoji ----

@Composable
private fun EmojiSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    SettingsGroup("Access") {
        item {
            ToggleSetting(
                "Emoji button in toolbar", "One-tap emoji access from the top bar", settings.emojiToolbar,
                info = "Keeps the emoji button visible in the top bar even while suggestions " +
                    "are showing. The emoji panel itself has tabs per category, search in " +
                    "English and Bengali, and skin-tone variants on long-press.",
            ) { scope.launch { repository.setEmojiToolbar(it) } }
        }
    }
    SettingsGroup("Suggestions") {
        item {
            ToggleSetting(
                "Emoji suggestions",
                "Offer emojis while typing — birthday suggests 🎂 🎉 🥳",
                settings.emojiPrediction,
                info = "Matching emojis appear at the end of the suggestion strip while you " +
                    "type, in English or Bengali (জন্মদিন also suggests 🎂).",
            ) { scope.launch { repository.setEmojiPrediction(it) } }
        }
        if (settings.emojiPrediction) {
            item {
                ChoiceSetting(
                    title = "Emoji suggestion tap",
                    subtitle = "What happens to the word you typed",
                    info = "\"Replace word\" swaps the typed word for the emoji (typing " +
                        "birthday and tapping 🎂 leaves just 🎂, like Gboard). \"Keep word\" " +
                        "adds the emoji after it: birthday 🎂.",
                    options = listOf(
                        EmojiInsertMode.REPLACE to "Replace word",
                        EmojiInsertMode.APPEND to "Keep word",
                    ),
                    selected = settings.emojiInsertMode,
                ) { scope.launch { repository.setEmojiInsertMode(it) } }
            }
        }
    }
    SettingsGroup("Emoji panel") {
        item {
            ChoiceSetting(
                title = "History tab",
                subtitle = "What the first emoji-panel tab shows",
                info = "\"Recent\" lists emojis in the order you last used them; \"Most used\" " +
                    "ranks them by how often you use them. Favourited emojis are always " +
                    "pinned to the front of either list.",
                options = listOf(
                    EmojiTabMode.RECENTS to "Recent",
                    EmojiTabMode.MOST_USED to "Most used",
                ),
                selected = settings.emojiTabMode,
            ) { scope.launch { repository.setEmojiTabMode(it) } }
        }
    }
    SettingsGroup("Emoji row") {
        item {
            ChoiceSetting(
                title = "Emoji row",
                subtitle = "A dedicated row of your emojis, like Gboard",
                info = "\"Own row\" keeps a persistent emoji row between the toolbar and the " +
                    "keys. \"Button\" adds a toggle at the right edge of the toolbar that " +
                    "swaps the strip for the emoji row. \"Off\" hides both.",
                options = listOf(
                    EmojiBarMode.OFF to "Off",
                    EmojiBarMode.BUTTON to "Button",
                    EmojiBarMode.ALWAYS to "Own row",
                ),
                selected = settings.emojiBarMode,
            ) { scope.launch { repository.setEmojiBarMode(it) } }
        }
        if (settings.emojiBarMode != EmojiBarMode.OFF) {
            item {
                ChoiceSetting(
                    title = "Emoji row content",
                    subtitle = "Favourites always come first",
                    options = listOf(
                        EmojiBarContent.MOST_USED to "Most used",
                        EmojiBarContent.RECENTS to "Recent",
                        EmojiBarContent.FAVOURITES to "Favourites",
                    ),
                    selected = settings.emojiBarContent,
                ) { scope.launch { repository.setEmojiBarContent(it) } }
            }
        }
    }
    SettingsGroup("Emoji style") {
        item {
            val context = LocalContext.current
            // Bumped after an import so the preview re-resolves the (same-named) file.
            var fontRefresh by remember { mutableIntStateOf(0) }
            ChoiceSetting(
                title = "Emoji font",
                subtitle = "How emojis look on the keyboard itself",
                info = "\"System\" uses your phone's emoji pack — on Samsung phones " +
                    "that is Samsung's own set. \"Google\" fetches Noto Color Emoji " +
                    "(the stock-Android look) once through the system font provider " +
                    "and caches it on the device. \"Custom\" uses an emoji font file " +
                    "you import below, such as a Twemoji or OpenMoji build. Text you " +
                    "send is plain Unicode either way — other apps and other phones " +
                    "still draw it with their own emoji font.",
                options = listOf(
                    EmojiFontChoice.SYSTEM to "System",
                    EmojiFontChoice.NOTO to "Google",
                    EmojiFontChoice.CUSTOM to "Custom",
                ),
                selected = settings.emojiFont,
            ) { scope.launch { repository.setEmojiFont(it) } }
            val previewFamily = remember(settings.emojiFont, fontRefresh) {
                KeyboardFonts.emojiFamily(context, settings.emojiFont)
            }
            // One Text per emoji: emoji fonts often have no space glyph, so drawing
            // them as a single spaced string makes the glyphs overlap.
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                for (emoji in listOf("😀", "😂", "🥰", "😎", "🤔", "👍", "❤️", "🎉")) {
                    Text(
                        emoji,
                        fontSize = 24.sp,
                        fontFamily = previewFamily,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
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
                        "No emoji font imported yet — the system font is used until then.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importEmojiFont.launch(FONT_MIME_TYPES) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text(if (imported) "Replace emoji font file" else "Import emoji font file") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    CaptionText(
        "Tip: long-press any emoji in the panel to favourite it or pick skin tones — " +
            "two-person emojis like 🤝 let you set each person's tone separately.",
    )
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
    val lexicon = remember {
        UserLexicon(java.io.File(context.filesDir, "learning/user_lexicon.json"))
    }
    var words by remember { mutableStateOf(lexicon.allWords().sortedByDescending { it.second }) }
    var showAdd by remember { mutableStateOf(false) }

    fun persist() {
        lexicon.save()
        words = lexicon.allWords().sortedByDescending { it.second }
        scope.launch { repository.bumpLexiconVersion() }
    }

    Text(
        "Words the keyboard has learned from your typing, plus any you add " +
            "yourself. They are suggested while typing and never autocorrected " +
            "away. Everything stays on this device.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Button(
        onClick = { showAdd = true },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text("Add word") }
    Spacer(Modifier.height(12.dp))
    if (words.isEmpty()) {
        CaptionText(
            "Nothing here yet — words appear as you type (with \"Learn from " +
                "typing\" on under Privacy), or add one above.",
        )
    }
    SettingsGroup {
        for ((word, count) in words) {
            item {
                ListItem(
                    headlineContent = { Text(word) },
                    supportingContent = {
                        Text(if (count >= 200) "Added by you" else "Seen $count×")
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            lexicon.forget(word)
                            persist()
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove $word")
                        }
                    },
                    colors = transparentListColors(),
                )
            }
        }
    }

    if (showAdd) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add word") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Word") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        lexicon.addWord(input.trim())
                        persist()
                        showAdd = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

// ---- fonts ----

/** Mime types SAF offers when picking a font; octet-stream covers file managers that don't tag fonts. */
private val FONT_MIME_TYPES = arrayOf(
    "font/ttf", "font/otf", "font/*", "application/x-font-ttf", "application/octet-stream",
)

/**
 * Font picker: separate English and Bengali choices, each offering the
 * system default, curated Google Fonts (every row rendered in its own face
 * as a live preview — faces download on first view and are cached by the
 * system provider), plus an imported custom font file per script.
 */
@Composable
private fun FontSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Text(
        "Applies to key labels, suggestions and the keyboard's panels. The " +
            "English font is used in English mode, the Bengali font in Avro, " +
            "প্রভাত and জাতীয় modes. Google fonts are fetched once through the " +
            "system font provider and cached on-device; missing glyphs fall " +
            "back to the system font automatically.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    FontPickerSection(
        header = "English font",
        sample = "The quick brown fox jumps over the lazy dog",
        selectedId = settings.keyFontId,
        googleNames = KeyboardFonts.googleFonts,
        customId = KeyboardFonts.CUSTOM_ID,
        customFile = KeyboardFonts.customFontFile(context),
        customName = settings.customFontName,
        onSelect = { id -> scope.launch { repository.setKeyFontId(id) } },
        onImport = { uri ->
            scope.launch {
                val name = withContext(Dispatchers.IO) {
                    importFontFile(context, uri, KeyboardFonts.customFontFile(context))
                }
                if (name != null) repository.setCustomFont(name)
            }
        },
    )
    FontPickerSection(
        header = "Bengali font",
        sample = "আমি ভালো আছি · কখগঘঙ চছজঝঞ",
        selectedId = settings.bengaliFontId,
        googleNames = KeyboardFonts.bengaliGoogleFonts,
        customId = KeyboardFonts.CUSTOM_BENGALI_ID,
        customFile = KeyboardFonts.customBengaliFontFile(context),
        customName = settings.customBengaliFontName,
        onSelect = { id -> scope.launch { repository.setBengaliFontId(id) } },
        onImport = { uri ->
            scope.launch {
                val name = withContext(Dispatchers.IO) {
                    importFontFile(context, uri, KeyboardFonts.customBengaliFontFile(context))
                }
                if (name != null) repository.setCustomBengaliFont(name)
            }
        },
    )
    Spacer(Modifier.height(16.dp))
}

/** One script's font list: default, Google faces, the imported file, import button. */
@Composable
private fun FontPickerSection(
    header: String,
    sample: String,
    selectedId: String,
    googleNames: List<String>,
    customId: String,
    customFile: java.io.File,
    customName: String,
    onSelect: (String) -> Unit,
    onImport: (android.net.Uri) -> Unit,
) {
    val context = LocalContext.current
    val importFont = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onImport(uri) }
    SettingsGroup(header) {
        item {
            FontChoiceRow(
                label = "System default",
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
        if (customFile.exists()) {
            item {
                FontChoiceRow(
                    label = customName.ifBlank { "Imported font" },
                    family = remember(customName) { KeyboardFonts.family(context, customId) },
                    sample = sample,
                    selected = selectedId == customId,
                ) { onSelect(customId) }
            }
        }
        item {
            OutlinedButton(
                onClick = { importFont.launch(FONT_MIME_TYPES) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Import font file (.ttf / .otf)") }
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
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, fontFamily = family, fontSize = 18.sp) },
        supportingContent = {
            Text(
                sample,
                fontFamily = family,
                maxLines = 1,
            )
        },
        trailingContent = if (selected) {
            {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        colors = transparentListColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

/**
 * Copies a picked font into private storage and returns its display name,
 * or null when the stream can't be read or the platform can't parse the
 * file (the bad copy is deleted so it never sticks as the "custom font").
 */
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

private fun toolTitle(tool: ToolbarTool): String = when (tool) {
    ToolbarTool.EMOJI -> "Emoji"
    ToolbarTool.CLIPBOARD -> "Clipboard"
    ToolbarTool.SNIPPETS -> "Snippets"
    ToolbarTool.TEXT_EDIT -> "Text editing"
    ToolbarTool.ONE_HANDED -> "One-handed mode"
    ToolbarTool.SPLIT -> "Split keyboard"
    ToolbarTool.FLOATING -> "Floating keyboard"
    ToolbarTool.SETTINGS -> "Settings shortcut"
    ToolbarTool.FLASHLIGHT -> "Flashlight"
    ToolbarTool.COMPASS -> "Compass"
    ToolbarTool.LEVEL -> "Bubble level"
    ToolbarTool.UNDO -> "Undo"
    ToolbarTool.REDO -> "Redo"
    ToolbarTool.MOON_PHASE -> "Moon phase"
    ToolbarTool.WEATHER -> "Weather"
    ToolbarTool.CALENDAR -> "Calendar"
    ToolbarTool.INCOGNITO -> "Incognito"
    ToolbarTool.THEMES -> "Themes"
    ToolbarTool.AUTOCORRECT -> "Autocorrect"
    ToolbarTool.SOUND_HAPTICS -> "Sound & haptics"
    ToolbarTool.NUMPAD -> "Numpad"
    ToolbarTool.HANDWRITING -> "Handwriting"
    ToolbarTool.CAMERA -> "Camera"
    ToolbarTool.DICTIONARY -> "Dictionary"
}

private fun toolDescription(tool: ToolbarTool): String = when (tool) {
    ToolbarTool.EMOJI -> "Emoji panel with search and skin tones"
    ToolbarTool.CLIPBOARD -> "Paste from clipboard history"
    ToolbarTool.SNIPPETS -> "Insert saved text snippets"
    ToolbarTool.TEXT_EDIT -> "Cursor, selection and clipboard controls"
    ToolbarTool.ONE_HANDED -> "Shrink the keyboard toward one edge"
    ToolbarTool.SPLIT -> "Split the keys into two halves"
    ToolbarTool.FLOATING -> "Detach the keyboard into a movable panel"
    ToolbarTool.SETTINGS -> "Open this settings app"
    ToolbarTool.FLASHLIGHT -> "Toggle the torch from the keyboard"
    ToolbarTool.COMPASS -> "Live compass with degree readout and optional qibla"
    ToolbarTool.LEVEL -> "Bubble level using the accelerometer"
    ToolbarTool.UNDO -> "One tap sends the editor's undo shortcut (Ctrl+Z)"
    ToolbarTool.REDO -> "One tap sends the editor's redo shortcut"
    ToolbarTool.MOON_PHASE -> "Current phase, illumination, next full/new moon"
    ToolbarTool.WEATHER -> "Current conditions for a saved location"
    ToolbarTool.CALENDAR -> "Month view with Bengali and Hijri dates"
    ToolbarTool.INCOGNITO -> "One tap pauses learning and clipboard capture"
    ToolbarTool.THEMES -> "Quick theme switcher on the keyboard"
    ToolbarTool.AUTOCORRECT -> "One tap turns autocorrect on or off"
    ToolbarTool.SOUND_HAPTICS -> "Adjust key sound and vibration from the keyboard"
    ToolbarTool.NUMPAD -> "Dedicated number pad layout"
    ToolbarTool.HANDWRITING -> "Write words by hand — finger or S Pen — with on-device recognition"
    ToolbarTool.CAMERA -> "Take a photo and send it without leaving the keyboard"
    ToolbarTool.DICTIONARY -> "English definitions, pronunciation and synonyms"
}

private fun toolIconFor(tool: ToolbarTool): androidx.compose.ui.graphics.vector.ImageVector = when (tool) {
    ToolbarTool.EMOJI -> Icons.Outlined.EmojiEmotions
    ToolbarTool.CLIPBOARD -> Icons.Outlined.ContentPaste
    ToolbarTool.SNIPPETS -> Icons.Outlined.TextSnippet
    ToolbarTool.TEXT_EDIT -> Icons.Outlined.EditNote
    ToolbarTool.ONE_HANDED -> Icons.Outlined.Smartphone
    ToolbarTool.SPLIT -> Icons.Outlined.VerticalSplit
    ToolbarTool.FLOATING -> Icons.Outlined.PictureInPictureAlt
    ToolbarTool.SETTINGS -> Icons.Outlined.Settings
    ToolbarTool.FLASHLIGHT -> Icons.Outlined.FlashlightOn
    ToolbarTool.COMPASS -> Icons.Outlined.Explore
    ToolbarTool.LEVEL -> Icons.Outlined.Straighten
    ToolbarTool.UNDO -> Icons.AutoMirrored.Outlined.Undo
    ToolbarTool.REDO -> Icons.AutoMirrored.Outlined.Redo
    ToolbarTool.MOON_PHASE -> Icons.Outlined.DarkMode
    ToolbarTool.WEATHER -> Icons.Outlined.WbSunny
    ToolbarTool.CALENDAR -> Icons.Outlined.CalendarMonth
    ToolbarTool.INCOGNITO -> Icons.Outlined.VisibilityOff
    ToolbarTool.THEMES -> Icons.Outlined.Palette
    ToolbarTool.AUTOCORRECT -> Icons.Outlined.Spellcheck
    ToolbarTool.SOUND_HAPTICS -> Icons.Outlined.Vibration
    ToolbarTool.NUMPAD -> Icons.Outlined.Dialpad
    ToolbarTool.HANDWRITING -> Icons.Outlined.Draw
    ToolbarTool.CAMERA -> Icons.Outlined.PhotoCamera
    ToolbarTool.DICTIONARY -> Icons.AutoMirrored.Outlined.MenuBook
}

/**
 * The tool menu, grouped by what the tools do. Everything else — the
 * enable switch and the tool's own options — lives one level down.
 */
@Composable
private fun ToolsSettings(settings: KeyboardSettings, onOpenTool: (ToolbarTool) -> Unit) {
    CaptionText(
        "Tools live on the keyboard's toolbar and in the toolbox (grid button on " +
            "the toolbar). Tap a tool to enable or disable it and change its settings.",
    )
    val groups = listOf(
        "Panels" to listOf(
            ToolbarTool.EMOJI, ToolbarTool.CLIPBOARD, ToolbarTool.SNIPPETS,
            ToolbarTool.TEXT_EDIT, ToolbarTool.NUMPAD, ToolbarTool.HANDWRITING,
            ToolbarTool.CAMERA, ToolbarTool.DICTIONARY,
        ),
        "Keyboard modes" to listOf(
            ToolbarTool.ONE_HANDED, ToolbarTool.SPLIT, ToolbarTool.FLOATING,
        ),
        "Quick actions" to listOf(
            ToolbarTool.UNDO, ToolbarTool.REDO, ToolbarTool.AUTOCORRECT,
            ToolbarTool.INCOGNITO, ToolbarTool.SOUND_HAPTICS, ToolbarTool.THEMES,
            ToolbarTool.SETTINGS,
        ),
        "Utilities" to listOf(
            ToolbarTool.FLASHLIGHT, ToolbarTool.COMPASS, ToolbarTool.LEVEL,
            ToolbarTool.CALENDAR, ToolbarTool.WEATHER, ToolbarTool.MOON_PHASE,
        ),
    )
    for ((groupTitle, tools) in groups) {
        SettingsGroup(groupTitle) {
            for (tool in tools) {
                item {
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
                        trailingContent = if (tool !in settings.enabledTools) {
                            { Text("Off", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else null,
                        colors = transparentListColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTool(tool) },
                    )
                }
            }
        }
    }
}

/** One tool's screen: the enable switch plus every setting the tool has. */
@Composable
private fun ToolDetailSettings(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    tool: ToolbarTool,
    onOpenEmojiSettings: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    CaptionText(toolDescription(tool))
    SettingsGroup {
        item {
            ToggleSetting(
                "Enabled",
                "Show this tool on the toolbar and in the toolbox",
                tool in settings.enabledTools,
            ) { scope.launch { repository.setToolEnabled(tool, it) } }
        }
    }
    when (tool) {
        ToolbarTool.EMOJI -> SettingsGroup("Emoji") {
            item {
                ToggleSetting(
                    "Emoji button in toolbar",
                    "Keep the emoji button visible next to suggestions",
                    settings.emojiToolbar,
                ) { scope.launch { repository.setEmojiToolbar(it) } }
            }
            item {
                NavRow(
                    "All emoji settings",
                    "Suggestions, history tab, emoji row, skin tones & favourites",
                    onClick = onOpenEmojiSettings,
                )
            }
        }
        ToolbarTool.SNIPPETS -> SnippetSettings()
        ToolbarTool.CLIPBOARD -> SettingsGroup("History") {
            item {
                ToggleSetting(
                    "Clipboard history", "Save copied text for quick paste",
                    settings.clipboardHistory,
                ) { scope.launch { repository.setClipboardHistory(it) } }
            }
            item {
                SliderSetting(
                    "Clipboard expiry",
                    subtitle = "Remove unpinned items after this long",
                    value = settings.clipboardExpiryHours.toFloat(),
                    range = 0f..168f,
                    display = if (settings.clipboardExpiryHours == 0) "never"
                        else "${settings.clipboardExpiryHours} h",
                ) { scope.launch { repository.setClipboardExpiryHours(it.toInt()) } }
            }
        }
        ToolbarTool.SPLIT -> SettingsGroup("Options") {
            item {
                SliderSetting(
                    "Split gap",
                    subtitle = "Width of the gap between the halves",
                    value = settings.splitGapPercent.toFloat(),
                    range = 5f..40f,
                    display = "${settings.splitGapPercent}%",
                ) { scope.launch { repository.setSplitGapPercent(it.toInt()) } }
            }
        }
        ToolbarTool.FLOATING -> SettingsGroup("Options") {
            item {
                SliderSetting(
                    "Floating keyboard width",
                    subtitle = "Also adjustable by dragging the panel's corner grip",
                    value = settings.floatingWidthDp.toFloat(),
                    range = 240f..500f,
                    display = "${settings.floatingWidthDp} dp",
                ) { scope.launch { repository.setFloatingWidthDp(it.toInt()) } }
            }
        }
        ToolbarTool.FLASHLIGHT -> SettingsGroup("Options") {
            item {
                ToggleSetting(
                    "Auto-off with keyboard",
                    "Turn the torch off when the keyboard is dismissed",
                    settings.flashlightAutoOff,
                    info = "On: closing the keyboard (or switching apps) switches the " +
                        "torch off with it, so it is never left burning in your " +
                        "pocket. Off: the torch stays on until toggled again — from " +
                        "the tool or from the system quick-settings tile.",
                ) { scope.launch { repository.setFlashlightAutoOff(it) } }
            }
        }
        ToolbarTool.COMPASS -> {
            SettingsGroup("Options") {
                item {
                    ToggleSetting(
                        "Degree readout",
                        "Show the numeric heading under the compass rose",
                        settings.compassShowDegrees,
                    ) { scope.launch { repository.setCompassShowDegrees(it) } }
                }
                item {
                    ToggleSetting(
                        "Show qibla",
                        "Mark the direction of the Kaaba on the compass",
                        settings.compassShowQibla,
                        info = "The qibla bearing is computed from the location saved in the " +
                            "weather tool's settings (the two tools share it). Everything is " +
                            "calculated on-device; the compass never touches the network.",
                    ) { scope.launch { repository.setCompassShowQibla(it) } }
                }
            }
            if (settings.compassShowQibla && settings.weatherLatitude == null) {
                CaptionText(
                    "No location saved yet — set one under Tools → Weather.",
                    error = true,
                )
            }
        }
        ToolbarTool.LEVEL -> SettingsGroup("Options") {
            item {
                ToggleSetting(
                    "Angle readout",
                    "Show pitch and roll in degrees under the bubble",
                    settings.levelShowAngles,
                ) { scope.launch { repository.setLevelShowAngles(it) } }
            }
        }
        ToolbarTool.REDO -> SettingsGroup("Options") {
            item {
                ToggleSetting(
                    "Redo sends Ctrl+Y",
                    "Instead of the default Ctrl+Shift+Z",
                    settings.redoUsesCtrlY,
                    info = "Both are standard redo shortcuts; which one works depends " +
                        "on the app you are typing in. If redo does nothing, try " +
                        "the other one.",
                ) { scope.launch { repository.setRedoUsesCtrlY(it) } }
            }
        }
        ToolbarTool.MOON_PHASE -> SettingsGroup("Options") {
            item {
                ToggleSetting(
                    "Southern hemisphere",
                    "Mirror the moon the way it appears south of the equator",
                    settings.moonSouthernHemisphere,
                ) { scope.launch { repository.setMoonSouthernHemisphere(it) } }
            }
        }
        ToolbarTool.WEATHER -> {
            SettingsGroup("Options") {
                item { WeatherLocationSetting(repository, settings) }
                item {
                    ToggleSetting(
                        "Fahrenheit", "Show temperatures in °F instead of °C",
                        settings.weatherFahrenheit,
                    ) { scope.launch { repository.setWeatherFahrenheit(it) } }
                }
            }
            CaptionText(
                "Weather and place search both use Open-Meteo, only when you use " +
                    "them — the keyboard makes no other network requests.",
            )
        }
        ToolbarTool.CALENDAR -> SettingsGroup("Calendars") {
            item {
                ToggleSetting(
                    "Bengali calendar",
                    "বঙ্গাব্দ (revised Bangladeshi calendar) alongside dates",
                    settings.calendarShowBengali,
                ) { scope.launch { repository.setCalendarShowBengali(it) } }
            }
            item {
                ToggleSetting(
                    "Hijri calendar",
                    "Islamic (tabular) calendar alongside dates",
                    settings.calendarShowHijri,
                ) { scope.launch { repository.setCalendarShowHijri(it) } }
            }
            if (settings.calendarShowHijri) {
                item {
                    SliderSetting(
                        "Hijri day adjustment",
                        subtitle = "Shift the computed Hijri date to match local moon sighting",
                        value = settings.hijriAdjustDays.toFloat(),
                        range = -2f..2f,
                        display = if (settings.hijriAdjustDays > 0) "+${settings.hijriAdjustDays} d"
                            else "${settings.hijriAdjustDays} d",
                        info = "The tool uses the arithmetic (tabular) Hijri calendar. " +
                            "Real Islamic months begin at the sighting of the crescent, " +
                            "which can differ from the tables by a day or two either " +
                            "way — set the offset that matches your local authority.",
                    ) { scope.launch { repository.setHijriAdjustDays(it.roundToInt()) } }
                }
            }
        }
        ToolbarTool.CAMERA -> {
            SettingsGroup("Options") {
                item {
                    ToggleSetting(
                        "Start with the selfie camera",
                        "Open the tool on the front camera instead of the back one",
                        settings.cameraPreferFront,
                    ) { scope.launch { repository.setCameraPreferFront(it) } }
                }
                item {
                    ToggleSetting(
                        "Mirror selfies",
                        "Save front-camera photos the way the preview shows them",
                        settings.cameraMirrorFront,
                        info = "Camera sensors record selfies un-mirrored (text reads " +
                            "correctly, but the photo looks flipped compared to the " +
                            "preview). On: the saved photo matches what you saw while " +
                            "framing. Off: keep the sensor's true orientation.",
                    ) { scope.launch { repository.setCameraMirrorFront(it) } }
                }
            }
            SettingsGroup("Feedback") {
                item {
                    ToggleSetting(
                        "Shutter sound",
                        "Play the camera click when a photo is taken",
                        settings.cameraShutterSound,
                    ) { scope.launch { repository.setCameraShutterSound(it) } }
                }
                item {
                    ToggleSetting(
                        "Haptics",
                        "Vibrate on the shutter, controls and timer countdown",
                        settings.cameraHaptics,
                        info = "Uses the keyboard's haptic style and strength " +
                            "(Typing → Feedback). If keyboard haptics are off " +
                            "entirely, the camera tool stays silent too.",
                    ) { scope.launch { repository.setCameraHaptics(it) } }
                }
            }
            CaptionText(
                "Photos are cropped to what the viewfinder shows, saved in the " +
                    "app's private storage and sent straight into the chat. " +
                    "Nothing is added to your gallery, and the camera runs only " +
                    "while the tool is open.",
            )
        }
        ToolbarTool.DICTIONARY -> {
            SettingsGroup("Options") {
                item {
                    ToggleSetting(
                        "Look up the word at the cursor",
                        "Opening the tool searches the selected or current word",
                        settings.dictionaryAutoLookup,
                    ) { scope.launch { repository.setDictionaryAutoLookup(it) } }
                }
            }
            CaptionText(
                "Definitions come from the Free Dictionary API " +
                    "(dictionaryapi.dev). The word you look up is sent to that " +
                    "service — only when you use the tool.",
            )
        }
        ToolbarTool.INCOGNITO -> CaptionText(
            "Tapping the tool pauses on-device learning and clipboard capture; " +
                "tapping again resumes them. Same switch as Settings → Privacy.",
        )
        ToolbarTool.AUTOCORRECT -> SettingsGroup("Options") {
            item {
                ToggleSetting(
                    "Autocorrect",
                    "The tool flips this same switch (also under Typing)",
                    settings.autocorrect,
                ) { scope.launch { repository.setAutocorrect(it) } }
            }
        }
        ToolbarTool.SOUND_HAPTICS -> {
            SettingsGroup("Key press sound") {
                item {
                    ToggleSetting(
                        "Key sound", "Play a sound on every key press", settings.keySound,
                    ) { scope.launch { repository.setKeySound(it) } }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Sound style", style = MaterialTheme.typography.bodyLarge)
                        InfoButton(
                            "Sound style",
                            "All five come from the device's system sound pack, so they match " +
                                "the stock keyboard's palette: Click is the classic key tick, " +
                                "Standard the softer AOSP key press, Pop the spacebar thump, " +
                                "Thock the deeper delete sound, and Chime the return-key sound.",
                        )
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)) {
                        KeySoundStyle.entries.forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = settings.keySoundStyle == style,
                                onClick = { scope.launch { repository.setKeySoundStyle(style) } },
                                shape = SegmentedButtonDefaults.itemShape(index, KeySoundStyle.entries.size),
                            ) {
                                Text(
                                    when (style) {
                                        KeySoundStyle.CLICK -> "Click"
                                        KeySoundStyle.STANDARD -> "Std"
                                        KeySoundStyle.POP -> "Pop"
                                        KeySoundStyle.THOCK -> "Thock"
                                        KeySoundStyle.CHIME -> "Chime"
                                    },
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                item {
                    SliderSetting(
                        "Sound volume",
                        subtitle = "Relative to the system media volume",
                        value = settings.keySoundVolume,
                        range = 0.05f..1f,
                        display = "${(settings.keySoundVolume * 100).roundToInt()}%",
                    ) { scope.launch { repository.setKeySoundVolume(it) } }
                }
            }
            CaptionText(
                "Haptic style and strength live under Key press → Haptic feedback; " +
                    "the tool's panel changes the same settings from the keyboard.",
            )
        }
        ToolbarTool.HANDWRITING -> {
            SettingsGroup("Input") {
                item {
                    ToggleSetting(
                        "Stylus only",
                        "Only an S Pen or other stylus draws; finger touches are ignored",
                        settings.handwritingStylusOnly,
                        info = "Useful for palm rejection while writing with a stylus. Even " +
                            "with this off, finger touches are briefly ignored right after " +
                            "stylus strokes, so a resting palm doesn't scribble.",
                    ) { scope.launch { repository.setHandwritingStylusOnly(it) } }
                }
                item {
                    ToggleSetting(
                        "Auto space",
                        "Insert a space between consecutively written words",
                        settings.handwritingAutoSpace,
                    ) { scope.launch { repository.setHandwritingAutoSpace(it) } }
                }
                item {
                    SliderSetting(
                        "Recognition pause",
                        subtitle = "How long after the last stroke before the word is recognized",
                        value = settings.handwritingCommitDelayMs.toFloat(),
                        range = 300f..2000f,
                        display = "${settings.handwritingCommitDelayMs} ms",
                        info = "Shorter feels snappier but can cut multi-stroke letters and " +
                            "Bengali conjuncts in half; longer gives you more time between " +
                            "strokes. Gboard uses roughly half a second.",
                    ) { scope.launch { repository.setHandwritingCommitDelayMs(it.roundToInt()) } }
                }
            }
            SectionHeader("Recognition models")
            CaptionText(
                "Recognition runs fully on-device with Google ML Kit. Each language " +
                    "needs a one-time model download (about 20 MB); after that, " +
                    "handwriting works offline.",
            )
            HandwritingModelManager()
        }
        else -> {}
    }
}

/**
 * Download/delete state for each supported handwriting model. Status is
 * re-read from ML Kit's model manager after every action.
 */
@Composable
private fun HandwritingModelManager() {
    val scope = rememberCoroutineScope()
    // tag -> "checking" | "missing" | "downloaded" | "downloading" | "error"
    val statuses = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(Unit) {
        for (language in HandwritingModels.supported) {
            statuses[language.tag] =
                if (HandwritingModels.isDownloaded(language.tag)) "downloaded" else "missing"
        }
    }
    SettingsGroup {
        for (language in HandwritingModels.supported) {
            item {
                val status = statuses[language.tag] ?: "checking"
                ListItem(
                    headlineContent = { Text(language.displayName) },
                    supportingContent = {
                        Text(
                            when (status) {
                                "checking" -> "Checking…"
                                "downloaded" -> "Downloaded — works offline"
                                "downloading" -> "Downloading…"
                                "error" -> "Download failed — check your connection"
                                else -> "Not downloaded"
                            },
                        )
                    },
                    trailingContent = {
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
                                    contentDescription = "Delete ${language.displayName} model",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            else -> TextButton(onClick = {
                                statuses[language.tag] = "downloading"
                                scope.launch {
                                    val ok = runCatching { HandwritingModels.download(language.tag) }.isSuccess
                                    statuses[language.tag] = if (ok) "downloaded" else "error"
                                }
                            }) { Text("Download") }
                        }
                    },
                    colors = transparentListColors(),
                )
            }
        }
    }
}

/** Weather location: place label plus coordinates, edited in a dialog. */
@Composable
private fun WeatherLocationSetting(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    val summary = if (settings.weatherLatitude != null && settings.weatherLongitude != null) {
        val place = settings.weatherPlaceName.ifBlank { "Unnamed location" }
        "%s — %.3f, %.3f".format(place, settings.weatherLatitude, settings.weatherLongitude)
    } else {
        "Not set — tap to add coordinates"
    }
    ListItem(
        headlineContent = { Text("Location") },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(Icons.Outlined.Edit, contentDescription = "Edit location") },
        colors = transparentListColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { editing = true },
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
        title = { Text("Weather location") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search city or place") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { search() }, enabled = query.isNotBlank() && !searching) {
                        Text(if (searching) "…" else "Search")
                    }
                }
                if (searchFailed) {
                    Text(
                        if (results.isEmpty() && !searching) "No matches — try another spelling."
                        else "Search failed — check your connection.",
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
                                "%s · %.3f, %.3f".format(
                                    result.region.ifBlank { "—" },
                                    result.latitude, result.longitude,
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
                    "Or enter coordinates yourself (decimal degrees; south and " +
                        "west are negative).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text("Name (shown on the panel)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Latitude, e.g. 23.81") },
                    singleLine = true,
                    isError = lat.isNotBlank() && parsedLat == null,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it },
                    label = { Text("Longitude, e.g. 90.41") },
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
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (settings.weatherLatitude != null) {
                    TextButton(onClick = {
                        scope.launch { repository.setWeatherLocation(null, null, "") }
                        editing = false
                    }) { Text("Clear") }
                }
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        },
    )
}

// ---- privacy ----

@Composable
private fun PrivacySettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    SettingsGroup("On-device learning") {
        item {
            ToggleSetting(
                "Learn from typing",
                "Personalize suggestions on-device. Nothing ever leaves your phone.",
                settings.learnFromTyping,
                info = "Words and word pairs you type are stored in a private on-device " +
                    "dictionary to improve suggestions and gesture typing. Learning is skipped " +
                    "in password fields and while incognito mode is on, and can be wiped below " +
                    "at any time.",
            ) { scope.launch { repository.setLearnFromTyping(it) } }
        }
        item {
            ToggleSetting(
                "Incognito mode",
                "Pause learning and clipboard capture",
                settings.incognito,
                info = "While on, the keyboard learns nothing from your typing and clipboard " +
                    "history is not recorded. Existing learned words are untouched.",
            ) { scope.launch { repository.setIncognito(it) } }
        }
    }
    SettingsGroup("Your data") {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                OutlinedButton(onClick = {
                    java.io.File(context.filesDir, "learning/user_lexicon.json").delete()
                    java.io.File(context.filesDir, "learning/emoji_usage.json").delete()
                }) { Text("Clear learned words") }
            }
        }
    }
    CaptionText(
        "WM Keyboard works offline: dictionaries, Bengali transliteration and " +
            "emoji search are all bundled, and there is no telemetry. The one " +
            "exception is the optional weather tool, which fetches conditions from " +
            "Open-Meteo only when you open it (and can be disabled under Tools).",
    )
}

// ---- snippets ----

@Composable
private fun SnippetSettings() {
    val context = LocalContext.current
    val store = remember {
        SnippetStore(java.io.File(context.filesDir, "snippets/snippets.json"))
    }
    var snippets by remember { mutableStateOf(store.items()) }
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Text(
        "Snippets are reusable pieces of text — an address, an email sign-off, a " +
            "canned reply — inserted from the keyboard's snippet panel with one tap.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Template variables", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            // Live examples: expand the actual templates so the preview
            // always matches what an insertion would produce right now.
            VariableRow("{date}", "today's date", SnippetStore.expand("{date}"))
            VariableRow("{time}", "current time", SnippetStore.expand("{time}"))
            VariableRow("{datetime}", "date and time", SnippetStore.expand("{datetime}"))
            VariableRow("{clip}", "latest clipboard entry", "whatever you copied last")
            Spacer(Modifier.height(8.dp))
            Text(
                "Variables expand at the moment the snippet is inserted, not when it " +
                    "is saved — so {date} always produces the current date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { showAdd = true },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text("Add snippet") }
    Spacer(Modifier.height(12.dp))
    SettingsGroup {
        for (snippet in snippets) {
            item {
                ListItem(
                    headlineContent = { Text(snippet.label) },
                    supportingContent = {
                        Column {
                            Text(snippet.text, maxLines = 2)
                            if (snippet.text != SnippetStore.expand(snippet.text, clipboard = "…")) {
                                Text(
                                    "Inserts as: " + SnippetStore.expand(snippet.text, clipboard = "…"),
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editing = snippet }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = {
                                store.remove(snippet.id)
                                store.save()
                                snippets = store.items()
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                            }
                        }
                    },
                    colors = transparentListColors(),
                )
            }
        }
    }

    if (showAdd || editing != null) {
        SnippetDialog(
            initial = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { label, text ->
                val current = editing
                if (current == null) store.add(label, text) else store.update(current.id, label, text)
                store.save()
                snippets = store.items()
                showAdd = false
                editing = null
            },
        )
    }
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
                "e.g. $example",
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
    onSave: (String, String) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New snippet" else "Edit snippet") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text — supports {date} {time} {datetime} {clip}") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && text.isNotBlank(),
                onClick = { onSave(label.trim(), text) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
