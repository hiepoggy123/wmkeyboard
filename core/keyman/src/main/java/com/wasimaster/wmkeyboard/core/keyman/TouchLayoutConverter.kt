package com.wasimaster.wmkeyboard.core.keyman

import com.wasimaster.wmkeyboard.core.layout.FlickDirection
import com.wasimaster.wmkeyboard.core.layout.Key
import com.wasimaster.wmkeyboard.core.layout.KeyAction
import com.wasimaster.wmkeyboard.core.layout.LayerSpec
import com.wasimaster.wmkeyboard.core.layout.LayoutLayer
import com.wasimaster.wmkeyboard.core.layout.LayoutMessage
import com.wasimaster.wmkeyboard.core.layout.LayoutSpec
import com.wasimaster.wmkeyboard.core.layout.repair

/**
 * Turns a Keyman touch layout into a [LayoutSpec].
 *
 * The grid only. What each key *types* comes from the keyboard's rules, which is
 * why an ordinary key converts to [KeyAction.KeymanKey] carrying its virtual key
 * rather than to a text key carrying its cap: the cap and the output agree on a
 * positional keyboard and disagree on a mnemonic one, and only the engine knows
 * which this is.
 */
object TouchLayoutConverter {

    /**
     * Converts [doc], binding the result to [keyboardId] so the runtime can find
     * the rules. Never throws; an unusable document comes back as a fault.
     */
    fun convert(
        doc: KeymanTouchLayout,
        keyboardId: String,
        displayName: String,
    ): KeymanResult<ConvertedKeymanLayout> {
        val platform = doc.preferred()
            ?: return KeymanResult.Failure(KeymanFault.TOUCH_LAYOUT_EMPTY)
        if (platform.layer.isEmpty()) return KeymanResult.Failure(KeymanFault.TOUCH_LAYOUT_EMPTY)

        val report = Report()
        val converted = LinkedHashMap<String, LayerSpec>()
        val layerNames = platform.layer.map { it.id.lowercase() }.toSet() + MODIFIER_LAYER_NAMES
        for (layer in platform.layer) {
            val rows = layer.row.map { row -> convertRow(row, layerNames, report) }
                .filter { it.isNotEmpty() }
            if (rows.isEmpty()) continue
            converted[layer.id] = LayerSpec(rows = rows)
        }
        if (converted.isEmpty()) return KeymanResult.Failure(KeymanFault.TOUCH_LAYOUT_EMPTY)

        val folded = foldShiftLayer(converted, report)
        val named = folded.mapKeys { (id, _) -> layerName(id) }

        if (LayoutLayer.LETTERS.key !in named) {
            // Everything downstream assumes a letters layer exists: `compile`
            // falls back to it and `repair` guarantees delete, space and enter
            // on it. A Keyman layout whose base layer is called something else
            // would otherwise convert into a grid with no way in.
            return KeymanResult.Failure(KeymanFault.TOUCH_LAYOUT_EMPTY)
        }

        val spec = LayoutSpec(
            id = "asset_kmn_$keyboardId",
            name = displayName.ifBlank { keyboardId },
            langId = "",
            layers = named,
        )

        // Repair here rather than leaving it to the loader, the same way
        // `ForeignLayouts.finish` does. Some Keyman keyboards genuinely ship a
        // symbols layer with no key back to the letters, and a few ship no
        // spacebar; both are fine under Keyman's own renderer, which always
        // draws its own frame, and both strand the user under ours. Running the
        // existing pass reuses rules that are already tested instead of
        // reimplementing them, and it makes "a converted layout never needs
        // repair" true by construction — which is the invariant every committed
        // asset is held to.
        val repaired = spec.repair()
        return KeymanResult.Success(
            ConvertedKeymanLayout(repaired.spec, report.toReport(), repaired.repairNotes),
        )
    }

    /**
     * Keyman's base layer is `default`, its `?123` layer is `numeric` and its
     * further symbols layer is `symbol`. Those three have homes in
     * [LayoutLayer]; everything else — `shift`, `caps`, `altgr` and whatever the
     * author invented — keeps its own name, which the layer map already accepts
     * and which lets a key's `nextlayer` resolve by identity with no lookup.
     */
    private fun layerName(keymanId: String): String = when (keymanId.lowercase()) {
        "default" -> LayoutLayer.LETTERS.key
        "numeric" -> LayoutLayer.SYMBOLS.key
        "symbol" -> LayoutLayer.SYMBOLS_SHIFTED.key
        else -> keymanId
    }

    /**
     * Folds Keyman's `shift` layer onto the base layer as per-key
     * [Key.shiftLabel], when the two grids line up.
     *
     * We have no shift layer: shift is runtime state resolved in `keyOutput`,
     * which uses `shiftLabel` when a key has one and uppercases otherwise. A
     * Keyman `shift` layer that matches the base position for position is
     * exactly that information, and folding it means the shift key behaves the
     * way it does on every other layout here rather than switching grids.
     *
     * When the shapes differ the shift layer is kept as a named layer instead —
     * some keyboards genuinely put a different set of letters there, and
     * silently zipping mismatched grids would put the wrong character on the
     * wrong key.
     */
    private fun foldShiftLayer(
        layers: Map<String, LayerSpec>,
        report: Report,
    ): Map<String, LayerSpec> {
        val base = layers["default"] ?: return layers
        val shift = layers.entries.firstOrNull { it.key.equals("shift", ignoreCase = true) }
            ?: return layers
        if (!sameShape(base, shift.value)) {
            report.shiftLayerKept = true
            return layers
        }
        val merged = base.rows.mapIndexed { r, row ->
            row.mapIndexed { c, key ->
                val upper = shift.value.rows[r][c]
                val upperText = upper.output ?: upper.label
                if (upperText.isBlank() || upperText == (key.output ?: key.label)) {
                    key
                } else {
                    key.copy(shiftLabel = upperText)
                }
            }
        }
        report.shiftLayerFolded = true
        val without = layers.filterKeys { it != shift.key } + ("default" to base.copy(rows = merged))
        return without.mapValues { (_, spec) -> retargetShiftKeys(spec, shift.key) }
    }

    /**
     * Rewrites keys that pointed at the now-folded shift layer into our own
     * shift key.
     *
     * Folding removes the layer, and any key elsewhere whose `nextlayer` named
     * it would otherwise dangle — which is not cosmetic: a dangling target is a
     * key that appears to do something and does nothing. Our [KeyAction.Shift]
     * is the right destination rather than a dead end, because after folding the
     * shift *state* is exactly what that key was reaching for.
     */
    private fun retargetShiftKeys(spec: LayerSpec, foldedName: String): LayerSpec =
        spec.copy(
            rows = spec.rows.map { row ->
                row.map { key ->
                    val target = (key.action as? KeyAction.KeymanKey)?.nextLayer
                    if (target == foldedName || target == "shift") {
                        key.copy(action = KeyAction.Shift, label = key.label.ifBlank { "⇧" })
                    } else {
                        key
                    }
                }
            },
        )

    private fun sameShape(a: LayerSpec, b: LayerSpec): Boolean =
        a.rows.size == b.rows.size &&
            a.rows.indices.all { a.rows[it].size == b.rows[it].size }

    private fun convertRow(
        row: TouchRow,
        layerNames: Set<String>,
        report: Report,
    ): List<Key> {
        val out = ArrayList<Key>(row.key.size + 1)
        for (k in row.key) {
            // Keyman expresses a leading gap as padding on the next key; we have
            // no padding, so it becomes a spacer of the same width. The default
            // inter-key gap is 5%, so only padding beyond that is real.
            val pad = k.pad ?: 0f
            if (pad > DEFAULT_PAD * 2) {
                out += Key(label = "", action = KeyAction.None, width = (pad - DEFAULT_PAD) / 100f)
            }
            out += convertKey(k, layerNames, report) ?: continue
        }
        return out
    }

    private fun convertKey(k: TouchKey, layerNames: Set<String>, report: Report): Key? {
        // Some authors cap a layer-switch key with the layer's own id, so the
        // key reads "rightalt" or "default". Those are internal identifiers, not
        // something to print on a key.
        val capIsLayerName = k.text?.lowercase()?.let { it in layerNames } == true
        val width = ((k.width ?: 100f) / 100f).coerceIn(MIN_WIDTH, MAX_WIDTH)

        // A key that holds space rather than typing: both draw as a gap, and
        // KeyAction.None swallows taps on it.
        if (KeymanKeySp.of(k.sp) in HOLLOW_KEYS) {
            return Key(label = "", action = KeyAction.None, width = width)
        }

        // Frame keys are recognised by id before text, because the text is not
        // reliably there: `basic_kbdus` writes its spacebar as `K_SPACE` with an
        // empty cap and `sp: 0`, which by text alone reads as an ordinary key
        // and leaves the converted grid with no spacebar at all.
        //
        // Giving these their own actions rather than routing them through the
        // engine is deliberate. A keyboard whose rules do fire on space or
        // backspace still gets them: the IME seam offers the engine first
        // refusal on both, and only falls back to the ordinary handler when no
        // rule matches. Converting them to engine keys instead would cost
        // long-press repeat on backspace, auto-space and double-space-period.
        FRAME_KEYS[k.id]?.let { frame ->
            return Key(
                label = k.text
                    ?.takeIf { it.isNotBlank() && !it.startsWith("*") && !capIsLayerName }
                    ?: frame.label,
                action = frame.action,
                width = width,
            )
        }

        // A key that switches layers, handled before anything else can lose it.
        //
        // Keyman puts the layer switch on `nextlayer` and lets the id and cap be
        // anything: Khmer Angkor returns from its numeric layer with
        // `id="K_LCONTROL" text="១២ឥ" sp=2 nextlayer="default"`. Treating that as
        // an ordinary key — which is what happens when the id is looked up first
        // and does not resolve — drops the switch and strands the user on a
        // layer with no way back, the exact failure `repair`'s way-back rules
        // exist to prevent.
        //
        // Where the target is one of our own layers the key becomes our own
        // action rather than an engine key, so it keeps working with no rules
        // loaded and so `repair` can see it is a way back.
        val nextLayer = k.nextlayer?.let { layerName(it) }
        if (nextLayer != null) {
            val sp = KeymanKeySp.of(k.sp)
            val isFrame = sp == KeymanKeySp.SPECIAL || sp == KeymanKeySp.SPECIAL_ACTIVE
            val label = k.text?.takeIf { it.isNotBlank() && !it.startsWith("*") && !capIsLayerName }
            if (isFrame) {
                val action = when (nextLayer) {
                    LayoutLayer.LETTERS.key -> KeyAction.Letters
                    LayoutLayer.SYMBOLS.key, LayoutLayer.SYMBOLS_SHIFTED.key -> KeyAction.Symbols
                    else -> KeyAction.KeymanKey(vkey = 0, nextLayer = nextLayer)
                }
                // Never the layer's own name: "rightalt" and "default" are
                // internal identifiers, and printing one on a key puts a word
                // where the user expects a character.
                val fallback = when {
                    action == KeyAction.Letters -> "ABC"
                    action == KeyAction.Symbols -> "?123"
                    else -> "⌨"
                }
                return Key(label = label ?: fallback, action = action, width = width)
            }
        }

        val special = k.text?.let(::specialFor)
        if (special != null) {
            if (special.action == null) {
                report.droppedSpecial++
                return Key(label = "", action = KeyAction.None, width = width)
            }
            return Key(
                label = special.label,
                output = special.output,
                action = special.action,
                width = width,
            )
        }

        val label = if (capIsLayerName) "" else k.text.orEmpty()
        val longPress = k.sk.mapNotNull { subLabel(it) }.filter { it.isNotEmpty() }.distinct()
        val flick = convertFlicks(k, report)
        if (k.multitap.isNotEmpty()) report.droppedMultitaps++

        // A U_ key states its own output and needs no rule, so it converts to an
        // ordinary text key. That keeps it working even with no engine loaded.
        unicodeOutput(k.id)?.let { text ->
            return Key(
                label = label.ifEmpty { text },
                output = text.takeIf { it != label },
                width = width,
                longPress = longPress,
                flick = flick,
            )
        }

        val vkey = VirtualKeys.byName(k.id)
        if (vkey == null) {
            // A T_ key with no code in the binary, or an id we do not know. It
            // has a cap and no way to say what it types, so it types its cap.
            if (label.isEmpty()) {
                report.droppedUnknown++
                return null
            }
            report.unmappedIds++
            return Key(label = label, width = width, longPress = longPress, flick = flick)
        }

        return Key(
            label = label,
            action = KeyAction.KeymanKey(
                vkey = vkey,
                modifiers = modifierMask(k.layer),
                nextLayer = nextLayer,
            ),
            width = width,
            longPress = longPress,
            flick = flick,
        )
    }

    private fun subLabel(k: TouchKey): String? =
        k.text?.takeIf { !SPECIAL_KEYS.containsKey(it) } ?: unicodeOutput(k.id)

    /**
     * Keyman has eight flick directions and [FlickDirection] has four, so the
     * diagonals are dropped and counted rather than rounded to a neighbour —
     * a flick the user aims north-east landing on the north key's output is a
     * worse outcome than the gesture doing nothing.
     */
    private fun convertFlicks(k: TouchKey, report: Report): Map<FlickDirection, String> {
        if (k.flick.isEmpty()) return emptyMap()
        val out = LinkedHashMap<FlickDirection, String>()
        for ((direction, target) in k.flick) {
            val mapped = when (direction.lowercase()) {
                "n" -> FlickDirection.UP
                "s" -> FlickDirection.DOWN
                "e" -> FlickDirection.RIGHT
                "w" -> FlickDirection.LEFT
                else -> null
            }
            if (mapped == null) {
                report.droppedFlicks++
                continue
            }
            val text = subLabel(target) ?: continue
            if (text.isNotEmpty()) out[mapped] = text
        }
        return out
    }

    /** `U_0259` or `U_0041_0301` to the text it emits, or null. */
    private fun unicodeOutput(id: String): String? {
        if (!id.startsWith("U_") && !id.startsWith("u_")) return null
        val parts = id.substring(2).split('_')
        if (parts.isEmpty()) return null
        val sb = StringBuilder()
        for (part in parts) {
            val cp = part.toIntOrNull(16) ?: return null
            if (cp !in 0..0x10FFFF) return null
            sb.appendCodePoint(cp)
        }
        return sb.toString().takeIf { it.isNotEmpty() }
    }

    /** A touch key's `layer` attribute as a Keyman modifier mask. */
    private fun modifierMask(layer: String?): Int = when (layer?.lowercase()) {
        null, "", "default" -> 0
        "shift" -> KmxFormat.K_SHIFTFLAG
        "ctrl" -> KmxFormat.K_CTRLFLAG
        "alt" -> KmxFormat.K_ALTFLAG
        "ctrlshift" -> KmxFormat.K_CTRLFLAG or KmxFormat.K_SHIFTFLAG
        "altshift" -> KmxFormat.K_ALTFLAG or KmxFormat.K_SHIFTFLAG
        "ctrlalt" -> KmxFormat.K_CTRLFLAG or KmxFormat.K_ALTFLAG
        "ctrlaltshift" ->
            KmxFormat.K_CTRLFLAG or KmxFormat.K_ALTFLAG or KmxFormat.K_SHIFTFLAG
        // A `layer` naming an actual layer rather than a modifier combination.
        // It changes which rules match and we have no way to express that, so
        // the key matches unmodified.
        else -> 0
    }

    /**
     * The [Special] a cap names, whether or not the author starred it.
     *
     * The format documents these as `*ZWNJ*`, but a good number of keyboards in
     * the corpus write plain `ZWNJ`, `rlm` or `LRM` instead. Unrecognised, those
     * become the key's visible label — a word sitting on a key where a character
     * belongs, and no zero-width joiner actually typed.
     */
    private fun specialFor(text: String): Special? {
        SPECIAL_KEYS[text]?.let { return it }
        val bare = text.trim('*')
        if (bare.isEmpty()) return null
        return SPECIAL_KEYS["*$bare*"] ?: SPECIAL_BY_BARE_NAME[bare.lowercase()]
    }

    private class Special(
        val label: String,
        val action: KeyAction?,
        /** What the key commits, when that differs from its cap. */
        val output: String? = null,
    )

    private class Frame(val label: String, val action: KeyAction)

    /**
     * Keys whose identity is their id, whatever cap the author gave them. These
     * are the ones `repair` insists a layout has, so getting one wrong does not
     * produce a slightly odd keyboard — it produces one that cannot be enabled.
     */
    private val FRAME_KEYS: Map<String, Frame> = mapOf(
        "K_SPACE" to Frame(" ", KeyAction.Space),
        "K_BKSP" to Frame("⌫", KeyAction.Delete),
        "K_ENTER" to Frame("⏎", KeyAction.Enter),
        "K_SHIFT" to Frame("⇧", KeyAction.Shift),
        "K_CAPS" to Frame("⇪", KeyAction.CapsLock),
        "K_LOPT" to Frame("🌐", KeyAction.LanguageSwitch),
        "K_TAB" to Frame("⇥", KeyAction.SendKey(KEYCODE_TAB)),
    )

    /** `KeyEvent.KEYCODE_TAB`, spelled out so this module needs no Android import. */
    private const val KEYCODE_TAB = 61

    /** Layer ids Keyman defines that a key may also be capped with. */
    private val MODIFIER_LAYER_NAMES = setOf(
        "default", "shift", "caps", "ctrl", "alt", "ctrlshift", "altshift",
        "ctrlalt", "ctrlaltshift", "rightalt", "rightalt-shift", "numeric", "symbol",
    )

    /**
     * The `*Special*` labels, which Keyman renders from a private-use icon font
     * we do not ship. Each maps to our own action and our own cap, so a
     * converted keyboard's shift key looks like every other shift key here.
     *
     * A null action means we have no equivalent; the key becomes a spacer and
     * is counted, rather than being drawn with a label the user cannot act on.
     */
    private val SPECIAL_KEYS: Map<String, Special> = buildMap {
        for (t in listOf("*Shift*", "*Shifted*")) put(t, Special("⇧", KeyAction.Shift))
        for (t in listOf("*ShiftLock*", "*ShiftedLock*", "*Caps*")) {
            put(t, Special("⇪", KeyAction.CapsLock))
        }
        for (t in listOf("*BkSp*", "*LTRBkSp*", "*RTLBkSp*")) put(t, Special("⌫", KeyAction.Delete))
        for (t in listOf("*Enter*", "*LTREnter*", "*RTLEnter*")) put(t, Special("⏎", KeyAction.Enter))
        put("*Menu*", Special("🌐", KeyAction.LanguageSwitch))
        put("*ABC*", Special("ABC", KeyAction.Letters))
        put("*abc*", Special("abc", KeyAction.Letters))
        put("*123*", Special("?123", KeyAction.Symbols))
        put("*Numeral*", Special("?123", KeyAction.Symbols))
        put("*Symbol*", Special("=\\<", KeyAction.Symbols))
        put("*Currency*", Special("$", KeyAction.Symbols))
        // Invisible and width-varying characters. Each commits its own code
        // point, not a plain space: an earlier version routed every one of
        // these to KeyAction.Space, so a no-break space key typed U+0020 and the
        // distinction the key exists for was silently lost.
        //
        // Caps follow the convention the shipped layouts already use for
        // joiners — a dotted circle either side of the mark — rather than
        // spelling the name out, which reads as a word on a key.
        put("*Sp*", Special(" ", KeyAction.Space))
        put("*NBSp*", Special("⍽", KeyAction.Text, output = "\u00A0"))
        put("*NarNBSp*", Special("⍽", KeyAction.Text, output = "\u202F"))
        put("*EnQ*", Special("␣", KeyAction.Text, output = "\u2000"))
        put("*EmQ*", Special("␣", KeyAction.Text, output = "\u2001"))
        put("*EnSp*", Special("␣", KeyAction.Text, output = "\u2002"))
        put("*EmSp*", Special("␣", KeyAction.Text, output = "\u2003"))
        put("*PunctSp*", Special("␣", KeyAction.Text, output = "\u2008"))
        put("*ThSp*", Special("␣", KeyAction.Text, output = "\u2009"))
        put("*HSp*", Special("␣", KeyAction.Text, output = "\u200A"))
        put("*ZWSp*", Special("␣", KeyAction.Text, output = "\u200B"))
        for (t in listOf("*ZWNJ*", "*ZWNJiOS*", "*ZWNJAndroid*", "*ZWNJGeneric*")) {
            put(t, Special("◌│◌", KeyAction.Text, output = "\u200C"))
        }
        put("*ZWJ*", Special("◌‿◌", KeyAction.Text, output = "\u200D"))
        put("*WJ*", Special("◌⁀◌", KeyAction.Text, output = "\u2060"))
        put("*CGJ*", Special("◌⌇◌", KeyAction.Text, output = "\u034F"))
        put("*LTRM*", Special("◌→", KeyAction.Text, output = "\u200E"))
        put("*RTLM*", Special("←◌", KeyAction.Text, output = "\u200F"))
        put("*SH*", Special("◌-◌", KeyAction.Text, output = "\u00AD"))
        put("*HTab*", Special("⇥", KeyAction.SendKey(KEYCODE_TAB)))
        // No equivalent: hiding the keyboard is the system's gesture here, and
        // the desktop modifier caps have no meaning on a touch grid.
        for (t in listOf("*Hide*", "*Tab*", "*TabLeft*", "*AltGr*", "*Alt*", "*Ctrl*",
            "*LAlt*", "*RAlt*", "*LCtrl*", "*RCtrl*")) {
            put(t, Special("", null))
        }
    }

    /**
     * Bare spellings the corpus uses for keys the format wants starred, keyed
     * lowercase. Only the unambiguous ones: a cap of "shift" could plausibly be
     * a word someone meant to show, while "zwnj" could not.
     */
    private val SPECIAL_BY_BARE_NAME: Map<String, Special> by lazy {
        buildMap {
            for ((starred, special) in SPECIAL_KEYS) {
                put(starred.trim('*').lowercase(), special)
            }
            put("lrm", SPECIAL_KEYS.getValue("*LTRM*"))
            put("rlm", SPECIAL_KEYS.getValue("*RTLM*"))
            put("nbsp", SPECIAL_KEYS.getValue("*NBSp*"))
        }
    }

    /** The `sp` values that mean "leave a gap here" rather than "type". */
    private val HOLLOW_KEYS = setOf(KeymanKeySp.SPACER, KeymanKeySp.BLANK)

    private const val DEFAULT_PAD = 5f
    private const val MIN_WIDTH = 0.1f
    private const val MAX_WIDTH = 12f

    private class Report {
        var droppedMultitaps = 0
        var droppedFlicks = 0
        var droppedSpecial = 0
        var droppedUnknown = 0
        var unmappedIds = 0
        var shiftLayerFolded = false
        var shiftLayerKept = false

        fun toReport() = KeymanConversionReport(
            droppedMultitaps = droppedMultitaps,
            droppedDiagonalFlicks = droppedFlicks,
            droppedSpecialKeys = droppedSpecial,
            droppedBlankKeys = droppedUnknown,
            keysWithoutVirtualKey = unmappedIds,
            shiftLayerFolded = shiftLayerFolded,
            shiftLayerKeptSeparate = shiftLayerKept,
        )
    }
}

/** A converted layout and an honest account of what did not survive. */
data class ConvertedKeymanLayout(
    /** [LayoutSpec.langId] is deliberately blank; the caller assigns it. */
    val layout: LayoutSpec,
    val report: KeymanConversionReport,
    /**
     * What the repair pass had to add - typically a way back from a symbols
     * layer, or a spacebar. Worth showing on import: it means the upstream
     * keyboard leaned on Keyman's own frame for something ours expects the
     * layout to carry.
     */
    val repairNotes: List<LayoutMessage> = emptyList(),
)

/**
 * What the conversion could not carry across. Every field is a count rather
 * than a flag so the import screen can say how much, not just whether — a
 * keyboard that lost one diagonal flick and one that lost forty are different
 * situations for the person deciding whether to keep it.
 */
data class KeymanConversionReport(
    /** Repeated-tap cycles. We have no equivalent gesture. */
    val droppedMultitaps: Int = 0,
    /** Flicks aimed at a diagonal, which our four directions cannot express. */
    val droppedDiagonalFlicks: Int = 0,
    /** Frame keys with no counterpart here, e.g. hide-keyboard. */
    val droppedSpecialKeys: Int = 0,
    /** Keys with neither a cap nor an id we could use. */
    val droppedBlankKeys: Int = 0,
    /** Keys that type their cap because their id named no virtual key. */
    val keysWithoutVirtualKey: Int = 0,
    /** The shift layer became per-key shift labels. */
    val shiftLayerFolded: Boolean = false,
    /** The shift layer had a different shape and was kept as its own layer. */
    val shiftLayerKeptSeparate: Boolean = false,
) {
    val isClean: Boolean
        get() = droppedMultitaps == 0 && droppedDiagonalFlicks == 0 &&
            droppedSpecialKeys == 0 && droppedBlankKeys == 0 && keysWithoutVirtualKey == 0
}
