package com.wasimaster.wmkeyboard.app

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The date arithmetic behind the install-anniversary card: whole years since
 * install, whether today is the day, and where a February 29th install lands
 * in a year with no 29th.
 */
class AnniversaryEggTest {

    private val utc = TimeZone.getTimeZone("UTC")

    /** Noon on the given day, so day boundaries are nowhere near the value. */
    private fun millis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis

    @Test
    fun `first anniversary is the install date one year on`() {
        val install = millis(2024, 3, 10)
        assertTrue(AnniversaryEgg.isAnniversary(install, millis(2025, 3, 10), utc))
        assertEquals(1, AnniversaryEgg.yearsSince(install, millis(2025, 3, 10), utc))
    }

    @Test
    fun `the day before and the day after are not the anniversary`() {
        val install = millis(2024, 3, 10)
        assertFalse(AnniversaryEgg.isAnniversary(install, millis(2025, 3, 9), utc))
        assertFalse(AnniversaryEgg.isAnniversary(install, millis(2025, 3, 11), utc))
    }

    @Test
    fun `the install year itself never celebrates`() {
        val install = millis(2024, 3, 10)
        assertFalse(AnniversaryEgg.isAnniversary(install, install, utc))
        assertFalse(AnniversaryEgg.isAnniversary(install, millis(2024, 12, 31), utc))
        assertEquals(0, AnniversaryEgg.yearsSince(install, millis(2024, 12, 31), utc))
    }

    @Test
    fun `years count turns over on the anniversary day`() {
        val install = millis(2020, 5, 15)
        assertEquals(5, AnniversaryEgg.yearsSince(install, millis(2026, 5, 14), utc))
        assertEquals(6, AnniversaryEgg.yearsSince(install, millis(2026, 5, 15), utc))
    }

    @Test
    fun `leap day install celebrates on the 28th in common years`() {
        val install = millis(2024, 2, 29)
        assertTrue(AnniversaryEgg.isAnniversary(install, millis(2025, 2, 28), utc))
        assertFalse(AnniversaryEgg.isAnniversary(install, millis(2025, 3, 1), utc))
        assertEquals(1, AnniversaryEgg.yearsSince(install, millis(2025, 2, 28), utc))
    }

    @Test
    fun `leap day install celebrates on the 29th when it exists`() {
        val install = millis(2024, 2, 29)
        assertTrue(AnniversaryEgg.isAnniversary(install, millis(2028, 2, 29), utc))
        assertFalse(AnniversaryEgg.isAnniversary(install, millis(2028, 2, 28), utc))
        assertEquals(4, AnniversaryEgg.yearsSince(install, millis(2028, 2, 29), utc))
    }
}
