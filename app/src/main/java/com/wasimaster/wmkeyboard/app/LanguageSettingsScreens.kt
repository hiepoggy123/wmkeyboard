package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.R
import com.wasimaster.wmkeyboard.common.R as CommonR
import com.wasimaster.wmkeyboard.core.dictionaries.DictionaryCatalog
import com.wasimaster.wmkeyboard.core.dictionaries.DictionaryEntry
import com.wasimaster.wmkeyboard.core.dictionaries.DictionaryStore
import com.wasimaster.wmkeyboard.core.dictionaries.WordlistDownloadManager
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictCatalog
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictDownloadManager
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictEntry
import com.wasimaster.wmkeyboard.core.emoji.EmojiDictStore
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictCatalog
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictDownloadManager
import com.wasimaster.wmkeyboard.core.input.composer.CjkDictPack
import com.wasimaster.wmkeyboard.core.input.composer.DoublePinyin
import com.wasimaster.wmkeyboard.core.input.composer.DoublePinyinScheme
import com.wasimaster.wmkeyboard.core.input.composer.HanVariant
import com.wasimaster.wmkeyboard.core.layout.AssetLayouts
import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
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
import java.io.File
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

/**
 * A human label for a language's script, e.g. LATIN → "Latin". Script names are
 * proper nouns, like the language names beside them, so they are not translated.
 */
internal fun scriptLabel(lang: LanguageDef): String =
    lang.script.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }

/**
 * The one-line subtitle a language gets wherever it is offered for adding — here
 * and on the onboarding languages page, which browses the same registry.
 *
 * Composable so the text follows the app's language: it is read while the row is
 * drawn, not cached in a value that outlives a locale change.
 */
@Composable
internal fun languageRowSubtitle(lang: LanguageDef): String =
    if (lang.bundledDictionary) {
        stringResource(R.string.languages_row_subtitle_bundled, scriptLabel(lang))
    } else {
        scriptLabel(lang)
    }

/**
 * One language's search text, lowercased once: endonym, English name, id and
 * locale — so "german", "deutsch", "de" and "de-DE" all find it — plus its
 * layouts' names and ids, so "avro" or "bepo" finds the language that ships
 * that layout for someone who knows the input system but not what the app
 * files it under. An empty term matches everything.
 *
 * There are 352 languages in the registry, and filtering it runs per keystroke
 * in the search box; lowercasing every field on every call built and threw
 * away fourteen hundred strings per letter typed, which is why the strings are
 * built once here. The comparisons are kept field by field rather than
 * concatenated so a query cannot match across a boundary between two names.
 */
internal class LanguageSearchKey(language: LanguageDef) {
    private val displayName = language.displayName.lowercase()
    private val englishName = language.englishName.lowercase()
    private val id = language.id.lowercase()
    private val localeTag = language.localeTag.lowercase()

    /**
     * The layout names ("Avro phonetic", "প্রভাত (Probhat)") and the layout ids
     * minus their `builtin_`/`asset_` prefix ("avro", "fr_bepo"). The prefix is
     * dropped because "builtin" or "asset" would match every language at once.
     */
    private val layouts = language.layoutIds.flatMap { layoutId ->
        listOfNotNull(
            (BuiltInLayouts.byId(layoutId) ?: AssetLayouts.byId(layoutId))?.name?.lowercase(),
            layoutId.substringAfter('_').lowercase(),
        )
    }

    /** [query] must already be trimmed and lowercased. */
    fun matches(query: String): Boolean =
        query.isEmpty() ||
            displayName.contains(query) ||
            englishName.contains(query) ||
            id.contains(query) ||
            localeTag.contains(query) ||
            layouts.any { it.contains(query) }
}

/**
 * Every registry language paired with its search key. The registry is a
 * constant, but the asset layouts' names arrive only once their JSON finishes
 * parsing off the main thread — so the index is memoised on
 * [AssetLayouts.generation] rather than built once, the same way
 * `resolveLayouts` is: an index built before the load would file every asset
 * layout under no name at all, forever.
 */
private object LanguageSearchIndex {
    class Entry(val generation: Int, val index: List<Pair<LanguageDef, LanguageSearchKey>>)

    @Volatile
    var cache: Entry? = null

    fun get(): List<Pair<LanguageDef, LanguageSearchKey>> {
        val generation = AssetLayouts.generation
        cache?.let { if (it.generation == generation) return it.index }
        val built = LanguageRegistry.all.map { it to LanguageSearchKey(it) }
        cache = Entry(generation, built)
        return built
    }
}

/**
 * The registry languages matching an already-trimmed, lowercased [query], in
 * registry order — see [LanguageSearchKey] for what the query is compared
 * against. Shared by the settings and onboarding add-language searches.
 */
internal fun searchLanguages(query: String): List<LanguageDef> =
    if (query.isEmpty()) {
        LanguageRegistry.all
    } else {
        LanguageSearchIndex.get().mapNotNull { (language, key) ->
            language.takeIf { key.matches(query) }
        }
    }

/**
 * The enabled languages, for the one-line summary under the Languages row.
 *
 * Endonyms, in switch order, trimmed to the first few — the row is one line and
 * someone typing in eight languages does not need all eight recited at them.
 */
@Composable
internal fun enabledLanguagesSummary(settings: KeyboardSettings): String {
    val names = settings.enabledLanguages.map { it.displayName.substringBefore(" · ") }
    val shown = names.take(LANGUAGE_SUMMARY_LIMIT).joinToString()
    val rest = names.size - LANGUAGE_SUMMARY_LIMIT
    return when {
        names.isEmpty() -> stringResource(R.string.languages_summary_empty)
        rest > 0 -> pluralStringResource(R.plurals.languages_summary_more, rest, shown, rest)
        else -> shown
    }
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
@Composable
internal fun suggestionReasonLabel(suggestion: SuggestedLanguage): String = when (suggestion.reason) {
    SuggestionReason.SYSTEM_LANGUAGE ->
        stringResource(R.string.languages_suggestion_reason_system)
    SuggestionReason.REGION -> {
        val region = suggestion.regionCode
            ?.let { Locale.Builder().setRegion(it).build().displayCountry }
            ?.takeIf { it.isNotBlank() }
        if (region != null) {
            stringResource(R.string.languages_suggestion_reason_region, region)
        } else {
            stringResource(R.string.languages_suggestion_reason_nearby)
        }
    }
    SuggestionReason.FALLBACK -> languageRowSubtitle(suggestion.language)
}

/**
 * The searchable add-language list, over every [LanguageRegistry] entry — see
 * [LanguageSearchKey] for what a search term is compared against. Tapping a
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
    val filesDir = LocalContext.current.filesDir
    var query by remember { mutableStateOf("") }
    val enabledLangIds = remember(settings.enabledLanguages) {
        settings.enabledLanguages.mapTo(HashSet()) { it.id }
    }
    val q = query.trim().lowercase()
    // Both remembered on the query: this whole screen recomposes on every
    // letter typed into the search box, and re-running the filter over 352
    // languages — and rebuilding the enabled-id set — for a query that has not
    // changed is work with no result to show for it.
    val matches = remember(q) { searchLanguages(q) }
    val suggested = rememberSuggestedLanguages(settings)
    // The language is enabled straight away either way; the prompt only decides
    // whether its data comes down now, so answering it is never load-bearing.
    var pendingDownload by remember { mutableStateOf<LanguageDef?>(null) }
    val add: (LanguageDef) -> Unit = { lang ->
        if (languageData(lang.id).isEmpty) {
            addLanguage(scope, repository, settings, lang)
            onOpenLanguage(lang.id)
        } else {
            // Nothing is enabled until the dialog is answered, so its Cancel
            // really is a cancel and has nothing to undo.
            pendingDownload = lang
        }
    }

    pendingDownload?.let { lang ->
        val data = languageData(lang.id)
        val addAndOpen = {
            addLanguage(scope, repository, settings, lang)
            pendingDownload = null
            onOpenLanguage(lang.id)
        }
        LanguageDataDownloadDialog(
            language = lang,
            data = data,
            onCancel = { pendingDownload = null },
            onSkip = addAndOpen,
            onDownload = {
                data.wordlist?.let {
                    WordlistDownloadManager.start(filesDir, it, AUTO_DOWNLOAD_SIZE)
                }
                data.emojiDict?.let { EmojiDictDownloadManager.start(filesDir, it) }
                addAndOpen()
            },
        )
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text(stringResource(R.string.languages_search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
    // Only while browsing: once someone is searching, they know what they want
    // and a suggestion block above the results is in the way.
    if (q.isEmpty() && suggested.isNotEmpty()) {
        SettingsGroup(stringResource(R.string.languages_suggested_title)) {
            for (suggestion in suggested) {
                item {
                    NavRow(
                        suggestion.language.displayName,
                        subtitle = suggestionReasonLabel(suggestion),
                    ) { add(suggestion.language) }
                }
            }
            item { CaptionText(stringResource(R.string.languages_suggested_info)) }
        }
    }
    val allTitle = stringResource(R.string.languages_all_title)
    val addedLabel = stringResource(R.string.languages_added_label)
    SettingsGroup(if (q.isEmpty() && suggested.isNotEmpty()) allTitle else null) {
        for (lang in matches) {
            item {
                val added = lang.id in enabledLangIds
                NavRow(
                    lang.displayName,
                    subtitle = languageRowSubtitle(lang),
                    value = if (added) addedLabel else null,
                ) {
                    if (added) onOpenLanguage(lang.id) else add(lang)
                }
            }
        }
        if (matches.isEmpty()) {
            item { CaptionText(stringResource(R.string.languages_search_empty, query)) }
        }
    }
}

/**
 * A cluster the reader will recognise, for the conjunct-backspace row. A script
 * with no sample here still gets the setting; the row just describes it in words
 * rather than showing one, which beats showing a Bengali cluster to someone
 * setting up Khmer. Null means "no sample": the caller puts
 * `languages_conjunct_sample_fallback` in its place.
 */
private fun conjunctSample(script: ScriptId): String? = when (script) {
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
    else -> null
}

/**
 * What a language can download: its word list and its emoji keywords, with a
 * rough size for the two together. Either half may be missing — most languages
 * have a word list, only 125 have keywords.
 */
internal data class LanguageData(
    val wordlist: DictionaryEntry?,
    val emojiDict: EmojiDictEntry?,
    val bytes: Long,
) {
    val isEmpty: Boolean get() = wordlist == null && emojiDict == null
}

/** Word list and emoji keywords on offer for [langId], sized for a prompt. */
internal fun languageData(langId: String): LanguageData {
    val lists = DictionaryCatalog.forLanguage(langId)
    // Where a language has several lists (Portuguese), the one whose id is the
    // language itself is its default; the other is the regional variant.
    val wordlist = lists.firstOrNull { it.id == langId } ?: lists.firstOrNull()
    val emojiDict = EmojiDictCatalog.forLanguage(langId)
    // The catalogue sizes the whole file, and a capped download stops partway
    // through it, so scale by the share of the list actually read.
    val wordlistBytes = wordlist?.let {
        val cap = DictionaryCatalog.wordCap(it, AUTO_DOWNLOAD_SIZE)
        it.approxGzBytes * cap / it.totalWordCount.coerceAtLeast(1)
    } ?: 0L
    return LanguageData(wordlist, emojiDict, wordlistBytes + (emojiDict?.approxGzBytes ?: 0L))
}

/**
 * How much of a word list the add-a-language prompt fetches. The largest fixed
 * tier rather than [DictionaryCatalog.DictionarySize.ALL]: this download is one
 * tap on a dialog, so it should not be the one that pulls four million words.
 */
private val AUTO_DOWNLOAD_SIZE = DictionaryCatalog.DictionarySize.LARGE

/** Bytes a language's downloaded files are taking up right now. */
private fun downloadedLanguageBytes(filesDir: File, langId: String): Long =
    DictionaryStore.downloadedFile(filesDir, langId).length() +
        EmojiDictStore.packFile(filesDir, langId).length()

/**
 * Asks once, as a language is added, whether to fetch the data that makes it
 * work properly. The alternative to asking is either a silent download on a
 * metered connection or a language that quietly predicts nothing.
 *
 * Three answers, because the dialog is the first thing between tapping a name
 * in a long list and having a new language: [onDownload] and [onSkip] both add
 * it, [onCancel] backs out of the whole thing for the tap that was a mistake.
 * All three are laid out in one flow row so a long label wraps instead of
 * pushing "Download" off the edge.
 */
@Composable
private fun LanguageDataDownloadDialog(
    language: LanguageDef,
    data: LanguageData,
    onCancel: () -> Unit,
    onSkip: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.languages_data_download_title)) },
        text = {
            Text(
                stringResource(
                    R.string.languages_data_download_body,
                    language.displayName,
                    formatBytes(data.bytes),
                ),
            )
        },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.languages_data_download_dismiss_action))
                }
                TextButton(onClick = onDownload) {
                    Text(stringResource(CommonR.string.common_download))
                }
            }
        },
    )
}

/** The mirror of [LanguageDataDownloadDialog], for a language on its way out. */
@Composable
private fun LanguageDataDeleteDialog(
    language: LanguageDef,
    bytes: Long,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.languages_data_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.languages_data_delete_body,
                    language.displayName,
                    formatBytes(bytes),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text(stringResource(CommonR.string.common_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) {
                Text(stringResource(R.string.languages_data_delete_dismiss_action))
            }
        },
    )
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
    val filesDir = LocalContext.current.filesDir
    val lang = LanguageRegistry.byId(langId)
    var pendingDelete by remember { mutableStateOf(false) }

    val removeLanguage: () -> Unit = {
        scope.launch {
            val next = settings.enabledLayoutIds.filterNot {
                resolveLayout(settings.customLayouts, it).language().id == langId
            }
            if (next.isNotEmpty()) {
                repository.setEnabledLayoutIds(next.distinct())
                onRemoved()
            }
        }
    }

    if (pendingDelete) {
        LanguageDataDeleteDialog(
            language = lang,
            bytes = downloadedLanguageBytes(filesDir, langId),
            // Either answer removes the language: the dialog is about the
            // files on disk, not about the removal the user already asked for.
            onKeep = {
                pendingDelete = false
                removeLanguage()
            },
            onDelete = {
                pendingDelete = false
                DictionaryStore.delete(filesDir, langId)
                WordlistDownloadManager.refresh(filesDir)
                // Leaves a "declined" mark behind, which is what stops the
                // automatic pass fetching the keywords straight back.
                EmojiDictDownloadManager.delete(filesDir, langId)
                removeLanguage()
            },
        )
    }

    SettingsGroup(stringResource(R.string.languages_layouts_title)) {
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
        SettingsGroup(stringResource(R.string.languages_clusters_title)) {
            item {
                val sample = conjunctSample(lang.script)
                    ?: stringResource(R.string.languages_conjunct_sample_fallback)
                ToggleSetting(
                    R.string.languages_conjunct_backspace_title,
                    stringResource(R.string.languages_conjunct_backspace_subtitle, sample),
                    langId in settings.conjunctBackspaceLanguages,
                    info = stringResource(
                        R.string.languages_conjunct_backspace_info,
                        lang.englishName,
                    ),
                ) { scope.launch { repository.setConjunctBackspace(langId, it) } }
            }
        }
    }

    // Numerals are per language: Arabic can type ٠-٩ while English beside it
    // stays 0-9. Auto is the language's own default, so most languages never
    // need touching.
    SettingsGroup(stringResource(R.string.languages_numerals_title)) {
        item {
            val options = NumeralSystem.entries.map { it to stringResource(it.labelRes) }
            ChoiceSetting(
                R.string.languages_numeral_system_title,
                subtitle = stringResource(
                    R.string.languages_numeral_system_subtitle,
                    lang.englishName,
                ),
                info = stringResource(
                    R.string.languages_numeral_system_info,
                    lang.englishName,
                    stringResource(lang.numeralSystem.labelRes),
                ),
                options = options,
                selected = settings.layoutBehavior.numeralSystemFor(langId),
            ) { scope.launch { repository.setNumeralSystemForLanguage(langId, it) } }
        }
    }

    val others = settings.enabledLanguages.filter { it.id != langId }
    if (others.isNotEmpty()) {
        val secondaries = settings.secondaryLanguages[langId].orEmpty()
        SettingsGroup(stringResource(R.string.languages_secondary_title)) {
            item {
                CaptionText(
                    stringResource(R.string.languages_secondary_info, lang.englishName),
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
    SettingsGroup(stringResource(R.string.languages_dictionary_title)) {
        item {
            CaptionText(
                stringResource(
                    when {
                        lang.bundledDictionary && wordlistEntries.isNotEmpty() ->
                            R.string.languages_dictionary_bundled_more
                        lang.bundledDictionary -> R.string.languages_dictionary_bundled
                        wordlistEntries.isNotEmpty() ->
                            R.string.languages_dictionary_none_download
                        else -> R.string.languages_dictionary_none_learn
                    },
                ),
            )
        }
        for (entry in wordlistEntries) {
            item { WordlistRow(entry) }
        }
        item {
            NavRow(
                R.string.languages_custom_dictionaries_title,
                stringResource(R.string.languages_custom_dictionaries_subtitle),
                route = "customdictionaries",
            ) {
                onNavigate("customdictionaries")
            }
        }
    }

    val emojiDict = EmojiDictCatalog.forLanguage(langId)
    SettingsGroup(stringResource(R.string.languages_emoji_title)) {
        item {
            CaptionText(
                stringResource(
                    when {
                        emojiDict == null -> R.string.languages_emoji_keywords_unavailable
                        settings.emoji.autoDownloadKeywords ->
                            R.string.languages_emoji_keywords_auto
                        else -> R.string.languages_emoji_keywords_manual
                    },
                    lang.englishName,
                ),
            )
        }
        if (emojiDict != null) {
            item { EmojiDictRow(emojiDict) }
        }
        item {
            NavRow(
                R.string.languages_emoji_keywords_title,
                stringResource(R.string.languages_emoji_keywords_subtitle),
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
                        val downloaded = DictionaryStore.isDownloaded(filesDir, langId) ||
                            EmojiDictStore.isDownloaded(filesDir, langId)
                        if (downloaded) pendingDelete = true else removeLanguage()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.languages_remove_action, lang.englishName)) }
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
        title = stringResource(R.string.languages_emoji_keywords_title),
        supporting = {
            Text(
                when (status) {
                    is EmojiDictDownloadManager.DownloadStatus.Downloaded -> pluralStringResource(
                        R.plurals.languages_emoji_dict_downloaded,
                        status.emojiCount,
                        status.emojiCount,
                        formatBytes(status.sizeBytes),
                    )
                    EmojiDictDownloadManager.DownloadStatus.Queued ->
                        stringResource(R.string.languages_download_queued)
                    is EmojiDictDownloadManager.DownloadStatus.Downloading ->
                        stringResource(CommonR.string.common_downloading)
                    is EmojiDictDownloadManager.DownloadStatus.Failed ->
                        if (status.messageArg.isEmpty()) stringResource(status.messageRes)
                        else stringResource(status.messageRes, status.messageArg)
                    EmojiDictDownloadManager.DownloadStatus.NotDownloaded -> pluralStringResource(
                        R.plurals.languages_emoji_dict_download_size,
                        entry.emojiCount,
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
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(
                                R.string.languages_emoji_dict_delete_desc,
                            ),
                        )
                    }
                EmojiDictDownloadManager.DownloadStatus.Queued,
                is EmojiDictDownloadManager.DownloadStatus.Downloading,
                ->
                    IconButton(
                        onClick = { EmojiDictDownloadManager.cancel(entry.languageId) },
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(
                                R.string.languages_cancel_download_desc,
                            ),
                        )
                    }
                EmojiDictDownloadManager.DownloadStatus.NotDownloaded,
                is EmojiDictDownloadManager.DownloadStatus.Failed,
                -> TextButton(onClick = { EmojiDictDownloadManager.start(filesDir, entry) }) {
                    Text(
                        if (status is EmojiDictDownloadManager.DownloadStatus.Failed) {
                            stringResource(CommonR.string.common_retry)
                        } else {
                            stringResource(CommonR.string.common_download)
                        },
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
    var size by remember { mutableStateOf(DictionaryCatalog.DictionarySize.LARGE) }
    var sizeMenu by remember { mutableStateOf(false) }
    val effectiveWords = DictionaryCatalog.wordCap(entry, size)
    // Tiers past the end of a short list all keep the same words, so only the
    // first one that reaches the whole list is worth offering.
    val sizeOptions = remember(entry.totalWordCount) {
        DictionaryCatalog.DictionarySize.entries.distinctBy { DictionaryCatalog.wordCap(entry, it) }
    }

    WmRow(
        title = entry.variantRes?.let {
            stringResource(R.string.languages_wordlist_title_variant, stringResource(it))
        } ?: stringResource(R.string.languages_wordlist_title),
        supporting = {
            Text(
                when (status) {
                    is WordlistDownloadManager.DownloadStatus.Downloaded -> pluralStringResource(
                        R.plurals.languages_wordlist_downloaded,
                        status.wordCount,
                        status.wordCount,
                        formatBytes(status.sizeBytes),
                    )
                    WordlistDownloadManager.DownloadStatus.Processing ->
                        stringResource(R.string.languages_wordlist_preparing)
                    is WordlistDownloadManager.DownloadStatus.Downloading ->
                        stringResource(CommonR.string.common_downloading)
                    is WordlistDownloadManager.DownloadStatus.Failed ->
                        if (status.messageArg.isEmpty()) stringResource(status.messageRes)
                        else stringResource(status.messageRes, status.messageArg)
                    WordlistDownloadManager.DownloadStatus.NotDownloaded -> pluralStringResource(
                        R.plurals.languages_wordlist_word_count,
                        effectiveWords,
                        effectiveWords,
                    )
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
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(
                                R.string.languages_wordlist_delete_desc,
                            ),
                        )
                    }
                is WordlistDownloadManager.DownloadStatus.Downloading,
                WordlistDownloadManager.DownloadStatus.Processing,
                ->
                    IconButton(onClick = { WordlistDownloadManager.cancel() }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(
                                R.string.languages_cancel_download_desc,
                            ),
                        )
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
                            Text(stringResource(size.labelRes))
                            Icon(
                                Icons.Outlined.ArrowDropDown,
                                contentDescription = stringResource(
                                    R.string.languages_wordlist_size_desc,
                                ),
                            )
                        }
                        DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                            for (option in sizeOptions) {
                                DropdownMenuItem(
                                    text = {
                                        val words = DictionaryCatalog.wordCap(entry, option)
                                        Text(
                                            pluralStringResource(
                                                R.plurals.languages_wordlist_size_option,
                                                words,
                                                stringResource(option.labelRes),
                                                words,
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
                            if (status is WordlistDownloadManager.DownloadStatus.Failed) {
                                stringResource(CommonR.string.common_retry)
                            } else {
                                stringResource(CommonR.string.common_download)
                            },
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
                stringResource(
                    R.string.languages_wordlist_downloaded_bytes,
                    formatBytes(status.bytes),
                ),
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
    val groupTitle = stringResource(
        R.string.languages_cjk_options_title,
        LanguageRegistry.byId(langId).englishName,
    )
    SettingsGroup(groupTitle) {
        item { CaptionText(stringResource(R.string.languages_cjk_download_info)) }
        for (pack in CjkDictCatalog.forLang(langId)) {
            item {
                val status = states[pack.id] ?: CjkDictDownloadManager.DownloadStatus.NotDownloaded
                WmRow(
                    title = stringResource(pack.displayNameRes),
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
                                        contentDescription = stringResource(
                                            R.string.languages_cjk_delete_desc,
                                            stringResource(pack.displayNameRes),
                                        ),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            else -> TextButton(
                                enabled = pack.available && !CjkDictDownloadManager.isBusy,
                                onClick = { CjkDictDownloadManager.start(filesDir, pack) },
                            ) {
                                Text(
                                    if (status is CjkDictDownloadManager.DownloadStatus.Paused) {
                                        stringResource(R.string.languages_resume_action)
                                    } else {
                                        stringResource(CommonR.string.common_download)
                                    },
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
                R.string.languages_cjk_traditional_title,
                stringResource(R.string.languages_cjk_traditional_subtitle),
                settings.cjk.traditionalOutput,
            ) { on -> scope.launch { repository.setCjkTraditionalOutput(on) } }
        }

        // Traditional characters are only half of writing Traditional: Taipei
        // says 計程車 where the mainland says 出租車, and no character map
        // reaches that. Only worth showing once the toggle above is on.
        if (settings.cjk.traditionalOutput) {
            item { CaptionText(stringResource(R.string.languages_cjk_region_info)) }
            for (region in HanVariant.HanRegion.entries) {
                item {
                    val titleRes = when (region) {
                        HanVariant.HanRegion.GENERIC -> R.string.languages_cjk_region_generic_title
                        HanVariant.HanRegion.TAIWAN -> R.string.languages_cjk_region_taiwan_title
                        HanVariant.HanRegion.HONG_KONG ->
                            R.string.languages_cjk_region_hong_kong_title
                    }
                    val subtitleRes = when (region) {
                        HanVariant.HanRegion.GENERIC ->
                            R.string.languages_cjk_region_generic_subtitle
                        HanVariant.HanRegion.TAIWAN ->
                            R.string.languages_cjk_region_taiwan_subtitle
                        HanVariant.HanRegion.HONG_KONG ->
                            R.string.languages_cjk_region_hong_kong_subtitle
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
                            Text(stringResource(titleRes))
                            CaptionText(stringResource(subtitleRes))
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
                    R.string.languages_cjk_lazy_title,
                    stringResource(R.string.languages_cjk_lazy_subtitle),
                    settings.cjk.jyutpingLazy,
                ) { on -> scope.launch { repository.setJyutpingLazy(on) } }
            }
        }

        // Chinese-only: fuzzy pinyin + Double Pinyin scheme.
        if (langId == "zh") {
            item {
                ToggleSetting(
                    R.string.languages_cjk_fuzzy_title,
                    stringResource(R.string.languages_cjk_fuzzy_subtitle),
                    settings.cjk.pinyinFuzzy,
                ) { on -> scope.launch { repository.setPinyinFuzzy(on) } }
            }
            item { CaptionText(stringResource(R.string.languages_cjk_double_pinyin_info)) }
            for (scheme in DoublePinyinScheme.entries) {
                item {
                    // OFF is always selectable; a scheme is live only once its key
                    // table ships (currently Xiaohe), so the rest are shown but
                    // disabled rather than silently doing nothing.
                    val ready = scheme == DoublePinyinScheme.OFF || DoublePinyin.tableFor(scheme) != null
                    val select: () -> Unit = { scope.launch { repository.setPinyinDoublePinyin(scheme) } }
                    val schemeName = stringResource(scheme.displayNameRes)
                    WmRow(
                        title = if (ready) {
                            schemeName
                        } else {
                            stringResource(R.string.languages_cjk_scheme_coming_soon, schemeName)
                        },
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

/**
 * Supporting-line text for a pack's current download state. Composable so the
 * text follows the app's language: it is read only while the row is drawn.
 */
@Composable
private fun packStatusLabel(
    pack: CjkDictPack,
    status: CjkDictDownloadManager.DownloadStatus,
): String = when (status) {
    CjkDictDownloadManager.DownloadStatus.NotDownloaded ->
        if (pack.available) {
            stringResource(pack.descriptionRes)
        } else {
            stringResource(R.string.languages_cjk_pack_unavailable)
        }
    is CjkDictDownloadManager.DownloadStatus.Downloading ->
        if (status.total > 0) {
            stringResource(
                R.string.languages_cjk_downloading_percent,
                (status.bytes * PERCENT / status.total).toInt(),
            )
        } else {
            stringResource(CommonR.string.common_downloading)
        }
    is CjkDictDownloadManager.DownloadStatus.Paused ->
        stringResource(R.string.languages_cjk_paused)
    CjkDictDownloadManager.DownloadStatus.Downloaded ->
        stringResource(R.string.languages_cjk_downloaded)
    is CjkDictDownloadManager.DownloadStatus.Failed ->
        status.messageArg?.let { stringResource(status.messageRes, it) }
            ?: stringResource(status.messageRes)
}

/** Turns a fraction into the whole-number percentage the pack row shows. */
private const val PERCENT = 100L
