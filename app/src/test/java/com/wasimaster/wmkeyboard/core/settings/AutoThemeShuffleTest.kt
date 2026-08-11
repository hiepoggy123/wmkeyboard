package com.wasimaster.wmkeyboard.core.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.wasimaster.wmkeyboard.core.theme.DEFAULT_THEME_ID
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The random half of the auto theme: which theme a half shows, when it selects
 * again, and the states a user can leave it in.
 */
class AutoThemeShuffleTest {

    private val pool = setOf("builtin_ocean", "builtin_snow", "builtin_facet")

    private fun random(darkSlot: Boolean = false, block: AutoThemeSettings.() -> AutoThemeSettings) =
        AutoThemeSettings(enabled = true)
            .let { if (darkSlot) it.copy(darkRandom = true) else it.copy(lightRandom = true) }
            .block()

    // ---- which theme a half shows -------------------------------------

    @Test
    fun `a half that is not random shows the one theme it names`() {
        val auto = AutoThemeSettings(
            enabled = true,
            lightThemeId = "builtin_snow",
            darkThemeId = "builtin_ocean",
            // A pool left behind by turning the random selection off is ignored,
            // not applied: the flag is what decides, so coming back to it finds
            // the set still there.
            lightPoolIds = pool,
        )
        assertEquals("builtin_snow", auto.slotThemeId(darkSlot = false))
        assertEquals("builtin_ocean", auto.slotThemeId(darkSlot = true))
    }

    @Test
    fun `a random half shows the theme it selected`() {
        val auto = random { copy(lightPoolIds = pool, shuffleLightId = "builtin_facet") }
        assertEquals("builtin_facet", auto.slotThemeId(darkSlot = false))
    }

    @Test
    fun `a selection that has left the pool falls back inside it`() {
        val auto = random { copy(lightPoolIds = pool, shuffleLightId = "builtin_gone") }
        assertTrue(auto.slotThemeId(darkSlot = false) in pool)
        // Deterministic, so the board does not change on every read of a Set
        // that has no order of its own.
        assertEquals(pool.min(), auto.slotThemeId(darkSlot = false))
    }

    @Test
    fun `a random half with an empty pool shows the one theme it had`() {
        val auto = random { copy(lightThemeId = "builtin_snow", lightPoolIds = emptySet()) }
        assertEquals("builtin_snow", auto.slotThemeId(darkSlot = false))
    }

    @Test
    fun `a half that has not selected yet still shows a theme`() {
        val auto = random { copy(lightPoolIds = pool, shuffleLightId = "") }
        assertTrue(auto.slotThemeId(darkSlot = false) in pool)
    }

    @Test
    fun `the two halves are independent`() {
        val auto = AutoThemeSettings(
            enabled = true,
            lightThemeId = DEFAULT_THEME_ID,
            darkThemeId = "builtin_ocean",
            darkRandom = true,
            darkPoolIds = pool,
            shuffleDarkId = "builtin_facet",
        )
        assertEquals(DEFAULT_THEME_ID, auto.slotThemeId(darkSlot = false))
        assertEquals("builtin_facet", auto.slotThemeId(darkSlot = true))
        assertFalse(auto.slotRandom(darkSlot = false))
        assertTrue(auto.usesRandomSlot)
    }

    // ---- selecting the next theme -------------------------------------

    @Test
    fun `the next theme is never the one showing`() {
        // Every seed, not one lucky one: a repeat is the failure the exclusion
        // exists to stop, and it would show up in only some runs otherwise.
        for (seed in 0 until 200) {
            assertNotEquals(
                "builtin_snow",
                nextShuffledId(pool, current = "builtin_snow", random = Random(seed)),
            )
        }
    }

    @Test
    fun `every theme in the pool can come up`() {
        val seen = (0 until 200)
            .map { nextShuffledId(pool, current = "", random = Random(it)) }
            .toSet()
        assertEquals(pool, seen)
    }

    @Test
    fun `a pool of one keeps showing that one`() {
        val one = setOf("builtin_ocean")
        assertEquals("builtin_ocean", nextShuffledId(one, "builtin_ocean", Random(1)))
    }

    @Test
    fun `an empty pool selects nothing`() {
        assertEquals("", nextShuffledId(emptySet(), "builtin_ocean", Random(1)))
    }

    @Test
    fun `the same seed and pool always give the same theme`() {
        // The pool arrives as a Set out of DataStore, whose iteration order is
        // not its own. Two sets with the same members must agree.
        val a = setOf("builtin_ocean", "builtin_snow", "builtin_facet")
        val b = setOf("builtin_facet", "builtin_ocean", "builtin_snow")
        assertEquals(nextShuffledId(a, "", Random(7)), nextShuffledId(b, "", Random(7)))
    }

    // ---- when it selects again ----------------------------------------

    private fun due(
        auto: AutoThemeSettings,
        nowEpochMs: Long = 10_000_000L,
        nowElapsedMs: Long = 10_000_000L,
        sessionStarted: Boolean = false,
    ) = isThemeShuffleDue(auto, nowEpochMs, nowElapsedMs, sessionStarted)

    private fun rolled(interval: RotationInterval, atEpochMs: Long, atElapsedMs: Long) = random {
        copy(
            lightPoolIds = pool,
            shuffleInterval = interval,
            shuffledAtEpochMs = atEpochMs,
            shuffledAtElapsedMs = atElapsedMs,
        )
    }

    @Test
    fun `nothing is due while the pair is off or no half is random`() {
        val off = rolled(RotationInterval.HOURLY, 0L, 0L).copy(enabled = false)
        assertFalse(due(off, sessionStarted = true))
        val fixed = AutoThemeSettings(enabled = true, shuffleInterval = RotationInterval.HOURLY)
        assertFalse(due(fixed, sessionStarted = true))
    }

    @Test
    fun `every open selects only as a session starts`() {
        val auto = rolled(RotationInterval.EVERY_OPEN, 1L, 1L)
        assertTrue(due(auto, sessionStarted = true))
        assertFalse(due(auto, sessionStarted = false))
    }

    @Test
    fun `manual never selects on its own`() {
        val auto = rolled(RotationInterval.MANUAL, 0L, 0L)
        assertFalse(due(auto, sessionStarted = true))
    }

    @Test
    fun `a half that has never selected is due`() {
        assertTrue(due(rolled(RotationInterval.DAILY, 0L, 0L)))
    }

    @Test
    fun `an interval that has not passed is not due`() {
        val auto = rolled(RotationInterval.HOURLY, 9_000_000L, 9_000_000L)
        assertFalse(due(auto, nowEpochMs = 9_600_000L, nowElapsedMs = 9_600_000L))
        assertTrue(due(auto, nowEpochMs = 12_700_000L, nowElapsedMs = 12_700_000L))
    }

    @Test
    fun `a reboot falls back to the wall clock`() {
        // The monotonic clock restarted, so it says nothing; the wall clock says
        // two hours have passed.
        val auto = rolled(RotationInterval.HOURLY, 1_000_000L, 900_000_000L)
        assertTrue(due(auto, nowEpochMs = 8_200_000L, nowElapsedMs = 5_000L))
    }

    @Test
    fun `a clock set backwards selects rather than freezing`() {
        val auto = rolled(RotationInterval.DAILY, 900_000_000L, 900_000_000L)
        assertTrue(due(auto, nowEpochMs = 1_000L, nowElapsedMs = 1_000L))
    }

    // ---- what is stored ------------------------------------------------

    @Test
    fun `the defaults ship a fixed pair with no set behind it`() {
        val defaults = SettingsDefaults.autoTheme
        assertFalse(defaults.lightRandom)
        assertFalse(defaults.darkRandom)
        assertTrue(defaults.lightPoolIds.isEmpty())
        assertTrue(defaults.darkPoolIds.isEmpty())
        assertEquals(RotationInterval.EVERY_OPEN, defaults.shuffleInterval)
        assertEquals("", defaults.shuffleLightId)
        assertEquals(0L, defaults.shuffledAtEpochMs)
    }

    @Test
    fun `the pools travel with a backup and are not treated as secrets`() {
        val prefs = mutablePreferencesOf(
            booleanPreferencesKey("auto_theme_light_random") to true,
            stringSetPreferencesKey("auto_theme_light_pool") to pool,
            stringPreferencesKey("auto_theme_shuffle_interval") to "DAILY",
            stringPreferencesKey("auto_theme_shuffle_light_id") to "builtin_snow",
            longPreferencesKey("auto_theme_shuffled_at") to 42L,
        )
        val decoded = SettingsBackup.decode(
            SettingsBackup.encode(prefs, includeSecrets = false, appVersion = 1, appVersionName = "1.0"),
        )!!.entries.associate { it.name to it.value }
        assertEquals(true, decoded["auto_theme_light_random"])
        assertEquals(pool, decoded["auto_theme_light_pool"])
        assertEquals("DAILY", decoded["auto_theme_shuffle_interval"])
        assertEquals("builtin_snow", decoded["auto_theme_shuffle_light_id"])
        assertEquals(42L, decoded["auto_theme_shuffled_at"])
    }

    @Test
    fun `every interval name round trips`() {
        for (interval in RotationInterval.entries) {
            assertEquals(interval, runCatching { RotationInterval.valueOf(interval.name) }.getOrNull())
        }
        assertEquals(null, runCatching { RotationInterval.valueOf("NOT_A_CADENCE") }.getOrNull())
    }
}
