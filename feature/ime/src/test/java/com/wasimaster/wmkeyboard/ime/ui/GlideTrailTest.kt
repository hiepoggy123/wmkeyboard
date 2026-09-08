package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trail's points live in plain arrays that Compose cannot observe, and its
 * whole reason for existing is that nothing reads them at composition scope.
 * That makes the two things most likely to break here invisible on a device
 * until someone notices the trail smearing or the keyboard stuttering: the
 * expiry arithmetic, and the revision counter that stands in for observability.
 */
class GlideTrailTest {

    private companion object {
        const val KEEP_MS = 100L
    }

    private fun trailWith(vararg times: Long): GlideTrail {
        val trail = GlideTrail()
        trail.begin()
        for (t in times) trail.add(t.toFloat(), t.toFloat(), t, KEEP_MS)
        return trail
    }

    @Test
    fun `a fresh trail is invisible`() {
        val trail = GlideTrail()
        assertFalse(trail.visible)
        assertEquals(0, trail.sampleCount(trail.revision))
    }

    @Test
    fun `beginning a stroke makes it visible and unreleased`() {
        val trail = GlideTrail()
        trail.release()
        trail.begin()
        assertTrue(trail.visible)
        assertFalse(trail.released)
    }

    @Test
    fun `samples accumulate and the head follows the finger`() {
        val trail = trailWith(0L, 10L, 20L)
        assertEquals(3, trail.sampleCount(trail.revision))
        assertEquals(20f, trail.headX, 0f)
        assertEquals(20f, trail.headY, 0f)
    }

    @Test
    fun `appending drops whatever aged past the keep window`() {
        // 0 and 10 are more than KEEP_MS behind 150; 60 and 150 survive.
        val trail = trailWith(0L, 10L, 60L, 150L)
        assertEquals(2, trail.sampleCount(trail.revision))
        assertEquals(60f, trail.x(0), 0f)
        assertEquals(150f, trail.x(1), 0f)
    }

    @Test
    fun `a long stroke never empties itself while the finger is down`() {
        // Every sample is far enough apart to expire the one before it. The
        // newest must always survive, or the trail would blink out mid-glide.
        val trail = GlideTrail()
        trail.begin()
        for (i in 0 until 20) {
            trail.add(i.toFloat(), 0f, i * 500L, KEEP_MS)
            assertTrue("emptied at sample $i", trail.sampleCount(trail.revision) >= 1)
        }
    }

    @Test
    fun `the buffer grows past its initial capacity`() {
        val trail = GlideTrail()
        trail.begin()
        // One sample per millisecond stays inside the keep window, so nothing
        // expires and the arrays have to grow.
        for (i in 0 until 500) trail.add(i.toFloat(), 0f, i.toLong(), Long.MAX_VALUE)
        assertEquals(500, trail.sampleCount(trail.revision))
        assertEquals(499f, trail.x(499), 0f)
    }

    @Test
    fun `age is measured from the newest sample while the finger is down`() {
        val trail = trailWith(0L, 40L, 80L)
        assertEquals(80L, trail.ageAt(0))
        assertEquals(0L, trail.ageAt(2))
    }

    @Test
    fun `ticking an unreleased trail keeps it alive however long it idles`() {
        // A finger held still stops producing samples. The trail must fade but
        // must not be collected — it is still under the finger.
        val trail = trailWith(0L)
        assertTrue(trail.tick(10_000L, KEEP_MS))
        assertTrue(trail.visible)
        assertEquals(1, trail.sampleCount(trail.revision))
    }

    @Test
    fun `a released trail fades out and then reports itself finished`() {
        val trail = trailWith(0L, 10L)
        trail.release()
        assertTrue("still within the window", trail.tick(50L, KEEP_MS))
        assertTrue(trail.visible)
        assertFalse("every point expired", trail.tick(500L, KEEP_MS))
        assertFalse(trail.visible)
        assertEquals(0, trail.sampleCount(trail.revision))
    }

    @Test
    fun `releasing mid-stroke freezes the trail and a second release is harmless`() {
        // The ambiguity picker releases the trail while the finger is still
        // down, so it fades where it stopped; the lift then releases it again.
        val trail = trailWith(0, 10)
        trail.release()
        assertTrue(trail.released)
        assertTrue(trail.visible)
        assertFalse(trail.tick(500, KEEP_MS))
        assertFalse(trail.visible)
        trail.release()
        assertFalse(trail.visible)
        // The next stroke starts clean.
        trail.begin()
        assertTrue(trail.visible)
        assertFalse(trail.released)
        assertEquals(0, trail.sampleCount(trail.revision))
    }

    // ---- the modifier chord's straight band (issue #67) ---------------------

    @Test
    fun `a straight band keeps its anchor and follows the finger`() {
        val trail = GlideTrail()
        trail.beginLine(5f, 6f)
        trail.add(80f, 90f, 10L, KEEP_MS)
        trail.add(120f, 130f, 20L, KEEP_MS)
        assertTrue(trail.straight)
        assertEquals(5f, trail.startX, 0f)
        assertEquals(6f, trail.startY, 0f)
        assertEquals(120f, trail.headX, 0f)
        assertEquals(130f, trail.headY, 0f)
        // The two ends are the whole drawing: no path is kept, so nothing can
        // age out from under the anchor however long the drag lasts.
        assertEquals(0, trail.sampleCount(trail.revision))
    }

    @Test
    fun `a straight band outlives the keep window while the finger is down`() {
        val trail = GlideTrail()
        trail.beginLine(0f, 0f)
        trail.add(50f, 0f, 5_000L, KEEP_MS)
        assertTrue(trail.tick(10_000L, KEEP_MS))
        assertTrue(trail.visible)
        assertEquals(1f, trail.lineLife(trail.revision, KEEP_MS), 0f)
    }

    @Test
    fun `a released straight band fades whole and then finishes`() {
        val trail = GlideTrail()
        trail.beginLine(0f, 0f)
        trail.add(50f, 0f, 0L, KEEP_MS)
        trail.release()
        assertTrue(trail.tick(50L, KEEP_MS))
        assertEquals(0.5f, trail.lineLife(trail.revision, KEEP_MS), 0.001f)
        assertFalse(trail.tick(100L, KEEP_MS))
        assertFalse(trail.visible)
        assertEquals(0f, trail.lineLife(trail.revision, KEEP_MS), 0f)
    }

    @Test
    fun `the next glide is a comet again`() {
        val trail = GlideTrail()
        trail.beginLine(0f, 0f)
        trail.begin()
        assertFalse(trail.straight)
        trail.add(1f, 1f, 0L, KEEP_MS)
        assertEquals(1, trail.sampleCount(trail.revision))
        trail.beginLine(0f, 0f)
        trail.clear()
        assertFalse(trail.straight)
    }

    @Test
    fun `a straight band moves the revision on every sample`() {
        val trail = GlideTrail()
        trail.beginLine(0f, 0f)
        val before = trail.revision
        trail.add(10f, 10f, 5L, KEEP_MS)
        assertTrue("a straight add did not move the revision", trail.revision != before)
    }

    @Test
    fun `clear abandons the trail outright`() {
        val trail = trailWith(0L, 10L)
        trail.clear()
        assertFalse(trail.visible)
        assertEquals(0, trail.sampleCount(trail.revision))
    }

    @Test
    fun `every mutation moves the revision`() {
        // The revision is the only thing Compose can see. If a mutation ever
        // stops bumping it, the trail silently freezes on screen.
        val trail = GlideTrail()
        val seen = mutableListOf(trail.revision)
        fun changed(what: String) {
            assertTrue("$what did not move the revision", trail.revision != seen.last())
            seen.add(trail.revision)
        }
        trail.begin(); changed("begin")
        trail.add(1f, 1f, 0L, KEEP_MS); changed("add")
        trail.tick(20L, KEEP_MS); changed("tick")
        trail.clear(); changed("clear")
    }

    // --- the ribbon geometry (see [GlideTrail.sampleEdge]) -------------------
    //
    // The trail is filled as a ribbon: each sample is offset to a left and a
    // right edge, and neighbouring pieces meet on the pair the sample between
    // them produces. Two things break that invisibly. A normal that is not
    // perpendicular to the path skews the ribbon; a NaN anywhere in it makes
    // the whole drawPath a no-op, so the trail just stops appearing.

    private fun straightTrail(n: Int): GlideTrail {
        val trail = GlideTrail()
        trail.begin()
        for (i in 0 until n) trail.add(i * 10f, 0f, i.toLong(), KEEP_MS)
        return trail
    }

    @Test
    fun `an edge offset is perpendicular to the path and the given half width`() {
        val trail = straightTrail(5)
        val count = trail.sampleCount(trail.revision)
        for (i in 0 until count) {
            val edge = trail.sampleEdge(i, count, half = 7f)
            // The path runs along +x, so its normal is pure y.
            assertEquals("sample $i is not perpendicular", 0f, edge.x, 1e-3f)
            assertEquals("sample $i is not the half width", 7f, abs(edge.y), 1e-3f)
        }
    }

    @Test
    fun `neighbouring pieces are handed the same edge for the sample they share`() {
        // What keeps the joints from showing: the piece arriving at a sample
        // and the piece leaving it ask for that sample's edge and must get one
        // answer, or they overlap (a bead) or fall short (a seam).
        val trail = GlideTrail()
        trail.begin()
        val path = listOf(0f to 0f, 12f to 4f, 20f to 18f, 24f to 40f, 40f to 44f)
        path.forEachIndexed { i, (x, y) -> trail.add(x, y, i.toLong(), KEEP_MS) }
        val count = trail.sampleCount(trail.revision)
        for (i in 0 until count) {
            assertEquals(trail.sampleEdge(i, count, 5f), trail.sampleEdge(i, count, 5f))
        }
    }

    @Test
    fun `duplicate samples never produce a NaN edge`() {
        // A finger resting on glass reports the same point over and over.
        // Normalising that step divides by zero, and one NaN vertex silently
        // drops the whole path — the trail stops being drawn at all.
        val trail = GlideTrail()
        trail.begin()
        repeat(4) { trail.add(30f, 30f, it.toLong(), KEEP_MS) }
        trail.add(30f, 30f, 4L, KEEP_MS)
        val count = trail.sampleCount(trail.revision)
        for (i in 0 until count) {
            val edge = trail.sampleEdge(i, count, 6f)
            assertFalse("sample $i produced a NaN", edge.x.isNaN() || edge.y.isNaN())
        }
    }

    @Test
    fun `a stroke that doubles back folds instead of spiking`() {
        // In and out cancel at a reversal, leaving no bisector to take a normal
        // from. The incoming direction has to decide alone; a normalised zero
        // vector would put the edge at infinity.
        val trail = GlideTrail()
        trail.begin()
        listOf(0f to 0f, 10f to 0f, 20f to 0f, 10f to 0f, 0f to 0f)
            .forEachIndexed { i, (x, y) -> trail.add(x, y, i.toLong(), KEEP_MS) }
        val count = trail.sampleCount(trail.revision)
        val turn = trail.sampleEdge(2, count, 5f)
        assertFalse(turn.x.isNaN() || turn.y.isNaN())
        assertEquals("the fold is not the half width out", 5f, hypot(turn.x, turn.y), 1e-3f)
    }

    @Test
    fun `a sample that has aged out carries no width and no life`() {
        val trail = GlideTrail()
        trail.begin()
        trail.add(0f, 0f, 0L, KEEP_MS)
        trail.add(10f, 0f, KEEP_MS, KEEP_MS)
        val count = trail.sampleCount(trail.revision)
        assertEquals(0f, trail.sampleLife(0, KEEP_MS), 1e-3f)
        assertEquals(1f, trail.sampleLife(count - 1, KEEP_MS), 1e-3f)
        // The oldest sample tapers to the tail width, the newest to the head.
        assertEquals(1f, trail.sampleHalfWidth(0, KEEP_MS, head = 10f, tail = 2f), 1e-3f)
        assertEquals(5f, trail.sampleHalfWidth(count - 1, KEEP_MS, head = 10f, tail = 2f), 1e-3f)
    }

    @Test
    fun `a stroke that never moved draws nothing rather than dividing by zero`() {
        val trail = GlideTrail()
        trail.begin()
        trail.add(5f, 5f, 0L, KEEP_MS)
        assertEquals(Offset.Zero, trail.sampleEdge(0, trail.sampleCount(trail.revision), 8f))
    }
}
