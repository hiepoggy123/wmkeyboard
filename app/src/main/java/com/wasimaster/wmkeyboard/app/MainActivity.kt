package com.wasimaster.wmkeyboard.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.os.Build
import com.wasimaster.wmkeyboard.core.settings.InputMode
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import com.wasimaster.wmkeyboard.core.settings.ThemeMode
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
            val settings by repository.settings.collectAsStateWithLifecycle(KeyboardSettings())
            AppTheme(settings) {
                SettingsNavHost(repository, settings)
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
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                settings = settings,
                onNavigate = { navController.navigate(it) },
            )
        }
        composable("typing") {
            SettingsScreen("Typing", { navController.popBackStack() }) {
                TypingSettings(repository, settings)
            }
        }
        composable("appearance") {
            SettingsScreen("Appearance & themes", { navController.popBackStack() }) {
                AppearanceSettings(repository, settings)
            }
        }
        composable("languages") {
            SettingsScreen("Languages", { navController.popBackStack() }) {
                LanguageSettings(repository, settings)
            }
        }
        composable("clipboard") {
            SettingsScreen("Clipboard & emoji", { navController.popBackStack() }) {
                ClipboardEmojiSettings(repository, settings)
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
    Scaffold(
        topBar = { TopAppBar(title = { Text("WM Keyboard") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SetupCard(context)
            Spacer(Modifier.height(16.dp))
            SectionItem("Typing", "Autocorrect, suggestions, key behavior") { onNavigate("typing") }
            SectionItem("Appearance & themes", "Material You, AMOLED, key size") { onNavigate("appearance") }
            SectionItem("Languages", "English, বাংলা (Avro phonetic, প্রভাত)") { onNavigate("languages") }
            SectionItem("Clipboard & emoji", "History, expiry, toolbar") { onNavigate("clipboard") }
            SectionItem("Privacy", "On-device learning, incognito") { onNavigate("privacy") }
        }
    }
}

@Composable
private fun SetupCard(context: Context) {
    val imm = remember { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    val enabled = imm.enabledInputMethodList.any { it.packageName == context.packageName }
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
private fun SectionItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
    HorizontalDivider()
}

// ---- shared scaffold ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) { content() }
    }
}

@Composable
private fun ToggleSetting(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(display, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

// ---- sections ----

@Composable
private fun TypingSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    ToggleSetting("Autocorrect", "Fix typos when you press space", settings.autocorrect) {
        scope.launch { repository.setAutocorrect(it) }
    }
    ToggleSetting("Suggestions", "Show the suggestion bar while typing", settings.suggestions) {
        scope.launch { repository.setSuggestions(it) }
    }
    ToggleSetting("Gesture typing", "Swipe across letters to type a word", settings.gestureTyping) {
        scope.launch { repository.setGestureTyping(it) }
    }
    ToggleSetting("Spacebar cursor control", "Slide on the spacebar to move the cursor", settings.spacebarCursor) {
        scope.launch { repository.setSpacebarCursor(it) }
    }
    ToggleSetting("Auto-capitalize", "Capitalize the first word of sentences", settings.autoCapitalize) {
        scope.launch { repository.setAutoCapitalize(it) }
    }
    ToggleSetting("Double-space period", "Two spaces insert “. ”", settings.doubleSpacePeriod) {
        scope.launch { repository.setDoubleSpacePeriod(it) }
    }
    ToggleSetting("Number row", "Dedicated row of digits above the letters", settings.numberRow) {
        scope.launch { repository.setNumberRow(it) }
    }
    ToggleSetting("Key press haptics", null, settings.hapticFeedback) {
        scope.launch { repository.setHapticFeedback(it) }
    }
    SliderSetting(
        "Haptic strength", settings.hapticStrengthMs.toFloat(), 5f..60f,
        "${settings.hapticStrengthMs} ms",
    ) { scope.launch { repository.setHapticStrengthMs(it.toInt()) } }
    ToggleSetting("Key popup", "Show a character bubble while pressing", settings.keyPopup) {
        scope.launch { repository.setKeyPopup(it) }
    }
    SliderSetting(
        "Long-press delay", settings.longPressDelayMs.toFloat(), 150f..700f,
        "${settings.longPressDelayMs} ms",
    ) { scope.launch { repository.setLongPressDelayMs(it.toInt()) } }
    SliderSetting(
        "Key repeat interval", settings.keyRepeatIntervalMs.toFloat(), 20f..200f,
        "${settings.keyRepeatIntervalMs} ms",
    ) { scope.launch { repository.setKeyRepeatIntervalMs(it.toInt()) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    Text("Theme", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
    SingleChoiceSegmentedButtonRow(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = settings.themeMode == mode,
                onClick = { scope.launch { repository.setThemeMode(mode) } },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
            ) {
                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
    }
    ToggleSetting("Material You colors", "Use wallpaper-based dynamic color", settings.dynamicColor) {
        scope.launch { repository.setDynamicColor(it) }
    }
    SliderSetting("Key height", settings.keyHeightDp.toFloat(), 40f..80f, "${settings.keyHeightDp} dp") {
        scope.launch { repository.setKeyHeightDp(it.toInt()) }
    }
    SliderSetting(
        "Key corner radius", settings.keyCornerRadiusDp.toFloat(), 0f..28f,
        "${settings.keyCornerRadiusDp} dp",
    ) { scope.launch { repository.setKeyCornerRadiusDp(it.toInt()) } }
    SliderSetting("Font size", settings.fontScale, 0.7f..1.5f, "×%.2f".format(settings.fontScale)) {
        scope.launch { repository.setFontScale(it) }
    }
}

@Composable
private fun LanguageSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    Text(
        "Enabled input modes (cycle with the 🌐 key)",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp),
    )
    val labels = mapOf(
        InputMode.ENGLISH to ("English" to "QWERTY with suggestions"),
        InputMode.AVRO to ("বাংলা — Avro phonetic" to "Type \"ami valo achi\", get আমি ভালো আছি"),
        InputMode.PROBHAT to ("বাংলা — প্রভাত (Probhat)" to "Fixed Bengali layout"),
    )
    for (mode in InputMode.entries) {
        val (title, subtitle) = labels.getValue(mode)
        ToggleSetting(title, subtitle, mode in settings.enabledModes) { enable ->
            scope.launch {
                val next = if (enable) settings.enabledModes + mode else settings.enabledModes - mode
                if (next.isNotEmpty()) repository.setEnabledModes(next.distinct())
            }
        }
    }
}

@Composable
private fun ClipboardEmojiSettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    ToggleSetting("Clipboard history", "Save copied text for quick paste", settings.clipboardHistory) {
        scope.launch { repository.setClipboardHistory(it) }
    }
    SliderSetting(
        "Clipboard expiry",
        settings.clipboardExpiryHours.toFloat(), 0f..168f,
        if (settings.clipboardExpiryHours == 0) "never" else "${settings.clipboardExpiryHours} h",
    ) { scope.launch { repository.setClipboardExpiryHours(it.toInt()) } }
    ToggleSetting("Emoji button in toolbar", "One-tap emoji access", settings.emojiToolbar) {
        scope.launch { repository.setEmojiToolbar(it) }
    }
}

@Composable
private fun PrivacySettings(repository: SettingsRepository, settings: KeyboardSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ToggleSetting(
        "Learn from typing",
        "Personalize suggestions on-device. Nothing ever leaves your phone.",
        settings.learnFromTyping,
    ) { scope.launch { repository.setLearnFromTyping(it) } }
    ToggleSetting(
        "Incognito mode",
        "Pause learning and clipboard capture",
        settings.incognito,
    ) { scope.launch { repository.setIncognito(it) } }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = {
        java.io.File(context.filesDir, "learning/user_lexicon.json").delete()
        java.io.File(context.filesDir, "learning/emoji_usage.json").delete()
    }) { Text("Clear learned words") }
    Spacer(Modifier.height(4.dp))
    Text(
        "WM Keyboard works fully offline: dictionaries, Bengali transliteration and " +
            "emoji search are all bundled. There is no telemetry.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}
