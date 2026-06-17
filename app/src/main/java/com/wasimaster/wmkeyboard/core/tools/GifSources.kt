package com.wasimaster.wmkeyboard.core.tools

/** Where a GIF/sticker result came from. */
enum class GifSource { KLIPY, GIPHY }

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

/** Helpers shared by the multi-provider GIF/sticker pipeline. */
object GifSources {

    fun displayName(source: GifSource): String = when (source) {
        GifSource.KLIPY -> "Klipy"
        GifSource.GIPHY -> "GIPHY"
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
