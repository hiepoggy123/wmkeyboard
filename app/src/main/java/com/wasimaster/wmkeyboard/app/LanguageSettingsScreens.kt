package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.core.dictionaries.DictionaryCatalog
import com.wasimaster.wmkeyboard.core.dictionaries.DictionaryEntry
import com.wasimaster.wmkeyboard.core.dictionaries.WordlistDownloadManager
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictCatalog
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictDownloadManager
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictEntry
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictCatalog
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictDownloadManager
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictPack
import com.wasimaster.wmkeyboard.core.input.composer.DoublePinyin
import com.wasimaster.wmkeyboard.core.input.composer.DoublePinyinScheme
import com.wasimaster.wmkeyboard.core.input.composer.HanVariant
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.resolveLayout
import com.wasimaster.wmkeyboard.core.script.ComposerType
import com.wasimaster.wmkeyboard.core.script.DeviceLocales
import com.wasimaster.wmkeyboard.core.script.LanguageDef
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.script.LanguageSuggestions
import com.wasimaster.wmkeyboard.core.script.NumeralSystem
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.script.ScriptRegistry
import com.wasimaster.wmkeyboard.core.script.SuggestedLanguage
import com.wasimaster.wmkeyboard.core.script.SuggestionReason
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The screens that make the [LanguageRegistry] reachable: a searchable list to
 * add a language, and a per-language detail with its layouts, secondary
 * suggestion sources, dictionary status and a remove action. They live here
 * rather than in `MainActivity` both to keep that file's churn down and because
 * they are a self-contained feature; the reusable rows (`SettingsGroup`,
 * `NavRow`, `ToggleSetting`, `CaptionText`) are `internal` in the same package.
 *
 * Everything reads the registry, not the old static `LanguageCatalog`: the
 * "Your languages" list is [KeyboardSettings.enabledLanguages] (already deduped
 * in switch order by the repository), and a language's layouts come from
 * [LanguageDef.layoutIds] resolved for their display names.
 */

/** A human label for a language's script, e.g. LATIN → "Latin". */
internal fun scriptLabel(lang: LanguageDef): String =
    lang.script.name.lowercase().replaceFirstChar { it.uppercase() }

/**
 * The one-line subtitle a language gets wherever it is offered for adding — here
 * and on the onboarding languages page, which browses the same registry.
 */
internal fun languageRowSubtitle(lang: LanguageDef): String =
    scriptLabel(lang) + if (lang.bundledDictionary) " · dictionary included" else ""

/**
 * Matches a language against an already-lowercased search term on endonym,
 * English name, id and locale, so "german", "deutsch", "de" and "de-DE" all find
 * it. An empty term matches everything.
 */
internal fun LanguageDef.matchesQuery(query: String): Boolean =
    query.isEmpty() ||
        displayName.lowercase().contains(query) ||
        englishName.lowercase().contains(query) ||
        id.lowercase().contains(query) ||
        localeTag.lowercase().contains(query)

/**
 * The enabled languages, for the one-line summary under the Languages row.
 *
 * Endonyms, in switch order, trimmed to the first few — the row is one line and
 * someone typing in eight languages does not need all eight recited at them.
 */
internal fun enabledLanguagesSummary(settings: KeyboardSettings): String {
    val names = settings.enabledLanguages.map { it.displayName.substringBefore(" · ") }
    if (names.isEmpty()) return "No languages enabled"
    val shown = names.take(LANGUAGE_SUMMARY_LIMIT).joinToString()
    val rest = names.size - LANGUAGE_SUMMARY_LIMIT
    return if (rest > 0) "$shown +$rest more" else shown
}

private const val LANGUAGE_SUMMARY_LIMIT = 3

/**
 * How many suggestions the Languages screen offers before the user has to go
 * through "Add language". Short enough that it reads as a shortcut rather than
 * as a second list.
 */
internal const val LANGUAGE_SCREEN_SUGGESTIONS = 4

/**
 * The languages this device suggests, minus whatever is already enabled.
 *
 * Read once and cached for as long as the screen lives: the phone's language
 * list and SIM don't change mid-screen, and re-reading them on every
 * recomposition would put a `TelephonyManager` call in the middle of a list
 * scroll. The already-enabled set is *not* part of the cache key — a language
 * disappearing from the list the instant it is tapped is the point.
 */
@Composable
internal fun rememberSuggestedLanguages(
    settings: KeyboardSettings,
    limit: Int = LanguageSuggestions.DEFAULT_LIMIT,
): List<SuggestedLanguage> {
    val context = LocalContext.current
    val signals = remember(context) { DeviceLocales.read(context) }
    val enabled = settings.enabledLanguages.mapTo(HashSet()) { it.id }
    return remember(signals, enabled, limit) {
        LanguageSuggestions.suggest(signals, exclude = enabled, limit = limit)
    }
}

/**
 * Why a suggestion is being offered, in one line under its name. Region names
 * come from the platform, so they arrive in the user's own language.
 */
internal fun suggestionReasonLabel(suggestion: SuggestedLanguage): String = when (suggestion.reason) {
    SuggestionReason.SYSTEM_LANGUAGE -> "One of your phone's languages"
    SuggestionReason.REGION -> {
        val region = suggestion.regionCode
            ?.let { Locale.Builder().setRegion(it).build().displayCountry }
            ?.takeIf { it.isNotBlank() }
        if (region != null) "Widely typed in $region" else "Widely typed near you"
    }
    SuggestionReason.FALLBACK -> languageRowSubtitle(suggestion.language)
}

/**
 * The searchable add-language list, over every [LanguageRegistry] entry — see
 * [matchesQuery] for what a search term is compared against. Tapping a
 * not-yet-added language enables its default layout, then opens its detail so
 * the user can pick others or a secondary.
 */
@Composable
internal fun AddLanguageScreen(
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onOpenLanguage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val enabledLangIds = settings.enabledLanguages.mapTo(HashSet()) { it.id }
    val q = query.trim().lowercase()
    val matches = LanguageRegistry.all.filter { it.matchesQuery(q) }
    val suggested = rememberSuggestedLanguages(settings)

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
    // Only while browsing: once someone is searching, they know what they want
    // and a suggestion block above the results is in the way.
    if (q.isEmpty() && suggested.isNotEmpty()) {
        SettingsGroup("Suggested for you") {
            for (suggestion in suggested) {
                item {
                    NavRow(
                        suggestion.language.displayName,
                        subtitle = suggestionReasonLabel(suggestion),
                    ) {
                        addLanguage(scope, repository, settings, suggestion.language)
                        onOpenLanguage(suggestion.language.id)
                    }
                }
            }
            item {
                CaptionText(
                    "From your phone's own language settings and region. Nothing " +
                        "you type is looked at, and nothing leaves the device.",
                )
            }
        }
    }
    SettingsGroup(if (q.isEmpty() && suggested.isNotEmpty()) "All languages" else null) {
        for (lang in matches) {
            item {
                val added = lang.id in enabledLangIds
                NavRow(
                    lang.displayName,
                    subtitle = languageRowSubtitle(lang),
                    value = if (added) "Added" else null,
                ) {
                    if (!added) addLanguage(scope, repository, settings, lang)
                    onOpenLanguage(lang.id)
                }
            }
        }
        if (matches.isEmpty()) {
            item { CaptionText("No languages match “$query”.") }
        }
    }
}

/**
 * A cluster the reader will recognise, for the conjunct-backspace row. A script
 * with no sample here still gets the setting; the row just describes it in words
 * rather than showing one, which beats showing a Bengali cluster to someone
 * setting up Khmer.
 */
private fun conjunctSample(script: ScriptId): String = when (script) {
    ScriptId.BENGALI -> "ক্ষ"
    ScriptId.DEVANAGARI -> "क्ष"
    ScriptId.GURMUKHI -> "ਕ੍ਸ਼"
    ScriptId.GUJARATI -> "ક્ષ"
    ScriptId.ORIYA -> "କ୍ଷ"
    ScriptId.TAMIL -> "க்ஷ"
    ScriptId.TELUGU -> "క్ష"
    ScriptId.KANNADA -> "ಕ್ಷ"
    ScriptId.MALAYALAM -> "ക്ഷ"
    ScriptId.SINHALA -> "ක්ෂ"
    ScriptId.KHMER -> "ក្ស"
    ScriptId.MYANMAR -> "က္ခ"
    ScriptId.TIBETAN -> "ཀྵ"
    else -> "a conjunct"
}

/**
 * Adds a language by enabling its first layout. The rest of its layouts, and any
 * secondary suggestion sources, are then a tap away on its detail screen — which
 * is where every caller sends the user next.
 */
internal fun addLanguage(
    scope: CoroutineScope,
    repository: SettingsRepository,
    settings: KeyboardSettings,
    language: LanguageDef,
) {
    val first = language.layoutIds.firstOrNull() ?: return
    scope.launch {
        repository.setEnabledLayoutIds((settings.enabledLayoutIds + first).distinct())
    }
}

/**
 * One language: toggle its layouts on/off (at least one layout overall must stay
 * enabled), pick other enabled languages as secondary suggestion sources, see
 * its dictionary status, and remove it. [onRemoved] pops back to the list once
 * the language is gone.
 */
@Composable
internal fun LanguageDetailScreen(
    langId: String,
    repository: SettingsRepository,
    settings: KeyboardSettings,
    onNavigate: (String) -> Unit,
    onRemoved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lang = LanguageRegistry.byId(langId)

    SettingsGroup("Layouts") {
        for (layoutId in lang.layoutIds) {
            item {
                val name = resolveLayout(settings.customLayouts, layoutId).name
                ToggleSetting(name, null, layoutId in settings.enabledLayoutIds) { enable ->
                    scope.launch {
                        val next =
                            if (enable) settings.enabledLayoutIds + layoutId
                            else settings.enabledLayoutIds - layoutId
                        // At least one layout must stay enabled somewhere.
                        if (next.isNotEmpty()) repository.setEnabledLayoutIds(next.distinct())
                    }
                }
            }
        }
    }

    // Cluster deletion, for the languages whose script has clusters to delete.
    // Per language for the same reason numerals are: someone typing Bengali and
    // Hindi together may well want whole conjuncts gone in one and code points
    // in the other, and a single global switch made that impossible.
    if (ScriptRegistry[lang.script].composer == ComposerType.INDIC_CLUSTER) {
        SettingsGroup("Clusters") {
            item {
                val sample = conjunctSample(lang.script)
                ToggleSetting(
                    "Conjunct-aware backspace",
                    "Delete a whole cluster like $sample as one unit",
                    langId in settings.conjunctBackspaceLanguages,
                    info = "Normally backspace removes one code point at a time, which can " +
                        "leave half-formed clusters on screen. With this on, one press " +
                        "deletes the whole cluster while typing ${lang.englishName}. " +
                        "Other languages keep their own setting.",
                ) { scope.launch { repository.setConjunctBackspace(langId, it) } }
            }
        }
    }

    // Numerals are per language: Arabic can type ٠-٩ while English beside it
    // stays 0-9. Auto is the language's own default, so most languages never
    // need touching.
    SettingsGroup("Numerals") {
        item {
            ChoiceSetting(
                "Numeral system",
                subtitle = "Digits the number row and keypad show while typing " +
                    "${lang.englishName}",
                info = "Auto uses ${lang.englishName}'s own digits — " +
                    "${lang.numeralSystem.label} — and any other choice forces that system for " +
                    "this language only. The keys always display these glyphs; where they are " +
                    "also typed is set in Layout & size → Numerals.",
                options = NumeralSystem.entries.map { it to it.label },
                selected = settings.layoutBehavior.numeralSystemFor(langId),
            ) { scope.launch { repository.setNumeralSystemForLanguage(langId, it) } }
        }
    }

    val others = settings.enabledLanguages.filter { it.id != langId }
    if (others.isNotEmpty()) {
        val secondaries = settings.secondaryLanguages[langId].orEmpty()
        SettingsGroup("Also suggest from") {
            item {
                CaptionText(
                    "Words from these languages are offered while you type " +
                        "${lang.englishName}, and are never autocorrected away.",
                )
            }
            for (other in others) {
                item {
                    ToggleSetting(other.displayName, null, other.id in secondaries) { on ->
                        scope.launch {
                            val cur = settings.secondaryLanguages[langId].orEmpty()
                            val nextList = if (on) cur + other.id else cur - other.id
                            repository.setSecondaryLanguages(
                                settings.secondaryLanguages + (langId to nextList.distinct()),
                            )
                        }
                    }
                }
            }
        }
    }

    val wordlistEntries = DictionaryCatalog.forLanguage(langId)
    SettingsGroup("Dictionary") {
        item {
            CaptionText(
                when {
                    lang.bundledDictionary && wordlistEntries.isNotEmpty() ->
                        "A built-in dictionary ships with this language — download a " +
                            "bigger one for better suggestions."
                    lang.bundledDictionary ->
                        "A built-in dictionary ships with this language."
                    wordlistEntries.isNotEmpty() ->
                        "No dictionary is bundled — download one for suggestions and " +
                            "autocorrect, or import your own list."
                    else ->
                        "No dictionary is bundled — the keyboard learns your words as you " +
                            "type, and you can import your own list."
                },
            )
        }
        for (entry in wordlistEntries) {
            item { WordlistRow(entry) }
        }
        item {
            NavRow(
                "Custom dictionaries",
                "Import your own word lists",
                route = "customdictionaries",
            ) {
                onNavigate("customdictionaries")
            }
        }
    }

    val emojiDict = EmojiDictCatalog.forLanguage(langId)
    SettingsGroup("Emoji") {
        item {
            CaptionText(
                if (emojiDict != null) {
                    "Emoji keywords let you search emoji in ${lang.englishName} — " +
                        "typing its word for \"cake\" finds 🎂." +
                        if (settings.emoji.autoDownloadKeywords) {
                            " Downloaded automatically while the language is on."
                        } else {
                            " Automatic downloads are off, so this one is yours to start."
                        }
                } else {
                    "No emoji keywords are available for ${lang.englishName} yet, so " +
                        "emoji search answers in English. You can import your own list."
                },
            )
        }
        if (emojiDict != null) {
            item { EmojiDictRow(emojiDict) }
        }
        item {
            NavRow(
                "Emoji keywords",
                "Downloads for every language, and your own imports",
                route = "emojikeywords",
            ) {
                onNavigate("emojikeywords")
            }
        }
    }

    // Chinese/Japanese get a downloadable large conversion dictionary; Chinese
    // also gets fuzzy + Double Pinyin, all in one "… options" group.
    if (CjkDictCatalog.forLang(langId).isNotEmpty()) {
        CjkDictPackManager(langId, repository, settings)
    }

    // Removing the only language would leave nothing to type in, so it is only
    // offered when another language is enabled.
    if (settings.enabledLanguages.size > 1) {
        SettingsGroup {
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val next = settings.enabledLayoutIds.filterNot {
                                resolveLayout(settings.customLayouts, it).language().id == langId
                            }
                            if (next.isNotEmpty()) {
                                repository.setEnabledLayoutIds(next.distinct())
                                onRemoved()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) { Text("Remove ${lang.englishName}") }
            }
        }
    }
}

/**
 * Download/delete row for one language's emoji dictionary.
 *
 * Simpler than [WordlistRow] because the payload is: no size tier (the whole
 * file is ~100 KB), and a determinate progress bar, since nothing stops the
 * transfer early. Shared with the Emoji keywords screen, so a language shows
 * the same state wherever it is looked at.
 */
@Composable
internal fun EmojiDictRow(entry: EmojiDictEntry) {
    val filesDir = LocalContext.current.filesDir
    val states by EmojiDictDownloadManager.states.collectAsState()
    LaunchedEffect(entry.languageId) { EmojiDictDownloadManager.refresh(filesDir) }
    val status = states[entry.languageId]
        ?: EmojiDictDownloadManager.DownloadStatus.NotDownloaded

    WmRow(
        title = "Emoji keywords",
        supporting = {
            Text(
                when (status) {
                    is EmojiDictDownloadManager.DownloadStatus.Downloaded ->
                        "%,d emoji · %s".format(status.emojiCount, formatBytes(status.sizeBytes))
                    EmojiDictDownloadManager.DownloadStatus.Queued -> "Waiting…"
                    is EmojiDictDownloadManager.DownloadStatus.Downloading -> "Downloading…"
                    is EmojiDictDownloadManager.DownloadStatus.Failed -> status.message
                    EmojiDictDownloadManager.DownloadStatus.NotDownloaded ->
                        "%,d emoji · %s download".format(
                            entry.emojiCount,
                            formatBytes(entry.approxGzBytes),
                        )
                },
                color = if (status is EmojiDictDownloadManager.DownloadStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    androidx.compose.ui.graphics.Color.Unspecified
                },
            )
        },
        trailing = {
            when (status) {
                is EmojiDictDownloadManager.DownloadStatus.Downloaded ->
                    IconButton(
                        onClick = { EmojiDictDownloadManager.delete(filesDir, entry.languageId) },
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete emoji keywords")
                    }
                EmojiDictDownloadManager.DownloadStatus.Queued,
                is EmojiDictDownloadManager.DownloadStatus.Downloading,
                ->
                    IconButton(
                        onClick = { EmojiDictDownloadManager.cancel(entry.languageId) },
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel download")
                    }
                EmojiDictDownloadManager.DownloadStatus.NotDownloaded,
                is EmojiDictDownloadManager.DownloadStatus.Failed,
                -> TextButton(onClick = { EmojiDictDownloadManager.start(filesDir, entry) }) {
                    Text(
                        if (status is EmojiDictDownloadManager.DownloadStatus.Failed) "Retry"
                        else "Download",
                    )
                }
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    val downloading = status as? EmojiDictDownloadManager.DownloadStatus.Downloading
    if (downloading != null) {
        LinearProgressIndicator(
            progress = {
                if (downloading.totalBytes > 0) {
                    (downloading.bytes.toFloat() / downloading.totalBytes).coerceIn(0f, 1f)
                } else {
                    0f
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

/**
 * Download/delete row for one [DictionaryCatalog] wordlist, driven by the
 * process-level [WordlistDownloadManager] so progress survives navigation
 * (same pattern as the Whisper model rows). Before downloading, the trailing
 * dropdown picks how many of the most frequent words to keep — the choice is
 * a download parameter, not a setting; it is recorded in the file itself.
 */
@Composable
private fun WordlistRow(entry: DictionaryEntry) {
    val filesDir = LocalContext.current.filesDir
    val states by WordlistDownloadManager.states.collectAsState()
    LaunchedEffect(entry.id) { WordlistDownloadManager.refresh(filesDir) }
    val status = states[entry.id] ?: WordlistDownloadManager.DownloadStatus.NotDownloaded
    var size by remember { mutableStateOf(DictionaryCatalog.DictionarySize.MEDIUM) }
    var sizeMenu by remember { mutableStateOf(false) }
    val effectiveWords = minOf(size.wordCap, entry.totalWordCount)

    WmRow(
        title = entry.variant?.let { "Downloadable dictionary ($it)" }
            ?: "Downloadable dictionary",
        supporting = {
            Text(
                when (status) {
                    is WordlistDownloadManager.DownloadStatus.Downloaded ->
                        "%,d words · %s".format(status.wordCount, formatBytes(status.sizeBytes))
                    WordlistDownloadManager.DownloadStatus.Processing -> "Preparing dictionary…"
                    is WordlistDownloadManager.DownloadStatus.Downloading -> "Downloading…"
                    is WordlistDownloadManager.DownloadStatus.Failed -> status.message
                    WordlistDownloadManager.DownloadStatus.NotDownloaded ->
                        "%,d most frequent words".format(effectiveWords)
                },
                color = if (status is WordlistDownloadManager.DownloadStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    androidx.compose.ui.graphics.Color.Unspecified
                },
            )
        },
        trailing = {
            when (status) {
                is WordlistDownloadManager.DownloadStatus.Downloaded ->
                    IconButton(onClick = { WordlistDownloadManager.delete(filesDir, entry) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete dictionary")
                    }
                is WordlistDownloadManager.DownloadStatus.Downloading,
                WordlistDownloadManager.DownloadStatus.Processing,
                ->
                    IconButton(onClick = { WordlistDownloadManager.cancel() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel download")
                    }
                WordlistDownloadManager.DownloadStatus.NotDownloaded,
                is WordlistDownloadManager.DownloadStatus.Failed,
                -> Row {
                    // Hide the size picker when the whole list fits the
                    // smallest tier anyway.
                    if (entry.totalWordCount > DictionaryCatalog.DictionarySize.SMALL.wordCap) {
                        TextButton(
                            onClick = { sizeMenu = true },
                            enabled = !WordlistDownloadManager.isBusy,
                        ) {
                            Text(size.label)
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Dictionary size")
                        }
                        DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                            for (option in DictionaryCatalog.DictionarySize.entries) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "%s · %,d words".format(
                                                option.label,
                                                minOf(option.wordCap, entry.totalWordCount),
                                            ),
                                        )
                                    },
                                    onClick = {
                                        size = option
                                        sizeMenu = false
                                    },
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { WordlistDownloadManager.start(filesDir, entry, size) },
                        enabled = !WordlistDownloadManager.isBusy,
                    ) {
                        Text(
                            if (status is WordlistDownloadManager.DownloadStatus.Failed) "Retry"
                            else "Download",
                        )
                    }
                }
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    when (status) {
        is WordlistDownloadManager.DownloadStatus.Downloading -> Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            // Indeterminate on purpose: a capped download stops early, so
            // bytes-of-total would count to a total it never reaches.
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                "${formatBytes(status.bytes)} downloaded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        WordlistDownloadManager.DownloadStatus.Processing -> Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        else -> Unit
    }
}

/**
 * Download/delete rows for a language's [CjkDictCatalog] packs, driven by the
 * process-level [CjkDictDownloadManager] so progress survives navigation. The
 * pack replaces the small bundled dictionary once fetched (the service reloads
 * on the next field focus). A pack with no hosting URL yet shows "Not available
 * yet" with its download disabled.
 */
@Composable
private fun CjkDictPackManager(
    langId: String,
    repository: SettingsRepository,
    settings: KeyboardSettings,
) {
    val context = LocalContext.current
    val filesDir = context.filesDir
    val scope = rememberCoroutineScope()
    val states by CjkDictDownloadManager.states.collectAsState()
    LaunchedEffect(langId) { CjkDictDownloadManager.refresh(filesDir) }

    // Named from the registry rather than an if-chain, so a new CJK language
    // does not silently inherit another language's heading.
    val groupTitle = "${LanguageRegistry.byId(langId).englishName} options"
    SettingsGroup(groupTitle) {
        item {
            CaptionText(
                "Download a larger conversion dictionary — many more characters and " +
                    "phrases than the built-in set. Works offline once fetched.",
            )
        }
        for (pack in CjkDictCatalog.forLang(langId)) {
            item {
                val status = states[pack.id] ?: CjkDictDownloadManager.DownloadStatus.NotDownloaded
                WmRow(
                    title = pack.displayName,
                    subtitle = packStatusLabel(pack, status),
                    trailing = {
                        when (status) {
                            is CjkDictDownloadManager.DownloadStatus.Downloading ->
                                IconButton(onClick = { CjkDictDownloadManager.cancel() }) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            CjkDictDownloadManager.DownloadStatus.Downloaded ->
                                IconButton(onClick = { CjkDictDownloadManager.delete(filesDir, pack) }) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Delete ${pack.displayName}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            else -> TextButton(
                                enabled = pack.available && !CjkDictDownloadManager.isBusy,
                                onClick = { CjkDictDownloadManager.start(filesDir, pack) },
                            ) {
                                Text(
                                    if (status is CjkDictDownloadManager.DownloadStatus.Paused) "Resume"
                                    else "Download",
                                )
                            }
                        }
                    },
                )
            }
        }

        // Traditional output suits both Chinese (Taiwan) and Cantonese (Hong Kong),
        // so unlike the pinyin options below it is not gated to zh.
        item {
            ToggleSetting(
                "Traditional characters",
                "Convert candidates to Traditional (繁體). Per-character, so a few " +
                    "context-dependent forms may be wrong (发 → 發 / 髮).",
                settings.cjk.traditionalOutput,
            ) { on -> scope.launch { repository.setCjkTraditionalOutput(on) } }
        }

        // Traditional characters are only half of writing Traditional: Taipei
        // says 計程車 where the mainland says 出租車, and no character map
        // reaches that. Only worth showing once the toggle above is on.
        if (settings.cjk.traditionalOutput) {
            item {
                CaptionText(
                    "Regional wording — beyond characters, Taiwan and Hong Kong often " +
                        "use different words for the same thing.",
                )
            }
            for (region in HanVariant.HanRegion.entries) {
                item {
                    val label = when (region) {
                        HanVariant.HanRegion.GENERIC -> "Standard" to "Characters only, no wording changes"
                        HanVariant.HanRegion.TAIWAN -> "Taiwan (臺灣)" to "出租車 → 計程車, 光盤 → 光碟"
                        HanVariant.HanRegion.HONG_KONG -> "Hong Kong (香港)" to "Hong Kong character preferences"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { repository.setCjkHanRegion(region) } }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = settings.cjk.hanRegion == region,
                            onClick = { scope.launch { repository.setCjkHanRegion(region) } },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(label.first)
                            CaptionText(label.second)
                        }
                    }
                }
            }
        }

        // Cantonese-only: the sound mergers most Hong Kong speakers have, and
        // therefore spell — without this, someone who says 你 as lei5 types `lei`
        // and the dictionary (which files it under nei5) offers nothing at all.
        if (langId == "yue") {
            item {
                ToggleSetting(
                    "Lazy pronunciation (懶音)",
                    "Match merged sounds: n↔l, ng↔∅, gw→g before -o, -ng↔-n, -k↔-t…",
                    settings.cjk.jyutpingLazy,
                ) { on -> scope.launch { repository.setJyutpingLazy(on) } }
            }
        }

        // Chinese-only: fuzzy pinyin + Double Pinyin scheme.
        if (langId == "zh") {
            item {
                ToggleSetting(
                    "Fuzzy Pinyin",
                    "Match confusable sounds: zh↔z, ch↔c, sh↔s, n↔l, an↔ang, in↔ing…",
                    settings.cjk.pinyinFuzzy,
                ) { on -> scope.launch { repository.setPinyinFuzzy(on) } }
            }
            item {
                CaptionText(
                    "Double Pinyin — type each syllable in exactly two keys. Needs the " +
                        "Pinyin dictionary above.",
                )
            }
            for (scheme in DoublePinyinScheme.entries) {
                item {
                    // OFF is always selectable; a scheme is live only once its key
                    // table ships (currently Xiaohe), so the rest are shown but
                    // disabled rather than silently doing nothing.
                    val ready = scheme == DoublePinyinScheme.OFF || DoublePinyin.tableFor(scheme) != null
                    val select: () -> Unit = { scope.launch { repository.setPinyinDoublePinyin(scheme) } }
                    WmRow(
                        title = if (ready) scheme.displayName
                        else "${scheme.displayName} — coming soon",
                        trailing = {
                            RadioButton(
                                selected = settings.cjk.pinyinDoublePinyin == scheme,
                                enabled = ready,
                                onClick = select,
                            )
                        },
                        onClick = if (ready) select else null,
                    )
                }
            }
        }
    }
}

/** Supporting-line text for a pack's current download state. */
private fun packStatusLabel(
    pack: CjkDictPack,
    status: CjkDictDownloadManager.DownloadStatus,
): String = when (status) {
    CjkDictDownloadManager.DownloadStatus.NotDownloaded ->
        if (pack.available) pack.description else "Not available yet — coming soon."
    is CjkDictDownloadManager.DownloadStatus.Downloading ->
        if (status.total > 0) "Downloading… ${status.bytes * 100 / status.total}%" else "Downloading…"
    is CjkDictDownloadManager.DownloadStatus.Paused -> "Paused — tap Resume to continue."
    CjkDictDownloadManager.DownloadStatus.Downloaded -> "Downloaded — works offline."
    is CjkDictDownloadManager.DownloadStatus.Failed -> status.message
}
