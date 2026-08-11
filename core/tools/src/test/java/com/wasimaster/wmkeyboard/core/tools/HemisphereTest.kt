package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The region-derived starting point for the moon tool's mirroring. */
class HemisphereTest {

    @Test
    fun `southern regions read as southern`() {
        for (region in listOf("AU", "NZ", "ZA", "AR", "BR", "CL", "ID", "PE", "TZ", "FJ")) {
            assertTrue("$region should be southern", isSouthernHemisphere(region))
        }
    }

    @Test
    fun `northern regions read as northern`() {
        for (region in listOf("BD", "US", "GB", "IN", "JP", "DE", "EG", "NG", "MX", "CO")) {
            assertFalse("$region should be northern", isSouthernHemisphere(region))
        }
    }

    @Test
    fun `an unknown or absent region reads as northern`() {
        assertFalse(isSouthernHemisphere(null))
        assertFalse(isSouthernHemisphere(""))
        assertFalse(isSouthernHemisphere("ZZ"))
    }

    @Test
    fun `region codes match regardless of case`() {
        assertTrue(isSouthernHemisphere("au"))
        assertTrue(isSouthernHemisphere("Nz"))
    }
}
