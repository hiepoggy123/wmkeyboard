package com.wasimaster.wmkeyboard.core.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one question this answers is "should we offer an update?", so the cases
 * that matter most are the ones where getting it wrong would offer a
 * *downgrade* — a manifest can be hand-written and reach us with anything in
 * its version field.
 */
class SemverTest {

    @Test
    fun `orders by major, minor then patch`() {
        assertTrue(Semver.isNewer("1.0.1", "1.0.0"))
        assertTrue(Semver.isNewer("1.1.0", "1.0.9"))
        assertTrue(Semver.isNewer("2.0.0", "1.9.9"))
        assertFalse(Semver.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(Semver.isNewer("1.2.3", "1.2.3"))
        assertEquals(0, Semver.compare("1.2.3", "1.2.3"))
    }

    @Test
    fun `numbers compare numerically, not as text`() {
        // "10" sorts before "9" as a string; the whole point of parsing.
        assertTrue(Semver.isNewer("1.10.0", "1.9.0"))
        assertTrue(Semver.isNewer("1.0.10", "1.0.9"))
        assertTrue(Semver.isNewer("10.0.0", "9.0.0"))
    }

    @Test
    fun `a missing component reads as zero`() {
        assertEquals(0, Semver.compare("1.2", "1.2.0"))
        assertTrue(Semver.isNewer("1.2.1", "1.2"))
        assertFalse(Semver.isNewer("1.2", "1.2.1"))
    }

    @Test
    fun `a leading v is tolerated`() {
        assertEquals(0, Semver.compare("v1.2.3", "1.2.3"))
        assertTrue(Semver.isNewer("v2.0.0", "v1.0.0"))
    }

    @Test
    fun `a prerelease precedes its release`() {
        assertTrue(Semver.isNewer("1.0.0", "1.0.0-rc1"))
        assertFalse(Semver.isNewer("1.0.0-rc1", "1.0.0"))
    }

    @Test
    fun `prereleases order among themselves`() {
        assertTrue(Semver.isNewer("1.0.0-beta", "1.0.0-alpha"))
        assertTrue(Semver.isNewer("1.0.0-rc.2", "1.0.0-rc.1"))
        // A shorter prerelease precedes a longer one with the same prefix.
        assertTrue(Semver.isNewer("1.0.0-rc.1", "1.0.0-rc"))
        // Numeric identifiers rank below alphanumeric ones.
        assertTrue(Semver.isNewer("1.0.0-alpha", "1.0.0-1"))
    }

    @Test
    fun `build metadata does not affect ordering`() {
        assertEquals(0, Semver.compare("1.0.0+build1", "1.0.0+build2"))
        assertEquals(0, Semver.compare("1.0.0+build1", "1.0.0"))
    }

    @Test
    fun `garbage never produces a downgrade`() {
        // The failure we can live with is "no update offered". The one we
        // cannot is replacing something newer with something older.
        assertFalse(Semver.isNewer("", "1.0.0"))
        assertFalse(Semver.isNewer("not-a-version", "1.0.0"))
        assertFalse(Semver.isNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun `a partly numeric component still reads its number`() {
        assertEquals(0, Semver.compare("1.3beta.0", "1.3.0"))
    }

    @Test
    fun `date-style versions still order`() {
        assertTrue(Semver.isNewer("2026.7.2", "2026.7.1"))
        assertTrue(Semver.isNewer("2026.8", "2026.7"))
    }
}
