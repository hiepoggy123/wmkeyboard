package com.wasimaster.wmkeyboard.core.layout

/**
 * Checking a layout comes in two flavours, and keeping them apart is what makes
 * the editor usable.
 *
 * [LayoutSpec.repair] **rewrites** a layout so it cannot brick the keyboard, and
 * runs at the two moments where that matters: importing a file someone else
 * wrote, and turning a layout on. [validateLayout] only **reports**, and runs
 * continuously while editing.
 *
 * They must not be the same function. The editor auto-saves on every keystroke,
 * so a repair on the save path would append a delete key back the instant you
 * removed a row, and the undo stack would record the repaired layout rather than
 * the one you were building — an editor that fights you. Deferring the repair to
 * activation keeps the guarantee that matters (you can never *type on* a layout
 * you cannot backspace in) without taking the editor away.
 *
 * Both read the same rules below, so the warning the editor shows and the fix the
 * import applies can never drift apart.
 */

/** How much a finding matters. */
enum class LayoutSeverity {
    /** Worth telling the user about; the layout is still usable. */
    WARNING,

    /** The layout cannot be turned on until this is resolved. */
    BLOCKING,
}

/** One thing wrong with a layout, in words the user can act on. */
data class LayoutFinding(
    val severity: LayoutSeverity,
    val message: String,
    /** The layer it was found in, or null when it is about the layout as a whole. */
    val layer: LayoutLayer? = null,
)

/**
 * A layout after the repair pass, with a plain-English line per fix so the import
 * sheet can say what it changed rather than silently rewriting the user's file.
 * Repairing rather than rejecting is deliberate: the raw-JSON editor means
 * hand-written layouts, and a file that is ninety-five percent right should cost
 * its author a note, not a retype.
 */
data class RepairedLayout(val spec: LayoutSpec, val repairs: List<String>)

/** Widest a single key may be, in grid units — wider than the whole 10-column grid. */
private const val MaxKeyWidth = 12f

/** Widest a row may total before every key in it is scaled down to fit. */
private const val MaxRowWidth = 40f

/** Below roughly 2 mm per key on a phone, a row stops being tappable. */
private const val MaxKeysPerRow = 24

/** Taller than any phone shows without eating the whole screen. */
private const val MaxRowsPerLayer = 8

/**
 * Everything wrong with this layout, worst first. Pure — it never rewrites the
 * layout, so the editor can call it on every keystroke and show the result
 * without the layout changing under the user.
 *
 * Only [LayoutSeverity.BLOCKING] findings stop a layout being enabled. A missing
 * shift key is deliberately not one of them: a symbols-forward grid may
 * legitimately not want one, and it stays recoverable because long-press still
 * reaches the alternates.
 */
fun validateLayout(spec: LayoutSpec): List<LayoutFinding> {
    val findings = mutableListOf<LayoutFinding>()

    val letters = spec.layer(LayoutLayer.LETTERS)
    if (letters == null || letters.rows.none { it.isNotEmpty() }) {
        findings += LayoutFinding(
            LayoutSeverity.BLOCKING,
            "This layout has no letters layer.",
            LayoutLayer.LETTERS,
        )
    } else {
        val keys = letters.rows.flatten()
        if (keys.none { it.action == KeyAction.Delete }) {
            findings += LayoutFinding(
                LayoutSeverity.BLOCKING,
                "No delete key — you would not be able to correct a mistake.",
                LayoutLayer.LETTERS,
            )
        }
        if (keys.none { it.action == KeyAction.Enter }) {
            findings += LayoutFinding(
                LayoutSeverity.BLOCKING,
                "No enter key.",
                LayoutLayer.LETTERS,
            )
        }
        if (keys.none { it.action == KeyAction.Space }) {
            findings += LayoutFinding(
                LayoutSeverity.BLOCKING,
                "No space key.",
                LayoutLayer.LETTERS,
            )
        }
        if (keys.none { it.action == KeyAction.Shift }) {
            findings += LayoutFinding(
                LayoutSeverity.WARNING,
                "No shift key. Capitals will only be reachable through long-press.",
                LayoutLayer.LETTERS,
            )
        }
    }

    for (layer in LayoutLayer.entries) {
        val rows = spec.layer(layer)?.rows ?: continue
        val label = layer.key

        if (rows.size > MaxRowsPerLayer) {
            findings += LayoutFinding(
                LayoutSeverity.BLOCKING,
                "The $label layer has ${rows.size} rows; $MaxRowsPerLayer is the most that fits.",
                layer,
            )
        }
        if (layer != LayoutLayer.LETTERS && layer.isCycled && rows.isNotEmpty() &&
            rows.flatten().none { it.action == KeyAction.Letters || it.action == KeyAction.Symbols }
        ) {
            findings += LayoutFinding(
                LayoutSeverity.BLOCKING,
                "The $label layer has no way back to the letters — you would be stranded on it.",
                layer,
            )
        }

        val gridWeight = gridWeightOf(rows)
        rows.forEachIndexed { index, row ->
            val number = index + 1
            if (row.isEmpty()) {
                findings += LayoutFinding(
                    LayoutSeverity.WARNING,
                    "Row $number of the $label layer is empty.",
                    layer,
                )
                return@forEachIndexed
            }
            if (row.size > MaxKeysPerRow) {
                findings += LayoutFinding(
                    LayoutSeverity.BLOCKING,
                    "Row $number of the $label layer has ${row.size} keys; " +
                        "$MaxKeysPerRow is the most that stays tappable.",
                    layer,
                )
            }
            if (row.any { !it.width.isFinite() || it.width <= 0f }) {
                findings += LayoutFinding(
                    LayoutSeverity.BLOCKING,
                    "Row $number of the $label layer has a key with no width.",
                    layer,
                )
            }
            val total = row.sumOf { it.width.toDouble() }.toFloat()
            if (total > gridWeight + 0.01f) {
                findings += LayoutFinding(
                    LayoutSeverity.WARNING,
                    "Row $number of the $label layer is wider than the first row, " +
                        "so its keys will be drawn narrower.",
                    layer,
                )
            }
        }

        val unknown = rows.flatten().count { it.action is KeyAction.Unknown }
        if (unknown > 0) {
            findings += LayoutFinding(
                LayoutSeverity.BLOCKING,
                "$unknown key${if (unknown == 1) "" else "s"} in the $label layer " +
                    "use an action this version does not know.",
                layer,
            )
        }
    }

    return findings.sortedByDescending { it.severity == LayoutSeverity.BLOCKING }
}

/** Whether this layout is safe to turn on. */
fun LayoutSpec.canBeEnabled(): Boolean =
    validateLayout(this).none { it.severity == LayoutSeverity.BLOCKING }

/**
 * Rewrites this layout into one that cannot leave the user unable to type, and
 * says what it changed.
 *
 * Run on import and on activation, never on an ordinary save — see the note at
 * the top of this file for why. The delete/enter/space rules are the ones that
 * earn the whole design: they are the difference between "I imported a layout
 * and now I cannot type well enough to uninstall it" and "I imported a layout,
 * it told me it added a backspace key, fine."
 */
fun LayoutSpec.repair(): RepairedLayout {
    val repairs = mutableListOf<String>()

    val layers = layers.mapNotNull { (key, layerSpec) ->
        val layer = LayoutLayer.entries.firstOrNull { it.key == key }
        val label = layer?.key ?: key

        var rows = layerSpec.rows.map { row -> row.mapNotNull { it.repairKey(label, repairs) } }
            .filter { it.isNotEmpty() }

        if (rows.size > MaxRowsPerLayer) {
            repairs += "The $label layer had ${rows.size} rows; kept the first $MaxRowsPerLayer."
            rows = rows.take(MaxRowsPerLayer)
        }
        rows = rows.map { row -> row.repairRow(label, repairs) }

        // An empty layer is dropped rather than kept: a layout inherits the
        // shipped grid for anything it does not define, so dropping it restores
        // a working layer instead of leaving a blank one.
        if (rows.isEmpty()) {
            repairs += "The $label layer had no usable keys; it now uses the built-in grid."
            return@mapNotNull null
        }
        // Per-row heights are positional, so they are only still valid if repair
        // left the row count untouched (it never reorders, only drops/adds). Drop
        // them otherwise rather than let them land on the wrong rows.
        val heights = layerSpec.rowHeights?.takeIf { rows.size == layerSpec.rows.size }
        key to layerSpec.copy(rows = rows, rowHeights = heights)
    }.toMap().toMutableMap()

    val lettersKey = LayoutLayer.LETTERS.key
    layers[lettersKey]?.let { letters ->
        var rows = letters.rows
        rows = rows.ensuring(KeyAction.Delete, Key("⌫", action = KeyAction.Delete, width = 1.5f)) {
            repairs += "The letters layer had no delete key; added one."
        }
        rows = rows.ensuring(KeyAction.Space, Key(" ", action = KeyAction.Space, width = 4f)) {
            repairs += "The letters layer had no space key; added one."
        }
        rows = rows.ensuring(KeyAction.Enter, Key("⏎", action = KeyAction.Enter, width = 1.5f)) {
            repairs += "The letters layer had no enter key; added one."
        }
        // Same positional guard: a missing space/enter/delete key adds a row,
        // which would shift every per-row height down one.
        val heights = letters.rowHeights?.takeIf { rows.size == letters.rows.size }
        layers[lettersKey] = letters.copy(rows = rows, rowHeights = heights)
    }

    for (layer in LayoutLayer.entries) {
        if (layer == LayoutLayer.LETTERS || !layer.isCycled) continue
        val spec = layers[layer.key] ?: continue
        val hasWayBack = spec.rows.flatten()
            .any { it.action == KeyAction.Letters || it.action == KeyAction.Symbols }
        if (!hasWayBack) {
            repairs += "The ${layer.key} layer had no way back to the letters; added an ABC key."
            layers[layer.key] = spec.copy(
                rows = spec.rows.appendToLastRow(
                    Key("ABC", action = KeyAction.Letters, width = 1.5f),
                ),
            )
        }
    }

    return RepairedLayout(copy(layers = layers), repairs)
}

/**
 * Fixes what can be fixed about one key, or drops it. Returning null rather than
 * a placeholder lets the row re-flow around the gap, which is less confusing
 * than a button that is visibly there and silently does nothing.
 */
private fun Key.repairKey(label: String, repairs: MutableList<String>): Key? {
    if (action is KeyAction.Unknown) {
        repairs += "Dropped a key in the $label layer using the unknown action " +
            "\"${(action as KeyAction.Unknown).tag}\"."
        return null
    }
    if (action == KeyAction.Text && this.label.isEmpty() && output.isNullOrEmpty()) {
        repairs += "Dropped a blank key in the $label layer."
        return null
    }

    var fixed = this
    if (!width.isFinite() || width <= 0f) {
        repairs += "A key in the $label layer had no width; set it to 1."
        fixed = fixed.copy(width = 1f)
    } else if (width > MaxKeyWidth) {
        repairs += "A key in the $label layer was ${width} wide; capped it at $MaxKeyWidth."
        fixed = fixed.copy(width = MaxKeyWidth)
    }
    if (fixed.longPress.any { it.isEmpty() }) {
        fixed = fixed.copy(longPress = fixed.longPress.filter { it.isNotEmpty() })
    }
    return fixed
}

/** Truncates an over-long row and scales an over-wide one back into the grid. */
private fun List<Key>.repairRow(label: String, repairs: MutableList<String>): List<Key> {
    var row = this
    if (row.size > MaxKeysPerRow) {
        repairs += "A row in the $label layer had ${row.size} keys; kept the first $MaxKeysPerRow."
        row = row.take(MaxKeysPerRow)
    }
    val total = row.sumOf { it.width.toDouble() }.toFloat()
    if (total > MaxRowWidth) {
        val scale = MaxRowWidth / total
        repairs += "A row in the $label layer totalled $total units; scaled it to fit."
        row = row.map { it.copy(width = it.width * scale) }
    }
    return row
}

/** Appends [fallback] to the last row unless [action] is already somewhere in the grid. */
private inline fun List<List<Key>>.ensuring(
    action: KeyAction,
    fallback: Key,
    onAdded: () -> Unit,
): List<List<Key>> {
    if (flatten().any { it.action == action }) return this
    onAdded()
    return appendToLastRow(fallback)
}

private fun List<List<Key>>.appendToLastRow(key: Key): List<List<Key>> =
    if (isEmpty()) listOf(listOf(key)) else dropLast(1) + listOf(last() + key)
