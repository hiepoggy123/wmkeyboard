package com.wasimaster.wmkeyboard.core.snippets.espanso

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EspansoDateTest {

    @Test
    fun `the common specifiers convert`() {
        assertEquals("dd/MM/yyyy", EspansoDate.toPattern("%d/%m/%Y").value)
        assertEquals("HH:mm", EspansoDate.toPattern("%H:%M").value)
        assertEquals("yyyy-MM-dd", EspansoDate.toPattern("%F").value)
        assertEquals("EEEE", EspansoDate.toPattern("%A").value)
    }

    @Test
    fun `literal text between specifiers is quoted so it stays literal`() {
        // Unquoted, the h, o, u, r and s would each be read as a date field.
        val pattern = EspansoDate.toPattern("%H hours").value
        assertEquals("HH' hours'", pattern)
        assertEquals(
            "09 hours",
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(9 * 3600 * 1000L)),
        )
    }

    @Test
    fun `punctuation between specifiers is left alone`() {
        assertEquals("dd/MM", EspansoDate.toPattern("%d/%m").value)
        assertEquals("HH:mm:ss", EspansoDate.toPattern("%H:%M:%S").value)
    }

    @Test
    fun `an apostrophe in literal text is doubled`() {
        val pattern = EspansoDate.toPattern("%Y o'clock").value
        assertTrue(pattern.contains("''"))
        // And it survives being used, which is the only thing that matters.
        SimpleDateFormat(pattern, Locale.US).format(Date(0))
    }

    @Test
    fun `a double percent is a literal percent, and needs no quoting`() {
        // Only letters and apostrophes mean anything to SimpleDateFormat, so a
        // percent is already literal and quoting it would only add noise.
        assertEquals("100%", EspansoDate.toPattern("100%%").value)
    }

    @Test
    fun `an unknown specifier is dropped and named`() {
        val result = EspansoDate.toPattern("%Y%Q")
        assertEquals("yyyy", result.value)
        assertEquals(listOf("%Q"), result.dropped)
    }

    @Test
    fun `padding modifiers are accepted`() {
        assertEquals("dd", EspansoDate.toPattern("%-d").value)
        assertEquals("HH", EspansoDate.toPattern("%0H").value)
    }

    @Test
    fun `patterns convert back to strftime`() {
        assertEquals("%d/%m/%Y", EspansoDate.toStrftime("dd/MM/yyyy").value)
        assertEquals("%H:%M", EspansoDate.toStrftime("HH:mm").value)
        assertEquals("%A", EspansoDate.toStrftime("EEEE").value)
    }

    @Test
    fun `quoted literals come back out unquoted`() {
        assertEquals("%H hours", EspansoDate.toStrftime("HH' hours'").value)
        assertEquals("%Y o'clock", EspansoDate.toStrftime("yyyy' o''clock'").value)
    }

    @Test
    fun `a percent in literal text is escaped on the way out`() {
        assertEquals("%%", EspansoDate.toStrftime("'%'").value)
    }

    @Test
    fun `the common patterns survive a round trip`() {
        for (pattern in listOf("dd/MM/yyyy", "HH:mm", "yyyy-MM-dd", "EEEE", "d MMM yyyy")) {
            val back = EspansoDate.toPattern(EspansoDate.toStrftime(pattern).value).value
            assertEquals(pattern, back)
        }
    }
}
