package com.wasimaster.wmkeyboard.core.keyman

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/**
 * Keyman's `.keyman-touch-layout` document — the on-screen keyboard half of a
 * Keyman keyboard, and the only part of one that describes a grid.
 *
 * The same shape reaches us from two places: the `.keyman-touch-layout` source
 * file in the keyboards repository, and the `KVKL` property of a compiled
 * keyboard `.js` (see [KeymanJs]). They are the same JSON, so there is one
 * model and one converter.
 *
 * ## Why the loose primitives
 *
 * Modern `kmc` writes this with `JSON.stringify`, so it is strict JSON. Files
 * compiled by the older Delphi toolchain are not quite: `row.id` comes through
 * as a JSON *number* rather than a string in a minority of keyboards, and
 * `width`, `pad`, `sp` and `fontsize` are written as quoted strings (`"100"`)
 * everywhere. Rather than turn the whole parse lenient — which would cost the
 * error detection that catches a genuinely malformed file — each affected field
 * takes a serializer that accepts either spelling. See [LooseString],
 * [LooseInt] and [LooseFloat].
 */
@Serializable
data class KeymanTouchLayout(
    val phone: TouchPlatform? = null,
    val tablet: TouchPlatform? = null,
    val desktop: TouchPlatform? = null,
) {
    /**
     * The platform to convert from, preferring the one whose proportions suit a
     * handset. Keyman authors overwhelmingly write `tablet` and let the engine
     * scale it, so a missing `phone` is the normal case rather than a defect.
     */
    fun preferred(): TouchPlatform? = phone ?: tablet ?: desktop

    /** The tablet grid, when it differs from [preferred]. Feeds tablet parity. */
    fun tabletVariant(): TouchPlatform? = tablet?.takeIf { it !== preferred() }
}

@Serializable
data class TouchPlatform(
    val font: String? = null,
    val fontsize: String? = null,
    /**
     * Draw the underlying US key cap on each key as well as [TouchKey.text].
     * The affordance mnemonic keyboards want; ~77% of the corpus sets it false.
     */
    val displayUnderlying: Boolean = false,
    val defaultHint: String? = null,
    val layer: List<TouchLayer> = emptyList(),
)

@Serializable
data class TouchLayer(
    val id: String = "",
    val row: List<TouchRow> = emptyList(),
)

@Serializable
data class TouchRow(
    @Serializable(with = LooseString::class) val id: String = "",
    val key: List<TouchKey> = emptyList(),
)

/**
 * One key, one longpress subkey, one multitap step or one flick target — the
 * format uses the same object for all four.
 *
 * Keyman defines gestures as one level deep: [sk], [multitap] and [flick] on a
 * key reached *through* one of those fields must be ignored. The converter
 * relies on that rather than recursing.
 */
@Serializable
data class TouchKey(
    val id: String = "",
    val text: String? = null,
    /**
     * The modifier combination to match rules against, overriding the layer the
     * key sits in. Not a layer switch — that is [nextlayer], which wins over a
     * rule's own `layer()` statement.
     */
    val layer: String? = null,
    val nextlayer: String? = null,
    val font: String? = null,
    val fontsize: String? = null,
    /** Key kind. See [KeymanKeySp]. */
    @Serializable(with = LooseInt::class) val sp: Int = 0,
    /** Left padding, as a percentage of one default key width. */
    @Serializable(with = LooseFloat::class) val pad: Float? = null,
    /** Width, as a percentage of one default key width. 100 is a normal key. */
    @Serializable(with = LooseFloat::class) val width: Float? = null,
    /** Longpress subkeys. */
    val sk: List<TouchKey> = emptyList(),
    /** Repeated-tap cycle. */
    val multitap: List<TouchKey> = emptyList(),
    /** Direction (`n`, `ne`, `e`, …) to target key. */
    val flick: Map<String, TouchKey> = emptyMap(),
    val hint: String? = null,
    /** Longpress default, chosen when the user lifts without picking. */
    @SerialName("default") val isDefault: Boolean = false,
) {
    /** True when [text] is a `*Special*` token that renders from the icon font. */
    val isSpecialLabel: Boolean get() = text != null && SPECIAL_LABEL.matches(text)

    private companion object {
        val SPECIAL_LABEL = Regex("""\*[A-Za-z0-9]+\*""")
    }
}

/**
 * [TouchKey.sp] — what kind of key this is.
 *
 * 3 and 4 (`customSpecial`, `customSpecialActive`) are private to the KeymanWeb
 * runtime and never appear in a file, so they are absent here; an unrecognised
 * value reads as [NORMAL].
 */
enum class KeymanKeySp(val code: Int) {
    /** Emits a character. */
    NORMAL(0),

    /** A frame key — shift, enter, backspace — drawn from the OSK icon font. */
    SPECIAL(1),

    /** A frame key whose layer is the one on screen, e.g. shift while shifted. */
    SPECIAL_ACTIVE(2),

    /** Styling only: colours the key to suggest deadkey-ish behaviour. */
    DEADKEY(8),

    /** Drawn as a blank cap that blocks touches, keeping the grid's shape. */
    BLANK(9),

    /** Not drawn at all, but still occupies its width. */
    SPACER(10),
    ;

    companion object {
        fun of(code: Int): KeymanKeySp = entries.firstOrNull { it.code == code } ?: NORMAL
    }
}

/**
 * The `.keyman-touch-layout` reader.
 *
 * [parse] never throws: a file this cannot read is a [KeymanFault], the same as
 * every other refusal in this module, so a caller can report it rather than
 * catch it.
 */
object KeymanTouchLayoutReader {

    /**
     * Strict on purpose. The two known deviations from JSON are handled by the
     * field serializers above and by [KeymanJs.collapseVersionTernaries], not by
     * relaxing the parser — a lenient parse would read a malformed file as a
     * half-empty keyboard instead of refusing it.
     */
    internal val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(text: String): KeymanResult<KeymanTouchLayout> {
        val cleaned = KeymanJs.collapseVersionTernaries(text)
        val doc = runCatching { json.decodeFromString<KeymanTouchLayout>(cleaned) }
            .getOrElse { return KeymanResult.Failure(KeymanFault.TOUCH_LAYOUT_UNREADABLE) }
        if (doc.preferred()?.layer.isNullOrEmpty()) {
            return KeymanResult.Failure(KeymanFault.TOUCH_LAYOUT_EMPTY)
        }
        return KeymanResult.Success(doc)
    }
}

/** Accepts a JSON string or number, yielding the string spelling. */
internal object LooseString : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.wasimaster.wmkeyboard.core.keyman.LooseString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        return json.decodeJsonElement().jsonPrimitive.content
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/** Accepts a JSON number or a quoted number, yielding an Int. */
internal object LooseInt : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.wasimaster.wmkeyboard.core.keyman.LooseInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val json = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val raw = json.decodeJsonElement().jsonPrimitive.content
        return raw.toIntOrNull() ?: raw.toFloatOrNull()?.toInt() ?: 0
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/** Accepts a JSON number or a quoted number, yielding a Float. */
internal object LooseFloat : KSerializer<Float> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.wasimaster.wmkeyboard.core.keyman.LooseFloat", PrimitiveKind.FLOAT)

    override fun deserialize(decoder: Decoder): Float {
        val json = decoder as? JsonDecoder ?: return decoder.decodeFloat()
        return json.decodeJsonElement().jsonPrimitive.content.toFloatOrNull() ?: 0f
    }

    override fun serialize(encoder: Encoder, value: Float) = encoder.encodeFloat(value)
}
