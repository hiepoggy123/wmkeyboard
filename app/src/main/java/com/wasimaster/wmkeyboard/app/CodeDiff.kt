package com.wasimaster.wmkeyboard.app

import androidx.compose.runtime.Immutable

internal enum class DiffKind { SAME, ADDED, REMOVED }

@Immutable
internal data class DiffLine(val kind: DiffKind, val text: String)

/** A row of a diff as it is shown: a line, or a run of unchanged lines folded into a count. */
@Immutable
internal sealed interface DiffRow {
    data class Line(val line: DiffLine) : DiffRow

    data class Unchanged(val count: Int) : DiffRow
}

/** Past this many line pairs the middle is shown as all removed, then all added, rather than matched. */
private const val MAX_DIFF_CELLS = 4_000_000L

/**
 * The lines that turn [old] into [new], in order. Lines both share at the start
 * and the end are matched without any work, which is almost all of a file
 * between two versions; only the stretch between is matched line by line.
 */
internal fun lineDiff(old: String, new: String): List<DiffLine> {
    val a = old.split('\n')
    val b = new.split('\n')
    var start = 0
    while (start < a.size && start < b.size && a[start] == b[start]) start++
    var endA = a.size
    var endB = b.size
    while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) {
        endA--
        endB--
    }
    val out = ArrayList<DiffLine>(maxOf(a.size, b.size))
    for (index in 0 until start) out += DiffLine(DiffKind.SAME, a[index])
    val n = endA - start
    val m = endB - start
    if (n.toLong() * m > MAX_DIFF_CELLS) {
        for (index in start until endA) out += DiffLine(DiffKind.REMOVED, a[index])
        for (index in start until endB) out += DiffLine(DiffKind.ADDED, b[index])
    } else {
        // common[i][j] is how many lines the rest of a, from i, and the rest of b, from j, share in order.
        val common = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                common[i][j] = if (a[start + i] == b[start + j]) common[i + 1][j + 1] + 1 else maxOf(common[i + 1][j], common[i][j + 1])
            }
        }
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[start + i] == b[start + j] -> {
                    out += DiffLine(DiffKind.SAME, a[start + i])
                    i++
                    j++
                }
                common[i + 1][j] >= common[i][j + 1] -> out += DiffLine(DiffKind.REMOVED, a[start + i++])
                else -> out += DiffLine(DiffKind.ADDED, b[start + j++])
            }
        }
        while (i < n) out += DiffLine(DiffKind.REMOVED, a[start + i++])
        while (j < m) out += DiffLine(DiffKind.ADDED, b[start + j++])
    }
    for (index in endA until a.size) out += DiffLine(DiffKind.SAME, a[index])
    return out
}

/** [diff] with every run of unchanged lines more than [context] away from a change folded into a count. */
internal fun collapseDiff(diff: List<DiffLine>, context: Int = 3): List<DiffRow> {
    val near = BooleanArray(diff.size)
    for ((index, line) in diff.withIndex()) {
        if (line.kind == DiffKind.SAME) continue
        for (k in maxOf(0, index - context)..minOf(diff.lastIndex, index + context)) near[k] = true
    }
    val rows = ArrayList<DiffRow>()
    var folded = 0
    for ((index, line) in diff.withIndex()) {
        if (line.kind == DiffKind.SAME && !near[index]) {
            folded++
            continue
        }
        if (folded > 0) rows += DiffRow.Unchanged(folded)
        folded = 0
        rows += DiffRow.Line(line)
    }
    if (folded > 0) rows += DiffRow.Unchanged(folded)
    return rows
}
