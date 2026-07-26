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
import androidx.compose.material3.ListItem
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
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictCatalog
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictDownloadManager
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictPack
import com.wasimaster.wmkeyboard.core.input.composer.DoublePinyin
import com.wasimaster.wmkeyboard.core.input.composer.DoublePinyinScheme
import com.wasimaster.wmkeyboard.core.layout.language
import com.wasimaster.wmkeyboard.core.layout.resolveLayout
import com.wasimaster.wmkeyboard.core.script.LanguageDef
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.script.NumeralSystem
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import kotlinx.coroutines.launch

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
private fun scriptLabel(lang: LanguageDef): String =
    lang.script.name.lowercase().replaceFirstChar { it.uppercase() }

/**
 * The searchable add-language list, over every [LanguageRegistry] entry. Filters
 * on endonym, English name, id and locale so "german", "deutsch", "de" and
 * "de-DE" all find it. Tapping a not-yet-added language enables its default
 * layout, then opens its detail so the user can pick others or a secondary.
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
    val matches = LanguageRegistry.all.filter { lang ->
        q.isEmpty() ||
            lang.displayName.lowercase().contains(q) ||
            lang.englishName.lowercase().contains(q) ||
            lang.id.lowercase().contains(q) ||
            lang.localeTag.lowercase().contains(q)
    }

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
    SettingsGroup {
        for (lang in matches) {
            item {
                val added = lang.id in enabledLangIds
                NavRow(
                    lang.displayName,
                    subtitle = scriptLabel(lang) +
                        if (lang.bundledDictionary) " · dictionary included" else "",
                    value = if (added) "Added" else null,
                ) {
                    if (!added) {
                        lang.layoutIds.firstOrNull()?.let { first ->
                            scope.launch {
                                repository.setEnabledLayoutIds(
                                    (settings.enabledLayoutIds + first).distinct(),
                                )
                            }
                        }
                    }
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
            NavRow("Custom dictionaries", "Import your own word lists") {
                onNavigate("customdictionaries")
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

    ListItem(
        colors = transparentListColors(),
        headlineContent = {
            Text(entry.variant?.let { "Downloadable dictionary ($it)" } ?: "Downloadable dictionary")
        },
        supportingContent = {
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
        trailingContent = {
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
                ListItem(
                    headlineContent = { Text(pack.displayName) },
                    supportingContent = { Text(packStatusLabel(pack, status)) },
                    trailingContent = {
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
                    colors = transparentListColors(),
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
                    val select = { scope.launch { repository.setPinyinDoublePinyin(scheme) }; Unit }
                    ListItem(
                        headlineContent = {
                            Text(if (ready) scheme.displayName else "${scheme.displayName} — coming soon")
                        },
                        trailingContent = {
                            RadioButton(
                                selected = settings.cjk.pinyinDoublePinyin == scheme,
                                enabled = ready,
                                onClick = select,
                            )
                        },
                        colors = transparentListColors(),
                        modifier = if (ready) {
                            Modifier.fillMaxWidth().clickable(onClick = select)
                        } else {
                            Modifier.fillMaxWidth()
                        },
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
