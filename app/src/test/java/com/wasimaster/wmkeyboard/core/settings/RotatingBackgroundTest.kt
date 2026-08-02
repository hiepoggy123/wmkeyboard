package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.theme.PhotoAttribution
import com.wasimaster.wmkeyboard.core.theme.ThemeSpec
import com.wasimaster.wmkeyboard.core.theme.alphaFraction
import com.wasimaster.wmkeyboard.core.theme.themeFromSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a rotating background is due, and what it does to the theme on its way
 * to the screen.
 *
 * Every reading of the clock is a parameter, so a reboot, a time-zone change
 * and a user setting the date back a year are all one-line cases here rather
 * than things nobody can test.
 */
class RotatingBackgroundTest {

    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun stateAt(epochMs: Long, elapsedMs: Long) = RotationState(
        imagePath = "/files/theme_photos/p1.img",
        rotatedAtEpochMs = epochMs,
        rotatedAtElapsedMs = elapsedMs,
    )

    private fun due(
        interval: RotationInterval,
        state: RotationState,
        nowEpochMs: Long,
        nowElapsedMs: Long,
        sessionStarted: Boolean = true,
    ) = isRotationDue(
        interval = interval,
        state = state,
        nowEpochMs = nowEpochMs,
        nowElapsedMs = nowElapsedMs,
        sessionStarted = sessionStarted,
    )

    // ---- when a rotation is due ---------------------------------------

    @Test
    fun `the first photo is always due`() {
        assertTrue(due(RotationInterval.DAILY, RotationState(), 1_000L, 1_000L))
    }

    @Test
    fun `a daily rotation waits a day`() {
        val start = 1_700_000_000_000L
        val state = stateAt(start, 10_000L)
        assertFalse(due(RotationInterval.DAILY, state, start + 23 * hour, 10_000L + 23 * hour))
        assertTrue(due(RotationInterval.DAILY, state, start + day, 10_000L + day))
    }

    @Test
    fun `every open means every session`() {
        val state = stateAt(1_000L, 1_000L)
        assertTrue(due(RotationInterval.EVERY_OPEN, state, 2_000L, 2_000L, sessionStarted = true))
        assertFalse(due(RotationInterval.EVERY_OPEN, state, 2_000L, 2_000L, sessionStarted = false))
    }

    @Test
    fun `manual never comes due on its own`() {
        val state = stateAt(1_000L, 1_000L)
        // Not even after a year: the Shuffle button is the only trigger.
        assertFalse(due(RotationInterval.MANUAL, state, 1_000L + 365 * day, 1_000L + 365 * day))
    }

    @Test
    fun `a clock correction does not decide the schedule`() {
        val start = 1_700_000_000_000L
        val state = stateAt(start, 5 * hour)
        // The wall clock jumped an hour forward — a time-zone change, or NTP
        // correcting the device — but the monotonic clock says only ten minutes
        // really passed, and that is the one that cannot be moved.
        assertFalse(
            due(RotationInterval.HOURLY, state, start + hour + 600_000L, 5 * hour + 600_000L),
        )
    }

    @Test
    fun `the wall clock is used across a reboot`() {
        val start = 1_700_000_000_000L
        val state = stateAt(start, 5 * hour)
        // After a reboot the monotonic clock restarts, so it says two minutes;
        // only the wall clock knows a day went by.
        assertTrue(due(RotationInterval.DAILY, state, start + day, 120_000L))
    }

    @Test
    fun `a clock moved backwards does not freeze the background forever`() {
        val start = 1_700_000_000_000L
        val state = stateAt(start, 5 * hour)
        // The user set the date back a year, on a different boot. Waiting for
        // the interval to elapse from a stamp in the future would mean the
        // background never changes again.
        assertTrue(due(RotationInterval.DAILY, state, start - 365 * day, 120_000L))
    }

    @Test
    fun `an implausible jump forward is treated as a clock change`() {
        val start = 1_700_000_000_000L
        val state = stateAt(start, 5 * hour)
        assertTrue(due(RotationInterval.WEEKLY, state, start + 400 * day, 120_000L))
    }

    @Test
    fun `each interval waits its own period`() {
        val start = 1_700_000_000_000L
        val state = stateAt(start, hour)
        for (interval in listOf(
            RotationInterval.HOURLY,
            RotationInterval.SIX_HOURLY,
            RotationInterval.DAILY,
            RotationInterval.WEEKLY,
        )) {
            val period = interval.periodMs
            assertFalse(
                interval.name,
                due(interval, state, start + period - 1, hour + period - 1),
            )
            assertTrue(interval.name, due(interval, state, start + period, hour + period))
        }
    }

    // ---- which themes rotate ------------------------------------------

    @Test
    fun `rotation off means no theme rotates`() {
        val off = PhotoBackgroundSettings(rotateEnabled = false, scope = RotationScope.ALL_THEMES)
        assertFalse(off.rotates("a", activeThemeId = "a"))
    }

    @Test
    fun `each scope picks the themes it says it does`() {
        val current = PhotoBackgroundSettings(rotateEnabled = true, scope = RotationScope.CURRENT_THEME)
        assertTrue(current.rotates("a", activeThemeId = "a"))
        assertFalse(current.rotates("b", activeThemeId = "a"))

        val all = current.copy(scope = RotationScope.ALL_THEMES)
        assertTrue(all.rotates("b", activeThemeId = "a"))

        val selected = current.copy(
            scope = RotationScope.SELECTED_THEMES,
            scopeThemeIds = setOf("b", "c"),
        )
        assertTrue(selected.rotates("b", activeThemeId = "a"))
        assertFalse(selected.rotates("a", activeThemeId = "a"))
    }

    // ---- what the override does to a theme ----------------------------

    private val credit = PhotoAttribution(provider = "pexels", photoId = "9", photographer = "Bo")

    private fun rotating() = RotationState(
        imagePath = "/files/theme_photos/p1.img",
        credit = credit,
        seedColor = 0xFF4C8DF6,
        scrimAlpha = 0.4f,
    )

    @Test
    fun `an empty pool leaves the theme exactly as it was`() {
        val theme = ThemeSpec(id = "a", name = "A")
        val settings = PhotoBackgroundSettings(rotateEnabled = true)
        // Not merely equal: the same object, so this is provably a no-op for
        // anyone whose pool has not filled yet.
        assertSame(theme, theme.withRotation(null, settings))
        assertSame(theme, theme.withRotation(RotationState(), settings))
    }

    @Test
    fun `the photo and its credit are laid over the theme`() {
        val theme = ThemeSpec(id = "a", name = "A")
        val settings = PhotoBackgroundSettings(rotateEnabled = true)
        val shown = theme.withRotation(rotating(), settings)
        assertEquals("/files/theme_photos/p1.img", shown.backgroundImage)
        assertEquals(credit, shown.backgroundPhoto)
        // One photo covers both orientations; the draw code falls back to the
        // portrait image when there is no landscape one.
        assertNull(shown.backgroundImageLandscape)
        assertNull(shown.backgroundPhotoLandscape)
    }

    @Test
    fun `the stored theme is never touched`() {
        val theme = ThemeSpec(id = "a", name = "A", backgroundImage = "/mine.img")
        val settings = PhotoBackgroundSettings(rotateEnabled = true, seedPalette = true)
        theme.withRotation(rotating(), settings)
        // The whole reason this is an override and not a write.
        assertEquals("/mine.img", theme.backgroundImage)
        assertNull(theme.backgroundPhoto)
    }

    @Test
    fun `seeding the palette is off unless it is asked for`() {
        val theme = ThemeSpec(id = "a", name = "A", accent = 0xFF00FF00)
        val plain = theme.withRotation(rotating(), PhotoBackgroundSettings(rotateEnabled = true))
        assertEquals(0xFF00FF00, plain.accent)

        val seeded = theme.withRotation(
            rotating(),
            PhotoBackgroundSettings(rotateEnabled = true, seedPalette = true),
        )
        assertEquals(themeFromSeed("a", "A", 0xFF4C8DF6, true).accent, seeded.accent)
    }

    @Test
    fun `the scrim survives the reseed that would otherwise erase it`() {
        // Reseeding rebuilds the board colour opaque from the seed, so the
        // order of the two is what decides whether the keys stay readable.
        val theme = ThemeSpec(id = "a", name = "A")
        val shown = theme.withRotation(
            rotating(),
            PhotoBackgroundSettings(
                rotateEnabled = true,
                seedPalette = true,
                readabilityGuard = true,
            ),
        )
        assertEquals(0.4f, shown.boardBackground.alphaFraction(), 0.01f)
    }

    @Test
    fun `no scrim is applied when none was measured`() {
        val theme = ThemeSpec(id = "a", name = "A", boardBackground = 0xFF17181C)
        val shown = theme.withRotation(
            rotating().copy(scrimAlpha = -1f),
            PhotoBackgroundSettings(rotateEnabled = true),
        )
        assertEquals(0xFF17181C, shown.boardBackground)
    }

    // ---- the list-level helper ----------------------------------------

    @Test
    fun `a theme outside the scope comes back untouched`() {
        val themes = listOf(ThemeSpec(id = "a", name = "A"), ThemeSpec(id = "b", name = "B"))
        val settings = PhotoBackgroundSettings(rotateEnabled = true, scope = RotationScope.CURRENT_THEME)
        val shown = themes.withRotatingBackgrounds(
            states = mapOf("a" to rotating(), "b" to rotating()),
            settings = settings,
            activeThemeId = "a",
        )
        assertEquals("/files/theme_photos/p1.img", shown[0].backgroundImage)
        assertSame(themes[1], shown[1])
    }

    @Test
    fun `with rotation off the whole list is the same list`() {
        val themes = listOf(ThemeSpec(id = "a", name = "A"))
        assertSame(
            themes,
            themes.withRotatingBackgrounds(
                states = mapOf("a" to rotating()),
                settings = PhotoBackgroundSettings(rotateEnabled = false),
                activeThemeId = "a",
            ),
        )
    }

    // ---- the stored map -----------------------------------------------

    @Test
    fun `the rotation map round-trips`() {
        val states = mapOf("a" to rotating(), "b" to RotationState())
        assertEquals(states, RotationStateCodec.decode(RotationStateCodec.encode(states)))
    }

    @Test
    fun `a corrupt rotation map costs the photo and nothing else`() {
        assertTrue(RotationStateCodec.decode("not json at all").isEmpty())
        assertTrue(RotationStateCodec.decode("").isEmpty())
    }
}
