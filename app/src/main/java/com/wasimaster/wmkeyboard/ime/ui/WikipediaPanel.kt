package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.WikiUi

/** Tabs on an open Wikipedia article. */
private enum class WikiTab(val label: String) { SUMMARY("Summary"), LINKS("Links"), FULL("Full article") }

/**
 * Wikipedia in the tool viewbox: search (the query types on the key rows,
 * like the other search panels), then an article with summary, its
 * outgoing links and the full text — each insertable at the cursor.
 */
@Composable
internal fun WikipediaPanel(
    state: KeyboardUiState,
    onQueryTap: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onLoadLinks: () -> Unit,
    onLoadFull: () -> Unit,
    onInsert: (String) -> Unit,
) {
    val kb = LocalKbTheme.current
    // Search mode: only the query bar shows, keys underneath type into it.
    val height = if (state.mediaSearchActive) 56.dp else keyRowsHeight(state)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(kb.chip)
                .clickable { onQueryTap() }
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = kb.toolbarIcon,
            )
            Spacer(Modifier.width(8.dp))
            SearchQueryText(
                query = state.mediaQuery,
                placeholder = if (state.mediaSearchActive) {
                    "Search Wikipedia… (enter to search)"
                } else {
                    "Search Wikipedia…"
                },
                active = state.mediaSearchActive,
                textColor = kb.modifierKeyText,
                placeholderColor = kb.toolbarIcon,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.mediaSearchActive) return@Column

        when (val wiki = state.wiki) {
            WikiUi.Idle -> WikiMessage(
                "Search any topic on ${state.settings.wikiLanguage}.wikipedia.org — " +
                    "read the summary, follow links, insert text or the article's URL.",
            )
            WikiUi.Loading -> WikiMessage("Loading…")
            is WikiUi.Error -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    wiki.message,
                    color = kb.secondaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
                ToolPanelChip("Retry") { onRetry() }
            }
            is WikiUi.SearchResults -> {
                if (wiki.results.isEmpty()) {
                    WikiMessage("Nothing found for “${wiki.query}”.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    ) {
                        items(wiki.results.size) { index ->
                            val result = wiki.results[index]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpen(result.title) }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    result.title,
                                    color = kb.modifierKeyText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (result.description.isNotBlank()) {
                                    Text(
                                        result.description,
                                        color = kb.secondaryText,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is WikiUi.Article -> WikiArticle(
                wiki = wiki,
                markdownLinks = state.settings.wikiLinksMarkdown,
                lang = state.settings.wikiLanguage,
                onBack = onBack,
                onOpen = onOpen,
                onLoadLinks = onLoadLinks,
                onLoadFull = onLoadFull,
                onInsert = onInsert,
            )
        }
    }
}

@Composable
private fun WikiArticle(
    wiki: WikiUi.Article,
    markdownLinks: Boolean,
    lang: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onLoadLinks: () -> Unit,
    onLoadFull: () -> Unit,
    onInsert: (String) -> Unit,
) {
    val kb = LocalKbTheme.current
    var tab by rememberSaveable(wiki.summary.title) { mutableStateOf(WikiTab.SUMMARY) }
    val linkText = remember(wiki.summary, markdownLinks) {
        if (markdownLinks) "[${wiki.summary.title}](${wiki.summary.url})" else wiki.summary.url
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (wiki.canGoBack) {
                IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to results",
                        tint = kb.toolbarIcon,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                wiki.summary.title,
                color = kb.modifierKeyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            for (wikiTab in WikiTab.entries) {
                Spacer(Modifier.width(4.dp))
                ToolPanelChip(wikiTab.label, selected = tab == wikiTab) {
                    tab = wikiTab
                    when (wikiTab) {
                        WikiTab.LINKS -> if (wiki.links == null) onLoadLinks()
                        WikiTab.FULL -> if (wiki.fullText == null) onLoadFull()
                        WikiTab.SUMMARY -> {}
                    }
                }
            }
        }
        when (tab) {
            WikiTab.SUMMARY -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToolPanelChip("Insert summary") {
                            if (wiki.summary.extract.isNotBlank()) onInsert(wiki.summary.extract)
                        }
                        ToolPanelChip("Insert link") { onInsert(linkText) }
                        ToolPanelChip("Insert title") { onInsert(wiki.summary.title) }
                    }
                }
                if (wiki.summary.description.isNotBlank()) {
                    item {
                        Text(
                            wiki.summary.description,
                            color = kb.accent,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                item {
                    Text(
                        wiki.summary.extract.ifBlank { "No summary available." },
                        color = kb.modifierKeyText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            WikiTab.LINKS -> when {
                wiki.loadingExtra && wiki.links == null -> WikiMessage("Loading links…")
                wiki.links.isNullOrEmpty() -> WikiMessage("No outgoing article links found.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    items(wiki.links.size) { index ->
                        val title = wiki.links[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpen(title) }
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                title,
                                color = kb.modifierKeyText,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            ToolPanelChip("Insert") {
                                val url = com.wasimaster.wmkeyboard.core.tools.WikipediaClient
                                    .articleUrl(title, lang)
                                onInsert(if (markdownLinks) "[$title]($url)" else url)
                            }
                        }
                    }
                }
            }
            WikiTab.FULL -> when {
                wiki.loadingExtra && wiki.fullText == null -> WikiMessage("Loading the full article…")
                wiki.fullText.isNullOrBlank() -> WikiMessage("Couldn't load the article text.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    // Paragraph-per-item keeps huge articles scrollable; each
                    // paragraph is insertable on its own.
                    val paragraphs = wiki.fullText.split("\n\n").filter { it.isNotBlank() }
                    items(paragraphs.size) { index ->
                        val paragraph = paragraphs[index]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onInsert(paragraph) }
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                paragraph,
                                color = if (paragraph.startsWith("==")) kb.accent else kb.modifierKeyText,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WikiMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = LocalKbTheme.current.secondaryText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
