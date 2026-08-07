package com.wasimaster.wmkeyboard.core.tools

import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.tools.R

/**
 * Where a GIF/sticker result came from. [LOCAL] is the user's own sticker
 * packs on device — sticker-only, always available, and never mixed into a
 * provider grid.
 */
enum class GifSource { KLIPY, GIPHY, LOCAL }

/**
 * One GIF or sticker result: a small preview for the panel grid and the
 * full-size file that actually gets committed to the editor. Produced by
 * [KlipyClient] and [GiphyClient].
 */
data class GifItem(
    val id: String,
    /** Compact preview shown in the picker grid (animated for KLIPY/GIPHY). */
    val previewUrl: String,
    /** Full-size file downloaded and inserted on tap. */
    val fullUrl: String,
    /** MIME of [fullUrl] — image/gif, image/webp or image/png. */
    val mime: String,
    /** width/height of the preview, for grid cell sizing. */
    val aspectRatio: Float,
    val source: GifSource,
    /** Provider's name for the item, or a local sticker's — may be blank. */
    val title: String = "",
)

/** One chip in the source row, with the label it shows. */
data class SourceChip(@StringRes val labelRes: Int, val source: GifSource)

/** Helpers shared by the multi-provider GIF/sticker pipeline. */
object GifSources {

    /** The name a source goes by on screen. The UI resolves the id. */
    @StringRes
    fun displayNameRes(source: GifSource): Int = when (source) {
        GifSource.KLIPY -> R.string.core_tools_gif_source_klipy
        GifSource.GIPHY -> R.string.core_tools_gif_source_giphy
        GifSource.LOCAL -> R.string.core_tools_gif_source_local
    }

    /**
     * Which sources one fetch should hit, given the chip the user is on.
     *
     * Local packs are never interleaved with providers: picking them is
     * always a grid of its own, in mixed mode as much as in tabs mode.
     */
    fun targets(sources: List<GifSource>, selected: GifSource, tabs: Boolean): List<GifSource> {
        if (sources.isEmpty()) return emptyList()
        val pick = selected.takeIf { it in sources }
        if (pick == GifSource.LOCAL) return listOf(GifSource.LOCAL)
        if (tabs) return listOf(pick ?: sources.first())
        val remote = sources.filter { it != GifSource.LOCAL }
        return remote.ifEmpty { sources }
    }

    /**
     * Chips for the source row, or an empty list when there is nothing to
     * switch between. Mixed mode collapses the providers into one "Online"
     * chip standing for the interleaved grid, so local packs still get a tab.
     */
    fun chips(sources: List<GifSource>, tabs: Boolean): List<SourceChip> {
        if (tabs) {
            return if (sources.size > 1) sources.map { SourceChip(displayNameRes(it), it) } else emptyList()
        }
        val remote = sources.filter { it != GifSource.LOCAL }
        if (remote.isEmpty() || GifSource.LOCAL !in sources) return emptyList()
        return listOf(
            SourceChip(R.string.core_tools_gif_source_online, remote.first()),
            SourceChip(displayNameRes(GifSource.LOCAL), GifSource.LOCAL),
        )
    }

    /** Index of the active chip in [chips], falling back to the first. */
    fun selectedChip(chips: List<SourceChip>, selected: GifSource): Int {
        val exact = chips.indexOfFirst { it.source == selected }
        if (exact >= 0) return exact
        // Mixed mode: any provider means the "Online" chip is the active one.
        return chips.indexOfFirst { it.source != GifSource.LOCAL }.coerceAtLeast(0)
    }

    /**
     * A cell's shape in the picker grid. Clamped so one degenerate banner or
     * filmstrip cannot flatten its whole row (or stretch it off the panel);
     * the preview letterboxes inside the clamped cell instead of cropping.
     */
    fun cellRatio(item: GifItem): Float = item.aspectRatio.coerceIn(MIN_CELL_RATIO, MAX_CELL_RATIO)

    /**
     * Splits picker results into justified rows: every item in a row shares
     * one height, widths follow each item's own aspect ratio, and nothing is
     * cropped. Rows aim for three items and close early once they carry
     * [TARGET_ROW_RATIO] worth of width, so a pair of wide GIFs makes a row
     * of two rather than squeezing a cropped third in.
     *
     * A row never opens with an item that would push it past [MAX_ROW_RATIO]:
     * heights are `width / ratio-sum`, and an unbounded sum is a row of
     * thumbnails too short to read.
     */
    fun rows(items: List<GifItem>): List<List<GifItem>> {
        val rows = ArrayList<List<GifItem>>()
        var row = ArrayList<GifItem>()
        var sum = 0f
        for (item in items) {
            val ratio = cellRatio(item)
            if (row.isNotEmpty() && (row.size == MAX_ROW_ITEMS || sum + ratio > MAX_ROW_RATIO)) {
                rows += row
                row = ArrayList()
                sum = 0f
            }
            row += item
            sum += ratio
            if (sum >= TARGET_ROW_RATIO) {
                rows += row
                row = ArrayList()
                sum = 0f
            }
        }
        if (row.isNotEmpty()) rows += row
        return rows
    }

    /** How much ratio-width a row wants; also the floor rows are padded to. */
    const val TARGET_ROW_RATIO = 3.2f
    private const val MAX_ROW_RATIO = 4.6f
    private const val MAX_ROW_ITEMS = 3
    private const val MIN_CELL_RATIO = 0.5f
    private const val MAX_CELL_RATIO = 2.6f

    /**
     * Round-robin merge for "mixed" mode: one item from each provider in
     * turn, so no source dominates the top of the grid.
     */
    fun interleave(lists: List<List<GifItem>>): List<GifItem> {
        val iterators = lists.map { it.iterator() }
        val merged = ArrayList<GifItem>(lists.sumOf { it.size })
        while (iterators.any { it.hasNext() }) {
            for (iterator in iterators) {
                if (iterator.hasNext()) merged += iterator.next()
            }
        }
        return merged
    }
}
