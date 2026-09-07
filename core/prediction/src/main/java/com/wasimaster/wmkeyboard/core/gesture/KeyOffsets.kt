package com.wasimaster.wmkeyboard.core.gesture

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Where this user's finger lands relative to where each key is drawn, learned
 * from the glides they keep and handed back to the decoder as a shifted grid
 * (issue #52).
 *
 * The decoder scores a stroke against key centres, and the noise sweep says
 * that is its steepest axis by a distance: a stroke drawn systematically off
 * or short of the keys costs more accuracy than any amount of corner cutting.
 * Real hands are systematic. A right thumb reaches the far side of the board
 * short and lands low on the near side, and it does so on every stroke, which
 * is exactly what a per-key mean can learn and exactly what per-stroke noise
 * cannot. So each committed glide is laid back over the grid it was decoded
 * on, the place the stroke passed for each letter is measured against that
 * letter's key, and the running mean of those misses is where the key is put
 * for the next decode.
 *
 * **Keyed by position, not by letter.** The miss is a property of where the
 * key sits under the hand, not of what is printed on it: the same thumb cuts
 * the top-right corner the same way on QWERTY, AZERTY and Probhat. Cells are
 * half a key width square, which separates every key on every grid and lets
 * two layouts with the same geometry share what either one learned.
 *
 * **Shrunk toward the whole hand.** A cell with one observation is noise; a
 * board with forty is a hand. Each cell's mean is pulled toward the global
 * mean by a prior count, so a key the user has barely swiped through still
 * moves with the rest of the board, and a key they swipe through constantly
 * gets its own reading. Both means are capped in memory so a hand that
 * changes — a new phone, a case, a broken thumb — is followed rather than
 * averaged against for the rest of time.
 *
 * **A cell has to disagree with the hand by a margin to be heard.** Strokes
 * have geometry of their own that is not the hand: a finger cuts inside
 * every corner and starts each word a little late, and both leave a small
 * per-key mean that depends on which way the words through that key turn.
 * Those quirks point different ways on different keys and cancel in the
 * whole-hand mean, but they survive in the cells, and following them cost
 * an unbiased hand real accuracy on the harness. So a cell's residual from
 * the global mean is soft-thresholded at [DEAD_ZONE]: the first 0.08 key
 * widths of it are treated as the stroke's, not the hand's.
 *
 * **Undoable.** A glide the user backspaces was a wrong reading of the
 * stroke, and a wrong word aligned to a stroke teaches wrong offsets. So
 * [observe] hands back exactly what it changed and [retract] puts it back —
 * for the *last* observation only, which is the only one an undo can reach.
 *
 * Persisted as JSON in the learning directory beside the lexicon; in memory
 * only until the device is unlocked, like every store there.
 */
class KeyOffsets(private val storageFile: File?) {

    /**
     * One letter of one kept glide: where its key is drawn and where the
     * stroke actually passed for it, both in key widths.
     */
    class Observation(val keyX: Float, val keyY: Float, val seenX: Float, val seenY: Float)

    /** What one [observe] changed, so an undo can put it back exactly. */
    class Adjustment internal constructor(
        internal val cells: List<Pair<Long, Cell>>,
        internal val global: Cell,
    )

    /** A running mean, remembered [n] observations deep. */
    internal class Cell(var dx: Float, var dy: Float, var n: Int) {
        fun copy() = Cell(dx, dy, n)
    }

    @Serializable
    private data class StoredCell(val x: Int, val y: Int, val dx: Float, val dy: Float, val n: Int)

    @Serializable
    private data class Snapshot(
        val cells: List<StoredCell> = emptyList(),
        val gx: Float = 0f,
        val gy: Float = 0f,
        val gn: Int = 0,
    )

    private val cells = HashMap<Long, Cell>()
    private var global = Cell(0f, 0f, 0)
    private val json = Json { ignoreUnknownKeys = true }
    private var dirty = false

    /**
     * Bumped on every change. The decoder's grid is built from this model and
     * cached; the version is what tells the cache the grid has moved.
     */
    @Volatile
    var version: Int = 0
        private set

    init {
        load()
    }

    /** True until the first observation: a grid shifted by nothing is the grid. */
    @Synchronized
    fun isEmpty(): Boolean = global.n == 0

    /** How many glides have been learned from, capped at the global memory. */
    @Synchronized
    fun observations(): Int = global.n

    /**
     * The shift to apply at position ([x], [y]) in key widths, written into
     * [out] as dx, dy. The cell's own mean where it has one, pulled toward the
     * whole hand's by [CELL_PRIOR] observations' worth, and the whole hand's
     * mean scaled down while it is itself still young; never longer than
     * [MAX_SHIFT], whatever the data says.
     */
    @Synchronized
    fun offsetAt(x: Float, y: Float, out: FloatArray) {
        val trust = global.n / (global.n + GLOBAL_PRIOR).toFloat()
        var ox = global.dx * trust
        var oy = global.dy * trust
        val cell = cells[cellOf(x, y)]
        if (cell != null && cell.n > 0) {
            val w = cell.n / (cell.n + CELL_PRIOR).toFloat()
            var rx = (cell.dx - ox) * w
            var ry = (cell.dy - oy) * w
            val residual = sqrt(rx * rx + ry * ry)
            if (residual > DEAD_ZONE) {
                val keep = (residual - DEAD_ZONE) / residual
                rx *= keep
                ry *= keep
            } else {
                rx = 0f
                ry = 0f
            }
            ox += rx
            oy += ry
        }
        val length = sqrt(ox * ox + oy * oy)
        if (length > MAX_SHIFT) {
            ox *= MAX_SHIFT / length
            oy *= MAX_SHIFT / length
        }
        out[0] = ox
        out[1] = oy
    }

    /**
     * [keys] moved to where this hand lands on them, in the same pixel space,
     * for a keyboard whose keys are [keyWidth] wide. The list itself when
     * there is nothing learned yet. Characters sharing a key keep sharing it:
     * the shift is a function of the drawn position alone.
     */
    fun shifted(keys: List<KeyCenter>, keyWidth: Float): List<KeyCenter> {
        if (isEmpty() || keyWidth <= 0f) return keys
        val out = FloatArray(2)
        return keys.map { key ->
            offsetAt(key.x / keyWidth, key.y / keyWidth, out)
            KeyCenter(key.codePoint, key.x + out[0] * keyWidth, key.y + out[1] * keyWidth)
        }
    }

    /**
     * Learns from one kept glide. An observation further than [MAX_OBSERVED]
     * from its key is a misalignment, not a hand, and is skipped. Returns
     * what changed, or null when nothing usable was offered.
     */
    @Synchronized
    fun observe(observations: List<Observation>): Adjustment? {
        val usable = observations.filter {
            abs(it.seenX - it.keyX) <= MAX_OBSERVED && abs(it.seenY - it.keyY) <= MAX_OBSERVED
        }
        if (usable.isEmpty()) return null
        val before = LinkedHashMap<Long, Cell>()
        val globalBefore = global.copy()
        for (o in usable) {
            val id = cellOf(o.keyX, o.keyY)
            val cell = cells.getOrPut(id) { Cell(0f, 0f, 0) }
            if (id !in before) before[id] = cell.copy()
            val dx = o.seenX - o.keyX
            val dy = o.seenY - o.keyY
            update(cell, dx, dy, CELL_MEMORY)
            update(global, dx, dy, GLOBAL_MEMORY)
        }
        trim()
        dirty = true
        version++
        return Adjustment(before.entries.map { it.key to it.value }, globalBefore)
    }

    /**
     * Puts back what [adjustment] changed. Only sound for the most recent
     * [observe]: a later one has already built on the state this restores.
     */
    @Synchronized
    fun retract(adjustment: Adjustment) {
        for ((id, previous) in adjustment.cells) {
            if (previous.n == 0) cells.remove(id) else cells[id] = previous.copy()
        }
        global = adjustment.global.copy()
        dirty = true
        version++
    }

    @Synchronized
    fun save() {
        val file = storageFile ?: return
        if (!dirty) return
        val stored = cells.map { (id, cell) ->
            StoredCell(xOf(id), yOf(id), cell.dx, cell.dy, cell.n)
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Snapshot(stored, global.dx, global.dy, global.n)))
        }.onSuccess { dirty = false }
    }

    /** Re-reads the file after the settings app deleted or replaced it. */
    @Synchronized
    fun reload() {
        cells.clear()
        global = Cell(0f, 0f, 0)
        load()
        dirty = false
        version++
    }

    @Synchronized
    fun clear() {
        cells.clear()
        global = Cell(0f, 0f, 0)
        version++
        // The delete is the write; stay dirty only if it failed, so the next
        // save overwrites the stale file with the empty snapshot.
        dirty = storageFile?.delete() == false
    }

    private fun load() {
        val file = storageFile ?: return
        if (!file.exists()) return
        runCatching {
            val snapshot = json.decodeFromString<Snapshot>(file.readText())
            for (c in snapshot.cells) {
                if (c.n <= 0 || !c.dx.isFinite() || !c.dy.isFinite()) continue
                cells[pack(c.x, c.y)] = Cell(c.dx, c.dy, c.n.coerceAtMost(CELL_MEMORY))
            }
            if (snapshot.gn > 0 && snapshot.gx.isFinite() && snapshot.gy.isFinite()) {
                global = Cell(snapshot.gx, snapshot.gy, snapshot.gn.coerceAtMost(GLOBAL_MEMORY))
            }
        }
    }

    /** A capped running mean: the newest observation is worth `1/n`, never less than `1/memory`. */
    private fun update(cell: Cell, dx: Float, dy: Float, memory: Int) {
        val n = minOf(cell.n + 1, memory)
        cell.dx += (dx - cell.dx) / n
        cell.dy += (dy - cell.dy) / n
        cell.n = n
    }

    /** Past [MAX_CELLS] the least-observed cell makes room. Grids are small; this is a backstop. */
    private fun trim() {
        while (cells.size > MAX_CELLS) {
            val weakest = cells.entries.minByOrNull { it.value.n } ?: return
            cells.remove(weakest.key)
        }
    }

    private fun cellOf(x: Float, y: Float): Long =
        pack((x * CELLS_PER_KEY).roundToInt(), (y * CELLS_PER_KEY).roundToInt())

    private fun pack(qx: Int, qy: Int): Long = (qx.toLong() shl 32) or (qy.toLong() and 0xffffffffL)

    private fun xOf(id: Long): Int = (id shr 32).toInt()

    private fun yOf(id: Long): Int = id.toInt()

    companion object {
        /** Cells per key width along each axis: half a key, so no two keys share one. */
        private const val CELLS_PER_KEY = 2

        /** A letter seen further than this from its key, in key widths, is a bad alignment, not a hand. */
        const val MAX_OBSERVED = 0.9f

        /** The furthest a key is ever moved, in key widths, whatever was observed. */
        const val MAX_SHIFT = 0.45f

        /** Observations a cell's mean remembers; older ones fade so a changed hand is followed. */
        const val CELL_MEMORY = 40

        /** Observations the whole hand's mean remembers. */
        const val GLOBAL_MEMORY = 200

        /** Pseudo-observations of the whole hand a cell has to outweigh to speak for itself. */
        const val CELL_PRIOR = 10

        /** How far a cell's mean must leave the whole hand's before the difference counts, in key widths. */
        const val DEAD_ZONE = 0.08f

        /** Observations before the whole hand's mean is applied at full strength. */
        const val GLOBAL_PRIOR = 8

        private const val MAX_CELLS = 512
    }
}
