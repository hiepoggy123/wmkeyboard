package com.wasimaster.wmkeyboard.core.layout

import com.wasimaster.wmkeyboard.core.script.ComposerType
import com.wasimaster.wmkeyboard.core.script.LanguageDef
import com.wasimaster.wmkeyboard.core.script.LanguageRegistry
import com.wasimaster.wmkeyboard.core.script.ScriptDef
import com.wasimaster.wmkeyboard.core.script.ScriptRegistry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * A layer of the keyboard: which grid is on screen. [LETTERS] is the one a
 * layout has to define; the rest are overrides, and a layout that leaves one
 * out inherits the shipped grid rather than showing nothing.
 *
 * [key] rather than the enum name is what goes in the file, so a hand-written
 * layout reads `"symbols2"` the way the shipped grid is already named, and so
 * renaming an entry here is not a format change.
 */
enum class LayoutLayer(val key: String) {
    LETTERS("letters"),
    SYMBOLS("symbols"),
    SYMBOLS_SHIFTED("symbols2"),
    NUMBER("number"),
    PHONE("phone"),
    DATE("date"),
    TIME("time"),
    DATETIME("datetime"),
    FN("fn"),
    ;

    /**
     * Whether the user reaches this layer by pressing a key, rather than by
     * focusing a field of the matching kind.
     *
     * Only the cycled layers need a key back to the letters. The numeric pads
     * deliberately have none — a number field gets its keypad whatever the
     * layout mode says, so there is nothing to leave *to*, and demanding an ABC
     * key on them would put one on five shipped grids that have never had one.
     */
    val isCycled: Boolean
        get() = this == LETTERS || this == SYMBOLS || this == SYMBOLS_SHIFTED || this == FN
}

/**
 * One grid, plus the number row shown above it when that setting is on.
 *
 * Wrapped in an object rather than stored as a bare `List<List<Key>>` because
 * the raw-JSON editor is a supported way to write these, and
 * `{"letters": {"rows": [...]}}` is readable where `{"letters": [[...]]}` is
 * three levels of anonymous brackets.
 *
 * A null [numberRow] takes the digits the layer has always shown — see
 * `KeyRows`, which owns the per-layer defaults.
 */
@Serializable
data class LayerSpec(
    val rows: List<List<Key>>,
    val numberRow: List<Key>? = null,
)

/**
 * A complete keyboard layout as the user owns it: the grids for every layer it
 * overrides, plus the language it types in.
 *
 * A custom layout is not a new language — it is an existing one wearing a
 * different key grid. [langId] is what makes that work: the dictionary,
 * autocorrect sources, script rules, handwriting and dictation language and
 * shift behaviour all key off the language (and its script) named here, so
 * "my own Bengali arrangement" inherits everything Bengali knows without
 * restating any of it. [composer] optionally overrides how keystrokes compose —
 * the one thing the language cannot decide alone, since Bengali hosts both the
 * transliterating Avro and the fixed Probhat/Jatiya on one script.
 *
 * [legacyBaseMode] captures the `baseMode` field written by builds before the
 * language registry (a serialized `InputMode` name); [LayoutCodec] migrates it
 * into [langId] on the first read and clears it. Nothing else reads it.
 *
 * [layers] is keyed by [LayoutLayer.key] rather than by the enum, because an
 * enum used as a map *key* is not covered by `coerceInputValues` — a layer name
 * from a newer build would throw and cost the user the whole file, where an
 * unrecognized string is simply ignored.
 */
@Serializable
data class LayoutSpec(
    val id: String,
    val name: String,
    /** Language id ([LanguageDef.id], e.g. "en", "bn", "fr"); blank until migrated. */
    val langId: String = "",
    /** Keystroke composer override; null inherits the script's default composer. */
    val composer: ComposerType? = null,
    @SerialName("baseMode") val legacyBaseMode: String? = null,
    val layers: Map<String, LayerSpec> = emptyMap(),
    /**
     * Typo-proximity rows, one string of committed characters per physical row,
     * for the one thing the grid cannot express on its own: a staggered or split
     * arrangement whose rows do not share a column origin. Null — the normal
     * case — derives them from [LayoutLayer.LETTERS]; see
     * [com.wasimaster.wmkeyboard.core.prediction.KeyProximity.forLayout].
     */
    val proximityRows: List<String>? = null,
    /** Format revision, bumped by [LayoutCodec] migrations. */
    val version: Int = CurrentLayoutSpecVersion,
) {
    /** The grid for [layer], or null when this layout does not override it. */
    fun layer(layer: LayoutLayer): LayerSpec? = layers[layer.key]
}

/**
 * The language this layout types, resolved from [LayoutSpec.langId] against the
 * registry. An id this build does not know falls back to a generic Latin
 * language so the keyboard still renders and types.
 */
fun LayoutSpec.language(): LanguageDef = LanguageRegistry.byId(langId)

/** The script [language] writes, carrying its direction, case and font behaviour. */
fun LayoutSpec.script(): ScriptDef = ScriptRegistry[language().script]

/**
 * The composer that turns keystrokes into text: the layout's explicit
 * [LayoutSpec.composer] override (Avro's transliteration) when set, otherwise the
 * script's default (dead keys for Latin, cluster shaping for Indic).
 */
fun LayoutSpec.composerType(): ComposerType = composer ?: script().composer

/**
 * How many rounds of format changes [LayoutSpec] has been through. Bump it and
 * add a branch to `LayoutCodec.migrateLayout` whenever a stored field changes
 * shape — the same hook `KeyboardModeCodec.migrateMode` provides.
 *
 * v2: `baseMode` (a serialized `InputMode`) became [LayoutSpec.langId] +
 * [LayoutSpec.composer].
 */
const val CurrentLayoutSpecVersion: Int = 2

private val layoutJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    // A layout naming an InputMode or ClipboardKeyAction this build does not
    // have coerces to the field's default instead of failing the whole file.
    // The sibling codecs (ThemeCodec, SymbolSetCodec, KeyboardModeCodec) do
    // without this because none of them stores an enum a newer build is likely
    // to grow; a layout stores three.
    coerceInputValues = true
    serializersModule = SerializersModule {
        // An action tag this build does not know decodes to KeyAction.Unknown
        // rather than throwing, so one key written by a newer version cannot
        // cost the user the other forty. `repair` drops them and says so.
        polymorphicDefaultDeserializer(KeyAction::class) { tag ->
            UnknownKeyActionSerializer(tag.orEmpty())
        }
    }
}

object LayoutCodec {
    fun encodeList(layouts: List<LayoutSpec>): String = layoutJson.encodeToString(layouts)

    fun decodeList(json: String): List<LayoutSpec> =
        runCatching { layoutJson.decodeFromString<List<LayoutSpec>>(json) }
            .getOrDefault(emptyList())
            .map(::migrateLayout)

    fun encode(layout: LayoutSpec): String = layoutJson.encodeToString(layout)

    fun decode(json: String): LayoutSpec? =
        runCatching { layoutJson.decodeFromString<LayoutSpec>(json) }
            .getOrNull()
            ?.let(::migrateLayout)

    /**
     * Brings a stored layout up to [CurrentLayoutSpecVersion]. The v1→v2 step
     * converts a pre-registry `baseMode` ([LayoutSpec.legacyBaseMode]) into a
     * [LayoutSpec.langId], moving Avro's transliteration onto [LayoutSpec.composer]
     * so the phonetic-vs-fixed Bengali distinction survives. Gated on a blank
     * langId rather than the version alone, so a hand-written layout that omits
     * the version field still migrates instead of silently defaulting to English.
     */
    private fun migrateLayout(spec: LayoutSpec): LayoutSpec {
        if (spec.langId.isBlank()) {
            val langId = spec.legacyBaseMode?.let { LEGACY_MODE_LANG[it] } ?: "en"
            val composer =
                if (spec.legacyBaseMode == "AVRO") ComposerType.TRANSLITERATE else spec.composer
            return spec.copy(
                langId = langId,
                composer = composer,
                legacyBaseMode = null,
                version = CurrentLayoutSpecVersion,
            )
        }
        return if (spec.version < CurrentLayoutSpecVersion) {
            spec.copy(version = CurrentLayoutSpecVersion)
        } else {
            spec
        }
    }

    /** Exposed for [LayoutFile], which parses the envelope itself. */
    internal val json: Json get() = layoutJson
}

/**
 * Every layout the user has, built-ins first in their shipped order, then their
 * own. A custom layout whose id matches a built-in is an *edit* of that
 * built-in: it takes the built-in's slot rather than appearing twice, so a
 * reference pinned to "builtin_qwerty" keeps working and deleting the edit
 * restores the shipped grid — which is why the editor's button says "Reset" on
 * a built-in and "Delete" on a custom. Same rule as `resolveSymbolSets`.
 */
fun resolveLayouts(custom: List<LayoutSpec>): List<LayoutSpec> {
    val overrides = custom.associateBy { it.id }
    val builtIns = BuiltInLayouts.all.map { overrides[it.id] ?: it }
    val builtInIds = BuiltInLayouts.all.mapTo(HashSet()) { it.id }
    return builtIns + custom.filter { it.id !in builtInIds }
}

/**
 * The layout [id] names, falling back to the default when it has been deleted
 * out from under a stored reference. Never returns null: a keyboard with no
 * grid has nothing to draw and no way for the user to recover.
 */
fun resolveLayout(custom: List<LayoutSpec>, id: String): LayoutSpec =
    resolveLayouts(custom).firstOrNull { it.id == id } ?: BuiltInLayouts.default
