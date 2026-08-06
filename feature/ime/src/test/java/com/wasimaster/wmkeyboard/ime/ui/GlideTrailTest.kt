package com.wasimaster.wmkeyboard.ime.ui

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
}
