package com.wasimaster.wmkeyboard.core.tools

/** Where a GIF/sticker result came from. */
enum class GifSource { TENOR, GIPHY, GOOGLE }

/** Helpers shared by the multi-provider GIF/sticker pipeline. */
object GifSources {

    fun displayName(source: GifSource): String = when (source) {
        GifSource.TENOR -> "Tenor"
        GifSource.GIPHY -> "GIPHY"
        GifSource.GOOGLE -> "Google"
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
