package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.script.ScriptRegistry
import com.wasimaster.wmkeyboard.language.R
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer
import kotlin.math.roundToInt

/**
 * Reading a layout written for a *different* keyboard.
 *
 * Two formats, one parser between them. HeliBoard's JSON layouts are
 * FlorisBoard's: the same `{"$": "text_key", …}` key objects and the same
 * negative key-code numbering, because HeliBoard's parser package is a port of
 * it. HeliBoard's other format is a plain text file, one key per line.
 *
 * **Written from the published formats, not from either project's source.**
 * FlorisBoard is Apache-2.0 and HeliBoard is GPL-3.0; this app is MIT. The key
 * codes below are transcribed from the constants those projects document, which
 * is a fact about a file format, and no upstream code is copied. Test fixtures
 * are hand-written minimal files for the same reason.
 *
 * ### What comes out
 *
 * Only the letters layer. A foreign layout file carries one grid — the symbols
 * and numeric layers belong to the app that read it — and an omitted layer
 * inherits the shipped one (see [LayoutSpec.layers]), which is what a user
 * moving one grid across actually wants.
 *
 * ### Two rules that are not style
 *
 * **A key this app has no action for is dropped, never turned into
 * [KeyAction.Unknown].** That action exists for a layout written by a *newer
 * build of this app*, and its serializer throws on the way back out
 * (`UnknownKeyActionSerializer.serialize`), so a converted layout carrying one
 * would take the whole custom-layouts write down with it when saved. Every drop
 * is reported instead, with the code that caused it.
 *
 * **The result has already been through [LayoutSpec.repair].** The keyboard
 * repairs again on every layout compile and throws the notes away, so anything
 * left for it to fix would be a silent difference between the grid the user
 * approved and the grid they type on. Repairing here, once, keeps the two the
 * same and puts the fixes in front of the user. Repair is idempotent, so the
 * second pass is a no-op.
 */
enum class ForeignSource {
    /** HeliBoard's plain text layout: one key per line, blank line ends a row. */
    HELIBOARD_TEXT,

    /** The FlorisBoard `KeyData` JSON that HeliBoard also reads. */
    FLORIS_JSON,
}

/** A foreign layout after conversion, with everything that was lost on the way. */
data class ConvertedLayout(
    val layout: LayoutSpec,
    /** Conversion notes first, then whatever [LayoutSpec.repair] had to change. */
    val notes: List<LayoutMessage>,
    /** Foreign key codes with no equivalent here. Those keys are not in [layout]. */
    val unmapped: List<Int>,
    /**
     * What the characters on the keys suggest the language is. Seeds the
     * picker and nothing else: neither format states a language, and
     * [LayoutCodec] turns a blank one into English without asking, which would
     * quietly give a Georgian grid an English dictionary.
     */
    val guessedLangId: String,
    val source: ForeignSource,
) {

    /**
     * The layout with a language on it, ready to store.
     *
     * The only supported way to take [layout] out of here. It is deliberately
     * handed over with a blank `langId`, and a blank one is not a neutral
     * value: `LayoutCodec` migrates it to English on the very next read, so a
     * Georgian grid saved as-is would come back with an English dictionary,
     * English autocorrect and Latin shift behaviour, with nothing on screen to
     * say why. Going through here means the language was decided by someone.
     */
    fun withLanguage(langId: String): LayoutSpec =
        layout.copy(langId = langId.ifBlank { guessedLangId })
}

object ForeignLayouts {

    /**
     * Converts [text], guessing which of the two formats it is from its first
     * character. Null when it is neither, or holds no keys.
     *
     * Deliberately not reachable from `WMFileTypes.identify()`: neither format
     * carries a tag of ours, and the one untagged format already in that chain
     * is recognised by file name alone. A second one would have nothing left to
     * be told apart by.
     */
    fun convert(text: String, name: String): ConvertedLayout? =
        if (firstMeaningfulChar(text) in JSON_OPENERS) {
            fromFlorisJson(text, name)
        } else {
            fromSimpleText(text, name)
        }

    /**
     * The first character that is not blank space or a comment.
     *
     * Both formats allow `//` comments and both are usually headed by one, so
     * looking at the very first character of the file decides every commented
     * layout wrongly, in the direction that hurts: a JSON grid read as the text
     * format becomes one key per line of punctuation.
     */
    private fun firstMeaningfulChar(text: String): Char? =
        text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith(COMMENT_PREFIX) }
            ?.firstOrNull()

    /**
     * HeliBoard's text format: `label popup1 popup2 …` per line, a blank line
     * starts the next row.
     *
     * The format has no way to spell a delete, space or enter key, so every
     * file is missing all three. That is not a defect in the file — the app
     * that reads it draws its own bottom row — and [LayoutSpec.repair] adding
     * them back is exactly the right outcome.
     */
    fun fromSimpleText(text: String, name: String): ConvertedLayout? {
        val rows = mutableListOf<List<Key>>()
        val row = mutableListOf<Key>()
        var normalized = 0
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (row.isNotEmpty()) rows += row.toList()
                row.clear()
                continue
            }
            if (trimmed.startsWith(COMMENT_PREFIX)) continue
            val tokens = trimmed.split(' ', '\t').filter { it.isNotEmpty() }
            val label = tokens.first().let { token ->
                normalizeForScript(token).also { if (it != token) normalized++ }
            }
            row += Key(
                label = label,
                longPress = tokens.drop(1).map(::normalizeForScript),
            )
        }
        if (row.isNotEmpty()) rows += row.toList()
        return finish(rows, name, ForeignSource.HELIBOARD_TEXT, Report(normalized = normalized))
    }

    /**
     * The FlorisBoard/HeliBoard JSON grid: rows of key objects.
     *
     * Parsed leniently, with comments and trailing commas allowed, because
     * HeliBoard's own reader allows both and people hand-write these.
     */
    fun fromFlorisJson(text: String, name: String): ConvertedLayout? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return null
        val rowsJson = rowsOf(root) ?: return null
        val report = Report()
        val rows = rowsJson.mapNotNull { rowJson ->
            val row = (rowJson as? JsonArray) ?: return@mapNotNull null
            distributeExpandable(row.mapNotNull { keyFrom(it, report) })
        }
        return finish(rows, name, ForeignSource.FLORIS_JSON, report)
    }

    /**
     * The rows, whether the file is a bare array of them or an object that
     * names them. Floris ships the bare array; a hand-written file often wraps
     * it, and refusing those over a wrapper would be a poor trade.
     */
    private fun rowsOf(root: JsonElement): List<JsonElement>? = when (root) {
        is JsonArray -> root
        is JsonObject -> ROW_FIELDS.firstNotNullOfOrNull { root[it] as? JsonArray }
        is JsonPrimitive -> null
    }

    /**
     * Builds the spec, repairs it, and merges both sets of notes. Null when
     * there was nothing to convert — a file of comments, or an array of empty
     * arrays.
     */
    private fun finish(
        rows: List<List<Key>>,
        name: String,
        source: ForeignSource,
        report: Report,
    ): ConvertedLayout? {
        val kept = rows.filter { it.isNotEmpty() }
        if (kept.isEmpty()) return null
        val scaled = renormalizeWidths(kept)
        val layout = LayoutSpec(
            id = "foreign_${System.currentTimeMillis()}",
            name = layoutNameFrom(name),
            langId = "",
            layers = mapOf(LayoutLayer.LETTERS.key to LayerSpec(rows = scaled)),
        )
        val repaired = layout.repair()
        return ConvertedLayout(
            // The language is the caller's to set, from the picker this seeds.
            // Leaving it blank here rather than writing the guess in means the
            // guess can never reach storage without someone having seen it.
            layout = repaired.spec,
            notes = report.notes(scaled !== kept) + repaired.repairNotes,
            unmapped = report.unmapped.toList(),
            guessedLangId = guessLangId(scaled),
            source = source,
        )
    }

    /** The file name without its extensions, or a plain fallback. */
    private fun layoutNameFrom(name: String): String =
        name.substringAfterLast('/')
            .substringBefore('.')
            .replace('_', ' ')
            .trim()
            .ifBlank { FallbackName }

    private fun keyFrom(element: JsonElement, report: Report): Key? {
        if (element is JsonPrimitive) {
            val label = element.content.takeIf { element.isString } ?: return null
            return textKey(label, output = null, report = report)
        }
        val obj = element as? JsonObject ?: return null
        if (obj.string("type").equals(PLACEHOLDER_TYPE, ignoreCase = true)) {
            return Key(label = "", action = KeyAction.None, width = obj.width())
        }
        val code = obj.int("code")
        return when {
            code == null || code == MULTIPLE_CODE_POINTS -> characterKey(obj, code, report)
            // Space and enter are ordinary code points over there — 32 and 10 —
            // and real actions here. Left as text keys they would commit a bare
            // space and a bare newline, losing auto-space, the double-space full
            // stop and the action the enter key takes from the field. Worse,
            // repair would find no space key and add a *second* spacebar.
            code in PositiveActions -> actionKey(obj, code, report)
            code < 0 -> actionKey(obj, code, report)
            else -> characterKey(obj, code, report)
        }
    }

    /** A key that commits text: a code point, a run of them, or its own label. */
    private fun characterKey(obj: JsonObject, code: Int?, report: Report): Key? {
        val points = obj.intList("codePoints")
        val output = when {
            points.isNotEmpty() -> points.joinToString("") { String(Character.toChars(it)) }
            code != null && code > 0 -> String(Character.toChars(code))
            else -> null
        }
        val label = obj.string("label") ?: output ?: return null
        return textKey(label, output, report, obj)
    }

    private fun textKey(
        label: String,
        output: String?,
        report: Report,
        obj: JsonObject? = null,
    ): Key? {
        val cleanLabel = normalizeForScript(label).also { if (it != label) report.normalized++ }
        val cleanOutput = output?.let { normalizeForScript(it) }
        if (cleanLabel.isEmpty() && cleanOutput.isNullOrEmpty()) return null
        return Key(
            label = cleanLabel,
            // Only when it differs: a key whose output repeats its label is the
            // common case, and writing both doubles the file for nothing.
            output = cleanOutput?.takeIf { it != cleanLabel },
            // Never a materialized uppercase. A null shiftLabel already
            // uppercases at commit time for a cased script, which is what the
            // foreign `auto_text_key` means; writing one in would freeze the
            // wrong case for every script that has none.
            shiftLabel = null,
            width = obj?.width() ?: 1f,
            longPress = obj?.let { popupsOf(it, report) }.orEmpty(),
        )
    }

    /** A key that does something instead of typing. Null when nothing here does it. */
    private fun actionKey(obj: JsonObject, code: Int, report: Report): Key? {
        val action = PositiveActions[code] ?: keyActionForCode(code)
        if (action == null) {
            report.unmapped += code
            return null
        }
        if (code in ApproximateCodes) report.approximated++
        return Key(
            label = labelFor(action, obj.string("label")),
            action = action,
            width = obj.width(),
        )
    }

    /**
     * What to draw on an action key.
     *
     * The keyboard draws shift, delete, enter and their neighbours from icon
     * slots, so their labels are theirs to decide and the file's is discarded —
     * a file that spells its shift key "shift" or "⇧" would otherwise put a word
     * or a second, different arrow on a key that is already an icon. The keys
     * drawn as text keep the file's label, because "?123" and "ABC" travel
     * perfectly well and the file may have better ones.
     */
    private fun labelFor(action: KeyAction, given: String?): String {
        val fallback = action.fallbackLabel()
        if (fallback.isEmpty() || action == KeyAction.Space) return fallback
        return given?.let(::normalizeForScript)?.ifEmpty { null } ?: fallback
    }

    /**
     * The long-press letters, from whichever shape the file uses.
     *
     * `main` first so it becomes the corner hint, then `relevant`. A key that
     * points at a popup *group* instead of listing its letters is counted as a
     * loss: those groups live in a separate file that a single exported layout
     * does not carry, so there is nothing here to resolve them against.
     */
    private fun popupsOf(obj: JsonObject, report: Report): List<String> {
        val popup = obj["popup"]
        val entries = when (popup) {
            is JsonArray -> popup.toList()
            is JsonObject -> listOfNotNull(popup["main"]) + (popup["relevant"] as? JsonArray).orEmpty()
            is JsonPrimitive, null -> emptyList()
        }
        val letters = entries.mapNotNull { popupLabel(it) }.filter { it.isNotEmpty() }.distinct()
        if (letters.isEmpty() && (obj.int("groupId") ?: 0) != 0) report.lostPopups++
        return letters
    }

    private fun popupLabel(element: JsonElement): String? {
        val raw = when (element) {
            is JsonPrimitive -> element.content.takeIf { element.isString }
            is JsonObject -> element.string("label")
                ?: element.int("code")?.takeIf { it > 0 }?.let { String(Character.toChars(it)) }
            is JsonArray -> null
        }
        return raw?.let(::normalizeForScript)
    }

    /**
     * A row's expandable keys, given the width left over.
     *
     * HeliBoard spells "fill the rest of the row" as a width of -1, which is
     * how its spacebar is written. Resolved before [renormalizeWidths] so the
     * row is all real widths by the time anything is scaled, and floored so a
     * row that is already full cannot mint a zero-width key that repair would
     * then have to fix.
     */
    private fun distributeExpandable(row: List<Key>): List<Key> {
        val expandable = row.count { it.width == ExpandMarker }
        if (expandable == 0) return row
        val fixed = row.filter { it.width != ExpandMarker }.sumOf { it.width.toDouble() }
        val each = ((1.0 - fixed) / expandable).toFloat().coerceAtLeast(MinExpandWidth)
        return row.map { if (it.width == ExpandMarker) it.copy(width = each) else it }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

    /**
     * The declared width, as the file's own unit. A width the file spells as a
     * word ("regular") rather than a number becomes 1: those names mean "the
     * ordinary key" in a grid whose ordinary key is what everything else is
     * measured against.
     */
    private fun JsonObject.width(): Float {
        val raw = (this["width"] as? JsonPrimitive)?.content?.toFloatOrNull() ?: return 1f
        return when {
            raw == ExpandMarker -> ExpandMarker
            raw <= 0f || !raw.isFinite() -> 1f
            else -> raw
        }
    }

    private fun JsonObject.intList(key: String): List<Int> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
            .orEmpty()

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowComments = true
        allowTrailingComma = true
    }

    /** What a file has to start with to be JSON rather than the text format. */
    private val JSON_OPENERS = setOf('[', '{')

    /** Object fields a wrapped layout keeps its rows under. */
    private val ROW_FIELDS = listOf("arrangement", "rows", "keys", "layout")

    private const val COMMENT_PREFIX = "//"
    private const val PLACEHOLDER_TYPE = "placeholder"

    /**
     * The code a `multi_text_key` always carries. It says "read codePoints
     * instead", not "do something", so it must not reach the action table.
     */
    private const val MULTIPLE_CODE_POINTS = -902

    /**
     * Ordinary code points over there that are actions here: space, and the two
     * spellings of enter. Everything else positive is a character.
     */
    private val PositiveActions: Map<Int, KeyAction> = mapOf(
        0x20 to KeyAction.Space,
        0x0A to KeyAction.Enter,
        0x0D to KeyAction.Enter,
        0x09 to KeyAction.SendKey(KEYCODE_TAB),
    )
    private const val FallbackName = "Imported layout"

    /** The foreign width that means "take what is left of the row". */
    private const val ExpandMarker = -1f

    /** Narrowest an expanded key may end up, as a fraction of the row. */
    private const val MinExpandWidth = 0.1f
}

/**
 * The action a foreign key code means here, or null when nothing does.
 *
 * The numbering is shared: HeliBoard adopted FlorisBoard's constants, and added
 * its own from -10000 down. Codes that name something this keyboard reaches
 * another way — the settings screen, the clipboard panel, voice input, the
 * smartbar toggles — return null on purpose. They are not keys here, and a key
 * that silently does nothing is worse than a key that is honestly missing.
 *
 * Never returns [KeyAction.Unknown]: see the file header for why that would be
 * a crash rather than a compromise.
 */
@Suppress("CyclomaticComplexMethod")
internal fun keyActionForCode(code: Int): KeyAction? = when (code) {
    // Modifiers and layer keys.
    -1, -2 -> KeyAction.Mod(ModifierKey.CTRL)
    -3, -4 -> KeyAction.Mod(ModifierKey.ALT)
    -5, -6 -> KeyAction.Fn
    -11 -> KeyAction.Shift
    -13 -> KeyAction.CapsLock
    -10012, -10013 -> KeyAction.Mod(ModifierKey.META)
    -10044, -10045 -> KeyAction.Mod(ModifierKey.CTRL)
    -10046, -10047 -> KeyAction.Mod(ModifierKey.ALT)
    -10048, -10049 -> KeyAction.Mod(ModifierKey.META)

    // Editing. DELETE_WORD has no action of its own here; a plain delete is
    // closer than no key at all, and the note says so.
    -7, -8 -> KeyAction.Delete
    -9, -10 -> KeyAction.ForwardDelete
    -10005 -> KeyAction.Enter

    // Views. SYMBOLS2 is the second symbols page, which the symbols key already
    // steps to, so it collapses onto the same action.
    -201 -> KeyAction.Letters
    -202, -203 -> KeyAction.Symbols
    -204, -205 -> KeyAction.Numpad
    -212 -> KeyAction.Emoji
    // -221 is Florisboard's system input-method picker and -227 the layout
    // cycle. They used to collapse onto the cycle, which was the closest thing
    // this keyboard had; -221 now lands on the key it always meant.
    -221 -> KeyAction.InputMethodPicker
    -227 -> KeyAction.LanguageSwitch

    // Cursor movement and shortcuts, as raw key events.
    -21 -> KeyAction.SendKey(KEYCODE_DPAD_LEFT)
    -22 -> KeyAction.SendKey(KEYCODE_DPAD_RIGHT)
    -23 -> KeyAction.SendKey(KEYCODE_DPAD_UP)
    -24 -> KeyAction.SendKey(KEYCODE_DPAD_DOWN)
    -25 -> KeyAction.SendKey(KEYCODE_MOVE_HOME, META_CTRL_ON)
    -26 -> KeyAction.SendKey(KEYCODE_MOVE_END, META_CTRL_ON)
    -27 -> KeyAction.SendKey(KEYCODE_MOVE_HOME)
    -28 -> KeyAction.SendKey(KEYCODE_MOVE_END)
    -31 -> KeyAction.SendKey(KEYCODE_COPY)
    -32 -> KeyAction.SendKey(KEYCODE_CUT)
    -33 -> KeyAction.SendKey(KEYCODE_PASTE)
    -35 -> KeyAction.SendKey(KEYCODE_A, META_CTRL_ON)
    -131 -> KeyAction.SendKey(KEYCODE_Z, META_CTRL_ON)
    -132 -> KeyAction.SendKey(KEYCODE_Z, META_CTRL_ON or META_SHIFT_ON)
    -10010 -> KeyAction.SendKey(KEYCODE_PAGE_UP)
    -10011 -> KeyAction.SendKey(KEYCODE_PAGE_DOWN)
    -10014 -> KeyAction.SendKey(KEYCODE_TAB)
    -10015 -> KeyAction.SendKey(KEYCODE_DPAD_LEFT, META_CTRL_ON)
    -10016 -> KeyAction.SendKey(KEYCODE_DPAD_RIGHT, META_CTRL_ON)
    -10017 -> KeyAction.SendKey(KEYCODE_ESCAPE)
    in FunctionKeyCodes -> KeyAction.SendKey(KEYCODE_F1 + (FunctionKeyCodes.last - code))

    // A deliberate gap in the grid, in both files and here.
    -999 -> KeyAction.None

    else -> null
}

/**
 * Codes that map onto something close rather than something equal. Counted so
 * the import can say how many keys were approximated, without claiming the
 * layout came across untouched.
 */
internal val ApproximateCodes: Set<Int> = setOf(-8, -10, -203, -10005)

/**
 * Foreign widths brought onto this app's grid.
 *
 * The two are not the same unit. A foreign width is a fraction of the keyboard,
 * so a normal key is 0.1; here it is a weight against `gridWeightOf`, the width
 * the most rows share, which is 10 for every shipped grid. Copying the numbers
 * across passes every check [LayoutSpec.repair] makes — they are positive, under
 * [MaxKeyWidth], and the row is under [MaxRowWidth] — so nothing warns, and the
 * grid simply renders wrong.
 *
 * Multiplying by ten puts a ten-key row on the same footing as QWERTY's, and
 * leaves a nine-key home row summing to 9, which is how it centres itself today.
 * A file whose rows already total more than [FractionRowCeiling] is writing grid
 * units, not fractions, and passes through untouched.
 */
internal fun renormalizeWidths(rows: List<List<Key>>): List<List<Key>> {
    if (rows.isEmpty()) return rows
    val widest = rows.maxOf { row -> row.sumOf { it.width.toDouble() } }
    if (widest > FractionRowCeiling) return rows
    return rows.map { row -> row.map { it.copy(width = scaleWidth(it.width)) } }
}

private fun scaleWidth(width: Float): Float {
    val scaled = (width * FractionToGrid).coerceIn(MinGridWidth, MaxKeyWidth)
    // Two decimals: these are accumulated floats, and a row of ten 0.1 keys
    // should read as ten 1.0 keys rather than ten 0.99999994s.
    return (scaled * 100f).roundToInt() / 100f
}

/**
 * The characters on the keys, in the spelling the rest of the app uses.
 *
 * Unicode has two ways to write a letter that carries a nukta or a similar mark,
 * and normalizing to NFC only picks one of them: the composition exclusions —
 * U+09DF and its Bengali neighbours, the Devanagari, Oriya and Gurmukhi
 * equivalents — are deliberately left decomposed by NFC. The cluster composer
 * and the backspace rules test those code points as single characters, so a key
 * that arrives decomposed shapes and deletes wrongly while looking correct.
 *
 * Latin text is returned untouched, byte for byte: nothing below U+0080 can
 * carry a combining mark, and a normalizer that rewrites ASCII is a normalizer
 * nobody can reason about.
 */
internal fun normalizeForScript(text: String): String {
    if (text.isEmpty() || text.all { it.code < FIRST_NON_ASCII }) return text
    val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
    if (nfc.none { it.code in NuktaSigns }) return nfc
    val out = StringBuilder(nfc.length)
    var i = 0
    while (i < nfc.length) {
        val composed = if (i + 1 < nfc.length) {
            NuktaCompositions[(nfc[i].code shl SHIFT_BASE) or nfc[i + 1].code]
        } else {
            null
        }
        if (composed != null) {
            out.append(composed)
            i += 2
        } else {
            out.append(nfc[i])
            i++
        }
    }
    return out.toString()
}

/**
 * The language the characters on the keys suggest, or English when they suggest
 * nothing. A guess for a picker to start from, never an answer.
 */
internal fun guessLangId(rows: List<List<Key>>): String {
    val counts = HashMap<ScriptId, Int>()
    for (row in rows) {
        for (key in row) {
            if (key.action != KeyAction.Text) continue
            for (ch in (key.output ?: key.label)) {
                val script = scriptOf(ch) ?: continue
                counts[script] = (counts[script] ?: 0) + 1
            }
        }
    }
    val dominant = counts.maxByOrNull { it.value }?.key ?: return DefaultLangId
    return LanguageRegistry.all.firstOrNull { it.script == dominant }?.id ?: DefaultLangId
}

private fun scriptOf(ch: Char): ScriptId? =
    scriptRanges.firstOrNull { (_, range) -> ch.code in range }?.first

/** Built once: `ScriptRegistry.all` copies its map on every read. */
private val scriptRanges: List<Pair<ScriptId, IntRange>> by lazy {
    ScriptRegistry.all.filter { !it.unicodeRange.isEmpty() }.map { it.id to it.unicodeRange }
}

/** What the conversion had to give up, tallied as it goes. */
private class Report(
    var normalized: Int = 0,
    var approximated: Int = 0,
    var lostPopups: Int = 0,
    val unmapped: MutableList<Int> = mutableListOf(),
) {
    fun notes(scaled: Boolean): List<LayoutMessage> = buildList {
        if (unmapped.isNotEmpty()) {
            add(
                LayoutMessage(
                    pluralsRes = R.plurals.core_lang_foreign_keys_dropped,
                    quantity = unmapped.size,
                    args = listOf(unmapped.size, unmapped.distinct().joinToString(", ")),
                ),
            )
        }
        if (approximated > 0) {
            add(
                LayoutMessage(
                    pluralsRes = R.plurals.core_lang_foreign_keys_approximated,
                    quantity = approximated,
                    args = listOf(approximated),
                ),
            )
        }
        if (lostPopups > 0) {
            add(
                LayoutMessage(
                    pluralsRes = R.plurals.core_lang_foreign_popups_missing,
                    quantity = lostPopups,
                    args = listOf(lostPopups),
                ),
            )
        }
        if (normalized > 0) {
            add(
                LayoutMessage(
                    pluralsRes = R.plurals.core_lang_foreign_letters_normalized,
                    quantity = normalized,
                    args = listOf(normalized),
                ),
            )
        }
        if (scaled) add(LayoutMessage(stringRes = R.string.core_lang_foreign_widths_scaled))
    }
}

/** Language a layout falls back to when its characters name no script. */
private const val DefaultLangId = "en"

/** Above this, a row is written in grid units already. */
private const val FractionRowCeiling = 2.0

private const val FractionToGrid = 10f
private const val MinGridWidth = 0.1f

private const val FIRST_NON_ASCII = 0x80
private const val SHIFT_BASE = 16

/**
 * Base letter plus nukta, and the single character Unicode also has for it.
 *
 * These are the composition exclusions: NFC will not join them, so they have to
 * be joined here. Keyed on `(base shl 16) or nukta` to keep the lookup to one
 * map read per character with nothing allocated.
 *
 * The base letters are written as code points on purpose. A composed letter and
 * its decomposed pair are indistinguishable on screen, so a table written
 * entirely in glyphs cannot be proof-read, and an entry that decomposed on its
 * way into the file would map a letter to itself and silently do nothing. The
 * composed values have to stay glyphs — they are `Char`, and a decomposed pair
 * would not compile — and a test asserts each one decomposes back to its key.
 */
private val NuktaCompositions: Map<Int, Char> = buildMap {
    fun put(base: Int, nukta: Int, composed: Char) {
        put((base shl SHIFT_BASE) or nukta, composed)
    }
    // Devanagari.
    put(0x0915, DEVANAGARI_NUKTA, 'क़')
    put(0x0916, DEVANAGARI_NUKTA, 'ख़')
    put(0x0917, DEVANAGARI_NUKTA, 'ग़')
    put(0x091C, DEVANAGARI_NUKTA, 'ज़')
    put(0x0921, DEVANAGARI_NUKTA, 'ड़')
    put(0x0922, DEVANAGARI_NUKTA, 'ढ़')
    put(0x0928, DEVANAGARI_NUKTA, 'ऩ')
    put(0x092B, DEVANAGARI_NUKTA, 'फ़')
    put(0x092F, DEVANAGARI_NUKTA, 'य़')
    put(0x0930, DEVANAGARI_NUKTA, 'ऱ')
    put(0x0933, DEVANAGARI_NUKTA, 'ऴ')
    // Bengali. These three matter most here: the cluster composer and the
    // backspace rules name the composed code points directly.
    put(0x09A1, BENGALI_NUKTA, 'ড়')
    put(0x09A2, BENGALI_NUKTA, 'ঢ়')
    put(0x09AF, BENGALI_NUKTA, 'য়')
    // Gurmukhi.
    put(0x0A16, GURMUKHI_NUKTA, 'ਖ਼')
    put(0x0A17, GURMUKHI_NUKTA, 'ਗ਼')
    put(0x0A1C, GURMUKHI_NUKTA, 'ਜ਼')
    put(0x0A2B, GURMUKHI_NUKTA, 'ਫ਼')
    put(0x0A32, GURMUKHI_NUKTA, 'ਲ਼')
    put(0x0A38, GURMUKHI_NUKTA, 'ਸ਼')
    // Oriya.
    put(0x0B21, ORIYA_NUKTA, 'ଡ଼')
    put(0x0B22, ORIYA_NUKTA, 'ଢ଼')
}

private const val DEVANAGARI_NUKTA = 0x093C
private const val BENGALI_NUKTA = 0x09BC
private const val GURMUKHI_NUKTA = 0x0A3C
private const val ORIYA_NUKTA = 0x0B3C

/** The four nukta signs, so text carrying none skips the scan entirely. */
private val NuktaSigns =
    setOf(DEVANAGARI_NUKTA, BENGALI_NUKTA, GURMUKHI_NUKTA, ORIYA_NUKTA)

/** F1 through F12, which HeliBoard numbers downwards. */
private val FunctionKeyCodes = -10039..-10028

// Written as numbers rather than KeyEvent.KEYCODE_* for the reason KeyActions.kt
// gives: this module is layout data and takes no android.view import.
private const val KEYCODE_A = 29
private const val KEYCODE_Z = 54
private const val KEYCODE_TAB = 61
private const val KEYCODE_ESCAPE = 111
private const val KEYCODE_DPAD_UP = 19
private const val KEYCODE_DPAD_DOWN = 20
private const val KEYCODE_DPAD_LEFT = 21
private const val KEYCODE_DPAD_RIGHT = 22
private const val KEYCODE_PAGE_UP = 92
private const val KEYCODE_PAGE_DOWN = 93
private const val KEYCODE_MOVE_HOME = 122
private const val KEYCODE_MOVE_END = 123
private const val KEYCODE_CUT = 277
private const val KEYCODE_COPY = 278
private const val KEYCODE_PASTE = 279
private const val KEYCODE_F1 = 131
private const val META_SHIFT_ON = 0x1
private const val META_CTRL_ON = 0x1000
