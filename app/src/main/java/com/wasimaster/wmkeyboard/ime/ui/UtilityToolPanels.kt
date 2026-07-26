package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.core.tools.CalcEngine
import com.wasimaster.wmkeyboard.core.tools.CurrencyClient
import com.wasimaster.wmkeyboard.core.tools.SymbolCatalog
import androidx.compose.ui.graphics.Color
import com.wasimaster.wmkeyboard.core.tools.ToolPrefill
import com.wasimaster.wmkeyboard.core.tools.UnitConvert
import com.wasimaster.wmkeyboard.ime.CurrencyUi
import com.wasimaster.wmkeyboard.ime.FocusRegion
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.PanelMode

// Shared building blocks for the utility tool panels (symbols, calculator,
// unit & currency converters): a pill chip and a compact keypad button.

@Composable
internal fun ToolPanelChip(
    label: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val kb = LocalKbTheme.current
    // Dimmed by scaling whatever alpha the theme already chose, not by pinning
    // a new one: some themes draw chips translucent to begin with, and no theme
    // defines a "disabled" colour pair that would stay legible across all of them.
    fun Color.dim() = if (enabled) this else copy(alpha = alpha * DISABLED_ALPHA)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background((if (selected) kb.toolCircleActive else kb.chip).dim())
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = (if (selected) kb.toolCircleActiveIcon else kb.modifierKeyText).dim(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** How far [ToolPanelChip] fades a chip that cannot be tapped. */
private const val DISABLED_ALPHA = 0.4f

@Composable
private fun KeypadButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val feedback = LocalKeyPressFeedback.current
    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (accent) kb.toolCircleActive else kb.key)
            .clickable { feedback(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (accent) kb.toolCircleActiveIcon else kb.keyText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---- symbols tool ----

/**
 * One-tap picker for the special characters people otherwise hunt down
 * online — fractions, math operators, Greek, arrows and friends. Tapping a
 * symbol types it and files it under Recents.
 */
/**
 * Category chips for the symbols tool, living in the [FullBleedTool] header
 * next to the back button (the reclaimed toolbar row's blank space does the
 * navigation work).
 */
@Composable
internal fun RowScope.SymbolCategoryChips(
    state: KeyboardUiState,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val recents = state.settings.symbolRecents
    val categoryNames = buildList {
        if (recents.isNotEmpty()) add("Recents")
        SymbolCatalog.categories.forEach { add(it.name) }
    }
    val focusedChip = state.focusedIndex(FocusRegion.CHIPS)
    Row(
        modifier = Modifier
            .weight(1f)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        categoryNames.forEachIndexed { index, name ->
            ToolPanelChip(
                name,
                selected = name == selected,
                modifier = Modifier.focusRing(index == focusedChip, RoundedCornerShape(12.dp)),
            ) { onSelect(name) }
        }
    }
}

@Composable
internal fun SymbolsPanel(
    state: KeyboardUiState,
    onSymbol: (String) -> Unit,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit = {},
) {
    val kb = LocalKbTheme.current
    val recents = state.settings.symbolRecents
    val symbols = if (selectedCategory == "Recents") recents
    else SymbolCatalog.categories.firstOrNull { it.name == selectedCategory }?.symbols.orEmpty()

    val gridState = rememberLazyGridState()
    // The grid is Adaptive, so its width in cells is only knowable after layout.
    val columns by remember {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column }?.plus(1) ?: 1
        }
    }
    PanelFocusTarget(
        panel = PanelMode.SYMBOLS,
        count = symbols.size,
        columns = columns,
        onActivate = { index -> symbols.getOrNull(index)?.let(onSymbol) },
    )
    // Tab reaches the category chips, which are drawn in the panel header by
    // [SymbolCategoryChips] — the names are recomputed here rather than hoisted,
    // so the header keeps owning its own selection.
    val categoryNames = buildList {
        if (recents.isNotEmpty()) add("Recents")
        SymbolCatalog.categories.forEach { add(it.name) }
    }
    PanelFocusTarget(
        panel = PanelMode.SYMBOLS,
        region = FocusRegion.CHIPS,
        count = categoryNames.size,
        columns = categoryNames.size.coerceAtLeast(1),
        onActivate = { index -> categoryNames.getOrNull(index)?.let(onSelectCategory) },
    )
    val focused = state.focusedIndex()
    ScrollFocusIntoView(focused) { gridState.animateScrollToItem(it) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 44.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
    ) {
        itemsIndexed(symbols, key = { _, symbol -> symbol }) { index, symbol ->
            Box(
                modifier = Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .focusRing(index == focused)
                    .clickable { onSymbol(symbol) },
                contentAlignment = Alignment.Center,
            ) {
                val label = SymbolCatalog.label(symbol)
                Text(
                    label,
                    color = kb.keyText,
                    fontSize = if (label.length > 2) 11.sp else 20.sp,
                )
            }
        }
    }
}

// ---- calculator tool ----

/**
 * Scientific calculator in the tool viewbox. The result is evaluated live
 * as the expression grows; `=` collapses the expression to its result and
 * Insert types the result at the cursor.
 */
@Composable
internal fun CalculatorPanel(
    state: KeyboardUiState,
    onInsert: (String) -> Unit,
    onPrefillConsumed: () -> Unit = {},
) {
    val kb = LocalKbTheme.current
    // Opened from a strip chip: start on what was typed rather than on the
    // blank display, so the chip's answer is one keystroke from being
    // edited. Read once — the service clears the prefill straight after.
    val prefill = remember { state.toolPrefill as? ToolPrefill.Calc }
    var expression by rememberSaveable { mutableStateOf(prefill?.expression.orEmpty()) }
    LaunchedEffect(Unit) { if (prefill != null) onPrefillConsumed() }
    val degrees = state.settings.calcDegrees
    val precision = state.settings.calcPrecision
    val result = remember(expression, degrees, precision) {
        if (expression.isBlank()) null
        else runCatching { CalcEngine.format(CalcEngine.evaluate(expression, degrees), precision) }
    }

    fun append(text: String) {
        expression += text
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        // Display: expression, live result, insert.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(kb.chip)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (expression.isEmpty()) "0" else expression,
                    color = kb.modifierKeyText,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
                val resultText = result?.fold(
                    onSuccess = { "= $it" },
                    onFailure = { it.message ?: "…" },
                )
                if (resultText != null) {
                    Text(
                        resultText,
                        color = if (result.isSuccess) kb.accent else kb.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (result?.isSuccess == true) {
                Spacer(Modifier.width(8.dp))
                ToolPanelChip("Insert") { onInsert(result.getOrThrow()) }
            }
        }
        // Scientific row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ToolPanelChip("C") { expression = "" }
            listOf(
                "sin(", "cos(", "tan(", "π", "e", "^", "√", "ln(", "log(", "abs(", "mod ",
            ).forEach { token ->
                ToolPanelChip(token.trimEnd('(', ' ')) { append(token) }
            }
            ToolPanelChip(if (degrees) "deg" else "rad", selected = true) {}
        }
        // Keypad.
        val rows = listOf(
            listOf("7", "8", "9", "÷", "⌫"),
            listOf("4", "5", "6", "×", "("),
            listOf("1", "2", "3", "−", ")"),
            listOf("0", ".", "%", "+", "="),
        )
        Column(Modifier.weight(1f)) {
            for (row in rows) {
                Row(Modifier.weight(1f)) {
                    for (label in row) {
                        KeypadButton(
                            label,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            accent = label == "=",
                        ) {
                            when (label) {
                                "⌫" -> expression = expression.dropLast(1)
                                "=" -> result?.onSuccess { expression = it }
                                else -> append(label)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- shared converter scaffolding ----

/** Digits-and-dot keypad used by the unit and currency converters. */
@Composable
private fun ConverterKeypad(
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
    value: String,
) {
    val rows = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf(".", "0", "⌫"),
    )
    Column(modifier) {
        for (row in rows) {
            Row(Modifier.weight(1f)) {
                for (label in row) {
                    KeypadButton(
                        label,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        when (label) {
                            "⌫" -> onChange(value.dropLast(1))
                            "." -> if ('.' !in value) onChange(value + ".")
                            else -> onChange((value + label).take(14))
                        }
                    }
                }
            }
        }
    }
}

// ---- unit converter tool ----

/**
 * Unit converter: pick a category, a from/to unit pair, type a value on
 * the keypad and insert the result. Everything converts locally. The
 * selection persists (via [onSelectionChange]) so the panel reopens on
 * whatever was converted last.
 */
@Composable
internal fun UnitConverterPanel(
    state: KeyboardUiState,
    onInsert: (String) -> Unit,
    onSelectionChange: (String) -> Unit = {},
    onPrefillConsumed: () -> Unit = {},
) {
    val kb = LocalKbTheme.current
    // "Cat|from|to;Cat|from|to;…" — every category keeps its own unit pair,
    // and the last-used category leads the list (it's what the panel reopens
    // on). Matching is by name/symbol, not index, so a units-list change in
    // an update degrades to the defaults instead of picking a wrong unit.
    val savedEntries = state.settings.unitConvertLast.split(';').mapNotNull { entry ->
        val parts = entry.split('|')
        if (parts.size == 3) Triple(parts[0], parts[1], parts[2]) else null
    }
    // Opened from a "1 ft" chip: the category, pair and amount all arrive
    // together and outrank the remembered selection. Snapshotted at first
    // composition because the service clears the prefill immediately after.
    val prefill = remember { state.toolPrefill as? ToolPrefill.Units }
    LaunchedEffect(Unit) { if (prefill != null) onPrefillConsumed() }
    var categoryIndex by rememberSaveable {
        mutableStateOf(
            UnitConvert.categories
                .indexOfFirst { it.name == (prefill?.category ?: savedEntries.firstOrNull()?.first) }
                .takeIf { it >= 0 } ?: 0
        )
    }
    val category = UnitConvert.categories[categoryIndex.coerceIn(UnitConvert.categories.indices)]
    fun savedUnit(pick: (Triple<String, String, String>) -> String, fallback: Int): Int =
        savedEntries.firstOrNull { it.first == category.name }?.let { entry ->
            category.units.indexOfFirst { it.symbol == pick(entry) }.takeIf { it >= 0 }
        } ?: fallback
    /** The prefilled unit, but only while the panel is on its category. */
    fun prefilledUnit(pick: (ToolPrefill.Units) -> String): Int? =
        prefill?.takeIf { it.category == category.name }
            ?.let { category.units.indexOfFirst { unit -> unit.symbol == pick(it) } }
            ?.takeIf { it >= 0 }
    var fromIndex by rememberSaveable(categoryIndex) {
        mutableStateOf(prefilledUnit { it.from } ?: savedUnit({ it.second }, 0))
    }
    var toIndex by rememberSaveable(categoryIndex) {
        mutableStateOf(prefilledUnit { it.to } ?: savedUnit({ it.third }, 1))
    }
    var value by rememberSaveable { mutableStateOf(prefill?.value ?: "1") }

    val from = category.units.getOrElse(fromIndex) { category.units.first() }
    val to = category.units.getOrElse(toIndex) { category.units.last() }
    LaunchedEffect(category.name, from.symbol, to.symbol) {
        val others = savedEntries.filter { it.first != category.name }
        val encoded = (listOf(Triple(category.name, from.symbol, to.symbol)) + others)
            .joinToString(";") { "${it.first}|${it.second}|${it.third}" }
        onSelectionChange(encoded)
    }
    val amount = value.toDoubleOrNull()
    val converted = amount?.let { UnitConvert.convert(it, from, to) }
    val resultText = converted?.takeIf { !it.isNaN() && !it.isInfinite() }
        ?.let { CalcEngine.format(it, state.settings.calcPrecision) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            UnitConvert.categories.forEachIndexed { index, cat ->
                ToolPanelChip(cat.name, selected = index == categoryIndex) {
                    categoryIndex = index
                }
            }
        }
        UnitChipRow(
            label = "From",
            units = category.units,
            selected = fromIndex,
        ) { fromIndex = it }
        UnitChipRow(
            label = "To",
            units = category.units,
            selected = toIndex,
        ) { toIndex = it }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$value ${from.symbol}",
                color = kb.modifierKeyText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
            IconButton(
                onClick = { val tmp = fromIndex; fromIndex = toIndex; toIndex = tmp },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = "Swap units",
                    tint = kb.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                if (resultText != null) "$resultText ${to.symbol}" else "—",
                color = kb.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (resultText != null) {
                ToolPanelChip("Insert") { onInsert(resultText) }
            }
        }
        ConverterKeypad(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            onChange = { value = it },
            value = value,
        )
    }
}

@Composable
private fun UnitChipRow(
    label: String,
    units: List<UnitConvert.ConvUnit>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val kb = LocalKbTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = kb.secondaryText, fontSize = 10.sp, modifier = Modifier.width(34.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            units.forEachIndexed { index, unit ->
                ToolPanelChip(unit.symbol, selected = index == selected) { onSelect(index) }
            }
        }
    }
}

// ---- currency converter tool ----

/**
 * Currency converter on free keyless rate APIs. Rates come as one
 * USD-based table and cross-convert locally; the from/to pair persists
 * via [onPairChange].
 */
@Composable
internal fun CurrencyPanel(
    state: KeyboardUiState,
    onPairChange: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onInsert: (String) -> Unit,
    onPrefillConsumed: () -> Unit = {},
) {
    val kb = LocalKbTheme.current
    // Opened from a "150 usd" chip: take its amount, and push its pair into
    // settings (which is where the panel reads from/to) before the first
    // conversion is drawn.
    val prefill = remember { state.toolPrefill as? ToolPrefill.Currency }
    var amountText by rememberSaveable { mutableStateOf(prefill?.amount ?: "1") }
    LaunchedEffect(Unit) {
        prefill?.let {
            if (it.from != state.settings.currencyFrom || it.to != state.settings.currencyTo) {
                onPairChange(it.from, it.to)
            }
            onPrefillConsumed()
        }
    }
    val from = state.settings.currencyFrom
    val to = state.settings.currencyTo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        when (val currency = state.currency) {
            CurrencyUi.Loading -> ConverterMessage("Fetching exchange rates…")
            is CurrencyUi.Error -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    currency.message,
                    color = kb.secondaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
                ToolPanelChip("Retry") { onRefresh() }
            }
            is CurrencyUi.Ready -> {
                val codes = remember(currency.rates) {
                    val all = currency.rates.rates.keys.sorted()
                    CurrencyClient.popular.filter { it in currency.rates.rates } +
                        all.filterNot { it in CurrencyClient.popular }
                }
                CurrencyChipRow("From", codes, from) { onPairChange(it, to) }
                CurrencyChipRow("To", codes, to) { onPairChange(from, it) }
                val amount = amountText.toDoubleOrNull()
                val converted = amount?.let {
                    CurrencyClient.convert(it, from, to, currency.rates)
                }
                val resultText = converted?.let {
                    CalcEngine.format(it, state.settings.currencyDecimals)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$amountText $from",
                        color = kb.modifierKeyText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                    )
                    IconButton(
                        onClick = { onPairChange(to, from) },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Outlined.SwapHoriz,
                            contentDescription = "Swap currencies",
                            tint = kb.accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        if (resultText != null) "$resultText $to" else "—",
                        color = kb.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (resultText != null) {
                        ToolPanelChip("Insert") { onInsert(resultText) }
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Refresh rates",
                            tint = kb.toolbarIcon,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                ConverterKeypad(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onChange = { amountText = it },
                    value = amountText,
                )
            }
        }
    }
}

@Composable
private fun CurrencyChipRow(
    label: String,
    codes: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val kb = LocalKbTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = kb.secondaryText, fontSize = 10.sp, modifier = Modifier.width(34.dp))
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(codes.size) { index ->
                val code = codes[index]
                ToolPanelChip(code, selected = code == selected) { onSelect(code) }
            }
        }
    }
}

@Composable
private fun ConverterMessage(text: String) {
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
