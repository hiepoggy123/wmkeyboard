package com.wasimaster.wmkeyboard.core.layout

import androidx.compose.runtime.Immutable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure

/**
 * What a key does when it is tapped.
 *
 * A sealed interface rather than the flat enum this started as, because the
 * actions a custom layout needs carry a payload — a modifier key names which
 * modifier it latches, a raw-key-event key names the Android key code it
 * injects. Keeping the enum and hanging nullable `modifier`/`keyCode` fields
 * off [Key] was the alternative and was rejected: it makes
 * `action = Shift, keyCode = 61` representable, and every reader would then
 * have to re-derive which fields its own branch is allowed to trust.
 *
 * The original values stay `data object`s so that `key.action ==
 * KeyAction.Text` and the three dozen other equality comparisons scattered
 * through the keyboard view keep working verbatim. Only `when (key.action)`
 * subjects have to grow branches, and only one of those — the dispatch in
 * `WMKeyboardService.onKey` — is exhaustive, which is exactly the site that
 * *should* fail to compile when an action is added.
 *
 * [Immutable] is load-bearing, not decoration: [Key] is a Compose parameter,
 * the Compose compiler infers a sealed interface as unstable, and an unstable
 * [Key] under strong skipping falls back to reference comparison — which
 * `currentLayout`'s rewrite pass defeats by allocating fresh keys on every
 * recomposition. Without this annotation every key recomposes on every
 * keystroke, and nothing about the app looks wrong while it happens.
 */
@Immutable
@Serializable
sealed interface KeyAction {

    /** Commits [Key.output], falling back to [Key.label]. The default. */
    @Serializable @SerialName("text") data object Text : KeyAction

    @Serializable @SerialName("shift") data object Shift : KeyAction

    @Serializable @SerialName("delete") data object Delete : KeyAction

    @Serializable @SerialName("space") data object Space : KeyAction

    @Serializable @SerialName("enter") data object Enter : KeyAction

    /** Steps LETTERS → SYMBOLS → SYMBOLS_SHIFTED → SYMBOLS. */
    @Serializable @SerialName("symbols") data object Symbols : KeyAction

    /** Jumps straight back to the letter layer from wherever you are. */
    @Serializable @SerialName("letters") data object Letters : KeyAction

    @Serializable @SerialName("language_switch") data object LanguageSwitch : KeyAction

    @Serializable @SerialName("emoji") data object Emoji : KeyAction

    /** A deliberate gap in the grid: drawn as empty space, swallows its taps. */
    @Serializable @SerialName("none") data object None : KeyAction

    /**
     * An action written by a build newer than this one. Decoding keeps the tag
     * so the failure is reportable ("2 keys use an action this version does
     * not know"), and [LayoutSpec.repair] drops the key so the row re-flows
     * around it rather than rendering a button that silently does nothing.
     *
     * The foreign payload is deliberately *not* retained. Keeping it would
     * mean a hand-written serializer that re-emits an arbitrary JSON object
     * inline, and a layout that survives a round trip down to an old build and
     * back is not worth that much machinery. Re-saving on an old build loses
     * the key, which is what the import report warns about.
     */
    @Serializable @SerialName("unknown") data class Unknown(val tag: String) : KeyAction
}

/** Clipboard shortcut a letter key can perform on long press (A/C/V/X). */
enum class ClipboardKeyAction { SELECT_ALL, COPY, PASTE, CUT }

/**
 * What a key means to the runtime beyond the character it types.
 *
 * Field adaptation used to find these slots by matching `label == ","` on the
 * last row, which silently skipped any custom layout whose bottom row is
 * arranged differently: an email box in such a layout would keep its comma and
 * never get its @ key, with nothing to tell the user why.
 */
enum class KeyRole {
    /** The sentence-punctuation slot; gains domain suffixes in EMAIL/URI fields. */
    Period,

    /** The secondary-punctuation slot; becomes @ in EMAIL, / in URI, or the emoji key. */
    Comma,
}

/**
 * Decodes an unregistered action tag into [KeyAction.Unknown], keeping the tag
 * and discarding whatever fields came with it.
 *
 * This is the only non-obvious serialization code in the layout model, which
 * is why `LayoutCodecTest` exercises it first: a layout whose second key has
 * `{"type":"teleport","destination":"mars"}` must decode to a three-key layout
 * with `Unknown("teleport")` in the middle, not throw and cost the user the
 * other two.
 */
internal class UnknownKeyActionSerializer(
    private val tag: String,
) : KSerializer<KeyAction.Unknown> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("wm.unknownKeyAction")

    override fun deserialize(decoder: Decoder): KeyAction.Unknown {
        // The descriptor has no elements, so with ignoreUnknownKeys on, the
        // decoder steps over the foreign payload and reports DECODE_DONE.
        decoder.decodeStructure(descriptor) {}
        return KeyAction.Unknown(tag)
    }

    override fun serialize(encoder: Encoder, value: KeyAction.Unknown): Nothing =
        throw UnsupportedOperationException(
            "unknown actions are dropped by repair and never written back",
        )
}
