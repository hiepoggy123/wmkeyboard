package com.wasimaster.wmkeyboard.core.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phone-number masks: what the user says their numbers look like. */
class PhoneFormatsTest {

    private fun masks(vararg raw: String) = PhoneFormats.parseAll(raw.toList())

    // ---- parsing ----

    @Test fun splitsTheDialCodeOffAnInternationalMask() {
        val mask = PhoneFormats.parse("+880 1XXX-XXXXXX")
        assertEquals("880", mask?.countryCode)
        assertEquals("1XXXXXXXXX", mask?.national)
    }

    @Test fun findsTheDialCodeWithNoSeparatorToHelpIt() {
        assertEquals("880", PhoneFormats.parse("+8801XXXXXXXXX")?.countryCode)
        assertEquals("1", PhoneFormats.parse("+1XXXXXXXXXX")?.countryCode)
        assertEquals("44", PhoneFormats.parse("+447XXXXXXXXX")?.countryCode)
    }

    @Test fun aNationalMaskHasNoDialCode() {
        val mask = PhoneFormats.parse("(XXX) XXX-XXXX")
        assertNull(mask?.countryCode)
        assertEquals("XXXXXXXXXX", mask?.national)
    }

    @Test fun rejectsWhatIsNotAMask() {
        assertNull(PhoneFormats.parse(""))
        assertNull(PhoneFormats.parse("call me"))
        // Too few digits to name a number, and too many for E.164.
        assertNull(PhoneFormats.parse("XXX"))
        assertNull(PhoneFormats.parse("X".repeat(16)))
    }

    @Test fun writesEveryDigitWildcardTheSameWay() {
        assertEquals("+1 XXX XXX XXXX", PhoneFormats.canonical("+1 xxx ### XXXX"))
        assertNull(PhoneFormats.canonical("not a number"))
    }

    // ---- masks from an example ----

    @Test fun keepsTheDialCodeAndTheSeparatorsOfAnExample() {
        assertEquals("+880 XXXX-XXXXXX", PhoneFormats.fromExample("+880 1712-345678"))
        assertEquals("(XXX) XXX-XXXX", PhoneFormats.fromExample("(415) 555-2671"))
        assertEquals("+1 XXX XXX XXXX", PhoneFormats.fromExample("+1 415 555 2671"))
    }

    @Test fun anExampleThatIsNotANumberMakesNoMask() {
        assertNull(PhoneFormats.fromExample("call the office"))
        assertNull(PhoneFormats.fromExample("12"))
    }

    // ---- matching ----

    @Test fun noMaskKeepsEveryNumber() {
        assertTrue(PhoneFormats.matches("415-555-2671", emptyList()))
        assertTrue(PhoneFormats.matches("100234567890", emptyList()))
    }

    @Test fun separatorsDoNotMatter() {
        val masks = masks("+880 1XXX-XXXXXX")
        assertTrue(PhoneFormats.matches("+880 1712-345678", masks))
        assertTrue(PhoneFormats.matches("+8801712345678", masks))
        assertTrue(PhoneFormats.matches("(880) 1712 345 678", masks))
    }

    @Test fun theDialCodeIsOptionalAndSoIsTheTrunkZero() {
        val masks = masks("+880 1XXX-XXXXXX")
        assertTrue(PhoneFormats.matches("1712345678", masks))
        assertTrue(PhoneFormats.matches("01712-345678", masks))
        assertTrue(PhoneFormats.matches("8801712345678", masks))
        assertTrue(PhoneFormats.matches("00880 1712 345678", masks))
    }

    @Test fun aMaskWrittenWithTheTrunkZeroTakesItOffToo() {
        val masks = masks("0XXXX XXXXXX")
        assertTrue(PhoneFormats.matches("07911 123456", masks))
        assertTrue(PhoneFormats.matches("7911123456", masks))
        assertTrue(PhoneFormats.matches("+44 7911 123456", masks))
    }

    @Test fun aLiteralDigitInTheMaskHasToMatch() {
        val masks = masks("+880 1XXX-XXXXXX")
        // Same length, wrong first digit: a landline, not the mobile shape.
        assertFalse(PhoneFormats.matches("2712345678", masks))
    }

    @Test fun theWrongLengthIsTheWrongNumber() {
        val masks = masks("+880 1XXX-XXXXXX")
        assertFalse(PhoneFormats.matches("171234567", masks))
        assertFalse(PhoneFormats.matches("17123456789", masks))
        // The invoice totals and tracking ids that started all this.
        assertFalse(PhoneFormats.matches("100234567890", masks))
    }

    @Test fun anExplicitDialCodeHasToBeTheRightCountry() {
        val masks = masks("+880 1XXX-XXXXXX")
        assertFalse(PhoneFormats.matches("+1 415 555 2671", masks))
        assertFalse(PhoneFormats.matches("+91 1712345678", masks))
    }

    @Test fun anyOfTheUsersFormatsIsEnough() {
        val masks = masks("+880 1XXX-XXXXXX", "+1 XXX-XXX-XXXX")
        assertTrue(PhoneFormats.matches("+880 1712-345678", masks))
        assertTrue(PhoneFormats.matches("+1 415-555-2671", masks))
        assertFalse(PhoneFormats.matches("+49 30 12345678", masks))
    }

    @Test fun aForeignNumberStillMatchesANationalMaskByShape() {
        // A national mask says nothing about countries, so an international
        // clip has its own dial code taken off before the shapes are compared.
        val masks = masks("XXXXXXXXXX")
        assertTrue(PhoneFormats.matches("+1 415 555 2671", masks))
        assertFalse(PhoneFormats.matches("+1 415 555 267", masks))
    }

    // ---- the detector uses them ----

    @Test fun theDetectorKeepsOnlyTheNumbersThatMatch() {
        val masks = masks("+880 1XXX-XXXXXX")
        val text = "Order 4002345678 ships today. Call 01712-345678 about it."
        val found = ClipEntities.extract(text, phoneFormats = masks)
            .filter { it.kind == ClipEntityKind.PHONE }
            .map { it.value }
        assertEquals(listOf("01712-345678"), found)
    }

    @Test fun theDetectorKeepsBothWithNoMask() {
        val text = "Order 4002345678 ships today. Call 01712-345678 about it."
        val found = ClipEntities.extract(text)
            .filter { it.kind == ClipEntityKind.PHONE }
            .map { it.value }
        assertEquals(listOf("4002345678", "01712-345678"), found)
    }

    @Test fun aMaskNeverBringsBackWhatTheGuardsDropped() {
        val masks = masks("XXXXXXXXXX")
        // A date and a price keep their guards whatever the masks say.
        val found = ClipEntities.extract("Due 2024-05-12 for \$1234567890", phoneFormats = masks)
            .filter { it.kind == ClipEntityKind.PHONE }
        assertTrue(found.isEmpty())
    }
}
