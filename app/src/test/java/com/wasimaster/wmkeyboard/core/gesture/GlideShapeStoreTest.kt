package com.wasimaster.wmkeyboard.core.gesture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [GlideShapeStore], how the user draws each word (issue #52). */
class GlideShapeStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(): File = File(temp.root, "learning/glide_shapes.json")

    /** A shape whose every coordinate is [value], in quantised units. */
    private fun flat(value: Int): ByteArray = ByteArray(GlideShapeStore.POINTS) { value.toByte() }

    /** A shape [away] quantised units off [base] on every coordinate. */
    private fun near(base: ByteArray, away: Int): ByteArray = ByteArray(base.size) { (base[it] + away).toByte() }

    private val layout = 0x5e1fL

    @Test
    fun `a settled shape is kept and found`() {
        val store = GlideShapeStore(null)
        assertNull(store.forLayout(layout))
        store.learn(GlideShapeSample(layout, flat(10)), "hello")
        val source = store.forLayout(layout) ?: error("no source")
        assertEquals(0f, source.minDistance("Hello", flat(10)), 1e-6f)
        assertEquals(-1f, source.minDistance("help", flat(10)), 0f)
        assertNull(store.forLayout(layout + 1))
        assertEquals(1, store.countFor("hello"))
    }

    @Test
    fun `a nearby draw blends in and a far one is a new way of drawing the word`() {
        val store = GlideShapeStore(null)
        val base = flat(10)
        store.learn(GlideShapeSample(layout, base), "hello")
        // Two quantised units is a twentieth of a key width: the same shape.
        store.learn(GlideShapeSample(layout, near(base, 2)), "hello")
        assertEquals(1, store.countFor("hello"))
        // Blended halfway: one unit off on both axes of every point.
        assertEquals(kotlin.math.sqrt(2f) / GlideShapeStore.QUANT, store.forLayout(layout)!!.minDistance("hello", base), 1e-6f)
        // Forty units is a whole key width off: another shape.
        store.learn(GlideShapeSample(layout, near(base, 40)), "hello")
        assertEquals(2, store.countFor("hello"))
        // And a fourth distinct shape replaces the least accepted.
        store.learn(GlideShapeSample(layout, near(base, 80)), "hello")
        store.learn(GlideShapeSample(layout, near(base, -60)), "hello")
        assertEquals(GlideShapeStore.MAX_SHAPES_PER_WORD, store.countFor("hello"))
    }

    @Test
    fun `a rejected shape goes once rejected more than accepted`() {
        val store = GlideShapeStore(null)
        val base = flat(10)
        store.learn(GlideShapeSample(layout, base), "hello")
        assertFalse(store.reject(layout, "hello", near(base, 100)))
        assertTrue(store.reject(layout, "hello", near(base, 1)))
        assertEquals(1, store.countFor("hello"))
        assertTrue(store.reject(layout, "hello", near(base, 1)))
        assertEquals(0, store.countFor("hello"))
        assertTrue(store.isEmpty())
    }

    @Test
    fun `words near a draw come nearest first`() {
        val store = GlideShapeStore(null)
        store.learn(GlideShapeSample(layout, flat(10)), "hello")
        store.learn(GlideShapeSample(layout, flat(14)), "help")
        store.learn(GlideShapeSample(layout, flat(90)), "world")
        val near = store.forLayout(layout)!!.wordsNear(flat(11), radius = 0.2f, limit = 8)
        assertEquals(listOf("hello", "help"), near)
        assertEquals(listOf("hello"), store.forLayout(layout)!!.wordsNear(flat(11), radius = 0.2f, limit = 1))
    }

    @Test
    fun `forget takes every layout's shapes and the file survives a round trip`() {
        val store = GlideShapeStore(file())
        store.learn(GlideShapeSample(layout, flat(10)), "hello")
        store.learn(GlideShapeSample(layout + 1, flat(20)), "hello")
        store.learn(GlideShapeSample(layout, flat(30)), "world")
        store.save()
        val reopened = GlideShapeStore(file())
        assertEquals(2, reopened.countFor("hello"))
        assertEquals(0f, reopened.forLayout(layout + 1)!!.minDistance("hello", flat(20)), 1e-6f)
        assertTrue(reopened.forget("hello"))
        assertEquals(0, reopened.countFor("hello"))
        assertEquals(1, reopened.countFor("world"))
        assertFalse(reopened.forget("hello"))
    }

    @Test
    fun `a file from another version starts empty and clear deletes the file`() {
        file().parentFile?.mkdirs()
        file().writeText("""{"v":2,"tick":5,"layouts":{"5e1f":{"hello":{"t":5,"s":[{"p":"00","a":1,"r":0}]}}}}""")
        assertTrue(GlideShapeStore(file()).isEmpty())
        val store = GlideShapeStore(file())
        store.learn(GlideShapeSample(layout, flat(10)), "hello")
        store.save()
        assertTrue(file().exists())
        store.clear()
        assertFalse(file().exists())
        assertTrue(store.isEmpty())
    }

    @Test
    fun `past the cap the word left alone longest goes`() {
        val store = GlideShapeStore(null)
        store.learn(GlideShapeSample(layout, flat(1)), "first")
        for (i in 0 until GlideShapeStore.MAX_WORDS - 1) store.learn(GlideShapeSample(layout, flat(2)), "word$i")
        assertEquals(GlideShapeStore.MAX_WORDS, store.wordCount())
        // Touching "first" makes it the newest; the next word evicts word0.
        store.learn(GlideShapeSample(layout, flat(1)), "first")
        store.learn(GlideShapeSample(layout, flat(3)), "newest")
        assertEquals(GlideShapeStore.MAX_WORDS, store.wordCount())
        assertEquals(1, store.countFor("first"))
        assertEquals(0, store.countFor("word0"))
    }

    @Test
    fun `distance is the shape channel's mean point distance`() {
        val a = flat(0)
        val b = near(a, 40)
        // Forty units on both axes is sqrt(2) key widths at every point.
        assertEquals(kotlin.math.sqrt(2f), GlideShapeStore.distance(a, b), 1e-5f)
        assertTrue(GlideShapeStore.distance(a, b, abortAbove = 0.5f) > 0.5f)
    }
}
