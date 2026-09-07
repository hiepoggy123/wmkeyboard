package com.wasimaster.wmkeyboard.core.gesture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hand model behind glide adaptation (issue #52): what one kept swipe
 * teaches, how far a lesson is trusted, and that an undo takes it back
 * exactly.
 */
class KeyOffsetsTest {

    private val keyWidth = 60f
    private val keys = listOf(
        KeyCenter('q', 30f, 30f), KeyCenter('w', 90f, 30f), KeyCenter('e', 150f, 30f),
        KeyCenter('a', 60f, 90f), KeyCenter('s', 120f, 90f),
    )

    private fun seen(key: Char, dx: Float, dy: Float): KeyOffsets.Observation {
        val k = keys.first { it.codePoint == key.code }
        val x = k.x / keyWidth
        val y = k.y / keyWidth
        return KeyOffsets.Observation(x, y, x + dx, y + dy)
    }

    private fun offset(model: KeyOffsets, key: Char): Pair<Float, Float> {
        val k = keys.first { it.codePoint == key.code }
        val out = FloatArray(2)
        model.offsetAt(k.x / keyWidth, k.y / keyWidth, out)
        return out[0] to out[1]
    }

    @Test
    fun `nothing learned shifts nothing`() {
        val model = KeyOffsets(null)
        assertTrue(model.isEmpty())
        assertSame(keys, model.shifted(keys, keyWidth))
        assertEquals(0f to 0f, offset(model, 'q'))
    }

    @Test
    fun `one observation moves the key part of the way, in its direction`() {
        val model = KeyOffsets(null)
        assertNotNull(model.observe(listOf(seen('q', 0.3f, 0.2f))))
        val (dx, dy) = offset(model, 'q')
        // Shrunk toward a whole-hand mean that is itself barely trusted yet,
        // so well short of the observation and never past it.
        assertTrue("dx $dx", dx > 0f && dx < 0.3f)
        assertTrue("dy $dy", dy > 0f && dy < 0.2f)
        assertEquals(dx / dy, 0.3f / 0.2f, 1e-4f)
    }

    @Test
    fun `a consistent hand converges on its miss`() {
        val model = KeyOffsets(null)
        repeat(100) { model.observe(listOf(seen('q', 0.3f, -0.1f), seen('s', 0.3f, -0.1f))) }
        val (dx, dy) = offset(model, 'q')
        assertEquals(0.3f, dx, 0.02f)
        assertEquals(-0.1f, dy, 0.02f)
    }

    @Test
    fun `a key never swiped follows the rest of the hand`() {
        val model = KeyOffsets(null)
        repeat(40) { model.observe(listOf(seen('q', 0.25f, 0f), seen('w', 0.25f, 0f))) }
        val (dx, _) = offset(model, 'e')
        assertTrue("dx $dx", dx > 0.15f)
    }

    @Test
    fun `a key with its own history outweighs the hand`() {
        val model = KeyOffsets(null)
        repeat(40) { model.observe(listOf(seen('q', 0.3f, 0f), seen('w', 0.3f, 0f), seen('a', -0.3f, 0f))) }
        val (dx, _) = offset(model, 'a')
        // Its own way, less the dead zone and the pull of the hand.
        assertTrue("dx $dx", dx < -0.1f)
    }

    @Test
    fun `a cell that barely differs from the hand is read as the hand`() {
        val model = KeyOffsets(null)
        repeat(40) { model.observe(listOf(seen('q', 0.2f, 0f), seen('w', 0.2f, 0f), seen('a', 0.25f, 0f))) }
        assertEquals(offset(model, 'q'), offset(model, 'a'))
    }

    @Test
    fun `a hand that draws small is read as a trend`() {
        // Misses that grow with the distance from the middle key and change
        // sign across it: a scale, which the whole-hand mean cannot say and
        // the trend can. A key never swiped, further out still, gets the
        // trend's share.
        val model = KeyOffsets(null)
        repeat(40) { model.observe(listOf(seen('q', -0.2f, 0f), seen('w', 0f, 0f), seen('e', 0.2f, 0f))) }
        assertTrue("trend ${model.trendX()}", model.trendX() > 0.05f)
        assertEquals(0f, model.trendY(), 1e-6f)
        val out = FloatArray(2)
        model.offsetAt(4.5f, 0.5f, out)
        assertTrue("far key dx ${out[0]}", out[0] > 0.2f)
        model.offsetAt(-1.5f, 0.5f, out)
        assertTrue("near key dx ${out[0]}", out[0] < -0.2f)
    }

    @Test
    fun `a constant miss is no trend`() {
        val model = KeyOffsets(null)
        repeat(40) { model.observe(listOf(seen('q', 0.25f, 0f), seen('w', 0.25f, 0f), seen('e', 0.25f, 0f))) }
        assertEquals(0f, model.trendX(), 1e-6f)
        assertEquals(0f, model.trendY(), 1e-6f)
    }

    @Test
    fun `a trend needs the keys to spread along its axis`() {
        // One column: nothing to fit a vertical slope to, however the misses run.
        val model = KeyOffsets(null)
        repeat(40) { model.observe(listOf(seen('w', 0f, -0.2f), seen('s', 0f, 0.2f))) }
        assertEquals(0f, model.trendY(), 1e-6f)
    }

    @Test
    fun `the shift is capped however far the finger lands`() {
        val model = KeyOffsets(null)
        repeat(60) { model.observe(listOf(seen('q', 0.85f, 0.85f))) }
        val (dx, dy) = offset(model, 'q')
        assertTrue(kotlin.math.hypot(dx, dy) <= KeyOffsets.MAX_SHIFT + 1e-4f)
    }

    @Test
    fun `a misaligned letter teaches nothing`() {
        val model = KeyOffsets(null)
        assertNull(model.observe(listOf(seen('q', 1.5f, 0f))))
        assertTrue(model.isEmpty())
        // ...and does not poison the letters beside it in the same word.
        model.observe(listOf(seen('q', 1.5f, 0f), seen('w', 0.2f, 0f)))
        assertEquals(1, model.observations())
    }

    @Test
    fun `retracting the last observation restores the model exactly`() {
        val model = KeyOffsets(null)
        model.observe(listOf(seen('q', 0.2f, 0.1f)))
        val before = offset(model, 'q')
        val version = model.version
        val taught = model.observe(listOf(seen('q', -0.4f, 0.3f), seen('e', 0.1f, 0.1f)))!!
        model.retract(taught)
        assertEquals(before, offset(model, 'q'))
        // e's own cell is gone again, so it reads like any unswiped key.
        assertEquals(offset(model, 'w'), offset(model, 'e'))
        assertTrue(model.version > version)
        // A cell the retracted lesson created is gone, not left at zero.
        val fresh = KeyOffsets(null)
        val only = fresh.observe(listOf(seen('e', 0.1f, 0.1f)))!!
        fresh.retract(only)
        assertTrue(fresh.isEmpty())
    }

    @Test
    fun `shifted keys keep sharing a key`() {
        val model = KeyOffsets(null)
        repeat(20) { model.observe(listOf(seen('q', 0.2f, 0.1f))) }
        val shared = listOf(KeyCenter('a', 60f, 90f), KeyCenter('A', 60f, 90f), KeyCenter('ä', 60f, 90f))
        val shifted = model.shifted(shared, keyWidth)
        assertEquals(1, shifted.map { it.x to it.y }.distinct().size)
        assertFalse(shifted[0].x == 60f && shifted[0].y == 90f)
    }

    @Test
    fun `the file round-trips and a reload drops what was in memory`() {
        val file = File.createTempFile("key_offsets", ".json").apply { delete() }
        try {
            val model = KeyOffsets(file)
            repeat(10) { model.observe(listOf(seen('q', 0.2f, 0.1f))) }
            model.save()
            val back = KeyOffsets(file)
            assertEquals(offset(model, 'q'), offset(back, 'q'))
            assertEquals(model.observations(), back.observations())

            file.delete()
            model.reload()
            assertTrue(model.isEmpty())
        } finally {
            file.delete()
        }
    }
}
