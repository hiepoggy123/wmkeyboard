package com.wasimaster.wmkeyboard.core.aihistory

import org.junit.Assert.assertEquals
import org.junit.Test

class AiHistoryGuardTest {

    @Test
    fun `every combination of the four conditions`() {
        // All sixteen, spelled out. This is the one decision in the feature
        // that is not allowed to be wrong, and a table is cheaper to read than
        // an argument about which conditions can occur together.
        for (enabled in listOf(false, true)) {
            for (unlocked in listOf(false, true)) {
                for (secure in listOf(false, true)) {
                    for (incognito in listOf(false, true)) {
                        val expected = enabled && unlocked && !secure && !incognito
                        assertEquals(
                            "enabled=$enabled unlocked=$unlocked " +
                                "secure=$secure incognito=$incognito",
                            expected,
                            AiHistoryGuard.shouldRecord(enabled, unlocked, secure, incognito),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the ordinary case records`() {
        assertEquals(
            true,
            AiHistoryGuard.shouldRecord(
                enabled = true,
                unlocked = true,
                secureField = false,
                incognito = false,
            ),
        )
    }

    @Test
    fun `a password field is never recorded, whatever else is true`() {
        assertEquals(
            false,
            AiHistoryGuard.shouldRecord(
                enabled = true,
                unlocked = true,
                secureField = true,
                incognito = false,
            ),
        )
    }

    @Test
    fun `incognito is never recorded, whatever else is true`() {
        assertEquals(
            false,
            AiHistoryGuard.shouldRecord(
                enabled = true,
                unlocked = true,
                secureField = false,
                incognito = true,
            ),
        )
    }

    @Test
    fun `nothing is recorded before the device is unlocked`() {
        assertEquals(
            false,
            AiHistoryGuard.shouldRecord(
                enabled = true,
                unlocked = false,
                secureField = false,
                incognito = false,
            ),
        )
    }
}
