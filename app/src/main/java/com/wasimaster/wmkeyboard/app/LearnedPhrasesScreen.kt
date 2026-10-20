package com.wasimaster.wmkeyboard.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import com.wasimaster.wmkeyboard.core.icons.symbols.outlined.Close
import com.wasimaster.wmkeyboard.core.icons.symbols.outlined.Delete
import com.wasimaster.wmkeyboard.core.icons.symbols.outlined.ExpandMore
import com.wasimaster.wmkeyboard.core.icons.symbols.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.wasimaster.wmkeyboard.core.prediction.UserLexicon
import com.wasimaster.wmkeyboard.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PHRASE_LIST_PAGE = 100
private const val SEARCH_THRESHOLD = 8

private enum class PhraseTab {
    ALL,
    BIGRAMS,
    TRIGRAMS,
    SKIPGRAMS,
}

private sealed class LearnedItem(val count: Int) {
    abstract val phrase: String
    abstract val subtitleRes: Int?
    abstract val isSkip1: Boolean
    abstract val isSkip2: Boolean

    data class Bigram(val item: UserLexicon.LearnedBigram) : LearnedItem(item.count) {
        override val phrase: String = "${item.previous} ${item.next}"
        override val subtitleRes: Int? = null
        override val isSkip1: Boolean = false
        override val isSkip2: Boolean = false
    }

    data class Trigram(val item: UserLexicon.LearnedTrigram) : LearnedItem(item.count) {
        override val phrase: String = "${item.prev2} ${item.prev1} ${item.next}"
        override val subtitleRes: Int = R.string.typing_learned_phrases_tab_trigrams
        override val isSkip1: Boolean = false
        override val isSkip2: Boolean = false
    }

    data class Skipgram(val item: UserLexicon.LearnedSkipgram) : LearnedItem(item.count) {
        override val phrase: String = "${item.previous} … ${item.next}"
        override val subtitleRes: Int = R.string.typing_learned_phrases_tab_skipgrams
        override val isSkip1: Boolean = item.skipDistance == 1
        override val isSkip2: Boolean = item.skipDistance == 2
    }
}

/**
 * Screen displaying learned N-grams (bigrams, trigrams, and skip-grams).
 * Allows users to review, search, delete individual phrases, or clear all learned phrases.
 */
@Composable
internal fun LearnedPhrasesSettings(repository: SettingsRepository, settings: LiveSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val file = remember { File(context.filesDir, "learning/user_lexicon.json") }
    var bigrams by remember { mutableStateOf<List<UserLexicon.LearnedBigram>>(emptyList()) }
    var trigrams by remember { mutableStateOf<List<UserLexicon.LearnedTrigram>>(emptyList()) }
    var skipgrams by remember { mutableStateOf<List<UserLexicon.LearnedSkipgram>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(settings.watch { it.lexiconVersion }) {
        val lex = withContext(Dispatchers.IO) { UserLexicon(file) }
        bigrams = lex.allBigrams()
        trigrams = lex.allTrigrams()
        skipgrams = lex.allSkipgrams()
        loaded = true
    }

    fun persist(edit: (UserLexicon) -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val lex = UserLexicon(file)
                edit(lex)
                lex.save()
            }
            repository.bumpLexiconVersion()
        }
    }

    if (!loaded) return

    var selectedTab by remember { mutableStateOf(PhraseTab.ALL) }
    var query by remember { mutableStateOf("") }

    val items = remember(bigrams, trigrams, skipgrams, selectedTab) {
        when (selectedTab) {
            PhraseTab.ALL -> {
                (bigrams.map { LearnedItem.Bigram(it) } +
                    trigrams.map { LearnedItem.Trigram(it) } +
                    skipgrams.map { LearnedItem.Skipgram(it) }).sortedByDescending { it.count }
            }
            PhraseTab.BIGRAMS -> bigrams.map { LearnedItem.Bigram(it) }
            PhraseTab.TRIGRAMS -> trigrams.map { LearnedItem.Trigram(it) }
            PhraseTab.SKIPGRAMS -> skipgrams.map { LearnedItem.Skipgram(it) }
        }
    }

    val filtered = remember(items, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) items else items.filter { needle in it.phrase.lowercase() }
    }

    var visible by remember(query, selectedTab) { mutableIntStateOf(PHRASE_LIST_PAGE) }

    val totalCount = bigrams.size + trigrams.size + skipgrams.size
    if (totalCount > 0) {
        SettingsGroup {
            item {
                ActionRow(
                    title = R.string.typing_learned_phrases_clear_title,
                    subtitle = stringResource(R.string.typing_learned_phrases_clear_subtitle),
                    action = stringResource(CommonR.string.common_clear),
                    confirm = stringResource(R.string.typing_learned_phrases_clear_confirm),
                ) {
                    persist { it.clearNgrams() }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedTab == PhraseTab.ALL,
            onClick = { selectedTab = PhraseTab.ALL },
            label = { Text("${stringResource(R.string.typing_learned_phrases_tab_all)} ($totalCount)") },
        )
        FilterChip(
            selected = selectedTab == PhraseTab.BIGRAMS,
            onClick = { selectedTab = PhraseTab.BIGRAMS },
            label = { Text("${stringResource(R.string.typing_learned_phrases_tab_bigrams)} (${bigrams.size})") },
        )
        FilterChip(
            selected = selectedTab == PhraseTab.TRIGRAMS,
            onClick = { selectedTab = PhraseTab.TRIGRAMS },
            label = { Text("${stringResource(R.string.typing_learned_phrases_tab_trigrams)} (${trigrams.size})") },
        )
        FilterChip(
            selected = selectedTab == PhraseTab.SKIPGRAMS,
            onClick = { selectedTab = PhraseTab.SKIPGRAMS },
            label = { Text("${stringResource(R.string.typing_learned_phrases_tab_skipgrams)} (${skipgrams.size})") },
        )
    }

    if (items.size > SEARCH_THRESHOLD || query.isNotEmpty()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(CommonR.string.common_search)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(CommonR.string.common_clear),
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    if (totalCount == 0) {
        CaptionText(stringResource(R.string.typing_learned_phrases_empty))
        return
    }

    if (filtered.isEmpty() && query.isNotEmpty()) {
        CaptionText(stringResource(R.string.typing_learned_phrases_no_matches, query))
        return
    }

    SettingsGroup(
        pluralStringResource(
            R.plurals.typing_learned_phrases_count,
            filtered.size,
            filtered.size,
        ),
    ) {
        for (item in filtered.take(visible)) {
            val key = when (item) {
                is LearnedItem.Bigram -> "bi_${item.item.previous}_${item.item.next}"
                is LearnedItem.Trigram -> "tri_${item.item.prev2}_${item.item.prev1}_${item.item.next}"
                is LearnedItem.Skipgram -> "skip_${item.item.skipDistance}_${item.item.previous}_${item.item.next}"
            }
            item(key = key) {
                val times = pluralStringResource(R.plurals.typing_learned_phrases_typed_times, item.count, item.count)
                val subtitle = when {
                    item is LearnedItem.Skipgram -> {
                        val skipDesc = if (item.isSkip1) {
                            stringResource(R.string.typing_learned_phrases_skip1_desc)
                        } else {
                            stringResource(R.string.typing_learned_phrases_skip2_desc)
                        }
                        "$skipDesc • $times"
                    }
                    item.subtitleRes != null -> {
                        val res = item.subtitleRes ?: 0
                        if (res != 0) "${stringResource(res)} • $times" else times
                    }
                    else -> times
                }
                WmRow(
                    title = item.phrase,
                    subtitle = subtitle,
                    trailing = {
                        IconButton(onClick = {
                            persist { lex ->
                                when (item) {
                                    is LearnedItem.Bigram -> lex.forgetBigram(item.item.previous, item.item.next)
                                    is LearnedItem.Trigram -> lex.forgetTrigram(item.item.prev2, item.item.prev1, item.item.next)
                                    is LearnedItem.Skipgram -> lex.forgetSkipgram(item.item.previous, item.item.next, item.item.skipDistance)
                                }
                            }
                        }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.typing_learned_phrases_delete_desc),
                            )
                        }
                    },
                )
            }
        }
        if (filtered.size > visible) {
            item {
                WmRow(
                    title = pluralStringResource(R.plurals.backup_word_list_show_more, filtered.size - visible, filtered.size - visible),
                    icon = Icons.Outlined.ExpandMore,
                    onClick = { visible += PHRASE_LIST_PAGE },
                )
            }
        }
    }
}
