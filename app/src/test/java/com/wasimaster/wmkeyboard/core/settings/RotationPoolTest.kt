package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.theme.PhotoAttribution
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Choosing the next photo, deciding what to throw away, and deciding whether
 * the network may be used at all.
 */
class RotationPoolTest {

    private fun entry(
        name: String,
        lastShownAt: Long = 0L,
        addedAt: Long = 0L,
        source: RotationSourceKind = RotationSourceKind.ONLINE,
        width: Int = 1200,
        height: Int = 627,
        bytes: Long = 200_000,
    ) = PoolEntry(
        fileName = name,
        source = source,
        credit = PhotoAttribution(provider = "unsplash", photoId = name, photographer = "Ana"),
        widthPx = width,
        heightPx = height,
        bytes = bytes,
        addedAt = addedAt,
        lastShownAt = lastShownAt,
    )

    // ---- picking ------------------------------------------------------

    @Test
    fun `an empty pool has nothing to show`() {
        assertNull(pickNextPhoto(emptyList(), currentFileName = null))
    }

    @Test
    fun `a photo never shown goes first`() {
        val entries = listOf(
            entry("seen", lastShownAt = 500),
            entry("fresh", lastShownAt = 0, addedAt = 100),
        )
        assertEquals("fresh", pickNextPhoto(entries, currentFileName = null)?.fileName)
    }

    @Test
    fun `the oldest unshown photo goes before a newer one`() {
        val entries = listOf(
            entry("newer", addedAt = 900),
            entry("older", addedAt = 100),
        )
        assertEquals("older", pickNextPhoto(entries, currentFileName = null)?.fileName)
    }

    @Test
    fun `once everything has been seen the longest ago wins`() {
        val entries = listOf(
            entry("recent", lastShownAt = 900),
            entry("stale", lastShownAt = 100),
        )
        assertEquals("stale", pickNextPhoto(entries, currentFileName = null)?.fileName)
    }

    @Test
    fun `the photo already up is not chosen again`() {
        val entries = listOf(
            entry("current", lastShownAt = 100),
            entry("other", lastShownAt = 900),
        )
        // "current" is the longest ago, but showing it again is not a rotation.
        assertEquals("other", pickNextPhoto(entries, currentFileName = "current")?.fileName)
    }

    @Test
    fun `a pool of one keeps showing its only photo`() {
        val entries = listOf(entry("only", lastShownAt = 100))
        assertEquals("only", pickNextPhoto(entries, currentFileName = "only")?.fileName)
    }

    @Test
    fun `a tie is broken at random rather than settling into an order`() {
        val entries = listOf(
            entry("a", lastShownAt = 100),
            entry("b", lastShownAt = 100),
            entry("c", lastShownAt = 100),
        )
        val seen = (0 until 40)
            .mapNotNull { pickNextPhoto(entries, currentFileName = null, random = Random(it))?.fileName }
            .toSet()
        assertTrue(seen.toString(), seen.size > 1)
    }

    @Test
    fun `a wide photo is preferred when one is wanted`() {
        val entries = listOf(
            entry("tall", width = 800, height = 1200, addedAt = 1),
            entry("wide", width = 1200, height = 627, addedAt = 2),
        )
        assertEquals("wide", pickNextPhoto(entries, null, wantWide = true)?.fileName)
    }

    @Test
    fun `wanting a wide photo does not mean showing nothing`() {
        // Nothing wide in the pool: a tall photo beats a blank background.
        val entries = listOf(entry("tall", width = 800, height = 1200))
        assertEquals("tall", pickNextPhoto(entries, null, wantWide = true)?.fileName)
    }

    // ---- eviction -----------------------------------------------------

    private fun filesOf(vararg names: String) = names.toSet()

    @Test
    fun `a photo not yet seen is never thrown away`() {
        // Holding photos ready is the entire point of the pool.
        val entries = listOf(entry("unseen"), entry("unseen2"), entry("unseen3"))
        val plan = poolEvictionPlan(entries, filesOf("unseen", "unseen2", "unseen3"), maxEntries = 1, maxBytes = 1)
        assertEquals(3, plan.keep.size)
        assertTrue(plan.deleteFiles.isEmpty())
    }

    @Test
    fun `a saved or device photo is never thrown away`() {
        val entries = listOf(
            entry("saved", lastShownAt = 1, source = RotationSourceKind.SAVED),
            entry("device", lastShownAt = 2, source = RotationSourceKind.DEVICE),
        )
        val plan = poolEvictionPlan(entries, filesOf("saved", "device"), maxEntries = 0, maxBytes = 0)
        assertEquals(2, plan.keep.size)
    }

    @Test
    fun `the count cap drops the longest-unseen downloads first`() {
        val entries = listOf(
            entry("old", lastShownAt = 100),
            entry("mid", lastShownAt = 200),
            entry("new", lastShownAt = 300),
        )
        val plan = poolEvictionPlan(entries, filesOf("old", "mid", "new"), maxEntries = 2, maxBytes = Long.MAX_VALUE)
        assertEquals(listOf("old"), plan.deleteFiles)
        assertEquals(setOf("mid", "new"), plan.keep.map { it.fileName }.toSet())
    }

    @Test
    fun `the byte cap also drops photos`() {
        val entries = listOf(
            entry("old", lastShownAt = 100, bytes = 400_000),
            entry("new", lastShownAt = 300, bytes = 400_000),
        )
        val plan = poolEvictionPlan(entries, filesOf("old", "new"), maxEntries = 99, maxBytes = 500_000)
        assertEquals(listOf("old"), plan.deleteFiles)
    }

    @Test
    fun `a record whose file is gone is dropped for free`() {
        val entries = listOf(entry("here", lastShownAt = 1), entry("vanished", lastShownAt = 2))
        val plan = poolEvictionPlan(entries, filesOf("here"), maxEntries = 99, maxBytes = Long.MAX_VALUE)
        assertEquals(listOf("here"), plan.keep.map { it.fileName })
        // Nothing to delete: the file already is not there.
        assertTrue(plan.deleteFiles.isEmpty())
    }

    @Test
    fun `evicted photos are remembered so they are not fetched straight back`() {
        val entries = listOf(entry("old", lastShownAt = 100), entry("new", lastShownAt = 300))
        val plan = poolEvictionPlan(entries, filesOf("old", "new"), maxEntries = 1, maxBytes = Long.MAX_VALUE)
        assertEquals(listOf("unsplash:old"), plan.evictedKeys)
    }

    // ---- when the network may be used ---------------------------------

    private val online = PhotoNetworkConditions(
        unlocked = true,
        online = true,
        metered = false,
        powerSaving = false,
        highContrastKeys = false,
    )

    private val rotating = PhotoBackgroundSettings(
        rotateEnabled = true,
        sources = setOf(RotationSourceKind.ONLINE),
    )

    @Test
    fun `a top-up needs rotation on, a network source, and a network`() {
        assertTrue(rotating.mayFetch(online))
        assertFalse(rotating.copy(rotateEnabled = false).mayFetch(online))
        assertFalse(rotating.copy(sources = setOf(RotationSourceKind.SAVED)).mayFetch(online))
        assertFalse(rotating.mayFetch(online.copy(online = false)))
    }

    @Test
    fun `nothing is fetched while the device is locked`() {
        // The pool is in credential-encrypted storage: there is nowhere to put
        // a photo and nothing that could read it.
        assertFalse(rotating.mayFetch(online.copy(unlocked = false)))
    }

    @Test
    fun `power saving stops a top-up nobody asked for`() {
        assertFalse(rotating.mayFetch(online.copy(powerSaving = true)))
    }

    @Test
    fun `mobile data is not spent without being asked`() {
        assertFalse(rotating.mayFetch(online.copy(metered = true)))
        assertTrue(rotating.copy(fetchOnMetered = true).mayFetch(online.copy(metered = true)))
    }

    @Test
    fun `no photos are fetched when they would never be drawn`() {
        // High-contrast keys drops background images entirely.
        assertFalse(rotating.mayFetch(online.copy(highContrastKeys = true)))
    }

    // ---- when a top-up is worth making --------------------------------

    @Test
    fun `an almost empty pool is topped up at once`() {
        assertTrue(rotating.needsTopUp(readyCount = 0, lastTopUpAt = 999_999, nowMs = 1_000_000))
    }

    @Test
    fun `a full pool is left alone`() {
        assertFalse(
            rotating.needsTopUp(readyCount = rotating.poolTarget, lastTopUpAt = 0, nowMs = Long.MAX_VALUE / 2),
        )
    }

    @Test
    fun `a partly full pool waits rather than fetching every time`() {
        val now = 100L * 3_600_000
        assertFalse(rotating.needsTopUp(readyCount = 5, lastTopUpAt = now - 3_600_000, nowMs = now))
        assertTrue(rotating.needsTopUp(readyCount = 5, lastTopUpAt = now - 24 * 3_600_000, nowMs = now))
    }

    // ---- the stored index ---------------------------------------------

    @Test
    fun `the pool index round-trips`() {
        val index = PoolIndex(
            entries = listOf(entry("a"), entry("b", lastShownAt = 5)),
            recentlyEvicted = listOf("unsplash:x"),
            lastTopUpAt = 42,
        )
        assertEquals(index, PoolIndexCodec.decode(PoolIndexCodec.encode(index)))
    }

    @Test
    fun `a corrupt pool index costs a re-download and nothing else`() {
        assertEquals(PoolIndex(), PoolIndexCodec.decode("{{{"))
        assertEquals(PoolIndex(), PoolIndexCodec.decode(""))
    }
}
