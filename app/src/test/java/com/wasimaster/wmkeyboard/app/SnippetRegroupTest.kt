package com.wasimaster.wmkeyboard.app

import com.wasimaster.wmkeyboard.core.snippets.Snippet
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The splice behind reordering one group of snippets when the store keeps a
 * single order for all of them: see [regrouped].
 */
class SnippetRegroupTest {

    private fun snippet(id: Long, folderId: Long = 0) =
        Snippet(id = id, label = "s$id", text = "t$id", folderId = folderId)

    /** 1 and 3 are in the folder; 2 and 4 are not. */
    private val all = listOf(
        snippet(1, folderId = 7),
        snippet(2),
        snippet(3, folderId = 7),
        snippet(4),
    )

    @Test
    fun `a folder's snippets swap without moving anything else`() {
        val folder = all.filter { it.folderId == 7L }
        val swapped = listOf(folder[1], folder[0])
        // The folder's two positions are 0 and 2, and they are the only ones
        // that change: 2 and 4 stay exactly where they were.
        assertEquals(listOf(3L, 2L, 1L, 4L), regrouped(all, swapped))
    }

    @Test
    fun `reordering the loose snippets leaves the folder alone`() {
        val loose = all.filter { it.folderId == 0L }
        assertEquals(listOf(1L, 4L, 3L, 2L), regrouped(all, listOf(loose[1], loose[0])))
    }

    @Test
    fun `an unchanged order is the order it was`() {
        assertEquals(all.map { it.id }, regrouped(all, all.filter { it.folderId == 7L }))
    }

    @Test
    fun `a shorter group leaves every other snippet where it was`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), regrouped(all, listOf(all[2])))
    }

    @Test
    fun `a snippet deleted mid-drag is dropped rather than written twice`() {
        // The drag was over snippets 1 and 3; 1 went while the finger was down,
        // so the order coming back names something the store no longer has.
        // Every id has to appear exactly once whatever happens, or the store's
        // reorder duplicates one row and loses another.
        val gone = Snippet(id = 9, label = "gone", text = "gone", folderId = 7)
        val result = regrouped(all, listOf(gone, all[0]))
        assertEquals(listOf(1L, 2L, 3L, 4L), result)
        assertEquals(result.size, result.distinct().size)
    }
}
