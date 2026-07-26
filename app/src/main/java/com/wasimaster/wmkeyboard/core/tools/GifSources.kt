package com.wasimaster.wmkeyboard.core.tools

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
)

/** One chip in the source row, with the label it shows. */
data class SourceChip(val label: String, val source: GifSource)

/** Helpers shared by the multi-provider GIF/sticker pipeline. */
object GifSources {

    fun displayName(source: GifSource): String = when (source) {
        GifSource.KLIPY -> "Klipy"
        GifSource.GIPHY -> "GIPHY"
        GifSource.LOCAL -> "My stickers"
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
            return if (sources.size > 1) sources.map { SourceChip(displayName(it), it) } else emptyList()
        }
        val remote = sources.filter { it != GifSource.LOCAL }
        if (remote.isEmpty() || GifSource.LOCAL !in sources) return emptyList()
        return listOf(
            SourceChip("Online", remote.first()),
            SourceChip(displayName(GifSource.LOCAL), GifSource.LOCAL),
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
